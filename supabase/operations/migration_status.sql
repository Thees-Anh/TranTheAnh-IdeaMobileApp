-- READ-ONLY. Shows which repository migrations are present in this database, by checking an
-- object or data row each migration creates. Works whether migrations were applied with the
-- Supabase CLI or pasted into the SQL Editor. Run in the SQL Editor; changes nothing
-- (the helper below is a session-only temporary function).
create or replace function pg_temp.has_rows(q text) returns boolean language plpgsql as $f$
declare r boolean; begin execute q into r; return coalesce(r,false);
exception when undefined_table then return false; end $f$;

with checks(migration, applied) as (values
 ('20261105000100_initial_schema',           to_regclass('public.profiles') is not null),
 ('20261105000200_reward_integrity',         to_regprocedure('pawquest_private.apply_reward()') is not null),
 ('20261105000300_auth_account_readiness',   exists(select 1 from pg_proc p join pg_namespace n on n.oid=p.pronamespace where n.nspname='public' and p.proname='ensure_account')),
 ('20261105000400_quest_definitions',        exists(select 1 from information_schema.columns where table_schema='public' and table_name='quests' and column_name='repeat_type')),
 ('20261105000500_quest_daily_progress',     exists(select 1 from pg_proc p join pg_namespace n on n.oid=p.pronamespace where n.nspname='public' and p.proname='quest_daily_reward_count')),
 ('20261105000600_focus_sessions',           exists(select 1 from information_schema.columns where table_schema='public' and table_name='quest_sessions' and column_name='title_snapshot')),
 ('20261105000700_reward_claims',            exists(select 1 from pg_proc p join pg_namespace n on n.oid=p.pronamespace where n.nspname='public' and p.proname='reward_claim')),
 ('20261105000800_pet_state',                to_regclass('pawquest_private.pet_care_requests') is not null),
 ('20261105000900_pet_care',                 exists(select 1 from pg_proc p join pg_namespace n on n.oid=p.pronamespace where n.nspname='pawquest_private' and p.proname='pet_care_balance')),
 ('20261105001000_shop_inventory',           to_regclass('public.item_catalog') is not null),
 ('20261105001100_shop_purchases',           exists(select 1 from information_schema.columns where table_schema='public' and table_name='wallet_transactions' and column_name='unit_price')),
 ('20261105001200_pet_equipment',            to_regclass('public.pet_equipment') is not null),
 ('20261105001300_room_furniture',           to_regclass('public.room_furniture') is not null),
 ('20261106001400_pet_hydration',            exists(select 1 from information_schema.columns where table_schema='public' and table_name='pets' and column_name='hydration')),
 ('20261106001500_quest_reminders',          exists(select 1 from pg_proc p join pg_namespace n on n.oid=p.pronamespace where n.nspname='public' and p.proname='reminder_context')),
 ('20261106001600_pet_wake',                 exists(select 1 from pg_proc p join pg_namespace n on n.oid=p.pronamespace where n.nspname='public' and p.proname='pet_state' and p.prosrc like '%woken%')),
 ('20261106001700_pet_collection',           to_regclass('public.pet_species') is not null),
 ('20261106001800_play_costs',               exists(select 1 from pg_proc p join pg_namespace n on n.oid=p.pronamespace where n.nspname='pawquest_private' and p.proname='play_balance')),
 ('20261106001900_puppy',                    exists(select 1 from pg_proc p join pg_namespace n on n.oid=p.pronamespace where n.nspname='public' and p.proname='pet_companion_context')),
 ('20261109002000_progression',              to_regclass('public.progress_events') is not null),
 ('20261109002100_widget_context',           exists(select 1 from pg_proc p join pg_namespace n on n.oid=p.pronamespace where n.nspname='public' and p.proname='widget_context')),
 ('20261109002200_capybara_rabbit',          pg_temp.has_rows($$select count(*)=2 from public.pet_species where code in ('capybara','rabbit')$$)),
 ('20261109002300_decor_catalog',            exists(select 1 from information_schema.columns where table_schema='public' and table_name='item_catalog' and column_name='description')),
 ('20261110002400_item_case_shards',         to_regclass('public.item_case_banners') is not null),
 ('20261110002500_item_case_seed',           pg_temp.has_rows($$select exists(select 1 from public.item_case_banners where code='item_case')$$)),
 ('20261110002600_secure_item_case_open',    exists(select 1 from pg_proc p join pg_namespace n on n.oid=p.pronamespace where n.nspname='pawquest_private' and p.proname='item_case_random_bytes')),
 ('20261110002700_item_shard_exchange',      exists(select 1 from pg_proc p join pg_namespace n on n.oid=p.pronamespace where n.nspname='public' and p.proname='item_shard_redeem')),
 ('20261110002800_lucky_wheel',              to_regclass('public.user_pet_unlocks') is not null),
 ('20261111002900_pawquest_bistro',          to_regclass('public.bistro_meals') is not null),
 ('20261111003000_bistro_menu',              pg_temp.has_rows($$select count(*)>=12 from public.bistro_foods$$)),
 ('20261112003100_account_deletion',         exists(select 1 from pg_proc p join pg_namespace n on n.oid=p.pronamespace where n.nspname='public' and p.proname='delete_my_account')),
 ('20261112003200_reminder_schedule',        exists(select 1 from pg_proc p join pg_namespace n on n.oid=p.pronamespace where n.nspname='public' and p.proname='reminder_schedule')),
 ('20261112003300_gacha_pet_care_gate',      exists(select 1 from pg_proc p join pg_namespace n on n.oid=p.pronamespace where n.nspname='pawquest_private' and p.proname='gacha_care_block')),
 ('20261112003400_widget_streak',            exists(select 1 from pg_proc p join pg_namespace n on n.oid=p.pronamespace where n.nspname='pawquest_private' and p.proname='widget_context' and p.prosrc like '%current_streak%')),
 ('20261120003500_account_deletion_pet_names', exists(select 1 from pg_proc p join pg_namespace n on n.oid=p.pronamespace where n.nspname='public' and p.proname='delete_my_account' and p.prosrc like '%pet_species%')),
 ('20261126003600_app_config',               to_regclass('public.app_config') is not null)
)
select migration, case when applied then 'applied' else 'MISSING' end as status
from checks order by applied, migration;
