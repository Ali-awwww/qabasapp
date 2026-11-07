-- Private, account-owned adhan uploads. Public studio voices remain in public.azan_voices.
create table if not exists public.user_azan_voices (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users(id) on delete cascade,
    title text not null check (char_length(btrim(title)) between 1 and 120),
    storage_path text not null unique,
    content_type text not null,
    size_bytes bigint not null check (size_bytes > 0 and size_bytes <= 26214400),
    created_at timestamptz not null default now(),
    constraint user_azan_voices_path_owner check (storage_path like user_id::text || '/%')
);

alter table public.user_azan_voices enable row level security;

drop policy if exists user_azan_voices_select_own on public.user_azan_voices;
create policy user_azan_voices_select_own
    on public.user_azan_voices for select to authenticated
    using ((select auth.uid()) = user_id);

drop policy if exists user_azan_voices_insert_own on public.user_azan_voices;
create policy user_azan_voices_insert_own
    on public.user_azan_voices for insert to authenticated
    with check ((select auth.uid()) = user_id);

drop policy if exists user_azan_voices_update_own on public.user_azan_voices;
create policy user_azan_voices_update_own
    on public.user_azan_voices for update to authenticated
    using ((select auth.uid()) = user_id)
    with check ((select auth.uid()) = user_id);

drop policy if exists user_azan_voices_delete_own on public.user_azan_voices;
create policy user_azan_voices_delete_own
    on public.user_azan_voices for delete to authenticated
    using ((select auth.uid()) = user_id);

grant select, insert, update, delete on public.user_azan_voices to authenticated;

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
    'user-azan-private',
    'user-azan-private',
    false,
    26214400,
    array['audio/mpeg','audio/mp3','audio/wav','audio/x-wav','audio/aac','audio/x-m4a','audio/mp4','audio/ogg','audio/webm']
)
on conflict (id) do update
set public = false,
    file_size_limit = 26214400,
    allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists user_azan_storage_select_own on storage.objects;
create policy user_azan_storage_select_own
    on storage.objects for select to authenticated
    using (
        bucket_id = 'user-azan-private'
        and (storage.foldername(name))[1] = (select auth.uid())::text
    );

drop policy if exists user_azan_storage_insert_own on storage.objects;
create policy user_azan_storage_insert_own
    on storage.objects for insert to authenticated
    with check (
        bucket_id = 'user-azan-private'
        and (storage.foldername(name))[1] = (select auth.uid())::text
    );

drop policy if exists user_azan_storage_update_own on storage.objects;
create policy user_azan_storage_update_own
    on storage.objects for update to authenticated
    using (
        bucket_id = 'user-azan-private'
        and (storage.foldername(name))[1] = (select auth.uid())::text
    )
    with check (
        bucket_id = 'user-azan-private'
        and (storage.foldername(name))[1] = (select auth.uid())::text
    );

drop policy if exists user_azan_storage_delete_own on storage.objects;
create policy user_azan_storage_delete_own
    on storage.objects for delete to authenticated
    using (
        bucket_id = 'user-azan-private'
        and (storage.foldername(name))[1] = (select auth.uid())::text
    );
