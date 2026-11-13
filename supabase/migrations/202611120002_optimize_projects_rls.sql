-- Optimize owner-scoped project RLS policies.
-- Wrapping auth.uid() in a scalar SELECT lets Postgres evaluate it once
-- per statement instead of once per row.

DROP POLICY IF EXISTS projects_select_own ON public.projects;
DROP POLICY IF EXISTS projects_insert_own ON public.projects;
DROP POLICY IF EXISTS projects_update_own ON public.projects;
DROP POLICY IF EXISTS projects_delete_own ON public.projects;

CREATE POLICY projects_select_own
    ON public.projects
    FOR SELECT
    TO authenticated
    USING (user_id = (SELECT auth.uid())::text);

CREATE POLICY projects_insert_own
    ON public.projects
    FOR INSERT
    TO authenticated
    WITH CHECK (user_id = (SELECT auth.uid())::text);

CREATE POLICY projects_update_own
    ON public.projects
    FOR UPDATE
    TO authenticated
    USING (user_id = (SELECT auth.uid())::text)
    WITH CHECK (user_id = (SELECT auth.uid())::text);

CREATE POLICY projects_delete_own
    ON public.projects
    FOR DELETE
    TO authenticated
    USING (user_id = (SELECT auth.uid())::text);
