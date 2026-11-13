-- Keep the denormalized reels.likes_count column consistent with reel_likes.
-- The client writes only the owner-scoped relation; this trigger owns the public counter.

CREATE OR REPLACE FUNCTION public.sync_reel_likes_count()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        UPDATE public.reels
        SET likes_count = (
            SELECT COUNT(*)::integer
            FROM public.reel_likes
            WHERE reel_id = OLD.reel_id
        ),
            updated_at = timezone('utc'::text, now())
        WHERE id = OLD.reel_id;
    ELSE
        UPDATE public.reels
        SET likes_count = (
            SELECT COUNT(*)::integer
            FROM public.reel_likes
            WHERE reel_id = NEW.reel_id
        ),
            updated_at = timezone('utc'::text, now())
        WHERE id = NEW.reel_id;
    END IF;

    RETURN NULL;
END;
$$;

DROP TRIGGER IF EXISTS trg_sync_reel_likes_count ON public.reel_likes;

CREATE TRIGGER trg_sync_reel_likes_count
AFTER INSERT OR UPDATE OR DELETE ON public.reel_likes
FOR EACH ROW
EXECUTE FUNCTION public.sync_reel_likes_count();

-- Repair counters for rows created before this trigger existed.
UPDATE public.reels AS r
SET likes_count = COALESCE(
    (
        SELECT COUNT(*)::integer
        FROM public.reel_likes AS l
        WHERE l.reel_id = r.id
    ),
    0
);
