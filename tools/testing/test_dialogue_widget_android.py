"""Native widget/dialogue smoke. Requires the loopback SQL fixture APK only."""
import sys,json,time,uuid
from pathlib import Path
import test_mvp_android as ui
sys.path.insert(0,str(Path(__file__).resolve().parent/'fixtures'))
import mvp_sql_backend as backend
OUT=ui.ROOT/'build/widget-ui';OUT.mkdir(exist_ok=True);ui.OUT=OUT
backend.DB=json.loads(backend.MARKER.read_text())['database']
assert backend.DB.startswith('pawquest_e2e_')
RESULTS=[]
def record(name):
    RESULTS.append(name);(OUT/'results.json').write_text(json.dumps(RESULTS,indent=2));print('PASS',name,flush=True)
def home():ui.adb('shell','input','keyevent','3');time.sleep(.5)
def run_exercise():
    granted='android.permission.POST_NOTIFICATIONS: granted=true' in ui.adb('shell','dumpsys','package','com.pawquest')
    ui.adb('shell','pm','revoke','com.pawquest','android.permission.POST_NOTIFICATIONS')
    try:exercise()
    finally:
        if granted:ui.adb('shell','pm','grant','com.pawquest','android.permission.POST_NOTIFICATIONS')
def exercise():
    actor=json.loads((OUT/'account.json').read_text())['actor']
    ui.wait('Còn 4 quest hôm nay');ui.find('Thức ăn: 20%');ui.find('Nước: 20%');ui.find('Đói bụng')
    record('Real pinned 2x2 widget shows owner pet, mood, low food/water and four local-day pending quests')
    ui.click('Còn 4 quest hôm nay');ui.wait('Your quests')
    record('Widget quest target opens the Quests destination')
    home();ui.click('Mochi');ui.wait("Mochi's little world");ui.click('Feed',scroll=True);time.sleep(1)
    hunger=backend.sql(f"select to_json(hunger) from public.pets where user_id='{actor}';")
    assert hunger>20
    home();ui.wait(f'Thức ăn: {hunger}%');ui.capture('widget-after-feed')
    record('Successful Feed updates widget from persisted server stats')
    ui.adb('shell','am','kill','com.pawquest');time.sleep(.5);ui.find(f'Thức ăn: {hunger}%')
    record('Launcher retains last verified widget after app process stop')
    ui.click('Mochi');ui.wait("Mochi's little world");home()
    ui.adb('reverse','--remove','tcp:55447')
    try:
        ui.click('Cập nhật widget');time.sleep(4)
        ui.find(f'Thức ăn: {hunger}%');ui.capture('widget-offline')
        assert any('Offline' in t for t in ui.texts())
        record('Offline refresh retains verified stats and marks stale connection')
    finally:ui.adb('reverse','tcp:55447','tcp:55447')
    ui.click('Cập nhật widget');time.sleep(3)
    assert not any('Offline' in t for t in ui.texts())
    record('Manual refresh recovers without notification permission')
    backend.sql(f"update public.pets set xp=400 where user_id='{actor}';select '{{}}'::jsonb;")
    backend.sql("select public.pet_select_species('puppy');",actor=actor)
    ui.click('Cập nhật widget');ui.wait('Puppy');ui.capture('widget-puppy')
    record('Selected Puppy restores its name and illustration on the widget')
    ui.click('Puppy');time.sleep(2)
    if 'Level up!' in ui.texts():ui.click('Later')
    ui.click('Profile');ui.click('Sign Out',scroll=True);ui.wait('Confirm Sign Out');ui.click('Confirm Sign Out');ui.wait('New here? Create an account')
    home();ui.wait('Đăng nhập để xem pet');ui.find('Quest hôm nay: —');ui.capture('widget-signed-out')
    assert 'Puppy' not in ui.texts()
    record('Logout removes cached identity, pet percentages and quest count from the home widget')
def main():
    ui.require_fixture()
    if '--exercise' in sys.argv:run_exercise();return
    if '--pin-only' not in sys.argv:
        if 'Sign out' in ui.texts():ui.click('Sign out')
        ui.wait('New here? Create an account');ui.click('New here? Create an account')
        email='widget-'+uuid.uuid4().hex[:8]+'@example.invalid'
        ui.fill(0,email);ui.fill(1,'FixturePass123!');ui.fill(2,'FixturePass123!')
        ui.adb('shell','input','keyevent','4');ui.click('Create account');ui.wait("Mochi's little world")
        actor=backend.sql("select to_json(id) from auth.users where email='"+email+"';")
        (OUT/'account.json').write_text(json.dumps({'actor':actor,'email':email}))
        backend.sql(f"""update public.pets set hunger=20,hydration=20 where user_id='{actor}';
          insert into public.quests(user_id,title,duration_minutes,repeat_type) select '{actor}','Widget daily '||i,5,'daily' from generate_series(1,3) i;
          insert into public.quests(user_id,title,duration_minutes) values('{actor}','Widget one-time',5);select '{{}}'::jsonb;""")
        ui.adb('shell','input','keyevent','3');ui.adb('shell','am','start','-n','com.pawquest/.MainActivity');time.sleep(1)
        ui.capture('pet-dialogue')
    ui.click('Profile')
    # Select Profile-only chip, avoiding the separate bottom navigation item.
    for n in ui.nodes():
        if n.get('text')=='Profile' and ui.bounds(n)[1]<1000:ui.tap(n);break
    ui.click('Thêm widget ra màn hình chính',scroll=True);time.sleep(1)
    print(json.dumps(ui.texts()),flush=True)
    ui.capture('widget-pin-dialog')
    ui.click('Add to home screen');home();run_exercise()
if __name__=='__main__':main()
