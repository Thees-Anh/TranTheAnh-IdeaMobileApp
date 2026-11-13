-- Run only on a disposable database. Fault/entropy injection is privileged,
-- transaction-local, restored explicitly, and finally rolled back in full.
begin;
create function pg_temp.api_assert(ok boolean,message text) returns void language plpgsql as $$
begin if ok is distinct from true then raise exception 'Case API ASSERT: %',message; end if; end $$;
create function pg_temp.api_reject(statement text,expected text) returns void language plpgsql as $$
begin
    begin execute statement;
    exception when others then
        if sqlstate=expected then return; end if;
        raise exception 'Expected %, got %: %',expected,sqlstate,sqlerrm;
    end;
    raise exception 'Expected rejection: %',statement;
end $$;
create temp table api_original_functions(name text primary key,definition text not null);
insert into api_original_functions values
('bytes',pg_get_functiondef('pawquest_private.item_case_random_bytes(integer)'::regprocedure)),
('below',pg_get_functiondef('pawquest_private.item_case_random_below(bigint)'::regprocedure));

-- Exercise the actual pgcrypto path, not only an injected random source.
select pg_temp.api_assert(octet_length(pawquest_private.item_case_random_bytes(7))=7,'pgcrypto bytes');
select pg_temp.api_assert((select bool_and(v>=0 and v<10000) from
    (select pawquest_private.item_case_random_below(10000) v from generate_series(1,1024)) s),'real secure draws bounded');
select pg_temp.api_assert(pawquest_private.item_case_random_below(1)=0,'unit bound');
select pg_temp.api_reject($q$select pawquest_private.item_case_random_below(0)$q$,'22023');
select pg_temp.api_reject($q$select pawquest_private.item_case_random_below(72057594037927937)$q$,'22023');

-- Exact unbiased rejection test: 2^56-1 is outside the accepted range for 10.
create temp table api_bytes_queue(seq bigint generated always as identity primary key,bytes bytea not null);
create or replace function pawquest_private.item_case_random_bytes(p_count integer) returns bytea
language plpgsql volatile security definer set search_path='' as $$
declare result bytea;
begin
    delete from pg_temp.api_bytes_queue where seq=(select min(seq) from pg_temp.api_bytes_queue) returning bytes into result;
    if result is null then raise exception 'Test bytes queue exhausted'; end if;
    return result;
end $$;
insert into api_bytes_queue(bytes) values(decode('ffffffffffffff','hex')),(decode('00000000000009','hex'));
select pg_temp.api_assert(pawquest_private.item_case_random_below(10)=9,'reject biased tail then accept next sample');
select pg_temp.api_assert(not exists(select 1 from api_bytes_queue),'two samples consumed');
do $$ begin execute (select definition from api_original_functions where name='bytes'); end $$;

-- Exhaustive tier mapping is deterministic and verifies exact configured mass.
select pg_temp.api_assert((select count(*)=5500 from generate_series(0,9999) n
    where pawquest_private.item_case_rarity_at('e2400000-0000-0000-0000-000000000002',n)='COMMON'),'5500 Common tickets');
select pg_temp.api_assert((select count(*)=3000 from generate_series(0,9999) n
    where pawquest_private.item_case_rarity_at('e2400000-0000-0000-0000-000000000002',n)='RARE'),'3000 Rare tickets');
select pg_temp.api_assert((select count(*)=1200 from generate_series(0,9999) n
    where pawquest_private.item_case_rarity_at('e2400000-0000-0000-0000-000000000002',n)='EPIC'),'1200 Epic tickets');
select pg_temp.api_assert((select count(*)=300 from generate_series(0,9999) n
    where pawquest_private.item_case_rarity_at('e2400000-0000-0000-0000-000000000002',n)='LEGENDARY'),'300 Legendary tickets');
select pg_temp.api_assert((select count(distinct pawquest_private.item_case_item_at('e2400000-0000-0000-0000-000000000002','COMMON',n))=6
    from generate_series(0,5) n),'all six equal-weight Common rewards');
select pg_temp.api_reject($q$select pawquest_private.item_case_item_at('e2400000-0000-0000-0000-000000000002','COMMON',6)$q$,'23514');

-- Save/replace only the private entropy helper for precise outcome tests. The
-- production RPC still accepts exactly banner and request; no RNG hook exists.
create temp table api_ticket_queue(seq bigint generated always as identity primary key,ticket bigint not null);
create or replace function pawquest_private.item_case_random_below(p_upper bigint) returns bigint
language plpgsql volatile security definer set search_path='' as $$
declare result bigint;
begin
    delete from pg_temp.api_ticket_queue where seq=(select min(seq) from pg_temp.api_ticket_queue) returning ticket into result;
    if result is null or result<0 or result>=p_upper then raise exception 'Test ticket invalid or exhausted'; end if;
    return result;
end $$;
create function pg_temp.api_open(ticket bigint,request uuid,banner uuid default 'e2400000-0000-0000-0000-000000000001') returns jsonb
language plpgsql as $$
begin
    insert into pg_temp.api_ticket_queue(ticket) values(ticket),(0);
    return public.item_case_open(banner,request);
end $$;

insert into auth.users(id,email) values
('fb260000-0000-0000-0000-000000000001','api-a@example.invalid'),
('fb260000-0000-0000-0000-000000000002','api-b@example.invalid'),
('fb260000-0000-0000-0000-000000000003','api-c@example.invalid'),
('fb260000-0000-0000-0000-000000000004','api-multi@example.invalid');
update public.wallets set coins=10000 where user_id='fb260000-0000-0000-0000-000000000001';
update public.wallets set coins=1000 where user_id in ('fb260000-0000-0000-0000-000000000003','fb260000-0000-0000-0000-000000000004');
update public.item_case_banners set enabled=true;
select set_config('request.jwt.claim.sub','fb260000-0000-0000-0000-000000000001',true);
create temp table api_first_response as select pg_temp.api_open(0,'fb270000-0000-0000-0000-000000000001') as result;
select pg_temp.api_assert((select result->>'status'='opened' and result->'receipt'->>'reward_outcome'='ITEM_GRANTED'
    and result->'receipt'->>'quantity_granted'='1' and result->'receipt'->>'item_shards_granted'='0'
    and result->'receipt'->>'opening_cost_coins'='100' and result->'receipt'->>'coins_balance_after'='9900'
    and result->'receipt'->>'item_shards_balance_after'='0'
    and result->'receipt'->>'winning_item_id'='e2300000-0000-0000-0000-000000000003' from api_first_response),'first acquisition response');
select pg_temp.api_assert(pg_temp.api_open(0,'fb270000-0000-0000-0000-000000000002')->'receipt'->>'item_shards_granted'='5','Common duplicate');
select pawquest_private.adjust_inventory('fb260000-0000-0000-0000-000000000001','e2300000-0000-0000-0000-000000000005',1);
select pawquest_private.adjust_inventory('fb260000-0000-0000-0000-000000000001','e2300000-0000-0000-0000-000000000002',1);
select pawquest_private.adjust_inventory('fb260000-0000-0000-0000-000000000001','e2300000-0000-0000-0000-000000000001',1);
select pg_temp.api_assert(pg_temp.api_open(5500,'fb270000-0000-0000-0000-000000000003')->'receipt'->>'item_shards_granted'='10','Rare duplicate');
select pg_temp.api_assert(pg_temp.api_open(8500,'fb270000-0000-0000-0000-000000000004')->'receipt'->>'item_shards_granted'='20','Epic duplicate');
select pg_temp.api_assert(pg_temp.api_open(9700,'fb270000-0000-0000-0000-000000000005')->'receipt'->>'item_shards_granted'='40','Legendary duplicate');
select pg_temp.api_assert((select count(*)=4 and bool_and(outcome='ITEM_SHARDS') and bool_and(quantity_granted=0)
    from public.item_case_openings where user_id=auth.uid() and outcome<>'ITEM'),'duplicate outcome, not another copy');
select pg_temp.api_assert((select balance=75 from public.shard_balances where user_id=auth.uid() and currency='ITEM_SHARD'),'authoritative Item Shards');
select pg_temp.api_assert(not exists(select 1 from public.shard_balances where currency='PET_SHARD'),'no Pet Shards');
select pg_temp.api_assert((select coins=9500 from public.wallets where user_id=auth.uid()),'five charges only');
select pg_temp.api_assert((select bool_and(quantity=1) from public.user_inventory where user_id=auth.uid() and max_quantity=1),'unique copies preserved');

-- Exhausted test entropy queue means any redraw during recovery fails the test.
update public.item_case_banners set enabled=false;
update public.item_catalog set name='Changed after opening' where asset_id='acc-sprout';
select pg_temp.api_assert((select public.item_case_open('e2400000-0000-0000-0000-000000000001','fb270000-0000-0000-0000-000000000001')=result from api_first_response),'byte-equivalent JSON replay after name/balance/banner changes');
select pg_temp.api_assert(public.item_case_open(gen_random_uuid(),'fb270000-0000-0000-0000-000000000001')->>'status'='request_conflict','key cannot switch banner');
select pg_temp.api_assert(public.item_case_open('e2400000-0000-0000-0000-000000000001',gen_random_uuid())->>'status'='banner_unavailable','disabled banner');
update public.item_case_banners set enabled=true;
select pg_temp.api_assert(public.item_case_open(null,gen_random_uuid())->>'status'='invalid_request','null identifiers');
select set_config('request.jwt.claim.sub','fb260000-0000-0000-0000-000000000002',true);
select pg_temp.api_assert(public.item_case_open('e2400000-0000-0000-0000-000000000001','fb270000-0000-0000-0000-000000000001')->>'status'='insufficient_coins','same key from another user does not recover foreign result');
select pg_temp.api_assert(not exists(select 1 from public.item_case_openings where user_id=auth.uid()),'no receipt or debit on insufficient coins');
select set_config('request.jwt.claim.sub','fb260000-0000-0000-0000-000000000001',true);

-- Rate limit counts failed fresh attempts too; recovery is exempt and does not
-- consume or reset the quota. Other users have independent windows.
update pawquest_private.item_case_api_settings set max_attempts=2;
delete from pawquest_private.item_case_request_limits;
select pg_temp.api_assert(public.item_case_open(gen_random_uuid(),gen_random_uuid())->>'status'='banner_unavailable','failed attempt one');
select pg_temp.api_assert(public.item_case_open(gen_random_uuid(),gen_random_uuid())->>'status'='banner_unavailable','failed attempt two');
select pg_temp.api_assert(public.item_case_open('e2400000-0000-0000-0000-000000000001',gen_random_uuid())->>'status'='rate_limited','new attempts throttled');
select pg_temp.api_assert((select public.item_case_open('e2400000-0000-0000-0000-000000000001','fb270000-0000-0000-0000-000000000001')=result from api_first_response),'recovery bypasses throttle');
update pawquest_private.item_case_request_limits set window_started_at=clock_timestamp()-interval '61 seconds';
select pg_temp.api_assert(public.item_case_open(gen_random_uuid(),gen_random_uuid())->>'status'='banner_unavailable','window expires');
update pawquest_private.item_case_api_settings set max_attempts=30;
delete from pawquest_private.item_case_request_limits;

-- Fault injection proves the whole transaction rolls back on either grant path.
create function pg_temp.api_fail() returns trigger language plpgsql as $$ begin raise exception 'Injected API fault'; end $$;
create trigger api_fail before insert on public.user_inventory for each row execute function pg_temp.api_fail();
select set_config('request.jwt.claim.sub','fb260000-0000-0000-0000-000000000003',true);
select pg_temp.api_reject($q$select pg_temp.api_open(0,'fb270000-0000-0000-0000-000000000006')$q$,'P0001');
drop trigger api_fail on public.user_inventory;
select pg_temp.api_assert((select coins=1000 from public.wallets where user_id=auth.uid()),'grant failure rolls back debit');
select pg_temp.api_assert(not exists(select 1 from public.item_case_openings where user_id=auth.uid()),'grant failure rolls back receipt');
create trigger api_fail before insert on public.shard_ledger for each row execute function pg_temp.api_fail();
select set_config('request.jwt.claim.sub','fb260000-0000-0000-0000-000000000001',true);
select pg_temp.api_reject($q$select pg_temp.api_open(0,'fb270000-0000-0000-0000-000000000007')$q$,'P0001');
drop trigger api_fail on public.shard_ledger;
select pg_temp.api_assert((select coins=9500 from public.wallets where user_id=auth.uid()),'shard failure rolls back debit');
select pg_temp.api_assert((select balance=75 from public.shard_balances where user_id=auth.uid() and currency='ITEM_SHARD'),'shard failure no extra shards');
select pg_temp.api_assert(not exists(select 1 from public.wallet_transactions where request_id='fb270000-0000-0000-0000-000000000007'),'shard failure rolls back ledger');

-- Real authenticated-role execution; only the private test queue is preloaded.
insert into api_ticket_queue(ticket) values(0),(0);
set local role authenticated;
select pg_temp.api_assert(public.item_case_open('e2400000-0000-0000-0000-000000000001','fb270000-0000-0000-0000-000000000008')->'receipt'->>'reward_outcome'='DUPLICATE_CONVERTED','authenticated RPC permitted');
select pg_temp.api_reject($q$select public.item_case_open(p_banner_id=>'e2400000-0000-0000-0000-000000000001',p_request_id=>gen_random_uuid(),p_reward_id=>gen_random_uuid())$q$,'42883');
select pg_temp.api_reject($q$select public.item_case_open(p_banner_id=>'e2400000-0000-0000-0000-000000000001',p_request_id=>gen_random_uuid(),p_shard_amount=>999)$q$,'42883');
select pg_temp.api_reject($q$select pawquest_private.settle_item_case(auth.uid(),'e2400000-0000-0000-0000-000000000002','e2300000-0000-0000-0000-000000000001',gen_random_uuid())$q$,'42501');
select pg_temp.api_reject($q$select * from pawquest_private.item_case_response_snapshots$q$,'42501');
select pg_temp.api_reject($q$select pawquest_private.item_case_random_below(10000)$q$,'42501');
select pg_temp.api_reject($q$update public.shard_balances set balance=100000$q$,'42501');
select set_config('request.jwt.claim.sub','fb260000-0000-0000-0000-000000000002',true);
select pg_temp.api_assert(not exists(select 1 from public.item_case_openings),'cross-user history hidden');
select pg_temp.api_assert(not exists(select 1 from public.shard_ledger),'cross-user ledger hidden');
select set_config('request.jwt.claim.sub','',true);
select pg_temp.api_reject($q$select public.item_case_open('e2400000-0000-0000-0000-000000000001',gen_random_uuid())$q$,'42501');
reset role;
set local role anon;
select pg_temp.api_reject($q$select public.item_case_open('e2400000-0000-0000-0000-000000000001',gen_random_uuid())$q$,'42501');
reset role;

-- Future multi-copy furniture branch: the real catalog currently forbids it.
-- Relax that constraint ONLY inside this rolled-back fixture, never migration.
alter table public.item_catalog drop constraint item_catalog_check;
insert into public.item_catalog(id,code,name,category,price_coins,asset_id,max_quantity)
values('fb280000-0000-0000-0000-000000000001','test_multi_furniture','Test multi-copy furniture','FURNITURE',100,'test-multi-furniture',10);
insert into public.furniture_slots values('fb280000-0000-0000-0000-000000000001','PLANT');
insert into public.item_case_banners(id,code) values('fb290000-0000-0000-0000-000000000001','test_multi_case');
insert into public.item_case_versions(id,banner_id,version,price_coins) values('fb290000-0000-0000-0000-000000000002','fb290000-0000-0000-0000-000000000001',1,100);
insert into public.item_case_tiers select 'fb290000-0000-0000-0000-000000000002',rarity,probability_bps,duplicate_item_shards,redemption_item_shards
    from public.item_case_tiers where version_id='e2400000-0000-0000-0000-000000000002';
insert into public.item_case_pool(version_id,item_id,rarity,weight) values
('fb290000-0000-0000-0000-000000000002','fb280000-0000-0000-0000-000000000001','COMMON',3),
('fb290000-0000-0000-0000-000000000002','e2300000-0000-0000-0000-000000000005','RARE',1),
('fb290000-0000-0000-0000-000000000002','e2300000-0000-0000-0000-000000000002','EPIC',1),
('fb290000-0000-0000-0000-000000000002','e2300000-0000-0000-0000-000000000001','LEGENDARY',1);
-- Include a second Common with weight 1 to check exact nonuniform boundaries.
insert into public.item_case_pool(version_id,item_id,rarity,weight)
values('fb290000-0000-0000-0000-000000000002','e2300000-0000-0000-0000-000000000003','COMMON',1);
update public.item_case_versions set published_at=now() where id='fb290000-0000-0000-0000-000000000002';
update public.item_case_banners set current_version=1,enabled=true where id='fb290000-0000-0000-0000-000000000001';
select pg_temp.api_assert((select count(*)=3 from generate_series(0,3) n
    where pawquest_private.item_case_item_at('fb290000-0000-0000-0000-000000000002','COMMON',n)='fb280000-0000-0000-0000-000000000001'),'3:1 weight mapping');
-- The earlier UUID is the unique Common at ticket 0; multi-copy is tickets 1..3.
select set_config('request.jwt.claim.sub','fb260000-0000-0000-0000-000000000004',true);
insert into api_ticket_queue(ticket) values(0),(1);
select pg_temp.api_assert(public.item_case_open('fb290000-0000-0000-0000-000000000001',gen_random_uuid())->'receipt'->>'reward_outcome'='ITEM_GRANTED','first multi-copy item');
insert into api_ticket_queue(ticket) values(0),(3);
select pg_temp.api_assert(public.item_case_open('fb290000-0000-0000-0000-000000000001',gen_random_uuid())->'receipt'->>'reward_outcome'='ITEM_GRANTED','second legitimate furniture copy');
select pg_temp.api_assert((select quantity=2 from public.user_inventory where user_id=auth.uid() and item_id='fb280000-0000-0000-0000-000000000001'),'multi-copy quantity preserved');
select pg_temp.api_assert(not exists(select 1 from public.shard_balances where user_id=auth.uid()),'multi-copy grants no shards');
select pawquest_private.adjust_inventory('fb260000-0000-0000-0000-000000000004','fb280000-0000-0000-0000-000000000001',8);
insert into api_ticket_queue(ticket) values(0),(1);
select pg_temp.api_assert(public.item_case_open('fb290000-0000-0000-0000-000000000001',gen_random_uuid())->>'status'='inventory_full','full capacity rejects without reroll or conversion');
select pg_temp.api_assert((select coins=800 from public.wallets where user_id=auth.uid()),'full capacity no debit');

-- Restore real entropy, run one end-to-end authenticated cryptographic draw.
do $$ begin execute (select definition from api_original_functions where name='below'); end $$;
select pg_temp.api_assert(public.item_case_open('e2400000-0000-0000-0000-000000000001',gen_random_uuid())->>'status'='opened','end-to-end real entropy');
select pg_temp.api_assert((select p.pronargs=2 and p.proargnames=array['p_banner_id','p_request_id']::text[]
    from pg_proc p where p.oid='public.item_case_open(uuid,uuid)'::regprocedure),'only two accepted parameters');
set constraints all immediate;
rollback;
\echo PASS secure Item Case RPC, real cryptographic entropy, exact weights, all duplicate tiers, replay, rollback, auth/RLS and future multi-copy fixture
