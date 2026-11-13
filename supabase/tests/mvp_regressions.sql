begin;
-- Spending must never reopen the daily earned-Coins allowance.
insert into auth.users(id,email) values('ac100000-0000-0000-0000-000000000001','mvp-cap@example.invalid');
insert into public.quests(id,user_id,title,duration_minutes) values
 ('ac200000-0000-0000-0000-000000000001','ac100000-0000-0000-0000-000000000001','MVP reward cap',75);
insert into public.quest_sessions(id,user_id,quest_id,duration_minutes,started_at,ends_at,status)
select ('ac300000-0000-0000-0000-'||lpad(i::text,12,'0'))::uuid,
 'ac100000-0000-0000-0000-000000000001','ac200000-0000-0000-0000-000000000001',75,
 '2026-01-01 10:00Z','2026-01-01 11:15Z','completed' from generate_series(1,6) i;
set local role authenticated;
select set_config('request.jwt.claim.sub','ac100000-0000-0000-0000-000000000001',true);
select public.reward_claim(('ac300000-0000-0000-0000-'||lpad(i::text,12,'0'))::uuid) from generate_series(1,4) i;
select public.shop_purchase('e1000000-0000-0000-0000-000000000001',10,'ac400000-0000-0000-0000-000000000001');
do $$ declare r jsonb; begin
    r:=public.reward_claim('ac300000-0000-0000-0000-000000000005');
    if (r->'reward'->>'coins')::integer<>0 then raise exception 'Spending reopened daily allowance'; end if;
    if (select coins<>150 from public.wallets where user_id=auth.uid()) then raise exception 'Wallet mismatch after capped reward'; end if;
    if (select xp<>250 from public.pets where user_id=auth.uid()) then raise exception 'Full XP at cap lost'; end if;
    if (select sum(coins)<>250 or count(*)<>5 from public.wallet_transactions where user_id=auth.uid() and kind='quest_reward') then
        raise exception 'Reward totals include debit'; end if;
    begin
        perform public.reward_claim('ac300000-0000-0000-0000-000000000006');
        raise exception 'Sixth claim was accepted';
    exception when check_violation then null; end;
end $$;
reset role;
rollback;
\echo PASS MVP spending-at-cap regression: 250 earned, 100 spent, fifth reward zero Coins/full XP, sixth rejected
