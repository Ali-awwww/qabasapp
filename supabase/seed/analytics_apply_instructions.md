# تطبيق ترحيل أحداث التحليلات — الدفعة 5، الجزء أ

## التطبيق (من لوحة Supabase > SQL Editor)

1. انسخ محتوى `supabase/migrations/20261122120000_analytics_events.sql` كاملاً.
2. الصقه في محرر SQL ونفّذه.
3. نفّذ استعلامات التحقق أدناه.

## التحقق

```sql
select count(*) from public.analytics_events;  -- المتوقع 0
select column_name from information_schema.columns
  where table_name = 'analytics_events'
  order by ordinal_position;
-- المتوقع: id, user_id, event_name, properties, session_id,
-- app_version, created_at
```

## ملاحظات

- هذا الترحيل واحد من أربعة معلّقة: `mishkat_daily` (الدفعة 3)،
  و`studio_jobs` (الدفعة 4)، و`dev_queues` (الدفعة 5)، وهذا الترحيل.
  طبّقها كلها من محرر SQL ثم تحقق بقراءة الجداول.
- حتى يُطبَّق الترحيل تبقى شاشة «نظرة عامة» في لوحة المطور تعرض
  «لا توجد بيانات بعد» لكل مقاييس الأحداث، وهذا هو السلوك الصحيح —
  لا رقم مخترع. إجمالي المستخدمين يُقرأ من `profiles` ويظهر فوراً.
- بعد التطبيق يبدأ `RealAnalyticsService.logEvent` بتسجيل الأحداث
  بأفضل جهد (يفشل بصمت إلى Logcat إن تعذّر الإدراج).
