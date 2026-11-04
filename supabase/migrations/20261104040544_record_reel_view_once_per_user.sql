-- Record one cloud view per authenticated user and reel.
-- The unique key is the source of truth; the RPC increments the aggregate only
-- when the insert wins, in the same transaction.

create unique index if not exists reel_history_reel_user_uidx
    on public.reel_history (reel_id, user_id);

create or replace function public.record_reel_view(
    p_reel_id text,
    p_watched_duration_sec integer default 0
)
returns integer
language plpgsql
security definer
set search_path = ''
as $function$
declare
    v_user_id uuid := auth.uid();
    v_inserted_id uuid;
    v_views_count integer;
begin
    if v_user_id is null then
        raise exception 'Authentication required' using errcode = '28000';
    end if;

    if p_reel_id is null or length(trim(p_reel_id)) = 0 then
        raise exception 'A valid reel id is required' using errcode = '22023';
    end if;

    perform 1
      from public.reels r
     where r.id = p_reel_id;

    if not found then
        raise exception 'Reel not found' using errcode = 'P0002';
    end if;

    insert into public.reel_history (
        reel_id,
        user_id,
        watched_duration_sec
    )
    values (
        p_reel_id,
        v_user_id::text,
        greatest(0, least(coalesce(p_watched_duration_sec, 0), 3600))
    )
    on conflict (reel_id, user_id) do nothing
    returning id into v_inserted_id;

    if v_inserted_id is not null then
        update public.reels
           set views_count = coalesce(views_count, 0) + 1,
               updated_at = now()
         where id = p_reel_id
        returning views_count into v_views_count;
    else
        select coalesce(r.views_count, 0)
          into v_views_count
          from public.reels r
         where r.id = p_reel_id;
    end if;

    return coalesce(v_views_count, 0);
end;
$function$;

revoke all on function public.record_reel_view(text, integer) from public;
revoke all on function public.record_reel_view(text, integer) from anon;
grant execute on function public.record_reel_view(text, integer) to authenticated;
