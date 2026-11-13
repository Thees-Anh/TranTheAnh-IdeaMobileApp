begin;
create function pg_temp.room_assert(ok boolean,message text) returns void language plpgsql as $$
begin if ok is distinct from true then raise exception 'Room ASSERT: %',message; end if; end $$;
create function pg_temp.room_denied(statement text) returns void language plpgsql as $$
begin
    begin execute statement;
    exception when check_violation or foreign_key_violation or restrict_violation or insufficient_privilege then return; end;
    raise exception 'Expected room denial: %',statement;
end $$;
insert into auth.users(id,email) values
 ('aa100000-0000-0000-0000-000000000001','room-a@example.invalid'),
 ('aa100000-0000-0000-0000-000000000005','room-b@example.invalid');
select set_config('request.jwt.claim.sub','aa100000-0000-0000-0000-000000000001',true);
select pg_temp.room_assert(jsonb_array_length(public.room_state())=0,'initial empty');
select pg_temp.room_denied($q$select public.room_set('BED','e1000000-0000-0000-0000-000000000005')$q$);
select pawquest_private.adjust_inventory(auth.uid(),'e1000000-0000-0000-0000-000000000005',1);
select pawquest_private.adjust_inventory(auth.uid(),'e1000000-0000-0000-0000-000000000006',1);
select pawquest_private.adjust_inventory(auth.uid(),'e1000000-0000-0000-0000-000000000007',1);
select pg_temp.room_denied($q$select public.room_set('LAMP','e1000000-0000-0000-0000-000000000005')$q$);
select pg_temp.room_denied($q$select public.room_set('BED','e1000000-0000-0000-0000-000000000001')$q$);
select pg_temp.room_denied($q$select public.room_set('OTHER',null)$q$);
select pg_temp.room_denied($q$select public.room_set(null,null)$q$);
select public.room_set('BED','e1000000-0000-0000-0000-000000000005');
select public.room_set('BED','e1000000-0000-0000-0000-000000000005');
select public.room_set('LAMP','e1000000-0000-0000-0000-000000000006');
select public.room_set('CARPET','e1000000-0000-0000-0000-000000000007');
select pg_temp.room_assert(jsonb_array_length(public.room_state())=3,'three restored slots');
select pg_temp.room_assert((select count(*)=3 from public.room_furniture),'retries no duplicate');
select pg_temp.room_assert((select coins=0 from public.wallets where user_id=auth.uid()),'no equip charge');
select pg_temp.room_assert((select sum(quantity)=3 from public.user_inventory where user_id=auth.uid() and max_quantity=1),'no consumption');
-- A second owned catalog variant replaces the same slot, rather than stacking placements.
insert into public.item_catalog(id,code,name,category,price_coins,asset_id,max_quantity)
    values('ab100000-0000-0000-0000-000000000001','bed_variant','Test bed variant','FURNITURE',120,'fixture.bed.variant',1);
insert into public.furniture_slots values('ab100000-0000-0000-0000-000000000001','BED');
select pawquest_private.adjust_inventory(auth.uid(),'ab100000-0000-0000-0000-000000000001',1);
select public.room_set('BED','ab100000-0000-0000-0000-000000000001');
select pg_temp.room_assert((select count(*)=1 and min(item_id::text)='ab100000-0000-0000-0000-000000000001'
    from public.room_furniture where user_id=auth.uid() and slot='BED'),'replace one slot');
select public.room_set('BED','e1000000-0000-0000-0000-000000000005');
select pg_temp.room_denied($q$delete from public.user_inventory where user_id=auth.uid() and item_id='e1000000-0000-0000-0000-000000000005'$q$);
select pg_temp.room_denied($q$update public.item_catalog set category='ACCESSORY' where code='bed'$q$);
set local role authenticated;
select pg_temp.room_assert((select count(*)=3 from public.room_furniture),'owner RLS');
select pg_temp.room_denied('delete from public.room_furniture');
select public.room_set('BED',null);
select public.room_set('BED',null);
select pg_temp.room_assert(jsonb_array_length(public.room_state())=2,'unequip idempotent');
select set_config('request.jwt.claim.sub','aa100000-0000-0000-0000-000000000005',true);
select pg_temp.room_assert(jsonb_array_length(public.room_state())=0,'second user isolated');
select pg_temp.room_assert(not exists(select 1 from public.room_furniture),'table RLS');
select pg_temp.room_denied($q$select public.room_set('LAMP','e1000000-0000-0000-0000-000000000006')$q$);
reset role;
set local role anon;
select pg_temp.room_denied('select public.room_state()');
select pg_temp.room_denied($q$select public.room_set('BED',null)$q$);
reset role;
rollback;
\echo PASS owned room, slot compatibility, restore, retries, no debit/consumption and RLS
