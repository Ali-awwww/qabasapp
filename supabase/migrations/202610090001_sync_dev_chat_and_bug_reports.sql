-- Migration: Sync Dev Chat and Bug Reports (PostgreSQL / Supabase)
-- 2026-10-09: Ensures reliable schema and RLS for instant messaging and bug reports

-- ==========================================================
-- 1. جدول رسائل المطور (dev_chat_messages)
-- ==========================================================
CREATE TABLE IF NOT EXISTS public.dev_chat_messages (
    id TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
    user_id TEXT,
    user_email TEXT NOT NULL DEFAULT 'anonymous',
    sender TEXT NOT NULL DEFAULT 'user', -- 'user' | 'developer'
    message TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- إضافة عمود user_id في حال كان الجدول موجوداً مسبقاً بدونه
ALTER TABLE public.dev_chat_messages ADD COLUMN IF NOT EXISTS user_id TEXT;
ALTER TABLE public.dev_chat_messages ADD COLUMN IF NOT EXISTS user_email TEXT NOT NULL DEFAULT 'anonymous';

-- تفعيل سياسات الأمان RLS
ALTER TABLE public.dev_chat_messages ENABLE ROW LEVEL SECURITY;

-- السماح بإدراج الرسائل (من المستخدمين والمطورين)
DROP POLICY IF EXISTS "Allow public insert dev_chat_messages" ON public.dev_chat_messages;
DROP POLICY IF EXISTS "Users can insert own dev_chat_messages" ON public.dev_chat_messages;
CREATE POLICY "Allow public insert dev_chat_messages"
    ON public.dev_chat_messages
    FOR INSERT
    WITH CHECK (true);

-- السماح بقراءة الرسائل (لتظهر للمطور في اللوحة وللمستخدم في محادثته)
DROP POLICY IF EXISTS "Allow public select dev_chat_messages" ON public.dev_chat_messages;
DROP POLICY IF EXISTS "Users can view own dev_chat_messages" ON public.dev_chat_messages;
CREATE POLICY "Allow public select dev_chat_messages"
    ON public.dev_chat_messages
    FOR SELECT
    USING (true);

-- فهارس السرعة
CREATE INDEX IF NOT EXISTS idx_dev_chat_messages_email ON public.dev_chat_messages(user_email, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_dev_chat_messages_created ON public.dev_chat_messages(created_at DESC);


-- ==========================================================
-- 2. جدول بلاغات الأعطال والتشخيص (dev_bug_reports)
-- ==========================================================
CREATE TABLE IF NOT EXISTS public.dev_bug_reports (
    id TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
    user_email TEXT NOT NULL DEFAULT 'user@qabas.app',
    screen_name TEXT NOT NULL DEFAULT '',
    description TEXT NOT NULL DEFAULT '',
    image_url TEXT NOT NULL DEFAULT '',
    report_type TEXT NOT NULL DEFAULT 'BUG',
    status TEXT NOT NULL DEFAULT 'open',
    app_version TEXT NOT NULL DEFAULT '',
    device_info TEXT NOT NULL DEFAULT '',
    developer_reply TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE public.dev_bug_reports ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Allow public insert dev_bug_reports" ON public.dev_bug_reports;
CREATE POLICY "Allow public insert dev_bug_reports"
    ON public.dev_bug_reports
    FOR INSERT
    WITH CHECK (true);

DROP POLICY IF EXISTS "Allow public select dev_bug_reports" ON public.dev_bug_reports;
CREATE POLICY "Allow public select dev_bug_reports"
    ON public.dev_bug_reports
    FOR SELECT
    USING (true);

DROP POLICY IF EXISTS "Allow update dev_bug_reports" ON public.dev_bug_reports;
CREATE POLICY "Allow update dev_bug_reports"
    ON public.dev_bug_reports
    FOR UPDATE
    USING (true)
    WITH CHECK (true);

DROP POLICY IF EXISTS "Allow delete dev_bug_reports" ON public.dev_bug_reports;
CREATE POLICY "Allow delete dev_bug_reports"
    ON public.dev_bug_reports
    FOR DELETE
    USING (true);

CREATE INDEX IF NOT EXISTS idx_dev_bug_reports_created_at ON public.dev_bug_reports(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_dev_bug_reports_status ON public.dev_bug_reports(status);
CREATE INDEX IF NOT EXISTS idx_dev_bug_reports_type ON public.dev_bug_reports(report_type);


-- ==========================================================
-- 3. سلة التخزين للقطات الشاشة (Storage Bucket: bug_reports)
-- ==========================================================
INSERT INTO storage.buckets (id, name, public)
VALUES ('bug_reports', 'bug_reports', true)
ON CONFLICT (id) DO UPDATE SET public = true;

-- سياسات تخزين الصور
DROP POLICY IF EXISTS "Public can upload bug reports images" ON storage.objects;
CREATE POLICY "Public can upload bug reports images"
    ON storage.objects FOR INSERT
    WITH CHECK (bucket_id = 'bug_reports');

DROP POLICY IF EXISTS "Public can view bug reports images" ON storage.objects;
CREATE POLICY "Public can view bug reports images"
    ON storage.objects FOR SELECT
    USING (bucket_id = 'bug_reports');
