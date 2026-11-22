-- طابور مهام الاستوديو وسجل التوليدات (الدفعة 4) + عمود مرحلة في projects
-- + سلة studio-assets الخاصة.
--
-- التطبيق: من لوحة Supabase → SQL Editor (أو supabase db push).
-- يُطبَّق مع ترحيل مشكاة اليومية 20261121222300_mishkat_daily.sql —
-- انظر supabase/seed/studio_apply_instructions.md (الأعداد المتوقعة:
-- studio_jobs 0، وai_generations 0 بعد التطبيق).
--
-- حدود صريحة:
-- - التوليد متزامن داخل أداة generate_video؛ هذان الجدولان يسجّلان
--   النتيجة المنتهية فقط، ولا يحرّكان تنفيذاً غير متزامن بعد.
-- - التكلفة الفعلية في ai_generations.cost_usd = 0 حتى تُقاس من المزود.
-- - لا update/delete من العميل إلا إلغاء المهمة: سياسة UPDATE الوحيدة
--   تشترط status = 'cancelled'، ولا توجد سياسة DELETE أبداً.

create table if not exists public.studio_jobs (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null,
    type text not null default 'video' check (type in ('video', 'image', 'voice')),
    status text not null default 'running'
        check (status in ('running', 'completed', 'failed', 'cancelled')),
    progress real not null default 0 check (progress >= 0 and progress <= 1),
    prompt text not null default '',
    file_path text null,
    error text null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_studio_jobs_user_created
    on public.studio_jobs (user_id, created_at desc);

create table if not exists public.ai_generations (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null,
    type text not null check (type in ('image', 'video', 'voice', 'script')),
    provider text not null default '',
    prompt text null,
    file_path text null,
    -- التكلفة الفعلية 0 حتى تُقاس من المزود (خارج نطاق الدفعة 4).
    cost_usd double precision not null default 0 check (cost_usd >= 0),
    created_at timestamptz not null default now()
);

create index if not exists idx_ai_generations_user_created
    on public.ai_generations (user_id, created_at desc);

-- عمود مرحلة المشروع (آخر مرحلة وصلها المشروع، مثل lastScreen المحلية).
alter table public.projects
    add column if not exists stage text null;

alter table public.studio_jobs enable row level security;
alter table public.ai_generations enable row level security;

grant select, insert on public.studio_jobs to authenticated;
grant select, insert on public.ai_generations to authenticated;

-- studio_jobs: المالك فقط — قراءة وإدراج، وتحديث للإلغاء فقط، وبلا حذف.
drop policy if exists studio_jobs_select_own on public.studio_jobs;
create policy studio_jobs_select_own
on public.studio_jobs
for select
to authenticated
using (user_id = auth.uid());

drop policy if exists studio_jobs_insert_own on public.studio_jobs;
create policy studio_jobs_insert_own
on public.studio_jobs
for insert
to authenticated
with check (user_id = auth.uid());

drop policy if exists studio_jobs_cancel_own on public.studio_jobs;
create policy studio_jobs_cancel_own
on public.studio_jobs
for update
to authenticated
using (user_id = auth.uid())
with check (user_id = auth.uid() and status = 'cancelled');

-- ai_generations: المالك فقط — قراءة وإدراج، وبلا تحديث ولا حذف.
drop policy if exists ai_generations_select_own on public.ai_generations;
create policy ai_generations_select_own
on public.ai_generations
for select
to authenticated
using (user_id = auth.uid());

drop policy if exists ai_generations_insert_own on public.ai_generations;
create policy ai_generations_insert_own
on public.ai_generations
for insert
to authenticated
with check (user_id = auth.uid());

-- سلة studio-assets الخاصة — على نمط project-recordings:
-- ملفات تحت <auth.uid()>/... فقط، وبلا قراءة عامة.
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
    'studio-assets',
    'studio-assets',
    false,
    104857600,
    ARRAY['image/jpeg', 'image/png', 'image/webp', 'video/mp4', 'audio/mpeg', 'audio/mp4', 'audio/x-m4a', 'audio/wav', 'audio/ogg']
)
ON CONFLICT (id) DO UPDATE SET
    public = false,
    file_size_limit = EXCLUDED.file_size_limit,
    allowed_mime_types = EXCLUDED.allowed_mime_types;

DROP POLICY IF EXISTS "qabas_studio_assets_select_own" ON storage.objects;
CREATE POLICY "qabas_studio_assets_select_own"
    ON storage.objects FOR SELECT TO authenticated
    USING (
        bucket_id = 'studio-assets'
        AND (storage.foldername(name))[1] = (select auth.uid())::text
    );

DROP POLICY IF EXISTS "qabas_studio_assets_insert_own" ON storage.objects;
CREATE POLICY "qabas_studio_assets_insert_own"
    ON storage.objects FOR INSERT TO authenticated
    WITH CHECK (
        bucket_id = 'studio-assets'
        AND (storage.foldername(name))[1] = (select auth.uid())::text
    );

DROP POLICY IF EXISTS "qabas_studio_assets_update_own" ON storage.objects;
CREATE POLICY "qabas_studio_assets_update_own"
    ON storage.objects FOR UPDATE TO authenticated
    USING (
        bucket_id = 'studio-assets'
        AND (storage.foldername(name))[1] = (select auth.uid())::text
    )
    WITH CHECK (
        bucket_id = 'studio-assets'
        AND (storage.foldername(name))[1] = (select auth.uid())::text
    );

DROP POLICY IF EXISTS "qabas_studio_assets_delete_own" ON storage.objects;
CREATE POLICY "qabas_studio_assets_delete_own"
    ON storage.objects FOR DELETE TO authenticated
    USING (
        bucket_id = 'studio-assets'
        AND (storage.foldername(name))[1] = (select auth.uid())::text
    );
