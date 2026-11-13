-- Run explicitly in the SQL Editor of the Supabase project used by the app.
-- This enables paid openings at the existing backend price. No currency,
-- inventory, history, price, probability or permission is changed.
begin;
do $$
declare config_id uuid;
begin
    if to_regprocedure('public.item_case_open(uuid,uuid)') is null
        or to_regprocedure('public.item_shard_quote(uuid,uuid)') is null
        or to_regprocedure('public.item_shard_redeem(uuid,uuid)') is null then
        raise exception 'Install migrations 024 through 027 before enabling Item Case';
    end if;
    select v.id into config_id
        from public.item_case_banners b join public.item_case_versions v
            on v.banner_id=b.id and v.version=b.current_version
        where b.id='e2400000-0000-0000-0000-000000000001' and b.code='item_case'
            and v.published_at is not null for share of b,v;
    if config_id is null then raise exception 'Item Case has no published current configuration'; end if;
    if (select count(*)<>4 or coalesce(sum(probability_bps),0)<>10000 from public.item_case_tiers where version_id=config_id)
        or exists(select 1 from public.item_case_tiers t where t.version_id=config_id and not exists(
            select 1 from public.item_case_pool p where p.version_id=config_id and p.rarity=t.rarity))
        or exists(select 1 from public.item_case_pool p join public.item_catalog i on i.id=p.item_id
            where p.version_id=config_id and (not i.active or i.code<>p.item_code or i.asset_id<>p.asset_id
                or i.category<>p.category or i.max_quantity<>p.max_quantity))
        or exists(select 1 from public.item_case_pool p where p.version_id=config_id and (
            (p.category='ACCESSORY' and not exists(select 1 from public.accessory_slots s where s.item_id=p.item_id))
            or (p.category='FURNITURE' and not exists(select 1 from public.furniture_slots s where s.item_id=p.item_id)))) then
        raise exception 'Published reward configuration is invalid or its catalog changed';
    end if;
    if exists(select 1 from pg_class c join pg_namespace n on n.oid=c.relnamespace
        where n.nspname='public' and c.relname in ('wallets','user_inventory','item_case_openings','shard_balances','shard_ledger','item_shard_redemptions')
            and not c.relrowsecurity) then raise exception 'Required economy RLS is disabled'; end if;
end $$;
update public.item_case_banners set enabled=true
    where id='e2400000-0000-0000-0000-000000000001' and code='item_case';
select b.code,b.enabled,v.version,v.price_coins,count(p.item_id) as reward_count
    from public.item_case_banners b join public.item_case_versions v on v.banner_id=b.id and v.version=b.current_version
    join public.item_case_pool p on p.version_id=v.id
    where b.id='e2400000-0000-0000-0000-000000000001'
    group by b.code,b.enabled,v.version,v.price_coins;
commit;
