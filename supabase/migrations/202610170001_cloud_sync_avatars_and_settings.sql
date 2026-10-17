-- ==============================================================================
-- مزامنة التفضيلات السحابية: ضمان جداول الإعدادات/الملفات وسلة الصور الشخصية
-- قابل للتشغيل أكثر من مرة (idempotent). نفّذه في Supabase SQL Editor.
-- ==============================================================================

-- 1) جداول المزامنة
CREATE TABLE IF NOT EXISTS public.profiles (
    id TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
    email TEXT,
    full_name TEXT,
    avatar_url TEXT,
    role TEXT DEFAULT 'user',
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.user_settings (
    user_id TEXT PRIMARY KEY,
    preferences_json TEXT DEFAULT '{}',
    updated_at TIMESTAMPTZ DEFAULT now()
);

-- 2) سياسات RLS + الصلاحيات (مطلوبة لعملية upsert من التطبيق)
DO $$
DECLARE
    tbl TEXT;
    tbls TEXT[] := ARRAY['profiles', 'user_settings'];
BEGIN
    FOREACH tbl IN ARRAY tbls LOOP
        EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY;', tbl);
        EXECUTE format('DROP POLICY IF EXISTS "Public select policy on %I" ON public.%I;', tbl, tbl);
        EXECUTE format('CREATE POLICY "Public select policy on %I" ON public.%I FOR SELECT USING (true);', tbl, tbl);
        EXECUTE format('DROP POLICY IF EXISTS "Public insert policy on %I" ON public.%I;', tbl, tbl);
        EXECUTE format('CREATE POLICY "Public insert policy on %I" ON public.%I FOR INSERT WITH CHECK (true);', tbl, tbl);
        EXECUTE format('DROP POLICY IF EXISTS "Public update policy on %I" ON public.%I;', tbl, tbl);
        EXECUTE format('CREATE POLICY "Public update policy on %I" ON public.%I FOR UPDATE USING (true) WITH CHECK (true);', tbl, tbl);
        EXECUTE format('DROP POLICY IF EXISTS "Public delete policy on %I" ON public.%I;', tbl, tbl);
        EXECUTE format('CREATE POLICY "Public delete policy on %I" ON public.%I FOR DELETE USING (true);', tbl, tbl);
        EXECUTE format('GRANT ALL ON TABLE public.%I TO anon, authenticated, service_role;', tbl);
    END LOOP;
END $$;

-- 3) سلة الصور الشخصية (عامة، صور فقط، حد 10MB)
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES ('avatars', 'avatars', true, 10485760, ARRAY['image/jpeg', 'image/png', 'image/webp'])
ON CONFLICT (id) DO UPDATE SET
    public = true,
    file_size_limit = EXCLUDED.file_size_limit,
    allowed_mime_types = EXCLUDED.allowed_mime_types;

-- 4) سياسات عناصر التخزين (لعرض/رفع/استبدال الصورة)
DROP POLICY IF EXISTS "Allow public select on storage objects" ON storage.objects;
CREATE POLICY "Allow public select on storage objects" ON storage.objects FOR SELECT USING (true);

DROP POLICY IF EXISTS "Allow public insert on storage objects" ON storage.objects;
CREATE POLICY "Allow public insert on storage objects" ON storage.objects FOR INSERT WITH CHECK (true);

DROP POLICY IF EXISTS "Allow public update on storage objects" ON storage.objects;
CREATE POLICY "Allow public update on storage objects" ON storage.objects FOR UPDATE USING (true);

DROP POLICY IF EXISTS "Allow public delete on storage objects" ON storage.objects;
CREATE POLICY "Allow public delete on storage objects" ON storage.objects FOR DELETE USING (true);
