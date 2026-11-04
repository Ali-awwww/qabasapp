alter table public.app_updates
  alter column release_notes set default '',
  alter column release_notes set not null,
  alter column sha256_hash set default '',
  alter column sha256_hash set not null,
  alter column is_mandatory set default false,
  alter column is_mandatory set not null,
  alter column is_active set default true,
  alter column is_active set not null,
  alter column file_size_bytes set default 0,
  alter column file_size_bytes set not null;

create index if not exists app_update_audit_actor_id_created_at_idx
  on public.app_update_audit (actor_id, created_at desc);
