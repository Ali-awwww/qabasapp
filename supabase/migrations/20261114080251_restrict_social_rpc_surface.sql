-- Expose only aggregate counts for the current user or a publicly listed creator.
CREATE OR REPLACE FUNCTION public.get_creator_follower_count(p_creator_id text)
RETURNS bigint
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog
SET row_security = off
AS $$
    SELECT CASE
        WHEN p_creator_id = (SELECT auth.uid())::text
          OR EXISTS (
              SELECT 1
              FROM public.profiles AS p
              JOIN public.reels AS r ON r.user_id = p.id
              WHERE p.id = p_creator_id
                AND r.is_active IS TRUE
          )
        THEN (
            SELECT count(*)
            FROM public.creator_follows AS f
            WHERE f.creator_id = p_creator_id
        )
        ELSE 0::bigint
    END;
$$;

REVOKE ALL ON FUNCTION public.get_creator_follower_count(text) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.get_creator_follower_count(text) FROM anon, authenticated;
GRANT EXECUTE ON FUNCTION public.get_creator_follower_count(text) TO anon, authenticated;

-- This is a trigger-only SECURITY DEFINER function, not an API operation.
REVOKE ALL ON FUNCTION public.sync_reel_likes_count() FROM PUBLIC, anon, authenticated;
