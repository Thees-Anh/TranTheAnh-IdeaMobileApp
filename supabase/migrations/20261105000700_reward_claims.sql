-- Tier calculation, cap clamping and atomic posting remain in the ledger triggers.
begin;
create function pawquest_private.reward_envelope(snapshot jsonb) returns jsonb
language plpgsql security definer set search_path='' as $$
declare s public.quest_sessions%rowtype; tier record; used integer; paid integer;
begin
    select * into s from public.quest_sessions
        where id=(snapshot->'session'->>'id')::uuid and user_id=auth.uid();
    if not found then return snapshot || '{"reward_offer":null}'::jsonb; end if;
    select * into tier from pawquest_private.reward_for_duration(s.duration_minutes);
    select count(*)::integer,coalesce(sum(coins),0)::integer into used,paid
        from public.wallet_transactions where user_id=s.user_id and reward_day=s.reward_day;
    return snapshot || jsonb_build_object('reward_offer',jsonb_build_object(
        'tier_coins',tier.coins,'coins',least(tier.coins,greatest(250-paid,0)),
        'pet_xp',tier.pet_xp,'reward_day',s.reward_day,'rewarded_sessions',used,
        'limit_reached',used>=5,
        'can_claim',used<5 and s.status in ('active','completed') and clock_timestamp()>=s.ends_at));
end $$;

create function public.reward_reconcile(p_session_id uuid default null) returns jsonb
language plpgsql security definer set search_path='' as $$
begin
    -- focus_reconcile locks this user's wallet; the quote shares the transaction.
    return pawquest_private.reward_envelope(public.focus_reconcile(p_session_id));
end $$;

create function public.reward_claim(p_session_id uuid) returns jsonb
language plpgsql security definer set search_path='' as $$
declare actor uuid:=auth.uid();
begin
    if actor is null then raise exception 'Authentication required' using errcode='42501'; end if;
    perform 1 from public.wallets where user_id=actor for update;
    if not found then raise exception 'Account not initialized' using errcode='23503'; end if;
    perform 1 from public.quest_sessions where id=p_session_id and user_id=actor for update;
    if not found then raise exception 'Session unavailable' using errcode='42501'; end if;
    if exists(select 1 from public.wallet_transactions where quest_session_id=p_session_id) then
        raise exception 'Reward already claimed' using errcode='23505';
    end if;
    perform public.claim_quest_reward(p_session_id);
    return pawquest_private.reward_envelope(pawquest_private.focus_snapshot(p_session_id));
end $$;
revoke all on function pawquest_private.reward_envelope(jsonb) from public,anon,authenticated,service_role;
revoke all on function public.reward_reconcile(uuid),public.reward_claim(uuid) from public,anon,authenticated,service_role;
grant execute on function public.reward_reconcile(uuid),public.reward_claim(uuid) to authenticated;
commit;
