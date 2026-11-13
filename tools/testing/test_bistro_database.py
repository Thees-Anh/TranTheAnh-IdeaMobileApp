"""Replay migrations and exercise Bistro only in a disposable loopback database."""
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
import argparse,json,os,subprocess,uuid,threading
ROOT=Path(__file__).resolve().parents[2]

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--port',default='55441');args=parser.parse_args()
    db='pawquest_bistro_test_'+uuid.uuid4().hex[:12]
    psql=['C:/Program Files/PostgreSQL/18/bin/psql.exe','-X','-Atq','-h','127.0.0.1','-p',args.port,'-U','postgres','-v','ON_ERROR_STOP=1']
    def sql(query=None,file=None,database=db,ok=True):
        result=subprocess.run(psql+['-d',database]+(['-f',str(file)] if file else ['-c',query]),capture_output=True,text=True,encoding='utf-8',env=dict(os.environ,PGCLIENTENCODING='UTF8',PGCONNECT_TIMEOUT='5'))
        if ok and result.returncode:raise AssertionError(result.stderr+result.stdout)
        return result
    def scalar(query):return sql(query).stdout.strip().splitlines()[-1]
    actor='ba290000-0000-0000-0000-000000000001';other='ba290000-0000-0000-0000-000000000002'
    def auth(owner,query):return f"set role authenticated;select set_config('request.jwt.claim.sub','{owner}',false);{query}"
    def meal(item,key=None,price=10,owner=actor):
        key=key or str(uuid.uuid4())
        return json.loads(scalar(auth(owner,f"select public.bistro_feed('{item}','{key}',{price});")))
    def amounts():return scalar(f"select coins||':'||xp from public.wallets join public.pets using(user_id) where user_id='{actor}'")
    sql(f'create database {db}',database='postgres')
    try:
        sql(file=ROOT/'supabase/tests/local_auth_shim.sql')
        for migration in sorted((ROOT/'supabase/migrations').glob('*.sql')):sql(file=migration)
        sql(f"insert into auth.users(id,email) values('{actor}','bistro@example.invalid'),('{other}','other@example.invalid');update public.wallets set coins=5000;update public.pets set hunger=0,happiness=0,hydration=55;")
        menu=json.loads(scalar(auth(actor,'select public.bistro_menu();')))
        original=json.loads((ROOT/'design/prototypes/food/menu.json').read_text(encoding='utf-8'))
        assert len(menu)==12
        for actual,expected in zip(menu,original):
            assert [actual[k] for k in ['asset_id','price_coins','hunger_gain','happiness_gain','xp_gain','meal_type']]==[expected[k] for k in ['id','price','hungerRestore','happinessBonus','expBonus','type']]
        item=menu[0]['item_id'];key=str(uuid.uuid4());before=amounts()
        assert meal(item,price=11)['status']=='price_changed' and amounts()==before
        assert meal('e1000000-0000-0000-0000-000000000001')['status']=='item_unavailable'
        first=meal(item,key);assert first['status']=='fed' and first['receipt']['xp_granted']==5
        after=amounts();assert meal(item,key)==first and amounts()==after
        sql(f"update public.item_catalog set price_coins=99 where id='{item}'")
        assert meal(item,key)==first
        sql(f"update public.item_catalog set price_coins=10 where id='{item}'")
        for entry in menu[1:]:
            sql(f"update public.pets set hunger=0,happiness=0 where user_id='{actor}'")
            result=meal(entry['item_id'],price=entry['price_coins']);assert result['status']=='fed'
            assert result['receipt']['hunger_granted']==entry['hunger_gain'] and result['receipt']['happiness_granted']==entry['happiness_gain']
        assert scalar(f"select coins from public.wallets where user_id='{actor}'")==str(5000-sum(m['price'] for m in original))
        assert scalar(f"select xp from public.pets where user_id='{actor}'")==str(sum(m['expBonus'] for m in original))
        assert scalar(f"select hydration from public.pets where user_id='{actor}'")=='55'
        assert scalar(f"select count(*) from public.user_inventory where user_id='{actor}' and item_id in(select item_id from public.bistro_foods) and quantity<>0")=='0'
        assert scalar(f"select count(*) from public.user_pet_unlocks where user_id='{actor}'")=='1'
        sql(f"update public.pets set hunger=99,happiness=99 where user_id='{actor}'")
        capped=meal(item)['receipt'];assert capped['hunger_granted']==1 and capped['happiness_granted']==1 and capped['xp_granted']==5
        assert scalar(auth(other,'select count(*) from public.bistro_meals;'))=='0'
        assert scalar("select not has_table_privilege('authenticated','public.bistro_meals','INSERT') and not has_table_privilege('authenticated','public.bistro_foods','UPDATE')")=='t'
        assert sql(auth(actor,"update public.bistro_foods set xp_gain=999;"),ok=False).returncode!=0
        sql(f"update public.pets set sleep_started_at=clock_timestamp(),sleep_start_energy=10,sleep_until=clock_timestamp()+interval '1 hour' where user_id='{actor}'")
        sleeping=amounts();assert meal(item)['status']=='pet_sleeping' and amounts()==sleeping
        sql(f"update public.pets set sleep_started_at=null,sleep_start_energy=null,sleep_until=null where user_id='{actor}';update public.wallets set coins=100 where user_id='{actor}'")
        def race(keys):
            gate=threading.Barrier(2)
            def run(key):gate.wait();return meal(item,key)
            with ThreadPoolExecutor(2) as pool:return list(pool.map(run,keys))
        key=str(uuid.uuid4());race_before=amounts();same=race([key,key]);assert same[0]==same[1] and same[0]['status']=='fed'
        assert scalar(f"select coins from public.wallets where user_id='{actor}'")=='90'
        sql(f"update public.wallets set coins=10 where user_id='{actor}'")
        different=race([str(uuid.uuid4()),str(uuid.uuid4())]);assert sorted(r['status'] for r in different)==['fed','insufficient_balance']
        sql(f"update public.wallets set coins=100 where user_id='{actor}';create function public.test_bistro_rollback() returns trigger language plpgsql as $$begin raise exception 'test rollback';end$$;create trigger test_rollback after insert on public.bistro_meals for each row execute function public.test_bistro_rollback();")
        before=amounts();failed=sql(auth(actor,f"select public.bistro_feed('{item}','{uuid.uuid4()}',10);"),ok=False)
        assert failed.returncode!=0 and amounts()==before
        sql('drop trigger test_rollback on public.bistro_meals;drop function public.test_bistro_rollback();')
        assert sql(auth(actor,f"select public.bistro_feed('{item}','{uuid.uuid4()}',10,999);"),ok=False).returncode!=0
        print('PASS 12 actual recipes, all grants, caps, unchanged hydration, history isolation, permissions, price guard, retry, concurrency, rollback and forged inputs')
    finally:sql(f'drop database if exists {db}',database='postgres')

if __name__=='__main__':main()
