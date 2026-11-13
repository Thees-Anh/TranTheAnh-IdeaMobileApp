begin;

create table public.item_catalog (
    id uuid primary key default gen_random_uuid(),
    code text not null unique check(code ~ '^[a-z][a-z0-9_]{1,63}$'),
    name text not null check(length(btrim(name)) between 1 and 100),
    category text not null check(category in ('FOOD','ACCESSORY','FURNITURE')),
    price_coins integer not null check(price_coins between 0 and 1000000),
    asset_id text not null unique check(length(btrim(asset_id)) between 1 and 200),
    max_quantity integer not null,
    active boolean not null default true,
    created_at timestamptz not null default now(),
    unique(id,max_quantity),
    check((category='FOOD' and max_quantity=1000000) or
          (category in ('ACCESSORY','FURNITURE') and max_quantity=1))
);
insert into public.item_catalog(id,code,name,category,price_coins,asset_id,max_quantity) values
 ('e1000000-0000-0000-0000-000000000001','basic_food','Cat food','FOOD',10,'item.food.basic',1000000),
 ('e1000000-0000-0000-0000-000000000002','hat','Hat','ACCESSORY',60,'item.accessory.hat',1),
 ('e1000000-0000-0000-0000-000000000003','glasses','Glasses','ACCESSORY',80,'item.accessory.glasses',1),
 ('e1000000-0000-0000-0000-000000000004','collar','Collar','ACCESSORY',40,'item.accessory.collar',1),
 ('e1000000-0000-0000-0000-000000000005','bed','Bed','FURNITURE',120,'item.furniture.bed',1),
 ('e1000000-0000-0000-0000-000000000006','lamp','Lamp','FURNITURE',70,'item.furniture.lamp',1),
 ('e1000000-0000-0000-0000-000000000007','carpet','Carpet','FURNITURE',90,'item.furniture.carpet',1);
alter table public.item_catalog enable row level security;
revoke all on public.item_catalog from public,anon,authenticated,service_role;
grant select on public.item_catalog to authenticated;
-- Inactive owned items remain resolvable; shop_catalog filters inactive listings.
create policy catalog_read on public.item_catalog for select to authenticated using(true);

-- Preserve every existing food count, including zero. No starter refill.
alter table public.pet_food_inventory rename to user_inventory;
alter table public.user_inventory drop constraint pet_food_inventory_pkey;
alter table public.user_inventory
    add column id uuid not null default gen_random_uuid() primary key,
    add column item_id uuid not null default 'e1000000-0000-0000-0000-000000000001',
    add column max_quantity integer not null default 1000000,
    add column created_at timestamptz not null default now(),
    add constraint inventory_owner_item unique(user_id,item_id),
    add constraint inventory_catalog foreign key(item_id,max_quantity) references public.item_catalog(id,max_quantity),
    add constraint inventory_quantity check(quantity between 0 and max_quantity and (max_quantity<>1 or quantity=1));
alter table public.user_inventory alter column item_id drop default,alter column max_quantity drop default;
alter policy food_read_own on public.user_inventory rename to inventory_read_own;
create index inventory_item_idx on public.user_inventory(item_id,max_quantity);
-- Existing table RLS and SELECT-only grants survive the rename.
create view public.pet_food_inventory with(security_invoker=true) as
    select user_id,quantity from public.user_inventory
    where item_id='e1000000-0000-0000-0000-000000000001';
revoke all on public.pet_food_inventory from public,anon,authenticated,service_role;
grant select on public.pet_food_inventory to authenticated;

create or replace function pawquest_private.starter_food() returns trigger
language plpgsql security definer set search_path='' as $$
begin
    insert into public.user_inventory(user_id,item_id,max_quantity,quantity)
    values(new.id,'e1000000-0000-0000-0000-000000000001',1000000,
        (pawquest_private.pet_care_balance()->>'starterFood')::integer)
    on conflict(user_id,item_id) do nothing;
    return new;
end $$;

-- Trusted backend primitive only. No mobile grant/consume endpoint is exposed.
-- Wallet first matches Pet Care's lock order. Unique ownership retries are no-ops.
create function pawquest_private.adjust_inventory(p_user_id uuid,p_item_id uuid,p_delta integer)
returns public.user_inventory language plpgsql set search_path='' as $$
declare item public.item_catalog%rowtype; owned public.user_inventory%rowtype;
begin
    if p_delta is null or p_delta=0 then raise exception 'Nonzero quantity required' using errcode='23514'; end if;
    perform 1 from public.wallets where user_id=p_user_id for update;
    if not found then raise exception 'Account unavailable' using errcode='23503'; end if;
    select * into item from public.item_catalog where id=p_item_id for share;
    if not found then raise exception 'Item unavailable' using errcode='23503'; end if;
    if item.max_quantity=1 and p_delta<>1 then raise exception 'Unique items cannot stack or be consumed' using errcode='23514'; end if;
    if p_delta>0 and not item.active then raise exception 'Item inactive' using errcode='23514'; end if;
    select * into owned from public.user_inventory where user_id=p_user_id and item_id=p_item_id for update;
    if found then
        if item.max_quantity=1 then return owned; end if;
        update public.user_inventory set quantity=(quantity::bigint+p_delta)::integer
            where id=owned.id returning * into owned;
    else
        insert into public.user_inventory(user_id,item_id,max_quantity,quantity)
            values(p_user_id,p_item_id,item.max_quantity,p_delta) returning * into owned;
    end if;
    return owned;
end $$;
revoke all on function pawquest_private.adjust_inventory(uuid,uuid,integer) from public,anon,authenticated,service_role;

create function public.shop_catalog() returns jsonb language sql stable security invoker set search_path='' as $$
    select coalesce(jsonb_agg(to_jsonb(i) order by i.category,i.code),'[]'::jsonb)
    from public.item_catalog i where i.active;
$$;
create function public.inventory_items() returns jsonb language sql stable security invoker set search_path='' as $$
    select coalesce(jsonb_agg(jsonb_build_object('id',v.id,'user_id',v.user_id,
        'quantity',v.quantity,'created_at',v.created_at,'item',to_jsonb(i)) order by i.category,i.code),'[]'::jsonb)
    from public.user_inventory v join public.item_catalog i on i.id=v.item_id
    where v.user_id=auth.uid() and v.quantity>0;
$$;
revoke all on function public.shop_catalog(),public.inventory_items() from public,anon,authenticated,service_role;
grant execute on function public.shop_catalog(),public.inventory_items() to authenticated;
commit;
