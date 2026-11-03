-- 🛡️ Repair social interaction permissions for Reels
-- Ensures likes, comments and follows work reliably for authenticated users.

-- 1. Reel Likes: Need SELECT for upsert conflict resolution, and DELETE for unliking.
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.reel_likes TO authenticated;

-- 2. Reel Comments: Need SELECT and DELETE for management.
GRANT SELECT, INSERT, DELETE ON TABLE public.reel_comments TO authenticated;

-- 3. Reel Bookmarks: Need full CRUD for the bookmarking system.
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.reel_bookmarks TO authenticated;

-- 4. Follow System: Need DELETE for unfollowing.
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.creator_follows TO authenticated;

-- 5. Safe Account Numbering: Function to get next sequence without reading all users.
CREATE OR REPLACE FUNCTION public.get_next_account_sequence()
RETURNS integer
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    max_seq integer;
BEGIN
    SELECT COALESCE(MAX(NULLIF(regexp_replace(account_number, '^#?QBS-', ''), '')::integer), 0)
    INTO max_seq
    FROM public.users;
    
    RETURN max_seq + 1;
END;
$$;

GRANT EXECUTE ON FUNCTION public.get_next_account_sequence() TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_next_account_sequence() TO anon;
