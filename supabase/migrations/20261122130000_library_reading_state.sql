-- Library reading state (Qabas Batch 6, Priority 2): per-book last position,
-- progress percentage and book-level bookmark, synced from the device and
-- owned strictly by the signed-in user. The app stores the same state
-- locally first (SharedPreferences `qabas_library_reading`) and works fully
-- without this table: reads return empty and syncs fail silently until the
-- migration is applied. Never applied live from the VM — apply from the
-- Supabase dashboard SQL Editor (see supabase/seed/library_reading_state_APPLY.md).
create table if not exists public.library_reading_state (
    user_id uuid not null references auth.users(id) on delete cascade,
    book_id text not null,
    last_chapter integer not null default 0 check (last_chapter >= 0),
    last_section integer not null default 0 check (last_section >= 0),
    progress integer not null default 0 check (progress >= 0 and progress <= 100),
    bookmarked boolean not null default false,
    updated_at timestamptz not null default now(),
    constraint library_reading_state_pkey primary key (user_id, book_id)
);

create index if not exists library_reading_state_user_updated_idx
    on public.library_reading_state (user_id, updated_at desc);

alter table public.library_reading_state enable row level security;

drop policy if exists library_reading_state_select_own on public.library_reading_state;
create policy library_reading_state_select_own
    on public.library_reading_state for select to authenticated
    using ((select auth.uid()) = user_id);

drop policy if exists library_reading_state_insert_own on public.library_reading_state;
create policy library_reading_state_insert_own
    on public.library_reading_state for insert to authenticated
    with check ((select auth.uid()) = user_id);

drop policy if exists library_reading_state_update_own on public.library_reading_state;
create policy library_reading_state_update_own
    on public.library_reading_state for update to authenticated
    using ((select auth.uid()) = user_id)
    with check ((select auth.uid()) = user_id);

drop policy if exists library_reading_state_delete_own on public.library_reading_state;
create policy library_reading_state_delete_own
    on public.library_reading_state for delete to authenticated
    using ((select auth.uid()) = user_id);

revoke all on table public.library_reading_state from anon, public;
grant select, insert, update, delete on table public.library_reading_state to authenticated;
