-- ==============================================================================
-- Migration: Add Reels system (TikTok-style)
-- ==============================================================================

-- 1. Reels Table
CREATE TABLE IF NOT EXISTS public.reels (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES public.users(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    description TEXT,
    video_url TEXT NOT NULL,
    thumbnail_url TEXT,
    tags TEXT[] DEFAULT '{}',
    likes_count INTEGER DEFAULT 0,
    comments_count INTEGER DEFAULT 0,
    shares_count INTEGER DEFAULT 0,
    views_count INTEGER DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- 2. Reel Likes Table (to prevent multiple likes)
CREATE TABLE IF NOT EXISTS public.reel_likes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reel_id UUID REFERENCES public.reels(id) ON DELETE CASCADE,
    user_id UUID REFERENCES public.users(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL,
    UNIQUE(reel_id, user_id)
);

-- 3. Reel Comments Table
CREATE TABLE IF NOT EXISTS public.reel_comments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reel_id UUID REFERENCES public.reels(id) ON DELETE CASCADE,
    user_id UUID REFERENCES public.users(id) ON DELETE CASCADE,
    comment_text TEXT NOT NULL,
    likes_count INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- Enable RLS
ALTER TABLE public.reels ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.reel_likes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.reel_comments ENABLE ROW LEVEL SECURITY;

-- Policies for Reels
CREATE POLICY "Anyone can view active reels" ON public.reels FOR SELECT USING (is_active = TRUE);
CREATE POLICY "Users can create their own reels" ON public.reels FOR INSERT WITH CHECK (auth.uid()::text = user_id::text);
CREATE POLICY "Users can update their own reels" ON public.reels FOR UPDATE USING (auth.uid()::text = user_id::text);

-- Policies for Likes
CREATE POLICY "Anyone can view likes" ON public.reel_likes FOR SELECT USING (TRUE);
CREATE POLICY "Authenticated users can like" ON public.reel_likes FOR INSERT WITH CHECK (auth.uid()::text = user_id::text);
CREATE POLICY "Users can unlike" ON public.reel_likes FOR DELETE USING (auth.uid()::text = user_id::text);

-- Policies for Comments
CREATE POLICY "Anyone can view comments" ON public.reel_comments FOR SELECT USING (TRUE);
CREATE POLICY "Authenticated users can comment" ON public.reel_comments FOR INSERT WITH CHECK (auth.uid()::text = user_id::text);
CREATE POLICY "Users can delete their own comments" ON public.reel_comments FOR DELETE USING (auth.uid()::text = user_id::text);
