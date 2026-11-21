-- The mobile client records delivery locally; it must not mutate the
-- global broadcasts row after showing a notification to one recipient.
-- Keep the function definition for compatibility, but remove its callable
-- surface until a trusted server-side delivery worker replaces it.
BEGIN;

REVOKE ALL ON FUNCTION public.mark_broadcast_sent(UUID) FROM PUBLIC, anon, authenticated;

COMMENT ON FUNCTION public.mark_broadcast_sent(UUID) IS
    'Reserved for a future trusted delivery worker; disabled for client roles because sent_at is global.';

COMMIT;
