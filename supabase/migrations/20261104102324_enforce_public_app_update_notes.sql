do $$
begin
  if not exists (
    select 1
    from pg_constraint
    where conname = 'app_updates_public_notes_safe'
      and conrelid = 'public.app_updates'::regclass
  ) then
    alter table public.app_updates
      add constraint app_updates_public_notes_safe check (
        position('github.com/' in lower(release_notes)) = 0
        and position('[force]' in lower(release_notes)) = 0
        and position('build number:' in lower(release_notes)) = 0
        and position('built at:' in lower(release_notes)) = 0
        and position('branch:' in lower(release_notes)) = 0
        and position('workflow run:' in lower(release_notes)) = 0
        and position('workflow run url:' in lower(release_notes)) = 0
        and position('commit:' in lower(release_notes)) = 0
        and position('commit sha:' in lower(release_notes)) = 0
      );
  end if;
end $$;
