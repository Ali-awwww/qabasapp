-- Push tokens for authenticated Qabas users.
-- Tokens are never public and are only readable/writable by their owner or support admins.
BEGIN;

CREATE TABLE IF NOT EXISTS public.user_push_tokens (
    user_id TEXT NOT NULL,
    token TEXT PRIMARY KEY,
    platform TEXT NOT NULL DEFAULT 'android',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE public.user_push_tokens ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "push_tokens_owner_insert" ON public.user_push_tokens;
CREATE POLICY "push_tokens_owner_insert"
    ON public.user_push_tokens FOR INSERT TO authenticated
    WITH CHECK (user_id = auth.uid()::text);

DROP POLICY IF EXISTS "push_tokens_owner_update" ON public.user_push_tokens;
CREATE POLICY "push_tokens_owner_update"
    ON public.user_push_tokens FOR UPDATE TO authenticated
    USING (user_id = auth.uid()::text)
    WITH CHECK (user_id = auth.uid()::text);

DROP POLICY IF EXISTS "push_tokens_owner_select" ON public.user_push_tokens;
CREATE POLICY "push_tokens_owner_select"
    ON public.user_push_tokens FOR SELECT TO authenticated
    USING (user_id = auth.uid()::text OR public.qabas_is_support_admin());

DROP POLICY IF EXISTS "push_tokens_owner_delete" ON public.user_push_tokens;
CREATE POLICY "push_tokens_owner_delete"
    ON public.user_push_tokens FOR DELETE TO authenticated
    USING (user_id = auth.uid()::text OR public.qabas_is_support_admin());

CREATE INDEX IF NOT EXISTS idx_user_push_tokens_user_id
    ON public.user_push_tokens(user_id);

COMMIT;
