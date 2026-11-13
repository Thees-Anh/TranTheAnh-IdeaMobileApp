-- Reminder deadlines do not alter focus eligibility, reward days, or reward limits.
begin;
alter table public.quests add column due_at timestamptz,
    add constraint quest_due_finite check(due_at is null or isfinite(due_at)),
    add constraint daily_due_is_local_day check(repeat_type<>'daily' or due_at is null);
grant insert(due_at),update(due_at) on public.quests to authenticated;
create index quests_owner_due on public.quests(user_id,due_at) where not archived and due_at is not null;

create function pawquest_private.reminder_snapshot(p_actor uuid,p_stamp timestamptz,p_timezone text) returns jsonb
language plpgsql security definer set search_path='' as $$
declare actor uuid:=p_actor; stamp timestamptz:=p_stamp; day_start timestamptz;
    day_end timestamptz; pending jsonb;
begin
    if actor is null then raise exception 'Authentication required' using errcode='42501'; end if;
    if p_timezone is null or not exists(select 1 from pg_catalog.pg_timezone_names where name=p_timezone) then
        raise exception 'Valid IANA timezone required' using errcode='23514';
    end if;
    day_start:=date_trunc('day',stamp at time zone p_timezone) at time zone p_timezone;
    day_end:=(date_trunc('day',stamp at time zone p_timezone)+interval '1 day') at time zone p_timezone;
    select coalesce(jsonb_agg(jsonb_build_object('id',q.id,'title',q.title,
        'due_at',case when q.repeat_type='daily' then day_end else q.due_at end)
        order by q.due_at nulls last,q.id),'[]'::jsonb) into pending
    from public.quests q
    where q.user_id=actor and not q.archived
      and ((q.repeat_type='daily' and day_end<=stamp+interval '2 hours') or
           (q.repeat_type='one_time' and q.due_at<=stamp+interval '2 hours'))
      and not exists(select 1 from public.quest_sessions s
          where s.user_id=actor and s.quest_id=q.id
          and s.status in ('active','completed','claimed') and s.ends_at<=stamp
          and (q.repeat_type='one_time' or s.ends_at>=day_start and s.ends_at<day_end));
    return jsonb_build_object('user_id',actor,'server_now',stamp,'timezone',p_timezone,
        'has_daily_quest',exists(select 1 from public.quests where user_id=actor and not archived and repeat_type='daily'),
        'pending_quests',pending);
end $$;
revoke all on function pawquest_private.reminder_snapshot(uuid,timestamptz,text) from public,anon,authenticated,service_role;
create function public.reminder_context(p_timezone text) returns jsonb
language sql security definer set search_path='' as $$
    select pawquest_private.reminder_snapshot(auth.uid(),clock_timestamp(),p_timezone);
$$;
revoke all on function public.reminder_context(text) from public,anon,authenticated,service_role;
grant execute on function public.reminder_context(text) to authenticated;
commit;
