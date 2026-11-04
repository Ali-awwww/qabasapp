-- Stop disclosing whether an email address belongs to a Qabas account.
-- Keep the two-argument signature and response key for compatibility with older clients.
BEGIN;

CREATE OR REPLACE FUNCTION public.check_account_availability(
    p_email TEXT,
    p_custom_id TEXT
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public
AS $$
DECLARE
    v_custom_id_taken BOOLEAN := FALSE;
    v_normalized_custom_id TEXT;
BEGIN
    -- p_email is intentionally ignored: registration status is private.
    IF p_custom_id IS NOT NULL AND btrim(p_custom_id) <> '' THEN
        v_normalized_custom_id := lower(btrim(regexp_replace(p_custom_id, '^@', '')));
        IF v_normalized_custom_id <> '' THEN
            SELECT EXISTS (
                SELECT 1
                FROM public.users
                WHERE lower(btrim(custom_id)) = v_normalized_custom_id
            ) INTO v_custom_id_taken;
        END IF;
    END IF;

    RETURN jsonb_build_object(
        'email_taken', FALSE,
        'custom_id_taken', v_custom_id_taken
    );
END;
$$;

REVOKE ALL ON FUNCTION public.check_account_availability(TEXT, TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.check_account_availability(TEXT, TEXT) TO anon, authenticated;

COMMIT;
