begin;
create function pg_temp.inventory_assert(ok boolean,message text) returns void language plpgsql as $$
begin if ok is distinct from true then raise exception 'Inventory ASSERT: %',message; end if; end $$;
create function pg_temp.inventory_rejected(statement text) returns void language plpgsql as $$
begin
    begin execute statement;
    exception when check_violation or unique_violation or foreign_key_violation or insufficient_privilege then return; end;
    raise exception 'Expected inventory rejection: %',statement;
end $$;
insert into auth.users(id,email) values
 ('e2000000-0000-0000-0000-000000000001','inventory-a@example.invalid'),
 ('e2000000-0000-0000-0000-000000000002','inventory-b@example.invalid');
select set_config('request.jwt.claim.sub','e2000000-0000-0000-0000-000000000001',true);
select pg_temp.inventory_assert((select quantity=3 from public.pet_food_inventory where user_id=auth.uid()),'starter food once');
select public.ensure_account(); select public.ensure_account();
select pg_temp.inventory_assert((select quantity=3 from public.user_inventory where user_id=auth.uid()),'initialization never refills');
select pawquest_private.adjust_inventory(auth.uid(),'e1000000-0000-0000-0000-000000000001',2);
select pg_temp.inventory_assert((select quantity=5 from public.pet_food_inventory where user_id=auth.uid()),'stack food');
update public.pets set hunger=0 where user_id=auth.uid();
select public.pet_state('feed','e3000000-0000-0000-0000-000000000001');
select public.pet_state('feed','e3000000-0000-0000-0000-000000000001');
select pg_temp.inventory_assert((select quantity=4 from public.user_inventory where user_id=auth.uid()),'feed uses common inventory exactly once');
select pawquest_private.adjust_inventory(auth.uid(),'e1000000-0000-0000-0000-000000000001',-4);
select pg_temp.inventory_assert(jsonb_array_length(public.inventory_items())=0,'zero stock excluded from owned list');
select pg_temp.inventory_rejected($q$select pawquest_private.adjust_inventory(auth.uid(),'e1000000-0000-0000-0000-000000000001',-1)$q$);
select pg_temp.inventory_rejected($q$select pawquest_private.adjust_inventory(auth.uid(),'e1000000-0000-0000-0000-000000000001',1000001)$q$);
select pawquest_private.adjust_inventory(auth.uid(),'e1000000-0000-0000-0000-000000000002',1);
select pawquest_private.adjust_inventory(auth.uid(),'e1000000-0000-0000-0000-000000000002',1);
select pawquest_private.adjust_inventory(auth.uid(),'e1000000-0000-0000-0000-000000000005',1);
select pg_temp.inventory_assert((select count(*)=2 and max(quantity)=1 from public.user_inventory where user_id=auth.uid() and max_quantity=1),'unique ownership retries');
select pg_temp.inventory_rejected($q$select pawquest_private.adjust_inventory(auth.uid(),'e1000000-0000-0000-0000-000000000002',2)$q$);
select pg_temp.inventory_rejected($q$select pawquest_private.adjust_inventory(auth.uid(),'e1000000-0000-0000-0000-000000000005',-1)$q$);
select pg_temp.inventory_rejected($q$update public.user_inventory set max_quantity=1000000,quantity=2 where item_id='e1000000-0000-0000-0000-000000000002'$q$);
select pg_temp.inventory_rejected($q$update public.user_inventory set quantity=-1 where user_id=auth.uid()$q$);
select pg_temp.inventory_rejected($q$insert into public.user_inventory(user_id,item_id,max_quantity,quantity) values(auth.uid(),'e1000000-0000-0000-0000-000000000002',1,1)$q$);
update public.item_catalog set active=false where code='hat';
select pg_temp.inventory_assert(not exists(select 1 from jsonb_array_elements(public.shop_catalog()) item where item->>'code'='hat'),'inactive hidden from shop');
select pg_temp.inventory_assert(jsonb_array_length(public.inventory_items())=2,'inactive owned remains resolvable');
select pg_temp.inventory_rejected($q$select pawquest_private.adjust_inventory(auth.uid(),'e1000000-0000-0000-0000-000000000002',1)$q$);
select pg_temp.inventory_assert((select coins=0 from public.wallets where user_id=auth.uid()),'catalog/inventory never credits or debits wallet');
set local role authenticated;
select pg_temp.inventory_assert(jsonb_array_length(public.shop_catalog())=(select count(*) from public.item_catalog where active),'authenticated catalog loads');
select pg_temp.inventory_assert((select count(*)=3 from public.user_inventory),'owner RLS');
select pg_temp.inventory_assert(jsonb_array_length(public.inventory_items())=2,'owned RPC');
select pg_temp.inventory_rejected('update public.user_inventory set quantity=100');
select pg_temp.inventory_rejected('delete from public.user_inventory');
select pg_temp.inventory_rejected('insert into public.item_catalog(code) values(''bad'')');
select pg_temp.inventory_rejected($q$select pawquest_private.adjust_inventory(auth.uid(),'e1000000-0000-0000-0000-000000000002',1)$q$);
select set_config('request.jwt.claim.sub','e2000000-0000-0000-0000-000000000002',true);
select pg_temp.inventory_assert(jsonb_array_length(public.inventory_items())=1,'second owner only sees starter food');
select pg_temp.inventory_assert((select count(*)=1 from public.pet_food_inventory),'compatibility view enforces owner RLS');
reset role;
set local role anon;
select pg_temp.inventory_rejected('select public.shop_catalog()');
select pg_temp.inventory_rejected('select public.inventory_items()');
select pg_temp.inventory_rejected('select * from public.user_inventory');
reset role;
rollback;
\echo PASS catalog, stack/unique inventory, Feed compatibility, boundaries, RLS and private mutation permissions
