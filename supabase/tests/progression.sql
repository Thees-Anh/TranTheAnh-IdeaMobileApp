begin;
create function pg_temp.progress_assert(value boolean,message text) returns void language plpgsql as $$
begin if value is distinct from true then raise exception 'Progress ASSERT: %',message; end if;end $$;
insert into auth.users(id,email) values
('fd100000-0000-0000-0000-000000000001','progress@example.invalid'),
('fd100000-0000-0000-0000-000000000002','other-progress@example.invalid');
select set_config('request.jwt.claim.sub','fd100000-0000-0000-0000-000000000001',true);
insert into public.quests(id,user_id,title,duration_minutes) values
('fd200000-0000-0000-0000-000000000001',auth.uid(),'Habit',5);
-- One hundred completion days, regardless of when reward is claimed.
insert into public.quest_sessions(user_id,quest_id,duration_minutes,started_at,ends_at,status)
select auth.uid(),'fd200000-0000-0000-0000-000000000001',5,
 '2026-01-01 23:55Z'::timestamptz+i*interval '1 day','2026-01-02 00:00Z'::timestamptz+i*interval '1 day','completed'
from generate_series(0,99) i;
select pg_temp.progress_assert((pawquest_private.progress_facts(auth.uid(),'2026-01-08 23:59:59Z')->>'current_streak')::int=7,'7 UTC days');
select pg_temp.progress_assert((pawquest_private.progress_facts(auth.uid(),'2026-01-08 23:59:59Z')->>'best_streak')::int=7,'future completions excluded');
select pg_temp.progress_assert((pawquest_private.progress_facts(auth.uid(),'2026-01-01 23:59:59Z')->>'current_streak')::int=0,'UTC midnight not early');
select pg_temp.progress_assert((pawquest_private.progress_facts(auth.uid(),'2026-01-02 00:00:00Z')->>'current_streak')::int=1,'UTC midnight begins completion day');
select pg_temp.progress_assert((pawquest_private.progress_facts(auth.uid(),'2026-04-12 00:00Z')->>'current_streak')::int=100,'yesterday preserves streak');
select pg_temp.progress_assert((pawquest_private.progress_facts(auth.uid(),'2026-04-13 00:00Z')->>'current_streak')::int=0,'missing day resets streak');
select pawquest_private.progress_sync(auth.uid(),'2026-04-11 12:00Z');
select pawquest_private.progress_sync(auth.uid(),'2026-04-11 12:00Z');
select pg_temp.progress_assert((select count(*)=3 from public.user_achievements where user_id=auth.uid()),'7/30/100 badges exactly once');
select pg_temp.progress_assert((select earned_on='2026-01-08' from public.user_achievements where user_id=auth.uid() and code='streak_7'),'earned day uses completion day');
select pg_temp.progress_assert((select count(*)=3 from public.progress_events where user_id=auth.uid()),'one event per badge');
select pg_temp.progress_assert((select coins=0 from public.wallets where user_id=auth.uid()),'badges never grant Coins');
select pg_temp.progress_assert((select xp=0 from public.pets where user_id=auth.uid()),'badges never grant XP');
-- A second quest on the same day increments count, not streak length.
insert into public.quest_sessions(user_id,quest_id,duration_minutes,started_at,ends_at,status)
values(auth.uid(),'fd200000-0000-0000-0000-000000000001',5,'2026-01-08 12:00Z','2026-01-08 12:05Z','completed');
select pg_temp.progress_assert((pawquest_private.progress_facts(auth.uid(),'2026-01-08 23:59:59Z')->>'current_streak')::int=7,'same day does not double streak');
-- Claim crossing a level grants food atomically, and duplicate claims cannot refill it.
update public.pets set xp=99 where user_id=auth.uid();
select public.reward_claim((select id from public.quest_sessions where user_id=auth.uid() order by ends_at limit 1));
select pg_temp.progress_assert((select xp=104 from public.pets where user_id=auth.uid()),'real reward updates XP');
select pg_temp.progress_assert((select quantity=4 from public.user_inventory where user_id=auth.uid() and item_id='e1000000-0000-0000-0000-000000000001'),'level gift adds one food');
do $$begin
 begin perform public.reward_claim((select id from public.quest_sessions where user_id=auth.uid() order by ends_at limit 1));raise exception 'Duplicate accepted';
 exception when unique_violation then null;end;
end $$;
select public.progress_snapshot('2026-01-01');
select pg_temp.progress_assert((select quantity=4 from public.user_inventory where user_id=auth.uid() and item_id='e1000000-0000-0000-0000-000000000001'),'retry does not duplicate food');
-- Full inventory must not prevent XP progression or create negative/overflow stock.
update public.user_inventory set quantity=1000000 where user_id=auth.uid() and item_id='e1000000-0000-0000-0000-000000000001';
update public.pets set xp=200 where user_id=auth.uid();
select public.progress_snapshot();
select pg_temp.progress_assert((select food_quantity=0 from public.progress_events where user_id=auth.uid() and source_key='level:3'),'capacity-clamped gift');
select pg_temp.progress_assert((select quantity=1000000 from public.user_inventory where user_id=auth.uid() and item_id='e1000000-0000-0000-0000-000000000001'),'stock cap preserved');
set local role authenticated;
do $$begin
 begin perform public.progress_snapshot('infinity');raise exception 'Infinite month accepted';
 exception when check_violation then null;end;
end $$;
select pg_temp.progress_assert(public.progress_snapshot('2026-01-01')->>'timezone'='UTC','explicit timezone');
select pg_temp.progress_assert(jsonb_array_length(public.progress_snapshot('2026-01-01')->'days')=30,'month filtering');
select public.progress_ack((select id from public.progress_events where user_id=auth.uid() limit 1));
select public.progress_ack((select id from public.progress_events where user_id=auth.uid() and acknowledged_at is not null limit 1));
select set_config('request.jwt.claim.sub','fd100000-0000-0000-0000-000000000002',true);
select pg_temp.progress_assert((select count(*)=0 from public.progress_events),'events owner RLS');
select pg_temp.progress_assert((select count(*)=0 from public.user_achievements),'badges owner RLS');
select pg_temp.progress_assert(public.progress_snapshot()->>'current_streak'='0','other owner has no streak');
reset role;
do $$begin
 begin perform public.progress_ack((select id from public.progress_events where user_id='fd100000-0000-0000-0000-000000000001' limit 1));raise exception 'Other owner acknowledgement accepted';
 exception when insufficient_privilege then null;end;
end $$;
select pg_temp.progress_assert(not has_table_privilege('authenticated','public.user_achievements','insert'),'no client badge minting');
select pg_temp.progress_assert(not has_table_privilege('authenticated','public.progress_events','update'),'no client gift editing');
select pg_temp.progress_assert(not has_function_privilege('authenticated','pawquest_private.progress_sync(uuid,timestamptz)','execute'),'no client clock or owner overrides');
select pg_temp.progress_assert(not has_function_privilege('anon','public.progress_snapshot(date)','execute'),'anonymous denied');
rollback;
\echo PASS UTC streak/calendar, 7/30/100 badges, atomic level food, duplicate claims, inventory cap, acknowledgement and owner RLS
