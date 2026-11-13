-- Read-only operational checks. Run as postgres in a migrated database.
-- Every query labelled "violations" should return zero rows.

-- RLS must be true on all six rows.
select c.relname as table_name, c.relrowsecurity as rls_enabled
from pg_class c join pg_namespace n on n.oid=c.relnamespace
where n.nspname='public' and c.relname in
    ('profiles','pets','quests','quest_sessions','wallets','wallet_transactions') order by c.relname;

select tablename, policyname, roles, cmd, qual, with_check
from pg_policies where schemaname='public' and tablename in
    ('profiles','pets','quests','quest_sessions','wallets','wallet_transactions')
order by tablename, policyname;

-- Daily cap/count violations.
select user_id, reward_day, count(*) as rewarded_sessions, sum(coins) as earned_coins
from public.wallet_transactions group by user_id, reward_day
having count(*) > 5 or sum(coins) > 250;

-- Wallet/ledger reconciliation violations (no spending/grants exist in this milestone).
select w.user_id, w.coins, coalesce(sum(t.coins),0) as ledger_coins
from public.wallets w left join public.wallet_transactions t on t.user_id=w.user_id
group by w.user_id, w.coins having w.coins <> coalesce(sum(t.coins),0);

-- XP reconciliation violations (initial XP is zero; only quest reward XP is implemented).
select p.user_id, p.xp, coalesce(sum(t.pet_xp),0) as ledger_xp
from public.pets p left join public.wallet_transactions t on t.user_id=p.user_id
group by p.user_id,p.xp having p.xp <> coalesce(sum(t.pet_xp),0);

-- Receipt/session status linkage violations.
select s.id, s.status, t.id as receipt_id
from public.quest_sessions s left join public.wallet_transactions t on t.quest_session_id=s.id
where (s.status='claimed') is distinct from (t.id is not null);

-- Active-session uniqueness violations.
select user_id, count(*) from public.quest_sessions where status='active'
group by user_id having count(*) > 1;

-- Direct sensitive client privileges: every value should be false.
select
    has_column_privilege('authenticated','public.wallets','coins','UPDATE') as client_can_edit_coins,
    has_column_privilege('authenticated','public.pets','xp','UPDATE') as client_can_edit_xp,
    has_table_privilege('authenticated','public.wallet_transactions','INSERT') as client_can_insert_receipts,
    has_table_privilege('authenticated','public.quest_sessions','INSERT') as client_can_forge_sessions,
    has_function_privilege('anon','public.claim_quest_reward(uuid)','EXECUTE') as anonymous_can_claim;
