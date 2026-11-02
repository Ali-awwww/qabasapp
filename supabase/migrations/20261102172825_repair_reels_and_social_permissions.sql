-- Repair the exact permissions used by Qabas reel publishing and social actions.
--
-- Keep authorization constrained by the existing owner-scoped RLS policies.
-- This migration grants only the operations/columns required by the client.

-- Reel likes use PostgREST upsert on the existing (reel_id, user_id) unique key.
grant insert on table public.reel_likes to authenticated;
grant update (reel_id, user_id) on table public.reel_likes to authenticated;

drop policy if exists reel_likes_update_own on public.reel_likes;
create policy reel_likes_update_own
    on public.reel_likes
    for update
    to authenticated
    using ((select auth.uid())::text = user_id)
    with check ((select auth.uid())::text = user_id);

-- Comments are inserted under the authenticated user's own UID.
grant insert on table public.reel_comments to authenticated;

-- Follow uses an upsert on the existing (follower_id, creator_id) unique key.
grant insert on table public.creator_follows to authenticated;
grant update (follower_id, creator_id, created_at)
    on table public.creator_follows to authenticated;

drop policy if exists creator_follows_update_self on public.creator_follows;
create policy creator_follows_update_self
    on public.creator_follows
    for update
    to authenticated
    using ((select auth.uid())::text = follower_id)
    with check ((select auth.uid())::text = follower_id);

-- The active-session heartbeat updates only these two columns.
-- The existing users_update_own RLS policy continues to enforce row ownership.
grant update (active_session_id, session_updated_at)
    on table public.users to authenticated;
