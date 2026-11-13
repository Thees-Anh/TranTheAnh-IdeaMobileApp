begin;
insert into public.pet_species(code,name,unlock_level,renderer_key,published)
    values('puppy','Puppy',5,'puppy',true);
update public.item_catalog set name='Pet food' where code='basic_food';

-- Presentation facts only; this never completes sessions or claims rewards.
create function pawquest_private.companion_snapshot(p_actor uuid,p_stamp timestamptz,p_timezone text) returns jsonb
language plpgsql security definer set search_path='' as $$
declare today_start timestamptz; yesterday_start timestamptz; completed_id uuid; completed_time timestamptz;
    missed boolean;
begin
    if p_actor is null then raise exception 'Authentication required' using errcode='42501'; end if;
    if p_timezone is null or not exists(select 1 from pg_catalog.pg_timezone_names where name=p_timezone) then
        raise exception 'Valid IANA timezone required' using errcode='23514';
    end if;
    today_start:=date_trunc('day',p_stamp at time zone p_timezone) at time zone p_timezone;
    yesterday_start:=(date_trunc('day',p_stamp at time zone p_timezone)-interval '1 day') at time zone p_timezone;
    select s.id,s.ends_at into completed_id,completed_time
        from public.quest_sessions s join public.quests q on q.id=s.quest_id and q.user_id=s.user_id
        where s.user_id=p_actor and q.repeat_type='daily' and s.status in ('active','completed','claimed') and s.ends_at<=p_stamp
        order by s.ends_at desc,s.id desc limit 1;
    select exists(
        select 1 from public.quests q where q.user_id=p_actor and not q.archived and
        ((q.repeat_type='one_time' and q.due_at<p_stamp and not exists(
            select 1 from public.quest_sessions s where s.user_id=p_actor and s.quest_id=q.id
            and s.status in ('active','completed','claimed') and s.ends_at<=p_stamp)) or
         (q.repeat_type='daily' and q.created_at<today_start and not exists(
            select 1 from public.quest_sessions s where s.user_id=p_actor and s.quest_id=q.id
            and s.status in ('active','completed','claimed') and s.ends_at>=yesterday_start and s.ends_at<=p_stamp)))
    ) into missed;
    return jsonb_build_object('user_id',p_actor,'server_now',p_stamp,'has_missed_quest',missed,
        'completion_id',completed_id,'completed_at',completed_time);
end $$;
revoke all on function pawquest_private.companion_snapshot(uuid,timestamptz,text) from public,anon,authenticated,service_role;
create function public.pet_companion_context(p_timezone text) returns jsonb
language sql security definer set search_path='' as $$
    select pawquest_private.companion_snapshot(auth.uid(),clock_timestamp(),p_timezone);
$$;
revoke all on function public.pet_companion_context(text) from public,anon,authenticated,service_role;
grant execute on function public.pet_companion_context(text) to authenticated;
commit;
