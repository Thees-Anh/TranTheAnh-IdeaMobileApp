-- Widget facts use local quest days, not the UTC reward/streak day.
begin;
create function pawquest_private.widget_context(p_actor uuid,p_stamp timestamptz,p_timezone text) returns jsonb
language plpgsql security definer set search_path='' as $$
declare day_start timestamptz; day_end timestamptz; pending integer;
begin
    if p_actor is null then raise exception 'Authentication required' using errcode='42501';end if;
    if p_timezone is null or not exists(select 1 from pg_catalog.pg_timezone_names where name=p_timezone) then
        raise exception 'Valid timezone required' using errcode='23514';end if;
    day_start:=date_trunc('day',p_stamp at time zone p_timezone) at time zone p_timezone;
    day_end:=(date_trunc('day',p_stamp at time zone p_timezone)+interval '1 day') at time zone p_timezone;
    select count(*) into pending from public.quests q
    where q.user_id=p_actor and not q.archived
      and (q.repeat_type='daily' or q.due_at is null or q.due_at<day_end)
      and not exists(select 1 from public.quest_sessions s where s.user_id=p_actor and s.quest_id=q.id
        and s.status in ('active','completed','claimed') and s.ends_at<=p_stamp
        and (q.repeat_type='one_time' or s.ends_at>=day_start and s.ends_at<day_end));
    return jsonb_build_object('user_id',p_actor,'server_now',p_stamp,'timezone',p_timezone,'pending_count',pending);
end $$;
create function public.widget_context(p_timezone text) returns jsonb
language sql security definer set search_path='' as $$
    select pawquest_private.widget_context(auth.uid(),clock_timestamp(),p_timezone);
$$;
revoke all on function pawquest_private.widget_context(uuid,timestamptz,text),public.widget_context(text) from public,anon,authenticated,service_role;
grant execute on function public.widget_context(text) to authenticated;
commit;
