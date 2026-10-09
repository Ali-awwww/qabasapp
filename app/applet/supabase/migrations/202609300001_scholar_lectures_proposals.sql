-- ==============================================================================
-- سحابة قبس (Qabas) - جداول المحاضرات والدروس ومقترحات المستخدمين
-- Qabas Supabase Migration: Scholar Lectures & Community Proposals
-- ==============================================================================

-- 1. جدول الدروس والمحاضرات المعتمدة (Scholar Curated & Official Lectures)
CREATE TABLE IF NOT EXISTS public.scholar_lectures (
    id TEXT PRIMARY KEY,
    scholar_id TEXT NOT NULL,
    scholar_name TEXT,
    title TEXT NOT NULL,
    speaker TEXT DEFAULT '',
    url TEXT NOT NULL,
    type TEXT DEFAULT 'يوتيوب / مرئي',
    category TEXT DEFAULT 'سيرة وعبر',
    synopsis TEXT DEFAULT '',
    duration_approx TEXT DEFAULT '',
    is_official BOOLEAN DEFAULT TRUE,
    notes TEXT DEFAULT '',
    added_at_millis BIGINT DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- فهارس للبحث السريع حسب الشخصية
CREATE INDEX IF NOT EXISTS idx_scholar_lectures_scholar_id ON public.scholar_lectures(scholar_id);
CREATE INDEX IF NOT EXISTS idx_scholar_lectures_created_at ON public.scholar_lectures(created_at DESC);

-- 2. جدول مقترحات ومساهمات المستخدمين (Community User Proposals)
CREATE TABLE IF NOT EXISTS public.scholar_proposals (
    id TEXT PRIMARY KEY,
    scholar_id TEXT NOT NULL,
    scholar_name TEXT,
    suggested_title TEXT NOT NULL,
    suggested_speaker TEXT DEFAULT '',
    suggested_url TEXT DEFAULT '',
    suggested_topic TEXT DEFAULT 'سيرة وعبر',
    rationale TEXT DEFAULT '',
    user_proposer_name TEXT DEFAULT 'طالب علم',
    upvotes_count INTEGER DEFAULT 1,
    status TEXT DEFAULT 'قيد المراجعة العلمية',
    created_at_millis BIGINT DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- فهارس للبحث والترتيب حسب التصويتات
CREATE INDEX IF NOT EXISTS idx_scholar_proposals_scholar_id ON public.scholar_proposals(scholar_id);
CREATE INDEX IF NOT EXISTS idx_scholar_proposals_upvotes ON public.scholar_proposals(upvotes_count DESC);

-- 3. تفعيل سياسات الأمان (Row Level Security - RLS)
ALTER TABLE public.scholar_lectures ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.scholar_proposals ENABLE ROW LEVEL SECURITY;

-- سياسات جدول الدروس والمحاضرات (الجميع يستطيع القراءة، والتطبيق يضيف ويحدث)
DROP POLICY IF EXISTS "Public select on scholar_lectures" ON public.scholar_lectures;
CREATE POLICY "Public select on scholar_lectures" 
    ON public.scholar_lectures FOR SELECT 
    USING (true);

DROP POLICY IF EXISTS "Public insert on scholar_lectures" ON public.scholar_lectures;
CREATE POLICY "Public insert on scholar_lectures" 
    ON public.scholar_lectures FOR INSERT 
    WITH CHECK (true);

DROP POLICY IF EXISTS "Public update on scholar_lectures" ON public.scholar_lectures;
CREATE POLICY "Public update on scholar_lectures" 
    ON public.scholar_lectures FOR UPDATE 
    USING (true);

DROP POLICY IF EXISTS "Public delete on scholar_lectures" ON public.scholar_lectures;
CREATE POLICY "Public delete on scholar_lectures" 
    ON public.scholar_lectures FOR DELETE 
    USING (true);

-- سياسات جدول مقترحات المستخدمين
DROP POLICY IF EXISTS "Public select on scholar_proposals" ON public.scholar_proposals;
CREATE POLICY "Public select on scholar_proposals" 
    ON public.scholar_proposals FOR SELECT 
    USING (true);

DROP POLICY IF EXISTS "Public insert on scholar_proposals" ON public.scholar_proposals;
CREATE POLICY "Public insert on scholar_proposals" 
    ON public.scholar_proposals FOR INSERT 
    WITH CHECK (true);

DROP POLICY IF EXISTS "Public update on scholar_proposals" ON public.scholar_proposals;
CREATE POLICY "Public update on scholar_proposals" 
    ON public.scholar_proposals FOR UPDATE 
    USING (true);

DROP POLICY IF EXISTS "Public delete on scholar_proposals" ON public.scholar_proposals;
CREATE POLICY "Public delete on scholar_proposals" 
    ON public.scholar_proposals FOR DELETE 
    USING (true);
