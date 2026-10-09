-- ==============================================================================
-- Migration: Add custom_id support to public.users table
-- Allows users to sign in using their unique custom handle/ID or their Gmail.
-- ==============================================================================

alter table if exists public.users 
add column if not exists custom_id text;

-- Create case-insensitive index on custom_id for rapid lookups during login
create index if not exists idx_users_custom_id_lower on public.users (lower(custom_id));

-- Backfill existing users with a default custom_id if empty
update public.users
set custom_id = coalesce(
    nullif(lower(regexp_replace(split_part(email, '@', 1), '[^a-zA-Z0-9_]', '', 'g')), ''),
    'qbs_' || substring(id::text from 1 for 6)
)
where custom_id is null and email is not null;
