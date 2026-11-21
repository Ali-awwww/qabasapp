# مصفوفة توافق Supabase — مساعد المسلم

**الحالة:** تحليل وتنفيذ تدريجي موثق
**المشروع:** `qabasapp` (`egkgygbvoeevakwkbvfu`)
**قاعدة العمل:** لا تغيير في الصلاحيات أو البيانات قبل ربط كل قرار باستعمال فعلي وخطة رجوع.

## معاني التصنيف

- **عام للقراءة:** يمكن للزائر القراءة، ولا توجد كتابة من العميل.
- **خاص بالمالك:** المستخدم المصادق عليه يتعامل مع صفوفه فقط.
- **إدارة محكومة:** الكتابة تتم عبر دور إداري أو RPC/Edge Function.
- **خادم فقط:** لا ينبغي أن يقرأه أو يكتب فيه تطبيق الهاتف مباشرة.
- **يحتاج توافقًا:** توجد استعمالات متعددة أو نموذج قديم، فلا ينفذ تغييره قبل مراجعة التطبيق.

## أولوية الحماية

| الأولوية | النطاق | القرار الحالي | سبب الترتيب |
|---|---|---|---|
| P0 | `user_push_tokens`, `user_azan_voices`, `app_secrets`, `platform_api_keys` | تدقيق grants وStorage فورًا | بيانات خاصة أو أسرار، وظهور منح واسعة في الفحص |
| P0 | `mishkat_reminder_content` | إزالة منح الكتابة العامة بعد إثبات عدم الحاجة | ظهر grant واسع لـ `anon` و`authenticated` |
| P1 | `dev_chat_messages`, `dev_bug_reports`, `chat_messages`, `bug_reports` | إبقاء الوصول عبر UID/support admin فقط | بيانات دعم وتشخيص خاصة |
| P1 | `promo_codes`, `promo_code_redemptions`, `transactions` | الخادم/RPC فقط للعمليات الحساسة | منع التلاعب بالقيمة أو الاسترداد |
| P1 | `users`, `profiles`, `user_settings` | تثبيت نموذج الهوية والملكية | تعدد معرفات المستخدم |
| P2 | `reels`, `reel_comments`, `reel_likes`, `reel_bookmarks`, `reel_history` | الإبقاء على العام للقراءة والملكية للكتابة | سياسات فعلية جيدة نسبيًا، تحتاج اختبارًا لا إعادة تصميم فورية |
| P2 | `app_updates`, `app_update_audit` | القراءة العامة للتحديثات وكتابة الإدارة عبر RPC | مسار أمني موجود ويحتاج توثيقًا واختبارًا |
| P3 | `audio_tracks`, `hadith_presets`, `scholar_lectures`, `master_styles` | عام للقراءة وكتابة الإدارة | محتوى عام مع عمليات تحرير محدودة |

## مصفوفة الجداول الحساسة

| الجدول | التصنيف المستهدف | القراءة | الكتابة | الاستعمال البرمجي المرصود | القرار قبل migration |
|---|---|---|---|---|---|
| `user_push_tokens` | خاص بالمالك/الخادم | المالك أو خدمة الإشعارات فقط | المالك عند التسجيل، والخادم عند الإرسال | `PushTokenManager`, خدمات الإشعارات، `SupabaseServices` | منفذ: إزالة منح `anon` وتضييق `authenticated` إلى CRUD فقط؛ بقي اختبار التسجيل والإلغاء مطلوبًا |
| `user_azan_voices` | خاص بالمالك | `auth.uid() = user_id` | المالك على صفه | `UserAzanCloudRepository`, `AzanCloudRepository` | منفذ: إزالة منح `anon` وتضييق المنح غير الوظيفية؛ بقي اختبار الرفع والحذف وBucket المقابل مطلوبًا |
| `app_secrets` | خادم فقط | Edge Functions/service role | Edge Functions/service role | `UpdateManager`, Xdelta Edge Functions | منع وصول العميل المباشر؛ التحقق من أن الاستعمالات الحالية لا تقرأ القيمة السرية من الهاتف |
| `platform_api_keys` | خادم/إدارة فقط | وظيفة Vault أو إدارة محكومة | وظيفة Vault فقط | `SupabaseServices`, key-vault paths | لا grants للعميل؛ مراجعة كل استدعاء قبل أي سحب |
| `mishkat_reminder_content` | عام للقراءة، كتابة مطوّر مصادق عليها مؤقتًا | `anon` و`authenticated` قراءة | التطبيق يكتب مباشرة عبر `SupabaseServices.Database.saveMishkatReminderContent`، والسياسة تقيدها بدور الدعم | `MishkatReminderSection`, `SupabaseServices.kt:80-105` | لا نسحب grants أو نكسر المسار الآن؛ ننقل الكتابة لاحقًا إلى RPC إداري بعد توفير بديل واختبار المسار |
| `dev_chat_messages` | خاص بالمالك/الدعم | المالك أو support admin | المستخدم في خيطه، الإدارة عبر وظائف الإدارة | `DevChatRepository`, `DevChatRealtimeManager`, شاشة التواصل | لا تغيير في RLS؛ اختبار Realtime وUID أولًا |
| `dev_bug_reports` | إدخال عام محدود، قراءة المالك/الدعم | المالك أو support admin | إدخال مقيد، تحديث الإدارة | `BugReportRepository`, `CrashTelemetry`, `CrashTelemetryCloudScreen` | الحفاظ على مسار البلاغ المجهول إن كان مطلوبًا، ومنع القراءة العامة |
| `chat_messages` | خاص بالمالك | المالك | المالك | خدمات المحادثة القديمة/التوافق | تحديد هل ما زال مستخدمًا؛ لا حذف قبل إزالة الاستدعاءات |
| `bug_reports` | legacy أو intake بديل | لا وصول عميل افتراضيًا | مسار البلاغ الرسمي فقط | يجب التحقق من استعمالاته | مرشح للأرشفة بعد إثبات عدم الاستخدام |
| `user_settings` | خاص بالمالك | `auth.uid() = user_id` | المالك | `UserPreferencesManager`, `UserAzanCloudRepository` | إبقاء النموذج الحالي، ثم إضافة قيود نوعية لاحقًا |
| `profiles` | خاص بالمالك/عرض عام عبر View | الصف الشخصي، والعام عبر View آمن | المالك للحقول الذاتية | `UserPreferencesManager`, `SupabaseServices` | عدم كشف `role` عبر View عام |
| `users` | خاص بالمالك/إدارة | المالك أو الإدارة | إنشاء/تحديث ذاتي محدود، الإدارة عبر RPC | `AccountService`, `UserIdentifierManager`, `AdminGuard` | لا ربط جديد قبل اعتماد نموذج الهوية |
| `transactions` | خاص بالمالك/الخادم | المالك | الخادم أو Billing backend | `GiftManager`, `PointsManager`, الخدمات المالية | منع إدخال/تعديل العميل المباشر |
| `promo_codes` | خادم/RPC | لا قراءة خام من العميل | `reserve/confirm/release` عبر RPC | `GiftManager`, `SupabaseServices` | مراجعة EXECUTE وidempotency والـ locks |
| `promo_code_redemptions` | خاص بالمالك/الخادم | المالك أو الإدارة | RPC فقط | وظائف الترويج | عدم منح PostgREST المباشر |
| `reels` | عام للقراءة، المالك للكتابة | عامة للريلز النشطة | المالك/وظائف الإدارة | `ReelsScreen`, `ReelPublishManager` | الإبقاء مع اختبار owner update وadmin flags |
| `reel_comments` | عام للقراءة، المالك للكتابة | عامة | المالك فقط | `Reel*` components | الإبقاء، ومراجعة طول النص ومكافحة الإساءة |
| `reel_likes` | خاص بعلاقة المستخدم | المستخدم لحالته | المالك | `ReelPublishManager`, `ReelsScreen` | الإبقاء على uniqueness والعدادات |
| `reel_bookmarks` | خاص بالمالك | المالك | المالك | `ReelPreferencesManager`, `ReelHistorySheet` | الإبقاء |
| `reel_history` | خاص بالمالك | المالك | المالك | `ReelHistorySheet`, player flows | الإبقاء مع حدود مدة المشاهدة |
| `app_updates` | عام للقراءة، إدارة عبر RPC | anon/authenticated | RPC إداري | `UpdateManager`, `UpdateChannelFacade` | عدم منح كتابة مباشرة |
| `app_update_audit` | إدارة فقط | developer/admin/owner | RPC داخلي | `UpdateManagementSection` | الإبقاء مع اختبار claims |

## مصفوفة Storage

| Bucket | الوضع الحالي | التصنيف المستهدف | القرار |
|---|---|---|---|
| `bug_reports` | خاص | خاص بالبلاغات | الإبقاء؛ التحقق من مسار signed URL والحذف الإداري |
| `project-recordings` | خاص | خاص بمالك المشروع | الإبقاء؛ ربط path بـ UID/project ownership |
| `user-azan-private` | خاص | خاص بمالك الصوت | الإبقاء؛ لا public URL دائم |
| `azan-voices` | عام | محتوى عام رسمي | توثيق أنه لا يحتوي أصوات مستخدمين خاصة |
| `audio_tracks` و`audio-tracks` | عامة ومتشابهة | Bucket رسمي واحد | تحديد bucket canonical ثم خطة نقل غير مدمرة |
| `avatars` | عام | صور عامة بعد موافقة المستخدم | التحقق من path وعدم تخزين بيانات حساسة |
| `reels` | عام | فيديوهات منشورة فقط | فصل الملفات المؤقتة عن المنشورة |
| `user-uploads` | عام | محتوى reels منشور، مع legacy paths مختلطة | لم يُغيّر: الرفع الجديد يستخدم UUID، لكن توجد كائنات قديمة بمسارات بريد إلكتروني؛ لا حذف قبل مراجعة orphan candidates |
| `ai-generated`, `media-assets` | عامة | محتوى منشور أو مؤقت | لم يُغيّر: `media-assets` يحتوي `starter_reels/*` و`thumbnails/*` وpath يبدأ بمعرّف مستخدم؛ يجب مطابقة هذا path قبل فصل المنشور عن الخاص |

**نتيجة الجرد الأخير:** يوجد 58 كائنًا فعليًا في Storage. أربعة كائنات قديمة في `user-uploads` وأربعة thumbnails في `media-assets` تستخدم البريد الإلكتروني داخل المسار، كما توجد مسارات `anonymous` وبعض الكائنات ذات `owner_id` فارغ. لم تُنفذ أي عملية نقل أو حذف. الرفع الجديد في `ReelPublishManager` و`CentralMediaManager` يستخدم UUID، بينما إرشاد شاشة الصوت عُدّل ليمنع اقتراح `Public Insert`.

جدول `audio_tracks` فارغ حاليًا، لذلك كائن `audio-tracks` الجذري غير مرتبط بسجل جدول. في المقابل، صف Mishkat العالمي المفعّل يشير إلى ملف عام في `media-assets` تحت UID؛ لا يُحذف الملف، ويُرحّل مستقبلًا إلى مسار رسمي لا يكشف هوية المالك بعد توفير بديل واختبار الروابط.

تم تنفيذ إصلاح محلي غير تدميري: شاشة رفع الصوت تستخدم الآن مسار `<auth.uid>/<filename>`، ولا تسجل رابطًا عامًا عند فشل الرفع. لم يتم تعديل الكائن الجذري legacy أو نقل أي ملف.

## مصفوفة الوظائف الحساسة

| الوظيفة | التصنيف | مستخدموها المتوقعون | فحص مطلوب |
|---|---|---|---|
| `check_account_availability` | مصادق/محدود | مسار الهوية | منع enumeration وتثبيت rate limit |
| `claim_active_session` / `release_active_session` | مالك الجلسة | المستخدم الحالي | منع تمرير session id لمستخدم آخر |
| `reserve_promo_code` / `confirm_promo_code` / `release_promo_code` | خادم/RPC | مسار الشراء أو الهدية | اختبار idempotency والصلاحيات |
| `create_broadcast` | إدارة/خادم | developer/admin/owner | يتحقق من role claim و`search_path` والمدخلات؛ أبقي التنفيذ للمصادقين مع تحقق داخلي |
| `mark_broadcast_sent` | معطل حاليًا | لا يوجد دور ممنوح؛ worker مستقبلي بعد تصميمه | تم سحب التنفيذ من جميع الأدوار لأن `sent_at` عالمي؛ التطبيق يسجل العرض محليًا بدل تغيير الصف |
| `publish_qabas_app_update` / `set_qabas_app_update_active` | إدارة | developer/admin/owner | إبقاء الكتابة عبر RPC فقط |
| `qabas_edit_dev_chat_message` / `qabas_delete_dev_chat_message` | support admin | الدعم المعتمد | عدم الثقة في `p_user_id` القادم من العميل |
| `set_user_suspension` | إدارة | owner/admin | اختبار منع self-escalation وتغيير الدور |
| `replace_github_token` | خادم/إدارة | Edge Function أو إدارة محكومة | محصور في `service_role`؛ منع كشف token في response/logs |
| `record_reel_view` / `sync_reel_likes_count` | خادم/trigger | المستخدم أو النظام | حدود التكرار والعدادات الذرية |

## مصفوفة التطبيق والاعتماد

قبل تنفيذ أي سحب للصلاحيات، يجب اعتبار الملفات التالية نقاط تحقق إلزامية:

- `app/src/main/java/com/qabas/app/SupabaseServices.kt`
- `app/src/main/java/com/qabas/app/SupabaseRepository.kt`
- `app/src/main/java/com/qabas/app/PushTokenManager.kt`
- `app/src/main/java/com/qabas/app/UserAzanCloudRepository.kt`
- `app/src/main/java/com/qabas/app/UserPreferencesManager.kt`
- `app/src/main/java/com/qabas/app/DevChatRepository.kt`
- `app/src/main/java/com/qabas/app/BugReportRepository.kt`
- `app/src/main/java/com/qabas/app/UpdateManager.kt`
- `app/src/main/java/com/qabas/app/GiftManager.kt`
- `app/src/main/java/com/qabas/app/ReelPublishManager.kt`
- وظائف `supabase/functions` الخاصة بالمفاتيح وGitHub والتحديثات والدعم.

## أول migration — منفذة ومتحقق من أثرها

تم تطبيق أول migration grants صغيرة قابلة للعكس باسم `restrict_anon_private_data_grants`. استهدفت فقط:

1. سحب كل grants من `anon` على `user_push_tokens`.
2. سحب كل grants من `anon` على `user_azan_voices`.
3. سحب grants من `anon` على `mishkat_reminder_content` ثم إعادة `SELECT` فقط.
4. إبقاء RLS والسياسات والبيانات كما هي.

تم التحقق بعد التطبيق من أن `anon` يملك `SELECT` فقط على `mishkat_reminder_content` ولا يملك grants على الجدولين الخاصين.

## حالة التنفيذ

- **منفذ:** إنشاء المصفوفة وربطها بنتائج الجرد الفعلي.
- **متحقق قراءةً:** الجداول والسياسات والمنح والوظائف وBuckets من مشروع Supabase الإنتاجي.
- **منفذ:** أول `REVOKE/GRANT` محدود ومسجل في migration المحلية `20261121175820_restrict_anon_private_data_grants.sql`.
- **منفذ:** migration `20261121180054_tighten_authenticated_private_table_grants.sql`؛ CRUD باقٍ، والمنح البنيوية غير المطلوبة مسحوبة.
- **غير منفذ:** تغيير RLS أو المخطط أو البيانات، أو توحيد Buckets، أو بناء worker خادمي لتسليم broadcasts.
- **منفذ:** إصلاح مسار broadcasts في التطبيق عبر deduplication محلي محدود، وتقييد `mark_broadcast_sent` في migrations `20261121180446_restrict_broadcast_global_ack.sql` و`20261121180547_revoke_broadcast_global_ack_service.sql`.
- **غير منفذ:** بناء worker خادمي لإرسال البث وتسجيل التسليم الموثوق؛ هذا مسار لاحق مستقل.

## نتيجة تدقيق EXECUTE الأخير

تمت مطابقة منح الوظائف الحساسة الفعلية مع الاستدعاءات البرمجية. لم تُنفذ migration إضافية في هذه الجولة لأن الوظائف التي تظهر لـ`authenticated` تملك تحققًا داخليًا للدور أو الملكية، بينما تأكيد الأكواد الترويجية وتعديل أسرار GitHub محصوران في `service_role`. يلزم لاحقًا اختبار وظيفي مصادق عليه لهذه المسارات بعد الحصول على إذن تشغيل الاختبارات، ولا يُستنتج نجاحها من الفحص الساكن وحده.
- **غير مختبر:** build/compile/test/lint واختبارات مستخدمين حقيقية.

## نتيجة جرد Storage الفعلي

- `reels`: مسارات منشورة تحت `videos/<user-id>/...` و`thumbnails/<user-id>/...`.
- `user-uploads`: مسارات fallback للريلز تحت `reels/<user-id>/...`.
- `media-assets`: `starter_reels/*` و`thumbnails/*` ومسار يبدأ بمعرّف مستخدم؛ لذلك لا يجوز تضييق القراءة العامة قبل مطابقة السجل المرتبط به.
- `audio-tracks`: مستخدم فعليًا للرفع من التطبيق؛ أما `audio_tracks` فاسم Bucket مختلف ظهر في سياسات legacy ولا توجد له إحالة رفع حالية مؤكدة.
- `ai-generated`: لا توجد كائنات ظاهرة في القياس المجمع الحالي، ولا توجد migration تغيير مطلوبة الآن.
- كائنات `user-uploads` القديمة ذات المسار `reels/<email>/...` لم تُطابق روابط `public.reels` الحالية؛ تصنيفها `legacy/orphan candidate` وليس `safe to delete`.
- `ReelPublishManager` يستخدم `auth.uid()` في مسارات الرفع الجديدة، لذلك لا توجد حاجة لتغيير معرف المسار في الكود الحالي.
- توجد سياسات قراءة عامة متداخلة لبعض Buckets العامة، لكن لا توجد سياسة رفع عامة ظاهرة في الحالة الفعلية؛ لا يُنصح بتنظيف أسماء السياسات أو دمجها قبل اختبار روابط المحتوى المنشور.
