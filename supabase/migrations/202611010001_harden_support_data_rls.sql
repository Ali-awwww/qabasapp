-- Harden private support data and align the bug-report schema with the Android client.
-- The 2026-10-09 sync migration reintroduced public SELECT/INSERT policies for chat
-- and unrestricted CRUD for bug reports after the earlier chat RLS hardening.

ALTER TABLE public.dev_bug_reports ADD COLUMN IF NOT EXISTS user_id TEXT;
ALTER TABLE public.dev_chat_messages ADD COLUMN IF NOT EXISTS user_id TEXT;

CREATE INDEX IF NOT EXISTS idx_dev_bug_reports_user_id
    ON public.dev_bug_reports (user_id);

ALTER TABLE public.dev_chat_messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.dev_bug_reports ENABLE ROW LEVEL SECURITY;

CREATE OR REPLACE FUNCTION public.qabas_is_support_admin()
RETURNS boolean
LANGUAGE sql
STABLE
AS $$
    SELECT
        lower(coalesce(auth.jwt() ->> 'email', '')) IN (
            'aliwalead.2007@gmail.com',
            'xman88371@gmail.com'
        )
        OR lower(coalesce(auth.jwt() -> 'app_metadata' ->> 'role', '')) IN (
            'admin', 'developer', 'owner'
        );
$$;

REVOKE ALL ON FUNCTION public.qabas_is_support_admin() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.qabas_is_support_admin() TO authenticated;

-- Remove the permissive policies created by older migrations and master_schema.sql.
DROP POLICY IF EXISTS "Public select policy on dev_chat_messages" ON public.dev_chat_messages;
DROP POLICY IF EXISTS "Public insert policy on dev_chat_messages" ON public.dev_chat_messages;
DROP POLICY IF EXISTS "Public update policy on dev_chat_messages" ON public.dev_chat_messages;
DROP POLICY IF EXISTS "Public delete policy on dev_chat_messages" ON public.dev_chat_messages;
DROP POLICY IF EXISTS "Allow public insert dev_chat_messages" ON public.dev_chat_messages;
DROP POLICY IF EXISTS "Allow public select dev_chat_messages" ON public.dev_chat_messages;
DROP POLICY IF EXISTS "Users can view own dev_chat_messages" ON public.dev_chat_messages;
DROP POLICY IF EXISTS "Users can insert own dev_chat_messages" ON public.dev_chat_messages;

DROP POLICY IF EXISTS "Public select policy on dev_bug_reports" ON public.dev_bug_reports;
DROP POLICY IF EXISTS "Public insert policy on dev_bug_reports" ON public.dev_bug_reports;
DROP POLICY IF EXISTS "Public update policy on dev_bug_reports" ON public.dev_bug_reports;
DROP POLICY IF EXISTS "Public delete policy on dev_bug_reports" ON public.dev_bug_reports;
DROP POLICY IF EXISTS "Allow public insert dev_bug_reports" ON public.dev_bug_reports;
DROP POLICY IF EXISTS "Allow public select dev_bug_reports" ON public.dev_bug_reports;
DROP POLICY IF EXISTS "Allow update dev_bug_reports" ON public.dev_bug_reports;
DROP POLICY IF EXISTS "Allow delete dev_bug_reports" ON public.dev_bug_reports;

-- RLS alone is not enough: remove broad table grants and restore only required privileges.
REVOKE ALL ON TABLE public.dev_chat_messages FROM PUBLIC, anon, authenticated;
REVOKE ALL ON TABLE public.dev_bug_reports FROM PUBLIC, anon, authenticated;
GRANT SELECT, INSERT ON TABLE public.dev_chat_messages TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.dev_bug_reports TO authenticated;
GRANT INSERT ON TABLE public.dev_bug_reports TO anon;
GRANT ALL ON TABLE public.dev_chat_messages, public.dev_bug_reports TO service_role;

-- Users can read their own support thread; only trusted support admins can see all threads.
CREATE POLICY "Users can view own dev_chat_messages"
    ON public.dev_chat_messages FOR SELECT TO authenticated
    USING (
        public.qabas_is_support_admin()
        OR user_id = auth.uid()::text
        OR lower(user_email) = lower(coalesce(auth.jwt() ->> 'email', ''))
    );

-- A normal account can only write as itself. Developer replies require a trusted admin claim.
CREATE POLICY "Users can insert own dev_chat_messages"
    ON public.dev_chat_messages FOR INSERT TO authenticated
    WITH CHECK (
        public.qabas_is_support_admin()
        OR (
            sender = 'user'
            AND lower(user_email) = lower(coalesce(auth.jwt() ->> 'email', ''))
            AND (user_id IS NULL OR user_id = auth.uid()::text)
        )
    );

-- Keep report intake available to guests, but do not let a submitter publish a
-- pre-resolved report or attach a developer reply. Existing reports are private.
CREATE POLICY "Users can view own dev_bug_reports"
    ON public.dev_bug_reports FOR SELECT TO authenticated
    USING (
        public.qabas_is_support_admin()
        OR user_id = auth.uid()::text
        OR lower(user_email) = lower(coalesce(auth.jwt() ->> 'email', ''))
    );

CREATE POLICY "Public can submit dev_bug_reports"
    ON public.dev_bug_reports FOR INSERT TO anon, authenticated
    WITH CHECK (
        status = 'open'
        AND coalesce(developer_reply, '') = ''
        AND (
            (auth.uid() IS NULL AND user_id IS NULL)
            OR (auth.uid() IS NOT NULL AND user_id = auth.uid()::text)
        )
    );

CREATE POLICY "Support admins can update dev_bug_reports"
    ON public.dev_bug_reports FOR UPDATE TO authenticated
    USING (public.qabas_is_support_admin())
    WITH CHECK (public.qabas_is_support_admin());

CREATE POLICY "Support admins can delete dev_bug_reports"
    ON public.dev_bug_reports FOR DELETE TO authenticated
    USING (public.qabas_is_support_admin());
