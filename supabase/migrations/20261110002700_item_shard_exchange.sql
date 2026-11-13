-- Additive execution over 024's balances, ledger, catalog and receipt structures.
begin;
create table pawquest_private.item_shard_quotes (
    user_id uuid not null references public.profiles(id), request_id uuid not null,
    item_id uuid not null, version_id uuid not null, item_name text not null,
    price integer not null check(price>0), expires_at timestamptz not null,
    primary key(user_id,request_id),
    foreign key(version_id,item_id) references public.item_case_pool(version_id,item_id)
);
alter table pawquest_private.item_shard_quotes enable row level security;
revoke all on pawquest_private.item_shard_quotes from public,anon,authenticated,service_role;
create trigger item_shard_quotes_immutable before update or delete on pawquest_private.item_shard_quotes
    for each row execute function pawquest_private.reject_ledger_mutation();

create function pawquest_private.item_shard_response(p_user uuid,p_request uuid) returns jsonb
language sql security definer set search_path='' as $$
select jsonb_build_object('status','redeemed','receipt',jsonb_build_object(
    'transaction_id',r.id,'request_id',r.request_id,'item_id',r.item_id,'item_name',q.item_name,
    'asset_id',p.asset_id,'item_code',p.item_code,'category',p.category,'rarity',p.rarity,
    'shards_spent',r.shards_spent,'quantity_granted',r.quantity_granted,
    'item_shards_balance_after',l.balance_after,'created_at',r.created_at))
from public.item_shard_redemptions r join pawquest_private.item_shard_quotes q using(user_id,request_id)
join public.item_case_pool p on p.version_id=r.version_id and p.item_id=r.item_id
join public.shard_ledger l on l.id=r.shard_ledger_id
where r.user_id=p_user and r.request_id=p_request
$$;

-- Stable, read-only-to-the-economy quote. Retrying this endpoint cannot redeem.
create function public.item_shard_quote(p_item_id uuid,p_request_id uuid) returns jsonb
language plpgsql security definer set search_path='' set lock_timeout='5s' as $$
declare actor uuid:=auth.uid(); q pawquest_private.item_shard_quotes%rowtype; reward record;
    owned integer; balance bigint; prior public.item_shard_redemptions%rowtype;
    throttle pawquest_private.item_case_request_limits%rowtype; limits pawquest_private.item_case_api_settings%rowtype;
begin
    if actor is null then raise exception 'Authentication required' using errcode='42501'; end if;
    if p_item_id is null or p_request_id is null then return jsonb_build_object('status','invalid_request'); end if;
    perform 1 from public.wallets where user_id=actor for update;
    if not found then return jsonb_build_object('status','account_unavailable'); end if;
    select * into prior from public.item_shard_redemptions where user_id=actor and request_id=p_request_id;
    if found then
        if prior.item_id<>p_item_id then return jsonb_build_object('status','request_conflict'); end if;
        return pawquest_private.item_shard_response(actor,p_request_id);
    end if;
    select * into q from pawquest_private.item_shard_quotes where user_id=actor and request_id=p_request_id;
    if found then
        if q.item_id<>p_item_id then return jsonb_build_object('status','request_conflict'); end if;
    else
        if exists(select 1 from public.wallet_transactions where user_id=actor and request_id=p_request_id)
            or exists(select 1 from public.shard_ledger where user_id=actor and request_id=p_request_id) then
            return jsonb_build_object('status','request_conflict'); end if;
        select * into limits from pawquest_private.item_case_api_settings where singleton;
        select * into throttle from pawquest_private.item_case_request_limits where user_id=actor;
        if not found or clock_timestamp()>=throttle.window_started_at+make_interval(secs=>limits.window_seconds) then
            insert into pawquest_private.item_case_request_limits values(actor,clock_timestamp(),1)
                on conflict(user_id) do update set window_started_at=excluded.window_started_at,attempts=1;
        elsif throttle.attempts>=limits.max_attempts then return jsonb_build_object('status','rate_limited');
        else update pawquest_private.item_case_request_limits set attempts=attempts+1 where user_id=actor; end if;
        select p.*,t.redemption_item_shards as price,i.name into reward
            from public.item_case_banners b join public.item_case_versions v on v.banner_id=b.id and v.version=b.current_version
            join public.item_case_pool p on p.version_id=v.id join public.item_case_tiers t using(version_id,rarity)
            join public.item_catalog i on i.id=p.item_id
            where b.code='item_case' and v.published_at is not null and p.item_id=p_item_id and p.redeemable
                and i.active and i.max_quantity=p.max_quantity and i.code=p.item_code and i.category=p.category and i.asset_id=p.asset_id
            for share of b,v,i;
        if not found then return jsonb_build_object('status','item_unavailable'); end if;
        if (reward.category='ACCESSORY' and not exists(select 1 from public.accessory_slots where item_id=p_item_id))
            or (reward.category='FURNITURE' and not exists(select 1 from public.furniture_slots where item_id=p_item_id)) then
            return jsonb_build_object('status','item_unavailable'); end if;
        select quantity into owned from public.user_inventory where user_id=actor and item_id=p_item_id;
        if coalesce(owned,0)>=reward.max_quantity then
            return jsonb_build_object('status',case when reward.max_quantity=1 then 'already_owned' else 'inventory_full' end); end if;
        select coalesce((select s.balance from public.shard_balances s where s.user_id=actor and currency='ITEM_SHARD'),0) into balance;
        if balance<reward.price then return jsonb_build_object('status','insufficient_shards'); end if;
        insert into pawquest_private.item_shard_quotes values(actor,p_request_id,p_item_id,reward.version_id,reward.name,reward.price,clock_timestamp()+interval '10 minutes') returning * into q;
    end if;
    return jsonb_build_object('status','quoted','quote',jsonb_build_object('request_id',q.request_id,'item_id',q.item_id,
        'price_item_shards',q.price,'expires_at',q.expires_at));
exception when lock_not_available then return jsonb_build_object('status','busy');
end $$;

create function public.item_shard_redeem(p_item_id uuid,p_request_id uuid) returns jsonb
language plpgsql security definer set search_path='' set lock_timeout='5s' as $$
declare actor uuid:=auth.uid(); q pawquest_private.item_shard_quotes%rowtype; reward record;
    prior public.item_shard_redemptions%rowtype; balance bigint; owned integer;
    receipt_id uuid:=gen_random_uuid(); ledger_id uuid:=gen_random_uuid();
begin
    if actor is null then raise exception 'Authentication required' using errcode='42501'; end if;
    if p_item_id is null or p_request_id is null then return jsonb_build_object('status','invalid_request'); end if;
    perform 1 from public.wallets where user_id=actor for update;
    if not found then return jsonb_build_object('status','account_unavailable'); end if;
    select * into prior from public.item_shard_redemptions where user_id=actor and request_id=p_request_id;
    if found then
        if prior.item_id<>p_item_id then return jsonb_build_object('status','request_conflict'); end if;
        return pawquest_private.item_shard_response(actor,p_request_id);
    end if;
    if exists(select 1 from public.wallet_transactions where user_id=actor and request_id=p_request_id)
        or exists(select 1 from public.shard_ledger where user_id=actor and request_id=p_request_id) then
        return jsonb_build_object('status','request_conflict'); end if;
    select * into q from pawquest_private.item_shard_quotes where user_id=actor and request_id=p_request_id and item_id=p_item_id;
    if not found then return jsonb_build_object('status','quote_required'); end if;
    if clock_timestamp()>q.expires_at then return jsonb_build_object('status','quote_expired'); end if;
    select p.*,t.redemption_item_shards as price into reward
        from public.item_case_banners b join public.item_case_versions v on v.banner_id=b.id and v.version=b.current_version
        join public.item_case_pool p on p.version_id=v.id join public.item_case_tiers t using(version_id,rarity)
        join public.item_catalog i on i.id=p.item_id
        where b.code='item_case' and v.published_at is not null and p.item_id=p_item_id and p.redeemable
            and i.active and i.max_quantity=p.max_quantity and i.code=p.item_code and i.asset_id=p.asset_id and i.category=p.category
        for share of b,v,i;
    if not found then return jsonb_build_object('status','item_unavailable'); end if;
    if reward.version_id<>q.version_id or reward.price<>q.price then return jsonb_build_object('status','price_changed'); end if;
    perform 1 from public.accessory_slots where item_id=p_item_id for share;
    perform 1 from public.furniture_slots where item_id=p_item_id for share;
    if (reward.category='ACCESSORY' and not exists(select 1 from public.accessory_slots where item_id=p_item_id))
        or (reward.category='FURNITURE' and not exists(select 1 from public.furniture_slots where item_id=p_item_id)) then
        return jsonb_build_object('status','item_unavailable'); end if;
    select quantity into owned from public.user_inventory where user_id=actor and item_id=p_item_id for update;
    if coalesce(owned,0)>=reward.max_quantity then
        return jsonb_build_object('status',case when reward.max_quantity=1 then 'already_owned' else 'inventory_full' end); end if;
    select s.balance into balance from public.shard_balances s where s.user_id=actor and currency='ITEM_SHARD' for update;
    if coalesce(balance,0)<q.price then return jsonb_build_object('status','insufficient_shards'); end if;
    insert into public.item_shard_redemptions(id,user_id,request_id,version_id,item_id,shards_spent,quantity_granted,shard_ledger_id)
        values(receipt_id,actor,p_request_id,q.version_id,p_item_id,q.price,1,ledger_id);
    insert into public.shard_ledger(id,user_id,currency,request_id,reason,delta,balance_after,redemption_id)
        values(ledger_id,actor,'ITEM_SHARD',p_request_id,'ITEM_REDEMPTION',-q.price,0,receipt_id);
    perform pawquest_private.adjust_inventory(actor,p_item_id,1);
    return pawquest_private.item_shard_response(actor,p_request_id);
exception when lock_not_available then return jsonb_build_object('status','busy');
end $$;
revoke all on function pawquest_private.item_shard_response(uuid,uuid) from public,anon,authenticated,service_role;
revoke all on function public.item_shard_quote(uuid,uuid),public.item_shard_redeem(uuid,uuid) from public,anon,authenticated,service_role;
grant execute on function public.item_shard_quote(uuid,uuid),public.item_shard_redeem(uuid,uuid) to authenticated;
notify pgrst,'reload schema';
commit;
