"""Native Focus/progression smoke against the disposable SQL fixture only.

Requires mvp_sql_backend.py on 55447 and its APK. Never uses hosted data.
"""
import json,sys,time,uuid
from pathlib import Path
import test_mvp_android as ui
import test_prototype_care_android as web
sys.path.insert(0,str(Path(__file__).resolve().parent/'fixtures'))
import mvp_sql_backend as backend

OUT=ui.ROOT/'build/progression-ui'
OUT.mkdir(exist_ok=True);ui.OUT=OUT
backend.DB=json.loads(backend.MARKER.read_text())['database']
assert backend.DB.startswith('pawquest_e2e_')
RESULTS=json.loads((OUT/'results.json').read_text()) if '--reading-layout' in sys.argv and (OUT/'results.json').exists() else []
def record(name):
    RESULTS.append(name);(OUT/'results.json').write_text(json.dumps(RESULTS,indent=2));print('PASS',name,flush=True)
def statement(query):return backend.sql(query)
def open_app():
    ui.adb('shell','am','start','-n','com.pawquest/.MainActivity');time.sleep(1)
def notification():
    return 'id=3000' in ui.adb('shell','dumpsys','notification','--noredact')
def main():
    ui.require_fixture()
    if '--reading-layout' in sys.argv:
        for width,height,density,font in [(1080,1920,480,1),(1080,2340,480,1),(1080,2400,480,1),(960,1704,480,1),(1080,2400,480,1.5)]:
            ui.adb('shell','wm','size',f'{width}x{height}');ui.adb('shell','wm','density',str(density))
            ui.adb('shell','settings','put','system','font_scale',str(font));time.sleep(1)
            ui.wait('Focus Mode');ui.find('Cancel Quest',scroll=True)
            # Return scroll to the illustration before checking geometry.
            ui.adb('shell','input','swipe',str(width//2),'400',str(width//2),'1300','350');time.sleep(.5)
            web.forward()
            result=web.dom("""JSON.stringify((()=>{
              const svg=document.querySelector('svg'),v=svg.getBoundingClientRect();
              const ids=['cat-head-group','cat-body-group','cat-tail','prop-book','idea-bulb'];
              return {height:v.height,inside:ids.every(id=>{const r=document.getElementById(id).getBoundingClientRect();
                return r.width>0&&r.height>0&&r.left>=v.left-1&&r.right<=v.right+1&&r.top>=v.top-1&&r.bottom<=v.bottom+1;})};})())""")
            result=json.loads(result);assert result['height']>100 and result['inside'],result
            ui.capture(f'reading-{width}x{height}-font{font}')
        record('Visible reading cat, book and idea bulb fit all three portrait ratios, compact phone and large text')
        return
    if '--resume' in sys.argv:
        actor=json.loads((OUT/'account.json').read_text())['actor']
    else:
        email='progress-'+uuid.uuid4().hex[:8]+'@example.invalid'
        ui.wait('New here? Create an account');ui.click('New here? Create an account')
        ui.fill(0,email);ui.fill(1,'FixturePass123!');ui.fill(2,'FixturePass123!')
        ui.adb('shell','input','keyevent','4');ui.click('Create account');ui.wait("Mochi's little world")
        actor=statement("select to_json(id) from auth.users where email='"+email+"';")
        (OUT/'account.json').write_text(json.dumps({'actor':actor,'email':email}))
        qid=str(uuid.uuid4())
        statement(f"insert into public.quests(id,user_id,title,duration_minutes) values('{qid}','{actor}','Study with Mochi',5);select '{{}}'::jsonb;")
    ui.click('Quests');ui.click('Refresh quests',scroll=True);ui.wait('Study with Mochi');ui.click('View quest',scroll=True);ui.wait('Start Quest');ui.click('Start Quest');ui.wait('Focus Mode')
    # Verify original reading SVG and page animation inside the hardened native WebView.
    web.forward();time.sleep(1)
    assert web.dom("document.getElementById('pawquest-cat-svg').getAttribute('class')")=='state-reading'
    assert web.dom("document.querySelector('svg').getBoundingClientRect().height>100")
    assert web.dom("document.querySelectorAll('script[src],link[href^=http],image[href^=http]').length")==0
    record('Focus embeds the supplied reading SVG offline in the native screen')
    ui.capture('focus-reading')
    # No notification without explicit opt-in, even while OS permission is granted.
    ui.adb('shell','pm','grant','com.pawquest','android.permission.POST_NOTIFICATIONS')
    ui.adb('shell','input','keyevent','3');time.sleep(7)
    assert not notification();open_app();ui.wait('Focus Mode')
    record('Leaving Focus without in-app consent posts no nudge')
    for height in [1920,2340,2400]:
        ui.adb('shell','wm','size','1080x'+str(height));time.sleep(1)
        ui.find('Cancel Quest',scroll=True);ui.capture('focus-'+str(height))
        assert 'ROTATION_0' in ui.adb('shell','dumpsys','window','displays') or 'rotation=0' in ui.adb('shell','dumpsys','display')
    record('Focus controls reachable on 9:16, 19.5:9 and 20:9 portrait viewports')
    # Return without cancelling, then explicitly consent in the existing Profile flow.
    ui.adb('shell','input','keyevent','4');ui.click('Profile');ui.click('Profile',scroll=True)
    ui.click('Cho phép thông báo',scroll=True);ui.wait('Bật thông báo PawQuest?');ui.click('Cho phép')
    ui.click('Quests');ui.click('Resume Focus',scroll=True);ui.wait('Focus Mode')
    ui.adb('shell','input','keyevent','3');time.sleep(7)
    assert notification();open_app();ui.wait('Focus Mode')
    ui.adb('shell','input','keyevent','3');time.sleep(7)
    assert not notification();open_app();ui.wait('Focus Mode')
    record('Consented background Focus sends one gentle nudge and deduplicates repeated exits')
    statement(f"update public.pets set xp=99 where user_id='{actor}'; update pawquest_private.e2e_clock set offset_seconds=310;select '{{}}'::jsonb;")
    ui.adb('shell','input','keyevent','3');open_app();ui.wait('Quest complete!')
    ui.capture('reward-ready');ui.click('Claim Reward',scroll=True);ui.wait('Rewards claimed!')
    ui.wait('Level up!');ui.capture('level-up');ui.click('Open gift');ui.wait('+1 Pet food · saved in Inventory');ui.click('Continue')
    assert statement(f"select to_json(quantity) from public.user_inventory where user_id='{actor}' and item_id='e1000000-0000-0000-0000-000000000001';")==4
    record('Verified claim crosses level 2, shows celebration and awards one persisted food')
    ui.click('Return to Pet Home',scroll=True);ui.click('Profile');ui.wait('Daily Streak');ui.capture('habit-calendar')
    ui.adb('shell','am','force-stop','com.pawquest');open_app();time.sleep(2)
    assert statement(f"select to_json(count(*)) from public.progress_events where user_id='{actor}' and kind='level_up';")==1
    assert statement(f"select to_json(quantity) from public.user_inventory where user_id='{actor}' and item_id='e1000000-0000-0000-0000-000000000001';")==4
    assert 'Level up!' not in ui.texts()
    record('Acknowledged celebration and gifted inventory survive restart without duplication')
if __name__=='__main__':main()
