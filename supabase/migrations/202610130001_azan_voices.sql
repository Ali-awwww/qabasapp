-- أصوات الأذان السحابية: يرفعها المطور فقط، ويقرأها الجميع
create table if not exists public.azan_voices (
    id text primary key,
    name_ar text not null,
    muezzin_ar text not null,
    description text default '',
    audio_url text not null,
    is_fajr_specific boolean default false,
    is_active boolean default true,
    created_by text,
    created_at timestamptz default now()
);

alter table public.azan_voices enable row level security;

drop policy if exists "public read azan_voices" on public.azan_voices;
create policy "public read azan_voices"
    on public.azan_voices for select using (is_active = true);

drop policy if exists "allow insert azan_voices" on public.azan_voices;
create policy "allow insert azan_voices"
    on public.azan_voices for insert with check (true);

drop policy if exists "allow update azan_voices" on public.azan_voices;
create policy "allow update azan_voices"
    on public.azan_voices for update using (true);

drop policy if exists "allow delete azan_voices" on public.azan_voices;
create policy "allow delete azan_voices"
    on public.azan_voices for delete using (true);

-- bucket عام للأذان (أنشئه من لوحة Supabase > Storage إن لم يوجد: azan-voices / public)
insert into storage.buckets (id, name, public)
values ('azan-voices', 'azan-voices', true)
on conflict (id) do nothing;

drop policy if exists "public read azan-voices" on storage.objects;
create policy "public read azan-voices"
    on storage.objects for select using (bucket_id = 'azan-voices');

drop policy if exists "allow upload azan-voices" on storage.objects;
create policy "allow upload azan-voices"
    on storage.objects for insert with check (bucket_id = 'azan-voices');

drop policy if exists "allow delete azan-voices" on storage.objects;
create policy "allow delete azan-voices"
    on storage.objects for delete using (bucket_id = 'azan-voices');
