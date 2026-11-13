-- Evaluate auth.jwt()/auth.uid() once per statement in existing policies.
-- Policy logic and role scope remain unchanged.

ALTER POLICY "Public can submit dev_bug_reports"
    ON public.dev_bug_reports
    WITH CHECK (
        (status = 'open'::text)
        AND (COALESCE(developer_reply, ''::text) = ''::text)
        AND (
            (((SELECT auth.uid()) IS NULL) AND (user_id IS NULL))
            OR (
                ((SELECT auth.uid()) IS NOT NULL)
                AND (lower(COALESCE(user_email, ''::text)) = lower(COALESCE((SELECT auth.jwt()) ->> 'email'::text, ''::text)))
                AND ((user_id IS NULL) OR (user_id = (SELECT auth.uid())::text))
            )
        )
    );

ALTER POLICY "Users can view own dev_bug_reports"
    ON public.dev_bug_reports
    USING (
        qabas_is_support_admin()
        OR (((SELECT auth.uid()) IS NOT NULL) AND (user_id = (SELECT auth.uid())::text))
    );

ALTER POLICY "Users can insert own dev_chat_messages"
    ON public.dev_chat_messages
    WITH CHECK (
        qabas_is_support_admin()
        OR (
            (sender = 'user'::text)
            AND (lower(user_email) = lower(COALESCE((SELECT auth.jwt()) ->> 'email'::text, ''::text)))
            AND (user_id = (SELECT auth.uid())::text)
        )
    );

ALTER POLICY "Users can view own dev_chat_messages"
    ON public.dev_chat_messages
    USING (
        qabas_is_support_admin()
        OR (((SELECT auth.uid()) IS NOT NULL) AND (user_id = (SELECT auth.uid())::text))
    );

ALTER POLICY users_admin_select
    ON public.users
    USING (
        lower(COALESCE(((SELECT auth.jwt()) -> 'app_metadata'::text) ->> 'role'::text, ''::text))
        = ANY (ARRAY['admin'::text, 'developer'::text, 'owner'::text])
    );

ALTER POLICY users_admin_update
    ON public.users
    USING (
        lower(COALESCE(((SELECT auth.jwt()) -> 'app_metadata'::text) ->> 'role'::text, ''::text))
        = ANY (ARRAY['admin'::text, 'developer'::text, 'owner'::text])
    )
    WITH CHECK (
        lower(COALESCE(((SELECT auth.jwt()) -> 'app_metadata'::text) ->> 'role'::text, ''::text))
        = ANY (ARRAY['admin'::text, 'developer'::text, 'owner'::text])
    );

ALTER POLICY "scholar_lectures_developer_delete"
    ON public.scholar_lectures
    USING (
        lower(COALESCE(((SELECT auth.jwt()) -> 'app_metadata'::text) ->> 'role'::text, ''::text))
        = ANY (ARRAY['developer'::text, 'admin'::text, 'owner'::text])
    );

ALTER POLICY "scholar_lectures_developer_insert"
    ON public.scholar_lectures
    WITH CHECK (
        (
            lower(COALESCE(((SELECT auth.jwt()) -> 'app_metadata'::text) ->> 'role'::text, ''::text))
            = ANY (ARRAY['developer'::text, 'admin'::text, 'owner'::text])
        )
        AND (created_by_user_id = (SELECT auth.uid())::text)
    );

ALTER POLICY "scholar_lectures_developer_update"
    ON public.scholar_lectures
    USING (
        lower(COALESCE(((SELECT auth.jwt()) -> 'app_metadata'::text) ->> 'role'::text, ''::text))
        = ANY (ARRAY['developer'::text, 'admin'::text, 'owner'::text])
    )
    WITH CHECK (
        lower(COALESCE(((SELECT auth.jwt()) -> 'app_metadata'::text) ->> 'role'::text, ''::text))
        = ANY (ARRAY['developer'::text, 'admin'::text, 'owner'::text])
    );
