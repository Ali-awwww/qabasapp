-- أحداث التحليلات لنظرة النمو في لوحة المطور (الدفعة 5 — الجزء أ، إعادة إنشاء).
--
-- من تقرير الرئيسية ولوحة المطور: اللوحة بلا DAU/MAU ولا احتفاظ ولا
-- مقاييس نمو، فهذا الجدول هو مصدر الأحداث التي تُحسب منها المقاييس.
-- التطبيق يسجّل الأحداث بأفضل جهد (RealAnalyticsService.logEvent):
-- إن غاب الجدول قبل تطبيق الترحيل يسجَّل الفشل في Logcat فقط ولا
-- يتعطل التطبيق.
--
-- ملاحظات صريحة:
-- - DAU = مستخدمون مميزون بـ user_id غير فارغ؛ الجلسات المجهولة
--   تُحسب من session_id حيث user_id فارغ، وتُعرض منفصلة ولا تُدمج
--   مع DAU أبداً.
-- - سياسة الإدراج للمصادَقين تشترط user_id = auth.uid() (أو فارغاً)
--   حتى تحمل أحداث المستخدمين المسجَّلين معرّفهم فيُقاس DAU/MAU؛
--   وسياسة المجهولين تشترط user_id فارغاً. بلا سياسة المصادَقين
--   يبقى DAU صفراً إلى الأبد لأن الإدراج المجهول وحده لا يحمل معرّفاً.
-- - لا سياسة UPDATE ولا DELETE للعملاء إطلاقاً: الأحداث سجل إضافة فقط.
-- - القراءة للمدراء فقط عبر qabas_is_support_admin() (على نمط
--   moderation_queue في ترحيل dev_queues).
-- - لا معدل خالٍ من الأعطال من هذا الجدول: لا مصدر لإجمالي الجلسات.
--
-- ⚠️ التطبيق: غير مطبَّق حياً بعد. طبّقه من محرر SQL في لوحة Supabase
-- (أو `supabase db push`) — لا يوجد supabase CLI ولا psql في بيئة
-- التطوير الحالية. بعد التطبيق تحقق بـ:
--   select count(*) from public.analytics_events;  -- المتوقع 0

create table if not exists public.analytics_events (
    id uuid primary key default gen_random_uuid(),
    user_id uuid null,
    event_name text not null,
    properties jsonb not null default '{}'::jsonb,
    session_id text null,
    app_version text null,
    created_at timestamptz not null default now()
);

create index if not exists idx_analytics_events_name_created
    on public.analytics_events (event_name, created_at desc);
create index if not exists idx_analytics_events_created
    on public.analytics_events (created_at desc);
create index if not exists idx_analytics_events_user_created
    on public.analytics_events (user_id, created_at desc);

alter table public.analytics_events enable row level security;

revoke all on public.analytics_events from anon, authenticated;
grant select, insert on public.analytics_events to authenticated;
grant insert on public.analytics_events to anon;

-- القراءة للمدراء فقط.
drop policy if exists analytics_events_admin_read on public.analytics_events;
create policy analytics_events_admin_read
on public.analytics_events
for select
to authenticated
using (public.qabas_is_support_admin());

-- إدراج المجهولين: user_id فارغ حتماً.
drop policy if exists analytics_events_anon_insert on public.analytics_events;
create policy analytics_events_anon_insert
on public.analytics_events
for insert
to anon
with check (user_id is null);

-- إدراج المسجَّلين: user_id هو معرّفهم أو فارغ.
drop policy if exists analytics_events_auth_insert on public.analytics_events;
create policy analytics_events_auth_insert
on public.analytics_events
for insert
to authenticated
with check (user_id is null or user_id = auth.uid());
