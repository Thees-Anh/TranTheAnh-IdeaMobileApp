-- Lucky Wheel and Pet redemption. Additive; the seeded banner remains DISABLED.
begin;
-- Freeze wallet-serialized progression/selection during the one-time entitlement cutover.
lock table public.wallets in share row exclusive mode;
create table public.user_pet_unlocks (
 user_id uuid not null references public.profiles(id), species_code text not null references public.pet_species(code),
 source text not null check(source in ('DEFAULT','LEGACY','WHEEL','PET_REDEMPTION')),
 source_id uuid, created_at timestamptz not null default clock_timestamp(), primary key(user_id,species_code),
 check((source in ('DEFAULT','LEGACY') and source_id is null) or (source in ('WHEEL','PET_REDEMPTION') and source_id is not null))
);
-- Preserve all pre-transition selection rights, including the active appearance.
insert into public.user_pet_unlocks(user_id,species_code,source)
 select p.user_id,s.code,case when s.code='mochi_cat' then 'DEFAULT' else 'LEGACY' end
 from public.pets p cross join public.pet_species s where s.code='mochi_cat' or s.code=p.species_code
 or (s.published and p.xp/(pawquest_private.pet_care_balance()->>'xpPerLevel')::integer+1>=s.unlock_level);
create function pawquest_private.default_pet_ownership() returns trigger
language plpgsql security definer set search_path='' as $$ begin
 insert into public.user_pet_unlocks(user_id,species_code,source) values(new.user_id,'mochi_cat','DEFAULT') on conflict do nothing; return new;
end $$;
create trigger pet_default_ownership after insert on public.pets for each row execute function pawquest_private.default_pet_ownership();
create function pawquest_private.require_owned_pet_selection() returns trigger
language plpgsql security definer set search_path='' as $$ begin
 if not exists(select 1 from public.user_pet_unlocks where user_id=new.user_id and species_code=new.species_code) then
 raise exception 'Pet not owned' using errcode='42501'; end if; return new;
end $$;
-- Also protects an old in-flight selection function across the migration cutover.
create trigger pet_owned_selection before update of species_code on public.pets for each row
 when(new.species_code is distinct from old.species_code) execute function pawquest_private.require_owned_pet_selection();
create or replace function public.pet_collection() returns jsonb
language plpgsql security definer set search_path='' as $$
declare actor uuid:=auth.uid(); p public.pets%rowtype;
begin
 if actor is null then raise exception 'Authentication required' using errcode='42501'; end if;
 select * into p from public.pets where user_id=actor;
 if not found then raise exception 'Account not initialized' using errcode='23503'; end if;
 return jsonb_build_object('active_code',p.species_code,'level',p.xp/(pawquest_private.pet_care_balance()->>'xpPerLevel')::integer+1,
 'species',(select coalesce(jsonb_agg(jsonb_build_object('code',s.code,'name',s.name,
 -- Compatibility field only: no XP threshold remains in selection or grants.
 'unlock_level',s.unlock_level,'renderer_key',s.renderer_key,'unlocked',exists(
 select 1 from public.user_pet_unlocks u where u.user_id=actor and u.species_code=s.code)) order by s.code),'[]'::jsonb)
 from public.pet_species s where s.published));
end $$;
create or replace function public.pet_select_species(p_code text) returns jsonb
language plpgsql security definer set search_path='' as $$
declare actor uuid:=auth.uid(); p public.pets%rowtype; species public.pet_species%rowtype;
begin
 if actor is null then raise exception 'Authentication required' using errcode='42501'; end if;
 perform 1 from public.wallets where user_id=actor for update;
 if not found then raise exception 'Account not initialized' using errcode='23503'; end if;
 select * into p from public.pets where user_id=actor for update;
 select * into species from public.pet_species where code=p_code and published for share;
 if not found then raise exception 'Pet unavailable' using errcode='23514'; end if;
 if not exists(select 1 from public.user_pet_unlocks where user_id=actor and species_code=p_code) then
 raise exception 'Pet not owned' using errcode='42501'; end if;
 if p.species_code<>species.code then update public.pets set species_code=species.code,renderer_key=species.renderer_key,name=species.name where id=p.id; end if;
 return public.pet_state();
end $$;
comment on table public.pet_species is 'Published appearances. unlock_level is legacy metadata, never an ownership grant or selection gate.';

create table public.lucky_wheel_banners (
 id uuid primary key default gen_random_uuid(), code text not null unique, enabled boolean not null default false, current_version integer
);
create table public.lucky_wheel_versions (
 id uuid primary key default gen_random_uuid(), banner_id uuid not null references public.lucky_wheel_banners(id),
 version integer not null check(version>0), price_coins integer not null check(price_coins between 1 and 1000000),
 fallback_pet_shards integer not null check(fallback_pet_shards between 1 and 1000000), published_at timestamptz,
 unique(banner_id,version)
);
alter table public.lucky_wheel_banners add foreign key(id,current_version) references public.lucky_wheel_versions(banner_id,version);
create table public.lucky_wheel_sectors (
 version_id uuid not null references public.lucky_wheel_versions(id), sector_id text not null,
 position integer not null check(position between 0 and 7), weight integer not null check(weight between 1 and 10000),
 amount integer not null check(amount between 1 and 1000000), food_item_id uuid references public.item_catalog(id),
 primary key(version_id,sector_id), unique(version_id,position),
 check(sector_id=(array['FOOD','COINS_50','PET_SHARDS_5','PET_SHARDS_10','LUCKY_TICKET_1','PET_SHARDS_20','PET_SHARDS_50','PET_UNLOCK'])[position+1]),
 check((sector_id='FOOD')=(food_item_id is not null)), check(sector_id<>'PET_UNLOCK' or amount=1)
);
-- Shared Exchange currency, species-specific targets; no second shard wallet.
create table public.pet_shard_prices (
 version_id uuid not null references public.lucky_wheel_versions(id), species_code text not null references public.pet_species(code),
 price integer not null check(price between 1 and 1000000), primary key(version_id,species_code),
 check(species_code in ('rabbit','puppy','capybara'))
);
create function pawquest_private.wheel_config_guard() returns trigger
language plpgsql security definer set search_path='' as $$
declare v public.lucky_wheel_versions%rowtype; vid uuid;
begin
 if tg_table_name in ('lucky_wheel_sectors','pet_shard_prices') then
  vid:=case when tg_op='DELETE' then old.version_id else new.version_id end;
  if tg_op='UPDATE' and new.version_id<>old.version_id then raise exception 'Cannot move configuration'; end if;
  select * into v from public.lucky_wheel_versions where id=vid for update;
  if v.published_at is not null then raise exception 'Published configuration is immutable'; end if;
 elsif tg_table_name='lucky_wheel_versions' then
  if tg_op<>'INSERT' and old.published_at is not null then raise exception 'Published configuration is immutable'; end if;
  if tg_op<>'DELETE' and new.published_at is not null then
   if tg_op='INSERT' then raise exception 'Publish a populated draft'; end if;
   if (select count(*)<>8 or sum(weight)<>10000 from public.lucky_wheel_sectors where version_id=new.id)
    or (select count(*)<>3 from public.pet_shard_prices where version_id=new.id)
    or exists(select 1 from public.pet_shard_prices q join public.pet_species s on s.code=q.species_code where q.version_id=new.id and not s.published)
    or exists(select 1 from public.lucky_wheel_sectors r join public.item_catalog i on i.id=r.food_item_id
      where r.version_id=new.id and (i.category<>'FOOD' or not i.active or r.amount>i.max_quantity)) then
    raise exception 'Invalid wheel configuration' using errcode='23514'; end if;
  end if;
 else
  if new.current_version is not null and not exists(select 1 from public.lucky_wheel_versions where banner_id=new.id and version=new.current_version and published_at is not null) then
   raise exception 'Banner must point to published configuration'; end if;
 end if;
 if tg_op='DELETE' then return old; else return new; end if;
end $$;
create trigger wheel_sector_guard before insert or update or delete on public.lucky_wheel_sectors for each row execute function pawquest_private.wheel_config_guard();
create trigger pet_price_guard before insert or update or delete on public.pet_shard_prices for each row execute function pawquest_private.wheel_config_guard();
create trigger wheel_version_guard before insert or update or delete on public.lucky_wheel_versions for each row execute function pawquest_private.wheel_config_guard();
create trigger wheel_banner_guard before insert or update on public.lucky_wheel_banners for each row execute function pawquest_private.wheel_config_guard();

create table pawquest_private.wheel_requests (
 user_id uuid not null references public.profiles(id), request_id uuid not null,
 operation text not null check(operation in ('WHEEL','PET_REDEMPTION')), primary key(user_id,request_id)
);
create table public.lucky_wheel_ticket_balances (
 user_id uuid primary key references public.profiles(id), balance bigint not null default 0 check(balance>=0)
);
create table public.lucky_wheel_spins (
 id uuid primary key, user_id uuid not null references public.profiles(id), request_id uuid not null,
 version_id uuid not null, sector_id text not null, payment_method text not null check(payment_method in ('COINS','TICKET')),
 coins_spent integer not null check(coins_spent>=0), tickets_spent integer not null check(tickets_spent in (0,1)),
 coins_earned integer not null check(coins_earned>=0), pet_shards_earned integer not null check(pet_shards_earned>=0),
 tickets_earned integer not null check(tickets_earned>=0), food_item_id uuid references public.item_catalog(id),
 food_quantity integer not null check(food_quantity>=0), selected_pet_id text references public.pet_species(code),
 actual_reward_type text not null check(actual_reward_type in ('FOOD','COINS','PET_SHARDS','TICKET','PET_UNLOCKED','PET_UNLOCK_CONVERTED_TO_SHARDS')),
 coins_balance_after bigint not null check(coins_balance_after>=0), pet_shards_balance_after bigint not null check(pet_shards_balance_after>=0),
 tickets_balance_after bigint not null check(tickets_balance_after>=0), created_at timestamptz not null default clock_timestamp(),
 unique(user_id,request_id), foreign key(version_id,sector_id) references public.lucky_wheel_sectors(version_id,sector_id),
 check((payment_method='COINS' and coins_spent>0 and tickets_spent=0) or (payment_method='TICKET' and coins_spent=0 and tickets_spent=1)),
 check((actual_reward_type='FOOD' and sector_id='FOOD' and food_item_id is not null and food_quantity>0 and selected_pet_id is null and coins_earned=0 and pet_shards_earned=0 and tickets_earned=0)
 or (actual_reward_type='COINS' and sector_id='COINS_50' and food_item_id is null and food_quantity=0 and selected_pet_id is null and coins_earned>0 and pet_shards_earned=0 and tickets_earned=0)
 or (actual_reward_type='PET_SHARDS' and sector_id in ('PET_SHARDS_5','PET_SHARDS_10','PET_SHARDS_20','PET_SHARDS_50') and food_item_id is null and food_quantity=0 and selected_pet_id is null and coins_earned=0 and pet_shards_earned>0 and tickets_earned=0)
 or (actual_reward_type='TICKET' and sector_id='LUCKY_TICKET_1' and food_item_id is null and food_quantity=0 and selected_pet_id is null and coins_earned=0 and pet_shards_earned=0 and tickets_earned>0)
 or (actual_reward_type='PET_UNLOCKED' and sector_id='PET_UNLOCK' and food_item_id is null and food_quantity=0 and selected_pet_id is not null and selected_pet_id in ('rabbit','puppy','capybara') and coins_earned=0 and pet_shards_earned=0 and tickets_earned=0)
 or (actual_reward_type='PET_UNLOCK_CONVERTED_TO_SHARDS' and sector_id='PET_UNLOCK' and food_item_id is null and food_quantity=0 and selected_pet_id is null and coins_earned=0 and pet_shards_earned>0 and tickets_earned=0))
);
create index wheel_history on public.lucky_wheel_spins(user_id,created_at desc,id desc);
create table public.pet_shard_redemptions (
 id uuid primary key, user_id uuid not null references public.profiles(id), request_id uuid not null,
 version_id uuid not null, species_code text not null, shards_spent integer not null check(shards_spent>0),
 pet_shards_balance_after bigint not null check(pet_shards_balance_after>=0), created_at timestamptz not null default clock_timestamp(),
 unique(user_id,request_id), foreign key(version_id,species_code) references public.pet_shard_prices(version_id,species_code)
);
create index pet_redemption_history on public.pet_shard_redemptions(user_id,created_at desc,id desc);
create table pawquest_private.pet_shard_quotes (
 user_id uuid not null references public.profiles(id), request_id uuid not null, version_id uuid not null,
 species_code text not null, price integer not null check(price>0), expires_at timestamptz not null,
 primary key(user_id,request_id), foreign key(version_id,species_code) references public.pet_shard_prices(version_id,species_code)
);
create table public.lucky_wheel_ticket_ledger (
 id uuid primary key default gen_random_uuid(), user_id uuid not null references public.profiles(id),
 spin_id uuid not null references public.lucky_wheel_spins(id), leg text not null check(leg in ('PAYMENT','REWARD')),
 delta integer not null check(delta<>0), balance_after bigint not null check(balance_after>=0), created_at timestamptz not null default clock_timestamp(),
 unique(spin_id,leg)
);
create index wheel_ticket_history on public.lucky_wheel_ticket_ledger(user_id,created_at desc,id desc);

-- Keep gross Coin debit and prize credit as separate, source-linked ledger legs.
alter table public.wallet_transactions add column lucky_wheel_spin_id uuid references public.lucky_wheel_spins(id),
 add column wheel_leg text check(wheel_leg in ('PAYMENT','REWARD')),
 add constraint wheel_coin_leg_unique unique(lucky_wheel_spin_id,wheel_leg);
alter table public.wallet_transactions drop constraint ledger_shape;
alter table public.wallet_transactions add constraint ledger_shape check(
 (lucky_wheel_spin_id is null and wheel_leg is null and (
 (kind='quest_reward' and item_case_opening_id is null and quest_session_id is not null and reward_day is not null and reward_slot is not null
 and coins between 0 and 80 and pet_xp in (5,10,20,30,40,50) and balance_after>=coins and request_id is null and item_id is null and quantity is null and unit_price is null)
 or (kind='shop_purchase' and item_case_opening_id is null and quest_session_id is null and reward_day is null and reward_slot is null
 and pet_xp=0 and request_id is not null and item_id is not null and quantity between 1 and 100 and quantity is not null
 and unit_price between 0 and 1000000 and unit_price is not null and coins=-(quantity::bigint*unit_price))
 or (kind='item_case_opening' and item_case_opening_id is not null and quest_session_id is null and reward_day is null and reward_slot is null
 and pet_xp=0 and request_id is not null and item_id is not null and quantity=1 and unit_price between 1 and 1000000 and unit_price is not null and coins=-unit_price::bigint)))
 or (kind='lucky_wheel' and lucky_wheel_spin_id is not null and wheel_leg is not null and item_case_opening_id is null and quest_session_id is null
 and reward_day is null and reward_slot is null and pet_xp=0 and request_id is not null and item_id is null and quantity is null and unit_price is null
 and ((wheel_leg='PAYMENT' and coins<0) or (wheel_leg='REWARD' and coins>0)))
);
create function pawquest_private.prepare_wheel_coin() returns trigger
language plpgsql security definer set search_path='' as $$
declare s public.lucky_wheel_spins%rowtype; amount bigint;
begin
 select coins into amount from public.wallets where user_id=new.user_id for update;
 select * into s from public.lucky_wheel_spins where id=new.lucky_wheel_spin_id;
 if not found or s.user_id<>new.user_id or (new.wheel_leg='PAYMENT' and s.coins_spent=0) or (new.wheel_leg='REWARD' and s.coins_earned=0) then
 raise exception 'Wheel Coin source mismatch' using errcode='23514'; end if;
 new.coins:=case new.wheel_leg when 'PAYMENT' then -s.coins_spent else s.coins_earned end;
 new.balance_after:=amount+new.coins; new.pet_xp:=0; return new;
end $$;
create trigger wheel_coin_prepare before insert on public.wallet_transactions for each row when(new.kind='lucky_wheel') execute function pawquest_private.prepare_wheel_coin();
create trigger wheel_coin_apply after insert on public.wallet_transactions for each row when(new.kind='lucky_wheel') execute function pawquest_private.apply_item_case_debit();

alter table public.shard_ledger add column lucky_wheel_spin_id uuid unique references public.lucky_wheel_spins(id),
 add column pet_redemption_id uuid unique references public.pet_shard_redemptions(id);
alter table public.shard_ledger drop constraint shard_ledger_reason_check, drop constraint shard_ledger_check;
alter table public.shard_ledger add check(reason in ('ITEM_DUPLICATE','ITEM_REDEMPTION','ADMIN_ADJUSTMENT','WHEEL_REWARD','PET_REDEMPTION')),
 add check(
 (lucky_wheel_spin_id is null and pet_redemption_id is null and (
 (reason='ITEM_DUPLICATE' and currency='ITEM_SHARD' and delta>0 and opening_id is not null and redemption_id is null and note is null)
 or (reason='ITEM_REDEMPTION' and currency='ITEM_SHARD' and delta<0 and opening_id is null and redemption_id is not null and note is null)
 or (reason='ADMIN_ADJUSTMENT' and opening_id is null and redemption_id is null and note is not null)))
 or (reason='WHEEL_REWARD' and currency='PET_SHARD' and delta>0 and lucky_wheel_spin_id is not null and pet_redemption_id is null and opening_id is null and redemption_id is null and note is null)
 or (reason='PET_REDEMPTION' and currency='PET_SHARD' and delta<0 and pet_redemption_id is not null and lucky_wheel_spin_id is null and opening_id is null and redemption_id is null and note is null)
 );
-- Existing Item prepare/apply triggers remain responsible for balances and Item receipt validation.
create function pawquest_private.validate_wheel_shard() returns trigger
language plpgsql security definer set search_path='' as $$
begin
 if new.reason='WHEEL_REWARD' and not exists(select 1 from public.lucky_wheel_spins s where s.id=new.lucky_wheel_spin_id
 and s.user_id=new.user_id and s.request_id=new.request_id and s.pet_shards_earned=new.delta) then raise exception 'Wheel shard source mismatch'; end if;
 if new.reason='PET_REDEMPTION' and not exists(select 1 from public.pet_shard_redemptions r where r.id=new.pet_redemption_id
 and r.user_id=new.user_id and r.request_id=new.request_id and r.shards_spent=-new.delta) then raise exception 'Pet shard source mismatch'; end if;
 return new;
end $$;
create trigger wheel_shard_validate before insert on public.shard_ledger for each row execute function pawquest_private.validate_wheel_shard();
create function pawquest_private.prepare_wheel_ticket() returns trigger
language plpgsql security definer set search_path='' as $$
declare s public.lucky_wheel_spins%rowtype; amount bigint;
begin
 perform 1 from public.wallets where user_id=new.user_id for update;
 select * into s from public.lucky_wheel_spins where id=new.spin_id;
 if not found or s.user_id<>new.user_id or (new.leg='PAYMENT' and s.tickets_spent=0) or (new.leg='REWARD' and s.tickets_earned=0) then raise exception 'Ticket source mismatch'; end if;
 insert into public.lucky_wheel_ticket_balances(user_id) values(new.user_id) on conflict do nothing;
 select balance into amount from public.lucky_wheel_ticket_balances where user_id=new.user_id for update;
 new.delta:=case new.leg when 'PAYMENT' then -s.tickets_spent else s.tickets_earned end;
 new.balance_after:=amount+new.delta; return new;
end $$;
create function pawquest_private.apply_wheel_ticket() returns trigger
language plpgsql security definer set search_path='' as $$ begin
 update public.lucky_wheel_ticket_balances set balance=new.balance_after where user_id=new.user_id; return new;
end $$;
create trigger wheel_ticket_prepare before insert on public.lucky_wheel_ticket_ledger for each row execute function pawquest_private.prepare_wheel_ticket();
create trigger wheel_ticket_apply after insert on public.lucky_wheel_ticket_ledger for each row execute function pawquest_private.apply_wheel_ticket();

-- Protect new operation request keys even when an old Shop/Case RPC is called afterwards.
create function pawquest_private.wheel_request_guard() returns trigger
language plpgsql security definer set search_path='' as $$
declare operation text;
begin
 perform 1 from public.wallets where user_id=new.user_id for update;
 select r.operation into operation from pawquest_private.wheel_requests r where r.user_id=new.user_id and r.request_id=new.request_id;
 if found then
  if tg_table_name='wallet_transactions' or not ((operation='WHEEL' and new.reason='WHEEL_REWARD') or (operation='PET_REDEMPTION' and new.reason='PET_REDEMPTION')) then
   raise exception 'Request belongs to another operation' using errcode='23505'; end if;
 end if; return new;
end $$;
-- Separate bodies avoid accessing shard-only fields on the Coins trigger record.
create function pawquest_private.wheel_coin_request_guard() returns trigger
language plpgsql security definer set search_path='' as $$ begin
 perform 1 from public.wallets where user_id=new.user_id for update;
 if exists(select 1 from pawquest_private.wheel_requests where user_id=new.user_id and request_id=new.request_id) then
 raise exception 'Request belongs to another operation' using errcode='23505'; end if; return new;
end $$;
create trigger wheel_request_guard before insert on public.shard_ledger for each row execute function pawquest_private.wheel_request_guard();
create trigger wheel_request_guard before insert on public.wallet_transactions for each row execute function pawquest_private.wheel_coin_request_guard();

create function pawquest_private.wheel_request_used(p_user uuid,p_request uuid) returns boolean
language sql security definer set search_path='' as $$ select
 exists(select 1 from pawquest_private.wheel_requests where user_id=p_user and request_id=p_request)
 or exists(select 1 from public.wallet_transactions where user_id=p_user and request_id=p_request)
 or exists(select 1 from public.shard_ledger where user_id=p_user and request_id=p_request)
 or exists(select 1 from pawquest_private.item_shard_quotes where user_id=p_user and request_id=p_request)
 or exists(select 1 from pawquest_private.pet_shard_quotes where user_id=p_user and request_id=p_request) $$;
create function pawquest_private.wheel_allow_request(p_user uuid) returns boolean
language plpgsql security definer set search_path='' as $$
declare limits pawquest_private.item_case_api_settings%rowtype; t pawquest_private.item_case_request_limits%rowtype;
begin
 select * into strict limits from pawquest_private.item_case_api_settings where singleton;
 select * into t from pawquest_private.item_case_request_limits where user_id=p_user;
 if not found or clock_timestamp()>=t.window_started_at+make_interval(secs=>limits.window_seconds) then
 insert into pawquest_private.item_case_request_limits values(p_user,clock_timestamp(),1) on conflict(user_id) do update set window_started_at=excluded.window_started_at,attempts=1;
 elsif t.attempts>=limits.max_attempts then return false;
 else update pawquest_private.item_case_request_limits set attempts=attempts+1 where user_id=p_user; end if; return true;
end $$;
create function pawquest_private.wheel_sector_at(p_version uuid,p_draw bigint) returns text
language plpgsql stable security definer set search_path='' as $$
declare result text;
begin
 if p_draw is null or p_draw<0 or p_draw>=10000 then raise exception 'Draw outside range'; end if;
 select sector_id into result from (select sector_id,position,sum(weight) over(order by position) boundary
 from public.lucky_wheel_sectors where version_id=p_version) x where p_draw<boundary order by position limit 1;
 if result is null then raise exception 'Invalid wheel configuration'; end if; return result;
end $$;
create function pawquest_private.wheel_response(p_spin uuid) returns jsonb
language sql stable security definer set search_path='' as $$
 select jsonb_build_object('status','spun','receipt',to_jsonb(s)||jsonb_build_object('transaction_id',s.id,
 'banner_id',v.banner_id,'configuration_version',v.version,'original_sector_id',s.sector_id))
 from public.lucky_wheel_spins s join public.lucky_wheel_versions v on v.id=s.version_id where s.id=p_spin $$;
create function pawquest_private.pet_redemption_response(p_id uuid) returns jsonb
language sql stable security definer set search_path='' as $$
 select jsonb_build_object('status','redeemed','receipt',to_jsonb(r)||jsonb_build_object('transaction_id',r.id)) from public.pet_shard_redemptions r where id=p_id $$;

create function public.lucky_wheel_spin(p_banner_id uuid,p_payment_method text,p_request_id uuid) returns jsonb
language plpgsql security definer set search_path='' set lock_timeout='5s' as $$
declare actor uuid:=auth.uid(); coins bigint; tickets bigint; shards bigint; v public.lucky_wheel_versions%rowtype;
 sector_reward public.lucky_wheel_sectors%rowtype; prior public.lucky_wheel_spins%rowtype; remaining text[];
 result public.lucky_wheel_spins%rowtype; food record; owned integer; winning_sector text;
begin
 if actor is null then raise exception 'Authentication required' using errcode='42501'; end if;
 if p_banner_id is null or p_request_id is null or p_payment_method is null or p_payment_method not in ('COINS','TICKET') then return jsonb_build_object('status','invalid_request'); end if;
 select w.coins into coins from public.wallets w where user_id=actor for update;
 if not found then return jsonb_build_object('status','account_unavailable'); end if;
 select * into prior from public.lucky_wheel_spins where user_id=actor and request_id=p_request_id;
 if found then
  if prior.payment_method<>p_payment_method or not exists(select 1 from public.lucky_wheel_versions where id=prior.version_id and banner_id=p_banner_id) then return jsonb_build_object('status','request_conflict'); end if;
  return pawquest_private.wheel_response(prior.id);
 end if;
 if pawquest_private.wheel_request_used(actor,p_request_id) then return jsonb_build_object('status','request_conflict'); end if;
 if not pawquest_private.wheel_allow_request(actor) then return jsonb_build_object('status','rate_limited'); end if;
 select c.* into v from public.lucky_wheel_banners b join public.lucky_wheel_versions c on c.banner_id=b.id and c.version=b.current_version
 where b.id=p_banner_id and b.enabled and c.published_at is not null for share of b,c;
 if not found then return jsonb_build_object('status','banner_unavailable'); end if;
 select coalesce((select balance from public.lucky_wheel_ticket_balances where user_id=actor),0) into tickets;
 select coalesce((select balance from public.shard_balances where user_id=actor and currency='PET_SHARD'),0) into shards;
 if p_payment_method='COINS' and coins<v.price_coins then return jsonb_build_object('status','insufficient_coins'); end if;
 if p_payment_method='TICKET' and tickets<1 then return jsonb_build_object('status','insufficient_tickets'); end if;
 -- Validate every possible destination BEFORE drawing. A capacity failure cannot create a free outcome reroll.
 select i.*,r.amount into food from public.lucky_wheel_sectors r join public.item_catalog i on i.id=r.food_item_id where r.version_id=v.id and r.sector_id='FOOD' for share of i;
 if not found or not food.active or food.category<>'FOOD' then return jsonb_build_object('status','configuration_unavailable'); end if;
 perform 1 from public.pet_species s join public.pet_shard_prices q on q.species_code=s.code where q.version_id=v.id for share of s;
 if exists(select 1 from public.pet_shard_prices q join public.pet_species s on s.code=q.species_code where q.version_id=v.id and not s.published) then return jsonb_build_object('status','configuration_unavailable'); end if;
 select quantity into owned from public.user_inventory where user_id=actor and item_id=food.id for update;
 if coalesce(owned,0)::bigint+food.amount>food.max_quantity then return jsonb_build_object('status','inventory_full'); end if;
 winning_sector:=pawquest_private.wheel_sector_at(v.id,pawquest_private.item_case_random_below(10000));
 select * into strict sector_reward from public.lucky_wheel_sectors where version_id=v.id and sector_id=winning_sector;
 result.id:=gen_random_uuid(); result.user_id:=actor; result.request_id:=p_request_id; result.version_id:=v.id; result.sector_id:=sector_reward.sector_id; result.payment_method:=p_payment_method;
 result.coins_spent:=case p_payment_method when 'COINS' then v.price_coins else 0 end;
 result.tickets_spent:=case p_payment_method when 'TICKET' then 1 else 0 end;
 result.coins_earned:=0; result.pet_shards_earned:=0; result.tickets_earned:=0; result.food_quantity:=0;
 case sector_reward.sector_id
 when 'FOOD' then result.actual_reward_type:='FOOD'; result.food_item_id:=sector_reward.food_item_id; result.food_quantity:=sector_reward.amount;
 when 'COINS_50' then result.actual_reward_type:='COINS'; result.coins_earned:=sector_reward.amount;
 when 'LUCKY_TICKET_1' then result.actual_reward_type:='TICKET'; result.tickets_earned:=sector_reward.amount;
 when 'PET_UNLOCK' then
  select array_agg(q.species_code order by q.species_code) into remaining from public.pet_shard_prices q where q.version_id=v.id
  and not exists(select 1 from public.user_pet_unlocks u where u.user_id=actor and u.species_code=q.species_code);
  if coalesce(cardinality(remaining),0)=0 then result.actual_reward_type:='PET_UNLOCK_CONVERTED_TO_SHARDS'; result.pet_shards_earned:=v.fallback_pet_shards;
  else result.actual_reward_type:='PET_UNLOCKED'; result.selected_pet_id:=remaining[1+pawquest_private.item_case_random_below(cardinality(remaining))::integer]; end if;
 else result.actual_reward_type:='PET_SHARDS'; result.pet_shards_earned:=sector_reward.amount;
 end case;
 result.coins_balance_after:=coins-result.coins_spent+result.coins_earned;
 result.pet_shards_balance_after:=shards+result.pet_shards_earned;
 result.tickets_balance_after:=tickets-result.tickets_spent+result.tickets_earned; result.created_at:=clock_timestamp();
 insert into pawquest_private.wheel_requests values(actor,p_request_id,'WHEEL');
 insert into public.lucky_wheel_spins select result.*;
 if result.coins_spent>0 then insert into public.wallet_transactions(user_id,kind,request_id,lucky_wheel_spin_id,wheel_leg)
 values(actor,'lucky_wheel',gen_random_uuid(),result.id,'PAYMENT'); end if;
 if result.coins_earned>0 then insert into public.wallet_transactions(user_id,kind,request_id,lucky_wheel_spin_id,wheel_leg)
 values(actor,'lucky_wheel',gen_random_uuid(),result.id,'REWARD'); end if;
 if result.tickets_spent>0 then insert into public.lucky_wheel_ticket_ledger(user_id,spin_id,leg,delta,balance_after) values(actor,result.id,'PAYMENT',-1,0); end if;
 if result.tickets_earned>0 then insert into public.lucky_wheel_ticket_ledger(user_id,spin_id,leg,delta,balance_after) values(actor,result.id,'REWARD',1,0); end if;
 if result.pet_shards_earned>0 then insert into public.shard_ledger(user_id,currency,request_id,reason,delta,balance_after,lucky_wheel_spin_id)
 values(actor,'PET_SHARD',p_request_id,'WHEEL_REWARD',result.pet_shards_earned,0,result.id); end if;
 if result.food_quantity>0 then perform pawquest_private.adjust_inventory(actor,result.food_item_id,result.food_quantity); end if;
 if result.selected_pet_id is not null then insert into public.user_pet_unlocks(user_id,species_code,source,source_id) values(actor,result.selected_pet_id,'WHEEL',result.id); end if;
 return pawquest_private.wheel_response(result.id);
exception when lock_not_available then return jsonb_build_object('status','busy');
end $$;

create function public.pet_shard_quote(p_species_code text,p_request_id uuid) returns jsonb
language plpgsql security definer set search_path='' set lock_timeout='5s' as $$
declare actor uuid:=auth.uid(); q pawquest_private.pet_shard_quotes%rowtype; prior public.pet_shard_redemptions%rowtype; reward record; balance bigint;
begin
 if actor is null then raise exception 'Authentication required' using errcode='42501'; end if;
 if p_species_code is null or p_request_id is null then return jsonb_build_object('status','invalid_request'); end if;
 perform 1 from public.wallets where user_id=actor for update;
 if not found then return jsonb_build_object('status','account_unavailable'); end if;
 select * into prior from public.pet_shard_redemptions where user_id=actor and request_id=p_request_id;
 if found then if prior.species_code<>p_species_code then return jsonb_build_object('status','request_conflict'); end if; return pawquest_private.pet_redemption_response(prior.id); end if;
 if exists(select 1 from pawquest_private.wheel_requests where user_id=actor and request_id=p_request_id)
 or exists(select 1 from public.wallet_transactions where user_id=actor and request_id=p_request_id)
 or exists(select 1 from public.shard_ledger where user_id=actor and request_id=p_request_id) then return jsonb_build_object('status','request_conflict'); end if;
 select * into q from pawquest_private.pet_shard_quotes where user_id=actor and request_id=p_request_id;
 if found then if q.species_code<>p_species_code then return jsonb_build_object('status','request_conflict'); end if;
 else
  if pawquest_private.wheel_request_used(actor,p_request_id) then return jsonb_build_object('status','request_conflict'); end if;
  if not pawquest_private.wheel_allow_request(actor) then return jsonb_build_object('status','rate_limited'); end if;
  select r.* into reward from public.lucky_wheel_banners b join public.lucky_wheel_versions v on v.banner_id=b.id and v.version=b.current_version
  join public.pet_shard_prices r on r.version_id=v.id join public.pet_species s on s.code=r.species_code
  where b.code='lucky_wheel' and v.published_at is not null and s.published and r.species_code=p_species_code for share of b,v,s;
  if not found then return jsonb_build_object('status','pet_unavailable'); end if;
  if exists(select 1 from public.user_pet_unlocks where user_id=actor and species_code=p_species_code) then return jsonb_build_object('status','already_owned'); end if;
  select coalesce((select s.balance from public.shard_balances s where s.user_id=actor and currency='PET_SHARD'),0) into balance;
  if balance<reward.price then return jsonb_build_object('status','insufficient_shards'); end if;
  insert into pawquest_private.pet_shard_quotes values(actor,p_request_id,reward.version_id,p_species_code,reward.price,clock_timestamp()+interval '10 minutes') returning * into q;
 end if;
 return jsonb_build_object('status','quoted','quote',to_jsonb(q)-'user_id');
exception when lock_not_available then return jsonb_build_object('status','busy');
end $$;
create function public.pet_shard_redeem(p_species_code text,p_request_id uuid) returns jsonb
language plpgsql security definer set search_path='' set lock_timeout='5s' as $$
declare actor uuid:=auth.uid(); q pawquest_private.pet_shard_quotes%rowtype; prior public.pet_shard_redemptions%rowtype; balance bigint; reward record;
begin
 if actor is null then raise exception 'Authentication required' using errcode='42501'; end if;
 if p_species_code is null or p_request_id is null then return jsonb_build_object('status','invalid_request'); end if;
 perform 1 from public.wallets where user_id=actor for update;
 if not found then return jsonb_build_object('status','account_unavailable'); end if;
 select * into prior from public.pet_shard_redemptions where user_id=actor and request_id=p_request_id;
 if found then if prior.species_code<>p_species_code then return jsonb_build_object('status','request_conflict'); end if; return pawquest_private.pet_redemption_response(prior.id); end if;
 select * into q from pawquest_private.pet_shard_quotes where user_id=actor and request_id=p_request_id;
 if not found then return jsonb_build_object('status','quote_required'); end if;
 if q.species_code<>p_species_code then return jsonb_build_object('status','request_conflict'); end if;
 if clock_timestamp()>q.expires_at then return jsonb_build_object('status','quote_expired'); end if;
 select r.* into reward from public.lucky_wheel_banners b join public.lucky_wheel_versions v on v.banner_id=b.id and v.version=b.current_version
 join public.pet_shard_prices r on r.version_id=v.id join public.pet_species s on s.code=r.species_code
 where b.code='lucky_wheel' and v.published_at is not null and s.published and r.species_code=p_species_code for share of b,v,s;
 if not found then return jsonb_build_object('status','pet_unavailable'); end if;
 if reward.version_id<>q.version_id or reward.price<>q.price then return jsonb_build_object('status','price_changed'); end if;
 if exists(select 1 from public.user_pet_unlocks where user_id=actor and species_code=p_species_code) then return jsonb_build_object('status','already_owned'); end if;
 if exists(select 1 from pawquest_private.wheel_requests where user_id=actor and request_id=p_request_id)
 or exists(select 1 from public.wallet_transactions where user_id=actor and request_id=p_request_id)
 or exists(select 1 from public.shard_ledger where user_id=actor and request_id=p_request_id) then return jsonb_build_object('status','request_conflict'); end if;
 select s.balance into balance from public.shard_balances s where s.user_id=actor and currency='PET_SHARD' for update;
 if coalesce(balance,0)<q.price then return jsonb_build_object('status','insufficient_shards'); end if;
 insert into pawquest_private.wheel_requests values(actor,p_request_id,'PET_REDEMPTION');
 insert into public.pet_shard_redemptions(id,user_id,request_id,version_id,species_code,shards_spent,pet_shards_balance_after)
 values(gen_random_uuid(),actor,p_request_id,q.version_id,p_species_code,q.price,balance-q.price) returning * into prior;
 insert into public.shard_ledger(user_id,currency,request_id,reason,delta,balance_after,pet_redemption_id)
 values(actor,'PET_SHARD',p_request_id,'PET_REDEMPTION',-q.price,0,prior.id);
 insert into public.user_pet_unlocks(user_id,species_code,source,source_id) values(actor,p_species_code,'PET_REDEMPTION',prior.id);
 return pawquest_private.pet_redemption_response(prior.id);
exception when lock_not_available then return jsonb_build_object('status','busy');
end $$;

-- Deferred reverse links make incomplete/forged ledger or entitlement settlement fail at COMMIT.
create function pawquest_private.check_wheel_receipt() returns trigger
language plpgsql security definer set search_path='' as $$
begin
 if tg_table_name='lucky_wheel_spins' then
  if (select count(*) from public.wallet_transactions where lucky_wheel_spin_id=new.id)<>((new.coins_spent>0)::integer+(new.coins_earned>0)::integer)
   or (select coalesce(sum(coins),0) from public.wallet_transactions where lucky_wheel_spin_id=new.id)<>new.coins_earned-new.coins_spent
   or (select count(*) from public.lucky_wheel_ticket_ledger where spin_id=new.id)<>((new.tickets_spent>0)::integer+(new.tickets_earned>0)::integer)
   or (select coalesce(sum(delta),0) from public.lucky_wheel_ticket_ledger where spin_id=new.id)<>new.tickets_earned-new.tickets_spent
   or (new.pet_shards_earned>0 and not exists(select 1 from public.shard_ledger where lucky_wheel_spin_id=new.id and balance_after=new.pet_shards_balance_after))
   or (new.selected_pet_id is not null and not exists(select 1 from public.user_pet_unlocks where user_id=new.user_id and species_code=new.selected_pet_id and source='WHEEL' and source_id=new.id)) then
   raise exception 'Incomplete wheel settlement' using errcode='23514'; end if;
 else
  if not exists(select 1 from public.shard_ledger where pet_redemption_id=new.id and balance_after=new.pet_shards_balance_after)
  or not exists(select 1 from public.user_pet_unlocks where user_id=new.user_id and species_code=new.species_code and source='PET_REDEMPTION' and source_id=new.id) then
  raise exception 'Incomplete Pet redemption' using errcode='23514'; end if;
 end if; return new;
end $$;
create constraint trigger wheel_receipt_links after insert on public.lucky_wheel_spins deferrable initially deferred for each row execute function pawquest_private.check_wheel_receipt();
create constraint trigger pet_receipt_links after insert on public.pet_shard_redemptions deferrable initially deferred for each row execute function pawquest_private.check_wheel_receipt();

create function public.lucky_wheel_balances() returns jsonb
language plpgsql stable security definer set search_path='' as $$
declare actor uuid:=auth.uid(); result jsonb;
begin
 if actor is null then raise exception 'Authentication required' using errcode='42501'; end if;
 select jsonb_build_object('coins',w.coins,'tickets',coalesce(t.balance,0),'pet_shards',coalesce(s.balance,0)) into result
 from public.wallets w left join public.lucky_wheel_ticket_balances t using(user_id)
 left join public.shard_balances s on s.user_id=w.user_id and s.currency='PET_SHARD' where w.user_id=actor;
 return result;
end $$;
create function public.lucky_wheel_config(p_banner_id uuid) returns jsonb
language plpgsql stable security definer set search_path='' as $$
begin
 if auth.uid() is null then raise exception 'Authentication required' using errcode='42501'; end if;
 return (select jsonb_build_object('banner_id',b.id,'enabled',b.enabled,'version_id',v.id,'version',v.version,'price_coins',v.price_coins,
 'ticket_cost',1,'fallback_pet_shards',v.fallback_pet_shards,'sectors',(select jsonb_agg(to_jsonb(r)||jsonb_build_object('probability_bps',r.weight) order by position) from public.lucky_wheel_sectors r where r.version_id=v.id))
 from public.lucky_wheel_banners b join public.lucky_wheel_versions v on v.banner_id=b.id and v.version=b.current_version where b.id=p_banner_id and v.published_at is not null);
end $$;
create function public.lucky_wheel_history(p_before timestamptz default null,p_before_id uuid default null,p_limit integer default 20) returns jsonb
language plpgsql stable security definer set search_path='' as $$
begin
 if auth.uid() is null then raise exception 'Authentication required' using errcode='42501'; end if;
 if (p_before is null)<>(p_before_id is null) then raise exception 'Both cursor fields required'; end if;
 return (select coalesce(jsonb_agg(x.receipt order by x.created_at desc,x.id desc),'[]'::jsonb) from (
 select s.id,s.created_at,pawquest_private.wheel_response(s.id)->'receipt' receipt from public.lucky_wheel_spins s
 where user_id=auth.uid() and (p_before is null or (s.created_at,s.id)<(p_before,p_before_id))
 order by s.created_at desc,s.id desc limit greatest(1,least(coalesce(p_limit,20),50))) x);
end $$;
create function public.pet_shard_catalog() returns jsonb
language plpgsql stable security definer set search_path='' as $$
begin
 if auth.uid() is null then raise exception 'Authentication required' using errcode='42501'; end if;
 return (select coalesce(jsonb_agg(to_jsonb(r)||jsonb_build_object('name',s.name,'renderer_key',s.renderer_key,'owned',exists(
 select 1 from public.user_pet_unlocks u where u.user_id=auth.uid() and u.species_code=r.species_code)) order by r.price),'[]'::jsonb)
 from public.lucky_wheel_banners b join public.lucky_wheel_versions v on v.banner_id=b.id and v.version=b.current_version
 join public.pet_shard_prices r on r.version_id=v.id join public.pet_species s on s.code=r.species_code
 where b.code='lucky_wheel' and v.published_at is not null and s.published);
end $$;

-- RLS: configuration read-only; balances, ledger, ownership and history owner-read-only.
do $$ declare tbl text; begin
 foreach tbl in array array['user_pet_unlocks','lucky_wheel_ticket_balances','lucky_wheel_ticket_ledger','lucky_wheel_spins','pet_shard_redemptions'] loop
 execute format('alter table public.%I enable row level security',tbl);
 execute format('revoke all on public.%I from public,anon,authenticated,service_role',tbl);
 execute format('grant select on public.%I to authenticated',tbl);
 execute format('create policy owner_read on public.%I for select to authenticated using(user_id=(select auth.uid()))',tbl);
 if tbl not in ('lucky_wheel_ticket_balances') then
 execute format('create trigger receipt_immutable before update or delete on public.%I for each row execute function pawquest_private.reject_ledger_mutation()',tbl); end if;
 end loop;
 foreach tbl in array array['lucky_wheel_banners','lucky_wheel_versions','lucky_wheel_sectors','pet_shard_prices'] loop
 execute format('alter table public.%I enable row level security',tbl);
 execute format('revoke all on public.%I from public,anon,authenticated,service_role',tbl);
 end loop;
 foreach tbl in array array['wheel_requests','pet_shard_quotes'] loop
 execute format('alter table pawquest_private.%I enable row level security',tbl);
 execute format('revoke all on pawquest_private.%I from public,anon,authenticated,service_role',tbl);
 execute format('create trigger receipt_immutable before update or delete on pawquest_private.%I for each row execute function pawquest_private.reject_ledger_mutation()',tbl);
 end loop;
end $$;
-- Revoke all newly created helpers, including trigger functions. Existing grants unaffected.
do $$ declare f record; begin
 for f in select p.oid::regprocedure signature from pg_proc p join pg_namespace n on n.oid=p.pronamespace
 where n.nspname='pawquest_private' and p.proname in ('default_pet_ownership','require_owned_pet_selection','wheel_config_guard','prepare_wheel_coin','validate_wheel_shard',
 'prepare_wheel_ticket','apply_wheel_ticket','wheel_request_guard','wheel_coin_request_guard','wheel_request_used','wheel_allow_request',
 'wheel_sector_at','wheel_response','pet_redemption_response','check_wheel_receipt') loop
 execute format('revoke all on function %s from public,anon,authenticated,service_role',f.signature); end loop;
end $$;
revoke all on function public.lucky_wheel_spin(uuid,text,uuid),public.pet_shard_quote(text,uuid),public.pet_shard_redeem(text,uuid),
 public.lucky_wheel_config(uuid),public.lucky_wheel_balances(),public.lucky_wheel_history(timestamptz,uuid,integer),public.pet_shard_catalog()
 from public,anon,authenticated,service_role;
grant execute on function public.lucky_wheel_spin(uuid,text,uuid),public.pet_shard_quote(text,uuid),public.pet_shard_redeem(text,uuid),
 public.lucky_wheel_config(uuid),public.lucky_wheel_balances(),public.lucky_wheel_history(timestamptz,uuid,integer),public.pet_shard_catalog() to authenticated;

insert into public.lucky_wheel_banners(id,code) values('e2800000-0000-0000-0000-000000000001','lucky_wheel');
insert into public.lucky_wheel_versions(id,banner_id,version,price_coins,fallback_pet_shards)
 values('e2800000-0000-0000-0000-000000000002','e2800000-0000-0000-0000-000000000001',1,100,50);
insert into public.lucky_wheel_sectors(version_id,sector_id,position,weight,amount,food_item_id)
 select 'e2800000-0000-0000-0000-000000000002',code,pos,weight,amount,
 case when code='FOOD' then 'e1000000-0000-0000-0000-000000000001'::uuid end from (values
 ('FOOD',0,2500,1),('COINS_50',1,2000,50),('PET_SHARDS_5',2,1800,5),('PET_SHARDS_10',3,1200,10),
 ('LUCKY_TICKET_1',4,1000,1),('PET_SHARDS_20',5,800,20),('PET_SHARDS_50',6,500,50),('PET_UNLOCK',7,200,1)) x(code,pos,weight,amount);
insert into public.pet_shard_prices values('e2800000-0000-0000-0000-000000000002','rabbit',50),
 ('e2800000-0000-0000-0000-000000000002','puppy',70),('e2800000-0000-0000-0000-000000000002','capybara',90);
update public.lucky_wheel_versions set published_at=clock_timestamp() where id='e2800000-0000-0000-0000-000000000002';
update public.lucky_wheel_banners set current_version=1 where code='lucky_wheel';
notify pgrst,'reload schema';
commit;
