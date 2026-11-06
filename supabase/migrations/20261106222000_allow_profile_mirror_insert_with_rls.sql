-- The profile serializer may include nullable created_at in INSERT payloads.
-- Table-level INSERT is still constrained by profiles_insert_own (id + role=user).
GRANT INSERT ON TABLE public.profiles TO authenticated;
