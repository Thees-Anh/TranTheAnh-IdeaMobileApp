-- Explicit operator action AFTER migration 028, in the same project used by the app.
-- Enables the existing published banner only. Never changes odds, prices, balances or grants.
begin;
do $$
declare config_id uuid;
begin
    if to_regprocedure('public.lucky_wheel_spin(uuid,text,uuid)') is null
        or to_regprocedure('public.lucky_wheel_config(uuid)') is null
        or to_regprocedure('public.lucky_wheel_balances()') is null
        or to_regprocedure('public.pet_shard_quote(text,uuid)') is null
        or to_regprocedure('public.pet_shard_redeem(text,uuid)') is null then
        raise exception 'Apply migration 028 before enabling Lucky Wheel';
    end if;
    select v.id into config_id from public.lucky_wheel_banners b
        join public.lucky_wheel_versions v on v.banner_id=b.id and v.version=b.current_version
        where b.id='e2800000-0000-0000-0000-000000000001' and b.code='lucky_wheel'
            and v.published_at is not null for share of b,v;
    if config_id is null then raise exception 'Lucky Wheel has no published current configuration'; end if;
    if (select count(*)<>8 or coalesce(sum(weight),0)<>10000 from public.lucky_wheel_sectors where version_id=config_id)
        or (select count(*)<>3 from public.pet_shard_prices where version_id=config_id)
        or exists(select 1 from public.pet_shard_prices q join public.pet_species s on s.code=q.species_code
            where q.version_id=config_id and (not s.published or s.code not in ('rabbit','puppy','capybara')))
        or exists(select 1 from public.lucky_wheel_sectors s join public.item_catalog i on i.id=s.food_item_id
            where s.version_id=config_id and (not i.active or i.category<>'FOOD' or s.amount>i.max_quantity)) then
        raise exception 'Published Lucky Wheel configuration or reward catalog is invalid';
    end if;
    if exists(select 1 from pg_class c join pg_namespace n on n.oid=c.relnamespace
        where n.nspname='public' and c.relname in ('wallets','wallet_transactions','user_inventory','shard_balances',
            'shard_ledger','user_pet_unlocks','lucky_wheel_ticket_balances','lucky_wheel_ticket_ledger','lucky_wheel_spins','pet_shard_redemptions')
            and not c.relrowsecurity) then raise exception 'Required economy RLS is disabled'; end if;
end $$;
update public.lucky_wheel_banners set enabled=true
    where id='e2800000-0000-0000-0000-000000000001' and code='lucky_wheel';
select b.code,b.enabled,v.version,v.price_coins,count(s.sector_id) as sector_count,sum(s.weight) as total_weight
    from public.lucky_wheel_banners b join public.lucky_wheel_versions v on v.banner_id=b.id and v.version=b.current_version
    join public.lucky_wheel_sectors s on s.version_id=v.id
    where b.id='e2800000-0000-0000-0000-000000000001'
    group by b.code,b.enabled,v.version,v.price_coins;
commit;
