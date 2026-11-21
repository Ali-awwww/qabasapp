-- No current trusted worker calls this legacy global acknowledgement RPC.
-- Keep the function definition for compatibility, but expose no EXECUTE grant
-- until a replacement worker and its authorization contract are implemented.
REVOKE EXECUTE ON FUNCTION public.mark_broadcast_sent(UUID) FROM service_role;
