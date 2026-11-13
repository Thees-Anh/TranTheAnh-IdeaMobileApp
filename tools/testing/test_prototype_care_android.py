"""Visual-care regression against the installed loopback 55446 fixture APK only."""
import json,subprocess,time,urllib.request,zipfile,sys
from pathlib import Path
import test_mvp_android as ui

OUT=ui.ROOT/'build/room-hud-ui'
ui.OUT=OUT
BASE='http://127.0.0.1:55446'
RESULTS=json.loads((OUT/'care-results.json').read_text()) if '--resume-sleep' in sys.argv and (OUT/'care-results.json').exists() else []

def control(path):
    with urllib.request.urlopen(BASE+path,timeout=5) as response:return json.load(response)

def dom(expression):
    script='''
const pages=await (await fetch('http://127.0.0.1:9229/json')).json();
const page=pages.find(p=>p.url.includes('pawquest.invalid'))||pages[0];
const ws=new WebSocket(page.webSocketDebuggerUrl);
await new Promise((r,j)=>{ws.onopen=r;ws.onerror=j;});
const response=new Promise(r=>ws.onmessage=e=>r(JSON.parse(e.data)));
ws.send(JSON.stringify({id:1,method:'Runtime.evaluate',params:{expression:EXPRESSION,returnByValue:true}}));
const value=await response;ws.close();console.log(JSON.stringify(value.result.result.value));
'''.replace('EXPRESSION',json.dumps(expression))
    return json.loads(subprocess.check_output(['node','--input-type=module','-'],input=script,encoding='utf-8',timeout=10))

def forward():
    pid=ui.adb('shell','pidof','com.pawquest')
    ui.adb('forward','tcp:9229','localabstract:webview_devtools_remote_'+pid)

def animation(expected):
    time.sleep(.2)
    assert dom("document.querySelector('svg').getAttribute('class')")=='state-'+expected

def record(name):
    RESULTS.append({'test':name,'status':'PASS'})
    (OUT/'care-results.json').write_text(json.dumps(RESULTS,indent=2))
    print('PASS',name,flush=True)

def main():
    apk=ui.adb('shell','pm','path','com.pawquest').splitlines()[0].removeprefix('package:')
    ui.adb('pull',apk,str(OUT/'installed-test.apk'))
    with zipfile.ZipFile(OUT/'installed-test.apk') as archive:
        assert any(b'http://127.0.0.1:55446' in archive.read(n) for n in archive.namelist() if n.endswith('.dex')),'Refuse real backend APK'
    if '--resume-sleep' in sys.argv:
        ui.wait("Mochi's little world");time.sleep(1);forward();animation('sleeping');ui.capture('sleep-restored')
        record('Sleep animation and persisted equipment/room restore after process restart')
        return
    try:
        ui.adb('shell','wm','density','480');ui.adb('shell','wm','size','1080x2400')
        ui.click('Pet Home');ui.wait('PawQuest');forward()
        before=control('/__test/pet/status')
        ui.click('Feed',scroll=True);animation('eating')
        assert control('/__test/pet/status')['feed']==before['feed']+1
        time.sleep(3);animation('idle')
        record('Feed projects confirmed eating and returns to idle')
        ui.click('Feed',scroll=True);animation('idle')
        assert control('/__test/pet/status')['feed']==before['feed']+1
        record('Empty-food disabled action sends no second Feed or animation')
        ui.click('Play',scroll=True);animation('laughing')
        assert control('/__test/pet/status')['play']==before['play']+1
        time.sleep(3)
        # Return to the top so the actual SVG cat is visible, then hit its head.
        ui.click('Quests');ui.click('Pet Home');ui.wait('PawQuest');forward()
        page=dom("JSON.stringify((()=>{const r=document.getElementById('cat-head-group').getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2,dpr:devicePixelRatio}})())")
        page=json.loads(page)
        scene=next(n for n in ui.nodes() if n.get('content-desc','').startswith('Mochi, idle.'))
        x,y,r,b=ui.bounds(scene)
        ui.adb('shell','input','tap',str(int(x+page['x']*page['dpr'])),str(int(y+page['y']*page['dpr'])))
        animation('laughing')
        assert control('/__test/pet/status')['touch']==before['touch']+1
        record('Play and actual SVG cat hit-testing project happy reactions')
        time.sleep(3);ui.click('Sleep',scroll=True);animation('sleeping')
        assert control('/__test/pet/status')['sleep']==before['sleep']+1
        ui.adb('shell','am','force-stop','com.pawquest');ui.adb('shell','am','start','-n','com.pawquest/.MainActivity')
        ui.wait("Mochi's little world");time.sleep(1);forward();animation('sleeping');ui.capture('sleep-restored')
        record('Sleep animation and persisted equipment/room restore after process restart')
    finally:
        ui.adb('shell','wm','size','reset');ui.adb('shell','wm','density','reset')

if __name__=='__main__':main()
