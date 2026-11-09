-- RPC function to update/promote user role securely from the developer dashboard.
-- Updates both public.users.type AND auth.users.raw_app_meta_data for immediate JWT RBAC authorization.

BEGIN;

CREATE OR REPLACE FUNCTION public.promote_user_role(
    p_target_user_id TEXT,
    p_new_type TEXT
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_caller_uid UUID := auth.uid();
    v_caller_email TEXT;
    v_target_role TEXT;
    v_is_authorized BOOLEAN := FALSE;
BEGIN
    IF v_caller_uid IS NULL THEN
        RAISE EXCEPTION 'Unauthenticated request' USING ERRCODE = '28000';
    END IF;

    -- Read the caller identity only from Supabase Auth.
    SELECT lower(email) INTO v_caller_email FROM auth.users WHERE id = v_caller_uid;

    -- Authorization trusts only server-managed app_metadata roles or the configured owner identity.
    -- public.users.type is profile data and must never grant permission to promote accounts.
    IF v_caller_email IN ('admin@qabas.studio', 'aliwalead.2007@gmail.com')
       OR lower(COALESCE((auth.jwt() -> 'app_metadata' ->> 'role'), '')) IN ('admin', 'developer', 'owner') THEN
        v_is_authorized := TRUE;
    END IF;

    IF NOT v_is_authorized THEN
        RAISE EXCEPTION 'Permission denied' USING ERRCODE = '42501';
    END IF;

    v_target_role := lower(trim(p_new_type));
    IF v_target_role NOT IN (
        'مطور', 'developer', 'admin', 'owner',
        'خاص', 'special',
        'مجاني', 'freemium', 'free', 'user'
    ) THEN
        RAISE EXCEPTION 'Unsupported role value' USING ERRCODE = '22023';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM auth.users WHERE id::text = p_target_user_id) THEN
        RAISE EXCEPTION 'Target auth user not found' USING ERRCODE = 'P0002';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM public.users WHERE id::text = p_target_user_id) THEN
        RAISE EXCEPTION 'Target profile not found' USING ERRCODE = 'P0002';
    END IF;

    -- 1. Persist the profile label for display only.
    UPDATE public.users
       SET type = p_new_type,
           updated_at = now()
     WHERE id::text = p_target_user_id;

    -- 2. Set only server-managed Auth metadata as the authorization source.
    IF v_target_role IN ('مطور', 'developer', 'admin', 'owner') THEN
        v_target_role := 'developer';
    ELSIF v_target_role IN ('خاص', 'special') THEN
        v_target_role := 'special';
    ELSE
        v_target_role := 'user';
    END IF;

    UPDATE auth.users
       SET raw_app_meta_data = COALESCE(raw_app_meta_data, '{}'::jsonb)
           || jsonb_build_object('role', v_target_role)
     WHERE id::text = p_target_user_id;

    RETURN jsonb_build_object('success', true, 'target_id', p_target_user_id, 'new_type', p_new_type, 'cloud_role', v_target_role);
END;
$$;

-- Keep the explicit profile-update policy aligned with the two configured owner identities.
DROP POLICY IF EXISTS users_admin_update ON public.users;
CREATE POLICY users_admin_update
    ON public.users
    FOR UPDATE
    TO authenticated
    USING (
        lower(COALESCE(auth.jwt() -> 'app_metadata' ->> 'role', '')) IN ('admin', 'developer', 'owner')
        OR lower(COALESCE(auth.jwt() ->> 'email', '')) IN ('admin@qabas.studio', 'aliwalead.2007@gmail.com')
    )
    WITH CHECK (
        lower(COALESCE(auth.jwt() -> 'app_metadata' ->> 'role', '')) IN ('admin', 'developer', 'owner')
        OR lower(COALESCE(auth.jwt() ->> 'email', '')) IN ('admin@qabas.studio', 'aliwalead.2007@gmail.com')
    );

REVOKE ALL ON FUNCTION public.promote_user_role(TEXT, TEXT) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.promote_user_role(TEXT, TEXT) TO authenticated;

COMMIT;
