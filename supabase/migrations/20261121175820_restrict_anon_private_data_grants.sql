-- Restrict anonymous table grants without changing RLS policies or data.
-- Scope: remove direct anon access from private user tables and keep public
-- read-only access for the global Mishkat reminder content.
-- Rollback reference (only after review): restore the previous table grants
-- explicitly, never via a blanket grant-all policy.

REVOKE ALL PRIVILEGES ON TABLE public.user_push_tokens FROM anon;
REVOKE ALL PRIVILEGES ON TABLE public.user_azan_voices FROM anon;

REVOKE ALL PRIVILEGES ON TABLE public.mishkat_reminder_content FROM anon;
GRANT SELECT ON TABLE public.mishkat_reminder_content TO anon;
