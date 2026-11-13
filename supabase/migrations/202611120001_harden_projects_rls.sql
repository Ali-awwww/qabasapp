-- Harden project ownership for the mobile workspace sync.
-- The legacy master schema created public CRUD policies for projects; replace
-- them with authenticated owner-only policies before enabling cloud workspace data.

ALTER TABLE public.projects ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Public select policy on projects" ON public.projects;
DROP POLICY IF EXISTS "Public insert policy on projects" ON public.projects;
DROP POLICY IF EXISTS "Public update policy on projects" ON public.projects;
DROP POLICY IF EXISTS "Public delete policy on projects" ON public.projects;
DROP POLICY IF EXISTS projects_select_own ON public.projects;
DROP POLICY IF EXISTS projects_insert_own ON public.projects;
DROP POLICY IF EXISTS projects_update_own ON public.projects;
DROP POLICY IF EXISTS projects_delete_own ON public.projects;

REVOKE ALL ON TABLE public.projects FROM anon;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.projects TO authenticated;
GRANT ALL ON TABLE public.projects TO service_role;

CREATE POLICY projects_select_own
    ON public.projects
    FOR SELECT
    TO authenticated
    USING (user_id = auth.uid()::text);

CREATE POLICY projects_insert_own
    ON public.projects
    FOR INSERT
    TO authenticated
    WITH CHECK (user_id = auth.uid()::text);

CREATE POLICY projects_update_own
    ON public.projects
    FOR UPDATE
    TO authenticated
    USING (user_id = auth.uid()::text)
    WITH CHECK (user_id = auth.uid()::text);

CREATE POLICY projects_delete_own
    ON public.projects
    FOR DELETE
    TO authenticated
    USING (user_id = auth.uid()::text);

CREATE INDEX IF NOT EXISTS idx_projects_owner_updated_at
    ON public.projects (user_id, updated_at DESC);
