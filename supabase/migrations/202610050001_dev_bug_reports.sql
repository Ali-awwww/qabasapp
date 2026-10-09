-- Migration: Dev Bug Reports & User Feedback System
-- Schema for public.dev_bug_reports connected with Qabas App

CREATE TABLE IF NOT EXISTS public.dev_bug_reports (
    id TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
    user_email TEXT NOT NULL DEFAULT 'anonymous',
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

-- Enable RLS
ALTER TABLE public.dev_bug_reports ENABLE ROW LEVEL SECURITY;

-- 1. السماح للجميع ومستخدمي التطبيق بإرسال بلاغات الأعطال
DROP POLICY IF EXISTS "Allow public insert dev_bug_reports" ON public.dev_bug_reports;
CREATE POLICY "Allow public insert dev_bug_reports"
    ON public.dev_bug_reports
    FOR INSERT
    WITH CHECK (true);

-- 2. السماح بقراءة البلاغات للاستعراض
DROP POLICY IF EXISTS "Allow public select dev_bug_reports" ON public.dev_bug_reports;
CREATE POLICY "Allow public select dev_bug_reports"
    ON public.dev_bug_reports
    FOR SELECT
    USING (true);

-- 3. السماح بتحديث الحالة ورد المطور
DROP POLICY IF EXISTS "Allow update dev_bug_reports" ON public.dev_bug_reports;
CREATE POLICY "Allow update dev_bug_reports"
    ON public.dev_bug_reports
    FOR UPDATE
    USING (true)
    WITH CHECK (true);

-- 4. السماح بحذف البلاغات
DROP POLICY IF EXISTS "Allow delete dev_bug_reports" ON public.dev_bug_reports;
CREATE POLICY "Allow delete dev_bug_reports"
    ON public.dev_bug_reports
    FOR DELETE
    USING (true);

-- الفهارس لتحسين سرعة الاستعلامات والترتيب
CREATE INDEX IF NOT EXISTS idx_dev_bug_reports_created_at ON public.dev_bug_reports(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_dev_bug_reports_status ON public.dev_bug_reports(status);
CREATE INDEX IF NOT EXISTS idx_dev_bug_reports_type ON public.dev_bug_reports(report_type);
