begin;
create table public.pet_species (
    code text primary key check(code ~ '^[a-z][a-z0-9_]{0,63}$'),
    name text not null check(length(btrim(name)) between 1 and 60),
    unlock_level integer not null check(unlock_level=1 or (unlock_level>=5 and unlock_level%5=0)),
    renderer_key text not null check(renderer_key ~ '^[a-z][a-z0-9_]{0,63}$'),
    published boolean not null default false,
    created_at timestamptz not null default clock_timestamp() check(isfinite(created_at)),
    unique(code,renderer_key)
);
alter table public.pet_species enable row level security;
revoke all on public.pet_species from public,anon,authenticated,service_role;
grant select on public.pet_species to authenticated;
create policy species_read_published on public.pet_species for select to authenticated using(published);
insert into public.pet_species(code,name,unlock_level,renderer_key,published)
    values('mochi_cat','Mochi',1,'prototype_cat',true);
alter table public.pets
    add column species_code text not null default 'mochi_cat',
    add column renderer_key text not null default 'prototype_cat',
    add constraint pets_species_art foreign key(species_code,renderer_key) references public.pet_species(code,renderer_key);

-- XP is monotonic and server-owned; eligibility is derived without a client grant endpoint.
create function public.pet_collection() returns jsonb
language plpgsql security definer set search_path='' as $$
declare actor uuid:=auth.uid(); p public.pets%rowtype; account_level bigint;
begin
    if actor is null then raise exception 'Authentication required' using errcode='42501'; end if;
    select * into p from public.pets where user_id=actor;
    if not found then raise exception 'Account not initialized' using errcode='23503'; end if;
    account_level:=p.xp/(pawquest_private.pet_care_balance()->>'xpPerLevel')::integer+1;
    return jsonb_build_object('active_code',p.species_code,'level',account_level,'species',
        (select coalesce(jsonb_agg(jsonb_build_object('code',s.code,'name',s.name,'unlock_level',s.unlock_level,
            'renderer_key',s.renderer_key,'unlocked',account_level>=s.unlock_level) order by s.unlock_level,s.code),'[]'::jsonb)
         from public.pet_species s where s.published));
end $$;

create function public.pet_select_species(p_code text) returns jsonb
language plpgsql security definer set search_path='' as $$
declare actor uuid:=auth.uid(); p public.pets%rowtype; species public.pet_species%rowtype;
begin
    if actor is null then raise exception 'Authentication required' using errcode='42501'; end if;
    perform 1 from public.wallets where user_id=actor for update;
    if not found then raise exception 'Account not initialized' using errcode='23503'; end if;
    select * into p from public.pets where user_id=actor for update;
    if not found then raise exception 'Pet not initialized' using errcode='23503'; end if;
    select * into species from public.pet_species where code=p_code and published for share;
    if not found then raise exception 'Pet unavailable' using errcode='23514'; end if;
    if p.xp/(pawquest_private.pet_care_balance()->>'xpPerLevel')::integer+1 < species.unlock_level then
        raise exception 'Pet locked until required level' using errcode='42501';
    end if;
    if p.species_code<>species.code then
        update public.pets set species_code=species.code,renderer_key=species.renderer_key,name=species.name where id=p.id;
    end if;
    -- Reconciles decay/rest without granting XP, Coins, food, or resetting stats.
    return public.pet_state();
end $$;
revoke all on function public.pet_collection(),public.pet_select_species(text) from public,anon,authenticated,service_role;
grant execute on function public.pet_collection(),public.pet_select_species(text) to authenticated;
comment on table public.pet_species is 'Admin-published pet appearances; account XP unlocks level 1/5/10/15/...; clients cannot grant or edit species.';
commit;
