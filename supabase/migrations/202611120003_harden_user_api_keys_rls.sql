-- Provider secrets are owned only by the authenticated Supabase UID.
-- The Edge Function is the only supported client path for decrypting values.

DROP POLICY IF EXISTS "Users can read own api keys" ON public.user_api_keys;
CREATE POLICY "Users can read own api keys"
    ON public.user_api_keys
    FOR SELECT TO authenticated
    USING ((select auth.uid())::text = user_id);

DROP POLICY IF EXISTS "Users can insert or update own api keys" ON public.user_api_keys;
CREATE POLICY "Users can insert or update own api keys"
    ON public.user_api_keys
    FOR ALL TO authenticated
    USING ((select auth.uid())::text = user_id)
    WITH CHECK ((select auth.uid())::text = user_id);
