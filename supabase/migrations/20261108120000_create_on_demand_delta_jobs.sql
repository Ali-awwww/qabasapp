-- On-demand XDelta job queue metadata.
-- Jobs are created/updated by trusted Edge Functions using the service role.
-- Authenticated clients can only read their own job status.

create table if not exists public.app_update_delta_jobs (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  base_version_code bigint not null check (base_version_code > 0),
  base_sha256_hash text not null check (base_sha256_hash ~ '^[a-fA-F0-9]{64}$'),
  target_version_code bigint not null check (target_version_code > 0),
  target_sha256_hash text not null check (target_sha256_hash ~ '^[a-fA-F0-9]{64}$'),
  status text not null default 'queued'
    check (status in ('queued', 'building', 'ready', 'failed')),
  github_run_id bigint,
  delta_url text,
  delta_size_bytes bigint check (delta_size_bytes is null or delta_size_bytes > 0),
  delta_sha256_hash text check (delta_sha256_hash is null or delta_sha256_hash ~ '^[a-fA-F0-9]{64}$'),
  error_code text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  expires_at timestamptz,
  unique (user_id, base_sha256_hash, target_sha256_hash)
);

create index if not exists app_update_delta_jobs_user_created_idx
  on public.app_update_delta_jobs (user_id, created_at desc);

create index if not exists app_update_delta_jobs_status_created_idx
  on public.app_update_delta_jobs (status, created_at);

alter table public.app_update_delta_jobs enable row level security;

revoke all on public.app_update_delta_jobs from anon, authenticated;
grant select on public.app_update_delta_jobs to authenticated;

drop policy if exists "Users read their own delta jobs" on public.app_update_delta_jobs;
create policy "Users read their own delta jobs"
  on public.app_update_delta_jobs
  for select
  to authenticated
  using ((select auth.uid()) = user_id);

-- No client INSERT/UPDATE/DELETE policies: service-role Edge Functions own the queue.
