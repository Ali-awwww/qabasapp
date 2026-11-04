ALTER TABLE public.reels
    ADD COLUMN IF NOT EXISTS user_custom_id text;

ALTER TABLE public.reel_comments
    ADD COLUMN IF NOT EXISTS user_custom_id text;

-- Backfill public creator handles for existing videos.
UPDATE public.reels AS r
SET user_custom_id = NULLIF(regexp_replace(btrim(u.custom_id), '^@', ''), '')
FROM public.users AS u
WHERE u.id = r.user_id
  AND NULLIF(btrim(u.custom_id), '') IS NOT NULL
  AND (r.user_custom_id IS NULL OR btrim(r.user_custom_id) = '');

-- Backfill the same handle snapshot for existing comments.
UPDATE public.reel_comments AS c
SET user_custom_id = NULLIF(regexp_replace(btrim(u.custom_id), '^@', ''), '')
FROM public.users AS u
WHERE u.id = c.user_id
  AND NULLIF(btrim(u.custom_id), '') IS NOT NULL
  AND (c.user_custom_id IS NULL OR btrim(c.user_custom_id) = '');

COMMENT ON COLUMN public.reels.user_custom_id IS
    'Public creator handle snapshot captured when the reel is published.';
COMMENT ON COLUMN public.reel_comments.user_custom_id IS
    'Public commenter handle snapshot captured when the comment is created.';
