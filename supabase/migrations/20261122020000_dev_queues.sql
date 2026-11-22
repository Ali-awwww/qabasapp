-- Developer work queues, alerts support tables (Batch 5, part B — الدفعة 5).
--
-- من تقرير الرئيسية ولوحة المطور (قسم 3-و/3-ز/3-ي): البلاغات اليوم رسائل
-- بلا حالة، ولا قائمة مراجعة لريلز المستخدمين، ولا فرز للأعطال حسب التأثير.
-- هذا الترحيل يضيف سير العمل الثلاثة:
--   1) public.moderation_queue: قائمة مراجعة الريلز (موافقة/رفض مع سبب).
--   2) أعمدة status/priority/assigned_to على public.dev_chat_messages
--      (الجدول موجود أصلاً منذ 202610060001، وuser_id أُضيف في 202610090001):
--      يُفضَّل تعديل الجدول الموجود على جدول حالة منفصل مفتاحاً بمعرّف الرسالة.
--   3) public.crash_queue: حالة سير عمل مجموعات الأعطال مفتاحاً بالتوقيع.
--
-- ملاحظات صريحة:
-- - جدول `reels` بلا أي عمود حالة مراجعة (فُحصت ترحيلاته وReelRow)،
--   فحالة المراجعة تعيش هنا في moderation_queue، وإجراء المراجع يضبط
--   ظهور المقطع نفسه عبر is_active (موافقة -> ظاهر، رفض -> مخفي).
--   إن أُضيف لاحقاً عمود review_status إلى reels، تُرحَّل الحالة من هنا.
-- - لا تُمنح سياسة UPDATE مباشرة على dev_chat_messages: تغيير الحالة يتم
--   حصرياً عبر دالتي RPC أدناه (على نمط qabas_edit_dev_chat_message)،
--   وكلتاهما محروستان بـ qabas_is_support_admin().
-- - التدقيق (audit): لا يوجد جدول تدقيق خادم في المشروع؛ سجل التدقيق
--   اليوم محلي في التطبيق (AuditLogger — سلسلة hash في qabas_prefs).
--   لذا كل تغيير حالة من العميل *يجب* أن يسجَّل عبر AuditLogger قبل/بعد
--   نجاح الاستدعاء (الكود في DevWorkQueues.kt يفعل ذلك بأسماء إجراءات
--   moderation_review وsupport_status_change وcrash_status_change).
--   إن أُنشئ لاحقاً جدول تدقيق خادم، تُضاف كتابة فيه داخل دالتي RPC.
-- - تنبيهات DevAlerts بلا جدول: التقييمات تُسجَّل محلياً في التطبيق
--   (qabas_dev_alerts)؛ لا جدول تنبيهات خادم في هذه الدفعة.
--
-- ⚠️ التطبيق: غير مطبَّق حياً بعد. طبّقه من محرر SQL في لوحة Supabase
-- (أو `supabase db push`) — لا يوجد supabase CLI ولا psql في بيئة
-- التطوير الحالية. بعد التطبيق تحقق بـ:
--   select count(*) from public.moderation_queue;  -- المتوقع 0
--   select count(*) from public.crash_queue;       -- المتوقع 0
--   select column_name from information_schema.columns
--     where table_name = 'dev_chat_messages'
--     and column_name in ('status', 'priority', 'assigned_to');  -- المتوقع 3

-- ============================================================
-- 1) moderation_queue — قائمة مراجعة الريلز
-- ============================================================
create table if not exists public.moderation_queue (
    id uuid primary key default gen_random_uuid(),
    reel_id text not null,
    submitted_by uuid null,
    status text not null default 'pending'
        check (status in ('pending', 'approved', 'rejected')),
    reason text null,
    reviewed_by uuid null,
    reviewed_at timestamptz null,
    created_at timestamptz not null default now()
);

-- ريلز واحد لا يراجَع مرتين في آن واحد: صف معلَّق واحد لكل مقطع.
create unique index if not exists idx_moderation_queue_pending_reel
    on public.moderation_queue (reel_id) where status = 'pending';
create index if not exists idx_moderation_queue_status_created
    on public.moderation_queue (status, created_at asc);

alter table public.moderation_queue enable row level security;

revoke all on public.moderation_queue from anon, authenticated;
grant select, insert, update on public.moderation_queue to authenticated;

drop policy if exists moderation_queue_admin_read on public.moderation_queue;
create policy moderation_queue_admin_read
on public.moderation_queue
for select
to authenticated
using (public.qabas_is_support_admin());

drop policy if exists moderation_queue_admin_insert on public.moderation_queue;
create policy moderation_queue_admin_insert
on public.moderation_queue
for insert
to authenticated
with check (public.qabas_is_support_admin());

drop policy if exists moderation_queue_admin_update on public.moderation_queue;
create policy moderation_queue_admin_update
on public.moderation_queue
for update
to authenticated
using (public.qabas_is_support_admin())
with check (public.qabas_is_support_admin());

-- ============================================================
-- 2) dev_chat_messages — حالة الدعم وأولويته (أعمدة على الجدول الموجود)
-- ============================================================
alter table public.dev_chat_messages
    add column if not exists status text not null default 'new'
        check (status in ('new', 'in_progress', 'done'));

alter table public.dev_chat_messages
    add column if not exists priority text not null default 'normal'
        check (priority in ('low', 'normal', 'high', 'urgent'));

alter table public.dev_chat_messages
    add column if not exists assigned_to uuid null;

create index if not exists idx_dev_chat_messages_status_created
    on public.dev_chat_messages (status, created_at desc);

-- تغيير حالة خيط كامل (كل رسائل user_id) — المسار الوحيد المسموح لتغيير
-- الحالة؛ لا سياسة UPDATE مباشرة على الجدول (انظر الملاحظة أعلاه).
create or replace function public.qabas_set_dev_chat_thread_status(
    p_user_id text,
    p_status text,
    p_priority text default null,
    p_assigned_to uuid default null
)
returns boolean
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
begin
    if auth.uid() is null or not public.qabas_is_support_admin() then
        raise exception 'Not authorized to manage support tickets' using errcode = '42501';
    end if;

    if p_user_id is null or btrim(p_user_id) = ''
       or p_status is null or p_status not in ('new', 'in_progress', 'done')
       or (p_priority is not null and p_priority not in ('low', 'normal', 'high', 'urgent')) then
        raise exception 'Invalid support ticket input' using errcode = '22023';
    end if;

    update public.dev_chat_messages
       set status = p_status,
           priority = coalesce(p_priority, priority),
           assigned_to = coalesce(p_assigned_to, assigned_to),
           updated_at = clock_timestamp()
     where user_id = p_user_id;

    return found;
end;
$$;

-- تغيير حالة رسالة واحدة — للخيوط القديمة بلا user_id (مفتاحها البريد فقط).
create or replace function public.qabas_set_dev_chat_message_status(
    p_message_id text,
    p_status text,
    p_priority text default null,
    p_assigned_to uuid default null
)
returns boolean
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
begin
    if auth.uid() is null or not public.qabas_is_support_admin() then
        raise exception 'Not authorized to manage support tickets' using errcode = '42501';
    end if;

    if p_message_id is null or btrim(p_message_id) = ''
       or p_status is null or p_status not in ('new', 'in_progress', 'done')
       or (p_priority is not null and p_priority not in ('low', 'normal', 'high', 'urgent')) then
        raise exception 'Invalid support ticket input' using errcode = '22023';
    end if;

    update public.dev_chat_messages
       set status = p_status,
           priority = coalesce(p_priority, priority),
           assigned_to = coalesce(p_assigned_to, assigned_to),
           updated_at = clock_timestamp()
     where id = p_message_id;

    return found;
end;
$$;

revoke all on function public.qabas_set_dev_chat_thread_status(text, text, text, uuid) from public, anon;
revoke all on function public.qabas_set_dev_chat_message_status(text, text, text, uuid) from public, anon;
grant execute on function public.qabas_set_dev_chat_thread_status(text, text, text, uuid) to authenticated;
grant execute on function public.qabas_set_dev_chat_message_status(text, text, text, uuid) to authenticated;

-- ============================================================
-- 3) crash_queue — حالة سير عمل مجموعات الأعطال
-- ============================================================
-- التوقيع هو exception_type من تقرير قياس الأعطال (كما يجمّع
-- CrashTelemetryCloudScreen)؛ الأعداد (المتأثرون/التقارير) تُحسب عند
-- القراءة من dev_bug_reports، وهذا الجدول يحمل الحالة والإصدار المُصلح فقط.
create table if not exists public.crash_queue (
    id uuid primary key default gen_random_uuid(),
    crash_signature text not null unique,
    affected_users int not null default 0 check (affected_users >= 0),
    status text not null default 'new'
        check (status in ('new', 'fixing', 'fixed')),
    fixing_release text null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_crash_queue_status_affected
    on public.crash_queue (status, affected_users desc);

alter table public.crash_queue enable row level security;

revoke all on public.crash_queue from anon, authenticated;
grant select, insert, update on public.crash_queue to authenticated;

drop policy if exists crash_queue_admin_read on public.crash_queue;
create policy crash_queue_admin_read
on public.crash_queue
for select
to authenticated
using (public.qabas_is_support_admin());

drop policy if exists crash_queue_admin_insert on public.crash_queue;
create policy crash_queue_admin_insert
on public.crash_queue
for insert
to authenticated
with check (public.qabas_is_support_admin());

drop policy if exists crash_queue_admin_update on public.crash_queue;
create policy crash_queue_admin_update
on public.crash_queue
for update
to authenticated
using (public.qabas_is_support_admin())
with check (public.qabas_is_support_admin());
