-- محتوى مشكاة اليومية: تذكير واحد جديد كل يوم (آية/حديث/حكمة) يدور 1–30،
-- مع أرشيف للأيام السابقة. يكمل السجل الواحد في mishkat_reminder_content
-- (اليوم 1) ولا يستبدله: التطبيق يرجع للسجل العام عند غياب هذا الجدول.
--
-- التطبيق: من لوحة Supabase → SQL Editor (أو supabase db push)، ثم شغّل
-- البذرة supabase/seed/mishkat_daily_seed.sql (29 صفاً، الأيام 2–30).

create table if not exists public.mishkat_daily (
    id uuid primary key default gen_random_uuid(),
    day_number integer not null unique check (day_number between 1 and 30),
    date date null,
    type text not null check (type in ('ayah', 'hadith', 'wisdom')),
    title text not null check (char_length(btrim(title)) between 1 and 200),
    text text not null check (char_length(btrim(text)) between 1 and 4000),
    source text not null check (char_length(btrim(source)) between 1 and 200),
    lesson_url text null check (lesson_url is null or (char_length(lesson_url) <= 2048 and lesson_url ~ '^https://')),
    is_active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_mishkat_daily_active_day
    on public.mishkat_daily (is_active, day_number);

alter table public.mishkat_daily enable row level security;

grant select on public.mishkat_daily to anon, authenticated;
grant insert, update, delete on public.mishkat_daily to authenticated;

drop policy if exists mishkat_daily_public_read on public.mishkat_daily;
create policy mishkat_daily_public_read
on public.mishkat_daily
for select
to anon, authenticated
using (true);

drop policy if exists mishkat_daily_admin_insert on public.mishkat_daily;
create policy mishkat_daily_admin_insert
on public.mishkat_daily
for insert
to authenticated
with check (public.qabas_is_support_admin());

drop policy if exists mishkat_daily_admin_update on public.mishkat_daily;
create policy mishkat_daily_admin_update
on public.mishkat_daily
for update
to authenticated
using (public.qabas_is_support_admin())
with check (public.qabas_is_support_admin());

drop policy if exists mishkat_daily_admin_delete on public.mishkat_daily;
create policy mishkat_daily_admin_delete
on public.mishkat_daily
for delete
to authenticated
using (public.qabas_is_support_admin());
