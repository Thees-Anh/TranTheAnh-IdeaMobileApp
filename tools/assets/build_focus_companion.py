"""Bundle only the supplied CatFocus reading artwork; never demo/CDN code."""
from pathlib import Path
import re,sys,hashlib
ROOT=next(parent for parent in Path(__file__).resolve().parents if (parent / 'settings.gradle.kts').is_file())
raw=(ROOT/'design/prototypes/focus/mochi-reading.html').read_text(encoding='utf-8')
assert raw.strip(),'Save design/prototypes/focus/mochi-reading.html before generating'
css=re.search(r'<style[^>]*id="pawquest-cat-animation-styles"[^>]*>([\s\S]*?)</style>',raw)[1]
svg=re.search(r'(<svg\s+id="pawquest-cat-svg"[\s\S]*?</svg>)',raw)[1]
svg=re.sub(r'class="[^"]*state-reading[^"]*"','class="state-reading"',svg,count=1)
# Keep the supplied bulb's anchor while CSS animates its local transform.
svg=re.sub(r'<g id="idea-bulb" transform="([^"]+)">([\s\S]*?)</g>',
    r'<g transform="\1"><g id="idea-bulb">\2</g></g>',svg,count=1)
output='''<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; img-src data:; connect-src 'none'">
<style>'''+css+'''
html,body{margin:0;width:100vw;height:100vh;overflow:hidden;background:#FFD9EC}
svg{position:absolute;inset:0;display:block;width:100vw;height:100vh;object-fit:contain}
@media(prefers-reduced-motion:reduce){*{animation:none!important;transition:none!important}}
</style></head><body>'''+svg+'''<script>
// Compose measures the WebView inside a scrollable Column. Some Android WebView
// versions report zero for CSS percentage/vh height there; use its measured size.
window.pawquestResize=(w,h)=>{
 const ratio=window.devicePixelRatio||1;
 const svg=document.getElementById('pawquest-cat-svg');
 svg.style.width=(w/ratio)+'px';svg.style.height=(h/ratio)+'px';
 document.documentElement.style.height=(h/ratio)+'px';document.body.style.height=(h/ratio)+'px';
};
window.pawquestRender=()=>document.getElementById('pawquest-cat-svg').setAttribute('class','state-reading');
</script></body></html>'''
assert 'cdn.tailwind' not in output and 'fonts.googleapis' not in output
for target in [ROOT/'shared/src/androidMain/assets/pet/focus_mochi.html',ROOT/'docs/media/previews/focus-mochi.html']:
    if '--check' in sys.argv:assert target.read_text(encoding='utf-8')==output,f'Stale {target}'
    else:target.parent.mkdir(parents=True,exist_ok=True);target.write_text(output,encoding='utf-8')
print('PASS Focus companion bundle; source SHA256',hashlib.sha256(raw.encode()).hexdigest())
