-- Cover the app_update_delta_cache_job_id_fkey foreign key.
-- This avoids sequential scans when jobs are updated or deleted.

CREATE INDEX IF NOT EXISTS idx_app_update_delta_cache_job_id
    ON public.app_update_delta_cache (job_id);
