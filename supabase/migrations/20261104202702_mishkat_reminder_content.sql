create table if not exists public.mishkat_reminder_content (
    id text primary key default 'global' check (id = 'global'),
    quote_text text not null check (char_length(btrim(quote_text)) between 1 and 2000),
    attribution text not null check (char_length(btrim(attribution)) between 1 and 120),
    video_url text null check (video_url is null or (char_length(video_url) <= 2048 and video_url ~ '^https://')),
    is_enabled boolean not null default false,
    updated_by uuid null references auth.users(id) on delete set null,
    updated_at timestamptz not null default now()
);

alter table public.mishkat_reminder_content enable row level security;

grant select on public.mishkat_reminder_content to anon, authenticated;
grant insert, update on public.mishkat_reminder_content to authenticated;

drop policy if exists mishkat_reminder_public_read on public.mishkat_reminder_content;
create policy mishkat_reminder_public_read
on public.mishkat_reminder_content
for select
to anon, authenticated
using (true);

drop policy if exists mishkat_reminder_developer_insert on public.mishkat_reminder_content;
create policy mishkat_reminder_developer_insert
on public.mishkat_reminder_content
for insert
to authenticated
with check (public.qabas_is_support_admin());

drop policy if exists mishkat_reminder_developer_update on public.mishkat_reminder_content;
create policy mishkat_reminder_developer_update
on public.mishkat_reminder_content
for update
to authenticated
using (public.qabas_is_support_admin())
with check (public.qabas_is_support_admin());

insert into public.mishkat_reminder_content
    (id, quote_text, attribution, video_url, is_enabled)
values
    ('global',
     'لذا هو دينٌ غالٍ، أتاك على أشلاءِ ناس، فلا تكن محطَّ نحس فتفرّط فيه',
     'الشيخ سمير مصطفى',
     null,
     false)
on conflict (id) do nothing;
