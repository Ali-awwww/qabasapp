alter table public.app_updates
  add column if not exists delta_url text,
  add column if not exists delta_size_bytes bigint not null default 0,
  add column if not exists delta_sha256_hash text,
  add column if not exists base_version_code integer,
  add column if not exists base_sha256_hash text;

alter table public.app_updates
  drop constraint if exists app_updates_delta_size_nonnegative,
  add constraint app_updates_delta_size_nonnegative check (delta_size_bytes >= 0);

drop function if exists public.publish_qabas_app_update(text, integer, text, text, bigint, text, boolean, text, text, text);
drop function if exists private.publish_qabas_app_update(text, integer, text, text, bigint, text, boolean, text, text, text);

create or replace function private.publish_qabas_app_update(
  p_version_name text, p_version_code integer, p_download_url text, p_release_notes text,
  p_file_size_bytes bigint, p_sha256_hash text, p_is_mandatory boolean,
  p_github_release_url text, p_release_tag text, p_asset_name text,
  p_delta_url text, p_delta_size_bytes bigint, p_delta_sha256_hash text,
  p_base_version_code integer, p_base_sha256_hash text
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
    raise exception 'Invalid APK SHA-256 digest' using errcode = '22023';
  end if;
  if p_delta_url is not null and p_delta_url <> '' then
    if p_delta_url !~ '^https://github[.]com/Ali-awwww/qabasapp/releases/download/[^[:space:]]+[.]delta$'
       or p_delta_size_bytes is null or p_delta_size_bytes <= 0
       or p_delta_size_bytes >= p_file_size_bytes
       or p_delta_sha256_hash is null or p_delta_sha256_hash !~ '^[0-9A-Fa-f]{64}$'
       or p_base_version_code is null or p_base_version_code <= 0
       or p_base_sha256_hash is null or p_base_sha256_hash !~ '^[0-9A-Fa-f]{64}$' then
      raise exception 'Invalid or incomplete delta metadata' using errcode = '22023';
    end if;
  elsif coalesce(p_delta_size_bytes, 0) <> 0
     or p_delta_sha256_hash is not null
     or p_base_version_code is not null
     or p_base_sha256_hash is not null then
    raise exception 'Delta metadata must be provided as a complete set' using errcode = '22023';
  end if;
  if p_asset_name is null or lower(p_asset_name) not like '%.apk' then
    raise exception 'A valid APK asset is required' using errcode = '22023';
  end if;

  perform pg_advisory_xact_lock(hashtext('qabas-app-update-publish'));
  select version_code into v_old_version_code
    from public.app_updates where is_active is true
    order by version_code desc limit 1 for update;

  if exists (select 1 from public.app_updates where version_code >= p_version_code) then
    raise exception 'Version code must be greater than every previously published build' using errcode = '22023';
  end if;

  update public.app_updates set is_active = false where is_active is true;
  insert into public.app_updates (
    version_code, version_name, title, release_notes, download_url,
    sha256_hash, is_mandatory, is_active, file_size_bytes, released_at,
    github_release_url, release_tag, asset_name,
    delta_url, delta_size_bytes, delta_sha256_hash, base_version_code, base_sha256_hash
  ) values (
    p_version_code, trim(p_version_name), 'v' || trim(p_version_name),
    coalesce(p_release_notes, ''), p_download_url,
    coalesce(p_sha256_hash, ''), coalesce(p_is_mandatory, false), true,
    p_file_size_bytes, now(), p_github_release_url, p_release_tag, p_asset_name,
    nullif(p_delta_url, ''), coalesce(p_delta_size_bytes, 0), nullif(p_delta_sha256_hash, ''),
    p_base_version_code, nullif(p_base_sha256_hash, '')
  ) returning * into v_new;

  insert into public.app_update_audit (
    actor_id, actor_role, action, old_version_code, new_version_code, version_name, is_mandatory
  ) values (
    v_actor, v_role, 'published', v_old_version_code, p_version_code,
    trim(p_version_name), coalesce(p_is_mandatory, false)
  );
  return v_new;
end;
$$;

create or replace function public.publish_qabas_app_update(
  p_version_name text, p_version_code integer, p_download_url text, p_release_notes text,
  p_file_size_bytes bigint, p_sha256_hash text, p_is_mandatory boolean,
  p_github_release_url text, p_release_tag text, p_asset_name text,
  p_delta_url text, p_delta_size_bytes bigint, p_delta_sha256_hash text,
  p_base_version_code integer, p_base_sha256_hash text
)
returns public.app_updates
language sql
set search_path = pg_catalog
as $$
  select private.publish_qabas_app_update(
    p_version_name, p_version_code, p_download_url, p_release_notes,
    p_file_size_bytes, p_sha256_hash, p_is_mandatory,
    p_github_release_url, p_release_tag, p_asset_name,
    p_delta_url, p_delta_size_bytes, p_delta_sha256_hash,
    p_base_version_code, p_base_sha256_hash
  );
$$;

revoke all on function private.publish_qabas_app_update(text, integer, text, text, bigint, text, boolean, text, text, text, text, bigint, text, integer, text) from public, anon;
grant execute on function private.publish_qabas_app_update(text, integer, text, text, bigint, text, boolean, text, text, text, text, bigint, text, integer, text) to authenticated;
revoke all on function public.publish_qabas_app_update(text, integer, text, text, bigint, text, boolean, text, text, text, text, bigint, text, integer, text) from public, anon;
grant execute on function public.publish_qabas_app_update(text, integer, text, text, bigint, text, boolean, text, text, text, text, bigint, text, integer, text) to authenticated;
