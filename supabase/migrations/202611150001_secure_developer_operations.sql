-- Secure developer operations behind server-side authorization.
-- The Android client must not update user status or broadcast rows directly.
BEGIN;

CREATE OR REPLACE FUNCTION public.set_user_suspension(
    p_target_user_id TEXT,
    p_is_suspended BOOLEAN
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_caller_uid UUID := auth.uid();
    v_caller_email TEXT;
    v_authorized BOOLEAN := FALSE;
    v_status TEXT := CASE WHEN p_is_suspended THEN 'suspended' ELSE 'active' END;
BEGIN
    IF v_caller_uid IS NULL THEN
        RAISE EXCEPTION 'Unauthenticated request' USING ERRCODE = '28000';
    END IF;

    SELECT lower(email) INTO v_caller_email FROM auth.users WHERE id = v_caller_uid;
    IF v_caller_email IN ('admin@qabas.studio', 'aliwalead.2007@gmail.com')
       OR lower(COALESCE((auth.jwt() -> 'app_metadata' ->> 'role'), '')) IN ('admin', 'developer', 'owner') THEN
        v_authorized := TRUE;
    END IF;

    IF NOT v_authorized THEN
        RAISE EXCEPTION 'Permission denied' USING ERRCODE = '42501';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM public.users WHERE id::text = p_target_user_id) THEN
        RAISE EXCEPTION 'Target profile not found' USING ERRCODE = 'P0002';
    END IF;

    UPDATE public.users
       SET status = v_status,
           updated_at = now()
     WHERE id::text = p_target_user_id;

    RETURN jsonb_build_object('success', true, 'target_id', p_target_user_id, 'status', v_status);
END;
$$;

CREATE OR REPLACE FUNCTION public.create_broadcast(
    p_title TEXT,
    p_message TEXT,
    p_target TEXT DEFAULT 'all'
)
RETURNS TEXT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_caller_uid UUID := auth.uid();
    v_caller_email TEXT;
    v_authorized BOOLEAN := FALSE;
    v_id UUID;
BEGIN
    IF v_caller_uid IS NULL THEN
        RAISE EXCEPTION 'Unauthenticated request' USING ERRCODE = '28000';
    END IF;

    SELECT lower(email) INTO v_caller_email FROM auth.users WHERE id = v_caller_uid;
    IF v_caller_email IN ('admin@qabas.studio', 'aliwalead.2007@gmail.com')
       OR lower(COALESCE((auth.jwt() -> 'app_metadata' ->> 'role'), '')) IN ('admin', 'developer', 'owner') THEN
        v_authorized := TRUE;
    END IF;

    IF NOT v_authorized THEN
        RAISE EXCEPTION 'Permission denied' USING ERRCODE = '42501';
    END IF;
    IF nullif(btrim(p_title), '') IS NULL OR nullif(btrim(p_message), '') IS NULL THEN
        RAISE EXCEPTION 'Title and message are required' USING ERRCODE = '22023';
    END IF;
    IF p_target NOT IN ('all', 'premium', 'developers') THEN
        RAISE EXCEPTION 'Unsupported broadcast target' USING ERRCODE = '22023';
    END IF;

    INSERT INTO public.broadcasts (title, message, target, status)
    VALUES (btrim(p_title), btrim(p_message), p_target, 'pending')
    RETURNING id INTO v_id;

    RETURN v_id::text;
END;
$$;

REVOKE ALL ON FUNCTION public.set_user_suspension(TEXT, BOOLEAN) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.set_user_suspension(TEXT, BOOLEAN) TO authenticated;
REVOKE ALL ON FUNCTION public.create_broadcast(TEXT, TEXT, TEXT) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.create_broadcast(TEXT, TEXT, TEXT) TO authenticated;

-- Broadcasts are readable by signed-in clients for delivery; only the RPC may create rows.
REVOKE INSERT, UPDATE, DELETE ON TABLE public.broadcasts FROM anon, authenticated;
GRANT SELECT ON TABLE public.broadcasts TO anon, authenticated;

COMMIT;
