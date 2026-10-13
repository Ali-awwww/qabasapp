-- ==============================================================================
-- Migration: Add author display columns to public.reels
--
-- The app model (SupabaseServices.ReelRow) and the Reels UI send and read
-- user_name / user_avatar, but the reels table never had these columns.
-- PostgREST rejects the insert with PGRST204 ("Could not find the 'user_name'
-- column"), saveReel returns false, and the publish dialog shows a misleading
-- "check your internet" error. Adding the columns restores publishing.
-- ==============================================================================

ALTER TABLE public.reels ADD COLUMN IF NOT EXISTS user_name TEXT DEFAULT 'صانع محتوى قبس';
ALTER TABLE public.reels ADD COLUMN IF NOT EXISTS user_avatar TEXT;
