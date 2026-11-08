-- Shared delta cache indexed by the exact APK content digests.
-- Only trusted Edge Functions (service role) may access cache metadata.

create table if not exists public.app_update_delta_cache (
  base_sha256_hash text not null check (base_sha256_hash ~ '^[a-fA-F0-9]{64}$'),
  target_sha256_hash text not null check (target_sha256_hash ~ '^[a-fA-F0-9]{64}$'),
  delta_url text not null check (delta_url ~ '^https://github.com/Ali-awwww/qabasapp/releases/download/'),
  delta_size_bytes bigint not null check (delta_size_bytes > 0),
  delta_sha256_hash text not null check (delta_sha256_hash ~ '^[a-fA-F0-9]{64}$'),
  created_at timestamptz not null default now(),
  expires_at timestamptz not null,
  primary key (base_sha256_hash, target_sha256_hash)
);

alter table public.app_update_delta_cache enable row level security;
revoke all on public.app_update_delta_cache from anon, authenticated;
