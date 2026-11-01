-- Keep authenticated clients limited to the four CRUD operations protected by RLS.
revoke all on table public.saved_items from anon, public, authenticated;
grant select, insert, update, delete on table public.saved_items to authenticated;
