begin;
create function pg_temp.care_assert(value boolean,message text) returns void language plpgsql as $$
begin if value is distinct from true then raise exception 'Care ASSERT: %',message; end if; end $$;
insert into auth.users(id,email) values('d1000000-0000-0000-0000-000000000001','care@example.invalid');
insert into auth.users(id,email) values('d1000000-0000-0000-0000-000000000002','other-care@example.invalid');
select pg_temp.care_assert((select quantity=3 from public.pet_food_inventory where user_id='d1000000-0000-0000-0000-000000000001'),'one starter allocation');
select set_config('request.jwt.claim.sub','d1000000-0000-0000-0000-000000000001',true);
select pg_temp.care_assert(public.pet_state('feed','d2000000-0000-0000-0000-000000000001')->'care_result'->>'code'='already_full','full pet rejected');
update public.pets set hunger=20,happiness=90,energy=4 where user_id=auth.uid();
select pg_temp.care_assert(public.pet_state('play','d2000000-0000-0000-0000-000000000002')->'care_result'->>'code'='not_enough_energy','energy gate');
select pg_temp.care_assert((select quantity=3 from public.pet_food_inventory where user_id=auth.uid()),'invalid actions consume nothing');
select public.pet_state('feed','d2000000-0000-0000-0000-000000000003');
select public.pet_state('feed','d2000000-0000-0000-0000-000000000003');
select pg_temp.care_assert((select quantity=2 from public.pet_food_inventory where user_id=auth.uid()),'one food consumed');
select pg_temp.care_assert((select hunger=35 from public.pets where user_id=auth.uid()),'one feed effect');
update public.pet_food_inventory set quantity=0 where user_id=auth.uid();
select pg_temp.care_assert(public.pet_state('feed','d2000000-0000-0000-0000-000000000004')->'care_result'->>'code'='no_food','no negative inventory');
update public.pet_food_inventory set quantity=1 where user_id=auth.uid();
select pg_temp.care_assert(public.pet_state('feed','d2000000-0000-0000-0000-000000000004')->'care_result'->>'code'='no_food','rejected identity remains rejected');
select pg_temp.care_assert((select quantity=1 from public.pet_food_inventory where user_id=auth.uid()),'replay cannot spend newly stocked food');
update public.pets set energy=5 where user_id=auth.uid();
select public.pet_state('play','d2000000-0000-0000-0000-000000000005');
select pg_temp.care_assert((select energy=0 and happiness=100 from public.pets where user_id=auth.uid()),'valid play exact cost and cap');
select public.pet_state('sleep','d2000000-0000-0000-0000-000000000006');
select pg_temp.care_assert((select energy=0 and sleep_until>clock_timestamp() from public.pets where user_id=auth.uid()),'sleep not instant');
select pg_temp.care_assert(public.pet_state('sleep','d2000000-0000-0000-0000-000000000007')->'care_result'->>'code'='already_sleeping','sleep cannot extend itself');
select pg_temp.care_assert(public.pet_state('feed','d2000000-0000-0000-0000-000000000008')->'care_result'->>'code'='pet_sleeping','sleep blocks care');
update public.pets set sleep_started_at=now()-interval '5 minutes',sleep_until=now()+interval '45 minutes' where user_id=auth.uid();
select public.pet_state();
select pg_temp.care_assert((select energy=10 from public.pets where user_id=auth.uid()),'elapsed sleep restore');
select public.pet_state();
select pg_temp.care_assert((select energy=10 from public.pets where user_id=auth.uid()),'polling cannot accelerate sleep');
update public.pets set sleep_started_at=now()-interval '60 minutes',sleep_until=now()-interval '10 minutes' where user_id=auth.uid();
select public.pet_state();
select pg_temp.care_assert((select energy=100 and sleep_until is null from public.pets where user_id=auth.uid()),'sleep completes without a background task');
update public.pets set happiness=20 where user_id=auth.uid();
select pg_temp.care_assert(public.pet_state('touch','d2000000-0000-0000-0000-000000000009')->'care_result'->>'code'='touched','touch response');
select pg_temp.care_assert((select happiness=20 and xp=0 from public.pets where user_id=auth.uid()),'touch temporary only');
update public.pets set sleep_started_at=now()-interval '3 hours 50 minutes',sleep_until=now()-interval '3 hours',
    sleep_start_energy=0 where user_id=auth.uid();
select public.pet_state();
select pg_temp.care_assert((select energy=97 and sleep_until is null from public.pets where user_id=auth.uid()),'post-sleep inactivity resumes');
select public.pet_state();
select pg_temp.care_assert((select energy=97 from public.pets where user_id=auth.uid()),'restored energy persists without double decay');
-- Failure after food debit must roll back debit, stat change and idempotency record.
update public.pets set hunger=0 where user_id=auth.uid();
create function pg_temp.fail_care() returns trigger language plpgsql as $$
begin if new.hunger>old.hunger then raise exception 'Injected care failure'; end if; return new; end $$;
create trigger test_fail_care before update on public.pets for each row execute function pg_temp.fail_care();
do $$ begin
    begin perform public.pet_state('feed','d2000000-0000-0000-0000-000000000010');
        raise exception 'Expected failure';
    exception when raise_exception then if sqlerrm<>'Injected care failure' then raise; end if; end;
end $$;
select pg_temp.care_assert((select quantity=1 from public.pet_food_inventory where user_id=auth.uid()),'atomic food rollback');
select pg_temp.care_assert((select hunger=0 from public.pets where user_id=auth.uid()),'atomic stat rollback');
select pg_temp.care_assert(not exists(select 1 from pawquest_private.pet_care_requests where request_id='d2000000-0000-0000-0000-000000000010'),'atomic receipt rollback');
select pg_temp.care_assert(not has_table_privilege('authenticated','public.pet_food_inventory','update'),'no client inventory writes');
set local role authenticated;
select pg_temp.care_assert((select count(*)=1 from public.pet_food_inventory),'inventory owner RLS');
reset role;
rollback;
\echo PASS atomic food, invalid actions, replay, timed sleep, temporary touch and injected rollback
