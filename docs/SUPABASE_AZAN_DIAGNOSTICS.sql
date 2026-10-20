-- ═══════════════════════════════════════════════════════════════════════
-- استعلامات تشخيص أصوات الأذان السحابية — استوديو قبس
-- نفّذها في: Supabase Dashboard → SQL Editor → New query → Run
-- ═══════════════════════════════════════════════════════════════════════

-- ───────────────────────────────────────────────────────────────────────
-- 1) هل جدول azan_voices موجود؟ وما أعمدته؟
-- ───────────────────────────────────────────────────────────────────────
select
    table_name,
    column_name,
    data_type,
    is_nullable,
    column_default
from information_schema.columns
where table_schema = 'public'
  and table_name   = 'azan_voices'
order by ordinal_position;
-- متوقع: 9 صفوف (id, name_ar, muezzin_ar, description, audio_url,
--          is_fajr_specific, is_active, created_by, created_at)
-- لو رجع 0 صفوف ← الجدول غير موجود: نفّذ migration 202610130001_azan_voices.sql


-- ───────────────────────────────────────────────────────────────────────
-- 2) هل الـ bucket موجود في Storage؟ وهل هو public؟
-- ───────────────────────────────────────────────────────────────────────
select
    id,
    name,
    public,
    file_size_limit,
    allowed_mime_types,
    created_at
from storage.buckets
where id = 'azan-voices';
-- متوقع: صف واحد public = true
-- لو رجع 0 صفوف ← الـ bucket غير موجود (وهذا سبب فشل الرفع!)
--   الحل: نفّذ السطر أدناه ثم أعِد المحاولة:
--
--   insert into storage.buckets (id, name, public)
--   values ('azan-voices', 'azan-voices', true)
--   on conflict (id) do update set public = true;


-- ───────────────────────────────────────────────────────────────────────
-- 3) كل الأصوات المرفوعة (الأحدث أولاً)
-- ───────────────────────────────────────────────────────────────────────
select
    id,
    name_ar,
    muezzin_ar,
    description,
    audio_url,
    is_fajr_specific,
    is_active,
    created_by,
    created_at
from public.azan_voices
order by created_at desc;


-- ───────────────────────────────────────────────────────────────────────
-- 4) عدّ الصفوف + هل الأصوات مفعّلة (is_active = true)؟
-- ───────────────────────────────────────────────────────────────────────
select
    count(*)                                  as total_rows,
    count(*) filter (where is_active)         as active_rows,
    count(*) filter (where not is_active)     as inactive_rows,
    max(created_at)                          as last_upload_at
from public.azan_voices;


-- ───────────────────────────────────────────────────────────────────────
-- 5) كل سياسات RLS على الجدول (هل يسمح بالإدراج لل anon؟)
-- ───────────────────────────────────────────────────────────────────────
select
    policyname,
    cmd,
    roles,
    qual,
    with_check
from pg_policies
where schemaname = 'public'
  and tablename  = 'azan_voices'
order by policyname;
-- متوقع صف: "allow insert azan_voices" | INSERT | {public} | with check = true
-- (بدون هذا الصف، يرفض PostgREST الإدراج)


-- ───────────────────────────────────────────────────────────────────────
-- 6) سياسات Storage على bucket azan-voices
-- ───────────────────────────────────────────────────────────────────────
select
    policyname,
    cmd,
    roles,
    qual,
    with_check
from pg_policies
where schemaname = 'storage'
  and tablename  = 'objects'
  and (qual ilike '%azan-voices%' or with_check ilike '%azan-voices%')
order by policyname;
-- متوقع: "allow upload azan-voices" (INSERT) + "public read azan-voices" (SELECT)


-- ───────────────────────────────────────────────────────────────────────
-- 7) الملفات الموجودة فعلياً داخل الـ bucket
-- ───────────────────────────────────────────────────────────────────────
select
    name,
    (metadata->>'size')::bigint          as size_bytes,
    (metadata->>'mimetype')              as mime,
    created_at
from storage.objects
where bucket_id = 'azan-voices'
order by created_at desc;
-- يجب أن يطابق عددُ الملفات عددَ الصفوف النشطة في الجدول


-- ───────────────────────────────────────────────────────────────────────
-- 8) فحص شامل: هل كل صف له ملف فعلي في الـ bucket؟
-- ───────────────────────────────────────────────────────────────────────
select
    v.name_ar,
    v.audio_url,
    (o.name is not null)            as file_exists_in_storage,
    (o.metadata->>'size')::bigint   as file_size,
    case
        when o.name is null            then '❌ الملف مفقود من Storage — أعد الرفع'
        when v.is_active = false       then '⚠️ معطّل (is_active = false)'
        else '✅ سليم'
    end                             as verdict
from public.azan_voices v
left join storage.objects o
       on o.bucket_id = 'azan-voices'
      and o.name = split_part(split_part(v.audio_url, '/object/public/', 2), '/', 2)
order by v.created_at desc;


-- ───────────────────────────────────────────────────────────────────────
-- 9) تفعيل كل الأصوات معطّلة (إصلاح سريع)
-- ───────────────────────────────────────────────────────────────────────
-- update public.azan_voices set is_active = true where is_active = false;


-- ───────────────────────────────────────────────────────────────────────
-- 10) إصلاح تلقائي شامل — نفّذه مرة واحدة لإصلاح أي نقص
-- ───────────────────────────────────────────────────────────────────────
-- (أ) ضمان وجود الجدول
-- create table if not exists public.azan_voices (
--     id text primary key,
--     name_ar text not null,
--     muezzin_ar text not null,
--     description text default '',
--     audio_url text not null,
--     is_fajr_specific boolean default false,
--     is_active boolean default true,
--     created_by text,
--     created_at timestamptz default now()
-- );
-- alter table public.azan_voices enable row level security;
--
-- (ب) ضمان وجود الـ bucket
-- insert into storage.buckets (id, name, public)
-- values ('azan-voices', 'azan-voices', true)
-- on conflict (id) do update set public = true;
--
-- (ج) ضمان السياسات
-- drop policy if exists "public read azan_voices" on public.azan_voices;
-- create policy "public read azan_voices"
--     on public.azan_voices for select using (is_active = true);
--
-- drop policy if exists "allow insert azan_voices" on public.azan_voices;
-- create policy "allow insert azan_voices"
--     on public.azan_voices for insert with check (true);
--
-- drop policy if exists "allow update azan_voices" on public.azan_voices;
-- create policy "allow update azan_voices"
--     on public.azan_voices for update using (true);
--
-- drop policy if exists "allow delete azan_voices" on public.azan_voices;
-- create policy "allow delete azan_voices"
--     on public.azan_voices for delete using (true);
--
-- drop policy if exists "public read azan-voices" on storage.objects;
-- create policy "public read azan-voices"
--     on storage.objects for select using (bucket_id = 'azan-voices');
--
-- drop policy if exists "allow upload azan-voices" on storage.objects;
-- create policy "allow upload azan-voices"
--     on storage.objects for insert with check (bucket_id = 'azan-voices');
--
-- drop policy if exists "allow delete azan-voices" on storage.objects;
-- create policy "allow delete azan-voices"
--     on storage.objects for delete using (bucket_id = 'azan-voices');
