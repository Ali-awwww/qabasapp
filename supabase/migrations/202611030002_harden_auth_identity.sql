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

create or replace function public.ensure_my_account_number()
returns text
language plpgsql
security definer
set search_path = pg_catalog, public
as $
declare
    authenticated_uid text := auth.uid()::text;
    current_number text;
begin
    if authenticated_uid is null then
        raise exception 'Authentication is required';
    end if;

    select account_number
      into current_number
      from public.users
     where id = authenticated_uid
     for update;

    if not found then
        raise exception 'Authenticated user profile is missing';
    end if;

    if current_number is not null and btrim(current_number) <> '' then
        return current_number;
    end if;

    current_number := '#QBS-' || lpad(nextval('public.qabas_account_number_seq')::text, 4, '0');

    update public.users
       set account_number = current_number
     where id = authenticated_uid;

    return current_number;
end;
$;

revoke all on function public.ensure_my_account_number() from public, anon;
grant execute on function public.ensure_my_account_number() to authenticated;

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

-- Existing owner-scoped RLS policies remain authoritative; authenticated users
-- need table privileges for their own profile INSERT/UPDATE operations.
grant select, insert, update on table public.users to authenticated;

commit;
