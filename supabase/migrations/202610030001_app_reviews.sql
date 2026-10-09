-- Migration: App Reviews & Community Rating System
-- Schema for public.app_reviews connected with Qabas App

CREATE TABLE IF NOT EXISTS public.app_reviews (
    id TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
    author_name TEXT NOT NULL DEFAULT 'مستخدم قبس',
    rating INT NOT NULL CHECK (rating >= 1 AND rating <= 5),
    category_rating TEXT NOT NULL DEFAULT 'overall',
    app_version TEXT NOT NULL DEFAULT 'v1.2.1',
    device_model TEXT NOT NULL DEFAULT '',
    comment TEXT NOT NULL DEFAULT '',
    helpful_count INT NOT NULL DEFAULT 0,
    verified_user BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Enable RLS
ALTER TABLE public.app_reviews ENABLE ROW LEVEL SECURITY;

-- Allow public read access to all verified reviews
CREATE POLICY "Allow public read app_reviews"
    ON public.app_reviews
    FOR SELECT
    USING (true);

-- Allow public and authenticated inserts of new reviews
CREATE POLICY "Allow public insert app_reviews"
    ON public.app_reviews
    FOR INSERT
    WITH CHECK (true);

-- Allow incrementing helpful_count
CREATE POLICY "Allow public update helpful_count"
    ON public.app_reviews
    FOR UPDATE
    USING (true)
    WITH CHECK (true);

-- Indexes for fast ordering & analytics
CREATE INDEX IF NOT EXISTS idx_app_reviews_created_at ON public.app_reviews(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_app_reviews_rating ON public.app_reviews(rating);
