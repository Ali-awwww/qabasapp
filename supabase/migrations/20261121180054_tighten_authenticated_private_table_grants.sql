-- Remove schema-level privileges that the mobile client does not need.
-- Keep application CRUD privileges and all existing RLS policies unchanged.
REVOKE REFERENCES, TRIGGER, TRUNCATE ON TABLE public.user_push_tokens FROM authenticated;
REVOKE REFERENCES, TRIGGER, TRUNCATE ON TABLE public.user_azan_voices FROM authenticated;
