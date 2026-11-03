-- Canonical cloud identity for Qabas users.
-- Auth UID is the only owner key. Usernames and account numbers are allocated
-- centrally by PostgreSQL and are never accepted from an untrusted client.

BEGIN;

CREATE SCHEMA IF NOT EXISTS qabas_identity_private;
REVOKE ALL ON SCHEMA qabas_identity_private FROM PUBLIC, anon, authenticated;

CREATE SEQUENCE IF NOT EXISTS qabas_identity_private.account_number_seq
    AS BIGINT INCREMENT BY 1 MINVALUE 1 NO MAXVALUE START WITH 1 CACHE 1;
REVOKE ALL ON SEQUENCE qabas_identity_private.account_number_seq
    FROM PUBLIC, anon, authenticated;

-- Continue after the highest existing QBS number. nextval() is atomic; gaps
-- are acceptable and allocated numbers are never reused.
DO $$
DECLARE v_max BIGINT;
BEGIN
    SELECT MAX(NULLIF(regexp_replace(account_number, '[^0-9]', '', 'g'), '')::BIGINT)
      INTO v_max
      FROM public.users;
    IF COALESCE(v_max, 0) > 0 THEN
        PERFORM setval('qabas_identity_private.account_number_seq'::regclass, v_max, TRUE);
    ELSE
        PERFORM setval('qabas_identity_private.account_number_seq'::regclass, 1, FALSE);
    END IF;
END
$$;

-- Repair duplicate handles without deleting historical profile rows.
WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (
        PARTITION BY lower(btrim(custom_id))
        ORDER BY is_eligible DESC, created_at ASC NULLS LAST, id
    ) AS rn
    FROM public.users
    WHERE custom_id IS NOT NULL AND btrim(custom_id) <> ''
)
UPDATE public.users u
   SET custom_id = 'legacy_' || substr(md5(u.id), 1, 16)
  FROM ranked r
 WHERE u.id = r.id AND r.rn > 1;

UPDATE public.users
   SET custom_id = 'legacy_' || substr(md5(id), 1, 16)
 WHERE custom_id IS NULL OR btrim(custom_id) = '';

-- Retain one existing number per duplicate group; allocate permanent new
-- numbers to the other historical rows.
WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (
        PARTITION BY COALESCE(
               NULLIF(ltrim(regexp_replace(btrim(account_number), '[^0-9]', '', 'g'), '0'), ''),
               '0'
           )
        ORDER BY is_eligible DESC, created_at ASC NULLS LAST, id
    ) AS rn
    FROM public.users
    WHERE account_number IS NOT NULL AND btrim(account_number) <> ''
)
UPDATE public.users u
   SET account_number =
       '#QBS-' || lpad(nextval('qabas_identity_private.account_number_seq')::TEXT, 4, '0')
  FROM ranked r
 WHERE u.id = r.id AND r.rn > 1;

UPDATE public.users
   SET account_number =
       '#QBS-' || lpad(nextval('qabas_identity_private.account_number_seq')::TEXT, 4, '0')
 WHERE account_number IS NULL OR btrim(account_number) = '';

-- An exact users.id -> auth.users.id match is stronger than email.
-- Refresh the email snapshot on that row from Auth, including email changes.
UPDATE public.users u
   SET external_id = a.id::TEXT,
       email = lower(btrim(a.email))
  FROM auth.users a
 WHERE u.id = a.id::TEXT
   AND a.email IS NOT NULL
   AND btrim(a.email) <> '';

-- Build a conservative alias map only when the Supabase Auth email is unique.
CREATE TEMP TABLE qbas_identity_aliases ON COMMIT DROP AS
WITH auth_emails AS (
    SELECT lower(btrim(email)) AS email, MIN(id::TEXT) AS auth_uid, COUNT(*) AS auth_count
    FROM auth.users
    WHERE email IS NOT NULL AND btrim(email) <> ''
    GROUP BY lower(btrim(email))
),
candidates AS (
    -- Exact Auth UID relations are unambiguous even if a user changed email.
    SELECT u.id AS alias_id, a.id::TEXT AS auth_uid
    FROM public.users u JOIN auth.users a ON u.id = a.id::TEXT
    UNION ALL
    SELECT u.external_id AS alias_id, a.id::TEXT AS auth_uid
    FROM public.users u JOIN auth.users a ON u.external_id = a.id::TEXT
    WHERE u.external_id IS NOT NULL AND btrim(u.external_id) <> ''
    UNION ALL
    SELECT u.id AS alias_id, a.auth_uid
    FROM public.users u JOIN auth_emails a ON a.email = lower(btrim(u.email))
    WHERE a.auth_count = 1
    UNION ALL
    SELECT u.external_id AS alias_id, a.auth_uid
    FROM public.users u JOIN auth_emails a ON a.email = lower(btrim(u.email))
    WHERE a.auth_count = 1 AND u.external_id IS NOT NULL AND btrim(u.external_id) <> ''
    UNION ALL
    SELECT lower(btrim(u.email)) AS alias_id, a.auth_uid
    FROM public.users u JOIN auth_emails a ON a.email = lower(btrim(u.email))
    WHERE a.auth_count = 1

    UNION ALL
    -- Also migrate legacy ownership values equal to a verified Auth email,
    -- even when the old public.users profile row is missing.
    SELECT a.email AS alias_id, a.auth_uid
    FROM auth_emails a
    WHERE a.auth_count = 1
)
SELECT alias_id, MIN(auth_uid) AS auth_uid
FROM candidates
WHERE alias_id IS NOT NULL AND btrim(alias_id) <> ''
GROUP BY alias_id
HAVING COUNT(DISTINCT auth_uid) = 1;
CREATE UNIQUE INDEX ON qbas_identity_aliases(alias_id);

-- Select one canonical profile per unambiguously matched Auth account.
CREATE TEMP TABLE qbas_identity_canonical ON COMMIT DROP AS
WITH auth_emails AS (
    SELECT lower(btrim(email)) AS email, MIN(id::TEXT) AS auth_uid, COUNT(*) AS auth_count
    FROM auth.users
    WHERE email IS NOT NULL AND btrim(email) <> ''
    GROUP BY lower(btrim(email))
),
direct_ranked AS (
    SELECT a.id::TEXT AS auth_uid,
           lower(btrim(a.email)) AS email,
           u.id AS profile_id,
           ROW_NUMBER() OVER (
               PARTITION BY a.id
               ORDER BY (u.id = a.id::TEXT) DESC,
                        u.is_eligible DESC, u.created_at ASC NULLS LAST, u.id
           ) AS rn
    FROM auth.users a
    JOIN public.users u
      ON u.id = a.id::TEXT OR u.external_id = a.id::TEXT
    WHERE a.email IS NOT NULL AND btrim(a.email) <> ''
),
email_ranked AS (
    SELECT a.auth_uid, a.email, u.id AS profile_id,
           ROW_NUMBER() OVER (
               PARTITION BY a.auth_uid
               ORDER BY u.is_eligible DESC, u.created_at ASC NULLS LAST, u.id
           ) AS rn
    FROM auth_emails a
    JOIN public.users u ON lower(btrim(u.email)) = a.email
    WHERE a.auth_count = 1
      AND NOT EXISTS (
          SELECT 1 FROM direct_ranked d WHERE d.auth_uid = a.auth_uid
      )
)
SELECT auth_uid, email, profile_id
FROM direct_ranked
WHERE rn = 1
UNION ALL
SELECT auth_uid, email, profile_id
FROM email_ranked
WHERE rn = 1;
CREATE UNIQUE INDEX ON qbas_identity_canonical(auth_uid);
CREATE UNIQUE INDEX ON qbas_identity_canonical(profile_id);

-- Preserve one row when an old alias and the canonical UID already liked or
-- bookmarked the same item. No comments or user-authored content are removed.
DELETE FROM public.reel_likes old_row
USING qbas_identity_aliases m
WHERE old_row.user_id = m.alias_id AND old_row.user_id <> m.auth_uid
  AND EXISTS (
      SELECT 1 FROM public.reel_likes current_row
      WHERE current_row.reel_id = old_row.reel_id AND current_row.user_id = m.auth_uid
  );
UPDATE public.reel_likes x
   SET user_id = m.auth_uid
  FROM qbas_identity_aliases m
 WHERE x.user_id = m.alias_id AND m.alias_id <> m.auth_uid;

DELETE FROM public.reel_bookmarks old_row
USING qbas_identity_aliases m
WHERE old_row.user_id = m.alias_id AND old_row.user_id <> m.auth_uid
  AND EXISTS (
      SELECT 1 FROM public.reel_bookmarks current_row
      WHERE current_row.reel_id = old_row.reel_id AND current_row.user_id = m.auth_uid
  );
UPDATE public.reel_bookmarks x
   SET user_id = m.auth_uid
  FROM qbas_identity_aliases m
 WHERE x.user_id = m.alias_id AND m.alias_id <> m.auth_uid;

DELETE FROM public.creator_follows old_row
USING qbas_identity_aliases m
WHERE old_row.follower_id = m.alias_id AND old_row.follower_id <> m.auth_uid
  AND EXISTS (
      SELECT 1 FROM public.creator_follows current_row
      WHERE current_row.follower_id = m.auth_uid AND current_row.creator_id = old_row.creator_id
  );
UPDATE public.creator_follows x
   SET follower_id = m.auth_uid
  FROM qbas_identity_aliases m
 WHERE x.follower_id = m.alias_id AND m.alias_id <> m.auth_uid;

DELETE FROM public.creator_follows old_row
USING qbas_identity_aliases m
WHERE old_row.creator_id = m.alias_id AND old_row.creator_id <> m.auth_uid
  AND EXISTS (
      SELECT 1 FROM public.creator_follows current_row
      WHERE current_row.creator_id = m.auth_uid AND current_row.follower_id = old_row.follower_id
  );
UPDATE public.creator_follows x
   SET creator_id = m.auth_uid
  FROM qbas_identity_aliases m
 WHERE x.creator_id = m.alias_id AND m.alias_id <> m.auth_uid;

DO $$
DECLARE v_item RECORD;
BEGIN
    FOR v_item IN
        SELECT * FROM (VALUES
            ('reels', 'user_id'),
            ('reel_comments', 'user_id'),
            ('reel_history', 'user_id'),
            ('chat_messages', 'user_id'),
            ('dev_chat_messages', 'user_id'),
            ('dev_bug_reports', 'user_id'),
            ('projects', 'user_id'),
            ('projects', 'external_id'),
            ('styles', 'user_id'),
            ('master_styles', 'user_id'),
            ('audio_tracks', 'user_id'),
            ('content_guard_logs', 'user_id'),
            ('league_leaderboard', 'user_id'),
            ('promo_code_redemptions', 'user_id'),
            ('scholar_proposals', 'user_id'),
            ('transactions', 'user_id'),
            ('app_reviews', 'user_id')
        ) AS t(table_name, column_name)
    LOOP
        EXECUTE format(
            'UPDATE public.%I AS target SET %I = map.auth_uid
              FROM qbas_identity_aliases AS map
             WHERE target.%I = map.alias_id AND map.alias_id <> map.auth_uid',
            v_item.table_name, v_item.column_name, v_item.column_name
        );
    END LOOP;
END
$$;

-- saved_items.user_id is UUID (unlike most legacy ownership columns which
-- are TEXT), so compare as text and cast only a validated Auth UUID.
UPDATE public.saved_items target
   SET user_id = map.auth_uid::UUID
  FROM qbas_identity_aliases map
 WHERE target.user_id::TEXT = map.alias_id
   AND map.auth_uid ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
   AND map.alias_id <> map.auth_uid;

-- Platform-curated starter reels are system-owned, never owned by the
-- first viewer who happens to like/comment/bookmark them.
INSERT INTO public.reels (
    id, user_id, user_name, title, description, video_url, thumbnail_url,
    tags, publish_at, quality, publish_type, is_silent_seed,
    is_featured_by_dev, is_active
)
VALUES
(
    'qabas_quran_recitation_starter',
    'qabas_system',
    'استوديو قبس',
    '﴿الَّذِينَ آمَنُوا وَتَطْمَئِنُّ قُلُوبُهُم بِذِكْرِ اللَّهِ﴾ 🌿 تلاوة خاشعة وسكينة',
    'تلاوة قرآنية مباركة تأخذ القلب إلى آفاق الطمأنينة والسكينة في رحاب كلام الله تبارك وتعالى.',
    'https://egkgygbvoeevakwkbvfu.supabase.co/storage/v1/object/public/media-assets/starter_reels/quran_recitation.mp4',
    'https://images.unsplash.com/photo-1609599006353-e629aaabfeae?w=600&q=80',
    '["قرآن","تلاوة","خشوع","سكينة","ذكر"]'::jsonb,
    now(), 'high', 'normal', false, true, true
),
(
    'qabas_faith_lecture_starter',
    'qabas_system',
    'استوديو قبس',
    'هادم اللذات 📜 وقفة صادقة مع الآخرة وزوال الدنيا',
    'موعظة مؤثرة تدق أبواب القلوب: تذكير بزوال الدنيا وسرعة الرحيل إلى الله سبحانه وتعالى.',
    'https://egkgygbvoeevakwkbvfu.supabase.co/storage/v1/object/public/media-assets/starter_reels/faith_lecture.mp4',
    'https://images.unsplash.com/photo-1548013146-72479768bada?w=600&q=80',
    '["مواعظ","تذكير","آخرة","زهد","قلب"]'::jsonb,
    now(), 'high', 'normal', false, true, true
)
ON CONFLICT (id) DO UPDATE
SET user_id = EXCLUDED.user_id,
    user_name = EXCLUDED.user_name;

-- Promote the selected profile row to Auth UID. Ambiguous emails and
-- non-selected legacy rows are preserved unchanged for manual review.
UPDATE public.users u
   SET id = c.auth_uid, external_id = c.auth_uid, email = c.email
  FROM qbas_identity_canonical c
 WHERE u.id = c.profile_id
   AND u.id <> c.auth_uid
   AND NOT EXISTS (SELECT 1 FROM public.users existing WHERE existing.id = c.auth_uid);

UPDATE public.users u
   SET external_id = u.id
  FROM qbas_identity_canonical c
 WHERE u.id = c.auth_uid AND u.external_id IS DISTINCT FROM u.id;

-- The counter is updated inside the profile INSERT transaction. Unlike a
-- PostgreSQL sequence, a rolled-back registration does not consume a number.
CREATE TABLE IF NOT EXISTS qabas_identity_private.account_number_counter (
    singleton BOOLEAN PRIMARY KEY DEFAULT TRUE CHECK (singleton),
    last_value BIGINT NOT NULL CHECK (last_value >= 0)
);
REVOKE ALL ON TABLE qabas_identity_private.account_number_counter
    FROM PUBLIC, anon, authenticated;

INSERT INTO qabas_identity_private.account_number_counter (singleton, last_value)
SELECT TRUE,
       COALESCE(MAX(NULLIF(regexp_replace(account_number, '[^0-9]', '', 'g'), '')::BIGINT), 0)
FROM public.users
ON CONFLICT (singleton) DO UPDATE
SET last_value = GREATEST(
    qabas_identity_private.account_number_counter.last_value,
    EXCLUDED.last_value
);

DROP SEQUENCE IF EXISTS qabas_identity_private.account_number_seq;

CREATE UNIQUE INDEX IF NOT EXISTS users_custom_id_lower_unique
    ON public.users (lower(btrim(custom_id)));
CREATE UNIQUE INDEX IF NOT EXISTS users_account_number_numeric_unique
    ON public.users (
        COALESCE(
            NULLIF(ltrim(regexp_replace(btrim(account_number), '[^0-9]', '', 'g'), '0'), ''),
            '0'
        )
    );

CREATE OR REPLACE FUNCTION qabas_identity_private.enforce_user_identity()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, auth, qabas_identity_private
AS $$
DECLARE
    v_auth_uid TEXT;
    v_auth_email TEXT;
    v_role TEXT;
    v_sequence BIGINT;
    v_slug TEXT;
BEGIN
    v_auth_uid := auth.uid()::TEXT;
    IF v_auth_uid IS NULL OR v_auth_uid = '' THEN
        RAISE EXCEPTION 'A valid Supabase Auth session is required to write a user profile'
            USING ERRCODE = '42501';
    END IF;

    v_auth_email := NULLIF(lower(btrim(auth.jwt() ->> 'email')), '');

    IF TG_OP = 'INSERT' THEN
        NEW.id := v_auth_uid;
        NEW.external_id := v_auth_uid;
        NEW.email := COALESCE(v_auth_email, NULLIF(lower(btrim(NEW.email)), ''));
        IF NEW.email IS NULL THEN
            RAISE EXCEPTION 'The authenticated account must have an email address'
                USING ERRCODE = '23502';
        END IF;

        v_role := lower(COALESCE(auth.jwt() -> 'app_metadata' ->> 'role', 'user'));
        NEW.type := CASE
            WHEN v_role IN ('developer', 'owner') THEN 'مطور'
            WHEN v_role IN ('admin', 'special') THEN 'خاص'
            ELSE 'مجاني'
        END;
        NEW.status := 'active';
        NEW.strikes := 0;
        NEW.is_eligible := TRUE;

        UPDATE qabas_identity_private.account_number_counter
           SET last_value = last_value + 1
         WHERE singleton = TRUE
         RETURNING last_value INTO v_sequence;

        IF v_sequence IS NULL THEN
            RAISE EXCEPTION 'The account number counter is not initialized'
                USING ERRCODE = '55000';
        END IF;

        NEW.account_number := '#QBS-' || lpad(v_sequence::TEXT, 4, '0');

        IF NULLIF(btrim(NEW.custom_id), '') IS NULL THEN
            v_slug := lower(regexp_replace(split_part(NEW.email, '@', 1), '[^a-z0-9]+', '_', 'g'));
            IF v_slug = '' THEN v_slug := 'user'; END IF;
            -- The sequence is globally unique, so it also guarantees a unique
            -- automatically-issued handle; trim the slug to keep the whole
            -- handle within the application's 25-character limit.
            v_slug := left(trim(both '_' from v_slug), GREATEST(1, 24 - length(v_sequence::TEXT)));
            NEW.custom_id := v_slug || '_' || v_sequence::TEXT;
        ELSE
            NEW.custom_id := lower(regexp_replace(btrim(NEW.custom_id), '^@', ''));
            IF NEW.custom_id !~ '^[a-z0-9][a-z0-9_-]{2,24}$' THEN
                RAISE EXCEPTION 'The requested username is invalid' USING ERRCODE = '22023';
            END IF;
        END IF;

        NEW.created_at := COALESCE(NEW.created_at, now());
        NEW.updated_at := now();
    ELSE
        v_role := lower(COALESCE(auth.jwt() -> 'app_metadata' ->> 'role', ''));
        IF OLD.id <> v_auth_uid AND v_role NOT IN ('admin', 'developer', 'owner') THEN
            RAISE EXCEPTION 'A user may only update their own profile' USING ERRCODE = '42501';
        END IF;

        NEW.id := OLD.id;
        NEW.external_id := OLD.external_id;
        NEW.account_number := OLD.account_number;
        NEW.created_at := OLD.created_at;
        -- Email is owned by Supabase Auth; updating another user's profile must
        -- never replace their email with the administrator's auth email.
        IF OLD.id = v_auth_uid THEN
            NEW.email := COALESCE(v_auth_email, OLD.email);
        ELSE
            NEW.email := OLD.email;
        END IF;

        IF v_role NOT IN ('admin', 'developer', 'owner') THEN
            NEW.type := OLD.type;
            NEW.status := OLD.status;
            NEW.strikes := OLD.strikes;
            NEW.is_eligible := OLD.is_eligible;
        END IF;

        IF NULLIF(btrim(NEW.custom_id), '') IS NOT NULL THEN
            NEW.custom_id := lower(regexp_replace(btrim(NEW.custom_id), '^@', ''));
            IF NEW.custom_id !~ '^[a-z0-9][a-z0-9_-]{2,24}$' THEN
                RAISE EXCEPTION 'The requested username is invalid' USING ERRCODE = '22023';
            END IF;
        ELSE
            NEW.custom_id := OLD.custom_id;
        END IF;
        NEW.updated_at := now();
    END IF;

    RETURN NEW;
END;
$$;

REVOKE ALL ON FUNCTION qabas_identity_private.enforce_user_identity()
    FROM PUBLIC, anon, authenticated;
DROP TRIGGER IF EXISTS enforce_user_identity_before_write ON public.users;
CREATE TRIGGER enforce_user_identity_before_write
    BEFORE INSERT OR UPDATE ON public.users
    FOR EACH ROW
    EXECUTE FUNCTION qabas_identity_private.enforce_user_identity();

ALTER TABLE public.users ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS users_select_own ON public.users;
CREATE POLICY users_select_own
    ON public.users
    FOR SELECT
    TO authenticated
    USING ((SELECT auth.uid())::text = id);

DROP POLICY IF EXISTS users_insert_own ON public.users;
CREATE POLICY users_insert_own
    ON public.users
    FOR INSERT
    TO authenticated
    WITH CHECK ((SELECT auth.uid())::text = id);

DROP POLICY IF EXISTS users_update_own ON public.users;
CREATE POLICY users_update_own
    ON public.users
    FOR UPDATE
    TO authenticated
    USING ((SELECT auth.uid())::text = id)
    WITH CHECK ((SELECT auth.uid())::text = id);

-- User deletion is an account-lifecycle action and is not exposed to clients.
DROP POLICY IF EXISTS users_delete_own ON public.users;

-- Authenticated users can read their own row; trusted app_metadata roles may
-- read/update the user directory for the developer dashboard.
DROP POLICY IF EXISTS users_admin_select ON public.users;
CREATE POLICY users_admin_select
    ON public.users
    FOR SELECT
    TO authenticated
    USING (
        lower(COALESCE(auth.jwt() -> 'app_metadata' ->> 'role', '')) IN ('admin', 'developer', 'owner')
    );

DROP POLICY IF EXISTS users_admin_update ON public.users;
CREATE POLICY users_admin_update
    ON public.users
    FOR UPDATE
    TO authenticated
    USING (
        lower(COALESCE(auth.jwt() -> 'app_metadata' ->> 'role', '')) IN ('admin', 'developer', 'owner')
    )
    WITH CHECK (
        lower(COALESCE(auth.jwt() -> 'app_metadata' ->> 'role', '')) IN ('admin', 'developer', 'owner')
    );

-- The interaction services use upsert for likes and bookmarks.
-- Keep the upsert conflict path owner-scoped by RLS and grant only the columns
-- required by the conflict update.
GRANT SELECT, INSERT, DELETE ON TABLE public.reel_likes TO authenticated;
GRANT UPDATE (reel_id, user_id) ON TABLE public.reel_likes TO authenticated;

DROP POLICY IF EXISTS reel_likes_update_own ON public.reel_likes;
CREATE POLICY reel_likes_update_own
    ON public.reel_likes
    FOR UPDATE
    TO authenticated
    USING ((SELECT auth.uid())::text = user_id)
    WITH CHECK ((SELECT auth.uid())::text = user_id);

GRANT SELECT, INSERT, DELETE ON TABLE public.reel_bookmarks TO authenticated;
GRANT UPDATE (reel_id, user_id) ON TABLE public.reel_bookmarks TO authenticated;

DROP POLICY IF EXISTS reel_bookmarks_update_own ON public.reel_bookmarks;
CREATE POLICY reel_bookmarks_update_own
    ON public.reel_bookmarks
    FOR UPDATE
    TO authenticated
    USING ((SELECT auth.uid())::text = user_id)
    WITH CHECK ((SELECT auth.uid())::text = user_id);

GRANT SELECT, INSERT, DELETE ON TABLE public.reel_comments TO authenticated;

-- The follow client uses upsert, which requires SELECT/INSERT/UPDATE and
-- the matching owner-scoped UPDATE policy for the conflict path.
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.creator_follows TO authenticated;

DROP POLICY IF EXISTS creator_follows_update_self ON public.creator_follows;
CREATE POLICY creator_follows_update_self
    ON public.creator_follows
    FOR UPDATE
    TO authenticated
    USING ((SELECT auth.uid())::text = follower_id)
    WITH CHECK ((SELECT auth.uid())::text = follower_id);

REVOKE ALL ON TABLE public.users FROM anon;
GRANT SELECT, INSERT, UPDATE ON TABLE public.users TO authenticated;
REVOKE DELETE ON TABLE public.users FROM authenticated;
DO $$
BEGIN
    IF to_regprocedure('public.get_next_account_sequence()') IS NOT NULL THEN
        EXECUTE 'REVOKE ALL ON FUNCTION public.get_next_account_sequence() FROM PUBLIC, anon, authenticated';
    END IF;
END
$$;

COMMIT;
