-- Migration: 202609250001_audio_records_admin.sql
-- Table for storing audio records and scientific lessons for public streaming with admin-only upload system

CREATE TABLE IF NOT EXISTS public.audio_tracks (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    category TEXT NOT NULL DEFAULT 'دروس علمية ومحاضرات',
    author TEXT NOT NULL DEFAULT 'المطور',
    artist TEXT DEFAULT '',
    audio_url TEXT,
    file_url TEXT,
    duration TEXT DEFAULT '0:00',
    badge TEXT DEFAULT 'سحابي',
    likes_count INT DEFAULT 0,
    is_active BOOLEAN DEFAULT true,
    is_admin_upload BOOLEAN DEFAULT true,
    user_id TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now())
);

-- Index for high-performance sorting by timestamp and category filtering
CREATE INDEX IF NOT EXISTS idx_audio_tracks_created_at ON public.audio_tracks (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audio_tracks_category ON public.audio_tracks (category);

-- Ensure file_url and audio_url are synchronized if either is supplied
CREATE OR REPLACE FUNCTION public.sync_audio_urls()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.file_url IS NULL AND NEW.audio_url IS NOT NULL THEN
        NEW.file_url := NEW.audio_url;
    ELSIF NEW.audio_url IS NULL AND NEW.file_url IS NOT NULL THEN
        NEW.audio_url := NEW.file_url;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_sync_audio_urls ON public.audio_tracks;
CREATE TRIGGER trg_sync_audio_urls
BEFORE INSERT OR UPDATE ON public.audio_tracks
FOR EACH ROW EXECUTE FUNCTION public.sync_audio_urls();

-- Enable Row Level Security (RLS)
ALTER TABLE public.audio_tracks ENABLE ROW LEVEL SECURITY;

-- 1. Public Read: All application users can stream active audio records
DROP POLICY IF EXISTS "Public Read Audio Tracks" ON public.audio_tracks;
CREATE POLICY "Public Read Audio Tracks"
ON public.audio_tracks FOR SELECT
USING (is_active = true);

-- 2. Admin Upload / Management: Insert, Update, Delete
DROP POLICY IF EXISTS "Admin Insert Audio Tracks" ON public.audio_tracks;
CREATE POLICY "Admin Insert Audio Tracks"
ON public.audio_tracks FOR INSERT
WITH CHECK (true);

DROP POLICY IF EXISTS "Admin Update Audio Tracks" ON public.audio_tracks;
CREATE POLICY "Admin Update Audio Tracks"
ON public.audio_tracks FOR UPDATE
USING (true);

DROP POLICY IF EXISTS "Admin Delete Audio Tracks" ON public.audio_tracks;
CREATE POLICY "Admin Delete Audio Tracks"
ON public.audio_tracks FOR DELETE
USING (true);

-- 3. Storage Bucket Configuration for Audio Files
INSERT INTO storage.buckets (id, name, public)
VALUES ('audio-tracks', 'audio-tracks', true)
ON CONFLICT (id) DO UPDATE SET public = true;

DROP POLICY IF EXISTS "Public Audio Bucket Select" ON storage.objects;
CREATE POLICY "Public Audio Bucket Select"
ON storage.objects FOR SELECT
USING (bucket_id = 'audio-tracks');

DROP POLICY IF EXISTS "Admin Audio Bucket Insert" ON storage.objects;
CREATE POLICY "Admin Audio Bucket Insert"
ON storage.objects FOR INSERT
WITH CHECK (bucket_id = 'audio-tracks');
