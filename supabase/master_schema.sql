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

-- جدول تحديثات التطبيق الرسمية (app_updates): public read-only, writes through guarded RPCs.
CREATE TABLE IF NOT EXISTS public.app_updates (
    id TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
    version_code INTEGER NOT NULL,
    version_name TEXT NOT NULL,
    title TEXT DEFAULT '',
    release_notes TEXT NOT NULL DEFAULT '',
    download_url TEXT NOT NULL,
    sha256_hash TEXT NOT NULL DEFAULT '',
    is_mandatory BOOLEAN NOT NULL DEFAULT false,
    is_active BOOLEAN NOT NULL DEFAULT true,
    file_size_bytes BIGINT NOT NULL DEFAULT 0,
    released_at TIMESTAMPTZ DEFAULT now(),
    created_at TIMESTAMPTZ DEFAULT now(),
    github_release_url TEXT,
    release_tag TEXT,
    asset_name TEXT
);

ALTER TABLE public.app_updates
    ADD COLUMN IF NOT EXISTS github_release_url TEXT,
    ADD COLUMN IF NOT EXISTS release_tag TEXT,
    ADD COLUMN IF NOT EXISTS asset_name TEXT;

CREATE INDEX IF NOT EXISTS idx_app_updates_version ON public.app_updates (version_code DESC);
CREATE UNIQUE INDEX IF NOT EXISTS app_updates_version_code_unique_idx ON public.app_updates (version_code);
CREATE UNIQUE INDEX IF NOT EXISTS app_updates_single_active_idx
    ON public.app_updates ((is_active)) WHERE is_active IS TRUE;

alter table public.app_updates
  add column if not exists github_release_url text,
  add column if not exists release_tag text,
  add column if not exists asset_name text;

create unique index if not exists app_updates_single_active_idx
  on public.app_updates ((is_active))
  where is_active is true;

create table if not exists public.app_update_audit (
  id bigint generated always as identity primary key,
  actor_id uuid not null references auth.users(id) on delete restrict,
  actor_role text not null,
  action text not null check (action in ('published', 'paused', 'activated')),
  old_version_code integer,
  new_version_code integer,
  version_name text,
  is_mandatory boolean not null default false,
  created_at timestamptz not null default now()
);

alter table public.app_update_audit enable row level security;
revoke all on public.app_update_audit from anon, authenticated;
grant select on public.app_update_audit to authenticated;

drop policy if exists app_update_audit_developer_read on public.app_update_audit;
create policy app_update_audit_developer_read
  on public.app_update_audit
  for select
  to authenticated
  using (
    lower(coalesce(((select auth.jwt()) -> 'app_metadata' ->> 'role'), ''))
      in ('developer', 'admin', 'owner', 'مطور', 'مكور')
  );

grant select on public.app_updates to anon, authenticated;
revoke insert, update, delete on public.app_updates from anon, authenticated;

create schema if not exists private;
revoke all on schema private from public, anon;
grant usage on schema private to authenticated;

create or replace function private.publish_qabas_app_update(
  p_version_name text,
  p_version_code integer,
  p_download_url text,
  p_release_notes text,
  p_file_size_bytes bigint,
  p_sha256_hash text,
  p_is_mandatory boolean,
  p_github_release_url text,
  p_release_tag text,
  p_asset_name text
)
returns public.app_updates
language plpgsql
security definer
set search_path = pg_catalog
as $$
declare
  v_actor uuid := auth.uid();
  v_role text := lower(coalesce(auth.jwt() -> 'app_metadata' ->> 'role', ''));
  v_old_version_code integer;
  v_new public.app_updates;
begin
  if v_actor is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;
  if v_role not in ('developer', 'admin', 'owner', 'مطور', 'مكور') then
    raise exception 'Verified developer role required' using errcode = '42501';
  end if;
  if p_version_name is null or length(trim(p_version_name)) = 0
     or p_version_code is null or p_version_code <= 0 then
    raise exception 'Invalid version metadata' using errcode = '22023';
  end if;
  if p_download_url is null
     or p_download_url !~ '^https://github[.]com/Ali-awwww/qabasapp/releases/download/[^[:space:]]+[.]apk$' then
    raise exception 'APK URL must point to a Qabas GitHub release asset' using errcode = '22023';
  end if;
  if p_github_release_url is null
     or p_github_release_url !~ '^https://github[.]com/Ali-awwww/qabasapp/releases/tag/[^[:space:]]+$' then
    raise exception 'Invalid GitHub release URL' using errcode = '22023';
  end if;
  if p_file_size_bytes is null or p_file_size_bytes <= 0 then
    raise exception 'Invalid APK file size' using errcode = '22023';
  end if;
  if p_sha256_hash is not null and p_sha256_hash <> ''
     and p_sha256_hash !~ '^[0-9A-Fa-f]{64}$' then
    raise exception 'Invalid SHA-256 digest' using errcode = '22023';
  end if;
  if p_asset_name is null or lower(p_asset_name) not like '%.apk' then
    raise exception 'A valid APK asset is required' using errcode = '22023';
  end if;

  perform pg_advisory_xact_lock(hashtext('qabas-app-update-publish'));
  select version_code into v_old_version_code
    from public.app_updates
    where is_active is true
    order by version_code desc
    limit 1
    for update;

  if exists (
    select 1 from public.app_updates
    where version_code >= p_version_code
  ) then
    raise exception 'Version code must be greater than every previously published build' using errcode = '22023';
  end if;

  update public.app_updates set is_active = false where is_active is true;

  insert into public.app_updates (
    version_code, version_name, title, release_notes, download_url,
    sha256_hash, is_mandatory, is_active, file_size_bytes, released_at,
    github_release_url, release_tag, asset_name
  ) values (
    p_version_code, trim(p_version_name), 'v' || trim(p_version_name),
    coalesce(p_release_notes, ''), p_download_url,
    coalesce(p_sha256_hash, ''), coalesce(p_is_mandatory, false), true,
    p_file_size_bytes, now(), p_github_release_url, p_release_tag, p_asset_name
  )
  returning * into v_new;

  insert into public.app_update_audit (
    actor_id, actor_role, action, old_version_code, new_version_code,
    version_name, is_mandatory
  ) values (
    v_actor, v_role, 'published', v_old_version_code, p_version_code,
    trim(p_version_name), coalesce(p_is_mandatory, false)
  );

  return v_new;
end;
$$;

create or replace function private.set_qabas_app_update_active(
  p_update_id text,
  p_is_active boolean
)
returns boolean
language plpgsql
security definer
set search_path = pg_catalog
as $$
declare
  v_actor uuid := auth.uid();
  v_role text := lower(coalesce(auth.jwt() -> 'app_metadata' ->> 'role', ''));
  v_target public.app_updates;
  v_old_version_code integer;
begin
  if v_actor is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;
  if v_role not in ('developer', 'admin', 'owner', 'مطور', 'مكور') then
    raise exception 'Verified developer role required' using errcode = '42501';
  end if;

  perform pg_advisory_xact_lock(hashtext('qabas-app-update-publish'));
  select version_code into v_old_version_code
    from public.app_updates where is_active is true
    order by version_code desc limit 1 for update;

  if coalesce(p_is_active, false) then
    select * into v_target from public.app_updates where id = p_update_id;
    if not found then
      raise exception 'Update release not found' using errcode = '22023';
    end if;
    if v_target.version_code <> (select max(version_code) from public.app_updates) then
      raise exception 'Only the newest published build can be activated' using errcode = '22023';
    end if;
    update public.app_updates set is_active = false where is_active is true;
    update public.app_updates set is_active = true where id = p_update_id;
    insert into public.app_update_audit (
      actor_id, actor_role, action, old_version_code, new_version_code,
      version_name, is_mandatory
    ) values (
      v_actor, v_role, 'activated', v_old_version_code, v_target.version_code,
      v_target.version_name, v_target.is_mandatory
    );
  else
    update public.app_updates set is_active = false where is_active is true;
    insert into public.app_update_audit (
      actor_id, actor_role, action, old_version_code, new_version_code,
      version_name, is_mandatory
    ) values (
      v_actor, v_role, 'paused', v_old_version_code, null, null, false
    );
  end if;
  return true;
end;
$$;

revoke all on function private.publish_qabas_app_update(text, integer, text, text, bigint, text, boolean, text, text, text) from public, anon;
revoke all on function private.set_qabas_app_update_active(text, boolean) from public, anon;
grant execute on function private.publish_qabas_app_update(text, integer, text, text, bigint, text, boolean, text, text, text) to authenticated;
grant execute on function private.set_qabas_app_update_active(text, boolean) to authenticated;

create or replace function public.publish_qabas_app_update(
  p_version_name text,
  p_version_code integer,
  p_download_url text,
  p_release_notes text,
  p_file_size_bytes bigint,
  p_sha256_hash text,
  p_is_mandatory boolean,
  p_github_release_url text,
  p_release_tag text,
  p_asset_name text
)
returns public.app_updates
language sql
security invoker
set search_path = pg_catalog
as $$
  select private.publish_qabas_app_update(
    p_version_name, p_version_code, p_download_url, p_release_notes,
    p_file_size_bytes, p_sha256_hash, p_is_mandatory,
    p_github_release_url, p_release_tag, p_asset_name
  );
$$;

create or replace function public.set_qabas_app_update_active(
  p_update_id text,
  p_is_active boolean
)
returns boolean
language sql
security invoker
set search_path = pg_catalog
as $$
  select private.set_qabas_app_update_active(p_update_id, p_is_active);
$$;

revoke all on function public.publish_qabas_app_update(text, integer, text, text, bigint, text, boolean, text, text, text) from public, anon;
revoke all on function public.set_qabas_app_update_active(text, boolean) from public, anon;
grant execute on function public.publish_qabas_app_update(text, integer, text, text, bigint, text, boolean, text, text, text) to authenticated;
grant execute on function public.set_qabas_app_update_active(text, boolean) to authenticated;

CREATE INDEX IF NOT EXISTS app_update_audit_actor_id_created_at_idx
    ON public.app_update_audit (actor_id, created_at DESC);
CREATE INDEX IF NOT EXISTS app_update_audit_created_at_idx
    ON public.app_update_audit (created_at DESC);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'app_updates_public_notes_safe'
          AND conrelid = 'public.app_updates'::regclass
    ) THEN
        ALTER TABLE public.app_updates
            ADD CONSTRAINT app_updates_public_notes_safe CHECK (
                position('github.com/' in lower(release_notes)) = 0
                AND position('[force]' in lower(release_notes)) = 0
                AND position('build number:' in lower(release_notes)) = 0
                AND position('built at:' in lower(release_notes)) = 0
                AND position('branch:' in lower(release_notes)) = 0
                AND position('workflow run:' in lower(release_notes)) = 0
                AND position('workflow run url:' in lower(release_notes)) = 0
                AND position('commit:' in lower(release_notes)) = 0
                AND position('commit sha:' in lower(release_notes)) = 0
            );
    END IF;
END $$;

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
    user_id TEXT,
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
        'hadith_presets', 'scholar_lectures', 'scholar_proposals',
        'app_reviews', 'chat_messages',
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
END $;

-- Projects contain private workspace metadata; override the legacy generic
-- policies above with owner-only access for authenticated users.
DROP POLICY IF EXISTS "Public select policy on projects" ON public.projects;
DROP POLICY IF EXISTS "Public insert policy on projects" ON public.projects;
DROP POLICY IF EXISTS "Public update policy on projects" ON public.projects;
DROP POLICY IF EXISTS "Public delete policy on projects" ON public.projects;
DROP POLICY IF EXISTS projects_select_own ON public.projects;
DROP POLICY IF EXISTS projects_insert_own ON public.projects;
DROP POLICY IF EXISTS projects_update_own ON public.projects;
DROP POLICY IF EXISTS projects_delete_own ON public.projects;
REVOKE ALL ON TABLE public.projects FROM anon;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.projects TO authenticated;
CREATE POLICY projects_select_own ON public.projects FOR SELECT TO authenticated
    USING (user_id = auth.uid()::text);
CREATE POLICY projects_insert_own ON public.projects FOR INSERT TO authenticated
    WITH CHECK (user_id = auth.uid()::text);
CREATE POLICY projects_update_own ON public.projects FOR UPDATE TO authenticated
    USING (user_id = auth.uid()::text) WITH CHECK (user_id = auth.uid()::text);
CREATE POLICY projects_delete_own ON public.projects FOR DELETE TO authenticated
    USING (user_id = auth.uid()::text);

-- Controlled app update tables: clients may read releases but cannot write them directly.
ALTER TABLE public.app_updates ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Public select policy on app_updates" ON public.app_updates;
DROP POLICY IF EXISTS "Public insert policy on app_updates" ON public.app_updates;
DROP POLICY IF EXISTS "Public update policy on app_updates" ON public.app_updates;
DROP POLICY IF EXISTS "Public delete policy on app_updates" ON public.app_updates;
DROP POLICY IF EXISTS app_updates_public_read ON public.app_updates;
CREATE POLICY app_updates_public_read
    ON public.app_updates FOR SELECT TO anon, authenticated USING (true);
REVOKE ALL ON TABLE public.app_updates FROM PUBLIC, anon, authenticated;
GRANT SELECT ON TABLE public.app_updates TO anon, authenticated;
GRANT ALL ON TABLE public.app_updates TO service_role;

ALTER TABLE public.app_update_audit ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Public select policy on app_update_audit" ON public.app_update_audit;
DROP POLICY IF EXISTS "Public insert policy on app_update_audit" ON public.app_update_audit;
DROP POLICY IF EXISTS "Public update policy on app_update_audit" ON public.app_update_audit;
DROP POLICY IF EXISTS "Public delete policy on app_update_audit" ON public.app_update_audit;
REVOKE ALL ON TABLE public.app_update_audit FROM PUBLIC, anon, authenticated;
GRANT SELECT ON TABLE public.app_update_audit TO authenticated;
GRANT ALL ON TABLE public.app_update_audit TO service_role;

NOTIFY pgrst, 'reload schema';

-- Support inbox tables contain private user messages and diagnostic reports.
-- They intentionally use per-user / support-admin policies instead of public CRUD.
ALTER TABLE public.dev_chat_messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.dev_bug_reports ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.dev_chat_messages ADD COLUMN IF NOT EXISTS user_id TEXT;
ALTER TABLE public.dev_bug_reports ADD COLUMN IF NOT EXISTS user_id TEXT;

CREATE INDEX IF NOT EXISTS idx_dev_bug_reports_user_id
    ON public.dev_bug_reports (user_id);

CREATE OR REPLACE FUNCTION public.qabas_is_support_admin()
RETURNS boolean
LANGUAGE sql
STABLE
AS $$
    SELECT
        lower(coalesce(auth.jwt() ->> 'email', '')) IN (
            'aliwalead.2007@gmail.com',
            'xman88371@gmail.com'
        )
        OR lower(coalesce(auth.jwt() -> 'app_metadata' ->> 'role', '')) IN (
            'admin', 'developer', 'owner'
        );
$$;

REVOKE ALL ON FUNCTION public.qabas_is_support_admin() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.qabas_is_support_admin() TO authenticated;

DROP POLICY IF EXISTS "Public select policy on dev_chat_messages" ON public.dev_chat_messages;
DROP POLICY IF EXISTS "Public insert policy on dev_chat_messages" ON public.dev_chat_messages;
DROP POLICY IF EXISTS "Public update policy on dev_chat_messages" ON public.dev_chat_messages;
DROP POLICY IF EXISTS "Public delete policy on dev_chat_messages" ON public.dev_chat_messages;
DROP POLICY IF EXISTS "Allow public insert dev_chat_messages" ON public.dev_chat_messages;
DROP POLICY IF EXISTS "Allow public select dev_chat_messages" ON public.dev_chat_messages;
DROP POLICY IF EXISTS "Users can view own dev_chat_messages" ON public.dev_chat_messages;
DROP POLICY IF EXISTS "Users can insert own dev_chat_messages" ON public.dev_chat_messages;

DROP POLICY IF EXISTS "Public select policy on dev_bug_reports" ON public.dev_bug_reports;
DROP POLICY IF EXISTS "Public insert policy on dev_bug_reports" ON public.dev_bug_reports;
DROP POLICY IF EXISTS "Public update policy on dev_bug_reports" ON public.dev_bug_reports;
DROP POLICY IF EXISTS "Public delete policy on dev_bug_reports" ON public.dev_bug_reports;
DROP POLICY IF EXISTS "Allow public insert dev_bug_reports" ON public.dev_bug_reports;
DROP POLICY IF EXISTS "Allow public select dev_bug_reports" ON public.dev_bug_reports;
DROP POLICY IF EXISTS "Allow update dev_bug_reports" ON public.dev_bug_reports;
DROP POLICY IF EXISTS "Allow delete dev_bug_reports" ON public.dev_bug_reports;

REVOKE ALL ON TABLE public.dev_chat_messages FROM PUBLIC, anon, authenticated;
REVOKE ALL ON TABLE public.dev_bug_reports FROM PUBLIC, anon, authenticated;
GRANT SELECT, INSERT ON TABLE public.dev_chat_messages TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.dev_bug_reports TO authenticated;
GRANT INSERT ON TABLE public.dev_bug_reports TO anon;
GRANT ALL ON TABLE public.dev_chat_messages, public.dev_bug_reports TO service_role;

CREATE POLICY "Users can view own dev_chat_messages"
    ON public.dev_chat_messages FOR SELECT TO authenticated
    USING (
        public.qabas_is_support_admin()
        OR user_id = auth.uid()::text
        OR lower(user_email) = lower(coalesce(auth.jwt() ->> 'email', ''))
    );

CREATE POLICY "Users can insert own dev_chat_messages"
    ON public.dev_chat_messages FOR INSERT TO authenticated
    WITH CHECK (
        public.qabas_is_support_admin()
        OR (
            sender = 'user'
            AND lower(user_email) = lower(coalesce(auth.jwt() ->> 'email', ''))
            AND user_id = auth.uid()::text
        )
    );

CREATE POLICY "Users can view own dev_bug_reports"
    ON public.dev_bug_reports FOR SELECT TO authenticated
    USING (
        public.qabas_is_support_admin()
        OR user_id = auth.uid()::text
        OR lower(user_email) = lower(coalesce(auth.jwt() ->> 'email', ''))
    );

CREATE POLICY "Public can submit dev_bug_reports"
    ON public.dev_bug_reports FOR INSERT TO anon, authenticated
    WITH CHECK (
        status = 'open'
        AND coalesce(developer_reply, '') = ''
        AND (
            (auth.uid() IS NULL AND user_id IS NULL)
            OR (auth.uid() IS NOT NULL AND user_id = auth.uid()::text)
        )
    );

CREATE POLICY "Support admins can update dev_bug_reports"
    ON public.dev_bug_reports FOR UPDATE TO authenticated
    USING (public.qabas_is_support_admin())
    WITH CHECK (public.qabas_is_support_admin());

CREATE POLICY "Support admins can delete dev_bug_reports"
    ON public.dev_bug_reports FOR DELETE TO authenticated
    USING (public.qabas_is_support_admin());

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

-- Public reads are limited to the app's published media buckets.
DROP POLICY IF EXISTS "Allow public select on storage objects" ON storage.objects;
CREATE POLICY "Allow public select on storage objects"
    ON storage.objects FOR SELECT TO public
    USING (bucket_id IN (
        'reels', 'audio_tracks', 'audio-tracks', 'bug_reports', 'avatars',
        'media-assets', 'user-uploads', 'ai-generated', 'azan-voices'
    ));

-- No blanket public writes. Users manage only objects inside their own UID folder;
-- trusted support admins retain management access for shared assets.
DROP POLICY IF EXISTS "Allow public insert on storage objects" ON storage.objects;
DROP POLICY IF EXISTS "Allow public update on storage objects" ON storage.objects;
DROP POLICY IF EXISTS "Allow public delete on storage objects" ON storage.objects;
DROP POLICY IF EXISTS "qabas_storage_authenticated_insert_own" ON storage.objects;
DROP POLICY IF EXISTS "qabas_storage_authenticated_update_own" ON storage.objects;
DROP POLICY IF EXISTS "qabas_storage_authenticated_delete_own" ON storage.objects;

CREATE POLICY "qabas_storage_authenticated_insert_own"
    ON storage.objects FOR INSERT TO authenticated
    WITH CHECK (
        public.qabas_is_support_admin()
        OR (bucket_id = 'ai-generated' AND (storage.foldername(name))[1] = auth.uid()::text)
        OR (bucket_id = 'avatars' AND (
            (storage.foldername(name))[1] = auth.uid()::text
            OR ((storage.foldername(name))[1] = 'user_avatars' AND (storage.foldername(name))[2] = auth.uid()::text)
        ))
        OR (bucket_id = 'bug_reports' AND (storage.foldername(name))[1] = auth.uid()::text)
        OR (bucket_id = 'media-assets' AND (
            (storage.foldername(name))[1] = auth.uid()::text
            OR ((storage.foldername(name))[1] IN ('reels', 'thumbnails') AND (storage.foldername(name))[2] = auth.uid()::text)
        ))
        OR (bucket_id = 'user-uploads' AND (
            (storage.foldername(name))[1] = auth.uid()::text
            OR ((storage.foldername(name))[1] = 'reels' AND (storage.foldername(name))[2] = auth.uid()::text)
        ))
        OR (bucket_id = 'audio-tracks' AND (storage.foldername(name))[1] = auth.uid()::text)
        OR (bucket_id = 'reels' AND
            (storage.foldername(name))[1] IN ('videos', 'thumbnails')
            AND (storage.foldername(name))[2] = auth.uid()::text
        )
    );

CREATE POLICY "qabas_storage_authenticated_update_own"
    ON storage.objects FOR UPDATE TO authenticated
    USING (
        public.qabas_is_support_admin()
        OR (bucket_id = 'ai-generated' AND (storage.foldername(name))[1] = auth.uid()::text)
        OR (bucket_id = 'avatars' AND (
            (storage.foldername(name))[1] = auth.uid()::text
            OR ((storage.foldername(name))[1] = 'user_avatars' AND (storage.foldername(name))[2] = auth.uid()::text)
        ))
        OR (bucket_id = 'bug_reports' AND (storage.foldername(name))[1] = auth.uid()::text)
        OR (bucket_id = 'media-assets' AND (
            (storage.foldername(name))[1] = auth.uid()::text
            OR ((storage.foldername(name))[1] IN ('reels', 'thumbnails') AND (storage.foldername(name))[2] = auth.uid()::text)
        ))
        OR (bucket_id = 'user-uploads' AND (
            (storage.foldername(name))[1] = auth.uid()::text
            OR ((storage.foldername(name))[1] = 'reels' AND (storage.foldername(name))[2] = auth.uid()::text)
        ))
        OR (bucket_id = 'audio-tracks' AND (storage.foldername(name))[1] = auth.uid()::text)
        OR (bucket_id = 'reels' AND
            (storage.foldername(name))[1] IN ('videos', 'thumbnails')
            AND (storage.foldername(name))[2] = auth.uid()::text
        )
    )
    WITH CHECK (
        public.qabas_is_support_admin()
        OR (bucket_id = 'ai-generated' AND (storage.foldername(name))[1] = auth.uid()::text)
        OR (bucket_id = 'avatars' AND (
            (storage.foldername(name))[1] = auth.uid()::text
            OR ((storage.foldername(name))[1] = 'user_avatars' AND (storage.foldername(name))[2] = auth.uid()::text)
        ))
        OR (bucket_id = 'bug_reports' AND (storage.foldername(name))[1] = auth.uid()::text)
        OR (bucket_id = 'media-assets' AND (
            (storage.foldername(name))[1] = auth.uid()::text
            OR ((storage.foldername(name))[1] IN ('reels', 'thumbnails') AND (storage.foldername(name))[2] = auth.uid()::text)
        ))
        OR (bucket_id = 'user-uploads' AND (
            (storage.foldername(name))[1] = auth.uid()::text
            OR ((storage.foldername(name))[1] = 'reels' AND (storage.foldername(name))[2] = auth.uid()::text)
        ))
        OR (bucket_id = 'audio-tracks' AND (storage.foldername(name))[1] = auth.uid()::text)
        OR (bucket_id = 'reels' AND
            (storage.foldername(name))[1] IN ('videos', 'thumbnails')
            AND (storage.foldername(name))[2] = auth.uid()::text
        )
    );

CREATE POLICY "qabas_storage_authenticated_delete_own"
    ON storage.objects FOR DELETE TO authenticated
    USING (
        public.qabas_is_support_admin()
        OR (bucket_id = 'ai-generated' AND (storage.foldername(name))[1] = auth.uid()::text)
        OR (bucket_id = 'avatars' AND (
            (storage.foldername(name))[1] = auth.uid()::text
            OR ((storage.foldername(name))[1] = 'user_avatars' AND (storage.foldername(name))[2] = auth.uid()::text)
        ))
        OR (bucket_id = 'bug_reports' AND (storage.foldername(name))[1] = auth.uid()::text)
        OR (bucket_id = 'media-assets' AND (
            (storage.foldername(name))[1] = auth.uid()::text
            OR ((storage.foldername(name))[1] IN ('reels', 'thumbnails') AND (storage.foldername(name))[2] = auth.uid()::text)
        ))
        OR (bucket_id = 'user-uploads' AND (
            (storage.foldername(name))[1] = auth.uid()::text
            OR ((storage.foldername(name))[1] = 'reels' AND (storage.foldername(name))[2] = auth.uid()::text)
        ))
        OR (bucket_id = 'audio-tracks' AND (storage.foldername(name))[1] = auth.uid()::text)
        OR (bucket_id = 'reels' AND
            (storage.foldername(name))[1] IN ('videos', 'thumbnails')
            AND (storage.foldername(name))[2] = auth.uid()::text
        )
    );

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

-- Private project voice recordings. Objects are scoped to the authenticated UID.
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
    'project-recordings', 'project-recordings', false, 26214400,
    ARRAY['audio/mp4', 'audio/m4a', 'audio/x-m4a', 'audio/aac', 'audio/mpeg', 'audio/wav', 'audio/ogg']
)
ON CONFLICT (id) DO UPDATE SET
    public = false,
    file_size_limit = EXCLUDED.file_size_limit,
    allowed_mime_types = EXCLUDED.allowed_mime_types;

DROP POLICY IF EXISTS "qabas_project_recordings_select_own" ON storage.objects;
CREATE POLICY "qabas_project_recordings_select_own" ON storage.objects FOR SELECT TO authenticated
USING (bucket_id = 'project-recordings' AND (storage.foldername(name))[1] = (select auth.uid())::text);

DROP POLICY IF EXISTS "qabas_project_recordings_insert_own" ON storage.objects;
CREATE POLICY "qabas_project_recordings_insert_own" ON storage.objects FOR INSERT TO authenticated
WITH CHECK (bucket_id = 'project-recordings' AND (storage.foldername(name))[1] = (select auth.uid())::text);

DROP POLICY IF EXISTS "qabas_project_recordings_update_own" ON storage.objects;
CREATE POLICY "qabas_project_recordings_update_own" ON storage.objects FOR UPDATE TO authenticated
USING (bucket_id = 'project-recordings' AND (storage.foldername(name))[1] = (select auth.uid())::text)
WITH CHECK (bucket_id = 'project-recordings' AND (storage.foldername(name))[1] = (select auth.uid())::text);

DROP POLICY IF EXISTS "qabas_project_recordings_delete_own" ON storage.objects;
CREATE POLICY "qabas_project_recordings_delete_own" ON storage.objects FOR DELETE TO authenticated
USING (bucket_id = 'project-recordings' AND (storage.foldername(name))[1] = (select auth.uid())::text);
