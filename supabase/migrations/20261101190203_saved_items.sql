-- Saved user content is owned by the active Supabase Auth user.
create table if not exists public.saved_items (
    user_id uuid not null references auth.users(id) on delete cascade,
    id text not null,
    item_type text not null
        check (item_type in ('QURAN_AYAH', 'INSPIRATION', 'DHIKR')),
    title text not null,
    content text not null,
    reference text not null default '',
    tags jsonb not null default '[]'::jsonb
        check (jsonb_typeof(tags) = 'array'),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint saved_items_pkey primary key (user_id, id)
);

create index if not exists saved_items_user_created_idx
    on public.saved_items (user_id, created_at desc);

alter table public.saved_items enable row level security;

drop policy if exists saved_items_select_own on public.saved_items;
create policy saved_items_select_own
    on public.saved_items for select to authenticated
    using ((select auth.uid()) = user_id);

drop policy if exists saved_items_insert_own on public.saved_items;
create policy saved_items_insert_own
    on public.saved_items for insert to authenticated
    with check ((select auth.uid()) = user_id);

drop policy if exists saved_items_update_own on public.saved_items;
create policy saved_items_update_own
    on public.saved_items for update to authenticated
    using ((select auth.uid()) = user_id)
    with check ((select auth.uid()) = user_id);

drop policy if exists saved_items_delete_own on public.saved_items;
create policy saved_items_delete_own
    on public.saved_items for delete to authenticated
    using ((select auth.uid()) = user_id);

revoke all on table public.saved_items from anon, public;
grant select, insert, update, delete on table public.saved_items to authenticated;

do $$
begin
    if exists (select 1 from pg_publication where pubname = 'supabase_realtime')
       and not exists (
           select 1 from pg_publication_tables
           where pubname = 'supabase_realtime'
             and schemaname = 'public'
             and tablename = 'saved_items'
       ) then
        alter publication supabase_realtime add table public.saved_items;
    end if;
end
$$;
