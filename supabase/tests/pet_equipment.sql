begin;
create function pg_temp.equipment_assert(ok boolean,message text) returns void language plpgsql as $$
begin if ok is distinct from true then raise exception 'Equipment ASSERT: %',message; end if; end $$;
create function pg_temp.equipment_denied(statement text) returns void language plpgsql as $$
begin
    begin execute statement;
    exception when check_violation or foreign_key_violation or restrict_violation or insufficient_privilege then return; end;
    raise exception 'Expected equipment denial: %',statement;
end $$;
insert into auth.users(id,email) values
 ('aa100000-0000-0000-0000-000000000001','equipment-a@example.invalid'),
 ('aa100000-0000-0000-0000-000000000002','equipment-b@example.invalid');
select set_config('request.jwt.claim.sub','aa100000-0000-0000-0000-000000000001',true);
select pg_temp.equipment_assert(jsonb_array_length(public.equipment_state())=0,'initial empty');
select pg_temp.equipment_denied($q$select public.equipment_set('HEAD','e1000000-0000-0000-0000-000000000002')$q$);
select pawquest_private.adjust_inventory(auth.uid(),'e1000000-0000-0000-0000-000000000002',1);
select pawquest_private.adjust_inventory(auth.uid(),'e1000000-0000-0000-0000-000000000003',1);
select pawquest_private.adjust_inventory(auth.uid(),'e1000000-0000-0000-0000-000000000004',1);
select pg_temp.equipment_denied($q$select public.equipment_set('FACE','e1000000-0000-0000-0000-000000000002')$q$);
select pg_temp.equipment_denied($q$select public.equipment_set('HEAD','e1000000-0000-0000-0000-000000000001')$q$);
select pg_temp.equipment_denied($q$select public.equipment_set('OTHER',null)$q$);
select pg_temp.equipment_denied($q$select public.equipment_set(null,null)$q$);
select public.equipment_set('HEAD','e1000000-0000-0000-0000-000000000002');
select public.equipment_set('HEAD','e1000000-0000-0000-0000-000000000002');
select public.equipment_set('FACE','e1000000-0000-0000-0000-000000000003');
select public.equipment_set('NECK','e1000000-0000-0000-0000-000000000004');
select pg_temp.equipment_assert(jsonb_array_length(public.equipment_state())=3,'three restored slots');
select pg_temp.equipment_assert((select count(*)=3 from public.pet_equipment),'retries no duplicate');
select pg_temp.equipment_assert((select coins=0 from public.wallets where user_id=auth.uid()),'no equip charge');
select pg_temp.equipment_assert((select sum(quantity)=3 from public.user_inventory where user_id=auth.uid() and max_quantity=1),'no consumption');
select pg_temp.equipment_denied($q$delete from public.user_inventory where user_id=auth.uid() and item_id='e1000000-0000-0000-0000-000000000002'$q$);
select pg_temp.equipment_denied($q$update public.item_catalog set category='FURNITURE' where code='hat'$q$);
set local role authenticated;
select pg_temp.equipment_assert((select count(*)=3 from public.pet_equipment),'owner RLS');
select pg_temp.equipment_denied('delete from public.pet_equipment');
select public.equipment_set('HEAD',null);
select public.equipment_set('HEAD',null);
select pg_temp.equipment_assert(jsonb_array_length(public.equipment_state())=2,'unequip idempotent');
select set_config('request.jwt.claim.sub','aa100000-0000-0000-0000-000000000002',true);
select pg_temp.equipment_assert(jsonb_array_length(public.equipment_state())=0,'second user isolated');
select pg_temp.equipment_assert(not exists(select 1 from public.pet_equipment),'table RLS');
select pg_temp.equipment_denied($q$select public.equipment_set('FACE','e1000000-0000-0000-0000-000000000003')$q$);
reset role;
set local role anon;
select pg_temp.equipment_denied('select public.equipment_state()');
select pg_temp.equipment_denied($q$select public.equipment_set('HEAD',null)$q$);
reset role;
rollback;
\echo PASS owned equipment, slot compatibility, restore, retries, no debit/consumption and RLS
