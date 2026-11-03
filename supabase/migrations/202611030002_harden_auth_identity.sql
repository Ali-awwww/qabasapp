-- Harden account identity allocation for concurrent registrations.
-- Existing account numbers are preserved; the sequence is initialized above the current maximum.
begin;

create sequence if not exists public.qabas_account_number_seq
    as bigint
    start with 1
    increment by 1
    minvalue 1
    no cycle;

do $$
declare
    current_max bigint;
begin
    select coalesce(
        max(nullif(regexp_replace(account_number, '^#?QBS-', ''), '')::bigint),
        0
    )
    into current_max
    from public.users
    where account_number ~ '^#?QBS-[0-9]+$';

    if current_max > 0 then
        perform setval('public.qabas_account_number_seq', current_max, true);
    end if;
end;
$$;

create or replace function public.get_next_account_sequence()
returns integer
language sql
security definer
set search_path = pg_catalog, public
as $$
    select nextval('public.qabas_account_number_seq')::integer;
$$;

revoke all on function public.get_next_account_sequence() from public, anon;
grant execute on function public.get_next_account_sequence() to authenticated;

-- Enforce case-insensitive username uniqueness at the database boundary.
do $$
begin
    if exists (
        select lower(custom_id)
        from public.users
        where custom_id is not null and btrim(custom_id) <> ''
        group by lower(custom_id)
        having count(*) > 1
    ) then
        raise exception 'Duplicate custom_id values exist in public.users; resolve them before applying the authentication migration.';
    end if;
end;
$$;

create unique index if not exists users_custom_id_lower_uidx
    on public.users (lower(custom_id))
    where custom_id is not null and btrim(custom_id) <> '';

commit;
