-- Keep the deployed reels schema aligned with the fields used by ReelRow and ReelInsertPayload.
-- Existing installations may already have some or all of these columns.
ALTER TABLE public.reels
    ADD COLUMN IF NOT EXISTS publish_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS expiry_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS quality TEXT DEFAULT 'high',
    ADD COLUMN IF NOT EXISTS ai_analysis TEXT,
    ADD COLUMN IF NOT EXISTS publish_type TEXT DEFAULT 'normal',
    ADD COLUMN IF NOT EXISTS is_silent_seed BOOLEAN DEFAULT false,
    ADD COLUMN IF NOT EXISTS is_featured_by_dev BOOLEAN DEFAULT false;

-- Normalize nullable legacy values before making non-null model fields safe to decode.
UPDATE public.reels SET quality = 'high' WHERE quality IS NULL;
UPDATE public.reels SET publish_type = 'normal' WHERE publish_type IS NULL;
UPDATE public.reels SET is_silent_seed = false WHERE is_silent_seed IS NULL;
UPDATE public.reels SET is_featured_by_dev = false WHERE is_featured_by_dev IS NULL;

ALTER TABLE public.reels
    ALTER COLUMN quality SET DEFAULT 'high',
    ALTER COLUMN quality SET NOT NULL,
    ALTER COLUMN publish_type SET DEFAULT 'normal',
    ALTER COLUMN publish_type SET NOT NULL,
    ALTER COLUMN is_silent_seed SET DEFAULT false,
    ALTER COLUMN is_silent_seed SET NOT NULL,
    ALTER COLUMN is_featured_by_dev SET DEFAULT false,
    ALTER COLUMN is_featured_by_dev SET NOT NULL;
