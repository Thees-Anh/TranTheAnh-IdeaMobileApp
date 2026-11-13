"""Disposable loopback PostgreSQL tests. Never connects to a hosted project.

Requires a running local cluster; creates/drops only a random test database.
Deterministic RNG overrides are installed only in that disposable database.
"""
import argparse
from concurrent.futures import ThreadPoolExecutor
import os
from pathlib import Path
import subprocess
import threading
import uuid

ROOT = Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--pg-bin', default='C:/Program Files/PostgreSQL/18/bin')
    parser.add_argument('--port', default='55439')
    args = parser.parse_args()
    cmd = [str(Path(args.pg_bin) / ('psql.exe' if os.name == 'nt' else 'psql')),
           '-X', '-Atq', '-h', '127.0.0.1', '-p', args.port, '-U', 'postgres',
           '-v', 'ON_ERROR_STOP=1']
    db = 'pawquest_wheel_test_' + uuid.uuid4().hex[:12]

    def sql(query=None, file=None, database=db):
        result = subprocess.run(cmd + ['-d', database] + (['-f', str(file)] if file else ['-c', query]),
                                capture_output=True, encoding='utf-8', errors='replace',
                                env=dict(os.environ, PGCLIENTENCODING='UTF8', PGCONNECT_TIMEOUT='5'))
        if result.returncode:
            raise AssertionError(result.stderr + result.stdout)
        return result.stdout.strip()

    def auth(actor, query):
        return f"set role authenticated; select set_config('request.jwt.claim.sub','{actor}',false); {query}"

    def race(statements):
        barrier = threading.Barrier(len(statements))
        def call(statement):
            barrier.wait(timeout=15)
            return sql(statement).splitlines()[-1]
        with ThreadPoolExecutor(max_workers=len(statements)) as executor:
            return list(executor.map(call, statements))

    sql(f'create database {db}', database='postgres')
    try:
        sql(file=ROOT / 'supabase/tests/local_auth_shim.sql')
        legacy = 'ab280000-0000-0000-0000-000000000001'
        for migration in sorted((ROOT / 'supabase/migrations').glob('*.sql')):
            if migration.name == '20261110002800_lucky_wheel.sql':
                try:
                    sql(file=ROOT / 'supabase/operations/enable_lucky_wheel.sql')
                    raise AssertionError('Activation unexpectedly succeeded without migration 028')
                except AssertionError as error:
                    assert 'Apply migration 028 before enabling Lucky Wheel' in str(error)
                sql(f"insert into auth.users values('{legacy}','legacy-wheel@example.invalid'); update public.pets set xp=1400 where user_id='{legacy}';")
            sql(file=migration)
        assert sql(f"select count(*)=4 from public.user_pet_unlocks where user_id='{legacy}'") == 't'
        print('PASS fresh migration replay and legacy ownership backfill', flush=True)
        before=sql("select jsonb_build_array((select count(*) from public.lucky_wheel_spins),(select count(*) from public.wallet_transactions),(select count(*) from public.shard_ledger),(select sum(coins) from public.wallets))")
        for _ in range(2):
            sql(file=ROOT / 'supabase/operations/enable_lucky_wheel.sql')
        assert sql("select enabled from public.lucky_wheel_banners where code='lucky_wheel'") == 't'
        after=sql("select jsonb_build_array((select count(*) from public.lucky_wheel_spins),(select count(*) from public.wallet_transactions),(select count(*) from public.shard_ledger),(select sum(coins) from public.wallets))")
        assert before==after
        print('PASS explicit banner activation twice: no grants, ledger or balance changes',flush=True)
        for name in ['item_case_shards.sql', 'item_case_open.sql', 'item_shard_exchange.sql', 'lucky_wheel.sql']:
            sql(file=ROOT / 'supabase/tests' / name)
            print('PASS ' + name, flush=True)

        # Test-only replacement in a random disposable DB, not a migration or production hook.
        sql("""create or replace function pawquest_private.item_case_random_below(p_upper bigint) returns bigint
        language plpgsql volatile security definer set search_path='' as $$ begin
        return coalesce(nullif(current_setting('pawquest.test_draw',true),''),'0')::bigint % p_upper; end $$;
        update public.lucky_wheel_banners set enabled=true;
        update pawquest_private.item_case_api_settings set max_attempts=600;
        """)
        banner = 'e2800000-0000-0000-0000-000000000001'
        actor = 'ab280000-0000-0000-0000-000000000002'
        sql(f"insert into auth.users values('{actor}','race-wheel@example.invalid'); update public.wallets set coins=100 where user_id='{actor}';")
        key = str(uuid.uuid4())
        request = auth(actor, f"select public.lucky_wheel_spin('{banner}','COINS','{key}')")
        results = race([request] * 6)
        assert len(set(results)) == 1 and 'spun' in results[0]
        assert sql(f"select coins=0 from public.wallets where user_id='{actor}'") == 't'
        assert sql(f"select count(*)=1 from public.lucky_wheel_spins where user_id='{actor}'") == 't'
        sql(f"update public.wallets set coins=100 where user_id='{actor}'")
        results = race([auth(actor, f"select public.lucky_wheel_spin('{banner}','COINS','{uuid.uuid4()}')") for _ in range(4)])
        assert sum('spun' in result for result in results) == 1
        assert sum('insufficient_coins' in result for result in results) == 3
        print('PASS concurrent same-key recovery and competing spins: no double debit/overspend', flush=True)

        sql(f"update public.wallets set coins=100 where user_id='{actor}'")
        sql(auth(actor, f"set pawquest.test_draw='7500'; select public.lucky_wheel_spin('{banner}','COINS','{uuid.uuid4()}')"))
        results = race([auth(actor, f"select public.lucky_wheel_spin('{banner}','TICKET','{uuid.uuid4()}')") for _ in range(4)])
        assert sum('spun' in result for result in results) == 1
        assert sum('insufficient_tickets' in result for result in results) == 3
        assert sql(f"select balance=0 from public.lucky_wheel_ticket_balances where user_id='{actor}'") == 't'
        print('PASS competing Ticket spins: one payment, no negative tickets', flush=True)

        sql(f"insert into public.shard_ledger(user_id,currency,request_id,reason,delta,balance_after,note) values('{actor}','PET_SHARD',gen_random_uuid(),'ADMIN_ADJUSTMENT',90,0,'Test funding')")
        keys = [str(uuid.uuid4()), str(uuid.uuid4())]
        for species, request_id in zip(['rabbit', 'capybara'], keys):
            sql(auth(actor, f"select public.pet_shard_quote('{species}','{request_id}')"))
        results = race([auth(actor, f"select public.pet_shard_redeem('{species}','{request_id}')") for species, request_id in zip(['rabbit', 'capybara'], keys)])
        assert sum('redeemed' in result for result in results) == 1
        assert sql(f"select balance>=0 from public.shard_balances where user_id='{actor}' and currency='PET_SHARD'") == 't'

        mixed = 'ab280000-0000-0000-0000-000000000003'
        sql(f"insert into auth.users values('{mixed}','mixed-wheel@example.invalid'); update public.wallets set coins=100 where user_id='{mixed}'; insert into public.shard_ledger(user_id,currency,request_id,reason,delta,balance_after,note) values('{mixed}','PET_SHARD',gen_random_uuid(),'ADMIN_ADJUSTMENT',50,0,'Test funding'); insert into public.user_pet_unlocks(user_id,species_code,source) values('{mixed}','puppy','LEGACY'),('{mixed}','capybara','LEGACY');")
        redeem_key = str(uuid.uuid4())
        sql(auth(mixed, f"select public.pet_shard_quote('rabbit','{redeem_key}')"))
        results = race([auth(mixed, f"set pawquest.test_draw='9800'; select public.lucky_wheel_spin('{banner}','COINS','{uuid.uuid4()}')"),
                        auth(mixed, f"select public.pet_shard_redeem('rabbit','{redeem_key}')")])
        assert sql(f"select count(*)=1 from public.user_pet_unlocks where user_id='{mixed}' and species_code='rabbit'") == 't'
        assert 'spun' in results[0] and ('redeemed' in results[1] or 'already_owned' in results[1])

        replay_actor = 'ab280000-0000-0000-0000-000000000004'
        replay_key = str(uuid.uuid4())
        sql(f"insert into auth.users values('{replay_actor}','replay-pet@example.invalid'); insert into public.shard_ledger(user_id,currency,request_id,reason,delta,balance_after,note) values('{replay_actor}','PET_SHARD',gen_random_uuid(),'ADMIN_ADJUSTMENT',50,0,'Test funding')")
        sql(auth(replay_actor, f"select public.pet_shard_quote('rabbit','{replay_key}')"))
        results = race([auth(replay_actor, f"select public.pet_shard_redeem('rabbit','{replay_key}')")] * 6)
        assert len(set(results)) == 1 and 'redeemed' in results[0]
        assert sql(f"select count(*)=1 from public.pet_shard_redemptions where user_id='{replay_actor}'") == 't'
        sql('set constraints all immediate')
        print('PASS concurrent Pet replay, competing redemptions and spin-vs-redemption unique pet grant', flush=True)
    finally:
        sql(f'drop database {db} with (force)', database='postgres')


if __name__ == '__main__':
    main()
