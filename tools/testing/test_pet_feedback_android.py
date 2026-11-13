"""Native feedback / wake / pets-tab regression. Disposable SQL fixture APK only."""
import json,time,uuid,sys,zipfile
from pathlib import Path
import test_mvp_android as ui
import test_companion_android as fixture
import test_prototype_care_android as scene

OUT=ui.ROOT/'build/pet-feedback-ui';OUT.mkdir(exist_ok=True);ui.OUT=OUT

def restart():
    ui.adb('shell','am','force-stop','com.pawquest')
    ui.adb('shell','am','start','-n','com.pawquest/.MainActivity')
    ui.wait("Mochi's little world")

def main():
    apk=ui.adb("shell","pm","path","com.pawquest").splitlines()[0].removeprefix("package:")
    ui.adb("pull",apk,str(OUT/"installed-fixture.apk"))
    with zipfile.ZipFile(OUT/"installed-fixture.apk") as archive:
        assert any(b"http://127.0.0.1:55447" in archive.read(n) for n in archive.namelist() if n.endswith(".dex")),"Refuse non-fixture APK"
    if '--resume-sleep' not in sys.argv:
        ui.adb('reverse','tcp:55447','tcp:55447')
        ui.adb('shell','am','force-stop','com.pawquest');ui.adb('shell','am','start','-n','com.pawquest/.MainActivity')
        ui.wait('New here? Create an account');ui.click('New here? Create an account')
        email='care-feedback-'+uuid.uuid4().hex[:6]+'@example.invalid'
        ui.fill(0,email);ui.fill(1,'FixturePass123!');ui.fill(2,'FixturePass123!')
        ui.adb('shell','input','keyevent','4');ui.click('Create account');ui.wait("Mochi's little world")
        actor=fixture.request('/auth/v1/signup',{'email':email,'password':'FixturePass123!'})['user']['id']
        for label in ['Feed','Drink','Sleep']:
            ui.click(label);ui.wait('Your companion')
            assert 'Got it' in ui.texts()
            ui.capture('full-'+label.lower());ui.click('Got it')
            assert fixture.request('/__test/snapshot')['wallets'][0]['coins']==0
        ui.record('Full-stat Feed/Drink/Sleep show explicit feedback dialogs without spending Coins')
        fixture.sql(f"update public.pets set hunger=20 where user_id='{actor}';update public.pet_food_inventory set quantity=0 where user_id='{actor}';select '{{}}'::jsonb;")
        restart();ui.click('Feed');ui.wait('Earn Coins');ui.capture('empty-food-wallet');ui.click('Earn Coins');ui.wait('Your quests')
        ui.record('No food + empty wallet explains recovery and routes to Quests')
        ui.click('Shop');ui.wait('Earn Coins');ui.click('Buy');ui.wait('Buy Pet food')
        ui.click('Confirm (',prefix=True);ui.wait('Purchase Failed')
        assert any('enough coins' in t for t in ui.texts());ui.capture('insufficient-purchase');ui.click('Got it!')
        ui.record('Insufficient-balance purchase returns visible server rejection without debit')
        fixture.sql(f"update public.pets set energy=20,energy_updated_at=pawquest_private.e2e_now() where user_id='{actor}';select '{{}}'::jsonb;")
        restart();ui.click('Sleep');time.sleep(.5);scene.forward()
    else:
        scene.forward()
        if not scene.dom("document.querySelector('svg').classList.contains('state-sleeping')"):
            ui.click('Sleep');time.sleep(.5)
    assert scene.dom("document.querySelector('svg').classList.contains('state-sleeping')")
    # Android's idle detector may otherwise wait indefinitely on sleeping SVG motion.
    scene.dom('document.querySelectorAll("*").forEach(e=>e.style.animationPlayState="paused"); true')
    page=json.loads(scene.dom("JSON.stringify((()=>{const r=document.getElementById('cat-head-group').getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2,dpr:devicePixelRatio}})())"))
    stage=next(n for n in ui.nodes() if n.get('content-desc','').startswith('Mochi, sleeping.'))
    x,y,_,_=ui.bounds(stage)
    ui.adb('shell','input','tap',str(int(x+page['x']*page['dpr'])),str(int(y+page['y']*page['dpr'])))
    scene.dom('document.querySelectorAll("*").forEach(e=>e.style.animationPlayState="running"); true')
    time.sleep(.6)
    pet=fixture.request('/__test/snapshot')['pets'][0]
    assert pet['sleep_until'] is None and 20<=pet['energy']<100
    assert scene.dom("document.querySelector('svg').classList.contains('state-laughing')")
    ui.capture('wake-touch');restart()
    assert fixture.request('/__test/snapshot')['pets'][0]['sleep_until'] is None
    ui.record('Actual SVG head touch wakes sleep, plays happy reaction and persists after restart')
    ui.click('Inventory');ui.click('Pets');ui.wait('Your pets');ui.wait('Selected')
    assert 'Lv. 5 companion' in ui.texts();ui.capture('pet-collection')
    ui.record('Inventory Pets shows selected Mochi and upcoming level-5 milestone')
    (OUT/'results.json').write_text(json.dumps(ui.RESULTS,indent=2))
    ui.click('Profile');ui.click('Sign Out',scroll=True);ui.click('Confirm Sign Out');ui.wait('Welcome back')

if __name__=='__main__':main()
