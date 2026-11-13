begin;
create function pg_temp.schedule_assert(value boolean,message text) returns void language plpgsql as $$
begin if value is distinct from true then raise exception 'Schedule ASSERT: %',message; end if; end $$;
insert into auth.users(id,email) values
 ('f7100000-0000-0000-0000-000000000001','schedule@example.invalid'),
 ('f7100000-0000-0000-0000-000000000002','other-schedule@example.invalid');
insert into public.quests(id,user_id,title,duration_minutes,repeat_type,due_at,archived) values
 ('f7200000-0000-0000-0000-000000000001','f7100000-0000-0000-0000-000000000001','Daily walk',5,'daily',null,false),
 ('f7200000-0000-0000-0000-000000000002','f7100000-0000-0000-0000-000000000001','Report',5,'one_time','2026-03-10T15:00:00Z',false),
 ('f7200000-0000-0000-0000-000000000003','f7100000-0000-0000-0000-000000000001','Far away',5,'one_time','2026-03-20T15:00:00Z',false),
 ('f7200000-0000-0000-0000-000000000004','f7100000-0000-0000-0000-000000000001','Archived',5,'one_time','2026-03-10T16:00:00Z',true),
 ('f7200000-0000-0000-0000-000000000005','f7100000-0000-0000-0000-000000000001','Done',5,'one_time','2026-03-10T17:00:00Z',false),
 ('f7200000-0000-0000-0000-000000000006','f7100000-0000-0000-0000-000000000002','Other owner',5,'one_time','2026-03-10T15:00:00Z',false);
insert into public.quest_sessions(user_id,quest_id,duration_minutes,started_at,ends_at,status) values
 ('f7100000-0000-0000-0000-000000000001','f7200000-0000-0000-0000-000000000005',5,'2026-03-10T01:00:00Z','2026-03-10T01:05:00Z','completed');

-- 2026-03-10 09:00 in Asia/Bangkok (UTC+7) = 02:00Z; today ends 17:00Z, tomorrow 2026-03-11 17:00Z.
create temp table s as select pawquest_private.reminder_schedule_snapshot('f7100000-0000-0000-0000-000000000001','2026-03-10T02:00:00Z','Asia/Bangkok') as v;
select pg_temp.schedule_assert((select v->>'has_daily_quest' from s)='true','daily flag');
select pg_temp.schedule_assert((select jsonb_array_length(v->'upcoming') from s)=3,'today daily, tomorrow daily and Report only');
select pg_temp.schedule_assert((select count(*) from s,jsonb_array_elements(v->'upcoming') e where e->>'id'='f7200000-0000-0000-0000-000000000001')=2,'daily appears for today and tomorrow');
select pg_temp.schedule_assert((select (v->'upcoming'->0->>'due_at')::timestamptz='2026-03-10T15:00:00Z' from s),'ordered by deadline');
select pg_temp.schedule_assert(not exists(select 1 from s,jsonb_array_elements(v->'upcoming') e where e->>'title' in ('Far away','Archived','Done','Other owner')),'excludes far, archived, completed and foreign quests');

-- Completing today's daily removes only today's entry.
insert into public.quest_sessions(user_id,quest_id,duration_minutes,started_at,ends_at,status) values
 ('f7100000-0000-0000-0000-000000000001','f7200000-0000-0000-0000-000000000001',5,'2026-03-10T01:30:00Z','2026-03-10T01:35:00Z','completed');
select pg_temp.schedule_assert((select count(*) from jsonb_array_elements(pawquest_private.reminder_schedule_snapshot('f7100000-0000-0000-0000-000000000001','2026-03-10T02:00:00Z','Asia/Bangkok')->'upcoming') e
    where e->>'id'='f7200000-0000-0000-0000-000000000001')=1,'completed daily keeps only tomorrow');

select pg_temp.schedule_assert(not has_function_privilege('authenticated','pawquest_private.reminder_schedule_snapshot(uuid,timestamptz,text)','EXECUTE'),'clients cannot choose owner/clock');
select pg_temp.schedule_assert(not has_function_privilege('anon','public.reminder_schedule(text)','EXECUTE'),'anonymous prohibited');
select set_config('request.jwt.claim.sub','f7100000-0000-0000-0000-000000000001',true);
set local role authenticated;
select pg_temp.schedule_assert(public.reminder_schedule('Asia/Bangkok')->>'user_id'=auth.uid()::text,'bound to caller');
do $$begin
    begin perform public.reminder_schedule('not-a-zone'); raise exception 'Invalid zone accepted';
    exception when check_violation then null; end;
end $$;
reset role;
rollback;
