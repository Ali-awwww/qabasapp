-- Keep broadcast delivery compatible with RLS without granting direct table updates.
BEGIN;

CREATE OR REPLACE FUNCTION public.mark_broadcast_sent(p_broadcast_id UUID)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF auth.uid() IS NULL THEN
        RAISE EXCEPTION 'Unauthenticated request' USING ERRCODE = '28000';
    END IF;

    UPDATE public.broadcasts
       SET status = 'sent', sent_at = now()
     WHERE id = p_broadcast_id
       AND status = 'pending';

    RETURN FOUND;
END;
$$;

REVOKE ALL ON FUNCTION public.mark_broadcast_sent(UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.mark_broadcast_sent(UUID) TO authenticated;

COMMIT;
