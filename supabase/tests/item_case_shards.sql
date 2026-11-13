-- Disposable database only. No remote project or real wallet.
begin;
create function pg_temp.case_assert(ok boolean,message text) returns void language plpgsql as $$
begin if ok is distinct from true then raise exception 'Item Case ASSERT: %',message; end if; end $$;
create function pg_temp.case_reject(statement text,expected text) returns void language plpgsql as $$
begin
    begin execute statement;
    exception when others then
        if sqlstate=expected then return; end if;
        raise exception 'Expected %, got %: %',expected,sqlstate,sqlerrm;
    end;
    raise exception 'Expected rejection: %',statement;
end $$;

select pg_temp.case_assert((select count(*)=20 from public.item_case_pool),'twenty real rewards');
select pg_temp.case_assert((select count(*)=4 and sum(probability_bps)=10000 from public.item_case_tiers),'four valid tiers');
select pg_temp.case_assert((select price_coins=100 and published_at is not null from public.item_case_versions),'published backend price');
select pg_temp.case_assert((select not enabled from public.item_case_banners),'banner remains disabled');
select pg_temp.case_assert((select count(*)=6 from public.item_case_pool where rarity='COMMON'),'six common');
select pg_temp.case_assert((select count(*)=6 from public.item_case_pool where rarity='RARE'),'six rare');
select pg_temp.case_assert((select count(*)=5 from public.item_case_pool where rarity='EPIC'),'five epic');
select pg_temp.case_assert((select count(*)=3 from public.item_case_pool where rarity='LEGENDARY'),'three legendary');
select pg_temp.case_assert((select abs(sum(effective_probability)-1)<0.000000000000001 from public.item_case_probabilities),'effective probabilities sum to one');
select pg_temp.case_assert((select bool_and(probability_numerator=300 and probability_denominator=30000)
    from public.item_case_probabilities where rarity='LEGENDARY'),'one percent per legendary');
select pg_temp.case_assert((select bool_and(p.asset_id=i.asset_id and p.item_code=i.code and p.max_quantity=1 and i.max_quantity=1)
    from public.item_case_pool p join public.item_catalog i on i.id=p.item_id),'catalog identity and unique ownership');
select pg_temp.case_assert((select bool_and(duplicate_item_shards=case rarity when 'COMMON' then 5 when 'RARE' then 10 when 'EPIC' then 20 else 40 end
    and redemption_item_shards=case rarity when 'COMMON' then 30 when 'RARE' then 60 when 'EPIC' then 120 else 240 end) from public.item_case_tiers),'conversion and redemption prices');
select pg_temp.case_assert((select count(*)=20 from public.item_shard_redemption_catalog),'redemption catalog uses real items');

select pg_temp.case_reject($q$update public.item_case_versions set price_coins=1$q$,'55000');
select pg_temp.case_reject($q$update public.item_case_tiers set probability_bps=1$q$,'55000');
select pg_temp.case_reject($q$delete from public.item_case_pool$q$,'55000');
select pg_temp.case_reject($q$update public.item_case_pool set weight=2$q$,'55000');

-- Drafts can be edited but cannot become visible/published until valid.
insert into public.item_case_versions(id,banner_id,version,price_coins)
values('fa240000-0000-0000-0000-000000000001','e2400000-0000-0000-0000-000000000001',2,100);
select pg_temp.case_reject($q$update public.item_case_versions set published_at=now() where version=2$q$,'23514');
select pg_temp.case_reject($q$update public.item_case_banners set current_version=2$q$,'23514');
select pg_temp.case_reject($q$insert into public.item_case_tiers values('fa240000-0000-0000-0000-000000000001','INVALID',100,5,30)$q$,'23514');
insert into public.item_case_tiers select 'fa240000-0000-0000-0000-000000000001',rarity,probability_bps,duplicate_item_shards,redemption_item_shards
    from public.item_case_tiers where version_id='e2400000-0000-0000-0000-000000000002';
select pg_temp.case_reject($q$update public.item_case_tiers set probability_bps=0 where version_id='fa240000-0000-0000-0000-000000000001'$q$,'23514');
select pg_temp.case_reject($q$update public.item_case_tiers set duplicate_item_shards=-1 where version_id='fa240000-0000-0000-0000-000000000001'$q$,'23514');
select pg_temp.case_reject($q$update public.item_case_tiers set redemption_item_shards=0 where version_id='fa240000-0000-0000-0000-000000000001'$q$,'23514');
select pg_temp.case_reject($q$insert into public.item_case_pool(version_id,item_id,rarity,weight) values('fa240000-0000-0000-0000-000000000001','fa990000-0000-0000-0000-000000000001','COMMON',1)$q$,'23503');
select pg_temp.case_reject($q$insert into public.item_case_pool(version_id,item_id,rarity,weight) values('fa240000-0000-0000-0000-000000000001','e1000000-0000-0000-0000-000000000001','COMMON',1)$q$,'23514');
select pg_temp.case_reject($q$insert into public.item_case_pool(version_id,item_id,rarity,weight) values('fa240000-0000-0000-0000-000000000001','e2300000-0000-0000-0000-000000000003','COMMON',0)$q$,'23514');
select pg_temp.case_reject($q$update public.item_case_versions set published_at=now() where version=2$q$,'23514'); -- empty pools
insert into public.item_case_pool(version_id,item_id,rarity,weight)
select 'fa240000-0000-0000-0000-000000000001',item_id,rarity,weight from public.item_case_pool where version_id='e2400000-0000-0000-0000-000000000002';
update public.item_case_tiers set probability_bps=5000 where version_id='fa240000-0000-0000-0000-000000000001' and rarity='COMMON';
select pg_temp.case_reject($q$update public.item_case_versions set published_at=now() where version=2$q$,'23514'); -- sum is not 10000
update public.item_case_tiers set probability_bps=5500 where version_id='fa240000-0000-0000-0000-000000000001' and rarity='COMMON';
update public.item_case_pool set weight=3 where version_id='fa240000-0000-0000-0000-000000000001' and asset_id='acc-sprout';
select pg_temp.case_assert((select probability_numerator=16500 and probability_denominator=80000 from public.item_case_probabilities
    where version_id='fa240000-0000-0000-0000-000000000001' and asset_id='acc-sprout'),'unequal per-item weights');
update public.item_catalog set active=false where asset_id='acc-sprout';
select pg_temp.case_reject($q$update public.item_case_versions set published_at=now() where version=2$q$,'23514');
update public.item_catalog set active=true where asset_id='acc-sprout';
update public.item_case_versions set published_at=now() where version=2;
insert into public.item_case_versions(id,banner_id,version,price_coins)
values('fa240000-0000-0000-0000-000000000003','e2400000-0000-0000-0000-000000000001',3,100);
insert into public.item_case_tiers values('fa240000-0000-0000-0000-000000000003','COMMON',5500,5,30);
insert into public.item_case_pool(version_id,item_id,rarity,weight)
values('fa240000-0000-0000-0000-000000000003','e2300000-0000-0000-0000-000000000003','COMMON',1);

insert into auth.users(id,email) values('fa250000-0000-0000-0000-000000000001','case-a@example.invalid'),
    ('fa250000-0000-0000-0000-000000000002','case-b@example.invalid');
update public.wallets set coins=2000 where user_id='fa250000-0000-0000-0000-000000000001';
select pg_temp.case_assert((select bool_and(max_quantity=1) from public.item_catalog where category in ('ACCESSORY','FURNITURE')),'no multi-copy furniture exists');
select pg_temp.case_assert((select max_quantity=1000000 from public.item_catalog where code='basic_food'),'food stack preserved');
select pg_temp.case_reject($q$insert into public.shard_balances values('fa250000-0000-0000-0000-000000000001','ITEM_SHARD',-1,now())$q$,'23514');
select pg_temp.case_reject($q$insert into public.shard_balances values('fa250000-0000-0000-0000-000000000001','COIN_SHARD',0,now())$q$,'23503');
select pg_temp.case_reject($q$select pawquest_private.settle_item_case('fa250000-0000-0000-0000-000000000001','e2400000-0000-0000-0000-000000000002','e2300000-0000-0000-0000-000000000003',gen_random_uuid())$q$,'23514');
update public.item_case_banners set enabled=true;

-- Two copies of one reward in every tier: first inventory, then Item Shards.
do $$ declare item uuid; rarity text; receipt public.item_case_openings%rowtype; req uuid;
begin
    for item,rarity in select item_id,p.rarity from public.item_case_pool p where version_id='e2400000-0000-0000-0000-000000000002'
        and asset_id in ('acc-sprout','acc-glasses','acc-wizard-hat','acc-crown') loop
        receipt:=pawquest_private.settle_item_case('fa250000-0000-0000-0000-000000000001','e2400000-0000-0000-0000-000000000002',item,gen_random_uuid());
        perform pg_temp.case_assert(receipt.outcome='ITEM' and receipt.quantity_granted=1 and receipt.item_shards_granted=0,'first acquisition');
        req:=gen_random_uuid();
        receipt:=pawquest_private.settle_item_case('fa250000-0000-0000-0000-000000000001','e2400000-0000-0000-0000-000000000002',item,req);
        perform pg_temp.case_assert(receipt.outcome='ITEM_SHARDS' and receipt.quantity_granted=0 and receipt.item_shards_granted=
            case rarity when 'COMMON' then 5 when 'RARE' then 10 when 'EPIC' then 20 else 40 end,'duplicate conversion');
        perform pg_temp.case_assert((pawquest_private.settle_item_case('fa250000-0000-0000-0000-000000000001','e2400000-0000-0000-0000-000000000002',item,req)).id=receipt.id,'idempotent private settlement');
    end loop;
end $$;
select pg_temp.case_assert((select balance=75 from public.shard_balances where user_id='fa250000-0000-0000-0000-000000000001' and currency='ITEM_SHARD'),'all tier conversions');
select pg_temp.case_assert(not exists(select 1 from public.shard_balances where currency='PET_SHARD'),'item openings grant no pet shards');
select pg_temp.case_assert((select coins=1200 from public.wallets where user_id='fa250000-0000-0000-0000-000000000001'),'eight case debits');
select pg_temp.case_assert((select count(*)=4 and bool_and(quantity=1) from public.user_inventory where user_id='fa250000-0000-0000-0000-000000000001' and max_quantity=1),'duplicate inventory unchanged');
select pg_temp.case_assert((select count(*)=8 and bool_and(coins=-100 and pet_xp=0 and reward_day is null) from public.wallet_transactions
    where user_id='fa250000-0000-0000-0000-000000000001'),'reuse coin ledger without quest effects');
do $$ declare receipt public.item_case_openings%rowtype;
begin
    select * into receipt from public.item_case_openings limit 1;
    update public.item_case_banners set enabled=false;
    perform pg_temp.case_assert((pawquest_private.settle_item_case(receipt.user_id,receipt.version_id,receipt.item_id,receipt.request_id)).id=receipt.id,'replay survives banner disable');
    perform pg_temp.case_reject(format('select pawquest_private.settle_item_case(%L,%L,%L,%L)',receipt.user_id,
        'fa240000-0000-0000-0000-000000000001',receipt.item_id,receipt.request_id),'23514');
    update public.item_case_banners set enabled=true;
end $$;
update public.item_catalog set active=false where asset_id='furn-plant';
select pg_temp.case_reject($q$select pawquest_private.settle_item_case('fa250000-0000-0000-0000-000000000001','e2400000-0000-0000-0000-000000000002','e2300000-0000-0000-0000-000000000003',gen_random_uuid())$q$,'23514');
update public.item_catalog set active=true where asset_id='furn-plant';

select pg_temp.case_reject($q$select pawquest_private.settle_item_case('fa250000-0000-0000-0000-000000000002','e2400000-0000-0000-0000-000000000002','e2300000-0000-0000-0000-000000000003',gen_random_uuid())$q$,'23514');
select pg_temp.case_reject($q$update public.shard_ledger set delta=1$q$,'55000');
select pg_temp.case_reject($q$delete from public.item_case_openings$q$,'55000');
select pg_temp.case_reject($q$insert into public.shard_ledger(user_id,currency,request_id,reason,delta,balance_after,note)
    values('fa250000-0000-0000-0000-000000000001','ITEM_SHARD',gen_random_uuid(),'ADMIN_ADJUSTMENT',-76,0,'negative test')$q$,'23514');
select pg_temp.case_reject($q$insert into public.shard_ledger(id,user_id,currency,request_id,reason,delta,balance_after,note)
    select gen_random_uuid(),user_id,currency,request_id,'ADMIN_ADJUSTMENT',1,0,'reused key' from public.shard_ledger limit 1$q$,'23505');
select pg_temp.case_assert((select balance=75 from public.shard_balances where currency='ITEM_SHARD'),'failed posts do not change balance');

-- Trusted fixture for reserved Pet balance. An Item debit cannot spend it.
insert into public.shard_ledger(user_id,currency,request_id,reason,delta,balance_after,note)
    values('fa250000-0000-0000-0000-000000000001','PET_SHARD',gen_random_uuid(),'ADMIN_ADJUSTMENT',50,0,'reserved currency isolation fixture');
select pg_temp.case_reject($q$insert into public.shard_ledger(user_id,currency,request_id,reason,delta,balance_after,note)
    values('fa250000-0000-0000-0000-000000000001','ITEM_SHARD',gen_random_uuid(),'ADMIN_ADJUSTMENT',-76,0,'cannot spend pet balance')$q$,'23514');
select pg_temp.case_assert((select balance=50 from public.shard_balances where currency='PET_SHARD'),'item debit leaves pet balance untouched');

-- Validate reserved redemption history with trusted test-only atomic posting.
-- This is not an application redemption endpoint.
select pg_temp.case_reject($q$insert into public.item_shard_redemptions(user_id,request_id,version_id,item_id,shards_spent,shard_ledger_id)
 values('fa250000-0000-0000-0000-000000000001',gen_random_uuid(),'e2400000-0000-0000-0000-000000000002','e2300000-0000-0000-0000-000000000017',1,gen_random_uuid())$q$,'23514');
select pg_temp.case_reject($q$insert into public.item_shard_redemptions(user_id,request_id,version_id,item_id,shards_spent,shard_ledger_id)
 values('fa250000-0000-0000-0000-000000000001',gen_random_uuid(),'e2400000-0000-0000-0000-000000000002','e2300000-0000-0000-0000-000000000003',30,gen_random_uuid())$q$,'23514');

-- Existing shop RPC must not return a case receipt as a shop purchase.
select set_config('request.jwt.claim.sub','fa250000-0000-0000-0000-000000000001',true);
select pg_temp.case_assert((select public.shop_purchase(item_id,1,request_id)->>'status'='request_conflict' from public.item_case_openings limit 1),'cross-kind request collision');

-- Inject a grant failure: wallet debit and opening receipt must roll back too.
create function pg_temp.fail_case_grant() returns trigger language plpgsql as $$ begin raise exception 'injected grant failure'; end $$;
create trigger fail_case_grant before insert on public.user_inventory for each row execute function pg_temp.fail_case_grant();
select pg_temp.case_reject($q$select pawquest_private.settle_item_case('fa250000-0000-0000-0000-000000000001','e2400000-0000-0000-0000-000000000002','e2300000-0000-0000-0000-000000000017','fa260000-0000-0000-0000-000000000001')$q$,'P0001');
drop trigger fail_case_grant on public.user_inventory;
select pg_temp.case_assert(not exists(select 1 from public.item_case_openings where request_id='fa260000-0000-0000-0000-000000000001'),'opening rolled back');
select pg_temp.case_assert((select coins=1200 from public.wallets where user_id='fa250000-0000-0000-0000-000000000001'),'debit rolled back');
create function pg_temp.fail_case_shards() returns trigger language plpgsql as $$ begin raise exception 'injected shard failure'; end $$;
create trigger fail_case_shards before insert on public.shard_ledger for each row execute function pg_temp.fail_case_shards();
select pg_temp.case_reject($q$select pawquest_private.settle_item_case('fa250000-0000-0000-0000-000000000001','e2400000-0000-0000-0000-000000000002','e2300000-0000-0000-0000-000000000003','fa260000-0000-0000-0000-000000000002')$q$,'P0001');
drop trigger fail_case_shards on public.shard_ledger;
select pg_temp.case_assert(not exists(select 1 from public.wallet_transactions where request_id='fa260000-0000-0000-0000-000000000002'),'duplicate debit rolled back');
select pg_temp.case_assert((select coins=1200 from public.wallets where user_id='fa250000-0000-0000-0000-000000000001'),'duplicate wallet unchanged');
select pg_temp.case_assert((select balance=75 from public.shard_balances where currency='ITEM_SHARD'),'duplicate shards unchanged');

insert into public.item_shard_redemptions(id,user_id,request_id,version_id,item_id,shards_spent,shard_ledger_id)
 values('fa300000-0000-0000-0000-000000000001','fa250000-0000-0000-0000-000000000001','fa310000-0000-0000-0000-000000000001',
 'e2400000-0000-0000-0000-000000000002','e2300000-0000-0000-0000-000000000017',30,'fa320000-0000-0000-0000-000000000001');
insert into public.shard_ledger(id,user_id,currency,request_id,reason,delta,balance_after,redemption_id)
 values('fa320000-0000-0000-0000-000000000001','fa250000-0000-0000-0000-000000000001','ITEM_SHARD','fa310000-0000-0000-0000-000000000001',
 'ITEM_REDEMPTION',-30,0,'fa300000-0000-0000-0000-000000000001');
select pawquest_private.adjust_inventory('fa250000-0000-0000-0000-000000000001','e2300000-0000-0000-0000-000000000017',1);
select pg_temp.case_assert((select balance=45 from public.shard_balances where currency='ITEM_SHARD'),'reserved redemption ledger debit');

-- Exercise deferred bidirectional ownership/receipt links now, not only at rollback.
set constraints all immediate;
set constraints all deferred;

set local role authenticated;
select pg_temp.case_assert((select count(*)=8 from public.item_case_openings),'own opening history');
select pg_temp.case_assert((select count(*)=6 from public.shard_ledger),'own shard ledger');
select pg_temp.case_assert((select count(*)=1 from public.item_shard_redemptions),'own redemption history');
select pg_temp.case_assert((select count(*)=20 from public.item_case_probabilities where version_id='e2400000-0000-0000-0000-000000000002'),'published effective odds readable');
select pg_temp.case_assert(not exists(select 1 from public.item_case_versions where version=3),'draft version hidden');
select pg_temp.case_assert(not exists(select 1 from public.item_case_probabilities where version_id='fa240000-0000-0000-0000-000000000003'),'draft odds hidden');
select pg_temp.case_reject($q$update public.shard_balances set balance=999$q$,'42501');
select pg_temp.case_reject($q$insert into public.item_case_openings(user_id,request_id) values(auth.uid(),gen_random_uuid())$q$,'42501');
select pg_temp.case_reject($q$insert into public.item_shard_redemptions(user_id,request_id) values(auth.uid(),gen_random_uuid())$q$,'42501');
select pg_temp.case_reject($q$insert into public.shard_ledger(user_id,currency,request_id,reason,delta,balance_after,note)
 values(auth.uid(),'PET_SHARD',gen_random_uuid(),'ADMIN_ADJUSTMENT',50,50,'client grant')$q$,'42501');
select pg_temp.case_reject($q$select pawquest_private.settle_item_case(auth.uid(),'e2400000-0000-0000-0000-000000000002','e2300000-0000-0000-0000-000000000003',gen_random_uuid())$q$,'42501');
select pg_temp.case_reject($q$update public.item_case_banners set enabled=true$q$,'42501');
select set_config('request.jwt.claim.sub','fa250000-0000-0000-0000-000000000002',true);
select pg_temp.case_assert(not exists(select 1 from public.item_case_openings),'cross-user openings hidden');
select pg_temp.case_assert(not exists(select 1 from public.shard_balances),'cross-user balances hidden');
select pg_temp.case_assert(not exists(select 1 from public.shard_ledger),'cross-user ledger hidden');
select pg_temp.case_assert(not exists(select 1 from public.item_shard_redemptions),'cross-user redemption history hidden');
reset role;
set local role anon;
select pg_temp.case_reject($q$select * from public.shard_balances$q$,'42501');
select pg_temp.case_reject($q$select * from public.item_case_probabilities$q$,'42501');
reset role;
rollback;
\echo PASS Item Case configuration, exact weights, catalog references, unique duplicate conversion, shard constraints, rollback and RLS
