begin;
create function pg_temp.companion_assert(value boolean,message text) returns void language plpgsql as $$
begin if value is distinct from true then raise exception 'Companion ASSERT: %',message; end if; end $$;
create function pg_temp.companion_error(statement text,expected text) returns void language plpgsql as $$
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
('fa100000-0000-0000-0000-000000000001','collection@example.invalid'),
('fa100000-0000-0000-0000-000000000002','other-collection@example.invalid');
-- Test-only metadata, never shipped fictional pet artwork.
insert into public.pet_species(code,name,unlock_level,renderer_key,published) values
('fixture_lv5','Fixture Five',5,'prototype_cat',true),
('fixture_lv10','Fixture Ten',10,'not_installed',true),
('fixture_hidden','Unreleased',15,'not_installed',false);
select set_config('request.jwt.claim.sub','fa100000-0000-0000-0000-000000000001',true);
select pg_temp.companion_assert(public.pet_collection()->>'active_code'='mochi_cat','starter migrated');
select pg_temp.companion_assert(jsonb_array_length(public.pet_collection()->'species')=6,'four shipped species plus two published fixtures; unpublished hidden');
select pg_temp.companion_error($q$select public.pet_select_species('fixture_lv5')$q$,'42501');
update public.pets set xp=399,hunger=40,happiness=50,hydration=60,energy=20 where user_id=auth.uid();
select pg_temp.companion_error($q$select public.pet_select_species('fixture_lv5')$q$,'42501');
update public.pets set xp=400 where user_id=auth.uid();
select pg_temp.companion_error($q$select public.pet_select_species('fixture_lv5')$q$,'42501');
-- Test-only server entitlement. XP no longer authorizes appearance selection.
insert into public.user_pet_unlocks(user_id,species_code,source) values(auth.uid(),'fixture_lv5','LEGACY');
set local role authenticated;
select pg_temp.companion_assert(public.pet_select_species('fixture_lv5')->'pet'->>'species_code'='fixture_lv5','owned fixture selectable');
select public.pet_select_species('fixture_lv5');
select pg_temp.companion_assert((select xp=400 and hunger=40 and happiness=50 and hydration=60 and energy=20 from public.pets),'selection/retry preserve stats and XP');
select pg_temp.companion_assert((select coins=0 from public.wallets),'selection grants no coins');
select pg_temp.companion_error($q$select public.pet_select_species('fixture_hidden')$q$,'23514');
select pg_temp.companion_error($q$select public.pet_select_species('missing')$q$,'23514');
select pg_temp.companion_error($q$update public.pets set species_code='mochi_cat'$q$,'42501');
select pg_temp.companion_error($q$update public.pet_species set unlock_level=1$q$,'42501');
select pg_temp.companion_assert((select count(*)=6 from public.pet_species),'published catalog RLS');
select public.pet_state('sleep','fa200000-0000-0000-0000-000000000001');
reset role;
update public.pets set sleep_started_at=clock_timestamp()-interval '5 minutes',sleep_until=clock_timestamp()+interval '35 minutes' where user_id=auth.uid();
set local role authenticated;
select pg_temp.companion_assert(public.pet_state('touch','fa200000-0000-0000-0000-000000000002')->'care_result'->>'code'='woken','touch wakes sleeping pet');
select pg_temp.companion_assert((select sleep_until is null and sleep_started_at is null and sleep_start_energy is null and energy=30 from public.pets),'wake saves partial energy');
select public.pet_state('touch','fa200000-0000-0000-0000-000000000002');
select pg_temp.companion_assert((select energy=30 from public.pets),'wake retry does not grant recovery twice');
select public.pet_state('sleep','fa200000-0000-0000-0000-000000000003');
select public.pet_state('touch','fa200000-0000-0000-0000-000000000002');
select pg_temp.companion_assert((select sleep_until is not null from public.pets),'old wake replay cannot cancel a new sleep');
select public.pet_state('touch','fa200000-0000-0000-0000-000000000004');
select pg_temp.companion_assert(public.pet_state('touch','fa200000-0000-0000-0000-000000000005')->'care_result'->>'code'='touched','awake touch still reacts');
select set_config('request.jwt.claim.sub','fa100000-0000-0000-0000-000000000002',true);
select pg_temp.companion_assert(public.pet_collection()->>'active_code'='mochi_cat','selection owner isolation');
select pg_temp.companion_assert((public.pet_collection()->>'level')::integer=1,'XP owner isolation');
select set_config('request.jwt.claim.sub','',true);
select pg_temp.companion_error('select public.pet_collection()','42501');
select pg_temp.companion_error($q$select public.pet_select_species('mochi_cat')$q$,'42501');
rollback;
\echo PASS wake partial recovery/replay, pet unlock boundary, selection persistence, RLS and unchanged economy
