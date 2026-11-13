begin;
create function pg_temp.gate_assert(value boolean,message text) returns void language plpgsql as $$
begin if value is distinct from true then raise exception 'Gacha gate ASSERT: %',message; end if; end $$;
update public.item_case_banners set enabled=true;
update public.lucky_wheel_banners set enabled=true;
insert into auth.users(id,email) values ('fc900000-0000-0000-0000-000000000001','gate@example.invalid');
update public.wallets set coins=1000 where user_id='fc900000-0000-0000-0000-000000000001';
create function pg_temp.set_pet(h integer,w integer) returns void language sql as $$
    update public.pets set hunger=h,hydration=w,stats_updated_at=clock_timestamp(),hydration_updated_at=clock_timestamp(),
        sleep_until=null,sleep_started_at=null,sleep_start_energy=null
    where user_id='fc900000-0000-0000-0000-000000000001';
$$;
select pg_temp.gate_assert(pawquest_private.gacha_care_threshold()=25,'threshold matches the hungry/thirsty line');
select pg_temp.gate_assert(not has_function_privilege('authenticated','pawquest_private.gacha_care_block(uuid)','EXECUTE'),'gate helper is private');

select set_config('request.jwt.claim.sub','fc900000-0000-0000-0000-000000000001',true);
select pg_temp.set_pet(20,90);
set local role authenticated;
select pg_temp.gate_assert(public.item_case_open('e2400000-0000-0000-0000-000000000001','fc910000-0000-0000-0000-000000000001')
    =jsonb_build_object('status','pet_needs_care','receipt',null,'need','hungry'),'hungry pet blocks Mystery Box');
select pg_temp.gate_assert(public.lucky_wheel_spin('e2800000-0000-0000-0000-000000000001','COINS','fc910000-0000-0000-0000-000000000002')
    =jsonb_build_object('status','pet_needs_care','need','hungry'),'hungry pet blocks Lucky Wheel');
reset role;
select pg_temp.gate_assert((select coins=1000 from public.wallets where user_id='fc900000-0000-0000-0000-000000000001'),'nothing charged');
select pg_temp.gate_assert(not exists(select 1 from public.item_case_openings where user_id='fc900000-0000-0000-0000-000000000001')
    and not exists(select 1 from public.lucky_wheel_spins where user_id='fc900000-0000-0000-0000-000000000001'),'nothing drawn');

select pg_temp.set_pet(90,25);
set local role authenticated;
select pg_temp.gate_assert(public.lucky_wheel_spin('e2800000-0000-0000-0000-000000000001','COINS','fc910000-0000-0000-0000-000000000003')->>'need'='thirsty','Water 25 is thirsty');
reset role;
select pg_temp.set_pet(10,10);
set local role authenticated;
select pg_temp.gate_assert(public.item_case_open('e2400000-0000-0000-0000-000000000001','fc910000-0000-0000-0000-000000000004')->>'need'='hungry_thirsty','both needs reported');
reset role;

-- Cared-for pet: the draw proceeds; a later hungry replay of the same request still returns its receipt.
select pg_temp.set_pet(26,26);
set local role authenticated;
create temp table first_open as select public.item_case_open('e2400000-0000-0000-0000-000000000001','fc910000-0000-0000-0000-000000000005') as v;
select pg_temp.gate_assert((select v->>'status'<>'pet_needs_care' and v->'receipt' is not null and v->'receipt'<>'null'::jsonb from first_open),'26 is not hungry: case opens');
create temp table first_spin as select public.lucky_wheel_spin('e2800000-0000-0000-0000-000000000001','COINS','fc910000-0000-0000-0000-000000000006') as v;
select pg_temp.gate_assert((select v->>'status'='spun' from first_spin),'fed pet can spin');
reset role;
select pg_temp.set_pet(5,5);
set local role authenticated;
select pg_temp.gate_assert((select public.item_case_open('e2400000-0000-0000-0000-000000000001','fc910000-0000-0000-0000-000000000005')=v from first_open),'committed case replay ignores later hunger');
select pg_temp.gate_assert((select public.lucky_wheel_spin('e2800000-0000-0000-0000-000000000001','COINS','fc910000-0000-0000-0000-000000000006')=v from first_spin),'committed spin replay ignores later hunger');
reset role;
-- Time-based decay counts: a pet fed to 100 long ago is judged on its current Hunger.
update public.pets set hunger=100,hydration=100,stats_updated_at=clock_timestamp()-interval '100 hours',hydration_updated_at=clock_timestamp()
    where user_id='fc900000-0000-0000-0000-000000000001';
set local role authenticated;
select pg_temp.gate_assert(public.lucky_wheel_spin('e2800000-0000-0000-0000-000000000001','COINS','fc910000-0000-0000-0000-000000000007')->>'need'='hungry','decay applied before judging');
reset role;
rollback;
