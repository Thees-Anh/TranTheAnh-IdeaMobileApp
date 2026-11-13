"""Native visual smoke test: requires 55446 room fixture APK already logged in.

No live backend calls. Restores emulator display overrides even if a check fails.
Use Node's test_prototype_stage.mjs separately for actual animated SVG bounds.
"""
import json,time,sys,zipfile
from pathlib import Path
import test_mvp_android as ui

OUT=next(parent for parent in Path(__file__).resolve().parents if (parent / 'settings.gradle.kts').is_file())/'build/prototype-ui'
OUT.mkdir(exist_ok=True)
ui.OUT=OUT
results=json.loads((OUT/'results.json').read_text()) if '--forms-only' in sys.argv and (OUT/'results.json').exists() else []

def record(name):
    results.append({'test':name,'status':'PASS'})
    print('PASS',name,flush=True)
    (OUT/'results.json').write_text(json.dumps(results,indent=2))

def main():
    apk=ui.adb('shell','pm','path','com.pawquest').splitlines()[0].removeprefix('package:')
    ui.adb('pull',apk,str(OUT/'installed-test.apk'))
    with zipfile.ZipFile(OUT/'installed-test.apk') as archive:
        assert any(b'http://127.0.0.1:55446' in archive.read(n) for n in archive.namelist() if n.endswith('.dex')), 'Refuse non-fixture installed APK'
    try:
        ui.adb('shell','wm','density','480')
        for name,width,height in ([] if '--forms-only' in sys.argv else [('9-16',1080,1920),('19.5-9',1080,2340),('20-9',1080,2400),('compact',960,1704)]):
            ui.adb('shell','wm','size',f'{width}x{height}');time.sleep(1)
            ui.click('Pet Home');ui.wait('PawQuest')
            assert ui.capture(name+'-home')==(width,height)
            for destination,expected in [('Quests','Your quests'),('Shop','Food'),('Inventory','Your collection of little comforts.'),('Profile','Your journey, one little quest at a time.')]:
                ui.click(destination);ui.wait(expected)
                visible=ui.nodes()
                for label in ['Pet Home','Quests','Shop','Inventory','Profile']:
                    node=next(n for n in visible if label in (n.get('text'),n.get('content-desc')))
                    x,y,r,b=ui.bounds(node)
                    assert 0<=x<r<=width and 0<=y<b<=height,(name,label,ui.bounds(node))
                ui.capture(name+'-'+destination.lower())
            record(name+': five destinations reachable, bottom navigation within viewport')
        ui.adb('shell','wm','size','960x1704')
        ui.adb('shell','settings','put','secure','show_ime_with_hard_keyboard','1')
        ui.adb('shell','am','force-stop','com.pawquest');ui.adb('shell','am','start','-n','com.pawquest/.MainActivity');time.sleep(2)
        ui.click('Quests');ui.click('Create Quest');ui.fill(0,'Cozy%sfocus')
        ui.adb('shell','input','keyevent','4')
        ui.click('Description (optional)',scroll=True);ui.adb('shell','input','text','A%slittle%stime%sfor%sme')
        ui.find('Save quest',scroll=True);ui.capture('compact-quest-keyboard')
        ui.adb('shell','input','keyevent','4')
        ui.find('Save quest',scroll=True);ui.capture('compact-quest-form')
        ui.click('Save quest',scroll=True);ui.wait('Cozy focus')
        ui.click('Start Quest',scroll=True);ui.wait('Focus Mode');ui.capture('compact-focus')
        ui.find('Cancel Quest',scroll=True)
        record('compact: quest create/details/start and scrollable Focus controls')
        ui.click('Cancel Quest',scroll=True);ui.click('Cancel Quest');ui.click('Back to quests',scroll=True)
        ui.click('Pet Home');ui.wait('PawQuest')
    finally:
        ui.adb('shell','wm','size','reset');ui.adb('shell','wm','density','reset')
        ui.adb('shell','settings','put','secure','show_ime_with_hard_keyboard','0')

if __name__=='__main__':main()
