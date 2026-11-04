-- Qabas: secure cloud publishing for developer-curated scholar lecture links.
-- Only trusted server-issued app_metadata roles may write. Local developer toggles
-- are intentionally not treated as authorization.
ALTER TABLE public.scholar_lectures
  ADD COLUMN IF NOT EXISTS created_by_user_id TEXT;

DROP POLICY IF EXISTS "Public insert on scholar_lectures" ON public.scholar_lectures;
DROP POLICY IF EXISTS "Public update on scholar_lectures" ON public.scholar_lectures;
DROP POLICY IF EXISTS "Public delete on scholar_lectures" ON public.scholar_lectures;
DROP POLICY IF EXISTS "scholar_lectures_developer_insert" ON public.scholar_lectures;
DROP POLICY IF EXISTS "scholar_lectures_developer_update" ON public.scholar_lectures;
DROP POLICY IF EXISTS "scholar_lectures_developer_delete" ON public.scholar_lectures;

CREATE POLICY "scholar_lectures_developer_insert"
  ON public.scholar_lectures FOR INSERT TO authenticated
  WITH CHECK (
    lower(coalesce((auth.jwt() -> 'app_metadata' ->> 'role'), ''))
      IN ('developer', 'admin', 'owner')
    AND created_by_user_id = auth.uid()::text
  );

CREATE POLICY "scholar_lectures_developer_update"
  ON public.scholar_lectures FOR UPDATE TO authenticated
  USING (
    lower(coalesce((auth.jwt() -> 'app_metadata' ->> 'role'), ''))
      IN ('developer', 'admin', 'owner')
  )
  WITH CHECK (
    lower(coalesce((auth.jwt() -> 'app_metadata' ->> 'role'), ''))
      IN ('developer', 'admin', 'owner')
  );

CREATE POLICY "scholar_lectures_developer_delete"
  ON public.scholar_lectures FOR DELETE TO authenticated
  USING (
    lower(coalesce((auth.jwt() -> 'app_metadata' ->> 'role'), ''))
      IN ('developer', 'admin', 'owner')
  );

GRANT SELECT ON public.scholar_lectures TO anon, authenticated;
GRANT INSERT, UPDATE, DELETE ON public.scholar_lectures TO authenticated;
