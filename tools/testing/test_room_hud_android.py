"""Expanded-room regression on the installed loopback 55446 visual fixture APK."""
import json,subprocess,time,zipfile,sys
from pathlib import Path
import test_mvp_android as ui

OUT=ui.ROOT/'build/room-hud-ui'
OUT.mkdir(exist_ok=True)
ui.OUT=OUT

def main():
    apk=ui.adb('shell','pm','path','com.pawquest').splitlines()[0].removeprefix('package:')
    ui.adb('pull',apk,str(OUT/'installed-fixture.apk'))
    with zipfile.ZipFile(OUT/'installed-fixture.apk') as archive:
        assert any(any(url in archive.read(n) for url in [b'http://127.0.0.1:55446',b'http://127.0.0.1:55447']) for n in archive.namelist() if n.endswith('.dex')),'Refuse non-fixture APK'
    original_font=ui.adb('shell','settings','get','system','font_scale')
    results=json.loads((OUT/'layout-results.json').read_text()) if '--compact-only' in sys.argv and (OUT/'layout-results.json').exists() else []
    try:
        ui.adb('shell','wm','density','480')
        for name,width,height,font in ([('compact',960,1704,1),('large-font',960,1704,1.5)] if '--compact-only' in sys.argv else [('9-16',1080,1920,1),('19.5-9',1080,2340,1),('20-9',1080,2400,1),('compact',960,1704,1),('large-font',960,1704,1.5)]):
            ui.adb('shell','settings','put','system','font_scale',str(font))
            ui.adb('shell','wm','size',f'{width}x{height}')
            time.sleep(.5)
            ui.click('Pet Home');ui.wait("Mochi's little world")
            root=ui.tree();nodes=list(root.iter('node'))
            parents={child:parent for parent in root.iter() for child in parent}
            scene=next(n for n in nodes if n.get('content-desc','').startswith('Mochi, idle.'))
            sx,sy,sr,sb=ui.bounds(scene)
            nav=next(n for n in nodes if n.get('content-desc')=='Pet Home')
            nx,ny,nr,nb=ui.bounds(nav)
            if font==1:
                assert 0<=ny-sb<=24*3,(name,'room should end close to bottom navigation',sb,ny)
                header=next(n for n in nodes if n.get('text')=='PawQuest')
                assert (sb-sy)/(ny-ui.bounds(header)[1])>.60,(name,'interaction room should dominate the viewport')
            for label in ['Feed','Play','Sleep','Drink']:
                button=next(n for n in nodes if n.get('text')==label)
                while button.get('clickable')!='true' and button in parents:button=parents[button]
                x,y,r,b=ui.bounds(button)
                assert r-x>=48*3 and b-y>=48*3,(name,label,'minimum touch target',ui.bounds(button))
                assert sx<=x<r<=sr and sy<y<b<=sb,(name,label,'must be inside room')
                assert b<=ny,(name,label,'must be above navigation')
            for label in ['Pet Home','Quests','Shop','Inventory','Profile']:
                assert any(n.get('content-desc')==label for n in nodes),(name,label)
            ui.capture(name)
            # Exercise fresh creation of the WebView as well as native navigation.
            ui.click('Quests');ui.wait('Your quests');ui.click('Pet Home');ui.wait("Mochi's little world")
            pid=ui.adb('shell','pidof','com.pawquest')
            ui.adb('forward','tcp:9229','localabstract:webview_devtools_remote_'+pid)
            time.sleep(.4)
            subprocess.check_call(['node','tools/testing/test_prototype_stage.mjs'])
            results.append({'viewport':name,'status':'PASS'})
            (OUT/'layout-results.json').write_text(json.dumps(results,indent=2))
            print('PASS',name,': in-room actions, touch targets, navigation, animation clearance',flush=True)
    finally:
        ui.adb('shell','settings','put','system','font_scale',original_font if original_font!='null' else '1.0')
        ui.adb('shell','wm','size','reset');ui.adb('shell','wm','density','reset')

if __name__=='__main__':main()
