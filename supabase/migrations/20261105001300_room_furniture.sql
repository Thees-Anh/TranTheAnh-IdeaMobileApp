begin;
create table public.furniture_slots (
    item_id uuid primary key references public.item_catalog(id),
    slot text not null check(slot in ('BED','LAMP','CARPET')),
    unique(item_id,slot)
);
insert into public.furniture_slots values
 ('e1000000-0000-0000-0000-000000000005','BED'),
 ('e1000000-0000-0000-0000-000000000006','LAMP'),
 ('e1000000-0000-0000-0000-000000000007','CARPET');
create function pawquest_private.check_furniture_slot() returns trigger language plpgsql set search_path='' as $$
begin
    if not exists(select 1 from public.item_catalog where id=new.item_id and category='FURNITURE') then
        raise exception 'Room item must be furniture' using errcode='23514'; end if;
    return new;
end $$;
create trigger furniture_type before insert or update on public.furniture_slots
    for each row execute function pawquest_private.check_furniture_slot();
-- Keep mapped furniture items in the furniture category.
create function pawquest_private.freeze_furniture_type() returns trigger language plpgsql set search_path='' as $$
begin
    if new.category<>'FURNITURE' and exists(select 1 from public.furniture_slots where item_id=new.id) then
        raise exception 'Room item must remain furniture' using errcode='23514'; end if;
    return new;
end $$;
create trigger room_catalog_type before update on public.item_catalog
    for each row execute function pawquest_private.freeze_furniture_type();
create table public.room_furniture (
    user_id uuid not null references public.profiles(id),
    slot text not null check(slot in ('BED','LAMP','CARPET')),
    item_id uuid not null,
    placed_at timestamptz not null default clock_timestamp(),
    primary key(user_id,slot),
    foreign key(user_id,item_id) references public.user_inventory(user_id,item_id) on delete restrict,
    foreign key(item_id,slot) references public.furniture_slots(item_id,slot)
);
create index room_item_idx on public.room_furniture(item_id,slot);
alter table public.furniture_slots enable row level security;
alter table public.room_furniture enable row level security;
revoke all on public.furniture_slots,public.room_furniture from public,anon,authenticated,service_role;
grant select on public.furniture_slots,public.room_furniture to authenticated;
create policy furniture_slot_read on public.furniture_slots for select to authenticated using(true);
create policy room_read_own on public.room_furniture for select to authenticated using(user_id=(select auth.uid()));

create function public.room_state() returns jsonb language sql stable security invoker set search_path='' as $$
    select coalesce(jsonb_agg(jsonb_build_object('slot',e.slot,'item_id',e.item_id,
        'asset_id',i.asset_id,'placed_at',e.placed_at) order by e.slot),'[]'::jsonb)
    from public.room_furniture e join public.item_catalog i on i.id=e.item_id where e.user_id=auth.uid();
$$;
-- Setting a slot is idempotent. Replays cannot consume inventory or spend Coins.
create function public.room_set(p_slot text,p_item_id uuid default null) returns jsonb
language plpgsql security definer set search_path='' as $$
declare actor uuid:=auth.uid(); owned integer;
begin
    if actor is null then raise exception 'Authentication required' using errcode='42501'; end if;
    if p_slot is null or p_slot not in ('BED','LAMP','CARPET') then raise exception 'Invalid slot' using errcode='23514'; end if;
    perform 1 from public.wallets where user_id=actor for update;
    if not found then raise exception 'Account unavailable' using errcode='23503'; end if;
    if p_item_id is null then
        delete from public.room_furniture where user_id=actor and slot=p_slot;
    else
        perform 1 from public.furniture_slots s join public.item_catalog i on i.id=s.item_id
            where s.item_id=p_item_id and s.slot=p_slot and i.category='FURNITURE' for share of s,i;
        if not found then raise exception 'Furniture does not fit slot' using errcode='23514'; end if;
        select quantity into owned from public.user_inventory where user_id=actor and item_id=p_item_id for update;
        if not found or owned<>1 then raise exception 'Furniture not owned' using errcode='42501'; end if;
        insert into public.room_furniture(user_id,slot,item_id) values(actor,p_slot,p_item_id)
            on conflict(user_id,slot) do update set item_id=excluded.item_id,placed_at=clock_timestamp()
            where public.room_furniture.item_id<>excluded.item_id;
    end if;
    return public.room_state();
end $$;
revoke all on function pawquest_private.check_furniture_slot(),pawquest_private.freeze_furniture_type(),
    public.room_state(),public.room_set(text,uuid) from public,anon,authenticated,service_role;
grant execute on function public.room_state(),public.room_set(text,uuid) to authenticated;
commit;
