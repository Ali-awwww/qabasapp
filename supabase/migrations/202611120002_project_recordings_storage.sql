-- Private project voice recordings.
-- Objects are always stored under <auth.uid()>/<project-id>/<filename>.
-- No public read policy is intentionally created.

INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
    'project-recordings',
    'project-recordings',
    false,
    26214400,
    ARRAY['audio/mp4', 'audio/m4a', 'audio/x-m4a', 'audio/aac', 'audio/mpeg', 'audio/wav', 'audio/ogg']
)
ON CONFLICT (id) DO UPDATE SET
    public = false,
    file_size_limit = EXCLUDED.file_size_limit,
    allowed_mime_types = EXCLUDED.allowed_mime_types;

DROP POLICY IF EXISTS "qabas_project_recordings_select_own" ON storage.objects;
CREATE POLICY "qabas_project_recordings_select_own"
    ON storage.objects FOR SELECT TO authenticated
    USING (
        bucket_id = 'project-recordings'
        AND (storage.foldername(name))[1] = (select auth.uid())::text
    );

DROP POLICY IF EXISTS "qabas_project_recordings_insert_own" ON storage.objects;
CREATE POLICY "qabas_project_recordings_insert_own"
    ON storage.objects FOR INSERT TO authenticated
    WITH CHECK (
        bucket_id = 'project-recordings'
        AND (storage.foldername(name))[1] = (select auth.uid())::text
    );

DROP POLICY IF EXISTS "qabas_project_recordings_update_own" ON storage.objects;
CREATE POLICY "qabas_project_recordings_update_own"
    ON storage.objects FOR UPDATE TO authenticated
    USING (
        bucket_id = 'project-recordings'
        AND (storage.foldername(name))[1] = (select auth.uid())::text
    )
    WITH CHECK (
        bucket_id = 'project-recordings'
        AND (storage.foldername(name))[1] = (select auth.uid())::text
    );

DROP POLICY IF EXISTS "qabas_project_recordings_delete_own" ON storage.objects;
CREATE POLICY "qabas_project_recordings_delete_own"
    ON storage.objects FOR DELETE TO authenticated
    USING (
        bucket_id = 'project-recordings'
        AND (storage.foldername(name))[1] = (select auth.uid())::text
    );
