begin;
create function pg_temp.puppy_assert(value boolean,message text) returns void language plpgsql as $$
begin if value is distinct from true then raise exception 'Puppy ASSERT: %',message; end if; end $$;
insert into auth.users(id,email) values
('fc100000-0000-0000-0000-000000000001','puppy@example.invalid'),
('fc100000-0000-0000-0000-000000000002','other-puppy@example.invalid');
select set_config('request.jwt.claim.sub','fc100000-0000-0000-0000-000000000001',true);
select pg_temp.puppy_assert((select unlock_level=5 and renderer_key='puppy' and published from public.pet_species where code='puppy'),'published level 5 Puppy');
do $$begin
    begin perform public.pet_select_species('puppy');raise exception 'Puppy unlocked early';
    exception when insufficient_privilege then null;end;
end $$;
update public.pets set xp=400,hunger=42,hydration=60,energy=70 where user_id=auth.uid();
do $$begin
    begin perform public.pet_select_species('puppy');raise exception 'XP incorrectly grants Puppy';
    exception when insufficient_privilege then null;end;
end $$;
insert into public.user_pet_unlocks(user_id,species_code,source) values(auth.uid(),'puppy','LEGACY');
select public.pet_select_species('puppy');
select pg_temp.puppy_assert((select species_code='puppy' and renderer_key='puppy' and name='Puppy' and hunger=42 and hydration=60 and energy=70 and xp=400 from public.pets where user_id=auth.uid()),'Puppy selection preserves progression/care');
insert into public.quests(id,user_id,title,duration_minutes,repeat_type,created_at) values
('fc200000-0000-0000-0000-000000000001',auth.uid(),'Daily Puppy walk',5,'daily','2026-03-08T12:00:00Z');
-- Spring DST calendar: a daily created yesterday can be missed, today's definition cannot.
select pg_temp.puppy_assert((pawquest_private.companion_snapshot(auth.uid(),'2026-03-09T04:01:00Z','America/New_York')->>'has_missed_quest')::boolean,'missed yesterday at local midnight');
select pg_temp.puppy_assert(not (pawquest_private.companion_snapshot(auth.uid(),'2026-03-08T23:00:00Z','America/New_York')->>'has_missed_quest')::boolean,'new daily is not missed on creation day');
insert into public.quest_sessions(id,user_id,quest_id,duration_minutes,started_at,ends_at,status,cancelled_at) values
('fc300000-0000-0000-0000-000000000001',auth.uid(),'fc200000-0000-0000-0000-000000000001',5,'2026-03-08T12:00:00Z','2026-03-08T12:05:00Z','cancelled','2026-03-08T12:02:00Z');
select pg_temp.puppy_assert((pawquest_private.companion_snapshot(auth.uid(),'2026-03-09T04:01:00Z','America/New_York')->>'has_missed_quest')::boolean,'cancelled is not completed');
insert into public.quest_sessions(id,user_id,quest_id,duration_minutes,started_at,ends_at,status) values
('fc300000-0000-0000-0000-000000000002',auth.uid(),'fc200000-0000-0000-0000-000000000001',5,'2026-03-08T13:00:00Z','2026-03-08T13:05:00Z','completed');
select pg_temp.puppy_assert(not (pawquest_private.companion_snapshot(auth.uid(),'2026-03-09T04:01:00Z','America/New_York')->>'has_missed_quest')::boolean,'completed daily resolves missed state');
select pg_temp.puppy_assert(pawquest_private.companion_snapshot(auth.uid(),'2026-03-09T04:01:00Z','America/New_York')->>'completion_id'='fc300000-0000-0000-0000-000000000002','latest verified daily completion returned');
select pg_temp.puppy_assert(pawquest_private.companion_snapshot(auth.uid(),'2026-03-08T13:04:59Z','America/New_York')->>'completion_id' is null,'no early celebration');
insert into public.quests(id,user_id,title,duration_minutes,repeat_type,due_at) values
('fc200000-0000-0000-0000-000000000002',auth.uid(),'Overdue one time',5,'one_time','2026-03-08T18:00:00Z');
select pg_temp.puppy_assert((pawquest_private.companion_snapshot(auth.uid(),'2026-03-09T04:01:00Z','America/New_York')->>'has_missed_quest')::boolean,'overdue one-time quest is missed');
update public.quests set archived=true where id='fc200000-0000-0000-0000-000000000002';
select pg_temp.puppy_assert(not (pawquest_private.companion_snapshot(auth.uid(),'2026-03-09T04:01:00Z','America/New_York')->>'has_missed_quest')::boolean,'archived overdue quest ignored');
select pg_temp.puppy_assert(not (pawquest_private.companion_snapshot('fc100000-0000-0000-0000-000000000002','2026-03-09T04:01:00Z','America/New_York')->>'has_missed_quest')::boolean,'owner isolation');
set local role authenticated;
select pg_temp.puppy_assert(public.pet_companion_context('Asia/Bangkok')->>'user_id'=auth.uid()::text,'public RPC binds authenticated owner');
do $$begin
    begin perform public.pet_companion_context('invalid-zone');raise exception 'Invalid timezone accepted';
    exception when check_violation then null;end;
end $$;
reset role;
select pg_temp.puppy_assert(not has_function_privilege('authenticated','pawquest_private.companion_snapshot(uuid,timestamptz,text)','execute'),'private owner/clock override forbidden');
select pg_temp.puppy_assert(not has_function_privilege('anon','public.pet_companion_context(text)','execute'),'anonymous RPC denied');
select pg_temp.puppy_assert((select coins=0 from public.wallets where user_id=auth.uid()),'context grants no Coins');
select pg_temp.puppy_assert((select xp=400 from public.pets where user_id=auth.uid()),'context grants no XP');
rollback;
\echo PASS Puppy unlock/select, verified daily completion, missed quest/DST/owner rules and unchanged rewards
