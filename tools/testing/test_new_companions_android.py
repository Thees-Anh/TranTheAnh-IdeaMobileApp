"""Native selection/portrait/accessory smoke using only the loopback SQL fixture."""
import sys,json,time,uuid,zipfile
from pathlib import Path
import test_mvp_android as ui
import test_prototype_care_android as web
sys.path.insert(0,str(Path(__file__).resolve().parent/'fixtures'))
import mvp_sql_backend as db
OUT=ui.ROOT/'build/companions-ui';OUT.mkdir(exist_ok=True);ui.OUT=OUT
db.DB=json.loads(db.MARKER.read_text())['database'];assert db.DB.startswith('pawquest_e2e_')
RESULTS=json.loads((OUT/'results.json').read_text()) if any(x in sys.argv for x in ['--selection','--restore']) and (OUT/'results.json').exists() else []
def record(message):
    RESULTS.append(message);(OUT/'results.json').write_text(json.dumps(RESULTS,indent=2));print('PASS',message,flush=True)
def dismiss():
    if 'Level up!' in ui.texts():ui.click('Later')
def restoration():
    ui.adb('shell','am','force-stop','com.pawquest');ui.adb('shell','am','start','-n','com.pawquest/.MainActivity')
    ui.wait_any(['Level up!',"Puppy's little world"]);dismiss();ui.wait("Puppy's little world");time.sleep(.5);web.forward()
    assert web.dom("['pq-head','pq-face','pq-neck'].every(id=>getComputedStyle(document.getElementById(id)).display!=='none')")
    record('Selected pet and all three equipped accessories restore after process restart')
def selection():
    actor=json.loads((OUT/'account.json').read_text())['actor']
    if 'Got it' in ui.texts():ui.click('Got it')
    dismiss()
    for key,name,prefix in [('capybara','Capybara','capy'),('rabbit','Rabbit','bunny'),('puppy','Puppy','dog')]:
        ui.click('Inventory');ui.click('Pets')
        if db.sql(f"select to_json(species_code) from public.pets where user_id='{actor}';")!=key:
            ui.click('Use '+name,scroll=True);time.sleep(.5)
            ui.wait('Got it');ui.click('Got it')
        ui.click('Pet Home');ui.wait(name+"'s little world");time.sleep(.5)
        web.forward()
        assert web.dom(f"!!document.getElementById('{prefix}-whole')")
        assert web.dom(f"['pq-head','pq-face','pq-neck'].every(id=>{{let e=document.getElementById(id);return getComputedStyle(e).display!=='none'&&document.getElementById('{prefix}-head-group').contains(e)}})")
        ui.capture(key+'-equipped')
        record(name+' selected through Inventory; all three owned accessories render attached to its head')
    restoration()
def main():
    package=ui.adb('shell','pm','path','com.pawquest').splitlines()[0].removeprefix('package:')
    ui.adb('pull',package,str(OUT/'installed.apk'))
    with zipfile.ZipFile(OUT/'installed.apk') as archive:
        assert any(b'http://127.0.0.1:55447' in archive.read(n) for n in archive.namelist() if n.endswith('.dex')),'Refuse hosted APK'
    if '--selection' in sys.argv:selection();return
    if '--restore' in sys.argv:restoration();return
    if '--resume' in sys.argv:actor=json.loads((OUT/'account.json').read_text())['actor']
    else:
        if 'Sign out' in ui.texts():ui.click('Sign out')
        ui.wait('New here? Create an account');ui.click('New here? Create an account')
        email='companions-'+uuid.uuid4().hex[:8]+'@example.invalid'
        ui.fill(0,email);ui.fill(1,'FixturePass123!');ui.fill(2,'FixturePass123!');ui.adb('shell','input','keyevent','4');ui.click('Create account');ui.wait("Mochi's little world")
        actor=db.sql("select to_json(id) from auth.users where email='"+email+"';")
        (OUT/'account.json').write_text(json.dumps({'actor':actor,'email':email}))
    ui.click('Inventory');ui.click('Pets')
    for _ in range(3):ui.adb('shell','input','swipe','540','700','540','1800','250')
    ui.click('Refresh pets');time.sleep(.5)
    for name,level in [('Capybara',10),('Rabbit',15)]:
        node=ui.find(f'Preview of {name}, unlocks at level {level}',scroll=True)
        for _ in range(4):
            x,y,r,b=ui.bounds(node)
            if r>x and b-y>100:break
            ui.adb('shell','input','swipe','540','1500' if y>1000 else '700','540','800' if y>1000 else '1400','350')
            node=ui.find(f'Preview of {name}, unlocks at level {level}',scroll=True)
        x,y,r,b=ui.bounds(node);assert r>x and b-y>100
        ui.find(f'Use {name}',scroll=True);ui.capture(name.lower()+'-locked-preview')
    record('Lv1 Inventory displays full-color supplied Capybara/Rabbit previews before unlock')
    db.sql(f"update public.pets set xp=1400 where user_id='{actor}';select '{{}}'::jsonb;")
    for item in [2,3,4]:db.sql(f"select pawquest_private.adjust_inventory('{actor}','e1000000-0000-0000-0000-{item:012}',1);select '{{}}'::jsonb;")
    for slot,item in [('HEAD',2),('FACE',3),('NECK',4)]:db.sql(f"select public.equipment_set('{slot}','e1000000-0000-0000-0000-{item:012}');",actor=actor)
    ui.adb('shell','input','keyevent','3');ui.adb('shell','am','start','-n','com.pawquest/.MainActivity');time.sleep(2);dismiss()
    selection()
if __name__=='__main__':main()
