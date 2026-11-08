create index if not exists idx_user_azan_voices_user_id_created_at
    on public.user_azan_voices (user_id, created_at desc);
