-- ==============================================================================
-- سحابة قبس (Qabas) - جداول الريلز، الإعجابات، التعليقات، المحفوظات، وسجل المشاهدات
-- Qabas Supabase Migration: Full Reels, Interactions & History Hub
-- ==============================================================================

-- 1. جدول مقاطع الريلز (Reels Video Posts Table)
CREATE TABLE IF NOT EXISTS public.reels (
    id TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
    user_id TEXT NOT NULL,
    title TEXT NOT NULL,
    description TEXT,
    video_url TEXT NOT NULL,
    thumbnail_url TEXT,
    tags JSONB DEFAULT '[]'::jsonb,
    likes_count INTEGER DEFAULT 0,
    comments_count INTEGER DEFAULT 0,
    shares_count INTEGER DEFAULT 0,
    views_count INTEGER DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_reels_active_created ON public.reels(is_active, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_reels_user_id ON public.reels(user_id);

-- 2. جدول إعجابات الريلز (Reel Likes)
CREATE TABLE IF NOT EXISTS public.reel_likes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reel_id TEXT NOT NULL REFERENCES public.reels(id) ON DELETE CASCADE,
    user_id TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT unique_reel_user_like UNIQUE (reel_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_reel_likes_reel_id ON public.reel_likes(reel_id);
CREATE INDEX IF NOT EXISTS idx_reel_likes_user_id ON public.reel_likes(user_id);

-- 3. جدول تعليقات الريلز (Reel Comments)
CREATE TABLE IF NOT EXISTS public.reel_comments (
    id TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
    reel_id TEXT NOT NULL REFERENCES public.reels(id) ON DELETE CASCADE,
    user_id TEXT NOT NULL,
    comment_text TEXT NOT NULL,
    likes_count INTEGER DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_reel_comments_reel_id ON public.reel_comments(reel_id, created_at DESC);

-- 4. جدول محفوظات الريلز (Reel Bookmarks / Saved)
CREATE TABLE IF NOT EXISTS public.reel_bookmarks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reel_id TEXT NOT NULL REFERENCES public.reels(id) ON DELETE CASCADE,
    user_id TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT unique_reel_user_bookmark UNIQUE (reel_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_reel_bookmarks_user ON public.reel_bookmarks(user_id, created_at DESC);

-- 5. جدول سجل مشاهدات ونشاط الريلز (Reel Watch History & Activity)
CREATE TABLE IF NOT EXISTS public.reel_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reel_id TEXT NOT NULL REFERENCES public.reels(id) ON DELETE CASCADE,
    user_id TEXT NOT NULL,
    watched_duration_sec INTEGER DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_reel_history_user ON public.reel_history(user_id, created_at DESC);

-- 6. جدول مسودات وأفكار الريلز المنتجة (Reels Studio Feed / MoneyPrinter)
CREATE TABLE IF NOT EXISTS public.reels_feed (
    id TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
    user_id TEXT,
    user_name TEXT DEFAULT 'صانع محتوى',
    title TEXT NOT NULL,
    topic TEXT,
    hook TEXT,
    script_body TEXT,
    cta TEXT,
    likes_count INTEGER DEFAULT 0,
    shares_count INTEGER DEFAULT 0,
    duration_seconds INTEGER DEFAULT 15,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- ==============================================================================
-- 7. دوال ومحفزات التحديث التلقائي للعدادات (Counters Auto-Increment Triggers)
-- ==============================================================================

-- دالة زيادة ونقصان الإعجابات تلقائياً
CREATE OR REPLACE FUNCTION public.handle_reel_like_counter()
RETURNS TRIGGER AS $$
BEGIN
    IF (TG_OP = 'INSERT') THEN
        UPDATE public.reels
        SET likes_count = likes_count + 1
        WHERE id = NEW.reel_id;
        RETURN NEW;
    ELSIF (TG_OP = 'DELETE') THEN
        UPDATE public.reels
        SET likes_count = GREATEST(0, likes_count - 1)
        WHERE id = OLD.reel_id;
        RETURN OLD;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS trg_reel_like_counter ON public.reel_likes;
CREATE TRIGGER trg_reel_like_counter
AFTER INSERT OR DELETE ON public.reel_likes
FOR EACH ROW EXECUTE FUNCTION public.handle_reel_like_counter();

-- دالة زيادة ونقصان التعليقات تلقائياً
CREATE OR REPLACE FUNCTION public.handle_reel_comment_counter()
RETURNS TRIGGER AS $$
BEGIN
    IF (TG_OP = 'INSERT') THEN
        UPDATE public.reels
        SET comments_count = comments_count + 1
        WHERE id = NEW.reel_id;
        RETURN NEW;
    ELSIF (TG_OP = 'DELETE') THEN
        UPDATE public.reels
        SET comments_count = GREATEST(0, comments_count - 1)
        WHERE id = OLD.reel_id;
        RETURN OLD;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS trg_reel_comment_counter ON public.reel_comments;
CREATE TRIGGER trg_reel_comment_counter
AFTER INSERT OR DELETE ON public.reel_comments
FOR EACH ROW EXECUTE FUNCTION public.handle_reel_comment_counter();

-- ==============================================================================
-- 8. تفعيل سياسات الأمان والحماية (Row Level Security - RLS)
-- ==============================================================================
ALTER TABLE public.reels ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.reel_likes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.reel_comments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.reel_bookmarks ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.reel_history ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.reels_feed ENABLE ROW LEVEL SECURITY;

-- السماح بالقراءة العامة لجميع المستخدمين
CREATE POLICY "Allow public select on reels" ON public.reels FOR SELECT USING (true);
CREATE POLICY "Allow public select on reel_likes" ON public.reel_likes FOR SELECT USING (true);
CREATE POLICY "Allow public select on reel_comments" ON public.reel_comments FOR SELECT USING (true);
CREATE POLICY "Allow public select on reel_bookmarks" ON public.reel_bookmarks FOR SELECT USING (true);
CREATE POLICY "Allow public select on reel_history" ON public.reel_history FOR SELECT USING (true);
CREATE POLICY "Allow public select on reels_feed" ON public.reels_feed FOR SELECT USING (true);

-- السماح بالإدراج والتعديل للتطبيق
CREATE POLICY "Allow public insert on reels" ON public.reels FOR INSERT WITH CHECK (true);
CREATE POLICY "Allow public update on reels" ON public.reels FOR UPDATE USING (true);

CREATE POLICY "Allow public insert on reel_likes" ON public.reel_likes FOR INSERT WITH CHECK (true);
CREATE POLICY "Allow public delete on reel_likes" ON public.reel_likes FOR DELETE USING (true);

CREATE POLICY "Allow public insert on reel_comments" ON public.reel_comments FOR INSERT WITH CHECK (true);
CREATE POLICY "Allow public insert on reel_bookmarks" ON public.reel_bookmarks FOR INSERT WITH CHECK (true);
CREATE POLICY "Allow public delete on reel_bookmarks" ON public.reel_bookmarks FOR DELETE USING (true);

CREATE POLICY "Allow public insert on reel_history" ON public.reel_history FOR INSERT WITH CHECK (true);
CREATE POLICY "Allow public insert on reels_feed" ON public.reels_feed FOR INSERT WITH CHECK (true);

-- ==============================================================================
-- 9. منح الصلاحيات لمفتاح التطبيق (Grant Permissions to anon & authenticated)
-- ==============================================================================
GRANT ALL ON TABLE public.reels TO anon, authenticated;
GRANT ALL ON TABLE public.reel_likes TO anon, authenticated;
GRANT ALL ON TABLE public.reel_comments TO anon, authenticated;
GRANT ALL ON TABLE public.reel_bookmarks TO anon, authenticated;
GRANT ALL ON TABLE public.reel_history TO anon, authenticated;
GRANT ALL ON TABLE public.reels_feed TO anon, authenticated;

-- ==============================================================================
-- 10. إعداد صندوق تخزين ملفات الفيديو (Storage Bucket: reels)
-- ==============================================================================
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
    'reels',
    'reels',
    true,
    52428800, -- 50 MB
    ARRAY['video/mp4', 'video/quicktime', 'video/webm', 'image/jpeg', 'image/png']
)
ON CONFLICT (id) DO UPDATE SET public = true;

CREATE POLICY "Allow public read on reels storage"
ON storage.objects FOR SELECT
USING (bucket_id = 'reels');

CREATE POLICY "Allow public upload on reels storage"
ON storage.objects FOR INSERT
WITH CHECK (bucket_id = 'reels');

-- مسح أي بيانات تجريبية سابقة إن وُجدت
DELETE FROM public.reels WHERE id LIKE 'qabas_reel_%';

