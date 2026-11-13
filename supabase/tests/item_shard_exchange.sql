-- Disposable local database only. All fixtures and fault injection roll back.
begin;
create function pg_temp.exchange_assert(ok boolean,msg text) returns void language plpgsql as $$
begin if ok is distinct from true then raise exception 'Exchange ASSERT: %',msg; end if; end $$;
create function pg_temp.exchange_reject(statement text,code text) returns void language plpgsql as $$
begin
    begin execute statement; exception when others then
        if sqlstate=code then return; end if; raise exception 'Expected %, got %: %',code,sqlstate,sqlerrm;
    end; raise exception 'Expected rejection: %',statement;
end $$;
insert into auth.users(id,email) values('fc100000-0000-0000-0000-000000000001','exchange@example.invalid'),('fc100000-0000-0000-0000-000000000002','other@example.invalid');
insert into public.shard_ledger(user_id,currency,request_id,reason,delta,balance_after,note)
values('fc100000-0000-0000-0000-000000000001','ITEM_SHARD',gen_random_uuid(),'ADMIN_ADJUSTMENT',1000,0,'Test funding');
select set_config('request.jwt.claim.sub','fc100000-0000-0000-0000-000000000001',true);
set local role authenticated;
select pg_temp.exchange_assert(public.item_shard_redeem('e2300000-0000-0000-0000-000000000003','fc200000-0000-0000-0000-000000000001')->>'status'='quote_required','no unquoted charge');
select pg_temp.exchange_assert(public.item_shard_quote('e2300000-0000-0000-0000-000000000003','fc200000-0000-0000-0000-000000000001')->>'status'='quoted','server quote');
select pg_temp.exchange_assert(public.item_shard_quote('e2300000-0000-0000-0000-000000000003','fc200000-0000-0000-0000-000000000001')#>>'{quote,price_item_shards}'='30','quote retry unchanged');
select pg_temp.exchange_assert((select balance=1000 from public.shard_balances where currency='ITEM_SHARD'),'quoting never spends');
select pg_temp.exchange_assert(public.item_shard_redeem('e2300000-0000-0000-0000-000000000003','fc200000-0000-0000-0000-000000000001')#>>'{receipt,item_shards_balance_after}'='970','atomic settlement');
select pg_temp.exchange_assert(public.item_shard_redeem('e2300000-0000-0000-0000-000000000003','fc200000-0000-0000-0000-000000000001')#>>'{receipt,item_shards_balance_after}'='970','replay original snapshot');
select pg_temp.exchange_assert((select quantity=1 from public.user_inventory where item_id='e2300000-0000-0000-0000-000000000003'),'one inventory copy');
select pg_temp.exchange_assert(public.item_shard_quote('e2300000-0000-0000-0000-000000000003','fc200000-0000-0000-0000-000000000002')->>'status'='already_owned','unique ownership');
select pg_temp.exchange_assert(public.item_shard_redeem('e2300000-0000-0000-0000-000000000007','fc200000-0000-0000-0000-000000000001')->>'status'='request_conflict','conflicting key');
select pg_temp.exchange_reject($q$select public.item_shard_redeem(p_item_id=>'e2300000-0000-0000-0000-000000000007',p_request_id=>gen_random_uuid(),p_price=>1)$q$,'42883');
select pg_temp.exchange_reject($q$update public.shard_balances set balance=99999$q$,'42501');
select pg_temp.exchange_reject($q$select * from pawquest_private.item_shard_quotes$q$,'42501');
select pg_temp.exchange_reject($q$insert into public.shard_ledger(user_id,currency,request_id,reason,delta,balance_after,note) values(auth.uid(),'ITEM_SHARD',gen_random_uuid(),'ADMIN_ADJUSTMENT',100,100,'Forged')$q$,'42501');
select set_config('request.jwt.claim.sub','fc100000-0000-0000-0000-000000000002',true);
select pg_temp.exchange_assert(not exists(select 1 from public.item_shard_redemptions),'cross-user receipt isolation');
select pg_temp.exchange_assert(not exists(select 1 from public.shard_ledger),'cross-user ledger isolation');
select pg_temp.exchange_assert(public.item_shard_quote('e2300000-0000-0000-0000-000000000007','fc200000-0000-0000-0000-000000000001')->>'status'='insufficient_shards','currencies/users cannot fund each other');
select set_config('request.jwt.claim.sub','',true);
select pg_temp.exchange_reject($q$select public.item_shard_redeem(gen_random_uuid(),gen_random_uuid())$q$,'42501');
reset role;
set local role anon;
select pg_temp.exchange_reject($q$select public.item_shard_quote(gen_random_uuid(),gen_random_uuid())$q$,'42501');
reset role;

-- A mid-transaction inventory failure must roll back ledger and receipt together.
select set_config('request.jwt.claim.sub','fc100000-0000-0000-0000-000000000001',true);
select public.item_shard_quote('e2300000-0000-0000-0000-000000000007','fc200000-0000-0000-0000-000000000003');
create function pg_temp.exchange_fail() returns trigger language plpgsql as $$ begin raise exception 'Injected grant failure'; end $$;
create trigger exchange_fail before insert on public.user_inventory for each row execute function pg_temp.exchange_fail();
set local role authenticated;
select pg_temp.exchange_reject($q$select public.item_shard_redeem('e2300000-0000-0000-0000-000000000007','fc200000-0000-0000-0000-000000000003')$q$,'P0001');
select pg_temp.exchange_assert((select balance=970 from public.shard_balances where currency='ITEM_SHARD'),'failure rolled back balance');
select pg_temp.exchange_assert(not exists(select 1 from public.item_shard_redemptions where request_id='fc200000-0000-0000-0000-000000000003'),'failure rolled back receipt');
reset role;
drop trigger exchange_fail on public.user_inventory;

-- Quote is bound to a version, so changed prices require a new explicit quote.
insert into public.item_case_versions(id,banner_id,version,price_coins) values('fc300000-0000-0000-0000-000000000001','e2400000-0000-0000-0000-000000000001',2,100);
insert into public.item_case_tiers select 'fc300000-0000-0000-0000-000000000001',rarity,probability_bps,duplicate_item_shards,redemption_item_shards+1 from public.item_case_tiers where version_id='e2400000-0000-0000-0000-000000000002';
insert into public.item_case_pool select 'fc300000-0000-0000-0000-000000000001',item_id,rarity,weight,redeemable,item_code,asset_id,category,max_quantity from public.item_case_pool where version_id='e2400000-0000-0000-0000-000000000002';
update public.item_case_versions set published_at=clock_timestamp() where id='fc300000-0000-0000-0000-000000000001';
update public.item_case_banners set current_version=2 where code='item_case';
set local role authenticated;
select pg_temp.exchange_assert(public.item_shard_redeem('e2300000-0000-0000-0000-000000000007','fc200000-0000-0000-0000-000000000003')->>'status'='price_changed','changed quote never charged');
select pg_temp.exchange_assert(public.item_shard_quote(gen_random_uuid(),gen_random_uuid())->>'status'='item_unavailable','ineligible item');
select pg_temp.exchange_assert((select balance=970 from public.shard_balances where currency='ITEM_SHARD'),'no extra charges');
select pg_temp.exchange_assert(not exists(select 1 from public.shard_balances where currency='PET_SHARD'),'no Pet Shards');
reset role;
set constraints all immediate;
rollback;
