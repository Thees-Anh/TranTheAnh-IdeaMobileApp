-- PawQuest production health check. READ-ONLY: one SELECT, changes nothing.
-- Run as postgres in the SQL Editor (or psql). Returns ONE ROW PER CHECK so the SQL Editor
-- shows everything at once: `violations` must be 0 and `status` PASS for every row.
-- To inspect a failing check, run its CTE body (same name below) on its own.
-- Matches the economy up to migration 20261126003600 (quests, shop, bistro, progression,
-- Mystery Box, Lucky Wheel, shard exchange, account deletion, app_config).
with
-- ---------- Access control ----------
public_tables as (
    select c.oid, c.relname from pg_class c join pg_namespace n on n.oid=c.relnamespace
    where n.nspname='public' and c.relkind in ('r','p')
),
rls_disabled as (
    select t.relname from public_tables t join pg_class c on c.oid=t.oid where not c.relrowsecurity
),
-- Clients may only: insert/update their quests, rename their pet, change their display name.
client_writes as (
    select t.relname, r.role, w.priv from public_tables t
    cross join (values('anon'),('authenticated')) r(role)
    cross join (values('INSERT'),('UPDATE'),('DELETE'),('TRUNCATE')) w(priv)
    where (w.priv in ('UPDATE','INSERT') and has_any_column_privilege(r.role,t.oid,w.priv)
           or w.priv in ('DELETE','TRUNCATE') and has_table_privilege(r.role,t.oid,w.priv))
      and not (r.role='authenticated' and (
               (t.relname='quests' and w.priv in ('INSERT','UPDATE'))
            or (t.relname in ('pets','profiles') and w.priv='UPDATE')))
),
client_sensitive_columns as (
    select x.tbl,x.col from (values('wallets','coins'),('pets','xp'),('pets','hunger'),('pets','species_code'),
        ('profiles','deleted_at'),('quests','user_id'),('shard_balances','balance'),
        ('lucky_wheel_ticket_balances','balance')) x(tbl,col)
    where to_regclass('public.'||x.tbl) is not null
      and (has_column_privilege('authenticated','public.'||x.tbl,x.col,'UPDATE')
        or has_column_privilege('anon','public.'||x.tbl,x.col,'UPDATE'))
),
anon_functions as (
    select p.oid::regprocedure::text as fn from pg_proc p join pg_namespace n on n.oid=p.pronamespace
    where n.nspname in ('public','pawquest_private') and has_function_privilege('anon',p.oid,'EXECUTE')
      and p.oid::regprocedure::text <> 'app_status(integer)'
),
private_functions_exposed as (
    select p.oid::regprocedure::text as fn from pg_proc p join pg_namespace n on n.oid=p.pronamespace
    where n.nspname='pawquest_private' and has_function_privilege('authenticated',p.oid,'EXECUTE')
),
definer_without_search_path as (
    select p.oid::regprocedure::text as fn from pg_proc p join pg_namespace n on n.oid=p.pronamespace
    where n.nspname in ('public','pawquest_private') and p.prosecdef
      and not exists(select 1 from unnest(coalesce(p.proconfig,'{}')) c where c like 'search_path=%')
),
ledgers_without_immutability as (
    select x.tbl from (values('public.wallet_transactions'),('public.shard_ledger'),('public.lucky_wheel_ticket_ledger'),
        ('public.lucky_wheel_spins'),('public.item_case_openings'),('public.item_shard_redemptions'),
        ('public.pet_shard_redemptions'),('public.bistro_meals')) x(tbl)
    where to_regclass(x.tbl) is not null and not exists(
        select 1 from pg_trigger g join pg_proc f on f.oid=g.tgfoid
        where g.tgrelid=to_regclass(x.tbl) and not g.tgisinternal and f.proname='reject_ledger_mutation')
),
-- ---------- Economy reconciliation ----------
coins_vs_ledger as (   -- every Coins change is a signed wallet_transactions row
    select w.user_id from public.wallets w
    left join (select user_id,sum(coins)::bigint s from public.wallet_transactions group by user_id) t on t.user_id=w.user_id
    where w.coins <> coalesce(t.s,0)
),
xp_vs_sources as (     -- XP comes from quest/progression receipts and Bistro meals only
    select p.user_id from public.pets p
    left join (select user_id,sum(pet_xp)::bigint s from public.wallet_transactions group by user_id) t on t.user_id=p.user_id
    left join (select user_id,sum(xp_granted)::bigint s from public.bistro_meals group by user_id) b on b.user_id=p.user_id
    where p.xp <> coalesce(t.s,0)+coalesce(b.s,0)
),
daily_quest_cap as (   -- at most 5 rewarded sessions and 250 Coins per UTC reward day
    select user_id,reward_day from public.wallet_transactions where kind='quest_reward'
    group by user_id,reward_day having count(*)>5 or sum(coins)>250
),
claim_receipt_mismatch as (
    select s.id from public.quest_sessions s
    left join public.wallet_transactions t on t.quest_session_id=s.id and t.kind='quest_reward'
    where (s.status='claimed') is distinct from (t.id is not null)
),
multiple_active_sessions as (
    select user_id from public.quest_sessions where status='active' group by user_id having count(*)>1
),
shards_vs_ledger as (
    select coalesce(b.user_id,l.user_id) as user_id from public.shard_balances b
    full join (select user_id,currency,sum(delta)::bigint s from public.shard_ledger group by user_id,currency) l
        on l.user_id=b.user_id and l.currency=b.currency
    where coalesce(b.balance,0) <> coalesce(l.s,0)
),
tickets_vs_ledger as (
    select coalesce(b.user_id,l.user_id) as user_id from public.lucky_wheel_ticket_balances b
    full join (select user_id,sum(delta)::bigint s from public.lucky_wheel_ticket_ledger group by user_id) l on l.user_id=b.user_id
    where coalesce(b.balance,0) <> coalesce(l.s,0)
),
-- ---------- Configuration ----------
item_case_odds as (    -- published current Mystery Box tiers must total 100.00 %
    select b.code from public.item_case_banners b join public.item_case_versions v on v.banner_id=b.id and v.version=b.current_version
    where v.published_at is not null
    group by b.code,v.id having (select coalesce(sum(probability_bps),0) from public.item_case_tiers t where t.version_id=v.id)<>10000
),
wheel_odds as (
    select b.code from public.lucky_wheel_banners b join public.lucky_wheel_versions v on v.banner_id=b.id and v.version=b.current_version
    where v.published_at is not null
    group by b.code,v.id having (select coalesce(sum(weight),0) from public.lucky_wheel_sectors s where s.version_id=v.id)<>10000
),
app_config_rows as (select 1 from public.app_config having count(*)<>1),
-- ---------- Account deletion (privacy) ----------
deleted_identity_remaining as (
    select p.id from public.profiles p join auth.users u on u.id=p.id where p.deleted_at is not null
),
deleted_text_remaining as (
    select p.id from public.profiles p where p.deleted_at is not null and (
        p.display_name is distinct from 'Deleted user'
        or exists(select 1 from public.quests q where q.user_id=p.id and (q.title<>'Deleted quest' or q.description<>''))
        or exists(select 1 from public.quest_sessions s where s.user_id=p.id and s.title_snapshot<>'Deleted quest')
        or exists(select 1 from public.pets x join public.pet_species sp on sp.code=x.species_code
                  where x.user_id=p.id and x.name<>sp.name))
),
results(ord,area,check_name,violations,meaning) as (values
 (1,'access','rls_disabled',(select count(*) from rls_disabled),'Public tables without Row Level Security'),
 (2,'access','client_write_privileges',(select count(*) from client_writes),'anon/authenticated write grants beyond quests, pet name, display name'),
 (3,'access','client_sensitive_columns',(select count(*) from client_sensitive_columns),'Clients able to edit Coins, XP, stats, shards, tickets or ownership'),
 (4,'access','anon_functions',(select count(*) from anon_functions),'Functions callable before sign-in (only app_status is allowed)'),
 (5,'access','private_functions_exposed',(select count(*) from private_functions_exposed),'pawquest_private helpers callable by signed-in clients'),
 (6,'access','definer_without_search_path',(select count(*) from definer_without_search_path),'SECURITY DEFINER functions without a fixed search_path'),
 (7,'access','ledgers_without_immutability',(select count(*) from ledgers_without_immutability),'Ledger tables missing the UPDATE/DELETE-rejecting trigger'),
 (8,'economy','coins_vs_ledger',(select count(*) from coins_vs_ledger),'Wallets whose Coins differ from the sum of their receipts'),
 (10,'economy','xp_vs_sources',(select count(*) from xp_vs_sources),'Pets whose XP differs from receipts + Bistro meals'),
 (11,'economy','daily_quest_cap',(select count(*) from daily_quest_cap),'User-days above 5 rewarded sessions or 250 Coins'),
 (12,'economy','claim_receipt_mismatch',(select count(*) from claim_receipt_mismatch),'Claimed sessions without a receipt, or receipts for unclaimed sessions'),
 (13,'economy','multiple_active_sessions',(select count(*) from multiple_active_sessions),'Users with more than one active Focus session'),
 (14,'economy','shards_vs_ledger',(select count(*) from shards_vs_ledger),'Shard balances differing from the shard ledger'),
 (15,'economy','tickets_vs_ledger',(select count(*) from tickets_vs_ledger),'Wheel ticket balances differing from the ticket ledger'),
 (16,'config','item_case_odds',(select count(*) from item_case_odds),'Published Mystery Box odds not totalling 100 %'),
 (17,'config','wheel_odds',(select count(*) from wheel_odds),'Published Lucky Wheel weights not totalling 100 %'),
 (18,'config','app_config_rows',(select count(*) from app_config_rows),'app_config must have exactly one row'),
 (19,'privacy','deleted_identity_remaining',(select count(*) from deleted_identity_remaining),'Deleted profiles whose sign-in identity still exists'),
 (20,'privacy','deleted_text_remaining',(select count(*) from deleted_text_remaining),'Deleted profiles still holding user-entered text (quests, display or pet name)')
)
select area, check_name, violations, case when violations=0 then 'PASS' else 'FAIL' end as status, meaning
from results order by (violations=0), ord;
