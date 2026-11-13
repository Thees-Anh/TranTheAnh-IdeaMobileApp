"""ADB MVP smoke against the LOCAL SQL adapter, never live Supabase.
Requires the fixture-config APK already built. Installs it without clearing app data.
Evidence goes to build/mvp-ui. Auth is a double; SQL business rules are real.
"""
import json,os,re,struct,subprocess,time,uuid,urllib.request,sys
from pathlib import Path
import xml.etree.ElementTree as E

ROOT=next(parent for parent in Path(__file__).resolve().parents if (parent / 'settings.gradle.kts').is_file())
OUT=ROOT/'build/mvp-ui';OUT.mkdir(exist_ok=True)
ADB=str(Path(os.environ['LOCALAPPDATA'])/'Android/Sdk/platform-tools/adb.exe')
BASE='http://127.0.0.1:55447'
RESULTS=[]
def require_fixture():
    build=(ROOT/'androidApp/build/generated/source/buildConfig/debug/com/pawquest/BuildConfig.java').read_text()
    assert 'SUPABASE_URL = "http://127.0.0.1:55447";' in build,'Refuse non-fixture APK'
def adb(*args):return subprocess.check_output([ADB,'-s','emulator-5554',*args],encoding='utf-8',errors='replace').strip()
def control(path):
    with urllib.request.urlopen(BASE+path,timeout=20) as r:return json.load(r)
def tree():
    for _ in range(4):
        output=adb('shell','uiautomator','dump','/data/local/tmp/mvp-ui.xml')
        if 'dumped' not in output.lower():time.sleep(.3);continue
        try:return E.fromstring(adb('exec-out','cat','/data/local/tmp/mvp-ui.xml'))
        except E.ParseError:time.sleep(.3)
    raise AssertionError('No UI hierarchy')
def nodes():return list(tree().iter('node'))
def texts():return [n.get('text') for n in nodes() if n.get('text')]
def bounds(n):return tuple(map(int,re.findall(r'\d+',n.get('bounds',''))))
def tap(n):
    x,y,r,b=bounds(n);adb('shell','input','tap',str((x+r)//2),str((y+b)//2))
def find(label,scroll=False,prefix=False):
    for attempt in range(12 if scroll else 1):
        found=[n for n in nodes() if (n.get('text','').startswith(label) if prefix else label in (n.get('text'),n.get('content-desc')))]
        if found:return found[-1]
        if scroll:
            areas=[n for n in nodes() if n.get('scrollable')=='true' and n.get('package')=='com.pawquest']
            if areas:
                area=max(areas,key=lambda n:(bounds(n)[2]-bounds(n)[0])*(bounds(n)[3]-bounds(n)[1]))
                x,y,r,b=bounds(area)
                start,end=(b-25,y+70) if attempt<6 else (y+70,b-25)
                adb('shell','input','swipe',str((x+r)//2),str(start),str((x+r)//2),str(end),'350')
            else:adb('shell','input','swipe','540','1100','540','500','350')
    raise AssertionError('Missing UI: '+label)
def click(label,scroll=False,prefix=False):tap(find(label,scroll,prefix))
def wait(label,seconds=35):
    end=time.monotonic()+seconds
    while time.monotonic()<end:
        try:return find(label)
        except AssertionError:time.sleep(.5)
    raise AssertionError('Timed out: '+label)
def wait_any(labels,seconds=35):
    end=time.monotonic()+seconds
    while time.monotonic()<end:
        try:
            current=texts()
            for label in labels:
                if label in current:return label
        except AssertionError:pass
        time.sleep(.3)
    raise AssertionError('Timed out waiting for restored screen')
def fill(index,value):
    fields=[n for n in nodes() if n.get('class')=='android.widget.EditText']
    tap(fields[index]);adb('shell','input','text',value)
def capture(name):
    adb('shell','screencap','-p','/sdcard/mvp.png');adb('pull','/sdcard/mvp.png',str(OUT/(name+'.png')))
    raw=(OUT/(name+'.png')).read_bytes()
    return struct.unpack_from('>II',raw,16)
def record(name):RESULTS.append({'test':name,'status':'PASS'});print('PASS',name,flush=True)
def resume():
    adb('shell','input','keyevent','3');adb('shell','am','start','-n','com.pawquest/.MainActivity');time.sleep(1)
def restart():
    adb('shell','am','force-stop','com.pawquest');adb('shell','am','start','-n','com.pawquest/.MainActivity');wait("Mochi's little world")
def buy(name,category):
    click('Shop');wait('Food');click(category)
    for attempt in range(16):
        root=tree();parents={c:p for p in root.iter() for c in p}
        target=next((n for n in root.iter('node') if n.get('text')==name),None)
        while target is not None:
            buttons=[n for n in target.iter('node') if n.get('text')=='Buy']
            if len(buttons)==1:tap(buttons[0]);break
            target=parents.get(target)
        if target is not None:break
        adb('shell','input','swipe','540','1500' if attempt<8 else '650','540','650' if attempt<8 else '1500','350')
    else:raise AssertionError('Cannot buy '+name)
    wait('Buy '+name)
    click('Confirm (',prefix=True)
    wait('Item added to your inventory!');click('Got it!')
def snapshot():return control('/__test/snapshot')

def main():
    require_fixture()
    original={key:adb('shell','settings','get',kind,key) for kind,key in [('system','accelerometer_rotation'),('system','user_rotation'),('secure','show_ime_with_hard_keyboard')]}
    resuming='--resume-countdown' in sys.argv
    if resuming:
        saved=json.loads((OUT/'account.json').read_text());email=saved['email'];title=saved['quest_title']
        RESULTS.extend(json.loads((OUT/'results.json').read_text()))
    else:
        email='native-'+uuid.uuid4().hex[:8]+'@example.invalid'
        title='MVP-Native-'+uuid.uuid4().hex[:6]
        (OUT/'account.json').write_text(json.dumps({'email':email,'quest_title':title}))
    try:
        if not resuming:
            adb('install','-r',str(ROOT/'androidApp/build/outputs/apk/debug/androidApp-debug.apk'))
            adb('shell','am','force-stop','com.pawquest');adb('shell','am','start','-n','com.pawquest/.MainActivity')
            for _ in range(20):
                current=texts()
                if "Mochi's little world" in current or 'New here? Create an account' in current:break
                time.sleep(.3)
            if "Mochi's little world" in texts():
                click('Profile');click('Sign Out',scroll=True);click('Confirm Sign Out')
            wait('New here? Create an account')
            click('New here? Create an account');fill(0,email);fill(1,'FixturePass123!');fill(2,'FixturePass123!')
            adb('shell','input','keyevent','4');click('Create account');wait("Mochi's little world")
            record('Registration + SQL account initialization')
            click('Quests');wait('Create Quest');click('Create Quest')
            adb('shell','settings','put','secure','show_ime_with_hard_keyboard','1')
            fill(0,title);fill(1,'Portrait%sintegration%stest')
            capture('quest-keyboard')
            # Scroll with the real IME open, then hide it to configure duration.
            find('Save quest',scroll=True)
            record('Quest form remains scrollable with keyboard open')
            adb('shell','input','keyevent','4')
            slider=find('Duration in minutes',scroll=True)
            x,y,r,b=bounds(slider)
            adb('shell','input','swipe',str(x+(r-x)//4),str((y+b)//2),str(r-40),str((y+b)//2),'650')
            for _ in range(8):
                if 'Duration · 90 minutes' in texts():break
                click('+ 1 min')
            wait('Duration · 90 minutes');click('Daily',scroll=True);click('Save quest',scroll=True)
            wait('Start Quest');record('Create daily 90-minute Quest through UI')
        actor=None
        for cycle in range(3):
            if resuming and cycle==0:
                wait('Focus Mode')
            elif cycle:
                click('Quests');time.sleep(.5)
                try:find('Start Quest')
                except AssertionError:click('View quest',scroll=True)
                click('Start Quest',scroll=True)
            else:click('Start Quest',scroll=True)
            wait('Focus Mode')
            state=snapshot();session=next(s for s in state['sessions'] if s['title_snapshot']==title and s['status']=='active')
            actor=session['user_id']
            if cycle==0:
                initial_end=session['ends_at']
                if not resuming:
                    for height in [1920,2340,2400]:
                        adb('shell','wm','size','1080x'+str(height));adb('shell','wm','density','480');time.sleep(1)
                        find('Cancel Quest',scroll=True)
                        capture('focus-'+str(height))
                    record('Focus controls reachable at 9:16, 19.5:9, 20:9')
                    adb('shell','am','force-stop','com.pawquest');control('/__test/advance?seconds=1200')
                    adb('shell','am','start','-n','com.pawquest/.MainActivity')
                    if wait_any(['Focus Mode',"Mochi's little world"])=="Mochi's little world":
                        click('Quests');wait('Resume Focus');click('Resume Focus')
                    wait('Focus Mode')
                restored=next(s for s in snapshot()['sessions'] if s['id']==session['id'])
                assert restored['ends_at']==initial_end
                assert any(t.isdigit() and 0<int(t)<=70 for t in texts())
                record('App closed during countdown: original session restored with reduced remaining time')
                control('/__test/advance?seconds=4201')
            else:control('/__test/advance?seconds=5401')
            resume();wait('Quest complete!');click('Claim Reward',scroll=True);wait('Rewards claimed!')
            capture('reward-'+str(cycle+1));click('Return to Pet Home',scroll=True);wait("Mochi's little world")
            if cycle==0:
                buy('Pet food','Food');click('Pet Home');wait('Feed')
                control('/__test/delay_care');click('Feed')
                for _ in range(30):
                    if control('/__test/pending_care')['pending']:break
                    time.sleep(.1)
                assert control('/__test/pending_care')['pending']
                restart();wait('Food: 3')
                record('Restart during Feed after SQL commit: consumed exactly once, pet state restored')
                buy('Hat','Accessories');click('Inventory');wait('Wear');click('Wear');click('Equip',scroll=True)
                wait('Equipment saved.');click('Pet Home');wait("Mochi's little world")
        record('Three explicit server-verified reward claims; no local reward grant')
        buy('Bed','Furniture');click('Inventory');wait('Home');click('Home');click('Place',scroll=True)
        wait('Room saved.');click('Pet Home');wait("Mochi's little world")
        restart();wait('Food: 3')
        data=snapshot()
        assert next(w for w in data['wallets'] if w['user_id']==actor)['coins']==50
        assert next(p for p in data['pets'] if p['user_id']==actor)['xp']==150
        assert len([x for x in data['equipment'] if x['user_id']==actor])==1
        assert len([x for x in data['room'] if x['user_id']==actor])==1
        (OUT/'final-snapshot.json').write_text(json.dumps(data,indent=2))
        record('Main journey restored: 50 Coins, 150 XP, food 3, owned hat equipped and bed placed')
        for height in [1920,2340,2400]:
            adb('shell','wm','size','1080x'+str(height));adb('shell','wm','density','480');time.sleep(.7)
            for label in ['Pet Home','Quests','Shop','Inventory','Profile']:
                n=find(label);assert n.get('enabled')=='true';assert bounds(n)[3]<=height
            capture('pet-home-'+str(height))
            for label in ['Quests','Shop','Inventory','Profile','Pet Home']:click(label);time.sleep(.4)
        record('Five labeled bottom destinations reachable at all three portrait ratios')
        adb('shell','settings','put','system','accelerometer_rotation','0');adb('shell','settings','put','system','user_rotation','1');time.sleep(.7)
        w,h=capture('rotation-request');assert h>w
        record('Landscape rotation request: Activity remains portrait')
        adb('reverse','--remove','tcp:55447')
        click('Inventory');time.sleep(17)
        assert any('load' in t.lower() and ('retry' in t.lower() or 'connection' in t.lower()) for t in texts())
        adb('reverse','tcp:55447','tcp:55447');click('Retry',scroll=True);wait('Food')
        record('Network interruption: load error displayed, retry recovers')
        click('Profile');click('Sign Out',scroll=True);click('Confirm Sign Out');wait('Welcome back')
        adb('shell','am','force-stop','com.pawquest');adb('shell','am','start','-n','com.pawquest/.MainActivity');wait('Welcome back')
        record('Logout clears stored session; restart keeps navigation protected')
        adb('shell','wm','size','1080x1920');adb('shell','wm','density','540')
        fill(0,email);click('Password',scroll=True);adb('shell','input','text','FixturePass123!')
        capture('compact-login-keyboard');click('Sign in',scroll=True);wait("Mochi's little world")
        record('Compact portrait login works with keyboard; account state restored')
    finally:
        (OUT/'results.json').write_text(json.dumps(RESULTS,indent=2))
        adb('reverse','tcp:55447','tcp:55447')
        adb('shell','wm','size','reset');adb('shell','wm','density','reset')
        for kind,key in [('system','accelerometer_rotation'),('system','user_rotation'),('secure','show_ime_with_hard_keyboard')]:
            value=original[key]
            if value!='null':adb('shell','settings','put',kind,key,value)

def finish_login():
    """Continue only the compact-login check after an interrupted UI harness."""
    require_fixture()
    RESULTS.extend(json.loads((OUT/'results.json').read_text()))
    ime=adb('shell','settings','get','secure','show_ime_with_hard_keyboard')
    try:
        adb('shell','wm','size','1080x1920');adb('shell','wm','density','540')
        adb('shell','settings','put','secure','show_ime_with_hard_keyboard','1')
        wait('Welcome back')
        click('Password',scroll=True);adb('shell','input','text','FixturePass123!')
        capture('compact-login-keyboard');click('Sign in',scroll=True);wait("Mochi's little world");wait('Food: 3')
        record('Compact portrait login works with keyboard; account state restored')
    finally:
        (OUT/'results.json').write_text(json.dumps(RESULTS,indent=2))
        adb('shell','wm','size','reset');adb('shell','wm','density','reset')
        if ime!='null':adb('shell','settings','put','secure','show_ime_with_hard_keyboard',ime)

if __name__=='__main__':
    if '--finish-login' in sys.argv:finish_login()
    else:main()
