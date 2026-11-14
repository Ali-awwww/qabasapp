-- Close raw social-graph reads while preserving the public follower count and
-- authenticated users' ability to read their own relationships.

CREATE INDEX IF NOT EXISTS creator_follows_creator_idx
    ON public.creator_follows (creator_id);

CREATE OR REPLACE FUNCTION public.get_creator_follower_count(p_creator_id text)
RETURNS bigint
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog
SET row_security = off
AS $$
    SELECT count(*)
    FROM public.creator_follows
    WHERE creator_id = p_creator_id;
$$;

REVOKE ALL ON FUNCTION public.get_creator_follower_count(text) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.get_creator_follower_count(text) FROM anon, authenticated;
GRANT EXECUTE ON FUNCTION public.get_creator_follower_count(text) TO anon, authenticated;

DROP POLICY IF EXISTS creator_follows_public_read ON public.creator_follows;
DROP POLICY IF EXISTS reel_likes_public_read ON public.reel_likes;
DROP POLICY IF EXISTS "Anyone can view likes" ON public.reel_likes;

DROP POLICY IF EXISTS creator_follows_select_own ON public.creator_follows;
CREATE POLICY creator_follows_select_own
    ON public.creator_follows
    FOR SELECT
    TO authenticated
    USING ((SELECT auth.uid())::text = follower_id);

DROP POLICY IF EXISTS reel_likes_select_own ON public.reel_likes;
CREATE POLICY reel_likes_select_own
    ON public.reel_likes
    FOR SELECT
    TO authenticated
    USING ((SELECT auth.uid())::text = user_id);

REVOKE SELECT ON TABLE public.creator_follows FROM PUBLIC, anon;
REVOKE SELECT ON TABLE public.reel_likes FROM PUBLIC, anon;
GRANT SELECT ON TABLE public.creator_follows TO authenticated;
GRANT SELECT ON TABLE public.reel_likes TO authenticated;
