create index if not exists app_update_audit_actor_id_created_at_idx
  on public.app_update_audit (actor_id, created_at desc);
