begin;
create function pg_temp.reminder_assert(value boolean,message text) returns void language plpgsql as $$
begin if value is distinct from true then raise exception 'Reminder ASSERT: %',message; end if; end $$;
insert into auth.users(id,email) values
 ('f5100000-0000-0000-0000-000000000001','reminders@example.invalid'),
 ('f5100000-0000-0000-0000-000000000002','other-reminders@example.invalid');
insert into public.quests(id,user_id,title,duration_minutes,repeat_type,due_at) values
 ('f5200000-0000-0000-0000-000000000001','f5100000-0000-0000-0000-000000000001','Daily',5,'daily',null),
 ('f5200000-0000-0000-0000-000000000002','f5100000-0000-0000-0000-000000000001','One time',5,'one_time','2026-03-09T03:00:00Z'),
 ('f5200000-0000-0000-0000-000000000003','f5100000-0000-0000-0000-000000000002','Other owner',5,'one_time','2026-03-09T03:00:00Z');
select pg_temp.reminder_assert(jsonb_array_length(pawquest_private.reminder_snapshot('f5100000-0000-0000-0000-000000000001','2026-03-09T02:00:00Z','America/New_York')->'pending_quests')=2,'two-hour window and owner isolation');
select pg_temp.reminder_assert(jsonb_array_length(pawquest_private.reminder_snapshot('f5100000-0000-0000-0000-000000000001','2026-03-09T01:59:59Z','America/New_York')->'pending_quests')=1,'daily not prematurely due');
select pg_temp.reminder_assert((select (e->>'due_at')::timestamptz='2026-03-09T04:00:00Z' from jsonb_array_elements(pawquest_private.reminder_snapshot('f5100000-0000-0000-0000-000000000001','2026-03-09T02:00:00Z','America/New_York')->'pending_quests') e where e->>'id'='f5200000-0000-0000-0000-000000000001'),'spring DST day ends at correct UTC hour');
select pg_temp.reminder_assert((select (e->>'due_at')::timestamptz='2026-11-02T05:00:00Z' from jsonb_array_elements(pawquest_private.reminder_snapshot('f5100000-0000-0000-0000-000000000001','2026-11-02T03:00:00Z','America/New_York')->'pending_quests') e where e->>'id'='f5200000-0000-0000-0000-000000000001'),'fall DST day ends at correct UTC hour');
-- Completion, not reward claim time, resolves a daily reminder.
insert into public.quest_sessions(user_id,quest_id,duration_minutes,started_at,ends_at,status,cancelled_at) values
 ('f5100000-0000-0000-0000-000000000001','f5200000-0000-0000-0000-000000000001',5,'2026-03-08T05:25:00Z','2026-03-08T05:30:00Z','completed',null),
 ('f5100000-0000-0000-0000-000000000001','f5200000-0000-0000-0000-000000000002',5,'2026-03-08T05:25:00Z','2026-03-08T05:30:00Z','cancelled','2026-03-08T05:27:00Z');
select pg_temp.reminder_assert(jsonb_array_length(pawquest_private.reminder_snapshot('f5100000-0000-0000-0000-000000000001','2026-03-09T02:00:00Z','America/New_York')->'pending_quests')=1,'completion removes daily, cancellation does not remove one-time');
select pg_temp.reminder_assert(jsonb_array_length(pawquest_private.reminder_snapshot('f5100000-0000-0000-0000-000000000001','2026-03-10T02:00:00Z','America/New_York')->'pending_quests')=2,'daily reappears the next local day');
select pg_temp.reminder_assert(not has_function_privilege('authenticated','pawquest_private.reminder_snapshot(uuid,timestamptz,text)','EXECUTE'),'clients cannot choose reminder clock/owner');
select pg_temp.reminder_assert(not has_function_privilege('anon','public.reminder_context(text)','EXECUTE'),'anonymous context prohibited');
select set_config('request.jwt.claim.sub','f5100000-0000-0000-0000-000000000001',true);
set local role authenticated;
select pg_temp.reminder_assert(public.reminder_context('Asia/Bangkok')->>'user_id'=auth.uid()::text,'public context bound to authenticated owner');
update public.quests set due_at=null where id='f5200000-0000-0000-0000-000000000002';
select pg_temp.reminder_assert((select due_at is null from public.quests where id='f5200000-0000-0000-0000-000000000002'),'deadline can be removed');
do $$begin
    begin update public.quests set due_at='2026-01-01' where id='f5200000-0000-0000-0000-000000000001';raise exception 'Daily must not have absolute deadline';
    exception when check_violation then null;end;
    begin perform public.reminder_context('not-a-timezone');raise exception 'Invalid zone accepted';
    exception when check_violation then null;end;
end $$;
reset role;
select pg_temp.reminder_assert((select coins=0 from public.wallets where user_id=auth.uid()),'reminder reads never grant rewards');
rollback;
