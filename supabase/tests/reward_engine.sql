begin;
create function pg_temp.reward_assert(value boolean,message text) returns void language plpgsql as $$
begin if value is distinct from true then raise exception 'Reward ASSERT: %',message; end if; end $$;
create function pg_temp.reward_error(statement text,expected text) returns void language plpgsql as $$
declare actual text;
begin
    begin execute statement;
    exception when others then
        get stacked diagnostics actual=returned_sqlstate;
        if actual=expected then return; end if;
        raise exception 'Expected %, got %',expected,actual;
    end;
    raise exception 'Expected rejection %',expected;
end $$;
-- Exercise both endpoints of every tier through the real claim/ledger path.
do $$
declare v record; actor uuid; quest uuid; session_id uuid; result jsonb;
begin
    for v in select * from (values (5,10,5),(14,10,5),(15,20,10),(29,20,10),
        (30,35,20),(44,35,20),(45,50,30),(59,50,30),(60,65,40),(74,65,40),
        (75,80,50),(90,80,50)) t(minutes,coins,xp) loop
        actor:=gen_random_uuid(); quest:=gen_random_uuid(); session_id:=gen_random_uuid();
        insert into auth.users(id,email) values(actor,actor::text||'@example.invalid');
        insert into public.quests(id,user_id,title,duration_minutes) values(quest,actor,'Boundary',v.minutes);
        insert into public.quest_sessions(id,user_id,quest_id,duration_minutes,started_at,ends_at,status)
            values(session_id,actor,quest,v.minutes,'2026-01-01 12:00Z'::timestamptz-v.minutes*interval '1 minute','2026-01-01 12:00Z','completed');
        perform set_config('request.jwt.claim.sub',actor::text,true);
        result:=public.reward_claim(session_id);
        perform pg_temp.reward_assert((result->'reward'->>'coins')::integer=v.coins,'tier Coins');
        perform pg_temp.reward_assert((result->'reward'->>'pet_xp')::integer=v.xp,'tier XP');
        perform pg_temp.reward_error(format('select public.reward_claim(%L)',session_id),'23505');
        perform pg_temp.reward_assert((select coins=v.coins from public.wallets where user_id=actor),'one wallet credit');
        perform pg_temp.reward_assert((select xp=v.xp from public.pets where user_id=actor),'one XP credit');
    end loop;
end $$;
insert into auth.users(id,email) values('b1000000-0000-0000-0000-000000000001','midnight@example.invalid');
insert into public.quests(id,user_id,title,duration_minutes) values
('b2000000-0000-0000-0000-000000000001','b1000000-0000-0000-0000-000000000001','Midnight',75);
insert into public.quest_sessions(id,user_id,quest_id,duration_minutes,started_at,ends_at,status)
select ('b3000000-0000-0000-0000-'||lpad(i::text,12,'0'))::uuid,'b1000000-0000-0000-0000-000000000001',
    'b2000000-0000-0000-0000-000000000001',75,stamp-interval '75 minutes',stamp,'completed'
from generate_series(1,7) i cross join lateral
    (select case when i<=6 then '2026-01-01 23:59:59.999Z'::timestamptz else '2026-01-02 00:00:00Z'::timestamptz end stamp) t;
set local role authenticated;
select set_config('request.jwt.claim.sub','b1000000-0000-0000-0000-000000000001',true);
select public.reward_claim(('b3000000-0000-0000-0000-'||lpad(i::text,12,'0'))::uuid) from generate_series(1,3) i;
select pg_temp.reward_assert((public.reward_reconcile('b3000000-0000-0000-0000-000000000004')->'reward_offer'->>'coins')::integer=10,'partial quote');
select pg_temp.reward_assert((public.reward_claim('b3000000-0000-0000-0000-000000000004')->'reward'->>'coins')::integer=10,'partial award');
select pg_temp.reward_assert((public.reward_claim('b3000000-0000-0000-0000-000000000005')->'reward'->>'coins')::integer=0,'zero Coins at cap');
select pg_temp.reward_assert((select xp=250 from public.pets),'full XP at cap');
select pg_temp.reward_assert((public.reward_reconcile('b3000000-0000-0000-0000-000000000006')->'reward_offer'->>'limit_reached')::boolean,'five-session quote');
select pg_temp.reward_error($q$select public.reward_claim('b3000000-0000-0000-0000-000000000006')$q$,'23514');
select pg_temp.reward_assert((select coins=250 from public.wallets),'cap rejection leaves wallet');
select pg_temp.reward_assert((select xp=250 from public.pets),'cap rejection leaves XP');
set local timezone='Pacific/Honolulu';
select pg_temp.reward_assert(public.reward_reconcile('b3000000-0000-0000-0000-000000000007')->'reward_offer'->>'reward_day'='2026-01-02','UTC midnight day');
select public.reward_claim('b3000000-0000-0000-0000-000000000007');
set local timezone='Asia/Bangkok';
select pg_temp.reward_assert((select count(*)=5 and sum(coins)=250 from public.wallet_transactions where reward_day='2026-01-01'),'late claims keep completion day cap');
select pg_temp.reward_assert((select count(*)=1 and sum(coins)=80 from public.wallet_transactions where reward_day='2026-01-02'),'new UTC day independent');
select pg_temp.reward_assert((select coins=330 from public.wallets),'two-day wallet');
reset role;
-- Inject a failure AFTER the wallet update to prove the entire claim rolls back.
insert into auth.users(id,email) values('b1000000-0000-0000-0000-000000000002','atomic@example.invalid');
insert into public.quests(id,user_id,title,duration_minutes) values
('b2000000-0000-0000-0000-000000000002','b1000000-0000-0000-0000-000000000002','Atomic',5);
insert into public.quest_sessions(id,user_id,quest_id,duration_minutes,started_at,ends_at,status) values
('b3000000-0000-0000-0000-000000000008','b1000000-0000-0000-0000-000000000002',
'b2000000-0000-0000-0000-000000000002',5,'2026-01-03 12:00Z','2026-01-03 12:05Z','completed');
create function pg_temp.fail_xp() returns trigger language plpgsql as $$
begin raise exception 'Injected XP update failure'; end $$;
create trigger test_fail_xp before update on public.pets for each row execute function pg_temp.fail_xp();
set local role authenticated;
select set_config('request.jwt.claim.sub','b1000000-0000-0000-0000-000000000002',true);
select pg_temp.reward_error($q$select public.reward_claim('b3000000-0000-0000-0000-000000000008')$q$,'P0001');
select pg_temp.reward_assert((select coins=0 from public.wallets),'atomic wallet rollback');
select pg_temp.reward_assert((select xp=0 from public.pets),'atomic XP rollback');
select pg_temp.reward_assert((select count(*)=0 from public.wallet_transactions),'atomic receipt rollback');
select pg_temp.reward_assert((select status='completed' from public.quest_sessions),'atomic session rollback');
reset role;
select pg_temp.reward_assert(not has_function_privilege('anon','public.reward_claim(uuid)','execute'),'anonymous denied');
rollback;
\echo PASS reward tier endpoints, strict duplicates, partial/zero awards, caps, late UTC midnight claims and atomic failure rollback
