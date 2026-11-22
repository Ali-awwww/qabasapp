-- Community points & honest reviews (Qabas Batch 6, Priority 5).
--
-- What this adds:
--   * `user_points` — one row per user: total community points and the
--     current ISO-week total. This replaces the legacy `users.strikes`
--     column as the points store. The `strikes` column is NOT touched
--     here: it is legacy, its data stays, and no code reads or writes it
--     as points any more.
--   * `points_ledger` — one auditable row per points award (delta +
--     reason + time). Weekly / monthly leagues are computed from this
--     table, never from an overwritten total.
--   * `review_helpful_votes` — one "helpful" vote per (user, review);
--     the displayed count is the real vote count.
--   * `review_reports` — one row per user report on a review. There is
--     no moderation-queue UI yet; reports are stored for later review.
--   * Hardening of `app_reviews`: `user_id` column (if missing),
--     `verified_user` default false (never assumed true), one review per
--     user, owner-only update/delete, authenticated-only insert.
--
-- Never applied live from the VM — apply from the Supabase dashboard
-- SQL Editor (see supabase/seed/community_points_APPLY.md).

-- ── user_points ──────────────────────────────────────────────────
create table if not exists public.user_points (
    user_id uuid not null references auth.users(id) on delete cascade,
    points bigint not null default 0 check (points >= 0),
    weekly_points bigint not null default 0 check (weekly_points >= 0),
    updated_at timestamptz not null default now(),
    constraint user_points_pkey primary key (user_id)
);

create index if not exists user_points_points_idx
    on public.user_points (points desc);

alter table public.user_points enable row level security;

-- Points are public (user_id + points + weekly_points are all
-- public-safe columns); writes are owner-only.
drop policy if exists user_points_select_public on public.user_points;
create policy user_points_select_public
    on public.user_points for select to anon, authenticated
    using (true);

drop policy if exists user_points_insert_own on public.user_points;
create policy user_points_insert_own
    on public.user_points for insert to authenticated
    with check ((select auth.uid()) = user_id);

drop policy if exists user_points_update_own on public.user_points;
create policy user_points_update_own
    on public.user_points for update to authenticated
    using ((select auth.uid()) = user_id)
    with check ((select auth.uid()) = user_id);

revoke all on table public.user_points from anon, public;
grant select on table public.user_points to anon, authenticated;
grant select, insert, update on table public.user_points to authenticated;

-- ── points_ledger ────────────────────────────────────────────────
create table if not exists public.points_ledger (
    id uuid not null default gen_random_uuid(),
    user_id uuid not null references auth.users(id) on delete cascade,
    points_delta integer not null check (points_delta > 0),
    reason text not null default '',
    created_at timestamptz not null default now(),
    constraint points_ledger_pkey primary key (id)
);

create index if not exists points_ledger_user_created_idx
    on public.points_ledger (user_id, created_at desc);
create index if not exists points_ledger_created_idx
    on public.points_ledger (created_at desc);

alter table public.points_ledger enable row level security;

-- A user sees only their own ledger; inserts are owner-only; no
-- updates or deletes (the ledger is append-only by design).
drop policy if exists points_ledger_select_own on public.points_ledger;
create policy points_ledger_select_own
    on public.points_ledger for select to authenticated
    using ((select auth.uid()) = user_id);

drop policy if exists points_ledger_insert_own on public.points_ledger;
create policy points_ledger_insert_own
    on public.points_ledger for insert to authenticated
    with check ((select auth.uid()) = user_id);

revoke all on table public.points_ledger from anon, public;
grant select, insert on table public.points_ledger to authenticated;

-- ── review_helpful_votes ─────────────────────────────────────────
create table if not exists public.review_helpful_votes (
    user_id uuid not null references auth.users(id) on delete cascade,
    review_id text not null references public.app_reviews(id) on delete cascade,
    created_at timestamptz not null default now(),
    constraint review_helpful_votes_pkey primary key (user_id, review_id)
);

create index if not exists review_helpful_votes_review_idx
    on public.review_helpful_votes (review_id);

alter table public.review_helpful_votes enable row level security;

-- Vote counts are public (needed to display them); a vote row is
-- written or removed only by its owner.
drop policy if exists review_helpful_votes_select_public on public.review_helpful_votes;
create policy review_helpful_votes_select_public
    on public.review_helpful_votes for select to anon, authenticated
    using (true);

drop policy if exists review_helpful_votes_insert_own on public.review_helpful_votes;
create policy review_helpful_votes_insert_own
    on public.review_helpful_votes for insert to authenticated
    with check ((select auth.uid()) = user_id);

drop policy if exists review_helpful_votes_delete_own on public.review_helpful_votes;
create policy review_helpful_votes_delete_own
    on public.review_helpful_votes for delete to authenticated
    using ((select auth.uid()) = user_id);

revoke all on table public.review_helpful_votes from anon, public;
grant select on table public.review_helpful_votes to anon, authenticated;
grant select, insert, delete on table public.review_helpful_votes to authenticated;

-- ── review_reports ───────────────────────────────────────────────
create table if not exists public.review_reports (
    id uuid not null default gen_random_uuid(),
    review_id text not null references public.app_reviews(id) on delete cascade,
    reporter_id uuid not null references auth.users(id) on delete cascade,
    reason text not null default '',
    created_at timestamptz not null default now(),
    constraint review_reports_pkey primary key (id)
);

create index if not exists review_reports_review_idx
    on public.review_reports (review_id, created_at desc);

alter table public.review_reports enable row level security;

-- Reports are write-only for regular users (no select policy):
-- they are stored for later moderation, not listed in the app.
drop policy if exists review_reports_insert_own on public.review_reports;
create policy review_reports_insert_own
    on public.review_reports for insert to authenticated
    with check ((select auth.uid()) = reporter_id);

revoke all on table public.review_reports from anon, public;
grant insert on table public.review_reports to authenticated;

-- ── app_reviews hardening ────────────────────────────────────────
-- user_id may already exist in the live database (the canonical
-- identity migration references it); add it only if missing. Its
-- type is left as-is on purpose: the app compares it as text.
alter table public.app_reviews
    add column if not exists user_id uuid references auth.users(id) on delete cascade;

-- A review is not "verified" by default; the app sets verified_user
-- only when the author's Auth email is actually confirmed.
alter table public.app_reviews
    alter column verified_user set default false;

-- One review per user: create the unique index only when no user
-- already has two reviews; otherwise the duplicates must be merged
-- by hand first (see community_points_APPLY.md).
do $$
begin
    if not exists (
        select 1
        from (
            select user_id
            from public.app_reviews
            where user_id is not null
            group by user_id
            having count(*) > 1
        ) dup
    ) then
        create unique index if not exists app_reviews_one_per_user_idx
            on public.app_reviews (user_id)
            where user_id is not null;
    end if;
end
$$;

-- Replace the old wide-open "Allow public update helpful_count"
-- policy: only the review's owner may update or delete it, and only
-- signed-in users may insert (as their own user_id). The old public
-- insert policy is dropped for the same reason: RLS policies are
-- permissive, so leaving it would keep anonymous inserts open.
-- Public read stays as it was. The legacy `helpful_count` column is
-- left in place (unmaintained cache); the displayed count comes from
-- `review_helpful_votes`.
drop policy if exists "Allow public update helpful_count" on public.app_reviews;
drop policy if exists "Allow public insert app_reviews" on public.app_reviews;

drop policy if exists app_reviews_insert_own on public.app_reviews;
create policy app_reviews_insert_own
    on public.app_reviews for insert to authenticated
    with check (user_id::text = (select auth.uid())::text);

drop policy if exists app_reviews_update_own on public.app_reviews;
create policy app_reviews_update_own
    on public.app_reviews for update to authenticated
    using (user_id::text = (select auth.uid())::text)
    with check (user_id::text = (select auth.uid())::text);

drop policy if exists app_reviews_delete_own on public.app_reviews;
create policy app_reviews_delete_own
    on public.app_reviews for delete to authenticated
    using (user_id::text = (select auth.uid())::text);

-- Keep the helpful_count column usable by the app's reads; nothing
-- writes it any more after this batch.
