-- Migration: Dev Chat Messages (user <-> developer)
CREATE TABLE IF NOT EXISTS public.dev_chat_messages (
    id TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
    user_email TEXT NOT NULL DEFAULT 'anonymous',
    sender TEXT NOT NULL DEFAULT 'user', -- user | developer
    message TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE public.dev_chat_messages ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Allow public insert dev_chat_messages" ON public.dev_chat_messages;
CREATE POLICY "Allow public insert dev_chat_messages"
    ON public.dev_chat_messages FOR INSERT WITH CHECK (true);

DROP POLICY IF EXISTS "Allow public select dev_chat_messages" ON public.dev_chat_messages;
CREATE POLICY "Allow public select dev_chat_messages"
    ON public.dev_chat_messages FOR SELECT USING (true);

CREATE INDEX IF NOT EXISTS idx_dev_chat_messages_user_email ON public.dev_chat_messages(user_email, created_at);
