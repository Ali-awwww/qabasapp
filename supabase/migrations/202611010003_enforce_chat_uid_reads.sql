-- Restrict support-chat reads to the authenticated Supabase UID.
-- Email is descriptive metadata, not a stable authorization key. Legacy chat rows
-- without user_id remain visible to support admins but not to ordinary users.

BEGIN;

DROP POLICY IF EXISTS "Users can view own dev_chat_messages"
    ON public.dev_chat_messages;
DROP POLICY IF EXISTS "dev_chat_select_own"
    ON public.dev_chat_messages;
DROP POLICY IF EXISTS "Allow public select dev_chat_messages"
    ON public.dev_chat_messages;
DROP POLICY IF EXISTS "Public select policy on dev_chat_messages"
    ON public.dev_chat_messages;

CREATE POLICY "Users can view own dev_chat_messages"
    ON public.dev_chat_messages
    FOR SELECT
    TO authenticated
    USING (
        public.qabas_is_support_admin()
        OR (
            auth.uid() IS NOT NULL
            AND user_id = auth.uid()::text
        )
    );

CREATE INDEX IF NOT EXISTS idx_dev_chat_messages_user_id_created_at
    ON public.dev_chat_messages (user_id, created_at);

COMMIT;
