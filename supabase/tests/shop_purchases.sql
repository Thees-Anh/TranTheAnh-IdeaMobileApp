begin;
create function pg_temp.purchase_assert(ok boolean,message text) returns void language plpgsql as $$
begin if ok is distinct from true then raise exception 'Purchase ASSERT: %',message; end if; end $$;
insert into auth.users(id,email) values
 ('f1000000-0000-0000-0000-000000000001','buyer@example.invalid'),
 ('f1000000-0000-0000-0000-000000000002','other-buyer@example.invalid');
select set_config('request.jwt.claim.sub','f1000000-0000-0000-0000-000000000001',true);
select pg_temp.purchase_assert(public.shop_purchase('e1000000-0000-0000-0000-000000000001',1,'f2000000-0000-0000-0000-000000000001')->>'status'='insufficient_balance','balance gate');
select pg_temp.purchase_assert((select coins=0 from public.wallets where user_id=auth.uid()),'failure no debit');
update public.wallets set coins=200 where user_id=auth.uid(); -- trusted test fixture funding
select pg_temp.purchase_assert(public.shop_purchase('e1000000-0000-0000-0000-000000000001',2,'f2000000-0000-0000-0000-000000000001')->>'status'='purchased','food purchase');
select pg_temp.purchase_assert((select quantity=5 from public.pet_food_inventory where user_id=auth.uid()),'grant quantity');
select pg_temp.purchase_assert((select coins=180 from public.wallets where user_id=auth.uid()),'server price debit');
select pg_temp.purchase_assert((select coins=-20 and pet_xp=0 and unit_price=10 and reward_day is null and balance_after=180 from public.wallet_transactions where user_id=auth.uid()),'debit receipt');
update public.item_catalog set price_coins=15,active=false where code='basic_food';
select pg_temp.purchase_assert(public.shop_purchase('e1000000-0000-0000-0000-000000000001',2,'f2000000-0000-0000-0000-000000000001')->'receipt'->>'unit_price'='10','retry original price even if inactive');
select pg_temp.purchase_assert((select count(*)=1 from public.wallet_transactions where user_id=auth.uid()),'retry no extra receipt');
select pg_temp.purchase_assert((select coins=180 from public.wallets where user_id=auth.uid()),'retry no extra debit');
select pg_temp.purchase_assert(public.shop_purchase('e1000000-0000-0000-0000-000000000001',1,'f2000000-0000-0000-0000-000000000001')->>'status'='request_conflict','request payload mismatch');
select pg_temp.purchase_assert(public.shop_purchase('e1000000-0000-0000-0000-000000000001',1,'f2000000-0000-0000-0000-000000000002')->>'status'='item_unavailable','inactive item');
update public.item_catalog set active=true where code='basic_food';
select pg_temp.purchase_assert(public.shop_purchase('e1000000-0000-0000-0000-000000000001',1,'f2000000-0000-0000-0000-000000000002')->'receipt'->>'unit_price'='15','new purchase current price');
select pg_temp.purchase_assert(public.shop_purchase('e1000000-0000-0000-0000-000000000002',2,gen_random_uuid())->>'status'='invalid_quantity','unique cannot stack');
select pg_temp.purchase_assert(public.shop_purchase('e1000000-0000-0000-0000-000000000002',1,gen_random_uuid())->>'status'='purchased','hat purchase');
select pg_temp.purchase_assert(public.shop_purchase('e1000000-0000-0000-0000-000000000002',1,gen_random_uuid())->>'status'='already_owned','hat new request no double charge');
select pg_temp.purchase_assert((select coins=105 from public.wallets where user_id=auth.uid()),'only successful purchases debit');
select pg_temp.purchase_assert(public.shop_purchase(gen_random_uuid(),1,gen_random_uuid())->>'status'='item_unavailable','missing item');
select pg_temp.purchase_assert(public.shop_purchase(null,1,gen_random_uuid())->>'status'='invalid_quantity','missing item identifier');
select pg_temp.purchase_assert(public.shop_purchase('e1000000-0000-0000-0000-000000000001',0,gen_random_uuid())->>'status'='invalid_quantity','zero rejected');
select pg_temp.purchase_assert(public.shop_purchase('e1000000-0000-0000-0000-000000000001',-1,gen_random_uuid())->>'status'='invalid_quantity','negative rejected');
select pg_temp.purchase_assert(public.shop_purchase('e1000000-0000-0000-0000-000000000001',101,gen_random_uuid())->>'status'='invalid_quantity','large rejected');
select pg_temp.purchase_assert(public.shop_purchase('e1000000-0000-0000-0000-000000000001',null,gen_random_uuid())->>'status'='invalid_quantity','null rejected');
update public.pet_food_inventory set quantity=1000000 where user_id=auth.uid();
select pg_temp.purchase_assert(public.shop_purchase('e1000000-0000-0000-0000-000000000001',1,gen_random_uuid())->>'status'='inventory_full','capacity rejected');
update public.pet_food_inventory set quantity=0 where user_id=auth.uid();
-- A wallet write failure after inventory grant rolls back stock AND receipt.
create function pg_temp.fail_purchase_wallet() returns trigger language plpgsql as $$
begin raise exception 'Injected wallet write failure'; end $$;
create trigger fail_purchase_wallet before update on public.wallets for each row execute function pg_temp.fail_purchase_wallet();
do $$ begin
    begin perform public.shop_purchase('e1000000-0000-0000-0000-000000000001',1,'f2000000-0000-0000-0000-000000000003');
        raise exception 'Expected failure';
    exception when raise_exception then if sqlerrm<>'Injected wallet write failure' then raise; end if; end;
end $$;
drop trigger fail_purchase_wallet on public.wallets;
select pg_temp.purchase_assert((select quantity=0 from public.pet_food_inventory where user_id=auth.uid()),'atomic inventory rollback');
select pg_temp.purchase_assert((select coins=105 from public.wallets where user_id=auth.uid()),'atomic wallet rollback');
select pg_temp.purchase_assert(not exists(select 1 from public.wallet_transactions where request_id='f2000000-0000-0000-0000-000000000003'),'atomic ledger rollback');
select pg_temp.purchase_assert(public.quest_daily_reward_count()=0,'spending never counts toward rewarded sessions');
select pg_temp.purchase_assert((select xp=0 from public.pets where user_id=auth.uid()),'no purchase XP');
set local role authenticated;
select pg_temp.purchase_assert(public.shop_purchase('e1000000-0000-0000-0000-000000000001',1,gen_random_uuid())->>'status'='purchased','authenticated RPC permitted');
do $$ begin
    begin insert into public.wallet_transactions(user_id,kind) values(auth.uid(),'shop_purchase');
        raise exception 'Expected permission failure'; exception when insufficient_privilege then null; end;
end $$;
select set_config('request.jwt.claim.sub','f1000000-0000-0000-0000-000000000002',true);
select pg_temp.purchase_assert(not exists(select 1 from public.wallet_transactions),'other receipt RLS');
select pg_temp.purchase_assert(public.shop_purchase('e1000000-0000-0000-0000-000000000001',2,'f2000000-0000-0000-0000-000000000001')->>'status'='insufficient_balance','request keys scoped to owner');
reset role;
set local role anon;
do $$ begin
    begin perform public.shop_purchase('e1000000-0000-0000-0000-000000000001',1,gen_random_uuid());
        raise exception 'Expected permission failure'; exception when insufficient_privilege then null; end;
end $$;
reset role;
select set_config('request.jwt.claim.sub','',true);
do $$ begin
    begin perform public.shop_purchase('e1000000-0000-0000-0000-000000000001',1,gen_random_uuid());
        raise exception 'Expected authentication failure'; exception when insufficient_privilege then null; end;
end $$;
rollback;
\echo PASS authoritative purchase price, balance, quantity, receipt replay, RLS and atomic failure rollback
