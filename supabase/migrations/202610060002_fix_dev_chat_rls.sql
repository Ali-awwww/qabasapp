-- Migration: Correct RLS Policies for Dev Chat (Security Audit v1.1)
-- Ensures users only see their own messages, while developers/admins can see all.

-- 1. Enable RLS (Should already be enabled, but safe to repeat)
ALTER TABLE public.dev_chat_messages ENABLE ROW LEVEL SECURITY;

-- 2. Drop old permissive policies
DROP POLICY IF EXISTS "Allow public insert dev_chat_messages" ON public.dev_chat_messages;
DROP POLICY IF EXISTS "Allow public select dev_chat_messages" ON public.dev_chat_messages;

-- 3. Correct Policy for Selection (Reading)
-- Rule: Users see their own messages (by user_email). Admins see everything.
CREATE POLICY "Users can view own dev_chat_messages"
    ON public.dev_chat_messages FOR SELECT
    USING (
        auth.jwt() ->> 'email' = user_email OR
        (EXISTS (SELECT 1 FROM public.users WHERE id = auth.uid() AND (type = 'admin' OR type = 'developer')))
    );

-- 4. Correct Policy for Insertion (Sending)
-- Rule: Users can only send messages as themselves.
CREATE POLICY "Users can insert own dev_chat_messages"
    ON public.dev_chat_messages FOR INSERT
    WITH CHECK (
        auth.jwt() ->> 'email' = user_email OR
        (EXISTS (SELECT 1 FROM public.users WHERE id = auth.uid() AND (type = 'admin' OR type = 'developer')))
    );

-- 5. Cleanup: Prevent update/delete by users
DROP POLICY IF EXISTS "Allow users update own dev_chat_messages" ON public.dev_chat_messages;
DROP POLICY IF EXISTS "Allow users delete own dev_chat_messages" ON public.dev_chat_messages;

-- 6. Add Index for performance
CREATE INDEX IF NOT EXISTS idx_dev_chat_messages_composite ON public.dev_chat_messages(user_email, sender, created_at);
