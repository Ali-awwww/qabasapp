-- Allow the authenticated client to receive INSERT events for its own chat rows.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_publication WHERE pubname = 'supabase_realtime')
       AND NOT EXISTS (
           SELECT 1
           FROM pg_publication_tables
           WHERE pubname = 'supabase_realtime'
             AND schemaname = 'public'
             AND tablename = 'dev_chat_messages'
       ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.dev_chat_messages;
    END IF;
END $$;
