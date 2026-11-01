-- Make bug-report screenshots private and issue short-lived signed URLs
-- only after the caller passes the storage.objects SELECT policy.
--
-- The previous "qabas storage public read" policy also exposed several public
-- media buckets. Recreate it with the same bucket list except bug_reports.

BEGIN;

DROP POLICY IF EXISTS "qabas storage public read" ON storage.objects;
CREATE POLICY "qabas storage public read"
    ON storage.objects
    FOR SELECT
    TO public
    USING (
        bucket_id = ANY (
            ARRAY[
                'reels'::text,
                'media-assets'::text,
                'user-uploads'::text,
                'avatars'::text,
                'azan-voices'::text,
                'audio_tracks'::text
            ]
        )
    );

DROP POLICY IF EXISTS "qabas_bug_reports_authenticated_read_own"
    ON storage.objects;
CREATE POLICY "qabas_bug_reports_authenticated_read_own"
    ON storage.objects
    FOR SELECT
    TO authenticated
    USING (
        bucket_id = 'bug_reports'
        AND (
            public.qabas_is_support_admin()
            OR (storage.foldername(name))[1] = auth.uid()::text
        )
    );

UPDATE storage.buckets
SET public = false
WHERE id = 'bug_reports';

COMMIT;
