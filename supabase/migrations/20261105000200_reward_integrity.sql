begin;

create function pawquest_private.reward_for_duration(minutes integer)
returns table(coins integer, pet_xp integer)
language sql immutable strict set search_path = '' as $$
    select case when minutes < 15 then 10 when minutes < 30 then 20
                when minutes < 45 then 35 when minutes < 60 then 50
                when minutes < 75 then 65 else 80 end,
           case when minutes < 15 then 5 when minutes < 30 then 10
                when minutes < 45 then 20 when minutes < 60 then 30
                when minutes < 75 then 40 else 50 end
    where minutes between 5 and 90;
$$;

-- Ledger creation is the only reward posting path. Serialize every user's
-- credits on their wallet row, including claims for different UTC days.
create function pawquest_private.prepare_reward() returns trigger
language plpgsql security definer set search_path = '' as $$
declare
    session_row public.quest_sessions%rowtype;
    current_balance bigint;
    daily_count integer;
    daily_coins integer;
    tier_coins integer;
    tier_xp integer;
begin
    select coins into current_balance from public.wallets where user_id = new.user_id for update;
    if not found then raise exception 'Wallet missing' using errcode = '23503'; end if;
    select * into session_row from public.quest_sessions
        where id = new.quest_session_id and user_id = new.user_id for update;
    if not found then raise exception 'Session missing' using errcode = '23503'; end if;
    if session_row.status not in ('active', 'completed') or clock_timestamp() < session_row.ends_at then
        raise exception 'Session is not eligible for a reward' using errcode = '23514';
    end if;
    select count(*)::integer, coalesce(sum(coins), 0)::integer into daily_count, daily_coins
        from public.wallet_transactions where user_id = new.user_id and reward_day = session_row.reward_day;
    if daily_count >= 5 then raise exception 'Daily rewarded session limit reached' using errcode = '23514'; end if;
    if daily_coins > 250 then raise exception 'Invalid daily reward totals' using errcode = '23514'; end if;
    select coins, pet_xp into tier_coins, tier_xp
        from pawquest_private.reward_for_duration(session_row.duration_minutes);
    new.kind := 'quest_reward';
    new.reward_day := session_row.reward_day;
    new.reward_slot := daily_count + 1;
    new.coins := least(tier_coins, 250 - daily_coins);
    new.pet_xp := tier_xp;
    new.balance_after := current_balance + new.coins;
    new.created_at := clock_timestamp();
    return new;
end;
$$;

create function pawquest_private.apply_reward() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
    update public.wallets set coins = new.balance_after where user_id = new.user_id;
    update public.pets set xp = xp + new.pet_xp where user_id = new.user_id;
    if not found then raise exception 'Pet missing' using errcode = '23503'; end if;
    update public.quest_sessions set status = 'claimed', claimed_at = new.created_at
        where id = new.quest_session_id;
    return new;
end;
$$;

create function pawquest_private.reject_ledger_mutation() returns trigger
language plpgsql set search_path = '' as $$
begin
    raise exception 'Wallet transactions are append-only' using errcode = '55000';
end;
$$;

create trigger reward_prepare before insert on public.wallet_transactions
    for each row execute function pawquest_private.prepare_reward();
create trigger reward_apply after insert on public.wallet_transactions
    for each row execute function pawquest_private.apply_reward();
create trigger wallet_transactions_immutable before update or delete on public.wallet_transactions
    for each row execute function pawquest_private.reject_ledger_mutation();

create function public.start_quest_session(p_quest_id uuid) returns public.quest_sessions
language plpgsql security definer set search_path = '' as $$
declare
    actor uuid := auth.uid();
    quest_row public.quests%rowtype;
    result public.quest_sessions%rowtype;
    server_now timestamptz;
begin
    if actor is null then raise exception 'Authentication required' using errcode = '42501'; end if;
    perform 1 from public.wallets where user_id = actor for update;
    if not found then raise exception 'Account not initialized' using errcode = '23503'; end if;
    server_now := clock_timestamp();
    select * into quest_row from public.quests where id = p_quest_id and user_id = actor and not archived for share;
    if not found then raise exception 'Quest unavailable' using errcode = '42501'; end if;
    -- Completion is timestamp-based; expired sessions no longer block a new start.
    update public.quest_sessions set status = 'completed'
        where user_id = actor and status = 'active' and ends_at <= server_now;
    if exists(select 1 from public.quest_sessions where user_id = actor and status = 'active') then
        raise exception 'An active session already exists' using errcode = '23505';
    end if;
    insert into public.quest_sessions(user_id, quest_id, duration_minutes, started_at, ends_at)
        values(actor, p_quest_id, quest_row.duration_minutes, server_now,
               server_now + quest_row.duration_minutes * interval '1 minute') returning * into result;
    return result;
end;
$$;

create function public.cancel_quest_session(p_session_id uuid) returns public.quest_sessions
language plpgsql security definer set search_path = '' as $$
declare
    actor uuid := auth.uid();
    result public.quest_sessions%rowtype;
    server_now timestamptz;
begin
    if actor is null then raise exception 'Authentication required' using errcode = '42501'; end if;
    perform 1 from public.wallets where user_id = actor for update;
    select * into result from public.quest_sessions where id = p_session_id and user_id = actor for update;
    if not found then raise exception 'Session unavailable' using errcode = '42501'; end if;
    if result.status = 'cancelled' then return result; end if;
    server_now := clock_timestamp();
    if result.status <> 'active' or result.ends_at <= server_now then
        raise exception 'Only an unfinished active session can be cancelled' using errcode = '23514';
    end if;
    update public.quest_sessions set status = 'cancelled', cancelled_at = server_now
        where id = p_session_id returning * into result;
    return result;
end;
$$;

create function public.claim_quest_reward(p_session_id uuid) returns public.wallet_transactions
language plpgsql security definer set search_path = '' as $$
declare
    actor uuid := auth.uid();
    result public.wallet_transactions%rowtype;
begin
    if actor is null then raise exception 'Authentication required' using errcode = '42501'; end if;
    perform 1 from public.wallets where user_id = actor for update;
    if not found then raise exception 'Account not initialized' using errcode = '23503'; end if;
    perform 1 from public.quest_sessions where id = p_session_id and user_id = actor for update;
    if not found then raise exception 'Session unavailable' using errcode = '42501'; end if;
    -- Replay returns the original receipt, even after the day has reached its limits.
    select * into result from public.wallet_transactions where quest_session_id = p_session_id;
    if found then return result; end if;
    -- Trigger fills day, slot, amounts, timestamp, and resulting balance.
    insert into public.wallet_transactions(user_id, quest_session_id)
        values(actor, p_session_id) returning * into result;
    return result;
end;
$$;

revoke all on function pawquest_private.reward_for_duration(integer), pawquest_private.prepare_reward(),
    pawquest_private.apply_reward(), pawquest_private.reject_ledger_mutation()
    from public, anon, authenticated, service_role;
revoke all on function public.start_quest_session(uuid), public.cancel_quest_session(uuid),
    public.claim_quest_reward(uuid) from public, anon, authenticated, service_role;
grant execute on function public.start_quest_session(uuid), public.cancel_quest_session(uuid),
    public.claim_quest_reward(uuid) to authenticated;

comment on table public.wallet_transactions is 'Append-only quest reward receipts; grants and trigger enforce authoritative Coins/XP, UTC-day limits and exactly-once claims. No purchase/debit flow yet.';
comment on column public.quest_sessions.reward_day is 'Scheduled completion date in UTC. Late claims consume this original day; device timezone does not apply.';
commit;
