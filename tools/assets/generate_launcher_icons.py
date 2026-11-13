"""Package imagegen mascot masters as Android icons (technical exports only)."""
from pathlib import Path
import subprocess
import sys
ROOT=Path(__file__).resolve().parents[2]
RES=ROOT/'androidApp/src/main/res'
CHECK='--check' in sys.argv
def write(path,text):
    if CHECK: assert path.read_text(encoding='utf-8')==text,f'Stale {path}'
    else:
        path.parent.mkdir(parents=True,exist_ok=True);path.write_text(text,encoding='utf-8')
export_script=(ROOT/'tools/assets/export_branding.ps1').read_text(encoding='utf-8')
subprocess.run(['powershell.exe','-NoProfile','-NonInteractive','-Command','& {\n'+export_script+'\n}'+(' -Check' if CHECK else '')],check=True,cwd=ROOT)
write(RES/'drawable/pawquest_brand_background.xml','''<shape xmlns:android="http://schemas.android.com/apk/res/android"><gradient android:angle="45" android:startColor="#63469F" android:endColor="#A48BDA" /></shape>
''')
write(RES/'values/companion_icons.xml','<resources><color name="pawquest_icon_background">#8062BE</color></resources>\n')
for mood in ['normal','hungry','bored']:
    write(RES/f'drawable/pawquest_brand_foreground_{mood}.xml',f'''<inset xmlns:android="http://schemas.android.com/apk/res/android" android:inset="22dp"><bitmap android:src="@drawable/pawquest_portrait_cat_{mood}" android:gravity="fill" android:filter="true" /></inset>
''')
    eyes={'normal':'M42,49 A3,4 0 1,0 48,49 A3,4 0 1,0 42,49 M60,49 A3,4 0 1,0 66,49 A3,4 0 1,0 60,49',
          'hungry':'M41,48 A4,5 0 1,0 49,48 A4,5 0 1,0 41,48 M59,48 A4,5 0 1,0 67,48 A4,5 0 1,0 59,48 M51,61 A3,4 0 1,0 57,61 A3,4 0 1,0 51,61',
          'bored':'M41,49 H49 V52 H41 Z M59,49 H67 V52 H59 Z M50,61 H58 V63 H50 Z'}[mood]
    write(RES/f'drawable/pawquest_brand_mono_{mood}.xml',f'''<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108"><path android:fillColor="#FFFFFFFF" android:fillType="evenOdd" android:pathData="M33,45 L32,29 Q33,26 37,29 L46,36 Q54,33 62,36 L71,29 Q75,26 76,30 L75,45 C84,70 67,79 54,79 C37,79 25,68 33,45 Z {eyes}" /></vector>
''')
    write(RES/f'mipmap-anydpi/pawquest_launcher_{mood}.xml',f'''<bitmap xmlns:android="http://schemas.android.com/apk/res/android" android:src="@drawable/pawquest_legacy_{mood}" android:gravity="fill" android:filter="true" />
''')
    for version in [26,33]:
        mono=f'    <monochrome android:drawable="@drawable/pawquest_brand_mono_{mood}" />\n' if version==33 else ''
        write(RES/f'mipmap-anydpi-v{version}/pawquest_launcher_{mood}.xml',f'''<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/pawquest_brand_background" />
    <foreground android:drawable="@drawable/pawquest_brand_foreground_{mood}" />
{mono}</adaptive-icon>
''')
for name in ['ic_launcher','ic_launcher_round']:
    for version in [26,33]:
        write(RES/f'mipmap-anydpi-v{version}/{name}.xml',(RES/f'mipmap-anydpi-v{version}/pawquest_launcher_normal.xml').read_text(encoding='utf-8'))
paths={'happy':'M12,20 C8,17 3,14 3,9 C3,4 9,3 12,7 C15,3 21,4 21,9 C21,14 16,17 12,20 Z',
 'hungry':'M3,11 H21 L18,19 H6 Z M4,8 H20 M8,4 V6 M12,3 V6 M16,4 V6',
 'thirsty':'M12,3 C10,7 5,11 5,15 A7,7 0 0,0 19,15 C19,11 14,7 12,3 Z',
 'tired':'M5,6 H11 L5,12 H11 M14,13 H20 L14,19 H20',
 'sleeping':'M18,3 A9,9 0 1,0 21,17 A9,9 0 0,1 18,3 Z'}
for mood,path in paths.items():
    write(RES/f'drawable/widget_mood_{mood}.xml',f'''<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="20dp" android:height="20dp" android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FFF9F5" android:pathData="M12,0 A12,12 0 1,0 12,24 A12,12 0 1,0 12,0" />
    <group android:pivotX="12" android:pivotY="12" android:scaleX="0.72" android:scaleY="0.72"><path android:pathData="{path}" android:fillColor="{'#9372C3' if mood in ['happy','sleeping'] else '#00000000'}" android:strokeColor="{'#447EA8' if mood=='thirsty' else '#71508F'}" android:strokeWidth="2" android:strokeLineCap="round" android:strokeLineJoin="round" /></group>
</vector>
''')
write(RES/'drawable/ic_notification_cat.xml','''<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24"><path android:fillColor="#FFFFFFFF" android:fillType="evenOdd" android:pathData="M4,10 L3,3 Q3,1 5,3 L9,6 Q12,4 15,6 L19,3 Q21,1 21,4 L20,10 C24,21 17,23 12,23 C5,23 0,20 4,10 Z M6,12 A2,2 0 1,0 10,12 A2,2 0 1,0 6,12 M14,12 A2,2 0 1,0 18,12 A2,2 0 1,0 14,12 M10,17 L12,19 L14,17 Z" /></vector>
''')
write(ROOT/'docs/media/previews/launcher-icons.html','''<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>PawQuest mascot identity</title><style>body{font:16px system-ui;background:#f5f0fc;color:#382947;padding:24px}main{display:flex;gap:24px;flex-wrap:wrap}figure{margin:0;text-align:center}img{display:block;width:180px;height:180px;border-radius:40px}</style><h1>PawQuest</h1><main><figure><img src="../../../design/branding/previews/normal.png" alt="Happy kitten">Normal</figure><figure><img src="../../../design/branding/previews/hungry.png" alt="Hungry kitten">Hungry</figure><figure><img src="../../../design/branding/previews/bored.png" alt="Bored kitten">Bored</figure></main></html>
''')
print('PASS branding resources'+(' check' if CHECK else ''))
