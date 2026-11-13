"""Native companion smoke against the installed 55447 SQL-backed fixture APK only.

Auth is a double; hydration/deadline/reminder RPCs execute the actual migrated SQL.
No hosted endpoint accepted. Individual --stage values support resuming UI checks.
"""
import argparse,json,re,subprocess,time,urllib.request,uuid,zipfile
from pathlib import Path
import test_mvp_android as ui
import test_prototype_care_android as scene

ROOT=ui.ROOT
OUT=ROOT/'build/companion-ui';OUT.mkdir(exist_ok=True)
ui.OUT=OUT
BASE='http://127.0.0.1:55447'
KEY='sb_publishable_pawquest_fixture_only'
PSQL='C:/Program Files/PostgreSQL/18/bin/psql.exe'
STATE=OUT/'state.json'
RESULTS=OUT/'results.json'

def request(path,body=None,token=None,method=None):
    headers={'apikey':KEY,'Content-Type':'application/json'}
    if token:headers['Authorization']='Bearer '+token
    r=urllib.request.Request(BASE+path,data=None if body is None else json.dumps(body).encode(),headers=headers,method=method)
    with urllib.request.urlopen(r,timeout=25) as response:
        raw=response.read()
        return json.loads(raw) if raw else None

def sql(query):
    db=json.loads((ROOT/'build/mvp-sql-backend.json').read_text())['database']
    assert re.fullmatch(r'pawquest_e2e_[0-9a-f]{16}',db),'Disposable test DB required'
    r=subprocess.run([PSQL,'-X','-Atq','-h','127.0.0.1','-p','55439','-U','postgres','-d',db,'-v','ON_ERROR_STOP=1'],
        input=query,encoding='utf-8',capture_output=True,check=True)
    return json.loads(r.stdout.strip().splitlines()[-1])

def record(name):
    results=json.loads(RESULTS.read_text()) if RESULTS.exists() else []
    results.append({'test':name,'status':'PASS'})
    RESULTS.write_text(json.dumps(results,indent=2))
    print('PASS',name,flush=True)

def refresh():
    ui.adb('shell','input','keyevent','3');ui.adb('shell','am','start','-n','com.pawquest/.MainActivity');time.sleep(1)

def alias():
    return ui.adb('shell','cmd','package','resolve-activity','--brief','-a','android.intent.action.MAIN','-c','android.intent.category.LAUNCHER','com.pawquest').splitlines()[-1]

def until(predicate,description,seconds=40):
    end=time.monotonic()+seconds
    while time.monotonic()<end:
        if predicate():return
        time.sleep(.5)
    raise AssertionError('Timed out: '+description)

def notifications():
    dump=ui.adb('shell','dumpsys','notification','--noredact')
    # Return metadata IDs only; never expose other apps' notification contents.
    records=[line for line in dump.splitlines() if 'NotificationRecord(' in line and 'pkg=com.pawquest ' in line]
    return {int(re.search(r'\bid=(\d+)',line)[1]) for line in records if re.search(r'\bid=(\d+)',line)}

def prefs():
    raw=ui.adb('shell','run-as','com.pawquest','cat','shared_prefs/pawquest_companion.xml')
    import xml.etree.ElementTree as E
    return {node.get('name'):(sorted(c.text for c in node) if node.tag=='set' else node.get('value',node.text)) for node in E.fromstring(raw)}

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--stage',choices=['drink','deadline','consent','navigation','mood','logout','all'],default='all');args=parser.parse_args()
    apk=ui.adb('shell','pm','path','com.pawquest').splitlines()[0].removeprefix('package:')
    ui.adb('pull',apk,str(OUT/'installed-fixture.apk'))
    with zipfile.ZipFile(OUT/'installed-fixture.apk') as archive:
        assert any(b'http://127.0.0.1:55447' in archive.read(n) for n in archive.namelist() if n.endswith('.dex')),'Refuse non-fixture app'
    session=request('/auth/v1/signup',{'email':'companion@example.invalid','password':'FixturePass123!'})
    actor=session['user']['id'];token=session['access_token']
    assert re.fullmatch(r'[0-9a-f-]{36}',actor)
    zone=ui.adb('shell','getprop','persist.sys.timezone');assert re.fullmatch(r'[A-Za-z0-9_+\-/]+',zone)
    stages=['drink','deadline','consent','mood','logout'] if args.stage=='all' else [args.stage]
    for stage in stages:
        if stage=='drink':
            sql(f"""update pawquest_private.e2e_clock set offset_seconds=ceil(extract(epoch from(
                ((date_trunc('day',pawquest_private.e2e_now() at time zone '{zone}')+interval '1 day 9 hours 30 minutes') at time zone '{zone}')-clock_timestamp())))::bigint;
                update public.pets set hunger=20,happiness=50,energy=50,hydration=20,
                    stats_updated_at=pawquest_private.e2e_now(),energy_updated_at=pawquest_private.e2e_now(),hydration_updated_at=pawquest_private.e2e_now() where user_id='{actor}';
                select '{{}}'::jsonb;""")
            refresh();ui.click('Pet Home');ui.wait("Mochi's little world")
            until(lambda:alias().endswith('.HungryLauncher'),'hungry launcher while permission is denied')
            assert not notifications(),'No notification allowed before consent'
            scene.forward();ui.click('Drink');time.sleep(.3)
            until(lambda:scene.dom("document.querySelector('svg').classList.contains('state-drinking')"),'real drinking animation',seconds=2)
            ui.capture('drinking')
            snapshot=request('/__test/snapshot');pet=next(p for p in snapshot['pets'] if p['user_id']==actor)
            assert pet['hydration']==40 and pet['xp']==0
            assert next(w for w in snapshot['wallets'] if w['user_id']==actor)['coins']==0
            assert next(i for i in snapshot['inventory'] if i['user_id']==actor and i['item_id'].endswith('000000000001'))['quantity']==3
            rid=sql(f"select to_jsonb(request_id) from pawquest_private.pet_care_requests where user_id='{actor}' and action='drink' order by request_id limit 1;")
            request('/rest/v1/rpc/pet_state',{'p_action':'drink','p_request_id':rid},token)
            assert sql(f"select to_jsonb(hydration) from public.pets where user_id='{actor}';")==40
            ui.adb('shell','am','force-stop','com.pawquest');ui.adb('shell','am','start','-n','com.pawquest/.MainActivity');ui.wait("Mochi's little world")
            assert sql(f"select to_jsonb(hydration) from public.pets where user_id='{actor}';")==40
            STATE.write_text(json.dumps({'actor':actor,'zone':zone}))
            record('SQL-backed Drink animation, one Water gain on replay, no Coins/XP/food spent, restart restore')
        elif stage=='deadline':
            due=sql(f"select to_jsonb(to_char((pawquest_private.e2e_now()+interval '1 hour') at time zone '{zone}','YYYY-MM-DD HH24:MI'));")
            ui.click('Quests');ui.click('Create Quest');ui.fill(0,'Water%sgarden');ui.adb('shell','input','keyevent','4')
            ui.click('Add a deadline',scroll=True);ui.click('Due date & time',scroll=True)
            ui.adb('shell','input','text',due.replace(' ','%s'));ui.adb('shell','input','keyevent','4')
            ui.click('Save quest',scroll=True);ui.find('Water garden',scroll=True)
            rows=request('/rest/v1/quests',token=token)
            quest=next(q for q in rows if q['title']=='Water garden')
            assert quest['due_at'] is not None
            ui.capture('deadline-saved')
            ctx=request('/rest/v1/rpc/reminder_context',{'p_timezone':zone},token)
            assert any(q['id']==quest['id'] for q in ctx['pending_quests'])
            record('Native one-time deadline form persists UTC due_at and appears in authoritative reminder context')
        elif stage=='consent':
            assert not notifications(),'No alerts before user/OS consent'
            if not any(label in ui.texts() for label in ["Don't allow","Don’t allow"]):
                ui.click('Profile');ui.click('Cho phép thông báo',scroll=True);ui.click('Cho phép')
            deny=ui.wait_any(["Don't allow","Don’t allow"])
            ui.click(deny);time.sleep(1)
            assert prefs().get('notifications.'+actor)=='false'
            refresh();assert not notifications(),'Denied permission must not post'
            assert alias().endswith('.HungryLauncher'),'Icon independent of notification permission'
            ui.click('Cho phép thông báo',scroll=True);ui.click('Cho phép');ui.wait('Allow');ui.click('Allow')
            until(lambda:{1000,1001,1002}<=notifications(),'daily idea, due quest and hungry reminders',seconds=60)
            assert prefs().get('notifications.'+actor)=='true'
            before=prefs().get('sent.'+actor)
            refresh();time.sleep(4)
            assert before==prefs().get('sent.'+actor),'Same-day refresh must not add receipts'
            record('In-app consent + Android deny/allow, correct reminder types and same-day dedup')
            ui.adb('shell','cmd','statusbar','expand-notifications');time.sleep(1);ui.capture('notifications')
            ui.click('Một chút tập trung nữa nhé',prefix=True);ui.wait('Your quests')
            record('Notification tap opens the Quest list')
        elif stage=='navigation':
            assert prefs().get('notifications.'+actor)=='true','Resume after permission consent checks'
            assert any('POST_NOTIFICATIONS: granted=true' in line for line in ui.adb('shell','dumpsys','package','com.pawquest').splitlines())
            before=prefs().get('sent.'+actor)
            refresh();time.sleep(3)
            assert before==prefs().get('sent.'+actor),'Repeated refresh keeps daily receipts unchanged'
            record('Granted notification consent persists across process restart and same-day receipts stay unchanged')
            request('/__test/advance?seconds=86400')
            refresh();until(lambda:{1000,1001,1002}<=notifications(),'new local day reminders',seconds=60)
            ui.click('Quests')
            if 'Your quests' in ui.texts():ui.click('View quest',scroll=True)
            ui.wait('Water garden');ui.click('Profile')
            ui.adb('shell','cmd','statusbar','expand-notifications');time.sleep(1);ui.capture('notifications')
            ui.click('Một chút tập trung nữa nhé',prefix=True);ui.wait('Your quests')
            record('Next-day reminders recur once; notification tap opens list even after leaving Quest details')
        elif stage=='mood':
            sql(f"update public.pets set hunger=100,happiness=20,hydration=100,stats_updated_at=pawquest_private.e2e_now(),hydration_updated_at=pawquest_private.e2e_now() where user_id='{actor}';select '{{}}'::jsonb;")
            refresh();until(lambda:alias().endswith('.BoredLauncher'),'bored launcher')
            until(lambda:1003 in notifications() and 1002 not in notifications(),'bored message replaces resolved hunger')
            ui.click('Pet Home');ui.capture('bored-home')
            ui.click('Profile');ui.click('Icon theo tâm trạng',scroll=True)
            until(lambda:alias().endswith('.NormalLauncher'),'icon setting restores default')
            ui.click('Icon theo tâm trạng',scroll=True);until(lambda:alias().endswith('.BoredLauncher'),'icon setting persists/re-enables mood')
            ui.adb('shell','pm','revoke','com.pawquest','android.permission.POST_NOTIFICATIONS')
            refresh();until(lambda:not notifications(),'runtime permission revocation removes alerts')
            ui.click('Profile')
            ui.click('Cho phép thông báo',scroll=True)
            until(lambda:prefs().get('notifications.'+actor)=='false','user can opt out even while OS permission is revoked')
            record('Hungry/Bored/Normal aliases, resolved-care cancellation, icon switch, revoked permission and independent opt-out')
        elif stage=='logout':
            ui.click('Profile');ui.click('Sign Out',scroll=True);ui.click('Confirm Sign Out');ui.wait('Welcome back')
            until(lambda:alias().endswith('.NormalLauncher') and not notifications(),'logout clears notifications and resets icon')
            record('Logout stops user companion work, clears notifications and restores normal launcher')

if __name__=='__main__':main()
