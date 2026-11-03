-- RPC function to check email and custom_id availability before registration.
-- Accessible to anonymous users to provide live feedback during sign-up.

BEGIN;

CREATE OR REPLACE FUNCTION public.check_account_availability(
    p_email TEXT,
    p_custom_id TEXT
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_email_taken BOOLEAN := FALSE;
    v_custom_id_taken BOOLEAN := FALSE;
BEGIN
    -- 1. Check email availability (case-insensitive)
    IF p_email IS NOT NULL AND btrim(p_email) <> '' THEN
        SELECT EXISTS (
            SELECT 1 FROM public.users 
            WHERE lower(btrim(email)) = lower(btrim(p_email))
        ) INTO v_email_taken;
    END IF;

    -- 2. Check custom_id availability (case-insensitive)
    IF p_custom_id IS NOT NULL AND btrim(p_custom_id) <> '' THEN
        -- Normalize the input custom_id by removing leading @ if present
        DECLARE
            v_norm_id TEXT := lower(btrim(regexp_replace(p_custom_id, '^@', '')));
        BEGIN
            IF v_norm_id <> '' THEN
                SELECT EXISTS (
                    SELECT 1 FROM public.users 
                    WHERE lower(btrim(custom_id)) = v_norm_id
                ) INTO v_custom_id_taken;
            END IF;
        END;
    END IF;

    RETURN jsonb_build_object(
        'email_taken', v_email_taken,
        'custom_id_taken', v_custom_id_taken
    );
END;
$$;

-- Grant execution to anonymous and authenticated users.
GRANT EXECUTE ON FUNCTION public.check_account_availability(TEXT, TEXT) TO anon, authenticated;

COMMIT;
