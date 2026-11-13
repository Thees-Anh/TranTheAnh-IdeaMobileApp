-- Upcoming quest deadlines for on-device alarm scheduling. The device schedules
-- local alarms from this list so reminders fire on time in Doze/offline; the
-- live reminder_context remains the authority whenever the network is available.
begin;

create function pawquest_private.reminder_schedule_snapshot(p_actor uuid,p_stamp timestamptz,p_timezone text) returns jsonb
language plpgsql security definer set search_path='' as $$
declare actor uuid:=p_actor; stamp timestamptz:=p_stamp; day_start timestamptz;
    day_end timestamptz; next_day_end timestamptz; horizon timestamptz:=p_stamp+interval '48 hours'; upcoming jsonb;
begin
    if actor is null then raise exception 'Authentication required' using errcode='42501'; end if;
    if p_timezone is null or not exists(select 1 from pg_catalog.pg_timezone_names where name=p_timezone) then
        raise exception 'Valid IANA timezone required' using errcode='23514';
    end if;
    day_start:=date_trunc('day',stamp at time zone p_timezone) at time zone p_timezone;
    day_end:=(date_trunc('day',stamp at time zone p_timezone)+interval '1 day') at time zone p_timezone;
    next_day_end:=(date_trunc('day',stamp at time zone p_timezone)+interval '2 days') at time zone p_timezone;
    with deadlines as (
        -- Today's daily deadline only while today's completion is missing.
        select q.id,q.title,day_end as due_at from public.quests q
        where q.user_id=actor and not q.archived and q.repeat_type='daily'
          and not exists(select 1 from public.quest_sessions s where s.user_id=actor and s.quest_id=q.id
              and s.status in ('active','completed','claimed') and s.ends_at>=day_start and s.ends_at<day_end and s.ends_at<=stamp)
        union all
        -- Tomorrow's daily deadline is always still open.
        select q.id,q.title,next_day_end from public.quests q
        where q.user_id=actor and not q.archived and q.repeat_type='daily'
        union all
        select q.id,q.title,q.due_at from public.quests q
        where q.user_id=actor and not q.archived and q.repeat_type='one_time'
          and q.due_at is not null and q.due_at>stamp and q.due_at<=horizon
          and not exists(select 1 from public.quest_sessions s where s.user_id=actor and s.quest_id=q.id
              and s.status in ('active','completed','claimed') and s.ends_at<=stamp)
    )
    select coalesce(jsonb_agg(jsonb_build_object('id',d.id,'title',d.title,'due_at',d.due_at) order by d.due_at,d.id),'[]'::jsonb)
        into upcoming from deadlines d where d.due_at>stamp and d.due_at<=horizon;
    return jsonb_build_object('user_id',actor,'server_now',stamp,'timezone',p_timezone,
        'has_daily_quest',exists(select 1 from public.quests where user_id=actor and not archived and repeat_type='daily'),
        'upcoming',upcoming);
end $$;
revoke all on function pawquest_private.reminder_schedule_snapshot(uuid,timestamptz,text) from public,anon,authenticated,service_role;

create function public.reminder_schedule(p_timezone text) returns jsonb
language sql security definer set search_path='' as $$
    select pawquest_private.reminder_schedule_snapshot(auth.uid(),clock_timestamp(),p_timezone);
$$;
revoke all on function public.reminder_schedule(text) from public,anon,authenticated,service_role;
grant execute on function public.reminder_schedule(text) to authenticated;
comment on function public.reminder_schedule(text) is
    'Read-only upcoming quest deadlines (next 48h) for local alarm scheduling; never grants rewards.';
commit;
