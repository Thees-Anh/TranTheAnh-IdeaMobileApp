-- Item Case data foundation. No public opening/redemption mutation RPC is exposed.
begin;

create table public.item_case_banners (
    id uuid primary key default gen_random_uuid(),
    code text not null unique check(code ~ '^[a-z][a-z0-9_]{1,63}$'),
    enabled boolean not null default false,
    current_version integer,
    created_at timestamptz not null default now()
);
create table public.item_case_versions (
    id uuid primary key default gen_random_uuid(),
    banner_id uuid not null references public.item_case_banners(id),
    version integer not null check(version>0),
    price_coins integer not null check(price_coins between 1 and 1000000),
    published_at timestamptz,
    created_at timestamptz not null default now(),
    unique(banner_id,version)
);
alter table public.item_case_banners add foreign key(id,current_version)
    references public.item_case_versions(banner_id,version);
create table public.item_case_tiers (
    version_id uuid not null references public.item_case_versions(id),
    rarity text not null check(rarity in ('COMMON','RARE','EPIC','LEGENDARY')),
    probability_bps integer not null check(probability_bps between 1 and 10000),
    duplicate_item_shards integer not null check(duplicate_item_shards between 1 and 1000000),
    redemption_item_shards integer not null check(redemption_item_shards between 1 and 1000000),
    primary key(version_id,rarity)
);
create table public.item_case_pool (
    version_id uuid not null,
    item_id uuid not null references public.item_catalog(id),
    rarity text not null,
    weight integer not null check(weight between 1 and 1000000),
    redeemable boolean not null default true,
    -- Snapshot identity for interpreting immutable historical versions.
    item_code text not null,
    asset_id text not null,
    category text not null check(category in ('ACCESSORY','FURNITURE')),
    max_quantity integer not null check(max_quantity>0),
    primary key(version_id,item_id),
    foreign key(version_id,rarity) references public.item_case_tiers(version_id,rarity)
);
create index item_case_pool_item on public.item_case_pool(item_id);

-- There is no generalized wallet: Coins remain in wallets. Shards never convert
-- currencies. Pet redemption is a disabled proposal, not an executable feature.
create table public.shard_currencies (
    code text primary key check(code in ('ITEM_SHARD','PET_SHARD')),
    proposed_pet_redemption_price integer check(proposed_pet_redemption_price>0),
    check(code='PET_SHARD' or proposed_pet_redemption_price is null)
);
insert into public.shard_currencies values('ITEM_SHARD',null),('PET_SHARD',50);
create table public.shard_balances (
    user_id uuid not null references public.profiles(id),
    currency text not null references public.shard_currencies(code),
    balance bigint not null default 0 check(balance>=0),
    updated_at timestamptz not null default now(),
    primary key(user_id,currency)
);

create table public.item_case_openings (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references public.profiles(id),
    request_id uuid not null,
    version_id uuid not null,
    item_id uuid not null,
    outcome text not null check(outcome in ('ITEM','ITEM_SHARDS')),
    inventory_before integer not null check(inventory_before>=0),
    quantity_granted integer not null check(quantity_granted in (0,1)),
    coins_spent integer not null check(coins_spent>0),
    coins_balance_after bigint not null check(coins_balance_after>=0),
    item_shards_granted integer not null check(item_shards_granted>=0),
    wallet_transaction_id uuid not null unique references public.wallet_transactions(id) deferrable initially deferred,
    shard_ledger_id uuid unique,
    created_at timestamptz not null default clock_timestamp(),
    unique(user_id,request_id),
    foreign key(version_id,item_id) references public.item_case_pool(version_id,item_id),
    check((outcome='ITEM' and quantity_granted=1 and item_shards_granted=0 and shard_ledger_id is null)
       or (outcome='ITEM_SHARDS' and inventory_before>0 and quantity_granted=0 and item_shards_granted>0 and shard_ledger_id is not null))
);
create index item_case_openings_history on public.item_case_openings(user_id,created_at desc,id);
create index item_case_openings_reward on public.item_case_openings(version_id,item_id);

-- Reserved receipt structure for future Item Shard redemption. No redemption
-- function or UI is introduced by this migration.
create table public.item_shard_redemptions (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references public.profiles(id),
    request_id uuid not null,
    version_id uuid not null,
    item_id uuid not null,
    shards_spent integer not null check(shards_spent>0),
    quantity_granted integer not null default 1 check(quantity_granted=1),
    shard_ledger_id uuid not null unique,
    created_at timestamptz not null default clock_timestamp(),
    unique(user_id,request_id),
    foreign key(version_id,item_id) references public.item_case_pool(version_id,item_id)
);
create index item_shard_redemptions_history on public.item_shard_redemptions(user_id,created_at desc,id);
create index item_shard_redemptions_reward on public.item_shard_redemptions(version_id,item_id);
create table public.shard_ledger (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references public.profiles(id),
    currency text not null references public.shard_currencies(code),
    request_id uuid not null,
    reason text not null check(reason in ('ITEM_DUPLICATE','ITEM_REDEMPTION','ADMIN_ADJUSTMENT')),
    delta bigint not null check(delta<>0),
    balance_after bigint not null check(balance_after>=0),
    opening_id uuid unique references public.item_case_openings(id),
    redemption_id uuid unique references public.item_shard_redemptions(id),
    note text check(length(note) between 1 and 500),
    created_at timestamptz not null default clock_timestamp(),
    unique(user_id,request_id),
    check((reason='ITEM_DUPLICATE' and currency='ITEM_SHARD' and delta>0 and opening_id is not null and redemption_id is null and note is null)
       or (reason='ITEM_REDEMPTION' and currency='ITEM_SHARD' and delta<0 and opening_id is null and redemption_id is not null and note is null)
       or (reason='ADMIN_ADJUSTMENT' and opening_id is null and redemption_id is null and note is not null))
);
create index shard_ledger_history on public.shard_ledger(user_id,currency,created_at desc,id);
alter table public.item_case_openings add foreign key(shard_ledger_id) references public.shard_ledger(id) deferrable initially deferred;
alter table public.item_shard_redemptions add foreign key(shard_ledger_id) references public.shard_ledger(id) deferrable initially deferred;

-- Serialize every draft edit and publication on the version row. Published
-- configuration is append-only: make a new version to rebalance any value.
create function pawquest_private.item_case_config_guard() returns trigger
language plpgsql security definer set search_path='' as $$
declare parent uuid; stamp timestamptz; item public.item_catalog%rowtype;
begin
    parent:=case when tg_op='DELETE' then old.version_id else new.version_id end;
    if tg_op='UPDATE' and new.version_id<>old.version_id then
        raise exception 'Cannot move configuration between versions' using errcode='23514'; end if;
    select published_at into stamp from public.item_case_versions where id=parent for update;
    if stamp is not null then raise exception 'Published configuration is immutable' using errcode='55000'; end if;
    if tg_table_name='item_case_pool' and tg_op<>'DELETE' then
        select * into item from public.item_catalog where id=new.item_id for share;
        if not found then raise exception 'Unknown catalog reward' using errcode='23503'; end if;
        if item.category not in ('ACCESSORY','FURNITURE') then
            raise exception 'Item Case only supports accessories and furniture' using errcode='23514'; end if;
        new.item_code:=item.code; new.asset_id:=item.asset_id;
        new.category:=item.category; new.max_quantity:=item.max_quantity;
    end if;
    if tg_op='DELETE' then return old; end if;
    return new;
end $$;
create trigger item_case_tier_guard before insert or update or delete on public.item_case_tiers
    for each row execute function pawquest_private.item_case_config_guard();
create trigger item_case_pool_guard before insert or update or delete on public.item_case_pool
    for each row execute function pawquest_private.item_case_config_guard();

create function pawquest_private.item_case_version_guard() returns trigger
language plpgsql security definer set search_path='' as $$
begin
    if tg_op<>'INSERT' and old.published_at is not null then
        raise exception 'Published version is immutable' using errcode='55000'; end if;
    if tg_op='DELETE' then return old; end if;
    if tg_op='UPDATE' and (new.id<>old.id or new.banner_id<>old.banner_id or new.version<>old.version) then
        raise exception 'Version identity is immutable' using errcode='23514'; end if;
    if new.published_at is not null then
        if tg_op='INSERT' then raise exception 'Insert a draft before publishing' using errcode='23514'; end if;
        if (select count(*)<>4 or sum(probability_bps)<>10000 from public.item_case_tiers where version_id=new.id) then
            raise exception 'Exactly four tiers totaling 10000 basis points required' using errcode='23514'; end if;
        if exists(select 1 from public.item_case_tiers t where t.version_id=new.id
            and not exists(select 1 from public.item_case_pool p where p.version_id=t.version_id and p.rarity=t.rarity)) then
            raise exception 'Every tier requires a nonempty weighted pool' using errcode='23514'; end if;
        -- Lock catalog rows during validation; never silently filter a published
        -- pool on read (that would change its advertised probabilities).
        perform 1 from public.item_catalog i join public.item_case_pool p on p.item_id=i.id where p.version_id=new.id for share of i;
        if exists(select 1 from public.item_case_pool p join public.item_catalog i on i.id=p.item_id
            where p.version_id=new.id and (not i.active or i.category<>p.category or i.asset_id<>p.asset_id
                or i.code<>p.item_code or i.max_quantity<>p.max_quantity
                or (i.category='ACCESSORY' and not exists(select 1 from public.accessory_slots s where s.item_id=i.id))
                or (i.category='FURNITURE' and not exists(select 1 from public.furniture_slots s where s.item_id=i.id)))) then
            raise exception 'Pool requires active, mapped, unchanged catalog items' using errcode='23514'; end if;
        new.published_at:=clock_timestamp();
    end if;
    return new;
end $$;
create trigger item_case_version_guard before insert or update or delete on public.item_case_versions
    for each row execute function pawquest_private.item_case_version_guard();
create function pawquest_private.item_case_banner_guard() returns trigger
language plpgsql security definer set search_path='' as $$
begin
    if (new.enabled or new.current_version is not null) and not exists(
        select 1 from public.item_case_versions where banner_id=new.id and version=new.current_version and published_at is not null) then
        raise exception 'Current banner version must be published' using errcode='23514'; end if;
    return new;
end $$;
create trigger item_case_banner_guard before insert or update on public.item_case_banners
    for each row execute function pawquest_private.item_case_banner_guard();

-- Exact rational odds: bps/10000 * item weight/tier total. Numeric probability
-- is for display; numerator/denominator preserve the precise interpretation.
create view public.item_case_probabilities with(security_invoker=true) as
select p.*,t.probability_bps,t.duplicate_item_shards,t.redemption_item_shards,
    (t.probability_bps::bigint*p.weight) as probability_numerator,
    (10000*sum(p.weight::bigint) over(partition by p.version_id,p.rarity)) as probability_denominator,
    (t.probability_bps::numeric*p.weight)/(10000*sum(p.weight::bigint) over(partition by p.version_id,p.rarity)) as effective_probability
from public.item_case_pool p join public.item_case_tiers t using(version_id,rarity);
create view public.item_shard_redemption_catalog with(security_invoker=true) as
select version_id,item_id,item_code,asset_id,category,rarity,'ITEM_SHARD'::text as currency,redemption_item_shards as price_item_shards
from public.item_case_probabilities where redeemable;

-- Validate reserved receipt pricing now, while leaving execution to a later
-- atomic redemption RPC. This trigger never consumes shards or grants items.
create function pawquest_private.prepare_item_redemption_receipt() returns trigger
language plpgsql security definer set search_path='' as $$
declare price integer; maximum integer; owned integer;
begin
    perform 1 from public.wallets where user_id=new.user_id for update;
    if not found then raise exception 'Wallet unavailable' using errcode='23503'; end if;
    select t.redemption_item_shards,i.max_quantity into price,maximum
        from public.item_case_pool p join public.item_case_tiers t using(version_id,rarity)
        join public.item_case_versions v on v.id=p.version_id join public.item_catalog i on i.id=p.item_id
        where p.version_id=new.version_id and p.item_id=new.item_id and p.redeemable
            and v.published_at is not null and i.active and i.max_quantity=p.max_quantity for share of i;
    if not found or price<>new.shards_spent then
        raise exception 'Redemption price or eligibility mismatch' using errcode='23514'; end if;
    select quantity into owned from public.user_inventory where user_id=new.user_id and item_id=new.item_id for update;
    if coalesce(owned,0)>=maximum then raise exception 'Redemption item already owned or full' using errcode='23514'; end if;
    return new;
end $$;
create trigger item_redemption_prepare before insert on public.item_shard_redemptions
    for each row execute function pawquest_private.prepare_item_redemption_receipt();

-- A missing balance row means zero. Rows are created only when posting a
-- ledger entry; no account initialization/backfill or Coins change is needed.
create function pawquest_private.prepare_shard_entry() returns trigger
language plpgsql security definer set search_path='' as $$
declare amount bigint; opening public.item_case_openings%rowtype; redemption public.item_shard_redemptions%rowtype;
begin
    perform 1 from public.wallets where user_id=new.user_id for update;
    if not found then raise exception 'Wallet unavailable' using errcode='23503'; end if;
    if new.reason='ITEM_DUPLICATE' then
        select * into opening from public.item_case_openings where id=new.opening_id;
        if not found or opening.user_id<>new.user_id or opening.request_id<>new.request_id
            or opening.shard_ledger_id<>new.id or opening.outcome<>'ITEM_SHARDS'
            or opening.item_shards_granted<>new.delta then
            raise exception 'Duplicate shard receipt mismatch' using errcode='23514'; end if;
    elsif new.reason='ITEM_REDEMPTION' then
        select * into redemption from public.item_shard_redemptions where id=new.redemption_id;
        if not found or redemption.user_id<>new.user_id or redemption.request_id<>new.request_id
            or redemption.shard_ledger_id<>new.id or -new.delta<>redemption.shards_spent then
            raise exception 'Redemption shard receipt mismatch' using errcode='23514'; end if;
    end if;
    insert into public.shard_balances(user_id,currency) values(new.user_id,new.currency) on conflict do nothing;
    select balance into amount from public.shard_balances where user_id=new.user_id and currency=new.currency for update;
    new.balance_after:=amount+new.delta; -- CHECK rejects negatives; bigint rejects overflow.
    new.created_at:=clock_timestamp();
    return new;
end $$;
create function pawquest_private.apply_shard_entry() returns trigger
language plpgsql security definer set search_path='' as $$
begin
    update public.shard_balances set balance=new.balance_after,updated_at=new.created_at
        where user_id=new.user_id and currency=new.currency;
    return new;
end $$;
create trigger shard_prepare before insert on public.shard_ledger for each row execute function pawquest_private.prepare_shard_entry();
create trigger shard_apply after insert on public.shard_ledger for each row execute function pawquest_private.apply_shard_entry();
create trigger shard_ledger_immutable before update or delete on public.shard_ledger
    for each row execute function pawquest_private.reject_ledger_mutation();
create trigger item_case_openings_immutable before update or delete on public.item_case_openings
    for each row execute function pawquest_private.reject_ledger_mutation();
create trigger item_shard_redemptions_immutable before update or delete on public.item_shard_redemptions
    for each row execute function pawquest_private.reject_ledger_mutation();

-- Reuse the existing append-only Coins ledger. Shop/quest branches and triggers
-- retain their existing semantics; case price is not the catalog direct price.
alter table public.wallet_transactions add column item_case_opening_id uuid unique references public.item_case_openings(id);
alter table public.wallet_transactions drop constraint ledger_shape;
alter table public.wallet_transactions add constraint ledger_shape check(
 (kind='quest_reward' and item_case_opening_id is null and quest_session_id is not null and reward_day is not null and reward_slot is not null
  and coins between 0 and 80 and pet_xp in (5,10,20,30,40,50) and balance_after>=coins
  and request_id is null and item_id is null and quantity is null and unit_price is null)
 or (kind='shop_purchase' and item_case_opening_id is null and quest_session_id is null and reward_day is null and reward_slot is null
  and pet_xp=0 and request_id is not null and item_id is not null and quantity is not null and quantity between 1 and 100
  and unit_price is not null and unit_price between 0 and 1000000 and coins=-(quantity::bigint*unit_price))
 or (kind='item_case_opening' and item_case_opening_id is not null and quest_session_id is null and reward_day is null and reward_slot is null
  and pet_xp=0 and request_id is not null and item_id is not null and quantity=1
  and unit_price between 1 and 1000000 and unit_price is not null and coins=-unit_price::bigint)
);
create function pawquest_private.prepare_item_case_debit() returns trigger
language plpgsql security definer set search_path='' as $$
declare receipt public.item_case_openings%rowtype; amount bigint;
begin
    select coins into amount from public.wallets where user_id=new.user_id for update;
    select * into receipt from public.item_case_openings where id=new.item_case_opening_id;
    if not found or receipt.wallet_transaction_id<>new.id or receipt.user_id<>new.user_id then
        raise exception 'Case debit receipt mismatch' using errcode='23514'; end if;
    new.request_id:=receipt.request_id; new.item_id:=receipt.item_id; new.quantity:=1;
    new.unit_price:=receipt.coins_spent; new.coins:=-receipt.coins_spent::bigint;
    new.balance_after:=amount+new.coins; new.pet_xp:=0;
    new.quest_session_id:=null; new.reward_day:=null; new.reward_slot:=null;
    new.created_at:=receipt.created_at;
    if new.balance_after<>receipt.coins_balance_after then
        raise exception 'Case balance mismatch' using errcode='23514'; end if;
    return new;
end $$;
create function pawquest_private.apply_item_case_debit() returns trigger
language plpgsql security definer set search_path='' as $$
begin
    update public.wallets set coins=new.balance_after where user_id=new.user_id;
    return new;
end $$;
create trigger item_case_debit_prepare before insert on public.wallet_transactions
    for each row when(new.kind='item_case_opening') execute function pawquest_private.prepare_item_case_debit();
create trigger item_case_debit_apply after insert on public.wallet_transactions
    for each row when(new.kind='item_case_opening') execute function pawquest_private.apply_item_case_debit();

-- Internal settlement primitive for a FUTURE trusted server-side draw. It does
-- not draw, accepts no client access, and must never be granted to mobile roles.
create function pawquest_private.settle_item_case(p_user_id uuid,p_version_id uuid,p_item_id uuid,p_request_id uuid)
returns public.item_case_openings language plpgsql security definer set search_path='' as $$
declare receipt public.item_case_openings%rowtype; config public.item_case_versions%rowtype;
    reward public.item_case_pool%rowtype; item public.item_catalog%rowtype;
    amount bigint; owned integer; shards integer:=0; debit uuid:=gen_random_uuid(); credit uuid;
begin
    if p_request_id is null or p_version_id is null or p_item_id is null then
        raise exception 'Opening identifiers required' using errcode='23514'; end if;
    select coins into amount from public.wallets where user_id=p_user_id for update;
    if not found then raise exception 'Wallet unavailable' using errcode='23503'; end if;
    select * into receipt from public.item_case_openings where user_id=p_user_id and request_id=p_request_id;
    if found then
        if receipt.version_id<>p_version_id or receipt.item_id<>p_item_id then
            raise exception 'Opening request conflict' using errcode='23514'; end if;
        return receipt;
    end if;
    -- Reject collisions with direct purchases before performing any grant.
    if exists(select 1 from public.wallet_transactions where user_id=p_user_id and request_id=p_request_id) then
        raise exception 'Wallet request already used' using errcode='23505'; end if;
    select v.* into config from public.item_case_versions v join public.item_case_banners b on b.id=v.banner_id
        where v.id=p_version_id and v.published_at is not null and b.enabled and b.current_version=v.version for share of b,v;
    if not found then raise exception 'Banner unavailable' using errcode='23514'; end if;
    select * into reward from public.item_case_pool where version_id=p_version_id and item_id=p_item_id;
    if not found then raise exception 'Reward not in version' using errcode='23503'; end if;
    -- Disable the whole operation rather than quietly remove unavailable pool
    -- members and distort advertised odds. Publish a new version to change it.
    perform 1 from public.item_catalog i join public.item_case_pool p on p.item_id=i.id where p.version_id=p_version_id for share of i;
    if exists(select 1 from public.item_case_pool p join public.item_catalog i on i.id=p.item_id where p.version_id=p_version_id
        and (not i.active or i.code<>p.item_code or i.asset_id<>p.asset_id or i.category<>p.category or i.max_quantity<>p.max_quantity)) then
        raise exception 'Published pool catalog changed' using errcode='23514'; end if;
    select * into item from public.item_catalog where id=p_item_id;
    select quantity into owned from public.user_inventory where user_id=p_user_id and item_id=p_item_id for update;
    owned:=coalesce(owned,0);
    if item.max_quantity=1 and owned>0 then
        select duplicate_item_shards into shards from public.item_case_tiers where version_id=p_version_id and rarity=reward.rarity;
        credit:=gen_random_uuid();
    elsif owned>=item.max_quantity then
        raise exception 'Inventory capacity reached' using errcode='23514';
    end if;
    if amount<config.price_coins then raise exception 'Insufficient Coins' using errcode='23514'; end if;
    insert into public.item_case_openings(user_id,request_id,version_id,item_id,outcome,inventory_before,quantity_granted,
        coins_spent,coins_balance_after,item_shards_granted,wallet_transaction_id,shard_ledger_id)
    values(p_user_id,p_request_id,p_version_id,p_item_id,case when shards>0 then 'ITEM_SHARDS' else 'ITEM' end,owned,
        case when shards>0 then 0 else 1 end,config.price_coins,amount-config.price_coins,shards,debit,credit) returning * into receipt;
    insert into public.wallet_transactions(id,user_id,kind,item_case_opening_id) values(debit,p_user_id,'item_case_opening',receipt.id);
    if shards>0 then
        insert into public.shard_ledger(id,user_id,currency,request_id,reason,delta,opening_id,balance_after)
            values(credit,p_user_id,'ITEM_SHARD',p_request_id,'ITEM_DUPLICATE',shards,receipt.id,0);
    else
        perform pawquest_private.adjust_inventory(p_user_id,p_item_id,1);
    end if;
    return receipt;
end $$;

-- Receipt references are bidirectional and owner/amount checked at commit.
-- This also rejects a forged history row pointing at somebody else's ledger.
create function pawquest_private.check_item_receipt_links() returns trigger
language plpgsql security definer set search_path='' as $$
declare entry public.shard_ledger%rowtype; debit public.wallet_transactions%rowtype;
begin
    if tg_table_name='item_case_openings' then
        select * into debit from public.wallet_transactions where id=new.wallet_transaction_id;
        if not found or debit.item_case_opening_id is distinct from new.id or debit.user_id<>new.user_id
            or debit.request_id is distinct from new.request_id or debit.item_id is distinct from new.item_id
            or debit.kind<>'item_case_opening' or debit.coins<>-new.coins_spent::bigint or debit.balance_after<>new.coins_balance_after then
            raise exception 'Opening debit link mismatch' using errcode='23514'; end if;
        if new.shard_ledger_id is null then return new; end if;
    end if;
    select * into entry from public.shard_ledger where id=new.shard_ledger_id;
    if not found or entry.user_id<>new.user_id or entry.request_id<>new.request_id or entry.currency<>'ITEM_SHARD' then
        raise exception 'Shard receipt link mismatch' using errcode='23514'; end if;
    if tg_table_name='item_case_openings' then
        if entry.opening_id is distinct from new.id or entry.reason<>'ITEM_DUPLICATE' or entry.delta<>new.item_shards_granted then
            raise exception 'Duplicate receipt link mismatch' using errcode='23514'; end if;
    elsif entry.redemption_id is distinct from new.id or entry.reason<>'ITEM_REDEMPTION' or entry.delta<>-new.shards_spent::bigint then
        raise exception 'Redemption receipt link mismatch' using errcode='23514';
    end if;
    return new;
end $$;
create constraint trigger item_case_receipt_links after insert on public.item_case_openings
    deferrable initially deferred for each row execute function pawquest_private.check_item_receipt_links();
create constraint trigger item_redemption_receipt_links after insert on public.item_shard_redemptions
    deferrable initially deferred for each row execute function pawquest_private.check_item_receipt_links();

-- A request used by a case must not replay as an ordinary shop purchase.
create or replace function public.shop_purchase(p_item_id uuid,p_quantity integer,p_request_id uuid) returns jsonb
language plpgsql security definer set search_path='' as $$
declare actor uuid:=auth.uid(); receipt public.wallet_transactions%rowtype; code text;
begin
    if actor is null then raise exception 'Authentication required' using errcode='42501'; end if;
    if p_request_id is null or p_item_id is null or p_quantity is null or p_quantity not between 1 and 100 then
        return jsonb_build_object('status','invalid_quantity','receipt',null); end if;
    perform 1 from public.wallets where user_id=actor for update;
    if not found then return jsonb_build_object('status','account_unavailable','receipt',null); end if;
    select * into receipt from public.wallet_transactions where user_id=actor and request_id=p_request_id;
    if found then
        if receipt.kind<>'shop_purchase' or receipt.item_id<>p_item_id or receipt.quantity<>p_quantity then
            return jsonb_build_object('status','request_conflict','receipt',null); end if;
        return jsonb_build_object('status','purchased','receipt',to_jsonb(receipt));
    end if;
    begin
        insert into public.wallet_transactions(user_id,kind,item_id,quantity,request_id)
            values(actor,'shop_purchase',p_item_id,p_quantity,p_request_id) returning * into receipt;
    exception when sqlstate 'P1101' or sqlstate 'P1102' or sqlstate 'P1103' or sqlstate 'P1104' or sqlstate 'P1105' or sqlstate 'P1106' then
        code:=case sqlstate when 'P1101' then 'invalid_quantity' when 'P1102' then 'account_unavailable'
            when 'P1103' then 'item_unavailable' when 'P1104' then 'already_owned'
            when 'P1105' then 'inventory_full' else 'insufficient_balance' end;
        return jsonb_build_object('status',code,'receipt',null);
    end;
    return jsonb_build_object('status','purchased','receipt',to_jsonb(receipt));
end $$;

-- RLS and grants: public configuration is authenticated read-only; user data
-- is owner-read-only. All mutation and private primitives remain server-only.
do $$ declare tbl text;
begin
    foreach tbl in array array['item_case_banners','item_case_versions','item_case_tiers','item_case_pool','shard_currencies',
        'shard_balances','item_case_openings','item_shard_redemptions','shard_ledger'] loop
        execute format('alter table public.%I enable row level security',tbl);
        execute format('revoke all on public.%I from public,anon,authenticated,service_role',tbl);
        execute format('grant select on public.%I to authenticated',tbl);
    end loop;
end $$;
create policy item_case_banner_read on public.item_case_banners for select to authenticated using(true);
create policy item_case_version_read on public.item_case_versions for select to authenticated using(published_at is not null);
create policy item_case_tier_read on public.item_case_tiers for select to authenticated
    using(exists(select 1 from public.item_case_versions v where v.id=version_id and v.published_at is not null));
create policy item_case_pool_read on public.item_case_pool for select to authenticated
    using(exists(select 1 from public.item_case_versions v where v.id=version_id and v.published_at is not null));
create policy shard_currency_read on public.shard_currencies for select to authenticated using(true);
create policy shard_balance_read_own on public.shard_balances for select to authenticated using(user_id=(select auth.uid()));
create policy shard_ledger_read_own on public.shard_ledger for select to authenticated using(user_id=(select auth.uid()));
create policy item_case_opening_read_own on public.item_case_openings for select to authenticated using(user_id=(select auth.uid()));
create policy item_redemption_read_own on public.item_shard_redemptions for select to authenticated using(user_id=(select auth.uid()));
revoke all on public.item_case_probabilities,public.item_shard_redemption_catalog from public,anon,authenticated,service_role;
grant select on public.item_case_probabilities,public.item_shard_redemption_catalog to authenticated;
revoke all on function pawquest_private.item_case_config_guard(),pawquest_private.item_case_version_guard(),
    pawquest_private.item_case_banner_guard(),pawquest_private.prepare_shard_entry(),pawquest_private.apply_shard_entry(),
    pawquest_private.prepare_item_case_debit(),pawquest_private.apply_item_case_debit(),
    pawquest_private.settle_item_case(uuid,uuid,uuid,uuid),pawquest_private.check_item_receipt_links(),
    pawquest_private.prepare_item_redemption_receipt()
    from public,anon,authenticated,service_role;

comment on table public.item_case_openings is 'Immutable committed openings. Settlement is private; future public RPC must choose outcomes on server and recover by owner/request before drawing.';
comment on table public.item_shard_redemptions is 'Reserved Item Shard receipts; no redemption execution endpoint yet. Future atomic RPC must validate version price/ownership, debit shards and grant inventory.';
comment on table public.shard_balances is 'Separate ITEM_SHARD/PET_SHARD balances. Missing row means zero. All postings through private ledger triggers; no client writes.';
commit;
