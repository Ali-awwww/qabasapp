-- Preserve a readable author identity for each comment and support owner-only edits.
ALTER TABLE public.reel_comments
    ADD COLUMN IF NOT EXISTS user_name text,
    ADD COLUMN IF NOT EXISTS updated_at timestamptz;

-- Backfill names for older comments when a matching public user record exists.
UPDATE public.reel_comments AS c
SET user_name = COALESCE(NULLIF(btrim(u.name), ''), 'مستخدم قبس')
FROM public.users AS u
WHERE u.id = c.user_id
  AND (c.user_name IS NULL OR btrim(c.user_name) = '');

UPDATE public.reel_comments
SET user_name = 'مستخدم قبس'
WHERE user_name IS NULL OR btrim(user_name) = '';

ALTER TABLE public.reel_comments
    ALTER COLUMN user_name SET DEFAULT 'مستخدم قبس',
    ALTER COLUMN user_name SET NOT NULL;

-- The client can request CRUD, while RLS restricts edits/deletes to the author.
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.reel_comments TO authenticated;

DROP POLICY IF EXISTS reel_comments_update_own ON public.reel_comments;
CREATE POLICY reel_comments_update_own
    ON public.reel_comments
    FOR UPDATE
    TO authenticated
    USING ((SELECT auth.uid())::text = user_id)
    WITH CHECK ((SELECT auth.uid())::text = user_id);
