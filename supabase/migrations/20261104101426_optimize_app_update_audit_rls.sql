drop policy if exists app_update_audit_developer_read on public.app_update_audit;
create policy app_update_audit_developer_read
  on public.app_update_audit
  for select
  to authenticated
  using (
    lower(coalesce(((select auth.jwt()) -> 'app_metadata' ->> 'role'), ''))
      in ('developer', 'admin', 'owner', 'مطور', 'مكور')
  );
