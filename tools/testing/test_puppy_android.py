"""Puppy smoke + portrait coverage on the disposable SQL fixture only."""
import json,time,sys,uuid,subprocess,zipfile
from pathlib import Path
import test_mvp_android as ui
import test_companion_android as fixture
import test_prototype_care_android as scene
OUT=ui.ROOT/'build/puppy-ui';OUT.mkdir(exist_ok=True);ui.OUT=OUT

def record(name):
 ui.record(name);(OUT/'results.json').write_text(json.dumps(ui.RESULTS,indent=2))

def restart():
 ui.adb('shell','am','force-stop','com.pawquest');ui.adb('shell','am','start','-n','com.pawquest/.MainActivity')
 ui.wait_any(["Puppy's little world","Quest complete!"])
 if 'Return to Pet Home' in ui.texts():ui.click('Return to Pet Home')
 scene.forward()

def tap_ready(label):
 end=time.monotonic()+45
 while time.monotonic()<end:
  root=ui.tree();parents={c:p for p in root.iter() for c in p}
  button=next((n for n in root.iter('node') if n.get('text')==label),None)
  if button is not None:
   while button.get('clickable')!='true' and button in parents:button=parents[button]
   if button.get('enabled')=='true':ui.tap(button);return
  time.sleep(.3)
 raise AssertionError('Button did not become ready: '+label)

def wait_state(expected):
 end=time.monotonic()+25
 while time.monotonic()<end:
  try:
   if scene.dom("document.querySelector('svg')?.classList.contains('state-"+expected+"')"):return
  except (subprocess.CalledProcessError,subprocess.TimeoutExpired,ValueError):scene.forward()
  time.sleep(.3)
 raise AssertionError('Puppy did not reach '+expected)

def main():
 if any('resume' in arg for arg in sys.argv):
  apk=ui.adb('shell','pm','path','com.pawquest').splitlines()[0].removeprefix('package:')
  ui.adb('pull',apk,str(OUT/'installed-fixture.apk'))
  with zipfile.ZipFile(OUT/'installed-fixture.apk') as archive:
   assert any(b'http://127.0.0.1:55447' in archive.read(n) for n in archive.namelist() if n.endswith('.dex')),'Refuse non-fixture APK'
 else:ui.require_fixture()
 if any(flag in sys.argv for flag in ['--care-resume','--switch-resume','--select-resume','--emotions-resume','--layout-resume']):
  actor=json.loads((OUT/'state.json').read_text())['actor']
  ui.RESULTS.extend(json.loads((OUT/'results.json').read_text()));scene.forward()
 else:
  if '--resume' in sys.argv:
   actor=json.loads((OUT/'state.json').read_text())['actor']
   if (OUT/'results.json').exists():ui.RESULTS.extend(json.loads((OUT/'results.json').read_text()))
  else:
   ui.adb('install','-r',str(ui.ROOT/'androidApp/build/outputs/apk/debug/androidApp-debug.apk'));ui.adb('reverse','tcp:55447','tcp:55447')
   ui.adb('shell','am','force-stop','com.pawquest');ui.adb('shell','am','start','-n','com.pawquest/.MainActivity')
   ui.wait('New here? Create an account');ui.click('New here? Create an account')
   email='puppy-'+uuid.uuid4().hex[:6]+'@example.invalid';ui.fill(0,email);ui.fill(1,'FixturePass123!');ui.fill(2,'FixturePass123!')
   ui.adb('shell','input','keyevent','4');ui.click('Create account');ui.wait("Mochi's little world")
   actor=fixture.request('/auth/v1/signup',{'email':email,'password':'FixturePass123!'})['user']['id']
   (OUT/'state.json').write_text(json.dumps({'actor':actor,'email':email}))
  if '--resume' not in sys.argv:ui.click('Inventory');ui.click('Pets')
  ui.find('Puppy',scroll=True)
  ui.find('Use Puppy',scroll=True)
  root=ui.tree();parents={c:p for p in root.iter() for c in p}
  button=next(n for n in root.iter('node') if n.get('text')=='Use Puppy')
  while button.get('clickable')!='true' and button in parents:button=parents[button]
  assert button.get('enabled')=='false'
  record('Puppy visible but locked at level 1')
  fixture.sql(f"update public.pets set xp=400,hunger=60,hydration=60,energy=30 where user_id='{actor}';select '{{}}'::jsonb;")
  ui.click('Refresh pets',scroll=True);tap_ready('Use Puppy');ui.wait('Got it');ui.click('Got it');ui.click('Pet Home')
  ui.wait("Puppy's little world");scene.forward();wait_state('idle');ui.capture('puppy-home')
  record('Level-5 Puppy selected from Inventory and actual supplied SVG loads')
 if '--layout-resume' not in sys.argv:
  if '--switch-resume' in sys.argv:
   ui.click('Inventory');ui.click('Pets');tap_ready('Use Mochi');ui.wait('Got it');ui.click('Got it')
   tap_ready('Use Puppy');ui.wait('Got it');ui.click('Got it');ui.click('Pet Home');ui.wait("Puppy's little world");scene.forward()
  if '--select-resume' in sys.argv:
   tap_ready('Use Puppy');ui.wait('Got it');ui.click('Got it');ui.click('Pet Home');ui.wait("Puppy's little world");scene.forward()
  if '--emotions-resume' not in sys.argv:
   for label,animation in [('Feed','eating'),('Drink','drinking'),('Play','laughing')]:
    tap_ready(label);wait_state(animation);record('Confirmed Puppy '+animation+' reaction');time.sleep(2.7);wait_state('idle')
   tap_ready('Sleep');wait_state('sleeping')
   scene.dom('document.querySelectorAll("*").forEach(e=>e.style.animationPlayState="paused"); true')
   pos=json.loads(scene.dom("JSON.stringify((()=>{const r=document.getElementById('dog-head-group').getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2,dpr:devicePixelRatio}})())"))
   stage=next(n for n in ui.nodes() if n.get('content-desc','').startswith('Puppy, sleeping.'));x,y,_,_=ui.bounds(stage)
   ui.adb('shell','input','tap',str(int(x+pos['x']*pos['dpr'])),str(int(y+pos['y']*pos['dpr'])))
   scene.dom('document.querySelectorAll("*").forEach(e=>e.style.animationPlayState="running"); true');wait_state('laughing')
   restart();wait_state('idle');record('Feed/Drink/Play/Sleep and actual dog head wake; selected Puppy persists after restart')
   fixture.sql(f"update public.pets set hunger=20 where user_id='{actor}';select '{{}}'::jsonb;")
   restart();wait_state('sad');ui.capture('puppy-sad');record('Low Hunger triggers actual sad expression')
   fixture.sql(f"update public.pets set hunger=100 where user_id='{actor}';insert into public.quests(id,user_id,title,duration_minutes,repeat_type,created_at) values('fd200000-0000-0000-0000-000000000001','{actor}','Daily Puppy walk',5,'daily',pawquest_private.e2e_now()-interval '2 days');select '{{}}'::jsonb;")
   restart();wait_state('sad');record('Missed daily quest triggers sad with full Hunger')
  fixture.sql(f"with t as materialized(select pawquest_private.e2e_now() stamp) insert into public.quest_sessions(user_id,quest_id,duration_minutes,started_at,ends_at,status) select '{actor}','fd200000-0000-0000-0000-000000000001',5,stamp-interval '5 minutes',stamp,'completed' from t;select '{{}}'::jsonb;")
  restart();wait_state('laughing');ui.capture('puppy-celebrate');time.sleep(4.2);wait_state('idle')
  record('Verified Daily completion plays laughing then returns idle; exact 4s boundary covered by shared test')
 fixture.sql(f"delete from public.quest_sessions where user_id='{actor}';update public.quests set archived=true where user_id='{actor}';select '{{}}'::jsonb;")
 restart();wait_state('idle')
 try:
  ui.adb('shell','wm','density','480')
  for name,w,h,font in [('9-16',1080,1920,1),('19.5-9',1080,2340,1),('20-9',1080,2400,1),('compact',960,1704,1),('large-font',960,1704,1.5)]:
   ui.adb('shell','settings','put','system','font_scale',str(font));ui.adb('shell','wm','size',f'{w}x{h}');time.sleep(.5)
   ui.wait_any(["Puppy's little world","Quest complete!"])
   if 'Return to Pet Home' in ui.texts():ui.click('Return to Pet Home')
   scene.forward();wait_state('idle')
   nodes=ui.nodes();stage=next(n for n in nodes if n.get('content-desc','').startswith('Puppy, idle.'))
   food=next(n for n in nodes if n.get('text','').startswith('Food:'))
   dpr=scene.dom('devicePixelRatio');_,sy,_,sb=ui.bounds(stage)
   nativeHeight=(sb-sy)/dpr;hudTop=(ui.bounds(food)[1]-sy)/dpr-4
   subprocess.check_call(['node','tools/testing/test_puppy_stage.mjs',str(nativeHeight),str(hudTop)])
   ui.capture('puppy-'+name);record('Puppy portrait framing '+name)
 finally:
  ui.adb('shell','settings','put','system','font_scale','1.0');ui.adb('shell','wm','size','reset');ui.adb('shell','wm','density','reset')
 (OUT/'results.json').write_text(json.dumps(ui.RESULTS,indent=2))
 ui.click('Profile');ui.click('Sign Out',scroll=True);ui.click('Confirm Sign Out');ui.wait('Welcome back')

if __name__=='__main__':main()
