-- psql -v ON_ERROR_STOP=1 -f supabase/tests/integrity.sql on a disposable migrated DB.
-- Uses no extension; rolls back all fixture data. Requires postgres privileges.
begin;
create function pg_temp.assert_true(value boolean, message text) returns void
language plpgsql as $$ begin
    if value is distinct from true then raise exception 'ASSERT: %', message; end if;
end $$;
create function pg_temp.expect_error(statement text, expected_code text) returns void
language plpgsql as $$ begin
    begin execute statement;
    exception when others then
        if sqlstate = expected_code then return; end if;
        raise exception 'Expected SQLSTATE %, got %: %', expected_code, sqlstate, sqlerrm;
    end;
    raise exception 'Expected SQLSTATE %, but statement succeeded: %', expected_code, statement;
end $$;

select pg_temp.assert_true((select count(*) = 6 from pg_class c join pg_namespace n on n.oid = c.relnamespace
    where n.nspname = 'public' and c.relname in ('profiles','pets','quests','quest_sessions','wallets','wallet_transactions')
    and c.relrowsecurity), 'all six tables use RLS');
insert into auth.users(id,email) values
    ('10000000-0000-0000-0000-000000000001','pq-a@example.invalid'),
    ('10000000-0000-0000-0000-000000000002','pq-b@example.invalid');
select pg_temp.assert_true((select count(*) = 2 from public.wallets), 'wallet bootstrap');
select pg_temp.assert_true((select count(*) = 2 from public.pets where xp = 0), 'pet bootstrap');
select pg_temp.assert_true((select bool_and(r.coins=e.coins and r.pet_xp=e.xp)
    from (values (5,10,5),(14,10,5),(15,20,10),(29,20,10),(30,35,20),(44,35,20),
        (45,50,30),(59,50,30),(60,65,40),(74,65,40),(75,80,50),(90,80,50)) e(minutes,coins,xp)
    cross join lateral pawquest_private.reward_for_duration(e.minutes) r), 'all reward tier boundaries');
insert into public.quests(id,user_id,title,duration_minutes) values
    ('20000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000001','Focus',5),
    ('20000000-0000-0000-0000-000000000002','10000000-0000-0000-0000-000000000002','Read',5);

set local role anon;
select pg_temp.expect_error('select * from public.wallets', '42501');
select pg_temp.expect_error($q$select public.start_quest_session('20000000-0000-0000-0000-000000000001')$q$, '42501');
reset role;
set local role authenticated;
select set_config('request.jwt.claim.sub','10000000-0000-0000-0000-000000000001',true);
select pg_temp.assert_true((select count(*) = 1 from public.profiles), 'read own profile only');
select pg_temp.assert_true((select count(*) = 1 from public.pets), 'read own pet only');
select pg_temp.assert_true((select count(*) = 1 from public.wallets), 'read own wallet only');
select pg_temp.assert_true((select count(*) = 1 from public.quests), 'read own quests only');
with changed as (update public.profiles set display_name='Stolen'
    where id='10000000-0000-0000-0000-000000000002' returning id)
select pg_temp.assert_true((select count(*)=0 from changed), 'RLS blocks editing another profile');
update public.profiles set display_name = 'Alice';
update public.pets set name = 'Pumpkin';
select pg_temp.expect_error('update public.wallets set coins = 1000', '42501');
select pg_temp.expect_error('update public.pets set xp = 1000', '42501');
select pg_temp.expect_error('update public.pets set hunger = 100', '42501');
select pg_temp.expect_error('update public.profiles set created_at = now()', '42501');
select pg_temp.expect_error('insert into public.quest_sessions default values', '42501');
select pg_temp.expect_error('insert into public.wallet_transactions default values', '42501');
select pg_temp.expect_error('delete from public.quests', '42501');
select pg_temp.expect_error($q$insert into public.quests(user_id,title,duration_minutes)
    values('10000000-0000-0000-0000-000000000002','Steal',5)$q$, '42501');
select pg_temp.expect_error($q$insert into public.quests(title,duration_minutes) values('Short',4)$q$, '23514');
select pg_temp.expect_error($q$insert into public.quests(title,duration_minutes) values('Long',91)$q$, '23514');
select pg_temp.expect_error($q$insert into public.quests(title,duration_minutes) values(' ',5)$q$, '23514');
insert into public.quests(title,duration_minutes) values('Own insert',30);
select pg_temp.expect_error($q$select public.start_quest_session('20000000-0000-0000-0000-000000000002')$q$, '42501');
select public.start_quest_session('20000000-0000-0000-0000-000000000001');
select pg_temp.expect_error($q$select public.start_quest_session('20000000-0000-0000-0000-000000000001')$q$, '23505');
select pg_temp.expect_error($q$select public.claim_quest_reward((select id from public.quest_sessions where status='active'))$q$, '23514');
update public.quests set duration_minutes = 90 where id = '20000000-0000-0000-0000-000000000001';
select pg_temp.assert_true((select duration_minutes = 5 from public.quest_sessions where status='active'), 'duration snapshot cannot change');
select set_config('request.jwt.claim.sub','10000000-0000-0000-0000-000000000000',true);
select pg_temp.assert_true((select count(*) = 0 from public.profiles), 'unknown JWT cannot read profiles');
select set_config('request.jwt.claim.sub','',true);
select pg_temp.expect_error($q$select public.claim_quest_reward('30000000-0000-0000-0000-000000000001')$q$, '42501');
reset role;

-- Composite FK prevents using another owner's quest, even for a privileged insert.
select pg_temp.expect_error($q$insert into public.quest_sessions(user_id,quest_id,duration_minutes,started_at,ends_at)
    values('10000000-0000-0000-0000-000000000002','20000000-0000-0000-0000-000000000001',5,now(),now()+interval '5 minutes')$q$, '23503');
select pg_temp.expect_error($q$insert into public.quest_sessions(user_id,quest_id,duration_minutes,started_at,ends_at)
    values('10000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000001',5,now(),now()+interval '5 minutes')$q$, '23505');
create temporary table active_fixture as select id from public.quest_sessions where status='active';
grant select on active_fixture to authenticated;
set local role authenticated;
select set_config('request.jwt.claim.sub','10000000-0000-0000-0000-000000000002',true);
select pg_temp.expect_error('select public.claim_quest_reward((select id from active_fixture))', '42501');
select pg_temp.expect_error('select public.cancel_quest_session((select id from active_fixture))', '42501');
select pg_temp.assert_true((select count(*)=0 from public.quest_sessions), 'cross-user sessions invisible');
select set_config('request.jwt.claim.sub','10000000-0000-0000-0000-000000000001',true);
select public.cancel_quest_session((select id from active_fixture));
select public.cancel_quest_session((select id from active_fixture));
select pg_temp.expect_error('select public.claim_quest_reward((select id from active_fixture))','23514');
reset role;

-- Late claims and timezone independence: UTC Jan 2, local Jan 3 in Auckland.
set local timezone = 'Pacific/Auckland';
insert into public.quest_sessions(id,user_id,quest_id,duration_minutes,started_at,ends_at,status)
    select ('30000000-0000-0000-0000-' || lpad(i::text,12,'0'))::uuid,
        '10000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000001',75,
        '2026-01-02 22:44:00+00','2026-01-02 23:59:00+00','completed' from generate_series(1,6) i;
select pg_temp.assert_true((select bool_and(reward_day = date '2026-01-02') from public.quest_sessions where status='completed'), 'reward day fixed to UTC');
set local role authenticated;
select set_config('request.jwt.claim.sub','10000000-0000-0000-0000-000000000001',true);
select public.claim_quest_reward(('30000000-0000-0000-0000-' || lpad(i::text,12,'0'))::uuid) from generate_series(1,5) i;
select pg_temp.assert_true((select coins = 250 from public.wallets), '250 daily cap');
select pg_temp.assert_true((select xp = 250 from public.pets), 'full tier XP including capped claims');
select pg_temp.assert_true((select array_agg(coins order by reward_slot) = array[80,80,80,10,0] from public.wallet_transactions), 'partial and XP-only capped rewards');
select pg_temp.expect_error($q$select public.claim_quest_reward('30000000-0000-0000-0000-000000000006')$q$,'23514');
select pg_temp.assert_true(
    (public.claim_quest_reward('30000000-0000-0000-0000-000000000001')).id =
    (select id from public.wallet_transactions where quest_session_id='30000000-0000-0000-0000-000000000001'), 'idempotent receipt replay');
select pg_temp.assert_true((select count(*)=5 and sum(coins)=250 from public.wallet_transactions), 'replay grants nothing twice');
reset role;
select pg_temp.expect_error('update public.wallet_transactions set coins=0','55000');
select pg_temp.expect_error('delete from public.wallet_transactions','55000');

-- A second completion day is independent and also uses the snapshot tier.
insert into public.quest_sessions(id,user_id,quest_id,duration_minutes,started_at,ends_at,status) values
    ('30000000-0000-0000-0000-000000000007','10000000-0000-0000-0000-000000000001',
     '20000000-0000-0000-0000-000000000001',5,'2026-01-03 10:00+00','2026-01-03 10:05+00','completed'),
    ('30000000-0000-0000-0000-000000000008','10000000-0000-0000-0000-000000000002',
     '20000000-0000-0000-0000-000000000002',5,'2026-01-03 10:00+00','2026-01-03 10:05+00','completed');
delete from public.pets where user_id='10000000-0000-0000-0000-000000000002';
set local role authenticated;
select set_config('request.jwt.claim.sub','10000000-0000-0000-0000-000000000002',true);
select pg_temp.expect_error($q$select public.claim_quest_reward('30000000-0000-0000-0000-000000000008')$q$, '23503');
select pg_temp.assert_true((select coins=0 from public.wallets), 'failed pet credit rolls back wallet');
select pg_temp.assert_true((select count(*)=0 from public.wallet_transactions), 'failed pet credit rolls back ledger');
select pg_temp.assert_true((select status='completed' from public.quest_sessions), 'failed claim rolls back session');
reset role;
insert into public.pets(user_id) values('10000000-0000-0000-0000-000000000002');
-- Force a late-stage XP overflow to prove rollback after wallet credit was attempted.
update public.pets set xp=9223372036854775807 where user_id='10000000-0000-0000-0000-000000000002';
set local role authenticated;
select set_config('request.jwt.claim.sub','10000000-0000-0000-0000-000000000002',true);
select pg_temp.expect_error($q$select public.claim_quest_reward('30000000-0000-0000-0000-000000000008')$q$, '22003');
select pg_temp.assert_true((select coins=0 from public.wallets), 'late XP failure rolls back wallet credit');
select pg_temp.assert_true((select count(*)=0 from public.wallet_transactions), 'late XP failure rolls back receipt');
reset role;
update public.pets set xp=0 where user_id='10000000-0000-0000-0000-000000000002';
set local role authenticated;
select set_config('request.jwt.claim.sub','10000000-0000-0000-0000-000000000001',true);
select public.claim_quest_reward('30000000-0000-0000-0000-000000000007');
select pg_temp.assert_true((select coins=260 from public.wallets), 'new reward day has fresh allowance');
select pg_temp.assert_true((select xp=255 from public.pets), 'XP ledger applies once');
select set_config('request.jwt.claim.sub','10000000-0000-0000-0000-000000000002',true);
select pg_temp.assert_true((select count(*)=0 from public.wallet_transactions), 'other ledger invisible');
reset role;
-- Even privileged accidental values are replaced by authoritative trigger values.
insert into public.wallet_transactions(user_id,quest_session_id,reward_day,reward_slot,coins,pet_xp,balance_after)
    values('10000000-0000-0000-0000-000000000002','30000000-0000-0000-0000-000000000008',
        '2000-01-01',5,999,999,999);
select pg_temp.assert_true((select coins=10 and pet_xp=5 and reward_slot=1 and reward_day='2026-01-03'
    from public.wallet_transactions where user_id='10000000-0000-0000-0000-000000000002'), 'trigger authoritatively derives reward fields');
select pg_temp.assert_true((select bool_and(w.coins = (select sum(t.coins) from public.wallet_transactions t where t.user_id=w.user_id))
    from public.wallets w), 'wallet/ledger reconciliation');
select pg_temp.expect_error($q$insert into public.wallet_transactions(user_id,quest_session_id)
    values('10000000-0000-0000-0000-000000000002','30000000-0000-0000-0000-000000000007')$q$, '23503');
-- An expired active row must be finalized before a new session starts.
insert into public.quest_sessions(id,user_id,quest_id,duration_minutes,started_at,ends_at,status) values
    ('30000000-0000-0000-0000-000000000009','10000000-0000-0000-0000-000000000001',
     '20000000-0000-0000-0000-000000000001',5,'2026-01-03 23:58+00','2026-01-04 00:03+00','active');
select pg_temp.assert_true((select reward_day='2026-01-04' from public.quest_sessions
    where id='30000000-0000-0000-0000-000000000009'), 'cross-midnight reward uses completion day');
set local role authenticated;
select set_config('request.jwt.claim.sub','10000000-0000-0000-0000-000000000001',true);
select public.start_quest_session('20000000-0000-0000-0000-000000000001');
select pg_temp.assert_true((select status='completed' from public.quest_sessions
    where id='30000000-0000-0000-0000-000000000009'), 'expired active session finalized');
select pg_temp.assert_true((select count(*)=1 from public.quest_sessions where status='active'), 'exactly one new active session');
reset role;
set local role service_role;
select pg_temp.assert_true((select count(*)=2 from public.wallets), 'trusted service read across users');
select pg_temp.expect_error('update public.wallets set coins=999', '42501');
select pg_temp.expect_error('insert into public.wallet_transactions default values', '42501');
reset role;
rollback;
\echo 'PASS integrity and RLS tests (all fixture data rolled back)'
