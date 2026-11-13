"""Shop/equipment/room native smoke; installed loopback SQL APK only."""
import sys,time,json,uuid,zipfile
from pathlib import Path
import test_mvp_android as ui
import test_prototype_care_android as web
sys.path.insert(0,str(Path(__file__).resolve().parent/'fixtures'))
import mvp_sql_backend as db
OUT=ui.ROOT/'build/decor-ui';OUT.mkdir(exist_ok=True);ui.OUT=OUT
db.DB=json.loads(db.MARKER.read_text())['database'];assert db.DB.startswith('pawquest_e2e_')
catalog={i['id']:i for i in json.loads((ui.ROOT/'design/assets/decor/catalog.json').read_text(encoding='utf-8'))}
RESULTS=json.loads((OUT/'results.json').read_text()) if '--resume' in sys.argv and (OUT/'results.json').exists() else []
def record(text):
    RESULTS.append(text);(OUT/'results.json').write_text(json.dumps(RESULTS,indent=2));print('PASS',text,flush=True)
def replace_field(index,value):
    fields=[n for n in ui.nodes() if n.get('class')=='android.widget.EditText']
    ui.tap(fields[index]);ui.adb('shell','input','keycombination','113','29');ui.adb('shell','input','keyevent','67');ui.adb('shell','input','text',value)
def action(name,label):
    for _ in range(14):
        root=ui.tree();parents={c:p for p in root.iter() for c in p};n=next((n for n in root.iter('node') if n.get('text')==name),None)
        while n is not None:
            alternate={'Equip':'Unequip','Place':'Store'}.get(label)
            buttons=[c for c in n.iter('node') if c.get('text') in (label,alternate)]
            if len(buttons)==1:
                if buttons[0].get('text')==alternate:return
                x,y,r,b=ui.bounds(buttons[0])
                if b-y>=20:ui.tap(buttons[0]);return
                break
            n=parents.get(n)
        ui.adb('shell','input','swipe','540','1600' if _<7 else '650','540','650' if _<7 else '1600','300')
    raise AssertionError('No action '+name+' '+label)
def main():
    package=ui.adb('shell','pm','path','com.pawquest').splitlines()[0].removeprefix('package:');ui.adb('pull',package,str(OUT/'installed.apk'))
    with zipfile.ZipFile(OUT/'installed.apk') as z:assert any(b'http://127.0.0.1:55447' in z.read(n) for n in z.namelist() if n.endswith('.dex'))
    if '--resume' in sys.argv:actor=json.loads((OUT/'account.json').read_text())['actor']
    else:
        if 'Sign out' in ui.texts():ui.click('Sign out')
        if 'Already have an account? Sign in' in ui.texts():ui.click('Already have an account? Sign in')
        ui.wait('New here? Create an account');ui.click('New here? Create an account');email='decor-'+uuid.uuid4().hex[:8]+'@example.invalid'
        replace_field(0,email);replace_field(1,'FixturePass123!');replace_field(2,'FixturePass123!');ui.adb('shell','input','keyevent','4');ui.click('Create account');ui.wait("Mochi's little world")
        actor=db.sql("select to_json(id) from auth.users where email='"+email+"';");(OUT/'account.json').write_text(json.dumps({'actor':actor,'email':email}))
        db.sql(f"update public.wallets set coins=10000 where user_id='{actor}';select '{{}}'::jsonb;")
    ui.adb('shell','input','keyevent','3');ui.adb('shell','am','start','-n','com.pawquest/.MainActivity');time.sleep(1)
    for key in ['acc-crown','acc-wings','furn-window','furn-clock']:
        if db.sql(f"select to_json(exists(select 1 from public.user_inventory where user_id='{actor}' and item_id='{catalog[key]['uuid']}'));"):continue
        item=catalog[key];ui.buy(item['name'],'Accessories' if item['category']=='ACCESSORY' else 'Furniture')
        record('Purchased '+key+' through the native confirmation flow')
    ui.click('Inventory');ui.click('Items');ui.wait('Wear');ui.click('Wear')
    for key in ['acc-crown','acc-wings']:action(catalog[key]['name'],'Equip');time.sleep(.4)
    ui.click('Home')
    for key in ['furn-window','furn-clock']:action(catalog[key]['name'],'Place');time.sleep(.4)
    ui.click('Pet Home');ui.wait("Mochi's little world");time.sleep(.5);web.forward()
    assert web.dom("['acc-crown','acc-wings','furn-window','furn-clock'].every(id=>getComputedStyle(document.getElementById(id)).display!=='none')")
    ui.capture('equipped-and-placed');record('HEAD and BACK render together; two wall furniture anchors coexist')
    item=catalog['acc-wizard-hat']
    if not db.sql(f"select to_json(exists(select 1 from public.user_inventory where user_id='{actor}' and item_id='{item['uuid']}'));"):ui.buy(item['name'],'Accessories')
    ui.click('Inventory');ui.click('Items');ui.click('Wear');action(item['name'],'Equip')
    ui.click('Pet Home');time.sleep(.5);web.forward()
    assert web.dom("getComputedStyle(document.getElementById('acc-crown')).display==='none'&&getComputedStyle(document.getElementById('acc-wizard-hat')).display!=='none'")
    record('Equipping a second HEAD item replaces the crown and retains BACK')
    assert db.sql(f"select to_json(coins) from public.wallets where user_id='{actor}';")==9140
    assert db.sql(f"select to_json(quantity) from public.user_inventory where user_id='{actor}' and item_id='{catalog['acc-crown']['uuid']}';")==1
    ui.adb('shell','am','force-stop','com.pawquest');ui.adb('shell','am','start','-n','com.pawquest/.MainActivity');ui.wait("Mochi's little world");time.sleep(.5);web.forward()
    assert web.dom("['acc-wizard-hat','acc-wings','furn-window','furn-clock'].every(id=>getComputedStyle(document.getElementById(id)).display!=='none')")
    record('Wallet debit, replaced ownership, equipment and room persist after restart')
if __name__=='__main__':main()

