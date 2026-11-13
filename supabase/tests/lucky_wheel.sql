-- Deterministic fixtures exist ONLY inside this rolled-back test transaction.
begin;
create or replace function pawquest_private.item_case_random_below(p_upper bigint) returns bigint
language plpgsql volatile security definer set search_path='' as $$ begin
 return coalesce(nullif(current_setting('pawquest.test_draw',true),''),'0')::bigint % p_upper;
end $$;
update pawquest_private.item_case_api_settings set max_attempts=600;
update public.lucky_wheel_banners set enabled=true;
insert into auth.users(id,email) values
 ('ab290000-0000-0000-0000-000000000001','wheel-one@example.invalid'),
 ('ab290000-0000-0000-0000-000000000002','wheel-two@example.invalid');
update public.wallets set coins=10000 where user_id='ab290000-0000-0000-0000-000000000001';
create function pg_temp.check_true(ok boolean,label text) returns void language plpgsql as $$ begin
 if ok is distinct from true then raise exception 'FAIL: %',label; end if;
end $$;
select pg_temp.check_true((select count(*)=1 and min(species_code)='mochi_cat' from public.user_pet_unlocks where user_id='ab290000-0000-0000-0000-000000000001'),'default Cat only');
-- Crossing old level thresholds grants NOTHING; old field is metadata only.
update public.pets set xp=1400 where user_id='ab290000-0000-0000-0000-000000000001';
select pg_temp.check_true((select count(*)=1 from public.user_pet_unlocks where user_id='ab290000-0000-0000-0000-000000000001'),'no level unlocks');
update public.pets set xp=0 where user_id='ab290000-0000-0000-0000-000000000001';
select pg_temp.check_true((select count(*)=8 and sum(weight)=10000 from public.lucky_wheel_sectors),'exact configuration total');
-- Every one of the 10,000 draw positions maps to the advertised weight exactly.
select pg_temp.check_true(not exists(
 select 1 from public.lucky_wheel_sectors s where s.weight<>(select count(*) from generate_series(0,9999) d
 where pawquest_private.wheel_sector_at(s.version_id,d)=s.sector_id)),'all cumulative probability boundaries');
do $$ begin
 begin update public.lucky_wheel_sectors set weight=999; raise exception 'FAIL mutable publication'; exception when raise_exception then if sqlerrm='FAIL mutable publication' then raise; end if; end;
 insert into public.lucky_wheel_versions(id,banner_id,version,price_coins,fallback_pet_shards)
 values('ab290000-0000-0000-0000-000000000010','e2800000-0000-0000-0000-000000000001',2,100,50);
 begin update public.lucky_wheel_versions set published_at=now() where version=2; raise exception 'FAIL invalid publication'; exception when check_violation then null; end;
 begin insert into public.lucky_wheel_sectors values('ab290000-0000-0000-0000-000000000010','FOOD',0,2500,1,gen_random_uuid()); raise exception 'FAIL invalid Food reference'; exception when foreign_key_violation then null; end;
 insert into public.lucky_wheel_sectors select 'ab290000-0000-0000-0000-000000000010',sector_id,position,weight,amount,food_item_id from public.lucky_wheel_sectors where version_id='e2800000-0000-0000-0000-000000000002';
 insert into public.pet_shard_prices select 'ab290000-0000-0000-0000-000000000010',species_code,price from public.pet_shard_prices where version_id='e2800000-0000-0000-0000-000000000002';
 update public.lucky_wheel_sectors set weight=2499 where version_id='ab290000-0000-0000-0000-000000000010' and sector_id='FOOD';
 begin update public.lucky_wheel_versions set published_at=now() where version=2; raise exception 'FAIL 9999 total accepted'; exception when check_violation then null; end;
end $$;
set local role authenticated;
select set_config('request.jwt.claim.sub','ab290000-0000-0000-0000-000000000001',true);
do $$
declare b uuid:='e2800000-0000-0000-0000-000000000001'; result jsonb; replay jsonb; key uuid;
 expected text[]:=array['FOOD','COINS','PET_SHARDS','PET_SHARDS','TICKET','PET_SHARDS','PET_SHARDS','PET_UNLOCKED'];
 draws integer[]:=array[0,2500,4500,6300,7500,8500,9300,9800];
 sectors text[]:=array['FOOD','COINS_50','PET_SHARDS_5','PET_SHARDS_10','LUCKY_TICKET_1','PET_SHARDS_20','PET_SHARDS_50','PET_UNLOCK'];
begin
 for n in 1..8 loop
  perform set_config('pawquest.test_draw',draws[n]::text,true); key:=gen_random_uuid();
  result:=public.lucky_wheel_spin(b,'COINS',key);
  perform pg_temp.check_true(result->>'status'='spun','sector spin '||n);
  perform pg_temp.check_true(result#>>'{receipt,original_sector_id}'=sectors[n],'original sector '||n);
  perform pg_temp.check_true(result#>>'{receipt,actual_reward_type}'=expected[n],'actual outcome '||n);
  replay:=public.lucky_wheel_spin(b,'COINS',key); perform pg_temp.check_true(result=replay,'immutable replay');
  perform pg_temp.check_true(public.lucky_wheel_spin(b,'TICKET',key)->>'status'='request_conflict','payment conflict');
 end loop;
 perform pg_temp.check_true((result#>>'{receipt,selected_pet_id}') in ('rabbit','puppy','capybara'),'real pet selected from three');
 perform pg_temp.check_true((public.lucky_wheel_config(b)->>'price_coins')::integer=100,'config price');
 perform pg_temp.check_true(jsonb_array_length(public.lucky_wheel_history())=8,'bounded owner history');
 perform pg_temp.check_true(jsonb_array_length(public.lucky_wheel_history(null,null,2))=2,'history limit');
 perform pg_temp.check_true(jsonb_array_length(public.pet_shard_catalog())=3,'Pet Exchange catalog');
 -- Ticket-paid Ticket win: full entry funds required, two gross legs, net zero.
 perform set_config('pawquest.test_draw','7500',true);
 result:=public.lucky_wheel_spin(b,'TICKET',gen_random_uuid());
 perform pg_temp.check_true(result->>'status'='spun' and (result#>>'{receipt,tickets_balance_after}')::bigint=1,'ticket net zero');
 perform set_config('pawquest.test_draw','2500',true);
 result:=public.lucky_wheel_spin(b,'TICKET',gen_random_uuid());
 perform pg_temp.check_true(result->>'status'='spun' and (result#>>'{receipt,coins_earned}')::integer=50 and (result#>>'{receipt,coins_spent}')::integer=0,'Ticket Coin prize');
 perform set_config('pawquest.test_draw','7500',true);
 perform pg_temp.check_true(public.lucky_wheel_spin(b,'TICKET',gen_random_uuid())->>'status'='insufficient_tickets','ticket prize cannot finance entry');
 perform pg_temp.check_true(public.lucky_wheel_spin(b,'AUTO',gen_random_uuid())->>'status'='invalid_request','explicit payment only');
 perform pg_temp.check_true(public.lucky_wheel_spin(b,'COINS',null)->>'status'='invalid_request','null key');
end $$;
reset role;
select pg_temp.check_true((select count(*)=2 and sum(coins)=-50 from public.wallet_transactions where lucky_wheel_spin_id=(select id from public.lucky_wheel_spins where payment_method='COINS' and sector_id='COINS_50')),'gross debit100 and credit50');
select pg_temp.check_true((select sum(delta)=85 from public.shard_ledger where user_id='ab290000-0000-0000-0000-000000000001' and currency='PET_SHARD'),'5+10+20+50 PET only');
select pg_temp.check_true(not exists(select 1 from public.shard_balances where currency='ITEM_SHARD' and user_id='ab290000-0000-0000-0000-000000000001'),'no Item Shard rewards');
select pg_temp.check_true((select quantity=4 from public.user_inventory where user_id='ab290000-0000-0000-0000-000000000001' and item_id='e1000000-0000-0000-0000-000000000001'),'existing Food increment');
-- Leave exactly one pet, then all-owned fallback; no random duplicate pet grant.
insert into public.user_pet_unlocks(user_id,species_code,source)
 select 'ab290000-0000-0000-0000-000000000001',code,'LEGACY' from public.pet_species where code in ('rabbit','puppy') on conflict do nothing;
-- First seeded draw picks rabbit (9800 % 3 = 2 in sorted [capybara,puppy,rabbit]).
alter table public.user_pet_unlocks disable trigger receipt_immutable;
delete from public.user_pet_unlocks where user_id='ab290000-0000-0000-0000-000000000001' and species_code='capybara';
alter table public.user_pet_unlocks enable trigger receipt_immutable;
set local role authenticated;
select set_config('pawquest.test_draw','9800',true);
do $$ declare r jsonb; begin
 r:=public.lucky_wheel_spin('e2800000-0000-0000-0000-000000000001','COINS',gen_random_uuid());
 perform pg_temp.check_true(r#>>'{receipt,selected_pet_id}'='capybara','one remaining pet');
 r:=public.lucky_wheel_spin('e2800000-0000-0000-0000-000000000001','COINS',gen_random_uuid());
 perform pg_temp.check_true(r#>>'{receipt,original_sector_id}'='PET_UNLOCK' and r#>>'{receipt,actual_reward_type}'='PET_UNLOCK_CONVERTED_TO_SHARDS'
 and (r#>>'{receipt,pet_shards_earned}')::integer=50 and r#>>'{receipt,selected_pet_id}' is null,'all-owned fallback preserves sector');
 perform public.pet_select_species('capybara');
 perform pg_temp.check_true(public.pet_collection()->>'active_code'='capybara','owned selection at level1');
end $$;
-- Cross-user history, balances, ownership and denial of direct privileged writes.
select set_config('request.jwt.claim.sub','ab290000-0000-0000-0000-000000000002',true);
select pg_temp.check_true(public.lucky_wheel_history()='[]'::jsonb,'cross-user history isolation');
select pg_temp.check_true((select count(*)=0 from public.lucky_wheel_spins),'RLS direct history isolation');
select pg_temp.check_true((select count(*)=1 from public.user_pet_unlocks),'RLS ownership isolation');
select pg_temp.check_true((select count(*)=0 from public.lucky_wheel_ticket_ledger),'RLS ticket ledger isolation');
do $$ begin
 perform pg_temp.check_true(public.lucky_wheel_spin('e2800000-0000-0000-0000-000000000001','COINS',gen_random_uuid())->>'status'='insufficient_coins','no Coins');
 perform pg_temp.check_true(public.pet_shard_quote('rabbit',gen_random_uuid())->>'status'='insufficient_shards','no Pet Shards');
 perform pg_temp.check_true(public.pet_shard_redeem('rabbit',gen_random_uuid())->>'status'='quote_required','explicit quote required');
 begin execute 'select public.lucky_wheel_spin(p_banner_id=>gen_random_uuid(),p_payment_method=>''COINS'',p_request_id=>gen_random_uuid(),p_reward=>''PET_UNLOCK'')';
 raise exception 'FAIL forged outcome accepted'; exception when undefined_function then null; end;
 begin update public.lucky_wheel_ticket_balances set balance=100; raise exception 'FAIL client tickets'; exception when insufficient_privilege then null; end;
 begin insert into public.user_pet_unlocks(user_id,species_code,source) values(auth.uid(),'rabbit','LEGACY'); raise exception 'FAIL client ownership'; exception when insufficient_privilege then null; end;
 begin update public.shard_balances set balance=100; raise exception 'FAIL client shards'; exception when insufficient_privilege then null; end;
 begin perform pawquest_private.item_case_random_below(10000); raise exception 'FAIL random permission'; exception when insufficient_privilege then null; end;
 begin perform public.pet_select_species('rabbit'); raise exception 'FAIL unowned selection'; exception when insufficient_privilege then null; end;
end $$;
reset role;
update public.wallets set coins=50 where user_id='ab290000-0000-0000-0000-000000000002';
set local role authenticated;
select set_config('pawquest.test_draw','2500',true);
select pg_temp.check_true(public.lucky_wheel_spin('e2800000-0000-0000-0000-000000000001','COINS',gen_random_uuid())->>'status'='insufficient_coins','Coin prize cannot finance entry');
reset role;
-- Pet redemption, prices and same-key recovery. Preserve unrelated Item Shards.
insert into public.shard_ledger(user_id,currency,request_id,reason,delta,balance_after,note) values
 ('ab290000-0000-0000-0000-000000000002','PET_SHARD',gen_random_uuid(),'ADMIN_ADJUSTMENT',210,0,'Test fixture'),
 ('ab290000-0000-0000-0000-000000000002','ITEM_SHARD',gen_random_uuid(),'ADMIN_ADJUSTMENT',25,0,'Test fixture');
set local role authenticated;
do $$ declare key uuid; q jsonb; r jsonb; pet text; cost integer; begin
 foreach pet in array array['rabbit','puppy','capybara'] loop
 key:=gen_random_uuid(); cost:=case pet when 'rabbit' then 50 when 'puppy' then 70 else 90 end;
 q:=public.pet_shard_quote(pet,key); perform pg_temp.check_true(q->>'status'='quoted' and (q#>>'{quote,price}')::integer=cost,'quoted pet price');
 r:=public.pet_shard_redeem(pet,key); perform pg_temp.check_true(r->>'status'='redeemed','pet redeemed');
 perform pg_temp.check_true(public.pet_shard_redeem(pet,key)=r and public.pet_shard_quote(pet,key)=r,'pet replay');
 perform pg_temp.check_true(public.pet_shard_quote(pet,gen_random_uuid())->>'status'='already_owned','duplicate redemption rejected');
 end loop;
 perform pg_temp.check_true(public.pet_shard_quote('mochi_cat',gen_random_uuid())->>'status'='pet_unavailable','Cat not redeemable');
end $$;
reset role;
select pg_temp.check_true((select balance=0 from public.shard_balances where user_id='ab290000-0000-0000-0000-000000000002' and currency='PET_SHARD'),'redemption balance');
select pg_temp.check_true((select balance=25 from public.shard_balances where user_id='ab290000-0000-0000-0000-000000000002' and currency='ITEM_SHARD'),'separate currencies');
-- Inject a fault AFTER payment and history; subtransaction must undo everything.
create function pg_temp.fail_inventory() returns trigger language plpgsql as $$ begin raise exception 'injected_failure'; end $$;
create trigger wheel_test_failure before update on public.user_inventory for each row execute function pg_temp.fail_inventory();
set local role authenticated;
select set_config('request.jwt.claim.sub','ab290000-0000-0000-0000-000000000001',true);
select set_config('pawquest.test_draw','0',true);
do $$ declare coins bigint; n integer; key uuid:=gen_random_uuid(); begin
 coins:=(public.lucky_wheel_balances()->>'coins')::bigint; select count(*) into n from public.lucky_wheel_spins;
 begin perform public.lucky_wheel_spin('e2800000-0000-0000-0000-000000000001','COINS',key); raise exception 'FAIL missing injected failure';
 exception when raise_exception then if sqlerrm<>'injected_failure' then raise; end if; end;
 perform pg_temp.check_true((public.lucky_wheel_balances()->>'coins')::bigint=coins and (select count(*)=n from public.lucky_wheel_spins),'complete rollback');
end $$;
reset role;
drop trigger wheel_test_failure on public.user_inventory;
-- Capacity rejection precedes RNG. An invalid fixture draw proves it is not evaluated.
update public.user_inventory set quantity=1000000 where user_id='ab290000-0000-0000-0000-000000000001' and item_id='e1000000-0000-0000-0000-000000000001';
set local role authenticated;
select set_config('pawquest.test_draw','not-an-integer',true);
select pg_temp.check_true(public.lucky_wheel_spin('e2800000-0000-0000-0000-000000000001','COINS',gen_random_uuid())->>'status'='inventory_full','capacity before RNG');
select set_config('request.jwt.claim.sub','',true);
do $$ begin
 begin perform public.lucky_wheel_balances(); raise exception 'FAIL missing identity'; exception when insufficient_privilege then null; end;
end $$;
reset role;
set constraints all immediate;
-- Auth is enforced on read and write APIs, not just by Android.
set local role anon;
do $$ begin
 begin perform public.lucky_wheel_balances(); raise exception 'FAIL anonymous read'; exception when insufficient_privilege then null; end;
 begin perform public.lucky_wheel_spin(gen_random_uuid(),'COINS',gen_random_uuid()); raise exception 'FAIL anonymous spin'; exception when insufficient_privilege then null; end;
end $$;
reset role;
rollback;
