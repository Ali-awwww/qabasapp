# إشعارات ردود المطور

تمت إضافة مسار FCM كامل داخل التطبيق، لكنه يحتاج تفعيل الجزء الخادمي مرة واحدة حتى تصل الإشعارات والتطبيق مغلق:

1. طبّق migration `20261114160000_user_push_tokens.sql`.
2. انشر الدالة:

```bash
supabase functions deploy send-developer-reply-push --no-verify-jwt
```

3. أضف أسرار الدالة:

```bash
supabase secrets set \
  DEV_CHAT_PUSH_WEBHOOK_SECRET="ضع-قيمة-عشوائية-طويلة" \
  FIREBASE_SERVICE_ACCOUNT_JSON="$(cat firebase-service-account.json)"
```

ملف حساب الخدمة لا يوضع داخل المستودع ولا داخل تطبيق Android. يجب أن يكون حساب Firebase قادراً على استخدام Firebase Cloud Messaging API (HTTP v1).

4. من لوحة Supabase أنشئ **Database Webhook** على:

- الجدول: `public.dev_chat_messages`
- الحدث: `INSERT`
- الطريقة: `POST`
- الرابط: `https://egkgygbvoeevakwkbvfu.supabase.co/functions/v1/send-developer-reply-push`
- Header: `x-qabas-webhook-secret: نفس قيمة DEV_CHAT_PUSH_WEBHOOK_SECRET`

الدالة تتحقق من أن `sender = developer`، ثم تبحث عن رموز أجهزة صاحب الرسالة فقط، وترسل Data Message عالية الأولوية إلى FCM. الرموز غير الصالحة تُحذف تلقائياً.

داخل التطبيق:

- يسجل `PushTokenManager` رمز FCM بعد استعادة جلسة Supabase.
- يخزن الرمز مؤقتاً إذا وصل قبل تسجيل الدخول ثم يزامنه بعد ذلك.
- يعرض `MyFirebaseMessagingService` إشعاراً بعنوان «رد جديد من المطور».
- النقرة على الإشعار تفتح مركز التواصل مباشرة.
- يمنع الإشعار المكرر لنفس `message_id`.

على Android 13 أو أحدث يجب أن تكون صلاحية الإشعارات مفعلة من إعدادات الجهاز. إشعارات الصلاة الحالية لا تتأثر بهذا المسار.
