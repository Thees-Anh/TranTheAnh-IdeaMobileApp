begin;
create function pg_temp.progress_assert(value boolean) returns void language plpgsql as $$
begin if value is distinct from true then raise exception 'Daily progress assertion failed'; end if; end $$;
insert into auth.users(id,email) values
('92000000-0000-0000-0000-000000000001','progress@example.invalid'),
('92000000-0000-0000-0000-000000000002','other-progress@example.invalid');
insert into public.quests(id,user_id,title,duration_minutes) values
('93000000-0000-0000-0000-000000000001','92000000-0000-0000-0000-000000000001','Focus',5);
insert into public.quest_sessions(id,user_id,quest_id,duration_minutes,started_at,ends_at,status) values
('94000000-0000-0000-0000-000000000001','92000000-0000-0000-0000-000000000001',
'93000000-0000-0000-0000-000000000001',5,
(date_trunc('day',now() at time zone 'UTC') at time zone 'UTC') - interval '5 minutes',
date_trunc('day',now() at time zone 'UTC') at time zone 'UTC','completed');
set local role authenticated;
select set_config('request.jwt.claim.sub','92000000-0000-0000-0000-000000000001',true);
select pg_temp.progress_assert(public.quest_daily_reward_count()=0);
select public.claim_quest_reward('94000000-0000-0000-0000-000000000001');
set local timezone='Pacific/Honolulu';
select pg_temp.progress_assert(public.quest_daily_reward_count()=1);
set local timezone='Asia/Bangkok';
select pg_temp.progress_assert(public.quest_daily_reward_count()=1);
select set_config('request.jwt.claim.sub','92000000-0000-0000-0000-000000000002',true);
select pg_temp.progress_assert(public.quest_daily_reward_count()=0);
reset role;
select pg_temp.progress_assert(not has_function_privilege('anon','public.quest_daily_reward_count()','execute'));
rollback;
\echo PASS daily progress ownership, server UTC day, claimed rewards only and anonymous denial
