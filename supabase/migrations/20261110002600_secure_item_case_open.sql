-- Step 03. Additive to deployed 024/025; leave the banner disabled.
begin;

create schema if not exists extensions;
create extension if not exists pgcrypto with schema extensions;
-- Supabase may already have pgcrypto in a different schema. Bind explicitly
-- to its installed extension namespace without relocating an existing extension.
do $install$ declare ns text;
begin
    select n.nspname into strict ns from pg_extension e join pg_namespace n on n.oid=e.extnamespace where e.extname='pgcrypto';
    execute format($ddl$
        create function pawquest_private.item_case_random_bytes(p_count integer) returns bytea
        language sql volatile security definer set search_path='' as $body$select %I.gen_random_bytes($1)$body$
    $ddl$,ns);
end $install$;

-- Uniform [0, upper), using 56 bits of cryptographic entropy. Rejection removes
-- modulo bias. No time, UUID, random(), client seed, or floating-point rounding.
create function pawquest_private.item_case_random_below(p_upper bigint) returns bigint
language plpgsql volatile security definer set search_path='' as $$
declare space constant bigint:=72057594037927936; limit_value bigint; bytes bytea; value bigint; attempt integer; idx integer;
begin
    if p_upper is null or p_upper<1 or p_upper>space then
        raise exception 'Invalid Item Case random bound' using errcode='22023'; end if;
    limit_value:=space-(space%p_upper);
    for attempt in 1..128 loop
        bytes:=pawquest_private.item_case_random_bytes(7); value:=0;
        for idx in 0..6 loop value:=value*256+get_byte(bytes,idx); end loop;
        if value<limit_value then return value%p_upper; end if;
    end loop;
    raise exception 'Item Case entropy unavailable' using errcode='P0001';
end $$;

-- Pure ticket-to-configuration mapping helpers allow exact boundary tests
-- without any production forced-reward or test-mode parameter in the RPC.
create function pawquest_private.item_case_rarity_at(p_version_id uuid,p_ticket bigint) returns text
language plpgsql stable security definer set search_path='' as $$
declare tier record; cumulative bigint:=0;
begin
    if p_ticket is null or p_ticket<0 or p_ticket>=10000 then
        raise exception 'Invalid rarity ticket' using errcode='22023'; end if;
    for tier in select rarity,probability_bps from public.item_case_tiers where version_id=p_version_id
        order by case rarity when 'COMMON' then 1 when 'RARE' then 2 when 'EPIC' then 3 else 4 end loop
        cumulative:=cumulative+tier.probability_bps;
        if p_ticket<cumulative then return tier.rarity; end if;
    end loop;
    raise exception 'Invalid Item Case tier configuration' using errcode='23514';
end $$;
create function pawquest_private.item_case_item_at(p_version_id uuid,p_rarity text,p_ticket bigint) returns uuid
language plpgsql stable security definer set search_path='' as $$
declare item record; cumulative bigint:=0;
begin
    if p_ticket is null or p_ticket<0 then raise exception 'Invalid item ticket' using errcode='22023'; end if;
    for item in select item_id,weight from public.item_case_pool where version_id=p_version_id and rarity=p_rarity order by item_id loop
        cumulative:=cumulative+item.weight;
        if p_ticket<cumulative then return item.item_id; end if;
    end loop;
    raise exception 'Invalid Item Case item configuration' using errcode='23514';
end $$;

-- Private operational limits: successful recovery is exempt, every new attempt
-- (including normal validation failures) counts. No client can alter these rows.
create table pawquest_private.item_case_api_settings (
    singleton boolean primary key default true check(singleton),
    max_attempts integer not null check(max_attempts between 1 and 600),
    window_seconds integer not null check(window_seconds between 1 and 3600)
);
insert into pawquest_private.item_case_api_settings values(true,30,60);
create table pawquest_private.item_case_request_limits (
    user_id uuid primary key references public.profiles(id),
    window_started_at timestamptz not null,
    attempts integer not null check(attempts>0)
);
-- Preserve replay response fields absent from 024 history, without changing its
-- immutable rows. This is response metadata, not a second financial ledger.
create table pawquest_private.item_case_response_snapshots (
    opening_id uuid primary key references public.item_case_openings(id),
    item_name text not null check(length(item_name)>0),
    item_shards_balance_after bigint not null check(item_shards_balance_after>=0)
);
create trigger item_case_response_immutable before update or delete on pawquest_private.item_case_response_snapshots
    for each row execute function pawquest_private.reject_ledger_mutation();
alter table pawquest_private.item_case_api_settings enable row level security;
alter table pawquest_private.item_case_request_limits enable row level security;
alter table pawquest_private.item_case_response_snapshots enable row level security;
revoke all on pawquest_private.item_case_api_settings,pawquest_private.item_case_request_limits,
    pawquest_private.item_case_response_snapshots from public,anon,authenticated,service_role;

create function pawquest_private.item_case_response(p_opening_id uuid) returns jsonb
language plpgsql volatile security definer set search_path='' as $$
declare result jsonb;
begin
    -- Legacy private settlements had no public API response. Freeze their name
    -- on first recovery and reconstruct the best recorded post-opening balance.
    -- New public openings insert exact snapshots under the wallet lock below.
    insert into pawquest_private.item_case_response_snapshots(opening_id,item_name,item_shards_balance_after)
    select o.id,i.name,coalesce(s.balance_after,(select l.balance_after from public.shard_ledger l
        where l.user_id=o.user_id and l.currency='ITEM_SHARD' and l.created_at<=o.created_at
        order by l.created_at desc,l.id desc limit 1),0)
    from public.item_case_openings o join public.item_catalog i on i.id=o.item_id
        left join public.shard_ledger s on s.id=o.shard_ledger_id
    where o.id=p_opening_id on conflict(opening_id) do nothing;
    select jsonb_build_object('status','opened','receipt',jsonb_build_object(
        'transaction_id',o.id,'request_id',o.request_id,'banner_id',v.banner_id,
        'configuration_version_id',v.id,'configuration_version',v.version,
        'winning_item_id',o.item_id,'item_name',s.item_name,'item_code',p.item_code,'asset_id',p.asset_id,'rarity',p.rarity,
        'opening_cost_coins',o.coins_spent,
        'reward_outcome',case o.outcome when 'ITEM' then 'ITEM_GRANTED' else 'DUPLICATE_CONVERTED' end,
        'quantity_granted',o.quantity_granted,'item_shards_granted',o.item_shards_granted,
        'coins_balance_after',o.coins_balance_after,'item_shards_balance_after',s.item_shards_balance_after,
        'created_at',to_char(o.created_at at time zone 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"')))
    into result from public.item_case_openings o join public.item_case_versions v on v.id=o.version_id
        join public.item_case_pool p on p.version_id=o.version_id and p.item_id=o.item_id
        join pawquest_private.item_case_response_snapshots s on s.opening_id=o.id where o.id=p_opening_id;
    if result is null then raise exception 'Item Case receipt unavailable' using errcode='P0001'; end if;
    return result;
end $$;

create function public.item_case_open(p_banner_id uuid,p_request_id uuid) returns jsonb
language plpgsql volatile security definer set search_path='' set lock_timeout='5s' as $$
declare actor uuid:=auth.uid(); amount bigint; config public.item_case_versions%rowtype;
    previous public.item_case_openings%rowtype; receipt public.item_case_openings%rowtype;
    limits pawquest_private.item_case_api_settings%rowtype; throttle pawquest_private.item_case_request_limits%rowtype;
    stamp timestamptz; retry_seconds integer; tier text; total_weight bigint; winning_item uuid; shard_balance bigint; problem text;
begin
    if actor is null then raise exception 'Authentication required' using errcode='42501'; end if;
    if p_banner_id is null or p_request_id is null then
        return jsonb_build_object('status','invalid_request','receipt',null); end if;
    -- Wallet first serializes this RPC with all existing economic operations.
    begin
        select coins into amount from public.wallets where user_id=actor for update;
    exception when lock_not_available then
        return jsonb_build_object('status','busy','receipt',null,'retry_after_seconds',1);
    end;
    if not found then return jsonb_build_object('status','account_unavailable','receipt',null); end if;
    select * into previous from public.item_case_openings where user_id=actor and request_id=p_request_id;
    if found then
        if not exists(select 1 from public.item_case_versions where id=previous.version_id and banner_id=p_banner_id) then
            return jsonb_build_object('status','request_conflict','receipt',null); end if;
        -- Recovery happens before throttle, balance, active banner or entropy.
        return pawquest_private.item_case_response(previous.id);
    end if;

    stamp:=clock_timestamp();
    select * into strict limits from pawquest_private.item_case_api_settings where singleton;
    select * into throttle from pawquest_private.item_case_request_limits where user_id=actor;
    if not found or stamp>=throttle.window_started_at+make_interval(secs=>limits.window_seconds) then
        insert into pawquest_private.item_case_request_limits(user_id,window_started_at,attempts) values(actor,stamp,1)
            on conflict(user_id) do update set window_started_at=excluded.window_started_at,attempts=1;
    elsif throttle.attempts>=limits.max_attempts then
        retry_seconds:=greatest(1,ceil(extract(epoch from throttle.window_started_at+make_interval(secs=>limits.window_seconds)-stamp))::integer);
        return jsonb_build_object('status','rate_limited','receipt',null,'retry_after_seconds',retry_seconds);
    else
        update pawquest_private.item_case_request_limits set attempts=attempts+1 where user_id=actor;
    end if;
    if exists(select 1 from public.wallet_transactions where user_id=actor and request_id=p_request_id)
        or exists(select 1 from public.shard_ledger where user_id=actor and request_id=p_request_id) then
        return jsonb_build_object('status','request_conflict','receipt',null); end if;
    select v.* into config from public.item_case_banners b join public.item_case_versions v on v.banner_id=b.id and v.version=b.current_version
        where b.id=p_banner_id and b.enabled and v.published_at is not null for share of b,v;
    if not found then return jsonb_build_object('status','banner_unavailable','receipt',null); end if;
    if amount<config.price_coins then return jsonb_build_object('status','insufficient_coins','receipt',null); end if;

    -- Do not filter owned/inactive items or reroll. Any changed catalog member
    -- invalidates this pool until an explicit new configuration is published.
    perform 1 from public.item_catalog i join public.item_case_pool p on p.item_id=i.id where p.version_id=config.id for share of i;
    if exists(select 1 from public.item_case_pool p join public.item_catalog i on i.id=p.item_id where p.version_id=config.id
        and (not i.active or i.code<>p.item_code or i.asset_id<>p.asset_id or i.category<>p.category or i.max_quantity<>p.max_quantity))
        or (select count(*)<>4 or sum(probability_bps)<>10000 from public.item_case_tiers where version_id=config.id) then
        return jsonb_build_object('status','configuration_unavailable','receipt',null); end if;
    -- Protect every mapped slot while settling; reject missing mappings without
    -- changing probabilities or choosing a different eligible item.
    perform 1 from public.accessory_slots s join public.item_case_pool p on p.item_id=s.item_id where p.version_id=config.id for share of s;
    perform 1 from public.furniture_slots s join public.item_case_pool p on p.item_id=s.item_id where p.version_id=config.id for share of s;
    if exists(select 1 from public.item_case_pool p where p.version_id=config.id and
        ((p.category='ACCESSORY' and not exists(select 1 from public.accessory_slots s where s.item_id=p.item_id))
        or (p.category='FURNITURE' and not exists(select 1 from public.furniture_slots s where s.item_id=p.item_id)))) then
        return jsonb_build_object('status','configuration_unavailable','receipt',null); end if;

    -- Inner subtransaction ensures expected settlement failures leave no
    -- partial economic changes; the normal attempt counter remains committed.
    begin
        tier:=pawquest_private.item_case_rarity_at(config.id,pawquest_private.item_case_random_below(10000));
        select sum(weight)::bigint into total_weight from public.item_case_pool where version_id=config.id and rarity=tier;
        if total_weight is null or total_weight<1 or total_weight>72057594037927936 then
            return jsonb_build_object('status','configuration_unavailable','receipt',null); end if;
        winning_item:=pawquest_private.item_case_item_at(config.id,tier,pawquest_private.item_case_random_below(total_weight));
        receipt:=pawquest_private.settle_item_case(actor,config.id,winning_item,p_request_id);
        select coalesce((select balance from public.shard_balances where user_id=actor and currency='ITEM_SHARD'),0) into shard_balance;
        insert into pawquest_private.item_case_response_snapshots(opening_id,item_name,item_shards_balance_after)
            select receipt.id,name,shard_balance from public.item_catalog where id=winning_item;
        return pawquest_private.item_case_response(receipt.id);
    exception
        when check_violation then
            get stacked diagnostics problem=MESSAGE_TEXT;
            if problem='Inventory capacity reached' then
                return jsonb_build_object('status','inventory_full','receipt',null);
            end if;
            -- Unexpected constraint failures must not be disguised as a
            -- successful/ordinary rejection, even though their effects roll back.
            raise exception 'Item Case opening failed; retry the same request ID' using errcode='P0001';
        when serialization_failure or deadlock_detected or lock_not_available then raise;
        when others then
            raise exception 'Item Case opening failed; retry the same request ID' using errcode='P0001';
    end;
exception when lock_not_available then
    return jsonb_build_object('status','busy','receipt',null,'retry_after_seconds',1);
end $$;

revoke all on function pawquest_private.item_case_random_bytes(integer),pawquest_private.item_case_random_below(bigint),
    pawquest_private.item_case_rarity_at(uuid,bigint),pawquest_private.item_case_item_at(uuid,text,bigint),
    pawquest_private.item_case_response(uuid) from public,anon,authenticated,service_role;
revoke all on function public.item_case_open(uuid,uuid) from public,anon,authenticated,service_role;
grant execute on function public.item_case_open(uuid,uuid) to authenticated;
comment on function public.item_case_open(uuid,uuid) is 'Authenticated atomic Item Case draw/recovery. Accepts only banner UUID and request UUID. Original winning item survives duplicate-to-Item-Shard conversion.';
notify pgrst,'reload schema';
commit;
