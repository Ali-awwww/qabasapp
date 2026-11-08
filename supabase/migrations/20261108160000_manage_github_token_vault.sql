-- Keep the XDelta GitHub credential as one active server-side secret.
-- The advisory transaction lock makes concurrent owner saves atomic without
-- exposing the credential to authenticated/anonymous database roles.
create or replace function public.replace_github_token(p_secret_value text)
returns void
language plpgsql
security invoker
set search_path = public, pg_temp
as $$
begin
  if p_secret_value is null or length(btrim(p_secret_value)) < 20
     or length(btrim(p_secret_value)) > 512 then
    raise exception 'invalid_github_token';
  end if;

  perform pg_advisory_xact_lock(hashtext('public.app_secrets.github_token'));

  update public.app_secrets
     set is_active = false,
         updated_at = now()
   where secret_key = 'github_token';

  insert into public.app_secrets (secret_key, secret_value, is_active, created_at, updated_at)
  values ('github_token', btrim(p_secret_value), true, now(), now());
end;
$$;

revoke all on function public.replace_github_token(text) from public, anon, authenticated;
grant execute on function public.replace_github_token(text) to service_role;
