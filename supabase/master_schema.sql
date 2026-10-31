-- ==============================================================================
-- 🏛️ سحابة قبس (Qabas Ecosystem) - مخطط قاعدة البيانات الموحد والشامل
-- MASTER SUPABASE SCHEMA: Tables, Views, Triggers, RLS Policies & Storage
-- المتوافق بنسبة 100% مع تطبيق «استوديو قبس | Qabas Studio» الإصدار 1.3.0
-- ==============================================================================

-- تفعيل الملحقات الأساسية
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ==============================================================================
-- 1. جداول المستخدمين، الهوية، والصلاحيات
-- ==============================================================================

-- جدول المستخدمين الرئيسي (users)
CREATE TABLE IF NOT EXISTS public.users (
    id TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
    external_id TEXT,
    email TEXT,
    name TEXT,
    type TEXT NOT NULL DEFAULT 'مجاني', -- 'مجاني' | 'مطور' | 'خاص'
    status TEXT NOT NULL DEFAULT 'active', -- 'active' | 'suspended'
    strikes INTEGER DEFAULT 0,
    is_eligible BOOLEAN DEFAULT true,
    custom_id TEXT,
    account_number TEXT,
    active_session_id TEXT,
    session_updated_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_users_email ON public.users (lower(email));
CREATE INDEX IF NOT EXISTS idx_users_custom_id ON public.users (lower(custom_id));
CREATE INDEX IF NOT EXISTS idx_users_account_number ON public.users (account_number);
CREATE INDEX IF NOT EXISTS idx_users_external_id ON public.users (external_id);

-- جدول الملفات الشخصية (profiles)
CREATE TABLE IF NOT EXISTS public.profiles (
    id TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
    email TEXT,
    full_name TEXT,
    avatar_url TEXT,
    role TEXT DEFAULT 'user',
    created_at TIMESTAMPTZ DEFAULT now()
);

-- جدول إعدادات وتفضيلات المستخدمين (user_settings)
CREATE TABLE IF NOT EXISTS public.user_settings (
    user_id TEXT PRIMARY KEY,
    preferences_json TEXT DEFAULT '{}',
    updated_at TIMESTAMPTZ DEFAULT now()
);

-- جدول مفاتيح المنصة الآمنة (platform_api_keys)
CREATE TABLE IF NOT EXISTS public.platform_api_keys (
    service TEXT PRIMARY KEY,
    ciphertext TEXT NOT NULL,
    nonce TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by UUID
);

-- ==============================================================================
-- 2. جداول المشاريع، الأنماط، الأكواد الترويجية، والمعاملات
-- ==============================================================================

-- جدول المشاريع ومسودات الاستوديو (projects)
CREATE TABLE IF NOT EXISTS public.projects (
    id TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
    user_id TEXT,
    external_id TEXT,
    title TEXT NOT NULL,
    description TEXT,
    status TEXT DEFAULT 'draft',
    cost DOUBLE PRECISION DEFAULT 0.0,
    tags JSONB DEFAULT '[]'::jsonb,
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_projects_user_id ON public.projects (user_id);
CREATE INDEX IF NOT EXISTS idx_projects_created_at ON public.projects (created_at DESC);

-- جدول أنماط المونتاج الشخصية (styles)
CREATE TABLE IF NOT EXISTS public.styles (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    name TEXT NOT NULL,
    source_video TEXT,
    payload JSONB DEFAULT '{}'::jsonb,
    score INTEGER DEFAULT 0,
    tags JSONB DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()),
    updated_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now())
);

CREATE INDEX IF NOT EXISTS idx_styles_user_id ON public.styles (user_id);

-- جدول أنماط الماستر المعتمدة (master_styles)
CREATE TABLE IF NOT EXISTS public.master_styles (
    id TEXT PRIMARY KEY,
    user_id TEXT,
    name TEXT NOT NULL,
    description TEXT,
    payload JSONB DEFAULT '{}'::jsonb,
    compliance INTEGER DEFAULT 95,
    created_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()),
    updated_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now())
);

-- جدول الأكواد الترويجية والهدايا (promo_codes)
CREATE TABLE IF NOT EXISTS public.promo_codes (
    code TEXT PRIMARY KEY,
    type TEXT DEFAULT 'PROMO', -- 'PROMO' | 'GIFT'
    value BIGINT DEFAULT 0,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now())
);

-- جدول المعاملات والاشتراكات (transactions)
CREATE TABLE IF NOT EXISTS public.transactions (
    id TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
    user_id TEXT,
    product_id TEXT DEFAULT '',
    purchase_token TEXT DEFAULT '',
    price_amount DOUBLE PRECISION DEFAULT 0.0,
    currency TEXT DEFAULT 'USD',
    created_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now())
);

CREATE INDEX IF NOT EXISTS idx_transactions_user_id ON public.transactions (user_id);

-- جدول سجلات فحص المحتوى والأمان (content_guard_logs)
CREATE TABLE IF NOT EXISTS public.content_guard_logs (
    id TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
    user_id TEXT,
    snippet TEXT DEFAULT '',
    verdict TEXT DEFAULT '',
    score INTEGER DEFAULT 0,
    reason TEXT DEFAULT '',
    created_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now())
);

-- ==============================================================================
-- 3. منظومة الريلز والتفاعل الكاملة (Reels Hub & Social Interactions)
-- ==============================================================================

-- جدول مقاطع الريلز (reels)
CREATE TABLE IF NOT EXISTS public.reels (
    id TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
    user_id TEXT NOT NULL,
    user_name TEXT DEFAULT 'صانع محتوى قبس',
    user_avatar TEXT,
    title TEXT NOT NULL,
    description TEXT,
    video_url TEXT NOT NULL,
    thumbnail_url TEXT,
    tags JSONB DEFAULT '[]'::jsonb,
    likes_count INTEGER DEFAULT 0,
    comments_count INTEGER DEFAULT 0,
    shares_count INTEGER DEFAULT 0,
    views_count INTEGER DEFAULT 0,
    is_active BOOLEAN DEFAULT true,
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_reels_active_created ON public.reels (is_active, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_reels_user_id ON public.reels (user_id);

-- جدول إعجابات الريلز (reel_likes) لمنع تكرار الإعجاب
CREATE TABLE IF NOT EXISTS public.reel_likes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reel_id TEXT NOT NULL REFERENCES public.reels(id) ON DELETE CASCADE,
    user_id TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL,
    CONSTRAINT unique_reel_user_like UNIQUE (reel_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_reel_likes_reel ON public.reel_likes (reel_id);
CREATE INDEX IF NOT EXISTS idx_reel_likes_user ON public.reel_likes (user_id);

-- جدول تعليقات الريلز (reel_comments)
CREATE TABLE IF NOT EXISTS public.reel_comments (
    id TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
    reel_id TEXT NOT NULL REFERENCES public.reels(id) ON DELETE CASCADE,
    user_id TEXT NOT NULL,
    comment_text TEXT NOT NULL,
    likes_count INTEGER DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_reel_comments_reel_id ON public.reel_comments (reel_id, created_at DESC);

-- جدول محفوظات الريلز (reel_bookmarks)
CREATE TABLE IF NOT EXISTS public.reel_bookmarks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reel_id TEXT NOT NULL REFERENCES public.reels(id) ON DELETE CASCADE,
    user_id TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now(),
    CONSTRAINT unique_reel_user_bookmark UNIQUE (reel_id, user_id)
);

-- جدول سجل المشاهدة (reel_history)
CREATE TABLE IF NOT EXISTS public.reel_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reel_id TEXT NOT NULL REFERENCES public.reels(id) ON DELETE CASCADE,
    user_id TEXT NOT NULL,
    watched_duration_sec INTEGER DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT now()
);

-- جدول تغذية الريلز التوليدية (reels_feed)
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
    created_at TIMESTAMPTZ DEFAULT now()
);

-- ==============================================================================
-- 4. الجناح الإسلامي، التلاوات، الأحاديث، ودروس العلماء
-- ==============================================================================

-- جدول المقاطع الصوتية والتلاوات (audio_tracks)
CREATE TABLE IF NOT EXISTS public.audio_tracks (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    artist TEXT DEFAULT '',
    category TEXT DEFAULT 'تلاوات قرآنية',
    audio_url TEXT NOT NULL,
    duration TEXT DEFAULT '0:00',
    badge TEXT DEFAULT 'سحابي',
    author TEXT DEFAULT '',
    likes_count INTEGER DEFAULT 0,
    is_active BOOLEAN DEFAULT true,
    user_id TEXT,
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_audio_tracks_active ON public.audio_tracks (is_active, created_at DESC);

-- جدول قوالب الأحاديث الشريفة (hadith_presets)
CREATE TABLE IF NOT EXISTS public.hadith_presets (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    narrator TEXT DEFAULT '',
    text TEXT NOT NULL,
    status TEXT DEFAULT 'صحيح',
    source TEXT DEFAULT '',
    theme_bg TEXT DEFAULT 'black_gold',
    suggested_aspect TEXT DEFAULT '9:16',
    category TEXT DEFAULT 'أخلاق وآداب',
    display_order INTEGER DEFAULT 0,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT now()
);

-- جدول دروس ومحاضرات العلماء (scholar_lectures)
CREATE TABLE IF NOT EXISTS public.scholar_lectures (
    id TEXT PRIMARY KEY,
    scholar_id TEXT NOT NULL,
    scholar_name TEXT DEFAULT '',
    title TEXT NOT NULL,
    speaker TEXT DEFAULT '',
    url TEXT NOT NULL,
    type TEXT DEFAULT 'يوتيوب / مرئي',
    category TEXT DEFAULT 'سيرة وعبر',
    synopsis TEXT DEFAULT '',
    duration_approx TEXT DEFAULT '',
    is_official BOOLEAN DEFAULT true,
    notes TEXT DEFAULT '',
    added_at_millis BIGINT DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_scholar_lectures_scholar ON public.scholar_lectures (scholar_id);

-- جدول مقترحات ومساهمات المجتمع العلمية (scholar_proposals)
CREATE TABLE IF NOT EXISTS public.scholar_proposals (
    id TEXT PRIMARY KEY,
    scholar_id TEXT NOT NULL,
    scholar_name TEXT DEFAULT '',
    suggested_title TEXT NOT NULL,
    suggested_speaker TEXT DEFAULT '',
    suggested_url TEXT DEFAULT '',
    suggested_topic TEXT DEFAULT 'سيرة وعبر',
    rationale TEXT DEFAULT '',
    user_proposer_name TEXT DEFAULT 'طالب علم',
    upvotes_count INTEGER DEFAULT 1,
    status TEXT DEFAULT 'قيد المراجعة العلمية',
    created_at_millis BIGINT DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_scholar_proposals_upvotes ON public.scholar_proposals (upvotes_count DESC);

-- ==============================================================================
-- 5. التحديثات، التقييمات، الدردشة، وتقارير الأعطال
-- ==============================================================================

-- جدول تحديثات التطبيق الرسمية (app_updates)
CREATE TABLE IF NOT EXISTS public.app_updates (
    id TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
    version_code INTEGER NOT NULL,
    version_name TEXT NOT NULL,
    title TEXT DEFAULT '',
    release_notes TEXT DEFAULT '',
    download_url TEXT NOT NULL,
    sha256_hash TEXT DEFAULT '',
    is_mandatory BOOLEAN DEFAULT false,
    is_active BOOLEAN DEFAULT true,
    file_size_bytes BIGINT DEFAULT 0,
    released_at TIMESTAMPTZ DEFAULT now(),
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_app_updates_version ON public.app_updates (version_code DESC);

-- جدول مراجعات وتقييمات المستخدمين (app_reviews)
CREATE TABLE IF NOT EXISTS public.app_reviews (
    id TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
    author_name TEXT NOT NULL DEFAULT 'مستخدم قبس',
    rating INTEGER NOT NULL CHECK (rating >= 1 AND rating <= 5),
    category_rating TEXT NOT NULL DEFAULT 'overall',
    app_version TEXT NOT NULL DEFAULT 'v1.3.0',
    device_model TEXT NOT NULL DEFAULT '',
    comment TEXT NOT NULL DEFAULT '',
    helpful_count INTEGER NOT NULL DEFAULT 0,
    verified_user BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_app_reviews_created ON public.app_reviews (created_at DESC);

-- جدول رسائل المطور المباشرة (dev_chat_messages) وجدول بديل (chat_messages)
CREATE TABLE IF NOT EXISTS public.dev_chat_messages (
    id TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
    user_id TEXT,
    user_email TEXT NOT NULL DEFAULT 'anonymous',
    account_number TEXT,
    sender TEXT NOT NULL DEFAULT 'user', -- 'user' | 'developer'
    message TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.chat_messages (
    id TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
    user_id TEXT,
    user_email TEXT NOT NULL DEFAULT 'anonymous',
    account_number TEXT,
    sender TEXT NOT NULL DEFAULT 'user',
    message TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_dev_chat_email ON public.dev_chat_messages (user_email, created_at ASC);

-- جدول بلاغات الأعطال والمشاكل (dev_bug_reports) والجدولين البديلين
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

CREATE TABLE IF NOT EXISTS public.bug_reports (
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

CREATE TABLE IF NOT EXISTS public.user_feedbacks (
    id TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
    user_email TEXT NOT NULL DEFAULT 'user@qabas.app',
    screen_name TEXT NOT NULL DEFAULT '',
    description TEXT NOT NULL DEFAULT '',
    image_url TEXT NOT NULL DEFAULT '',
    report_type TEXT NOT NULL DEFAULT 'FEEDBACK',
    status TEXT NOT NULL DEFAULT 'open',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_dev_bug_reports_status ON public.dev_bug_reports (status, created_at DESC);

-- جدول دوري النقاط والمتصدرين (league_leaderboard)
CREATE TABLE IF NOT EXISTS public.league_leaderboard (
    id TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
    user_id TEXT,
    name TEXT DEFAULT 'صانع محتوى',
    points BIGINT DEFAULT 0,
    is_eligible BOOLEAN DEFAULT true,
    avatar_url TEXT,
    last_won_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_leaderboard_points ON public.league_leaderboard (points DESC);

-- ==============================================================================
-- 6. محفزات ودوال التحديث التلقائي للعدادات (Counters Triggers)
-- ==============================================================================

-- محفز تحديث عداد إعجابات الريلز تلقائياً
CREATE OR REPLACE FUNCTION public.handle_reel_like_counter()
RETURNS TRIGGER AS $$
BEGIN
    IF (TG_OP = 'INSERT') THEN
        UPDATE public.reels SET likes_count = likes_count + 1 WHERE id = NEW.reel_id;
        RETURN NEW;
    ELSIF (TG_OP = 'DELETE') THEN
        UPDATE public.reels SET likes_count = GREATEST(0, likes_count - 1) WHERE id = OLD.reel_id;
        RETURN OLD;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS trg_reel_like_counter ON public.reel_likes;
CREATE TRIGGER trg_reel_like_counter
AFTER INSERT OR DELETE ON public.reel_likes
FOR EACH ROW EXECUTE FUNCTION public.handle_reel_like_counter();

-- محفز تحديث عداد تعليقات الريلز تلقائياً
CREATE OR REPLACE FUNCTION public.handle_reel_comment_counter()
RETURNS TRIGGER AS $$
BEGIN
    IF (TG_OP = 'INSERT') THEN
        UPDATE public.reels SET comments_count = comments_count + 1 WHERE id = NEW.reel_id;
        RETURN NEW;
    ELSIF (TG_OP = 'DELETE') THEN
        UPDATE public.reels SET comments_count = GREATEST(0, comments_count - 1) WHERE id = OLD.reel_id;
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
-- 7. تفعيل سياسات الأمان ومنح الصلاحيات الكاملة لتطبيق قبس (RLS & Grants)
-- ==============================================================================

DO $$ 
DECLARE
    tbl TEXT;
    tbls TEXT[] := ARRAY[
        'users', 'profiles', 'user_settings', 'projects', 'styles', 'master_styles',
        'promo_codes', 'transactions', 'content_guard_logs', 'reels', 'reel_likes',
        'reel_comments', 'reel_bookmarks', 'reel_history', 'reels_feed', 'audio_tracks',
        'hadith_presets', 'scholar_lectures', 'scholar_proposals', 'app_updates',
        'app_reviews', 'dev_chat_messages', 'chat_messages', 'dev_bug_reports',
        'bug_reports', 'user_feedbacks', 'league_leaderboard'
    ];
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

-- استثناء جدول platform_api_keys: مقتصر على service_role فقط
ALTER TABLE public.platform_api_keys ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON TABLE public.platform_api_keys FROM anon, authenticated;
GRANT ALL ON TABLE public.platform_api_keys TO service_role;

-- ==============================================================================
-- 8. إنشاء سلال التخزين السحابي وسياسات الملفات (Storage Buckets)
-- ==============================================================================

INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES 
    ('reels', 'reels', true, 104857600, ARRAY['video/mp4', 'video/quicktime', 'video/webm', 'image/jpeg', 'image/png']),
    ('audio_tracks', 'audio_tracks', true, 52428800, ARRAY['audio/mpeg', 'audio/mp3', 'audio/wav', 'audio/x-m4a', 'audio/aac']),
    ('bug_reports', 'bug_reports', true, 20971520, ARRAY['image/jpeg', 'image/png', 'image/webp']),
    ('avatars', 'avatars', true, 10485760, ARRAY['image/jpeg', 'image/png', 'image/webp'])
ON CONFLICT (id) DO UPDATE SET 
    public = true,
    file_size_limit = EXCLUDED.file_size_limit,
    allowed_mime_types = EXCLUDED.allowed_mime_types;

-- سياسات سلال التخزين
DROP POLICY IF EXISTS "Allow public select on storage objects" ON storage.objects;
CREATE POLICY "Allow public select on storage objects" ON storage.objects FOR SELECT USING (true);

DROP POLICY IF EXISTS "Allow public insert on storage objects" ON storage.objects;
CREATE POLICY "Allow public insert on storage objects" ON storage.objects FOR INSERT WITH CHECK (true);

DROP POLICY IF EXISTS "Allow public update on storage objects" ON storage.objects;
CREATE POLICY "Allow public update on storage objects" ON storage.objects FOR UPDATE USING (true);

DROP POLICY IF EXISTS "Allow public delete on storage objects" ON storage.objects;
CREATE POLICY "Allow public delete on storage objects" ON storage.objects FOR DELETE USING (true);

-- ==============================================================================
-- 9. جدول مفاتيح API المشفرة للمزامنة (user_api_keys)
-- ==============================================================================
CREATE TABLE IF NOT EXISTS public.user_api_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id TEXT NOT NULL,
    service TEXT NOT NULL,
    encrypted_value TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    UNIQUE(user_id, service)
);

CREATE INDEX IF NOT EXISTS idx_user_api_keys_lookup ON public.user_api_keys (user_id, service);

ALTER TABLE public.user_api_keys ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Users can read own api keys" ON public.user_api_keys;
CREATE POLICY "Users can read own api keys"
    ON public.user_api_keys
    FOR SELECT
    USING (auth.uid()::text = user_id OR user_id = coalesce(auth.jwt()->>'email', ''));

DROP POLICY IF EXISTS "Users can insert or update own api keys" ON public.user_api_keys;
CREATE POLICY "Users can insert or update own api keys"
    ON public.user_api_keys
    FOR ALL
    USING (auth.uid()::text = user_id OR user_id = coalesce(auth.jwt()->>'email', ''))
    WITH CHECK (auth.uid()::text = user_id OR user_id = coalesce(auth.jwt()->>'email', ''));


-- Reels hardening: secure the baseline schema with the same policies as the forward migration.
-- Reels hardening: durable Supabase sessions are paired with server-side
-- ownership checks, developer-role claims, and an atomic one-post daily quota.

begin;

-- Keep the database compatible with the complete ReelRow payload used by the app.
create table if not exists public.reel_bookmarks (
    id uuid primary key default gen_random_uuid(),
    reel_id text not null references public.reels(id) on delete cascade,
    user_id text not null,
    created_at timestamptz default now(),
    constraint unique_reel_user_bookmark unique (reel_id, user_id)
);

create table if not exists public.reel_history (
    id uuid primary key default gen_random_uuid(),
    reel_id text not null references public.reels(id) on delete cascade,
    user_id text not null,
    watched_duration_sec integer default 0,
    created_at timestamptz default now()
);

create index if not exists idx_reel_bookmarks_user
    on public.reel_bookmarks(user_id, created_at desc);
create index if not exists idx_reel_history_user
    on public.reel_history(user_id, created_at desc);

alter table public.reels
    add column if not exists user_name text default 'صانع محتوى قبس',
    add column if not exists user_avatar text,
    add column if not exists publish_at timestamptz,
    add column if not exists expiry_at timestamptz,
    add column if not exists quality text not null default 'high',
    add column if not exists ai_analysis text,
    add column if not exists publish_type text not null default 'normal',
    add column if not exists is_silent_seed boolean not null default false,
    add column if not exists is_featured_by_dev boolean not null default false;

-- Remove every prior policy on these tables, including permissive policies
-- created by older master-schema scripts and applet migrations.
do $$
declare
    p record;
begin
    for p in
        select schemaname, tablename, policyname
          from pg_policies
         where schemaname = 'public'
           and tablename in ('reels', 'reel_likes', 'reel_comments', 'reel_bookmarks', 'reel_history')
    loop
        execute format('drop policy if exists %I on %I.%I', p.policyname, p.schemaname, p.tablename);
    end loop;
end $$;

alter table public.reels enable row level security;
alter table public.reel_likes enable row level security;
alter table public.reel_comments enable row level security;
alter table public.reel_bookmarks enable row level security;
alter table public.reel_history enable row level security;

create policy reels_read_active
on public.reels for select
to anon, authenticated
using (is_active = true or user_id::text = auth.uid()::text);

create policy reels_insert_owner
on public.reels for insert
to authenticated
with check (
    auth.uid() is not null
    and user_id::text = auth.uid()::text
    and (
        coalesce(auth.jwt()->'app_metadata'->>'role', '') in ('developer', 'admin', 'owner')
        or (
            coalesce(publish_type, 'normal') = 'normal'
            and not coalesce(is_silent_seed, false)
            and not coalesce(is_featured_by_dev, false)
        )
    )
);

create policy reels_update_owner_or_staff
on public.reels for update
to authenticated
using (
    user_id::text = auth.uid()::text
    or coalesce(auth.jwt()->'app_metadata'->>'role', '') in ('developer', 'admin', 'owner')
)
with check (
    user_id::text = auth.uid()::text
    or coalesce(auth.jwt()->'app_metadata'->>'role', '') in ('developer', 'admin', 'owner')
);

create policy reels_delete_owner_or_staff
on public.reels for delete
to authenticated
using (
    user_id::text = auth.uid()::text
    or coalesce(auth.jwt()->'app_metadata'->>'role', '') in ('developer', 'admin', 'owner')
);

create policy reel_likes_read_public
on public.reel_likes for select to anon, authenticated using (true);
create policy reel_likes_insert_self
on public.reel_likes for insert to authenticated
with check (auth.uid() is not null and user_id::text = auth.uid()::text);
create policy reel_likes_delete_self
on public.reel_likes for delete to authenticated
using (auth.uid() is not null and user_id::text = auth.uid()::text);

create policy reel_comments_read_public
on public.reel_comments for select to anon, authenticated using (true);
create policy reel_comments_insert_self
on public.reel_comments for insert to authenticated
with check (auth.uid() is not null and user_id::text = auth.uid()::text);
create policy reel_comments_delete_self_or_staff
on public.reel_comments for delete to authenticated
using (
    user_id::text = auth.uid()::text
    or coalesce(auth.jwt()->'app_metadata'->>'role', '') in ('developer', 'admin', 'owner')
);

create policy reel_bookmarks_read_self
on public.reel_bookmarks for select to authenticated
using (auth.uid() is not null and user_id::text = auth.uid()::text);
create policy reel_bookmarks_insert_self
on public.reel_bookmarks for insert to authenticated
with check (auth.uid() is not null and user_id::text = auth.uid()::text);
create policy reel_bookmarks_delete_self
on public.reel_bookmarks for delete to authenticated
using (auth.uid() is not null and user_id::text = auth.uid()::text);

create policy reel_history_read_self
on public.reel_history for select to authenticated
using (auth.uid() is not null and user_id::text = auth.uid()::text);
create policy reel_history_insert_self
on public.reel_history for insert to authenticated
with check (auth.uid() is not null and user_id::text = auth.uid()::text);

-- Serialize same-user publishes so simultaneous requests cannot exceed one post/day.
create or replace function public.enforce_reel_daily_publish_limit()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    caller_id text;
    caller_role text;
    posts_today integer;
begin
    caller_id := auth.uid()::text;
    caller_role := coalesce(auth.jwt()->'app_metadata'->>'role', '');

    if caller_id is null then
        raise exception 'reels_auth_required' using errcode = '42501';
    end if;

    if new.user_id::text <> caller_id then
        raise exception 'reels_owner_mismatch' using errcode = '42501';
    end if;

    if caller_role in ('developer', 'admin', 'owner') then
        return new;
    end if;

    perform pg_advisory_xact_lock(hashtextextended(caller_id, 0));

    select count(*)::integer
      into posts_today
      from public.reels r
     where r.user_id::text = caller_id
       and r.created_at >= date_trunc('day', now())
       and r.created_at < date_trunc('day', now()) + interval '1 day';

    if posts_today >= 1 then
        raise exception 'reels_daily_limit_exceeded' using errcode = 'P0001';
    end if;

    return new;
end;
$$;

drop trigger if exists trg_enforce_reel_daily_publish_limit on public.reels;
create trigger trg_enforce_reel_daily_publish_limit
before insert on public.reels
for each row execute function public.enforce_reel_daily_publish_limit();

-- Public playback is allowed, but uploads into the reels bucket must be under
-- videos/<authenticated UID>/... or thumbnails/<authenticated UID>/...
do $
declare
    p record;
begin
    for p in
        select policyname
          from pg_policies
         where schemaname = 'storage'
           and tablename = 'objects'
           and (coalesce(qual, '') ilike '%reels%'
                or coalesce(with_check, '') ilike '%reels%')
    loop
        execute format('drop policy if exists %I on storage.objects', p.policyname);
    end loop;
end $;

create policy reels_public_read
on storage.objects for select to anon, authenticated
using (bucket_id = 'reels');

create policy reels_owner_upload
on storage.objects for insert to authenticated
with check (
    bucket_id = 'reels'
    and auth.uid() is not null
    and split_part(name, '/', 2) = auth.uid()::text
);

revoke insert, update, delete on public.reels from anon;
revoke insert, update, delete on public.reel_likes from anon;
revoke insert, update, delete on public.reel_comments from anon;
revoke all on public.reel_bookmarks from anon;
revoke all on public.reel_history from anon;

grant select on public.reels, public.reel_likes, public.reel_comments to anon, authenticated;
grant insert, update, delete on public.reels to authenticated;
grant select, insert, delete on public.reel_likes to authenticated;
grant select, insert, delete on public.reel_comments to authenticated;
grant select, insert, delete on public.reel_bookmarks to authenticated;
grant select, insert on public.reel_history to authenticated;

commit;
