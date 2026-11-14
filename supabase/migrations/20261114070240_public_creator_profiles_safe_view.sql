-- Expose only the identity fields needed by public creator cards.
-- The profile table remains owner-scoped; this view is the explicit public boundary.
CREATE OR REPLACE VIEW public.community_profiles_public
WITH (security_barrier = true, security_invoker = false)
AS
SELECT
    p.id,
    p.full_name,
    p.avatar_url
FROM public.profiles AS p
WHERE EXISTS (
    SELECT 1
    FROM public.reels AS r
    WHERE r.user_id = p.id
      AND r.is_active IS TRUE
);

REVOKE ALL PRIVILEGES ON TABLE public.community_profiles_public FROM PUBLIC, anon, authenticated, service_role;
GRANT SELECT ON TABLE public.community_profiles_public TO anon, authenticated, service_role;

COMMENT ON VIEW public.community_profiles_public IS
    'Public creator identity projection: only id, name, and avatar for accounts with an active public reel.';

-- Rollback to the prior owner-scoped view behavior if this migration must be reverted:
-- CREATE OR REPLACE VIEW public.community_profiles_public
-- WITH (security_barrier = true, security_invoker = true)
-- AS SELECT id, full_name, avatar_url FROM public.profiles;
-- GRANT SELECT ON TABLE public.community_profiles_public TO anon, authenticated, service_role;
-- GRANT ALL PRIVILEGES ON TABLE public.community_profiles_public TO service_role;
