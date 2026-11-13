begin;
create function pg_temp.pet_assert(value boolean,message text) returns void language plpgsql as $$
begin if value is distinct from true then raise exception 'Pet ASSERT: %',message; end if; end $$;
create function pg_temp.pet_error(statement text,expected text) returns void language plpgsql as $$
declare actual text;
begin
    begin execute statement; exception when others then
        get stacked diagnostics actual=returned_sqlstate;
        if actual=expected then return; end if;
        raise exception 'Expected %, got %',expected,actual;
    end;
    raise exception 'Expected rejection %',expected;
end $$;
insert into auth.users(id,email) values
('c1000000-0000-0000-0000-000000000001','pet-state@example.invalid'),
('c1000000-0000-0000-0000-000000000002','other-pet@example.invalid');
update public.pets set energy_updated_at=now()-interval '2 hours 30 minutes',stats_updated_at=now()-interval '2 hours 30 minutes' where user_id='c1000000-0000-0000-0000-000000000001';
set local role authenticated;
select set_config('request.jwt.claim.sub','c1000000-0000-0000-0000-000000000001',true);
select public.pet_state();
select pg_temp.pet_assert((select hunger=96 and happiness=98 and energy=98 from public.pets),'whole-hour decay');
select pg_temp.pet_assert((select stats_updated_at=now()-interval '30 minutes' from public.pets),'fractional hour preserved');
select public.pet_state();
select pg_temp.pet_assert((select hunger=96 from public.pets),'repeated restore no double decay');
select public.pet_state('play','c2000000-0000-0000-0000-000000000001');
select public.pet_state('play','c2000000-0000-0000-0000-000000000001');
select pg_temp.pet_assert((select happiness=100 and energy=93 from public.pets),'care clamp and replay once');
select pg_temp.pet_error($q$select public.pet_state('feed','c2000000-0000-0000-0000-000000000001')$q$,'23514');
select pg_temp.pet_error($q$select public.pet_state('invalid','c2000000-0000-0000-0000-000000000002')$q$,'23514');
select pg_temp.pet_error('update public.pets set xp=100','42501');
select pg_temp.pet_error('update public.pets set hunger=100','42501');
select pg_temp.pet_assert((select xp=0 from public.pets),'care grants no XP');
select pg_temp.pet_assert((select coins=0 from public.wallets),'care grants no Coins');
select set_config('request.jwt.claim.sub','c1000000-0000-0000-0000-000000000002',true);
select pg_temp.pet_assert(public.pet_state()->'pet'->>'user_id'='c1000000-0000-0000-0000-000000000002','owner isolation');
reset role;
update public.pets set energy_updated_at='1900-01-01Z',stats_updated_at='1900-01-01Z' where user_id='c1000000-0000-0000-0000-000000000001';
set local role authenticated;
select set_config('request.jwt.claim.sub','c1000000-0000-0000-0000-000000000001',true);
select public.pet_state();
select pg_temp.pet_assert((select hunger=0 and happiness=0 and energy=0 from public.pets),'long inactivity clamps');
select pg_temp.pet_assert((select count(*)=1 from public.pets),'pet survives');
select public.pet_state('feed','c2000000-0000-0000-0000-000000000003');
select public.pet_state('sleep','c2000000-0000-0000-0000-000000000004');
select public.pet_state('touch','c2000000-0000-0000-0000-000000000005');
select pg_temp.pet_assert((select hunger=15 and happiness=0 and energy=0 and sleep_until is null from public.pets),'touch wakes after inactivity without granting instant recovery');
reset role;
insert into public.quests(id,user_id,title,duration_minutes) values
('c3000000-0000-0000-0000-000000000001','c1000000-0000-0000-0000-000000000001','XP progression',75);
insert into public.quest_sessions(id,user_id,quest_id,duration_minutes,started_at,ends_at,status)
select ('c4000000-0000-0000-0000-'||lpad(i::text,12,'0'))::uuid,'c1000000-0000-0000-0000-000000000001',
    'c3000000-0000-0000-0000-000000000001',75,'2026-01-01 10:00Z','2026-01-01 11:15Z','completed' from generate_series(1,2) i;
set local role authenticated;
select public.reward_claim(('c4000000-0000-0000-0000-'||lpad(i::text,12,'0'))::uuid) from generate_series(1,2) i;
select pg_temp.pet_assert((public.pet_state()->'pet'->>'xp')::bigint=100,'reward XP persists through pet restore');
select pg_temp.pet_assert((select hunger=15 and happiness=0 and energy=0 and sleep_until is null from public.pets),'XP claim preserves care stats');
reset role;
select pg_temp.pet_assert(not has_function_privilege('anon','public.pet_state(text,uuid)','execute'),'anonymous denied');
rollback;
\echo PASS Pet persisted decay, fractional hours, bounded care, request replay, ownership, XP protection and survival
