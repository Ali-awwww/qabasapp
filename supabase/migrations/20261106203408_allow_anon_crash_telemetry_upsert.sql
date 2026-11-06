-- Allow anonymous CrashTelemetry upserts to resolve duplicate IDs.
-- RLS remains enabled and there is intentionally NO SELECT policy for anon,
-- so this SQL privilege does not make crash reports readable by public clients.
GRANT SELECT ON TABLE public.dev_bug_reports TO anon;
