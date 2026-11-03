-- Pin the support-admin helper to trusted schemas only.
ALTER FUNCTION public.qabas_is_support_admin() SET search_path = pg_catalog, auth;
