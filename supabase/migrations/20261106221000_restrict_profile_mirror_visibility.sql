-- The original broad community policy would become effective once authenticated
-- receives SELECT. Keep profile mirror reads limited to the account owner.
DROP POLICY IF EXISTS profiles_community_read ON public.profiles;
