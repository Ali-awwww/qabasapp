-- Enforce immutable Supabase UID ownership when users read their bug reports.
-- Earlier policy also accepted a matching user_email. The INSERT policy does not
-- bind user_email to auth.jwt()->>'email', so an authenticated caller could submit
-- a report using another person's email and then read reports with that email.
-- UID-only ownership closes that cross-account read path. Legacy rows without
-- user_id remain visible to support admins, but not to ordinary users.

BEGIN;

DROP POLICY IF EXISTS "Users can view own dev_bug_reports"
    ON public.dev_bug_reports;
DROP POLICY IF EXISTS "dev_bug_reports_select_own"
    ON public.dev_bug_reports;
DROP POLICY IF EXISTS "Allow public select dev_bug_reports"
    ON public.dev_bug_reports;
DROP POLICY IF EXISTS "Public select policy on dev_bug_reports"
    ON public.dev_bug_reports;

CREATE POLICY "Users can view own dev_bug_reports"
    ON public.dev_bug_reports
    FOR SELECT
    TO authenticated
    USING (
        public.qabas_is_support_admin()
        OR (
            auth.uid() IS NOT NULL
            AND user_id = auth.uid()::text
        )
    );

COMMIT;
