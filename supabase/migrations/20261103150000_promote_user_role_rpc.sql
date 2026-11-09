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
    v_is_authorized BOOLEAN := FALSE;
BEGIN
    IF v_caller_uid IS NULL THEN
        RAISE EXCEPTION 'Unauthenticated request' USING ERRCODE = '28000';
    END IF;

    -- Get caller email & type
    SELECT lower(email) INTO v_caller_email FROM auth.users WHERE id = v_caller_uid;

    -- Authorization trusts only server-managed app_metadata roles or the configured owner identity.
    -- public.users.type is profile data and must never grant permission to promote accounts.
    IF v_caller_email = 'aliwalead.2007@gmail.com'
       OR lower(COALESCE((auth.jwt() -> 'app_metadata' ->> 'role'), '')) IN ('admin', 'developer', 'owner') THEN
        v_is_authorized := TRUE;
    END IF;

    IF NOT v_is_authorized THEN
        RAISE EXCEPTION 'Permission denied' USING ERRCODE = '42501';
    END IF;

    -- 1. Update public.users.type
    UPDATE public.users
       SET type = p_new_type,
           updated_at = now()
     WHERE id = p_target_user_id;

    -- 2. Sync auth.users.raw_app_meta_data for immediate JWT authorization
    IF lower(p_new_type) IN ('مطور', 'developer', 'admin', 'owner') THEN
        UPDATE auth.users
           SET raw_app_meta_data = COALESCE(raw_app_meta_data, '{}'::jsonb) || jsonb_build_object('role', 'developer')
         WHERE id::text = p_target_user_id;
    ELSE
        UPDATE auth.users
           SET raw_app_meta_data = COALESCE(raw_app_meta_data, '{}'::jsonb) || jsonb_build_object('role', 'user')
         WHERE id::text = p_target_user_id;
    END IF;

    RETURN jsonb_build_object('success', true, 'target_id', p_target_user_id, 'new_type', p_new_type);
END;
$$;

-- Also update policies on public.users to allow the owner email (aliwalead.2007@gmail.com) to update user roles directly
DROP POLICY IF EXISTS users_admin_update ON public.users;
CREATE POLICY users_admin_update
    ON public.users
    FOR UPDATE
    TO authenticated
    USING (
        lower(COALESCE(auth.jwt() -> 'app_metadata' ->> 'role', '')) IN ('admin', 'developer', 'owner')
        OR lower(auth.jwt() ->> 'email') = 'aliwalead.2007@gmail.com'
    )
    WITH CHECK (
        lower(COALESCE(auth.jwt() -> 'app_metadata' ->> 'role', '')) IN ('admin', 'developer', 'owner')
        OR lower(auth.jwt() ->> 'email') = 'aliwalead.2007@gmail.com'
    );

GRANT EXECUTE ON FUNCTION public.promote_user_role(TEXT, TEXT) TO authenticated;

COMMIT;
