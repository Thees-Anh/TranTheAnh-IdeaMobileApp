begin;
-- Bistro purchases reuse the existing authoritative purchase ledger and inventory.
create table public.bistro_foods (
 item_id uuid primary key references public.item_catalog(id),
 menu_order integer not null unique check(menu_order between 1 and 12),
 tier text not null check(tier in ('BASIC','TASTY','PREMIUM','ROYAL')),
 meal_type text not null check(meal_type in ('food','drink')),
 hunger_gain integer not null check(hunger_gain between 0 and 100),
 happiness_gain integer not null check(happiness_gain between 0 and 100),
 xp_gain integer not null check(xp_gain between 0 and 1000),
 version integer not null default 1 check(version>0)
);
create table public.bistro_meals (
 id uuid primary key default gen_random_uuid(), user_id uuid not null references auth.users(id),
 request_id uuid not null, item_id uuid not null references public.bistro_foods(item_id),
 wallet_transaction_id uuid not null unique references public.wallet_transactions(id),
 menu_version integer not null check(menu_version>0), item_name text not null, asset_id text not null,
 meal_type text not null check(meal_type in ('food','drink')),
 price_coins integer not null check(price_coins>0), coins_balance_after bigint not null check(coins_balance_after>=0),
 hunger_granted integer not null check(hunger_granted between 0 and 100),
 happiness_granted integer not null check(happiness_granted between 0 and 100),
 xp_granted integer not null check(xp_granted between 0 and 1000),
 pet_id uuid not null references public.pets(id), species_code text not null,
 created_at timestamptz not null default clock_timestamp(), unique(user_id,request_id)
);
create index bistro_history_owner on public.bistro_meals(user_id,created_at desc,id desc);
alter table public.bistro_foods enable row level security;
alter table public.bistro_meals enable row level security;
revoke all on public.bistro_foods,public.bistro_meals from public,anon,authenticated,service_role;
grant select on public.bistro_foods,public.bistro_meals to authenticated;
create policy bistro_catalog_read on public.bistro_foods for select to authenticated using(true);
create policy bistro_history_read on public.bistro_meals for select to authenticated using(user_id=(select auth.uid()));
create trigger receipt_immutable before update or delete on public.bistro_meals
 for each row execute function pawquest_private.reject_ledger_mutation();

create function public.bistro_menu() returns jsonb language sql stable security invoker set search_path='' as $$
 select coalesce(jsonb_agg(jsonb_build_object('item_id',c.id,'asset_id',c.asset_id,'name',c.name,
  'price_coins',c.price_coins,'tier',f.tier,'meal_type',f.meal_type,'menu_order',f.menu_order,
  'hunger_gain',f.hunger_gain,'happiness_gain',f.happiness_gain,'xp_gain',f.xp_gain,'version',f.version)
  order by c.price_coins,f.menu_order),'[]'::jsonb)
 from public.bistro_foods f join public.item_catalog c on c.id=f.item_id where c.active and c.category='FOOD';
$$;

create function public.bistro_feed(p_item_id uuid,p_request_id uuid,p_expected_price integer) returns jsonb
language plpgsql security definer set search_path='' as $$
declare actor uuid:=auth.uid(); meal public.bistro_meals%rowtype; item public.item_catalog%rowtype;
 config public.bistro_foods%rowtype; p public.pets%rowtype; purchase jsonb; stamp timestamptz;
 hunger_added integer; happy_added integer; balance bigint;
begin
 if actor is null then raise exception 'Authentication required' using errcode='42501'; end if;
 if p_item_id is null or p_request_id is null or p_expected_price is null or p_expected_price<=0 then
  return jsonb_build_object('status','invalid_request','receipt',null); end if;
 -- Same lock order as care, purchase, wheel and case: wallet before pet/inventory.
 select coins into balance from public.wallets where user_id=actor for update;
 if not found then return jsonb_build_object('status','account_unavailable','receipt',null); end if;
 select * into meal from public.bistro_meals where user_id=actor and request_id=p_request_id;
 if found then
  if meal.item_id<>p_item_id then return jsonb_build_object('status','request_conflict','receipt',null); end if;
  return jsonb_build_object('status','fed','receipt',to_jsonb(meal));
 end if;
 if exists(select 1 from public.wallet_transactions where user_id=actor and request_id=p_request_id) then
  return jsonb_build_object('status','request_conflict','receipt',null); end if;
 select * into config from public.bistro_foods where item_id=p_item_id for share;
 if not found then return jsonb_build_object('status','item_unavailable','receipt',null); end if;
 select * into item from public.item_catalog where id=p_item_id for share;
 if not found or not item.active or item.category<>'FOOD' or item.price_coins<=0 then
  return jsonb_build_object('status','item_unavailable','receipt',null); end if;
 -- Client's displayed price is only a stale-price guard, never the charge source.
 if item.price_coins<>p_expected_price then return jsonb_build_object('status','price_changed','receipt',null); end if;
 perform public.pet_state();
 select * into p from public.pets where user_id=actor for update;
 if not found then return jsonb_build_object('status','account_unavailable','receipt',null); end if;
 if p.sleep_until is not null then return jsonb_build_object('status','pet_sleeping','receipt',null); end if;
 if balance<item.price_coins then return jsonb_build_object('status','insufficient_balance','receipt',null); end if;
 purchase:=public.shop_purchase(item.id,1,p_request_id);
 if purchase->>'status'<>'purchased' then return purchase; end if;
 -- Consume exactly the newly purchased unit; existing pantry quantities survive.
 perform pawquest_private.adjust_inventory(actor,item.id,-1);
 hunger_added:=least(100,p.hunger+config.hunger_gain)-p.hunger;
 happy_added:=least(100,p.happiness+config.happiness_gain)-p.happiness;
 update public.pets set hunger=hunger+hunger_added,happiness=happiness+happy_added,xp=xp+config.xp_gain
  where id=p.id;
 stamp:=clock_timestamp();
 -- Preserve existing level-up gifts; Bistro does not create quest completions/streak days.
 perform pawquest_private.progress_sync(actor,stamp);
 insert into public.bistro_meals(user_id,request_id,item_id,wallet_transaction_id,menu_version,item_name,
  asset_id,meal_type,price_coins,coins_balance_after,hunger_granted,happiness_granted,xp_granted,pet_id,species_code,created_at)
 values(actor,p_request_id,item.id,(purchase->'receipt'->>'id')::uuid,config.version,item.name,item.asset_id,
  config.meal_type,item.price_coins,(purchase->'receipt'->>'balance_after')::bigint,
  hunger_added,happy_added,config.xp_gain,p.id,p.species_code,stamp) returning * into meal;
 return jsonb_build_object('status','fed','receipt',to_jsonb(meal));
end $$;
revoke all on function public.bistro_menu(),public.bistro_feed(uuid,uuid,integer) from public,anon,authenticated,service_role;
grant execute on function public.bistro_menu(),public.bistro_feed(uuid,uuid,integer) to authenticated;
comment on function public.bistro_feed(uuid,uuid,integer) is 'Atomic purchase-and-consume. Authenticated user only; stable request UUID replays the original receipt. No hydration bonus is implied by drink visuals.';
commit;
