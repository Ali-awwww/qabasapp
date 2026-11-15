-- Controlled support-message management. No direct UPDATE/DELETE grants are added.
ALTER TABLE public.dev_chat_messages
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ;

CREATE OR REPLACE FUNCTION public.qabas_edit_dev_chat_message(
    p_message_id TEXT,
    p_user_id TEXT,
    p_message TEXT
)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public
AS $$
BEGIN
    IF auth.uid() IS NULL OR NOT public.qabas_is_support_admin() THEN
        RAISE EXCEPTION 'Not authorized to manage support messages' USING ERRCODE = '42501';
    END IF;

    IF p_message_id IS NULL OR btrim(p_message_id) = ''
       OR p_user_id IS NULL OR btrim(p_user_id) = ''
       OR p_message IS NULL OR btrim(p_message) = ''
       OR char_length(btrim(p_message)) > 10000 THEN
        RAISE EXCEPTION 'Invalid support message input' USING ERRCODE = '22023';
    END IF;

    UPDATE public.dev_chat_messages
       SET message = btrim(p_message),
           updated_at = clock_timestamp()
     WHERE id = p_message_id
       AND user_id = p_user_id;

    RETURN FOUND;
END;
$$;

CREATE OR REPLACE FUNCTION public.qabas_delete_dev_chat_message(
    p_message_id TEXT,
    p_user_id TEXT
)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public
AS $$
BEGIN
    IF auth.uid() IS NULL OR NOT public.qabas_is_support_admin() THEN
        RAISE EXCEPTION 'Not authorized to manage support messages' USING ERRCODE = '42501';
    END IF;

    IF p_message_id IS NULL OR btrim(p_message_id) = ''
       OR p_user_id IS NULL OR btrim(p_user_id) = '' THEN
        RAISE EXCEPTION 'Invalid support message input' USING ERRCODE = '22023';
    END IF;

    DELETE FROM public.dev_chat_messages
     WHERE id = p_message_id
       AND user_id = p_user_id;

    RETURN FOUND;
END;
$$;

CREATE OR REPLACE FUNCTION public.qabas_delete_dev_chat_thread(
    p_user_id TEXT
)
RETURNS INTEGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public
AS $$
DECLARE
    v_deleted_count INTEGER;
BEGIN
    IF auth.uid() IS NULL OR NOT public.qabas_is_support_admin() THEN
        RAISE EXCEPTION 'Not authorized to manage support conversations' USING ERRCODE = '42501';
    END IF;

    IF p_user_id IS NULL OR btrim(p_user_id) = '' THEN
        RAISE EXCEPTION 'Invalid support conversation owner' USING ERRCODE = '22023';
    END IF;

    DELETE FROM public.dev_chat_messages
     WHERE user_id = p_user_id;

    GET DIAGNOSTICS v_deleted_count = ROW_COUNT;
    RETURN v_deleted_count;
END;
$$;

REVOKE ALL ON FUNCTION public.qabas_edit_dev_chat_message(TEXT, TEXT, TEXT) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.qabas_delete_dev_chat_message(TEXT, TEXT) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.qabas_delete_dev_chat_thread(TEXT) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.qabas_edit_dev_chat_message(TEXT, TEXT, TEXT) TO authenticated;
GRANT EXECUTE ON FUNCTION public.qabas_delete_dev_chat_message(TEXT, TEXT) TO authenticated;
GRANT EXECUTE ON FUNCTION public.qabas_delete_dev_chat_thread(TEXT) TO authenticated;
