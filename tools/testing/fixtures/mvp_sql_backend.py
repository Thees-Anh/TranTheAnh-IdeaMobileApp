"""LOCAL TEST ONLY: Auth double + small REST adapter over real migrated PostgreSQL RPCs.

Creates a random disposable DB. Never accepts a hosted endpoint/database name.
The accelerated clock is installed ONLY in this disposable DB, never migrations.
"""
import argparse,json,re,subprocess,uuid,time
from pathlib import Path
from urllib.parse import urlparse,parse_qs
from auth_http_fixture import Handler as AuthHandler,ThreadingHTTPServer,ACCESS

ROOT=next(parent for parent in Path(__file__).resolve().parents if (parent / 'settings.gradle.kts').is_file())
MARKER=ROOT/'build/mvp-sql-backend.json'
PSQL='C:/Program Files/PostgreSQL/18/bin/psql.exe'
DB=None
DROP_NEXT=set()
DELAY_CARE=False
CARE_RESPONSE_PENDING=False
class SqlFailure(Exception):
    def __init__(self,code): self.code=code

def sql(query,args=None,actor=None,db=None):
    cmd=[PSQL,'-X','-Atq','-h','127.0.0.1','-p','55439','-U','postgres','-d',db or DB,
        '-v','ON_ERROR_STOP=1','-v','VERBOSITY=sqlstate']
    for key,value in (args or {}).items(): cmd+=['-v',f'{key}={value}']
    if actor:
        cmd+=['-v',f'actor={actor}']
        query="set role authenticated; select set_config('request.jwt.claim.sub', :'actor',false);\n"+query
    r=subprocess.run(cmd,input=query,encoding='utf-8',capture_output=True)
    if r.returncode:
        match=re.search(r'ERROR:\s+([A-Z0-9]{5})',r.stderr)
        raise SqlFailure(match[1] if match else 'XX000')
    lines=r.stdout.strip().splitlines()
    return json.loads(lines[-1]) if lines else None

def initialize():
    global DB
    DB='pawquest_e2e_'+uuid.uuid4().hex[:16]
    sql(f'create database {DB};',db='postgres')
    MARKER.write_text(json.dumps({'database':DB,'port':55447})+'\n')
    sql((ROOT/'supabase/tests/local_auth_shim.sql').read_text(encoding='utf-8'))
    for path in sorted((ROOT/'supabase/migrations').glob('*.sql')): sql(path.read_text(encoding='utf-8'))
    sql("""
    create table pawquest_private.e2e_clock(offset_seconds bigint not null);
    insert into pawquest_private.e2e_clock values(0);
    create function pawquest_private.e2e_now() returns timestamptz language sql volatile security definer set search_path='' as $$
      select pg_catalog.clock_timestamp()+offset_seconds*interval '1 second' from pawquest_private.e2e_clock;
    $$;
    revoke all on pawquest_private.e2e_clock from public,anon,authenticated,service_role;
    revoke all on function pawquest_private.e2e_now() from public,anon,authenticated,service_role;
    alter table public.pets alter column stats_updated_at set default pawquest_private.e2e_now();
    alter table public.pets alter column energy_updated_at set default pawquest_private.e2e_now();
    alter table public.pets alter column hydration_updated_at set default pawquest_private.e2e_now();
    do $rewrite$
    declare f record;
    begin
      for f in select pg_get_functiondef(p.oid) definition from pg_proc p join pg_namespace n on n.oid=p.pronamespace
        where n.nspname in ('public','pawquest_private') and p.prokind='f' and p.proname<>'e2e_now'
      loop
        if position('clock_timestamp()' in f.definition)>0 then
          execute replace(f.definition,'clock_timestamp()','pawquest_private.e2e_now()');
        end if;
      end loop;
    end $rewrite$;
    select '{}'::jsonb;
    """)

RPC={
 'widget_context':[('p_timezone','text')],
 'progress_snapshot':[('p_month','date')], 'progress_ack':[('p_event_id','uuid')],
 'ensure_account':[], 'quest_daily_reward_count':[], 'shop_catalog':[], 'inventory_items':[],
 'equipment_state':[], 'room_state':[], 'reminder_context':[('p_timezone','text')],
 'focus_start':[('p_quest_id','uuid'),('p_request_id','uuid')],
 'focus_reconcile':[('p_session_id','uuid')], 'reward_reconcile':[('p_session_id','uuid')],
 'focus_cancel':[('p_session_id','uuid')], 'reward_claim':[('p_session_id','uuid')],
 'shop_purchase':[('p_item_id','uuid'),('p_quantity','integer'),('p_request_id','uuid')],
 'pet_state':[('p_action','text'),('p_request_id','uuid')],
 'pet_collection':[], 'pet_select_species':[('p_code','text')],
 'pet_companion_context':[('p_timezone','text')],
 'equipment_set':[('p_slot','text'),('p_item_id','uuid')],
 'room_set':[('p_slot','text'),('p_item_id','uuid')],
}
QUEST_FIELDS={'title':'text','description':'text','category':'text','duration_minutes':'integer','repeat_type':'text','archived':'boolean','due_at':'timestamptz'}

class Handler(AuthHandler):
    def owner(self): return ACCESS.get(self.headers.get('Authorization','').removeprefix('Bearer '))
    def reply(self,status,value):
        global DELAY_CARE,CARE_RESPONSE_PENDING
        if self.path.split('?')[0]=='/auth/v1/signup' and status==200 and 'user' in value:
            user=value['user']
            sql("insert into auth.users(id,email) values(:'uid'::uuid,:'email') on conflict do nothing; select '{}'::jsonb;",
                {'uid':user['id'],'email':user['email']})
        rpc=self.path.split('?')[0].removeprefix('/rest/v1/rpc/')
        if status==200 and rpc in DROP_NEXT:
            DROP_NEXT.remove(rpc);self.close_connection=True;return
        if status==200 and rpc=='pet_state' and DELAY_CARE and value.get('care_result'):
            DELAY_CARE=False;CARE_RESPONSE_PENDING=True
            time.sleep(8)
            CARE_RESPONSE_PENDING=False
        try: super().reply(status,value)
        except ConnectionError: pass
    def do_GET(self):
        global DELAY_CARE
        parsed=urlparse(self.path)
        if parsed.path=='/__test/drop_next':
            rpc=parse_qs(parsed.query).get('rpc',[''])[0]
            if rpc not in RPC: self.reply(400,{});return
            DROP_NEXT.add(rpc);self.reply(200,{'armed':True});return
        if parsed.path=='/__test/delay_care':
            DELAY_CARE=True;self.reply(200,{'armed':True});return
        if parsed.path=='/__test/pending_care':
            self.reply(200,{'pending':CARE_RESPONSE_PENDING});return
        if parsed.path=='/__test/advance':
            seconds=int(parse_qs(parsed.query).get('seconds',['0'])[0])
            if not 0<=seconds<=86400: self.reply(400,{});return
            sql("update pawquest_private.e2e_clock set offset_seconds=offset_seconds+:'seconds'::bigint; select '{}'::jsonb;",{'seconds':seconds})
            self.reply(200,{'advanced_seconds':seconds});return
        if parsed.path=='/__test/snapshot':
            self.reply(200,sql("""select jsonb_build_object(
              'wallets',(select coalesce(jsonb_agg(to_jsonb(w)),'[]') from public.wallets w),
              'inventory',(select coalesce(jsonb_agg(to_jsonb(i)),'[]') from public.user_inventory i),
              'pets',(select coalesce(jsonb_agg(to_jsonb(p)),'[]') from public.pets p),
              'equipment',(select coalesce(jsonb_agg(to_jsonb(e)),'[]') from public.pet_equipment e),
              'room',(select coalesce(jsonb_agg(to_jsonb(r)),'[]') from public.room_furniture r),
              'sessions',(select coalesce(jsonb_agg(to_jsonb(s)),'[]') from public.quest_sessions s),
              'ledger',(select coalesce(jsonb_agg(to_jsonb(t)),'[]') from public.wallet_transactions t));"""));return
        if parsed.path!='/rest/v1/quests': return super().do_GET()
        user=self.owner()
        if not user: self.reply(401,{});return
        try:
            rows=sql("select coalesce(jsonb_agg(to_jsonb(q) order by q.created_at desc),'[]') from public.quests q where user_id=auth.uid() and not archived;",actor=user['id'])
            self.reply(200,rows)
        except SqlFailure as e: self.fail(e)
    def fail(self,error):
        status=403 if error.code=='42501' else 409 if error.code=='23505' else 400
        self.reply(status,dict(code=error.code,message='Test SQL request rejected',details=None,hint=None))
    def do_POST(self):
        path=urlparse(self.path).path
        if not path.startswith('/rest/v1/'): return super().do_POST()
        user=self.owner()
        if not user: self.reply(401,{});return
        body=json.loads(self.rfile.read(int(self.headers.get('Content-Length',0))) or b'{}')
        try:
            if path=='/rest/v1/quests': return self.quest_write(user,body,False)
            name=path.removeprefix('/rest/v1/rpc/')
            if name not in RPC: self.reply(404,{});return
            if set(body)-{n for n,t in RPC[name]}: self.reply(400,{});return
            args={};params=[]
            for i,(key,typ) in enumerate(RPC[name]):
                value=body.get(key)
                if value is None: params.append('null')
                else: args['arg'+str(i)]=str(value);params.append(f":'arg{i}'::{typ}")
            self.reply(200,sql(f"select to_jsonb(public.{name}({','.join(params)}));",args,actor=user['id']))
        except SqlFailure as e: self.fail(e)
    def do_PATCH(self):
        user=self.owner()
        if not user: self.reply(401,{});return
        body=json.loads(self.rfile.read(int(self.headers.get('Content-Length',0))) or b'{}')
        try: self.quest_write(user,body,True)
        except SqlFailure as e: self.fail(e)
    def quest_write(self,user,body,patch):
        if isinstance(body,list): body=body[0]
        if not body or set(body)-set(QUEST_FIELDS): self.reply(400,{});return
        args={};values=[]
        for i,(key,value) in enumerate(body.items()):
            if value is None:
                values.append('null');continue
            args['v'+str(i)]=str(value).lower() if isinstance(value,bool) else str(value)
            values.append(f":'v{i}'::{QUEST_FIELDS[key]}")
        if patch:
            args['qid']=parse_qs(urlparse(self.path).query)['id'][0].removeprefix('eq.')
            setters=','.join(f'{key}={value}' for key,value in zip(body,values))
            statement=f"update public.quests set {setters} where id=:'qid'::uuid and user_id=auth.uid() returning *"
        else: statement=f"insert into public.quests({','.join(body)}) values({','.join(values)}) returning *"
        result=sql(f"with changed as ({statement}) select coalesce(jsonb_agg(to_jsonb(changed)),'[]') from changed;",args,actor=user['id'])
        self.reply(200 if patch else 201,result)

def main():
    global DB
    parser=argparse.ArgumentParser();parser.add_argument('--cleanup',action='store_true');args=parser.parse_args()
    if args.cleanup:
        data=json.loads(MARKER.read_text());name=data['database']
        assert re.fullmatch(r'pawquest_e2e_[0-9a-f]{16}',name)
        sql(f'drop database {name} with(force);',db='postgres');print('Dropped disposable E2E database');return
    initialize()
    print('Ready: local SQL-backed test adapter on 127.0.0.1:55447',flush=True)
    ThreadingHTTPServer(('127.0.0.1',55447),Handler).serve_forever()

if __name__=='__main__': main()
