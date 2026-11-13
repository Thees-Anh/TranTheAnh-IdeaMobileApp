begin;
-- Preserve reward receipts and their unique daily slots; debits have no reward day.
alter table public.wallet_transactions
    alter column quest_session_id drop not null,
    alter column reward_day drop not null,
    alter column reward_slot drop not null,
    add column request_id uuid,
    add column item_id uuid references public.item_catalog(id),
    add column quantity integer,
    add column unit_price integer,
    drop constraint wallet_transactions_kind_check,
    drop constraint wallet_transactions_coins_check,
    drop constraint wallet_transactions_pet_xp_check,
    drop constraint wallet_transactions_check,
    add constraint ledger_balance_nonnegative check(balance_after>=0),
    add constraint purchase_request_unique unique(user_id,request_id),
    add constraint ledger_shape check(
      (kind='quest_reward' and quest_session_id is not null and reward_day is not null and reward_slot is not null
       and coins between 0 and 80 and pet_xp in (5,10,20,30,40,50) and balance_after>=coins
       and request_id is null and item_id is null and quantity is null and unit_price is null)
      or
      (kind='shop_purchase' and quest_session_id is null and reward_day is null and reward_slot is null
       and pet_xp=0 and request_id is not null and item_id is not null and quantity is not null and quantity between 1 and 100
       and unit_price is not null and unit_price between 0 and 1000000 and coins=-(quantity::bigint*unit_price)));
drop trigger reward_prepare on public.wallet_transactions;
drop trigger reward_apply on public.wallet_transactions;
create trigger reward_prepare before insert on public.wallet_transactions
    for each row when(new.kind='quest_reward') execute function pawquest_private.prepare_reward();
create trigger reward_apply after insert on public.wallet_transactions
    for each row when(new.kind='quest_reward') execute function pawquest_private.apply_reward();

create function pawquest_private.prepare_purchase() returns trigger
language plpgsql security definer set search_path='' as $$
declare balance bigint; item public.item_catalog%rowtype; owned integer;
begin
    if new.request_id is null or new.quantity is null or new.quantity not between 1 and 100 then
        raise exception 'Invalid purchase quantity or request' using errcode='P1101'; end if;
    select coins into balance from public.wallets where user_id=new.user_id for update;
    if not found then raise exception 'Account unavailable' using errcode='P1102'; end if;
    select * into item from public.item_catalog where id=new.item_id for share;
    if not found or not item.active then raise exception 'Item unavailable' using errcode='P1103'; end if;
    if item.max_quantity=1 and new.quantity<>1 then raise exception 'Unique quantity invalid' using errcode='P1101'; end if;
    select quantity into owned from public.user_inventory where user_id=new.user_id and item_id=new.item_id for update;
    if item.max_quantity=1 and coalesce(owned,0)>0 then raise exception 'Already owned' using errcode='P1104'; end if;
    if coalesce(owned,0)::bigint+new.quantity>item.max_quantity then raise exception 'Inventory full' using errcode='P1105'; end if;
    new.unit_price:=item.price_coins;
    new.coins:=-(new.quantity::bigint*item.price_coins);
    if balance+new.coins<0 then raise exception 'Insufficient balance' using errcode='P1106'; end if;
    new.balance_after:=balance+new.coins;
    new.pet_xp:=0; new.quest_session_id:=null; new.reward_day:=null; new.reward_slot:=null;
    new.created_at:=clock_timestamp();
    return new;
end $$;
create function pawquest_private.apply_purchase() returns trigger
language plpgsql security definer set search_path='' as $$
begin
    perform pawquest_private.adjust_inventory(new.user_id,new.item_id,new.quantity);
    update public.wallets set coins=new.balance_after where user_id=new.user_id;
    return new;
end $$;
create trigger purchase_prepare before insert on public.wallet_transactions
    for each row when(new.kind='shop_purchase') execute function pawquest_private.prepare_purchase();
create trigger purchase_apply after insert on public.wallet_transactions
    for each row when(new.kind='shop_purchase') execute function pawquest_private.apply_purchase();
revoke all on function pawquest_private.prepare_purchase(),pawquest_private.apply_purchase() from public,anon,authenticated,service_role;

create function public.shop_purchase(p_item_id uuid,p_quantity integer,p_request_id uuid) returns jsonb
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
        if receipt.item_id<>p_item_id or receipt.quantity<>p_quantity then
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
revoke all on function public.shop_purchase(uuid,integer,uuid) from public,anon,authenticated,service_role;
grant execute on function public.shop_purchase(uuid,integer,uuid) to authenticated;
comment on table public.wallet_transactions is 'Append-only authoritative quest credits and shop debits; purchases have no reward day and cannot change reward-day limits.';
commit;
