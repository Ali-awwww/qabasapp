-- ==============================================================================
-- جدول صنّاع المحتوى الموثّقين (شارة التوثيق) — يمنحها المطوّر فقط.
-- قابل للتشغيل أكثر من مرة (idempotent). نفّذه في Supabase SQL Editor.
-- ==============================================================================

CREATE TABLE IF NOT EXISTS public.verified_creators (
    id TEXT PRIMARY KEY,
    granted_by TEXT,
    created_at TIMESTAMPTZ DEFAULT now()
);

ALTER TABLE public.verified_creators ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Public select policy on verified_creators" ON public.verified_creators;
CREATE POLICY "Public select policy on verified_creators" ON public.verified_creators FOR SELECT USING (true);

DROP POLICY IF EXISTS "Public insert policy on verified_creators" ON public.verified_creators;
CREATE POLICY "Public insert policy on verified_creators" ON public.verified_creators FOR INSERT WITH CHECK (true);

DROP POLICY IF EXISTS "Public update policy on verified_creators" ON public.verified_creators;
CREATE POLICY "Public update policy on verified_creators" ON public.verified_creators FOR UPDATE USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "Public delete policy on verified_creators" ON public.verified_creators;
CREATE POLICY "Public delete policy on verified_creators" ON public.verified_creators FOR DELETE USING (true);

GRANT ALL ON TABLE public.verified_creators TO anon, authenticated, service_role;
