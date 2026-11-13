"""Validate migrations on fresh disposable local PostgreSQL databases (stdlib only).

The server must already be running. No deployed Supabase project is contacted.
Creates two uniquely named databases, replays migrations/tests, runs concurrent
RPC tests, and drops only those databases on exit. Use a disposable local cluster.
"""
import argparse
from concurrent.futures import ThreadPoolExecutor
import os
from pathlib import Path
import subprocess
import threading
import time
import uuid

ROOT = next(parent for parent in Path(__file__).resolve().parents if (parent / 'settings.gradle.kts').is_file())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--pg-bin', default='C:/Program Files/PostgreSQL/18/bin')
    parser.add_argument('--port', default='55439')
    args = parser.parse_args()
    psql = str(Path(args.pg_bin) / ('psql.exe' if os.name == 'nt' else 'psql'))
    common = [psql, '-X', '-h', '127.0.0.1', '-p', args.port, '-U', 'postgres',
              '-v', 'ON_ERROR_STOP=1', '-v', 'VERBOSITY=verbose', '-Atq']
    env = dict(os.environ, PGCONNECT_TIMEOUT='10', PGAPPNAME='pawquest-migration-tests',PGCLIENTENCODING='UTF8')
    logs = []

    def sql(db, text=None, file=None, check=True):
        command = common + ['-d', db] + (['-f', str(file)] if file else ['-c', text])
        result = subprocess.run(command, capture_output=True, env=env)
        logs.append(result.stdout.decode('utf-8', 'replace') + result.stderr.decode('utf-8', 'replace'))
        if check and result.returncode:
            raise AssertionError(logs[-1])
        return result

    def authenticate(actor, query):
        return f"set role authenticated; select set_config('request.jwt.claim.sub','{actor}',false); {query}"

    def race(db, statements):
        barrier = threading.Barrier(len(statements))
        def run(statement):
            barrier.wait(timeout=20)
            return sql(db, statement, check=False)
        with ThreadPoolExecutor(max_workers=len(statements)) as pool:
            return list(pool.map(run, statements))

    databases = []
    try:
        for replay in range(2):
            db = 'pawquest_test_' + uuid.uuid4().hex[:16]
            sql('postgres', f'create database {db};')
            databases.append(db)
            sql(db, file=ROOT / 'supabase/tests/local_auth_shim.sql')
            existing_user = '90000000-0000-0000-0000-000000000001'
            sql(db, f"insert into auth.users(id,email) values('{existing_user}','existing@example.invalid');")
            for migration in sorted((ROOT / 'supabase/migrations').glob('*.sql')):
                if migration.name == '20261105001000_shop_inventory.sql':
                    sql(db, f"update public.pet_food_inventory set quantity=0 where user_id='{existing_user}';")
                sql(db, file=migration)
            assert sql(db, f"select quantity=0 from public.user_inventory where user_id='{existing_user}';").stdout.strip() == b't', 'inventory migration refilled spent food'
            assert sql(db, f"select exists(select 1 from public.wallets w join public.pets p using(user_id) where user_id='{existing_user}');").stdout.strip() == b't'
            sql(db, f"alter table public.user_pet_unlocks disable trigger receipt_immutable; delete from public.user_pet_unlocks where user_id='{existing_user}'; alter table public.user_pet_unlocks enable trigger receipt_immutable; delete from public.pet_food_inventory where user_id='{existing_user}'; delete from public.pets where user_id='{existing_user}'; delete from public.wallets where user_id='{existing_user}'; delete from public.profiles where id='{existing_user}'; delete from auth.users where id='{existing_user}';")
            sql(db, file=ROOT / 'supabase/tests/integrity.sql')
            sql(db, file=ROOT / 'supabase/tests/auth_initialization.sql')
            sql(db, file=ROOT / 'supabase/tests/quest_definitions.sql')
            sql(db, file=ROOT / 'supabase/tests/quest_daily_progress.sql')
            sql(db, file=ROOT / 'supabase/tests/focus_sessions.sql')
            sql(db, file=ROOT / 'supabase/tests/reward_engine.sql')
            sql(db, file=ROOT / 'supabase/tests/pet_state.sql')
            sql(db, file=ROOT / 'supabase/tests/pet_care.sql')
            sql(db, file=ROOT / 'supabase/tests/shop_inventory.sql')
            sql(db, file=ROOT / 'supabase/tests/shop_purchases.sql')
            sql(db, file=ROOT / 'supabase/tests/pet_equipment.sql')
            sql(db, file=ROOT / 'supabase/tests/room_furniture.sql')
            sql(db, file=ROOT / 'supabase/tests/mvp_regressions.sql')
            sql(db, file=ROOT / 'supabase/tests/pet_hydration.sql')
            sql(db, file=ROOT / 'supabase/tests/quest_reminders.sql')
            sql(db, file=ROOT / 'supabase/tests/pet_collection_wake.sql')
            sql(db, file=ROOT / 'supabase/tests/play_costs.sql')
            sql(db, file=ROOT / 'supabase/tests/puppy.sql')
            sql(db, file=ROOT / 'supabase/tests/progression.sql')
            sql(db, file=ROOT / 'supabase/tests/widget_context.sql')
            sql(db, file=ROOT / 'supabase/tests/new_companions.sql')
            sql(db, file=ROOT / 'supabase/tests/decor_catalog.sql')
            sql(db, file=ROOT / 'supabase/tests/item_case_shards.sql')
            sql(db, file=ROOT / 'supabase/tests/item_case_open.sql')
            sql(db, file=ROOT / 'supabase/tests/item_shard_exchange.sql')
            sql(db, file=ROOT / 'supabase/tests/lucky_wheel.sql')
            sql(db, file=ROOT / 'supabase/tests/account_deletion.sql')
            sql(db, file=ROOT / 'supabase/tests/reminder_schedule.sql')
            sql(db, file=ROOT / 'supabase/tests/gacha_pet_care_gate.sql')
            sql(db, file=ROOT / 'supabase/tests/widget_streak.sql')
            print(f'PASS fresh migration replay and SQL integrity/RLS suite #{replay+1}', flush=True)

        exchange_user='fc400000-0000-0000-0000-000000000001'
        exchange_item='e2300000-0000-0000-0000-000000000003'
        exchange_key='fc500000-0000-0000-0000-000000000001'
        sql(db,f"insert into auth.users(id,email) values('{exchange_user}','exchange-race@example.invalid'); insert into public.shard_ledger(user_id,currency,request_id,reason,delta,balance_after,note) values('{exchange_user}','ITEM_SHARD',gen_random_uuid(),'ADMIN_ADJUSTMENT',60,0,'Test funding');")
        sql(db,authenticate(exchange_user,f"select public.item_shard_quote('{exchange_item}','{exchange_key}');"))
        results=race(db,[authenticate(exchange_user,f"select public.item_shard_redeem('{exchange_item}','{exchange_key}');")]*6)
        assert all(r.returncode==0 for r in results)
        assert len({r.stdout.decode().splitlines()[-1] for r in results})==1
        assert sql(db,f"select balance=30 from public.shard_balances where user_id='{exchange_user}' and currency='ITEM_SHARD';").stdout.strip()==b't'
        assert sql(db,f"select count(*)=1 from public.item_shard_redemptions where user_id='{exchange_user}';").stdout.strip()==b't'
        items=['e2300000-0000-0000-0000-000000000007','e2300000-0000-0000-0000-000000000008']
        keys=['fc500000-0000-0000-0000-000000000002','fc500000-0000-0000-0000-000000000003']
        for item,key in zip(items,keys): sql(db,authenticate(exchange_user,f"select public.item_shard_quote('{item}','{key}');"))
        results=race(db,[authenticate(exchange_user,f"select public.item_shard_redeem('{item}','{key}');") for item,key in zip(items,keys)])
        assert all(r.returncode==0 for r in results)
        assert sum('"redeemed"' in r.stdout.decode() for r in results)==1
        assert sql(db,f"select balance=0 from public.shard_balances where user_id='{exchange_user}' and currency='ITEM_SHARD';").stdout.strip()==b't'
        print('PASS exchange concurrent replay and competing redemptions: no double grant/overspend',flush=True)

        case_user='fa270000-0000-0000-0000-000000000001'
        case_version='e2400000-0000-0000-0000-000000000002'
        case_item='e2300000-0000-0000-0000-000000000003'
        case_request='fa280000-0000-0000-0000-000000000001'
        sql(db,f"insert into auth.users(id,email) values('{case_user}','case-race@example.invalid'); update public.wallets set coins=1000 where user_id='{case_user}'; update public.item_case_banners set enabled=true;")
        results=race(db,[f"select (pawquest_private.settle_item_case('{case_user}','{case_version}','{case_item}','{case_request}')).id;"]*6)
        assert all(r.returncode==0 for r in results), [r.stderr.decode() for r in results if r.returncode]
        assert len({r.stdout.decode().strip() for r in results})==1
        assert sql(db,f"select coins=900 from public.wallets where user_id='{case_user}';").stdout.strip()==b't'
        assert sql(db,f"select quantity=1 from public.user_inventory where user_id='{case_user}' and item_id='{case_item}';").stdout.strip()==b't'
        assert sql(db,f"select count(*)=1 from public.item_case_openings where user_id='{case_user}';").stdout.strip()==b't'
        results=race(db,[f"select pawquest_private.settle_item_case('{case_user}','{case_version}','{case_item}','fa290000-0000-0000-0000-{i:012d}');" for i in range(6)])
        assert all(r.returncode==0 for r in results)
        assert sql(db,f"select balance=30 from public.shard_balances where user_id='{case_user}' and currency='ITEM_SHARD';").stdout.strip()==b't'
        assert sql(db,f"select coins=300 from public.wallets where user_id='{case_user}';").stdout.strip()==b't'
        assert sql(db,f"select count(*)=6 from public.shard_ledger where user_id='{case_user}';").stdout.strip()==b't'
        # Case, direct Shop and quest rewards serialize on the same wallet row.
        case_quest='fa330000-0000-0000-0000-000000000001'
        case_session='fa340000-0000-0000-0000-000000000001'
        sql(db,f"insert into public.quests(id,user_id,title,duration_minutes) values('{case_quest}','{case_user}','Case concurrency',75); insert into public.quest_sessions(id,user_id,quest_id,duration_minutes,started_at,ends_at,status) values('{case_session}','{case_user}','{case_quest}',75,'2026-01-01 10:00Z','2026-01-01 11:15Z','completed');")
        results=race(db,[
            f"select pawquest_private.settle_item_case('{case_user}','{case_version}','{case_item}',gen_random_uuid());",
            authenticate(case_user,"select public.shop_purchase('e1000000-0000-0000-0000-000000000001',1,gen_random_uuid());"),
            authenticate(case_user,f"select public.claim_quest_reward('{case_session}');"),
        ])
        assert all(r.returncode==0 for r in results)
        assert sql(db,f"select coins=270 from public.wallets where user_id='{case_user}';").stdout.strip()==b't'
        assert sql(db,f"select balance=35 from public.shard_balances where user_id='{case_user}' and currency='ITEM_SHARD';").stdout.strip()==b't'
        sql(db,"update public.item_case_banners set enabled=false;")
        print('PASS concurrent case settlements and mixed Shop/reward operations: one first grant/debit, exact shard credits, no lost updates',flush=True)

        # Exercise the real two-argument authenticated API with pgcrypto entropy.
        api_banner='e2400000-0000-0000-0000-000000000001'
        api_user='fb350000-0000-0000-0000-000000000001'
        api_duplicate_user='fb350000-0000-0000-0000-000000000002'
        api_poor_user='fb350000-0000-0000-0000-000000000003'
        api_limited_user='fb350000-0000-0000-0000-000000000004'
        api_request='fb360000-0000-0000-0000-000000000001'
        sql(db,f"insert into auth.users(id,email) values('{api_user}','public-case-race@example.invalid'),('{api_duplicate_user}','duplicate-case-race@example.invalid'),('{api_poor_user}','poor-case-race@example.invalid'),('{api_limited_user}','limited-case-race@example.invalid'); update public.wallets set coins=1000 where user_id in ('{api_user}','{api_duplicate_user}','{api_limited_user}'); update public.wallets set coins=100 where user_id='{api_poor_user}'; update public.item_case_banners set enabled=true;")
        results=race(db,[authenticate(api_user,f"select public.item_case_open('{api_banner}','{api_request}');")]*6)
        assert all(r.returncode==0 for r in results), [r.stderr.decode() for r in results if r.returncode]
        assert len({r.stdout.decode().strip().splitlines()[-1] for r in results})==1
        first_response=results[0].stdout.decode().strip().splitlines()[-1]
        assert sql(db,f"select coins=900 from public.wallets where user_id='{api_user}';").stdout.strip()==b't'
        results=race(db,[authenticate(api_user,f"select public.item_case_open('{api_banner}','fb370000-0000-0000-0000-{i:012d}')->>'status';") for i in range(6)])
        assert all(r.returncode==0 and r.stdout.strip().splitlines()[-1]==b'opened' for r in results)
        assert sql(db,f"select coins=300 from public.wallets where user_id='{api_user}';").stdout.strip()==b't'
        assert sql(db,f"select count(*)=7 from public.item_case_openings where user_id='{api_user}';").stdout.strip()==b't'
        assert sql(db,f"select coalesce(sum(quantity),0)=(select sum(quantity_granted) from public.item_case_openings where user_id='{api_user}') from public.user_inventory where user_id='{api_user}' and max_quantity=1;").stdout.strip()==b't'
        assert sql(db,f"select coalesce((select balance from public.shard_balances where user_id='{api_user}' and currency='ITEM_SHARD'),0)=(select sum(item_shards_granted) from public.item_case_openings where user_id='{api_user}');").stdout.strip()==b't'
        api_quest='fb410000-0000-0000-0000-000000000001'
        api_session='fb420000-0000-0000-0000-000000000001'
        sql(db,f"insert into public.quests(id,user_id,title,duration_minutes) values('{api_quest}','{api_user}','Public case concurrency',75); insert into public.quest_sessions(id,user_id,quest_id,duration_minutes,started_at,ends_at,status) values('{api_session}','{api_user}','{api_quest}',75,'2026-01-01 10:00Z','2026-01-01 11:15Z','completed');")
        results=race(db,[
            authenticate(api_user,f"select public.item_case_open('{api_banner}',gen_random_uuid());"),
            authenticate(api_user,"select public.shop_purchase('e1000000-0000-0000-0000-000000000001',1,gen_random_uuid());"),
            authenticate(api_user,f"select public.claim_quest_reward('{api_session}');"),
        ])
        assert all(r.returncode==0 for r in results)
        assert sql(db,f"select coins=270 from public.wallets where user_id='{api_user}';").stdout.strip()==b't'
        sql(db,f"select pawquest_private.adjust_inventory('{api_duplicate_user}',item_id,1) from public.item_case_pool where version_id='{case_version}';")
        results=race(db,[authenticate(api_duplicate_user,f"select public.item_case_open('{api_banner}','fb380000-0000-0000-0000-{i:012d}')->'receipt'->>'reward_outcome';") for i in range(6)])
        assert all(r.returncode==0 and r.stdout.strip().splitlines()[-1]==b'DUPLICATE_CONVERTED' for r in results)
        assert sql(db,f"select count(*)=6 from public.shard_ledger where user_id='{api_duplicate_user}';").stdout.strip()==b't'
        assert sql(db,f"select coins=400 from public.wallets where user_id='{api_duplicate_user}';").stdout.strip()==b't'
        assert sql(db,f"select balance=(select sum(item_shards_granted) from public.item_case_openings where user_id='{api_duplicate_user}') from public.shard_balances where user_id='{api_duplicate_user}' and currency='ITEM_SHARD';").stdout.strip()==b't'
        results=race(db,[authenticate(api_poor_user,f"select public.item_case_open('{api_banner}','fb390000-0000-0000-0000-{i:012d}')->>'status';") for i in range(6)])
        assert all(r.returncode==0 for r in results)
        statuses=[r.stdout.strip().splitlines()[-1] for r in results]
        assert statuses.count(b'opened')==1 and statuses.count(b'insufficient_coins')==5
        assert sql(db,f"select coins=0 from public.wallets where user_id='{api_poor_user}';").stdout.strip()==b't'
        assert sql(db,f"select count(*)=1 from public.item_case_openings where user_id='{api_poor_user}';").stdout.strip()==b't'
        sql(db,"update pawquest_private.item_case_api_settings set max_attempts=2;")
        results=race(db,[authenticate(api_limited_user,f"select public.item_case_open('{api_banner}','fb400000-0000-0000-0000-{i:012d}')->>'status';") for i in range(6)])
        assert all(r.returncode==0 for r in results)
        statuses=[r.stdout.strip().splitlines()[-1] for r in results]
        assert statuses.count(b'opened')==2 and statuses.count(b'rate_limited')==4
        assert sql(db,f"select coins=800 from public.wallets where user_id='{api_limited_user}';").stdout.strip()==b't'
        # Keep one wallet locked long enough to exercise the RPC's bounded wait.
        with ThreadPoolExecutor(max_workers=1) as pool:
            blocker=pool.submit(sql,db,f"begin; select coins from public.wallets where user_id='{api_poor_user}' for update; select pg_sleep(10); commit; /* pawquest-case-wallet-blocker */")
            for _ in range(30):
                sleeping=sql(db,"select exists(select 1 from pg_stat_activity where pid<>pg_backend_pid() and query like '%pawquest-case-wallet-blocker%' and wait_event='PgSleep');").stdout.strip()
                if sleeping==b't':
                    break
                time.sleep(0.05)
            else:
                raise AssertionError('wallet blocker did not start')
            busy=sql(db,authenticate(api_poor_user,f"select public.item_case_open('{api_banner}',gen_random_uuid())->>'status';"))
            assert busy.stdout.strip().splitlines()[-1]==b'busy'
            assert blocker.result().returncode==0
        assert sql(db,f"select coins=0 from public.wallets where user_id='{api_poor_user}';").stdout.strip()==b't'
        sql(db,"update public.item_case_banners set enabled=false;")
        recovered=sql(db,authenticate(api_user,f"select public.item_case_open('{api_banner}','{api_request}');")).stdout.decode().strip().splitlines()[-1]
        assert recovered==first_response, 'recovery changed original response or redrew after disable/throttle'
        sql(db,"update pawquest_private.item_case_api_settings set max_attempts=30;")
        print('PASS public cryptographic API concurrency: identical retries, distinct draws, duplicate shards, overspending, rate limits and stable recovery',flush=True)

        progress_user='fd900000-0000-0000-0000-000000000001'
        sql(db,f"insert into auth.users(id,email) values('{progress_user}','progress-race@example.invalid'); update public.pets set xp=300 where user_id='{progress_user}';")
        results=race(db,[authenticate(progress_user,"select public.progress_snapshot();")]*6)
        assert all(r.returncode==0 for r in results)
        assert sql(db,f"select quantity=6 from public.user_inventory where user_id='{progress_user}' and item_id='e1000000-0000-0000-0000-000000000001';").stdout.strip()==b't'
        assert sql(db,f"select count(*)=1 from public.progress_events where user_id='{progress_user}' and kind='level_up';").stdout.strip()==b't'
        print('PASS 6 concurrent progression syncs: one level gift, no duplicate stock or event',flush=True)

        decor_user='ed240000-0000-0000-0000-000000000001'
        wings='e2300000-0000-0000-0000-000000000009'
        window='e2300000-0000-0000-0000-000000000011'
        sql(db,f"insert into auth.users(id,email) values('{decor_user}','decor-race@example.invalid');update public.wallets set coins=300 where user_id='{decor_user}';")
        results=race(db,[authenticate(decor_user,f"select public.shop_purchase('{wings}',1,'ed250000-0000-0000-0000-000000000001')->'receipt'->>'id';")]*6)
        assert all(r.returncode==0 for r in results)
        assert len({r.stdout.decode().strip().splitlines()[-1] for r in results})==1
        assert sql(db,f"select coins=100 from public.wallets where user_id='{decor_user}';").stdout.strip()==b't'
        results=race(db,[authenticate(decor_user,f"select public.equipment_set('BACK','{wings}');")]*6)
        assert all(r.returncode==0 for r in results)
        assert sql(db,f"select count(*)=1 from public.pet_equipment where user_id='{decor_user}';").stdout.strip()==b't'
        sql(db,f"select pawquest_private.adjust_inventory('{decor_user}','{window}',1);")
        results=race(db,[authenticate(decor_user,f"select public.room_set('WINDOW','{window}');")]*6)
        assert all(r.returncode==0 for r in results)
        assert sql(db,f"select count(*)=1 from public.room_furniture where user_id='{decor_user}';").stdout.strip()==b't'
        print('PASS concurrent new-item purchases, BACK equip and WINDOW placement: one debit, one row per slot',flush=True)

        a, b, c = ['40000000-0000-0000-0000-' + f'{i:012d}' for i in (1, 2, 3)]
        qa, qb, qc = ['50000000-0000-0000-0000-' + f'{i:012d}' for i in (1, 2, 3)]
        sa = '60000000-0000-0000-0000-000000000001'
        sql(db, f"""
            insert into auth.users(id,email) values ('{a}','race-a@example.invalid'),
                ('{b}','race-b@example.invalid'), ('{c}','race-c@example.invalid');
            insert into public.quests(id,user_id,title,duration_minutes) values
                ('{qa}','{a}','Replay race',75), ('{qb}','{b}','Daily race',75), ('{qc}','{c}','Start race',5);
            insert into public.quest_sessions(id,user_id,quest_id,duration_minutes,started_at,ends_at,status)
                values('{sa}','{a}','{qa}',75,'2026-01-01 10:00+00','2026-01-01 11:15+00','completed');
            insert into public.quest_sessions(id,user_id,quest_id,duration_minutes,started_at,ends_at,status)
                select ('70000000-0000-0000-0000-'||lpad(i::text,12,'0'))::uuid,
                    '{b}','{qb}',75,'2026-01-01 10:00+00','2026-01-01 11:15+00','completed'
                from generate_series(1,6) i;
        """)
        results = race(db, [authenticate(a, f"select (public.claim_quest_reward('{sa}')).id;")] * 6)
        assert all(r.returncode == 0 for r in results), 'duplicate claim RPC failed'
        receipts = {r.stdout.decode().strip().splitlines()[-1] for r in results}
        assert len(receipts) == 1, 'duplicate claims returned different receipts'
        assert sql(db, f"select coins=80 from public.wallets where user_id='{a}';").stdout.strip() == b't'
        assert sql(db, f"select xp=50 from public.pets where user_id='{a}';").stdout.strip() == b't'
        assert sql(db, f"select count(*)=1 from public.wallet_transactions where user_id='{a}';").stdout.strip() == b't'
        print('PASS 6 simultaneous identical claims: one receipt, one Coins/XP credit', flush=True)

        results = race(db, [authenticate(b, f"select (public.claim_quest_reward('70000000-0000-0000-0000-{i:012d}')).id;") for i in range(1,7)])
        assert sum(r.returncode == 0 for r in results) == 5, 'daily claim limit failed'
        assert all(r.returncode == 0 or b'23514' in r.stderr for r in results), 'unexpected concurrent error'
        assert sql(db, f"select count(*)=5 and sum(coins)=250 from public.wallet_transactions where user_id='{b}';").stdout.strip() == b't'
        assert sql(db, f"select coins=250 from public.wallets where user_id='{b}';").stdout.strip() == b't'
        assert sql(db, f"select xp=250 from public.pets where user_id='{b}';").stdout.strip() == b't'
        print('PASS 6 simultaneous distinct claims: exactly 5 rewards, 250 Coins total', flush=True)

        sql(db, f"delete from public.pets where user_id='{c}'; delete from public.wallets where user_id='{c}';")
        results = race(db, [authenticate(c, "select public.ensure_account()->'pet'->>'id';")] * 6)
        assert all(r.returncode == 0 for r in results)
        assert len({r.stdout.decode().strip().splitlines()[-1] for r in results}) == 1
        assert sql(db, f"select count(*)=1 from public.pets where user_id='{c}';").stdout.strip() == b't'
        assert sql(db, f"select count(*)=1 from public.wallets where user_id='{c}';").stdout.strip() == b't'
        print('PASS 6 simultaneous account initialization retries: one wallet and one cat', flush=True)

        results = race(db, [authenticate(c, f"select (public.start_quest_session('{qc}')).id;")] * 2)
        assert sum(r.returncode == 0 for r in results) == 1
        assert any(b'23505' in r.stderr for r in results)
        assert sql(db, f"select count(*)=1 from public.quest_sessions where user_id='{c}' and status='active';").stdout.strip() == b't'
        print('PASS simultaneous session starts: exactly one active session', flush=True)

        d='40000000-0000-0000-0000-000000000004'
        qd='50000000-0000-0000-0000-000000000004'
        request='80000000-0000-0000-0000-000000000004'
        sql(db,f"insert into auth.users(id,email) values('{d}','focus-race@example.invalid'); insert into public.quests(id,user_id,title,duration_minutes) values('{qd}','{d}','Focus race',5);")
        results=race(db,[authenticate(d,f"select public.focus_start('{qd}','{request}')->'session'->>'id';")]*6)
        assert all(r.returncode==0 for r in results)
        ids={r.stdout.decode().strip().splitlines()[-1] for r in results}
        assert len(ids)==1
        assert sql(db,f"select count(*)=1 from public.quest_sessions where user_id='{d}';").stdout.strip()==b't'
        print('PASS 6 repeated Focus starts: one durable request, one session',flush=True)

        e='40000000-0000-0000-0000-000000000005'
        qe='50000000-0000-0000-0000-000000000005'
        se='60000000-0000-0000-0000-000000000005'
        sql(db,f"""insert into auth.users(id,email) values('{e}','strict-reward-race@example.invalid');
            insert into public.quests(id,user_id,title,duration_minutes) values('{qe}','{e}','Strict race',5);
            insert into public.quest_sessions(id,user_id,quest_id,duration_minutes,started_at,ends_at,status)
            values('{se}','{e}','{qe}',5,'2026-01-01 10:00Z','2026-01-01 10:05Z','completed');""")
        results=race(db,[authenticate(e,f"select public.reward_claim('{se}');")]*6)
        assert sum(r.returncode==0 for r in results)==1
        assert all(r.returncode==0 or b'23505' in r.stderr for r in results)
        assert sql(db,f"select coins=10 from public.wallets where user_id='{e}';").stdout.strip()==b't'
        assert sql(db,f"select xp=5 from public.pets where user_id='{e}';").stdout.strip()==b't'
        assert sql(db,f"select count(*)=1 from public.wallet_transactions where user_id='{e}';").stdout.strip()==b't'
        print('PASS 6 concurrent strict claims: 1 success, 5 duplicate rejections, 1 Coins/XP credit',flush=True)

        pet_user='40000000-0000-0000-0000-000000000006'
        care_request='80000000-0000-0000-0000-000000000006'
        sql(db,f"insert into auth.users(id,email) values('{pet_user}','pet-race@example.invalid'); update public.pets set hunger=0 where user_id='{pet_user}';")
        results=race(db,[authenticate(pet_user,f"select public.pet_state('feed','{care_request}')->'pet'->>'hunger';")]*6)
        assert all(r.returncode==0 for r in results)
        assert sql(db,f"select hunger=15 and xp=0 from public.pets where user_id='{pet_user}';").stdout.strip()==b't'
        assert sql(db,f"select count(*)=1 from pawquest_private.pet_care_requests where user_id='{pet_user}';").stdout.strip()==b't'
        print('PASS 6 concurrent Pet care retries: one stat effect, one request, no XP grant',flush=True)

        sql(db,f"update public.pets set hunger=0 where user_id='{pet_user}'; update public.pet_food_inventory set quantity=1 where user_id='{pet_user}';")
        requests=['81000000-0000-0000-0000-'+f'{i:012d}' for i in range(6)]
        results=race(db,[authenticate(pet_user,f"select public.pet_state('feed','{r}')->'care_result'->>'code';") for r in requests])
        assert all(r.returncode==0 for r in results)
        codes=[r.stdout.decode().strip().splitlines()[-1] for r in results]
        assert codes.count('fed')==1 and codes.count('no_food')==5
        assert sql(db,f"select quantity=0 from public.pet_food_inventory where user_id='{pet_user}';").stdout.strip()==b't'
        assert sql(db,f"select hunger=15 from public.pets where user_id='{pet_user}';").stdout.strip()==b't'
        print('PASS 6 concurrent Feed attempts for one food: 1 applied, 5 rejected, no double consumption',flush=True)

        hat='e1000000-0000-0000-0000-000000000002'
        results=race(db,[f"select (pawquest_private.adjust_inventory('{pet_user}','{hat}',1)).id;"]*6)
        assert all(r.returncode==0 for r in results)
        assert len({r.stdout.decode().strip() for r in results})==1
        assert sql(db,f"select count(*)=1 and sum(quantity)=1 from public.user_inventory where user_id='{pet_user}' and item_id='{hat}';").stdout.strip()==b't'
        print('PASS 6 concurrent unique grants: one inventory row with quantity 1',flush=True)

        food='e1000000-0000-0000-0000-000000000001'
        results=race(db,[f"select pawquest_private.adjust_inventory('{pet_user}','{food}',1);"]*6)
        assert all(r.returncode==0 for r in results)
        assert sql(db,f"select quantity=6 from public.user_inventory where user_id='{pet_user}' and item_id='{food}';").stdout.strip()==b't'
        results=race(db,[f"select pawquest_private.adjust_inventory('{pet_user}','{food}',-1);"]*8)
        assert sum(r.returncode==0 for r in results)==6
        assert all(r.returncode==0 or b'23514' in r.stderr for r in results)
        assert sql(db,f"select quantity=0 from public.user_inventory where user_id='{pet_user}' and item_id='{food}';").stdout.strip()==b't'
        print('PASS concurrent stock grants and consumption: no lost increments or negative quantity',flush=True)

        buyer='f3000000-0000-0000-0000-000000000001'
        sql(db,f"insert into auth.users(id,email) values('{buyer}','purchase-race@example.invalid'); update public.wallets set coins=200 where user_id='{buyer}';")
        key='f4000000-0000-0000-0000-000000000001'
        results=race(db,[authenticate(buyer,f"select public.shop_purchase('{food}',2,'{key}')->'receipt'->>'id';")]*6)
        assert all(r.returncode==0 for r in results)
        assert len({r.stdout.decode().strip().splitlines()[-1] for r in results})==1
        assert sql(db,f"select coins=180 from public.wallets where user_id='{buyer}';").stdout.strip()==b't'
        assert sql(db,f"select quantity=5 from public.pet_food_inventory where user_id='{buyer}';").stdout.strip()==b't'
        assert sql(db,f"select count(*)=1 from public.wallet_transactions where user_id='{buyer}';").stdout.strip()==b't'
        print('PASS 6 concurrent repeated purchases: one debit, one grant, same receipt',flush=True)
        results=race(db,[authenticate(buyer,f"select public.shop_purchase('{hat}',1,'f5000000-0000-0000-0000-{i:012d}')->>'status';") for i in range(6)])
        assert all(r.returncode==0 for r in results)
        codes=[r.stdout.decode().strip().splitlines()[-1] for r in results]
        assert codes.count('purchased')==1 and codes.count('already_owned')==5
        assert sql(db,f"select coins=120 from public.wallets where user_id='{buyer}';").stdout.strip()==b't'
        sql(db,f"update public.wallets set coins=30 where user_id='{buyer}';")
        results=race(db,[authenticate(buyer,f"select public.shop_purchase('{food}',1,'f6000000-0000-0000-0000-{i:012d}')->>'status';") for i in range(8)])
        assert all(r.returncode==0 for r in results)
        codes=[r.stdout.decode().strip().splitlines()[-1] for r in results]
        assert codes.count('purchased')==3 and codes.count('insufficient_balance')==5
        assert sql(db,f"select coins=0 from public.wallets where user_id='{buyer}';").stdout.strip()==b't'
        assert sql(db,f"select quantity=8 from public.pet_food_inventory where user_id='{buyer}';").stdout.strip()==b't'
        print('PASS concurrent unique purchases and overspending: one unique debit; no negative wallet',flush=True)

        purchase_quest='f7000000-0000-0000-0000-000000000001'
        purchase_session='f8000000-0000-0000-0000-000000000001'
        sql(db,f"""insert into public.quests(id,user_id,title,duration_minutes) values('{purchase_quest}','{buyer}','Concurrent earning',75);
            insert into public.quest_sessions(id,user_id,quest_id,duration_minutes,started_at,ends_at,status)
            values('{purchase_session}','{buyer}','{purchase_quest}',75,'2026-01-01 10:00Z','2026-01-01 11:15Z','completed');""")
        results=race(db,[authenticate(buyer,f"select public.reward_claim('{purchase_session}');"),
            authenticate(buyer,f"select public.shop_purchase('{food}',1,'f9000000-0000-0000-0000-000000000001')->>'status';")])
        assert all(r.returncode==0 for r in results)
        code=results[1].stdout.decode().strip().splitlines()[-1]
        assert code in ('purchased','insufficient_balance')
        expected=70 if code=='purchased' else 80
        assert sql(db,f"select coins={expected} from public.wallets where user_id='{buyer}';").stdout.strip()==b't'
        assert sql(db,f"select coins=80 and reward_slot=1 from public.wallet_transactions where quest_session_id='{purchase_session}';").stdout.strip()==b't'
        print('PASS concurrent reward credit and purchase: serialized wallet, reward-day totals unchanged',flush=True)

        results=race(db,[authenticate(buyer,f"select public.equipment_set('HEAD','{hat}');")]*6)
        assert all(r.returncode==0 for r in results)
        assert sql(db,f"select count(*)=1 from public.pet_equipment where user_id='{buyer}' and slot='HEAD';").stdout.strip()==b't'
        results=race(db,[authenticate(buyer,"select public.equipment_set('HEAD',null);")]*6)
        assert all(r.returncode==0 for r in results)
        assert sql(db,f"select count(*)=0 from public.pet_equipment where user_id='{buyer}';").stdout.strip()==b't'
        assert sql(db,f"select coins={expected} from public.wallets where user_id='{buyer}';").stdout.strip()==b't'
        print('PASS concurrent equip/unequip retries: one slot, no duplicate stock or wallet changes',flush=True)

        bed='e1000000-0000-0000-0000-000000000005'
        sql(db,f"select pawquest_private.adjust_inventory('{buyer}','{bed}',1);")
        results=race(db,[authenticate(buyer,f"select public.room_set('BED','{bed}');")]*6)
        assert all(r.returncode==0 for r in results)
        assert sql(db,f"select count(*)=1 from public.room_furniture where user_id='{buyer}' and slot='BED';").stdout.strip()==b't'
        results=race(db,[authenticate(buyer,"select public.room_set('BED',null);")]*6)
        assert all(r.returncode==0 for r in results)
        assert sql(db,f"select count(*)=0 from public.room_furniture where user_id='{buyer}';").stdout.strip()==b't'
        assert sql(db,f"select coins={expected} from public.wallets where user_id='{buyer}';").stdout.strip()==b't'
        print('PASS concurrent place/store retries: one furniture slot, no wallet changes',flush=True)

        hydration_user='d9100000-0000-0000-0000-000000000001'
        hydration_request='d9200000-0000-0000-0000-000000000001'
        sql(db,f"insert into auth.users(id,email) values('{hydration_user}','concurrent-drink@example.invalid'); update public.pets set hydration=20 where user_id='{hydration_user}';")
        results=race(db,[authenticate(hydration_user,f"select public.pet_state('drink','{hydration_request}');")]*6)
        assert all(r.returncode==0 for r in results),'concurrent drink failed'
        assert sql(db,f"select hydration=40 and xp=0 from public.pets where user_id='{hydration_user}';").stdout.strip()==b't'
        assert sql(db,f"select coins=0 from public.wallets where user_id='{hydration_user}';").stdout.strip()==b't'
        print('PASS 6 concurrent Drink retries: one Water gain, no Coins or XP',flush=True)

        sql(db,f"update public.pets set hunger=100,hydration=100,energy=100,happiness=100 where user_id='{hydration_user}';")
        play_request='d9200000-0000-0000-0000-000000000002'
        results=race(db,[authenticate(hydration_user,f"select public.pet_state('play','{play_request}');")]*6)
        assert all(r.returncode==0 for r in results)
        assert sql(db,f"select hunger=98 and hydration=97 and energy=95 from public.pets where user_id='{hydration_user}';").stdout.strip()==b't'
        sql(db,f"update public.pets set hunger=2,hydration=3,energy=5 where user_id='{hydration_user}';")
        results=race(db,[authenticate(hydration_user,f"select public.pet_state('play','d9300000-0000-0000-0000-{i:012d}');") for i in range(6)])
        assert all(r.returncode==0 for r in results)
        assert sql(db,f"select hunger=0 and hydration=0 and energy=0 and xp=0 from public.pets where user_id='{hydration_user}';").stdout.strip()==b't'
        assert sql(db,f"select count(*)=1 from pawquest_private.pet_care_requests where user_id='{hydration_user}' and request_id::text like 'd9300000-%' and outcome='played';").stdout.strip()==b't'
        print('PASS concurrent Play retries and limited-resource attempts: one three-stat deduction, no negatives',flush=True)

    finally:
        for db in databases:
            # Names are generated here; never drop an existing or user-supplied database.
            sql('postgres', f'drop database {db} with (force);', check=False)
        (ROOT / 'build').mkdir(exist_ok=True)
        (ROOT / 'build/database-tests.log').write_text('\n'.join(logs), encoding='utf-8')


if __name__ == '__main__':
    main()
