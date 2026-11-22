# تطبيق ترحيلي الاستوديو ومشكاة اليومية (الدفعتان 3 و4)

هذان الترحيلان معلّقان — لا Supabase CLI ولا psql في بيئة التطوير،
فالتطبيق من لوحة Supabase (المالك) ثم التحقق بالقراءة.

## 1) تطبيق الترحيلين معاً

من لوحة Supabase → **SQL Editor** → **New query**، الصق وشغّل بالترتيب:

1. `supabase/migrations/20261121222300_mishkat_daily.sql` (الدفعة 3 — جدول `mishkat_daily`)
2. `supabase/migrations/20261122000000_studio_jobs.sql` (الدفعة 4 — جداول `studio_jobs`
   و`ai_generations` + عمود `stage` في `projects` + سلة `studio-assets`)

أو من سطر الأوامر إن توفر Supabase CLI:

```bash
supabase db push
```

## 2) تحميل بذرة مشكاة (29 صفاً)

بعد تطبيق ترحيل `mishkat_daily`، شغّل من SQL Editor نفسه:

- `supabase/seed/mishkat_daily_seed.sql` (الأيام 2–30؛ اليوم 1 يعيش أصلاً
  في `mishkat_reminder_content`).

لا بذرة لجدولي الاستوديو — يمتلئان من استخدام التطبيق نفسه.

## 3) استعلامات التحقق والأعداد المتوقعة

```sql
select count(*) from public.mishkat_daily;      -- المتوقع: 29
select count(*) from public.studio_jobs;        -- المتوقع: 0
select count(*) from public.ai_generations;     -- المتوقع: 0
select column_name from information_schema.columns
  where table_schema = 'public' and table_name = 'projects'
  and column_name = 'stage';                    -- المتوقع: صف واحد (stage)
select id, public from storage.buckets
  where id = 'studio-assets';                   -- المتوقع: صف واحد، public = false
```

## 4) ما يحدث إن لم تُطبَّق

الكود يفشل بلطف إلى فارغ: تذكير مشكاة يرجع للسجل العام الواحد،
ومكتبة المشاريع تعرض «لا مهام/لا توليدات»، ووكيل الاستوديو يعمل
(التوليد نفسه لا يعتمد على الجدولين — التسجيل فقط يتخطى الفشل).
