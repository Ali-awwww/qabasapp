-- Reels hardening: durable Supabase sessions are paired with server-side
-- ownership checks, developer-role claims, and an atomic one-post daily quota.

begin;

-- Keep the database compatible with the complete ReelRow payload used by the app.
alter table public.reels
    add column if not exists user_name text default 'صانع محتوى قبس',
    add column if not exists user_avatar text,
    add column if not exists publish_at timestamptz,
    add column if not exists expiry_at timestamptz,
    add column if not exists quality text not null default 'high',
    add column if not exists ai_analysis text,
    add column if not exists publish_type text not null default 'normal',
    add column if not exists is_silent_seed boolean not null default false,
    add column if not exists is_featured_by_dev boolean not null default false;

-- Remove every prior policy on these tables, including permissive policies
-- created by older master-schema scripts and applet migrations.
do $$
declare
    p record;
begin
    for p in
        select schemaname, tablename, policyname
          from pg_policies
         where schemaname = 'public'
           and tablename in ('reels', 'reel_likes', 'reel_comments', 'reel_bookmarks', 'reel_history')
    loop
        execute format('drop policy if exists %I on %I.%I', p.policyname, p.schemaname, p.tablename);
    end loop;
end $$;

alter table public.reels enable row level security;
alter table public.reel_likes enable row level security;
alter table public.reel_comments enable row level security;
alter table public.reel_bookmarks enable row level security;
alter table public.reel_history enable row level security;

create policy reels_read_active
on public.reels for select
to anon, authenticated
using (is_active = true or user_id::text = auth.uid()::text);

create policy reels_insert_owner
on public.reels for insert
to authenticated
with check (
    auth.uid() is not null
    and user_id::text = auth.uid()::text
    and (
        coalesce(auth.jwt()->'app_metadata'->>'role', '') in ('developer', 'admin', 'owner')
        or (
            coalesce(publish_type, 'normal') = 'normal'
            and not coalesce(is_silent_seed, false)
            and not coalesce(is_featured_by_dev, false)
            and not (coalesce(tags, '[]'::jsonb) ? 'silent_seed')
        )
    )
);

create policy reels_update_owner_or_staff
on public.reels for update
to authenticated
using (
    user_id::text = auth.uid()::text
    or coalesce(auth.jwt()->'app_metadata'->>'role', '') in ('developer', 'admin', 'owner')
)
with check (
    user_id::text = auth.uid()::text
    or coalesce(auth.jwt()->'app_metadata'->>'role', '') in ('developer', 'admin', 'owner')
);

create policy reels_delete_owner_or_staff
on public.reels for delete
to authenticated
using (
    user_id::text = auth.uid()::text
    or coalesce(auth.jwt()->'app_metadata'->>'role', '') in ('developer', 'admin', 'owner')
);

create policy reel_likes_read_public
on public.reel_likes for select to anon, authenticated using (true);
create policy reel_likes_insert_self
on public.reel_likes for insert to authenticated
with check (auth.uid() is not null and user_id::text = auth.uid()::text);
create policy reel_likes_delete_self
on public.reel_likes for delete to authenticated
using (auth.uid() is not null and user_id::text = auth.uid()::text);

create policy reel_comments_read_public
on public.reel_comments for select to anon, authenticated using (true);
create policy reel_comments_insert_self
on public.reel_comments for insert to authenticated
with check (auth.uid() is not null and user_id::text = auth.uid()::text);
create policy reel_comments_delete_self_or_staff
on public.reel_comments for delete to authenticated
using (
    user_id::text = auth.uid()::text
    or coalesce(auth.jwt()->'app_metadata'->>'role', '') in ('developer', 'admin', 'owner')
);

create policy reel_bookmarks_read_self
on public.reel_bookmarks for select to authenticated
using (auth.uid() is not null and user_id::text = auth.uid()::text);
create policy reel_bookmarks_insert_self
on public.reel_bookmarks for insert to authenticated
with check (auth.uid() is not null and user_id::text = auth.uid()::text);
create policy reel_bookmarks_delete_self
on public.reel_bookmarks for delete to authenticated
using (auth.uid() is not null and user_id::text = auth.uid()::text);

create policy reel_history_read_self
on public.reel_history for select to authenticated
using (auth.uid() is not null and user_id::text = auth.uid()::text);
create policy reel_history_insert_self
on public.reel_history for insert to authenticated
with check (auth.uid() is not null and user_id::text = auth.uid()::text);

-- Serialize same-user publishes so simultaneous requests cannot exceed one post/day.
create or replace function public.enforce_reel_daily_publish_limit()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    caller_id text;
    caller_role text;
    posts_today integer;
begin
    caller_id := auth.uid()::text;
    caller_role := coalesce(auth.jwt()->'app_metadata'->>'role', '');

    if caller_id is null then
        raise exception 'reels_auth_required' using errcode = '42501';
    end if;

    if new.user_id::text <> caller_id then
        raise exception 'reels_owner_mismatch' using errcode = '42501';
    end if;

    if caller_role in ('developer', 'admin', 'owner') then
        return new;
    end if;

    perform pg_advisory_xact_lock(hashtextextended(caller_id, 0));

    select count(*)::integer
      into posts_today
      from public.reels r
     where r.user_id::text = caller_id
       and r.created_at >= date_trunc('day', now())
       and r.created_at < date_trunc('day', now()) + interval '1 day';

    if posts_today >= 1 then
        raise exception 'reels_daily_limit_exceeded' using errcode = 'P0001';
    end if;

    return new;
end;
$$;

drop trigger if exists trg_enforce_reel_daily_publish_limit on public.reels;
create trigger trg_enforce_reel_daily_publish_limit
before insert on public.reels
for each row execute function public.enforce_reel_daily_publish_limit();

-- Public playback is allowed, but uploads into the reels bucket must be under
-- videos/<authenticated UID>/... or thumbnails/<authenticated UID>/...
drop policy if exists "Allow public upload on reels storage" on storage.objects;
drop policy if exists "Allow public read on reels storage" on storage.objects;
drop policy if exists reels_public_read on storage.objects;
drop policy if exists reels_owner_upload on storage.objects;

create policy reels_public_read
on storage.objects for select to anon, authenticated
using (bucket_id = 'reels');

create policy reels_owner_upload
on storage.objects for insert to authenticated
with check (
    bucket_id = 'reels'
    and auth.uid() is not null
    and split_part(name, '/', 2) = auth.uid()::text
);

revoke insert, update, delete on public.reels from anon;
revoke insert, update, delete on public.reel_likes from anon;
revoke insert, update, delete on public.reel_comments from anon;
revoke all on public.reel_bookmarks from anon;
revoke all on public.reel_history from anon;

grant select on public.reels, public.reel_likes, public.reel_comments to anon, authenticated;
grant insert, update, delete on public.reels to authenticated;
grant select, insert, delete on public.reel_likes to authenticated;
grant select, insert, delete on public.reel_comments to authenticated;
grant select, insert, delete on public.reel_bookmarks to authenticated;
grant select, insert on public.reel_history to authenticated;

commit;
