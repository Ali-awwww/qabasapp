-- Replace the client's SELECT/check/UPDATE/SELECT lease protocol with one
-- atomic server-side update. A lease expires after 150 seconds without a
-- heartbeat. The database clock is authoritative; timestamps far in the
-- future are treated as invalid so clock-skewed legacy values cannot lock an
-- account indefinitely.

CREATE OR REPLACE FUNCTION public.claim_active_session(p_session_id text)
RETURNS text
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog
AS $function$
DECLARE
    v_user_id uuid := auth.uid();
    v_now timestamptz := clock_timestamp();
    v_rows integer;
BEGIN
    IF v_user_id IS NULL THEN
        RETURN 'unauthenticated';
    END IF;

    IF p_session_id IS NULL OR char_length(btrim(p_session_id)) NOT BETWEEN 1 AND 128 THEN
        RETURN 'invalid_session';
    END IF;

    UPDATE public.users AS u
       SET active_session_id = p_session_id,
           session_updated_at = v_now
     WHERE u.id = v_user_id::text
       AND (
            NULLIF(btrim(u.active_session_id), '') IS NULL
            OR u.active_session_id = p_session_id
            OR u.session_updated_at IS NULL
            OR u.session_updated_at < v_now - interval '150 seconds'
            OR u.session_updated_at > v_now + interval '60 seconds'
       );

    GET DIAGNOSTICS v_rows = ROW_COUNT;
    IF v_rows = 1 THEN
        RETURN 'claimed';
    END IF;

    IF EXISTS (SELECT 1 FROM public.users AS u WHERE u.id = v_user_id::text) THEN
        RETURN 'active_elsewhere';
    END IF;

    RETURN 'profile_missing';
END;
$function$;

COMMENT ON FUNCTION public.claim_active_session(text) IS
    'Atomically claims or heartbeats the authenticated user session using the database clock.';

REVOKE ALL ON FUNCTION public.claim_active_session(text) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.claim_active_session(text) TO authenticated;

CREATE OR REPLACE FUNCTION public.release_active_session(p_session_id text)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog
AS $function$
DECLARE
    v_user_id uuid := auth.uid();
    v_rows integer;
BEGIN
    IF v_user_id IS NULL OR p_session_id IS NULL OR btrim(p_session_id) = '' THEN
        RETURN false;
    END IF;

    UPDATE public.users AS u
       SET active_session_id = NULL,
           session_updated_at = NULL
     WHERE u.id = v_user_id::text
       AND u.active_session_id = p_session_id;

    GET DIAGNOSTICS v_rows = ROW_COUNT;
    RETURN v_rows = 1;
END;
$function$;

COMMENT ON FUNCTION public.release_active_session(text) IS
    'Releases only the authenticated user session whose session ID matches the caller.';

REVOKE ALL ON FUNCTION public.release_active_session(text) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.release_active_session(text) TO authenticated;

NOTIFY pgrst, 'reload schema';
