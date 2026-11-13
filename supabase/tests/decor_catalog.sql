begin;
create function pg_temp.decor_assert(value boolean,message text) returns void language plpgsql as $$
begin if value is distinct from true then raise exception 'Decor ASSERT: %',message;end if;end $$;
insert into auth.users(id,email) values('ed230000-0000-0000-0000-000000000001','decor@example.invalid'),
('ed230000-0000-0000-0000-000000000002','decor-other@example.invalid');
select set_config('request.jwt.claim.sub','ed230000-0000-0000-0000-000000000001',true);
update public.wallets set coins=10000 where user_id=auth.uid();
select pg_temp.decor_assert((select count(*)=20 and bool_and(max_quantity=1 and length(description)>0) from public.item_catalog where id::text like 'e2300000-%'),'20 unique new items with descriptions');
select pg_temp.decor_assert((select price_coins=150 from public.item_catalog where asset_id='acc-crown'),'authored crown price');
select pg_temp.decor_assert((select price_coins=200 from public.item_catalog where asset_id='acc-wings'),'authored wings price');
select pg_temp.decor_assert((select price_coins=180 from public.item_catalog where asset_id='furn-lamp'),'authored flower lamp price');
do $$declare item public.item_catalog%rowtype; request uuid; receipt jsonb; replay jsonb; expected integer:=10000; balance integer;
begin
 for item in select * from public.item_catalog where id::text like 'e2300000-%' order by id loop
  request:=gen_random_uuid();receipt:=public.shop_purchase(item.id,1,request);replay:=public.shop_purchase(item.id,1,request);
  if receipt->>'status'<>'purchased' or receipt<>replay then raise exception 'Purchase retry failed: %',item.code;end if;
  expected:=expected-item.price_coins;
  if public.shop_purchase(item.id,1,gen_random_uuid())->>'status'<>'already_owned' then raise exception 'Duplicate ownership accepted';end if;
  if public.shop_purchase(item.id,2,gen_random_uuid())->>'status'<>'invalid_quantity' then raise exception 'Unique quantity accepted';end if;
  if item.category='ACCESSORY' then perform public.equipment_set((select slot from public.accessory_slots where item_id=item.id),item.id);
  else perform public.room_set((select slot from public.furniture_slots where item_id=item.id),item.id);end if;
 end loop;
 select coins into balance from public.wallets where user_id=auth.uid();
 if balance<>expected then raise exception 'Wrong total debit';end if;
end $$;
select pg_temp.decor_assert((select count(*)=20 from public.wallet_transactions where user_id=auth.uid()),'exactly one transaction per purchase');
select pg_temp.decor_assert((select count(*)=4 from public.pet_equipment where user_id=auth.uid()),'one item in each of four slots');
select pg_temp.decor_assert((select count(*)=10 from public.room_furniture where user_id=auth.uid()),'all ten distinct room anchors coexist');
select pg_temp.decor_assert((select xp=0 from public.pets where user_id=auth.uid()),'decor grants no XP');
-- Replacing variants preserves the old purchase and returns idempotent configurations.
select public.equipment_set('HEAD','e2300000-0000-0000-0000-000000000001');
select public.equipment_set('HEAD','e2300000-0000-0000-0000-000000000002');
select pg_temp.decor_assert((select quantity=1 from public.user_inventory where user_id=auth.uid() and item_id='e2300000-0000-0000-0000-000000000001'),'replaced crown remains owned');
select public.equipment_set('BACK',null);
select pg_temp.decor_assert((select count(*)=3 from public.pet_equipment where user_id=auth.uid()),'BACK unequip preserves other slots');
select public.room_set('WINDOW',null);
select pg_temp.decor_assert((select count(*)=9 from public.room_furniture where user_id=auth.uid()),'store one anchor independently');
select set_config('request.jwt.claim.sub','ed230000-0000-0000-0000-000000000002',true);
do $$begin
 begin perform public.equipment_set('BACK','e2300000-0000-0000-0000-000000000009');raise exception 'Unowned wings equipped';
 exception when insufficient_privilege then null;end;
 begin perform public.room_set('WINDOW','e2300000-0000-0000-0000-000000000011');raise exception 'Unowned window placed';
 exception when insufficient_privilege then null;end;
 begin perform public.equipment_set('HEAD','e2300000-0000-0000-0000-000000000009');raise exception 'Wings fit HEAD';
 exception when check_violation then null;end;
end $$;
set local role authenticated;
select pg_temp.decor_assert(jsonb_array_length(public.equipment_state())=0 and jsonb_array_length(public.room_state())=0,'owner-only configurations');
select pg_temp.decor_assert(not has_table_privilege('authenticated','public.item_catalog','update'),'clients cannot edit prices');
reset role;
rollback;
\echo PASS all 20 secure purchases/retries, authored prices, four equipment slots, ten anchors, replacement and ownership
