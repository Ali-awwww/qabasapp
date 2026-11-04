-- Index the per-user activity feeds used by the Reels Activity Hub.
create index if not exists idx_reel_history_user_created_at
    on public.reel_history (user_id, created_at desc);

create index if not exists idx_reel_bookmarks_user_created_at
    on public.reel_bookmarks (user_id, created_at desc);

create index if not exists idx_reel_likes_user_created_at
    on public.reel_likes (user_id, created_at desc);
