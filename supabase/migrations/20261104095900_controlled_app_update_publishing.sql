alter table public.app_updates
  add column if not exists github_release_url text,
  add column if not exists release_tag text,
  add column if not exists asset_name text;

create unique index if not exists app_updates_single_active_idx
  on public.app_updates ((is_active))
  where is_active is true;

create table if not exists public.app_update_audit (
  id bigint generated always as identity primary key,
  actor_id uuid not null references auth.users(id) on delete restrict,
  actor_role text not null,
  action text not null check (action in ('published', 'paused', 'activated')),
  old_version_code integer,
  new_version_code integer,
  version_name text,
  is_mandatory boolean not null default false,
  created_at timestamptz not null default now()
);

alter table public.app_update_audit enable row level security;
revoke all on public.app_update_audit from anon, authenticated;
grant select on public.app_update_audit to authenticated;

drop policy if exists app_update_audit_developer_read on public.app_update_audit;
create policy app_update_audit_developer_read
  on public.app_update_audit
  for select
  to authenticated
  using (
    lower(coalesce((select auth.jwt() -> 'app_metadata' ->> 'role'), ''))
      in ('developer', 'admin', 'owner', 'مطور', 'مكور')
  );

grant select on public.app_updates to anon, authenticated;
revoke insert, update, delete on public.app_updates from anon, authenticated;

create schema if not exists private;
revoke all on schema private from public, anon;
grant usage on schema private to authenticated;

create or replace function private.publish_qabas_app_update(
  p_version_name text,
  p_version_code integer,
  p_download_url text,
  p_release_notes text,
  p_file_size_bytes bigint,
  p_sha256_hash text,
  p_is_mandatory boolean,
  p_github_release_url text,
  p_release_tag text,
  p_asset_name text
)
returns public.app_updates
language plpgsql
security definer
set search_path = pg_catalog
as $$
declare
  v_actor uuid := auth.uid();
  v_role text := lower(coalesce(auth.jwt() -> 'app_metadata' ->> 'role', ''));
  v_old_version_code integer;
  v_new public.app_updates;
begin
  if v_actor is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;
  if v_role not in ('developer', 'admin', 'owner', 'مطور', 'مكور') then
    raise exception 'Verified developer role required' using errcode = '42501';
  end if;
  if p_version_name is null or length(trim(p_version_name)) = 0
     or p_version_code is null or p_version_code <= 0 then
    raise exception 'Invalid version metadata' using errcode = '22023';
  end if;
  if p_download_url is null
     or p_download_url !~ '^https://github[.]com/Ali-awwww/qabasapp/releases/download/[^[:space:]]+[.]apk$' then
    raise exception 'APK URL must point to a Qabas GitHub release asset' using errcode = '22023';
  end if;
  if p_github_release_url is null
     or p_github_release_url !~ '^https://github[.]com/Ali-awwww/qabasapp/releases/tag/[^[:space:]]+$' then
    raise exception 'Invalid GitHub release URL' using errcode = '22023';
  end if;
  if p_file_size_bytes is null or p_file_size_bytes <= 0 then
    raise exception 'Invalid APK file size' using errcode = '22023';
  end if;
  if p_sha256_hash is not null and p_sha256_hash <> ''
     and p_sha256_hash !~ '^[0-9A-Fa-f]{64}$' then
    raise exception 'Invalid SHA-256 digest' using errcode = '22023';
  end if;
  if p_asset_name is null or lower(p_asset_name) not like '%.apk' then
    raise exception 'A valid APK asset is required' using errcode = '22023';
  end if;

  perform pg_advisory_xact_lock(hashtext('qabas-app-update-publish'));
  select version_code into v_old_version_code
    from public.app_updates
    where is_active is true
    order by version_code desc
    limit 1
    for update;

  if exists (
    select 1 from public.app_updates
    where version_code >= p_version_code
  ) then
    raise exception 'Version code must be greater than every previously published build' using errcode = '22023';
  end if;

  update public.app_updates set is_active = false where is_active is true;

  insert into public.app_updates (
    version_code, version_name, title, release_notes, download_url,
    sha256_hash, is_mandatory, is_active, file_size_bytes, released_at,
    github_release_url, release_tag, asset_name
  ) values (
    p_version_code, trim(p_version_name), 'v' || trim(p_version_name),
    coalesce(p_release_notes, ''), p_download_url,
    coalesce(p_sha256_hash, ''), coalesce(p_is_mandatory, false), true,
    p_file_size_bytes, now(), p_github_release_url, p_release_tag, p_asset_name
  )
  returning * into v_new;

  insert into public.app_update_audit (
    actor_id, actor_role, action, old_version_code, new_version_code,
    version_name, is_mandatory
  ) values (
    v_actor, v_role, 'published', v_old_version_code, p_version_code,
    trim(p_version_name), coalesce(p_is_mandatory, false)
  );

  return v_new;
end;
$$;

create or replace function private.set_qabas_app_update_active(
  p_update_id text,
  p_is_active boolean
)
returns boolean
language plpgsql
security definer
set search_path = pg_catalog
as $$
declare
  v_actor uuid := auth.uid();
  v_role text := lower(coalesce(auth.jwt() -> 'app_metadata' ->> 'role', ''));
  v_target public.app_updates;
  v_old_version_code integer;
begin
  if v_actor is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;
  if v_role not in ('developer', 'admin', 'owner', 'مطور', 'مكور') then
    raise exception 'Verified developer role required' using errcode = '42501';
  end if;

  perform pg_advisory_xact_lock(hashtext('qabas-app-update-publish'));
  select version_code into v_old_version_code
    from public.app_updates where is_active is true
    order by version_code desc limit 1 for update;

  if coalesce(p_is_active, false) then
    select * into v_target from public.app_updates where id = p_update_id;
    if not found then
      raise exception 'Update release not found' using errcode = '22023';
    end if;
    if v_target.version_code <> (select max(version_code) from public.app_updates) then
      raise exception 'Only the newest published build can be activated' using errcode = '22023';
    end if;
    update public.app_updates set is_active = false where is_active is true;
    update public.app_updates set is_active = true where id = p_update_id;
    insert into public.app_update_audit (
      actor_id, actor_role, action, old_version_code, new_version_code,
      version_name, is_mandatory
    ) values (
      v_actor, v_role, 'activated', v_old_version_code, v_target.version_code,
      v_target.version_name, v_target.is_mandatory
    );
  else
    update public.app_updates set is_active = false where is_active is true;
    insert into public.app_update_audit (
      actor_id, actor_role, action, old_version_code, new_version_code,
      version_name, is_mandatory
    ) values (
      v_actor, v_role, 'paused', v_old_version_code, null, null, false
    );
  end if;
  return true;
end;
$$;

revoke all on function private.publish_qabas_app_update(text, integer, text, text, bigint, text, boolean, text, text, text) from public, anon;
revoke all on function private.set_qabas_app_update_active(text, boolean) from public, anon;
grant execute on function private.publish_qabas_app_update(text, integer, text, text, bigint, text, boolean, text, text, text) to authenticated;
grant execute on function private.set_qabas_app_update_active(text, boolean) to authenticated;

create or replace function public.publish_qabas_app_update(
  p_version_name text,
  p_version_code integer,
  p_download_url text,
  p_release_notes text,
  p_file_size_bytes bigint,
  p_sha256_hash text,
  p_is_mandatory boolean,
  p_github_release_url text,
  p_release_tag text,
  p_asset_name text
)
returns public.app_updates
language sql
security invoker
set search_path = pg_catalog
as $$
  select private.publish_qabas_app_update(
    p_version_name, p_version_code, p_download_url, p_release_notes,
    p_file_size_bytes, p_sha256_hash, p_is_mandatory,
    p_github_release_url, p_release_tag, p_asset_name
  );
$$;

create or replace function public.set_qabas_app_update_active(
  p_update_id text,
  p_is_active boolean
)
returns boolean
language sql
security invoker
set search_path = pg_catalog
as $$
  select private.set_qabas_app_update_active(p_update_id, p_is_active);
$$;

revoke all on function public.publish_qabas_app_update(text, integer, text, text, bigint, text, boolean, text, text, text) from public, anon;
revoke all on function public.set_qabas_app_update_active(text, boolean) from public, anon;
grant execute on function public.publish_qabas_app_update(text, integer, text, text, bigint, text, boolean, text, text, text) to authenticated;
grant execute on function public.set_qabas_app_update_active(text, boolean) to authenticated;
