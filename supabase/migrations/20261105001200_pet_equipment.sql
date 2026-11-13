begin;
create table public.accessory_slots (
    item_id uuid primary key references public.item_catalog(id),
    slot text not null check(slot in ('HEAD','FACE','NECK')),
    unique(item_id,slot)
);
insert into public.accessory_slots values
 ('e1000000-0000-0000-0000-000000000002','HEAD'),
 ('e1000000-0000-0000-0000-000000000003','FACE'),
 ('e1000000-0000-0000-0000-000000000004','NECK');
create function pawquest_private.check_accessory_slot() returns trigger language plpgsql set search_path='' as $$
begin
    if not exists(select 1 from public.item_catalog where id=new.item_id and category='ACCESSORY') then
        raise exception 'Equipment must be an accessory' using errcode='23514'; end if;
    return new;
end $$;
create trigger accessory_type before insert or update on public.accessory_slots
    for each row execute function pawquest_private.check_accessory_slot();
-- Prevent changing an equip-compatible catalog item into a consumable/furniture.
create function pawquest_private.freeze_accessory_type() returns trigger language plpgsql set search_path='' as $$
begin
    if new.category<>'ACCESSORY' and exists(select 1 from public.accessory_slots where item_id=new.id) then
        raise exception 'Equipment item must remain an accessory' using errcode='23514'; end if;
    return new;
end $$;
create trigger equipment_catalog_type before update on public.item_catalog
    for each row execute function pawquest_private.freeze_accessory_type();
create table public.pet_equipment (
    user_id uuid not null references public.profiles(id),
    slot text not null check(slot in ('HEAD','FACE','NECK')),
    item_id uuid not null,
    equipped_at timestamptz not null default clock_timestamp(),
    primary key(user_id,slot),
    foreign key(user_id,item_id) references public.user_inventory(user_id,item_id) on delete restrict,
    foreign key(item_id,slot) references public.accessory_slots(item_id,slot)
);
create index equipment_item_idx on public.pet_equipment(item_id,slot);
alter table public.accessory_slots enable row level security;
alter table public.pet_equipment enable row level security;
revoke all on public.accessory_slots,public.pet_equipment from public,anon,authenticated,service_role;
grant select on public.accessory_slots,public.pet_equipment to authenticated;
create policy accessory_slot_read on public.accessory_slots for select to authenticated using(true);
create policy equipment_read_own on public.pet_equipment for select to authenticated using(user_id=(select auth.uid()));

create function public.equipment_state() returns jsonb language sql stable security invoker set search_path='' as $$
    select coalesce(jsonb_agg(jsonb_build_object('slot',e.slot,'item_id',e.item_id,
        'asset_id',i.asset_id,'equipped_at',e.equipped_at) order by e.slot),'[]'::jsonb)
    from public.pet_equipment e join public.item_catalog i on i.id=e.item_id where e.user_id=auth.uid();
$$;
-- Setting a slot is idempotent. Replays cannot consume inventory or spend Coins.
create function public.equipment_set(p_slot text,p_item_id uuid default null) returns jsonb
language plpgsql security definer set search_path='' as $$
declare actor uuid:=auth.uid(); owned integer;
begin
    if actor is null then raise exception 'Authentication required' using errcode='42501'; end if;
    if p_slot is null or p_slot not in ('HEAD','FACE','NECK') then raise exception 'Invalid slot' using errcode='23514'; end if;
    perform 1 from public.wallets where user_id=actor for update;
    if not found then raise exception 'Account unavailable' using errcode='23503'; end if;
    if p_item_id is null then
        delete from public.pet_equipment where user_id=actor and slot=p_slot;
    else
        perform 1 from public.accessory_slots s join public.item_catalog i on i.id=s.item_id
            where s.item_id=p_item_id and s.slot=p_slot and i.category='ACCESSORY' for share of s,i;
        if not found then raise exception 'Accessory does not fit slot' using errcode='23514'; end if;
        select quantity into owned from public.user_inventory where user_id=actor and item_id=p_item_id for update;
        if not found or owned<>1 then raise exception 'Accessory not owned' using errcode='42501'; end if;
        insert into public.pet_equipment(user_id,slot,item_id) values(actor,p_slot,p_item_id)
            on conflict(user_id,slot) do update set item_id=excluded.item_id,equipped_at=clock_timestamp()
            where public.pet_equipment.item_id<>excluded.item_id;
    end if;
    return public.equipment_state();
end $$;
revoke all on function pawquest_private.check_accessory_slot(),pawquest_private.freeze_accessory_type(),
    public.equipment_state(),public.equipment_set(text,uuid) from public,anon,authenticated,service_role;
grant execute on function public.equipment_state(),public.equipment_set(text,uuid) to authenticated;
commit;
