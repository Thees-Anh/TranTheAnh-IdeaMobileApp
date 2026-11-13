begin;
create function pg_temp.widget_assert(value boolean,message text) returns void language plpgsql as $$
begin if value is distinct from true then raise exception 'Widget ASSERT: %',message;end if;end $$;
insert into auth.users(id,email) values
('fe100000-0000-0000-0000-000000000001','widget@example.invalid'),
('fe100000-0000-0000-0000-000000000002','widget-other@example.invalid');
select set_config('request.jwt.claim.sub','fe100000-0000-0000-0000-000000000001',true);
insert into public.quests(id,user_id,title,duration_minutes,repeat_type,due_at,archived) values
('fe200000-0000-0000-0000-000000000001',auth.uid(),'Daily',5,'daily',null,false),
('fe200000-0000-0000-0000-000000000002',auth.uid(),'Undated',5,'one_time',null,false),
('fe200000-0000-0000-0000-000000000003',auth.uid(),'Overdue',5,'one_time','2026-11-08 00:00Z',false),
('fe200000-0000-0000-0000-000000000004',auth.uid(),'Today',5,'one_time','2026-11-09 16:59:59Z',false),
('fe200000-0000-0000-0000-000000000005',auth.uid(),'Tomorrow',5,'one_time','2026-11-09 17:00Z',false),
('fe200000-0000-0000-0000-000000000006',auth.uid(),'Archived',5,'daily',null,true);
select pg_temp.widget_assert((pawquest_private.widget_context(auth.uid(),'2026-11-09 01:00Z','Asia/Bangkok')->>'pending_count')::int=4,'all local-day pending, not only near deadline');
insert into public.quest_sessions(user_id,quest_id,duration_minutes,started_at,ends_at,status) values
(auth.uid(),'fe200000-0000-0000-0000-000000000001',5,'2026-11-09 00:00Z','2026-11-09 00:05Z','completed'),
(auth.uid(),'fe200000-0000-0000-0000-000000000002',5,'2026-11-08 00:00Z','2026-11-08 00:05Z','completed');
select pg_temp.widget_assert((pawquest_private.widget_context(auth.uid(),'2026-11-09 01:00Z','Asia/Bangkok')->>'pending_count')::int=2,'completed unclaimed quests excluded');
select pg_temp.widget_assert((pawquest_private.widget_context(auth.uid(),'2026-11-09 17:00Z','Asia/Bangkok')->>'pending_count')::int=4,'daily resets at local midnight and next deadline enters day');
select pg_temp.widget_assert((pawquest_private.widget_context('fe100000-0000-0000-0000-000000000002','2026-11-09 01:00Z','Asia/Bangkok')->>'pending_count')::int=0,'owner isolated');
insert into public.quest_sessions(user_id,quest_id,duration_minutes,started_at,ends_at,status)
values(auth.uid(),'fe200000-0000-0000-0000-000000000001',5,'2026-11-01 04:00Z','2026-11-01 04:05Z','completed');
select pg_temp.widget_assert((pawquest_private.widget_context(auth.uid(),'2026-11-02 04:59:59Z','America/New_York')->>'pending_count')::int=1,'25-hour DST day retains completion');
select pg_temp.widget_assert((pawquest_private.widget_context(auth.uid(),'2026-11-02 05:00Z','America/New_York')->>'pending_count')::int=2,'DST midnight resets daily exactly once');
do $$begin
 begin perform public.widget_context('bad-timezone');raise exception 'invalid timezone accepted';
 exception when check_violation then null;end;
end $$;
set local role authenticated;
select pg_temp.widget_assert(public.widget_context('Asia/Bangkok')->>'user_id'=auth.uid()::text,'public function binds owner');
reset role;
select pg_temp.widget_assert(not has_function_privilege('authenticated','pawquest_private.widget_context(uuid,timestamptz,text)','execute'),'no client clock override');
select pg_temp.widget_assert(not has_function_privilege('anon','public.widget_context(text)','execute'),'anonymous denied');
select pg_temp.widget_assert((select coins=0 from public.wallets where user_id=auth.uid()),'read never grants Coins');
rollback;
\echo PASS widget local-day pending counts, unclaimed completions, midnight, owner isolation and permissions
