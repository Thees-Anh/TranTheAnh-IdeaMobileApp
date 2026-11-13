begin;
create table public.achievement_catalog (
    code text primary key, name text not null, streak_days integer not null unique check(streak_days>0)
);
insert into public.achievement_catalog values
('streak_7','A week of little wins',7),('streak_30','Thirty days of focus',30),('streak_100','One hundred day companion',100);
create table public.user_achievements (
    user_id uuid not null references public.profiles(id) on delete cascade,code text not null references public.achievement_catalog(code),
    earned_on date not null check(isfinite(earned_on)),earned_at timestamptz not null default clock_timestamp(),
    primary key(user_id,code)
);
create table public.progress_events (
    id uuid primary key default gen_random_uuid(),user_id uuid not null references public.profiles(id) on delete cascade,
    source_key text not null,kind text not null check(kind in ('level_up','badge')),
    from_level bigint,to_level bigint,food_quantity integer not null default 0 check(food_quantity between 0 and 1000000),
    badge_code text references public.achievement_catalog(code),created_at timestamptz not null default clock_timestamp(),
    acknowledged_at timestamptz,unique(user_id,source_key),
    check((kind='level_up' and from_level is not null and to_level is not null and from_level>=1 and to_level>from_level and badge_code is null)
       or (kind='badge' and from_level is null and to_level is null and badge_code is not null and food_quantity=0))
);
create table pawquest_private.progress_state (
    user_id uuid primary key references public.profiles(id) on delete cascade,last_level bigint not null check(last_level>=1)
);
-- Existing levels are a baseline, not a retroactive food refill.
insert into pawquest_private.progress_state
    select user_id,xp/(pawquest_private.pet_care_balance()->>'xpPerLevel')::integer+1 from public.pets;
create function pawquest_private.progress_new_profile() returns trigger
language plpgsql security definer set search_path='' as $$
begin insert into pawquest_private.progress_state values(new.id,1); return new; end $$;
create trigger profile_progress after insert on public.profiles for each row execute function pawquest_private.progress_new_profile();
revoke all on pawquest_private.progress_state from public,anon,authenticated,service_role;
alter table public.achievement_catalog enable row level security;
alter table public.user_achievements enable row level security;
alter table public.progress_events enable row level security;
revoke all on public.achievement_catalog,public.user_achievements,public.progress_events from public,anon,authenticated,service_role;
grant select on public.achievement_catalog,public.user_achievements,public.progress_events to authenticated;
create policy achievements_catalog_read on public.achievement_catalog for select to authenticated using(true);
create policy achievements_owner_read on public.user_achievements for select to authenticated using(user_id=(select auth.uid()));
create policy events_owner_read on public.progress_events for select to authenticated using(user_id=(select auth.uid()));
create index events_owner_pending on public.progress_events(user_id,created_at,id) where acknowledged_at is null;
create index sessions_habit_history on public.quest_sessions(user_id,ends_at) where status in ('active','completed','claimed');

create function pawquest_private.progress_facts(p_actor uuid,p_stamp timestamptz) returns jsonb
language sql stable set search_path='' as $$
with days as (
    select (ends_at at time zone 'UTC')::date completion_day,count(*)::integer count from public.quest_sessions
    where user_id=p_actor and ends_at<=p_stamp and status in ('active','completed','claimed') group by 1
), tagged as (select *,completion_day-row_number() over(order by completion_day)::integer grp from days),
numbered as (select *,row_number() over(partition by grp order by completion_day)::integer run_length from tagged),
runs as (select max(completion_day) last_day,count(*)::integer length from tagged group by grp)
select jsonb_build_object('today',(p_stamp at time zone 'UTC')::date,
 'current_streak',coalesce((select case when last_day>=(p_stamp at time zone 'UTC')::date-1 then length else 0 end from runs order by last_day desc limit 1),0),
 'best_streak',coalesce((select max(length) from runs),0),
 'days',coalesce((select jsonb_agg(jsonb_build_object('day',completion_day,'count',count,'run_length',run_length) order by completion_day) from numbered),'[]'::jsonb));
$$;

create function pawquest_private.progress_sync(p_actor uuid,p_stamp timestamptz) returns jsonb
language plpgsql security definer set search_path='' as $$
declare p public.pets%rowtype; previous bigint; current_level bigint; food integer; capacity integer;
    award integer; facts jsonb; badge public.achievement_catalog%rowtype; earned date;
begin
    if p_actor is null then raise exception 'Authentication required' using errcode='42501'; end if;
    perform 1 from public.wallets where user_id=p_actor for update;
    if not found then raise exception 'Account not initialized' using errcode='23503'; end if;
    select * into p from public.pets where user_id=p_actor for update;
    if not found then raise exception 'Pet unavailable' using errcode='23503'; end if;
    current_level:=p.xp/(pawquest_private.pet_care_balance()->>'xpPerLevel')::integer+1;
    insert into pawquest_private.progress_state values(p_actor,current_level) on conflict do nothing;
    select last_level into previous from pawquest_private.progress_state where user_id=p_actor for update;
    if current_level>previous then
        select quantity into food from public.user_inventory where user_id=p_actor and item_id='e1000000-0000-0000-0000-000000000001' for update;
        select max_quantity into capacity from public.item_catalog where code='basic_food' and active;
        award:=least(current_level-previous,greatest(0,coalesce(capacity,0)-coalesce(food,0)))::integer;
        if award>0 then perform pawquest_private.adjust_inventory(p_actor,'e1000000-0000-0000-0000-000000000001',award); end if;
        insert into public.progress_events(user_id,source_key,kind,from_level,to_level,food_quantity,created_at)
            values(p_actor,'level:'||current_level,'level_up',previous,current_level,award,p_stamp);
        update pawquest_private.progress_state set last_level=current_level where user_id=p_actor;
    end if;
    facts:=pawquest_private.progress_facts(p_actor,p_stamp);
    for badge in select * from public.achievement_catalog where streak_days<=(facts->>'best_streak')::integer order by streak_days loop
        select min((d->>'day')::date) into earned from jsonb_array_elements(facts->'days') d where (d->>'run_length')::integer>=badge.streak_days;
        insert into public.user_achievements(user_id,code,earned_on,earned_at) values(p_actor,badge.code,earned,p_stamp) on conflict do nothing;
        if found then
            insert into public.progress_events(user_id,source_key,kind,badge_code,created_at)
                values(p_actor,'badge:'||badge.code,'badge',badge.code,p_stamp);
        end if;
    end loop;
    return facts;
end $$;

-- Level gifts and milestone awards share the original reward transaction.
create or replace function pawquest_private.apply_reward() returns trigger
language plpgsql security definer set search_path='' as $$
begin
    update public.wallets set coins=new.balance_after where user_id=new.user_id;
    update public.pets set xp=xp+new.pet_xp where user_id=new.user_id;
    if not found then raise exception 'Pet missing' using errcode='23503'; end if;
    update public.quest_sessions set status='claimed',claimed_at=new.created_at where id=new.quest_session_id;
    perform pawquest_private.progress_sync(new.user_id,new.created_at);
    return new;
end $$;

create function public.progress_snapshot(p_month date default null) returns jsonb
language plpgsql security definer set search_path='' as $$
declare actor uuid:=auth.uid(); stamp timestamptz:=clock_timestamp(); facts jsonb; today date;
    month_start date; next_month date; week_start date;
begin
    facts:=pawquest_private.progress_sync(actor,stamp);today:=(facts->>'today')::date;
    if p_month is not null and (not isfinite(p_month) or p_month<'1900-01-01' or p_month>'9999-01-01') then
        raise exception 'Invalid calendar month' using errcode='23514'; end if;
    month_start:=date_trunc('month',coalesce(p_month,today))::date;next_month:=(month_start+interval '1 month')::date;
    week_start:=date_trunc('week',today)::date;
    return (facts-'days') || jsonb_build_object('user_id',actor,'server_now',stamp,'timezone','UTC','month',month_start,
      'days',(select coalesce(jsonb_agg(d order by d->>'day'),'[]'::jsonb) from jsonb_array_elements(facts->'days') d where (d->>'day')::date>=month_start and (d->>'day')::date<next_month),
      'week',(select jsonb_agg(jsonb_build_object('day',week_start+i,'count',coalesce((select (d->>'count')::integer from jsonb_array_elements(facts->'days') d where (d->>'day')::date=week_start+i),0)) order by i) from generate_series(0,6) i),
      'today_count',coalesce((select (d->>'count')::integer from jsonb_array_elements(facts->'days') d where (d->>'day')::date=today),0),
      'rewarded_today',(select count(*) from public.wallet_transactions where user_id=actor and kind='quest_reward' and reward_day=today),
      'coins_today',(select coalesce(sum(coins),0) from public.wallet_transactions where user_id=actor and kind='quest_reward' and reward_day=today),
      'badges',(select jsonb_agg(jsonb_build_object('code',c.code,'name',c.name,'days',c.streak_days,'earned_on',a.earned_on) order by c.streak_days) from public.achievement_catalog c left join public.user_achievements a on a.user_id=actor and a.code=c.code),
      'events',(select coalesce(jsonb_agg(to_jsonb(e)||jsonb_build_object('name',coalesce(c.name,'A surprise for your companion')) order by e.created_at,e.id),'[]'::jsonb) from public.progress_events e left join public.achievement_catalog c on c.code=e.badge_code where e.user_id=actor and e.acknowledged_at is null));
end $$;
create function public.progress_ack(p_event_id uuid) returns void
language plpgsql security definer set search_path='' as $$
begin
    if auth.uid() is null then raise exception 'Authentication required' using errcode='42501'; end if;
    update public.progress_events set acknowledged_at=coalesce(acknowledged_at,clock_timestamp()) where id=p_event_id and user_id=auth.uid();
    if not found then raise exception 'Event unavailable' using errcode='42501'; end if;
end $$;
revoke all on function pawquest_private.progress_new_profile(),pawquest_private.progress_facts(uuid,timestamptz),pawquest_private.progress_sync(uuid,timestamptz) from public,anon,authenticated,service_role;
revoke all on function public.progress_snapshot(date),public.progress_ack(uuid) from public,anon,authenticated,service_role;
grant execute on function public.progress_snapshot(date),public.progress_ack(uuid) to authenticated;
commit;
