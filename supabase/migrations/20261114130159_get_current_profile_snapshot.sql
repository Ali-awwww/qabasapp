-- One authenticated, owner-scoped read for the data already stored in Supabase
-- for the current Qabas profile. This deliberately does not mirror external
-- social-platform counters, which currently have no server-side source of truth.
CREATE OR REPLACE FUNCTION public.get_current_profile_snapshot()
RETURNS jsonb
LANGUAGE plpgsql
STABLE
SECURITY INVOKER
SET search_path = pg_catalog
AS $function$
DECLARE
    v_uid text := (SELECT auth.uid())::text;
    v_settings_text text;
    v_bio text;
    v_bio_parse_failed boolean := false;
    v_result jsonb;
BEGIN
    IF v_uid IS NULL OR pg_catalog.btrim(v_uid) = '' THEN
        RAISE EXCEPTION 'Authentication required'
            USING ERRCODE = '42501';
    END IF;

    SELECT settings.preferences_json
      INTO v_settings_text
      FROM public.user_settings AS settings
     WHERE settings.user_id = v_uid
     LIMIT 1;

    IF v_settings_text IS NOT NULL AND pg_catalog.btrim(v_settings_text) <> '' THEN
        BEGIN
            v_bio := (v_settings_text::jsonb ->> 'bio');
        EXCEPTION
            WHEN invalid_text_representation THEN
                v_bio := NULL;
                v_bio_parse_failed := true;
        END;
    END IF;

    SELECT pg_catalog.jsonb_build_object(
        'user', (
            SELECT pg_catalog.jsonb_build_object(
                'id', u.id,
                'name', u.name,
                'custom_id', u.custom_id,
                'account_number', u.account_number,
                'created_at', u.created_at
            )
            FROM public.users AS u
            WHERE u.id = v_uid
        ),
        'profile', (
            SELECT pg_catalog.jsonb_build_object(
                'id', p.id,
                'full_name', p.full_name,
                'avatar_url', p.avatar_url
            )
            FROM public.profiles AS p
            WHERE p.id = v_uid
        ),
        'bio', v_bio,
        'bio_parse_failed', v_bio_parse_failed,
        'published_reels_count', (
            SELECT pg_catalog.count(*)
            FROM public.reels AS r
            WHERE r.user_id = v_uid
              AND r.is_active IS TRUE
        ),
        'published_reels_truncated', (
            SELECT pg_catalog.count(*) > 100
            FROM public.reels AS r
            WHERE r.user_id = v_uid
              AND r.is_active IS TRUE
        ),
        'published_reels', COALESCE((
            SELECT pg_catalog.jsonb_agg(
                pg_catalog.jsonb_build_object(
                    'id', page.id,
                    'user_id', page.user_id,
                    'user_custom_id', page.user_custom_id,
                    'user_name', page.user_name,
                    'user_avatar', page.user_avatar,
                    'title', page.title,
                    'description', page.description,
                    'video_url', page.video_url,
                    'thumbnail_url', page.thumbnail_url,
                    'tags', CASE
                        WHEN pg_catalog.jsonb_typeof(page.tags) = 'array' THEN page.tags
                        ELSE '[]'::jsonb
                    END,
                    'likes_count', COALESCE(page.likes_count, 0),
                    'comments_count', COALESCE(page.comments_count, 0),
                    'shares_count', COALESCE(page.shares_count, 0),
                    'views_count', COALESCE(page.views_count, 0),
                    'is_active', COALESCE(page.is_active, false),
                    'publish_at', page.publish_at,
                    'expiry_at', page.expiry_at,
                    'quality', COALESCE(page.quality, 'high'),
                    'created_at', page.created_at,
                    'updated_at', page.updated_at,
                    'publish_type', page.publish_type,
                    'is_silent_seed', COALESCE(page.is_silent_seed, false),
                    'is_featured_by_dev', COALESCE(page.is_featured_by_dev, false)
                ) ORDER BY page.created_at DESC, page.id DESC
            )
            FROM (
                SELECT r.id, r.user_id, r.user_custom_id, r.user_name, r.user_avatar,
                       r.title, r.description, r.video_url, r.thumbnail_url, r.tags,
                       r.likes_count, r.comments_count, r.shares_count, r.views_count,
                       r.is_active, r.publish_at, r.expiry_at, r.quality,
                       r.created_at, r.updated_at, r.publish_type,
                       r.is_silent_seed, r.is_featured_by_dev
                FROM public.reels AS r
                WHERE r.user_id = v_uid
                  AND r.is_active IS TRUE
                ORDER BY r.created_at DESC, r.id DESC
                LIMIT 100
            ) AS page
        ), '[]'::jsonb),
        'follower_count', public.get_creator_follower_count(v_uid),
        'following_count', (
            SELECT pg_catalog.count(*)
            FROM public.creator_follows AS f
            WHERE f.follower_id = v_uid
        ),
        'following_truncated', (
            SELECT pg_catalog.count(*) > 500
            FROM public.creator_follows AS f
            WHERE f.follower_id = v_uid
        ),
        'following', COALESCE((
            SELECT pg_catalog.jsonb_agg(
                pg_catalog.jsonb_build_object(
                    'creator_id', page.creator_id,
                    'created_at', page.created_at,
                    'full_name', public_profile.full_name,
                    'avatar_url', public_profile.avatar_url
                ) ORDER BY page.created_at DESC NULLS LAST, page.creator_id
            )
            FROM (
                SELECT f.creator_id, f.created_at
                FROM public.creator_follows AS f
                WHERE f.follower_id = v_uid
                ORDER BY f.created_at DESC NULLS LAST, f.creator_id
                LIMIT 500
            ) AS page
            LEFT JOIN public.community_profiles_public AS public_profile
              ON public_profile.id = page.creator_id
        ), '[]'::jsonb),
        'activity', pg_catalog.jsonb_build_object(
            'watchHistoryCount', (
                SELECT pg_catalog.count(*)
                FROM public.reel_history AS h
                WHERE h.user_id = v_uid
            ),
            'savedReelsCount', (
                SELECT pg_catalog.count(*)
                FROM public.reel_bookmarks AS b
                WHERE b.user_id = v_uid
            ),
            'likedReelsCount', (
                SELECT pg_catalog.count(*)
                FROM public.reel_likes AS l
                WHERE l.user_id = v_uid
            ),
            'totalWatchSeconds', COALESCE((
                SELECT pg_catalog.sum(GREATEST(COALESCE(h.watched_duration_sec, 0), 0))
                FROM public.reel_history AS h
                WHERE h.user_id = v_uid
            ), 0),
            'watchHistoryTruncated', (
                SELECT pg_catalog.count(*) > 100
                FROM public.reel_history AS h
                WHERE h.user_id = v_uid
            ),
            'savedReelsTruncated', (
                SELECT pg_catalog.count(*) > 100
                FROM public.reel_bookmarks AS b
                WHERE b.user_id = v_uid
            ),
            'likedReelsTruncated', (
                SELECT pg_catalog.count(*) > 100
                FROM public.reel_likes AS l
                WHERE l.user_id = v_uid
            ),
            'watchHistory', COALESCE((
                SELECT pg_catalog.jsonb_agg(
                    pg_catalog.jsonb_build_object(
                        'reelId', page.reel_id,
                        'title', COALESCE(NULLIF(page.title, ''), 'ريلز قبس'),
                        'userName', COALESCE(NULLIF(page.user_name, ''), 'صانع محتوى قبس'),
                        'userAvatar', page.user_avatar,
                        'videoUrl', COALESCE(page.video_url, ''),
                        'timestamp', (pg_catalog.date_part('epoch', COALESCE(page.created_at, page.reel_created_at, pg_catalog.now())) * 1000)::bigint,
                        'watchedDurationSec', COALESCE(page.watched_duration_sec, 0),
                        'category', COALESCE(NULLIF(page.tags ->> 0, ''), 'عام'),
                        'thumbnailUrl', page.thumbnail_url
                    ) ORDER BY page.created_at DESC NULLS LAST, page.reel_id
                )
                FROM (
                    SELECT h.reel_id, h.created_at, h.watched_duration_sec,
                           r.title, r.user_name, r.user_avatar, r.video_url,
                           r.tags, r.thumbnail_url, r.created_at AS reel_created_at
                    FROM public.reel_history AS h
                    LEFT JOIN public.reels AS r ON r.id = h.reel_id
                    WHERE h.user_id = v_uid
                    ORDER BY h.created_at DESC NULLS LAST, h.reel_id
                    LIMIT 100
                ) AS page
            ), '[]'::jsonb),
            'savedReels', COALESCE((
                SELECT pg_catalog.jsonb_agg(
                    pg_catalog.jsonb_build_object(
                        'reelId', page.reel_id,
                        'title', COALESCE(NULLIF(page.title, ''), 'ريلز قبس'),
                        'userName', COALESCE(NULLIF(page.user_name, ''), 'صانع محتوى قبس'),
                        'userAvatar', page.user_avatar,
                        'videoUrl', COALESCE(page.video_url, ''),
                        'timestamp', (pg_catalog.date_part('epoch', COALESCE(page.created_at, page.reel_created_at, pg_catalog.now())) * 1000)::bigint,
                        'watchedDurationSec', 0,
                        'category', COALESCE(NULLIF(page.tags ->> 0, ''), 'عام'),
                        'thumbnailUrl', page.thumbnail_url
                    ) ORDER BY page.created_at DESC NULLS LAST, page.reel_id
                )
                FROM (
                    SELECT b.reel_id, b.created_at, r.title, r.user_name, r.user_avatar,
                           r.video_url, r.tags, r.thumbnail_url, r.created_at AS reel_created_at
                    FROM public.reel_bookmarks AS b
                    LEFT JOIN public.reels AS r ON r.id = b.reel_id
                    WHERE b.user_id = v_uid
                    ORDER BY b.created_at DESC NULLS LAST, b.reel_id
                    LIMIT 100
                ) AS page
            ), '[]'::jsonb),
            'likedReels', COALESCE((
                SELECT pg_catalog.jsonb_agg(
                    pg_catalog.jsonb_build_object(
                        'reelId', page.reel_id,
                        'title', COALESCE(NULLIF(page.title, ''), 'ريلز قبس'),
                        'userName', COALESCE(NULLIF(page.user_name, ''), 'صانع محتوى قبس'),
                        'userAvatar', page.user_avatar,
                        'videoUrl', COALESCE(page.video_url, ''),
                        'timestamp', (pg_catalog.date_part('epoch', COALESCE(page.created_at, page.reel_created_at, pg_catalog.now())) * 1000)::bigint,
                        'watchedDurationSec', 0,
                        'category', COALESCE(NULLIF(page.tags ->> 0, ''), 'عام'),
                        'thumbnailUrl', page.thumbnail_url
                    ) ORDER BY page.created_at DESC NULLS LAST, page.reel_id
                )
                FROM (
                    SELECT l.reel_id, l.created_at, r.title, r.user_name, r.user_avatar,
                           r.video_url, r.tags, r.thumbnail_url, r.created_at AS reel_created_at
                    FROM public.reel_likes AS l
                    LEFT JOIN public.reels AS r ON r.id = l.reel_id
                    WHERE l.user_id = v_uid
                    ORDER BY l.created_at DESC NULLS LAST, l.reel_id
                    LIMIT 100
                ) AS page
            ), '[]'::jsonb)
        )
    ) INTO v_result;

    RETURN v_result;
END;
$function$;

REVOKE ALL ON FUNCTION public.get_current_profile_snapshot() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_current_profile_snapshot() TO authenticated;

COMMENT ON FUNCTION public.get_current_profile_snapshot() IS
    'Returns a bounded profile snapshot for auth.uid() only; executes as invoker and relies on existing owner RLS policies.';

-- Rollback (if the app is rolled back before this RPC is required):
-- REVOKE EXECUTE ON FUNCTION public.get_current_profile_snapshot() FROM authenticated;
-- DROP FUNCTION public.get_current_profile_snapshot();
