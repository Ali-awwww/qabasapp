-- Create audio_tracks table in Supabase for user uploaded and shared audio recordings
create table if not exists public.audio_tracks (
    id text primary key,
    title text not null,
    artist text default '',
    category text default 'تلاوات قرآنية',
    audio_url text not null,
    duration text default '0:00',
    badge text default 'سحابي',
    author text default '',
    likes_count integer default 0,
    is_active boolean default true,
    user_id text,
    created_at timestamptz default now()
);

-- Enable RLS
alter table public.audio_tracks enable row level security;

-- Allow public read access to active tracks so everyone using the app can stream recordings
create policy "Allow public read access to audio_tracks"
    on public.audio_tracks
    for select
    using (true);

-- Allow developer/admin insert and update for audio tracks
create policy "Allow insert access to audio_tracks"
    on public.audio_tracks
    for insert
    with check (true);

-- Allow developer/admin update and delete
create policy "Allow update access to audio_tracks"
    on public.audio_tracks
    for update
    using (true);

create policy "Allow delete access to audio_tracks"
    on public.audio_tracks
    for delete
    using (true);
