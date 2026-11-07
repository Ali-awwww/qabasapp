alter table public.mishkat_reminder_content
    add column if not exists lesson_url text null
    check (lesson_url is null or (char_length(lesson_url) <= 2048 and lesson_url ~ '^https://'));
