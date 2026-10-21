-- ==============================================================================
-- Migration: User identity hardening + single active session
-- 1) Deduplicates user rows that were created for the same email by old logins.
-- 2) Adds session columns used to enforce one active session per account.
-- ==============================================================================

alter table if exists public.users
    add column if not exists active_session_id text,
    add column if not exists session_updated_at timestamptz;

create index if not exists idx_users_active_session on public.users (active_session_id);
create index if not exists idx_users_email_lower on public.users (lower(btrim(email)));

-- Backfill missing custom_id from the email local part (kept stable afterwards)
update public.users
set custom_id = nullif(lower(regexp_replace(split_part(email, '@', 1), '[^a-zA-Z0-9_]', '', 'g')), '')
where (custom_id is null or btrim(custom_id) = '')
  and email is not null
  and btrim(email) <> '';

-- Merge duplicate accounts: keep one eligible row per email (the one with the most
-- complete identity), and hide the rest from the leaderboard without deleting them
-- so no reels/comments lose their author reference.
with ranked as (
    select id,
           row_number() over (
               partition by lower(btrim(email))
               order by
                   (account_number is not null and btrim(account_number) <> '') desc,
                   (custom_id is not null and btrim(custom_id) <> '') desc,
                   created_at asc nulls last,
                   id asc
           ) as rn
    from public.users
    where email is not null and btrim(email) <> ''
)
update public.users u
set is_eligible = false
from ranked r
where u.id = r.id
  and r.rn > 1;

-- Free any session that was active before this migration
update public.users
set active_session_id = null,
    session_updated_at = now()
where active_session_id is not null;
