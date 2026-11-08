-- Shared queue/cache keyed by the exact approved APK content digests.
-- Only trusted Edge Functions (service role) may access this table.

create table if not exists public.app_update_delta_cache (
  base_sha256_hash text not null check (base_sha256_hash ~ '^[a-fA-F0-9]{64}$'),
  target_sha256_hash text not null check (target_sha256_hash ~ '^[a-fA-F0-9]{64}$'),
  status text not null default 'building'
    check (status in ('building', 'ready', 'failed')),
  job_id uuid references public.app_update_delta_jobs(id) on delete set null,
  delta_url text check (delta_url is null or delta_url ~ '^https://github.com/Ali-awwww/qabasapp/releases/download/'),
  delta_size_bytes bigint check (delta_size_bytes is null or delta_size_bytes > 0),
  delta_sha256_hash text check (delta_sha256_hash is null or delta_sha256_hash ~ '^[a-fA-F0-9]{64}$'),
  created_at timestamptz not null default now(),
  expires_at timestamptz not null,
  primary key (base_sha256_hash, target_sha256_hash),
  check (
    status <> 'ready' or
    (delta_url is not null and delta_size_bytes is not null and delta_sha256_hash is not null)
  )
);

alter table public.app_update_delta_cache enable row level security;
revoke all on public.app_update_delta_cache from anon, authenticated;
