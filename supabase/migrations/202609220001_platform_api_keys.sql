-- Central vault for platform API credentials.
-- Only the Edge Function service role can read/write this table.
create table if not exists public.platform_api_keys (
  service text primary key check (service ~ '^[a-z0-9_]+$'),
  ciphertext text not null,
  nonce text not null,
  updated_at timestamptz not null default now(),
  updated_by uuid
);

alter table public.platform_api_keys enable row level security;

-- Deliberately no anon/authenticated policies: clients must use the gateway.
revoke all on table public.platform_api_keys from anon, authenticated;
 grant all on table public.platform_api_keys to service_role;

create index if not exists platform_api_keys_updated_at_idx
  on public.platform_api_keys (updated_at desc);
