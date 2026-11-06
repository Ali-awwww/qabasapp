-- Restore authenticated self-service access to the profile mirror.
-- Keep row-level isolation and prevent clients from changing the role column.
-- Remove the broad community policy before granting table-level SELECT; profile
-- reads are intentionally limited to the signed-in account.
DROP POLICY IF EXISTS profiles_community_read ON public.profiles;
GRANT SELECT ON TABLE public.profiles TO authenticated;
GRANT INSERT (id, email, full_name, avatar_url, role)
    ON TABLE public.profiles TO authenticated;
GRANT UPDATE (email, full_name, avatar_url)
    ON TABLE public.profiles TO authenticated;

DROP POLICY IF EXISTS profiles_select_own ON public.profiles;
CREATE POLICY profiles_select_own
    ON public.profiles
    FOR SELECT
    TO authenticated
    USING (id = (SELECT auth.uid())::text);

DROP POLICY IF EXISTS profiles_insert_own ON public.profiles;
CREATE POLICY profiles_insert_own
    ON public.profiles
    FOR INSERT
    TO authenticated
    WITH CHECK (
        id = (SELECT auth.uid())::text
        AND role = 'user'
    );

DROP POLICY IF EXISTS profiles_update_own ON public.profiles;
CREATE POLICY profiles_update_own
    ON public.profiles
    FOR UPDATE
    TO authenticated
    USING (id = (SELECT auth.uid())::text)
    WITH CHECK (id = (SELECT auth.uid())::text);
