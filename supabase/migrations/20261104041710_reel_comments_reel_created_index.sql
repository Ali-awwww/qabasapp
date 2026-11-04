-- Support comment lookup by reel and the chronological comments list.
CREATE INDEX IF NOT EXISTS idx_reel_comments_reel_id_created_at
    ON public.reel_comments (reel_id, created_at ASC);
