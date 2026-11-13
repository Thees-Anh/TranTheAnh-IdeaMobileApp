"""Bundle the user-provided Puppy SVG/CSS with the existing offline room protocol."""
from pathlib import Path
import re,sys,hashlib,xml.etree.ElementTree as E
ROOT=next(parent for parent in Path(__file__).resolve().parents if (parent / 'settings.gradle.kts').is_file())
css=(ROOT/'design/assets/pets/puppy/puppy.css').read_text(encoding='utf-8')
raw=(ROOT/'design/assets/pets/puppy/puppy.svg').read_text(encoding='utf-8')
assert 'id="pawquest-dog-svg"' in raw
svg=raw.replace('class="w-full h-full block state-sleeping"','class="state-idle"')
# Preserve translation anchors while CSS animates the inner heart's transform.
svg=re.sub(r'<g class="(heart-[123])" transform="([^"]+)">([\s\S]*?)</g>',
           lambda m:f'<g transform="{m[2]}"><g class="{m[1]}">{m[3]}</g></g>',svg)
# Existing owned furniture stays behind the new pet, under the same uniform room transform.
cat=(ROOT/'shared/src/androidMain/assets/pet/prototype_stage.html').read_text(encoding='utf-8')
tree=E.fromstring(re.search(r'(<svg[\s\S]*?</svg>)',cat)[1])
E.register_namespace('','http://www.w3.org/2000/svg')
pieces=''.join(E.tostring(tree.find(f".//*[@id='{id}']"),encoding='unicode') for id in ['pq-bed','pq-lamp','pq-carpet'])
pigment=tree.find(".//*[@id='rugPlaid']")
rug=E.tostring(pigment,encoding='unicode') if pigment is not None else ''
pattern=E.tostring(tree.find(".//*[@id='rugPlaid']"),encoding='unicode')
svg=svg.replace('</defs>',pattern+'''</defs><rect id="room-wall-extension" width="400" height="330" fill="#FFD9EC"/>
<rect id="room-floor-extension" width="400" y="176" height="154" fill="#efb6df"/><g id="room-art">''',1)
svg=svg.replace('<g id="dog-whole">',pieces+'<g id="dog-whole">',1).replace('</svg>','</g></svg>')
from pet_equipment_svg import attach
svg=attach(svg,'puppy')
script='''(()=>{
const stage=document.getElementById('pawquest-dog-svg');let current='';
const states=new Set(['idle','eating','drinking','sleeping','laughing','sad']);
window.pawquestResize=(w,h,top,bottom)=>{
 if(w>0&&h>0){
  stage.dataset.ratio=String(h/w);
  stage.dataset.safeTop=String(top>0?400*top/w:50);
  stage.dataset.safeBottom=String(bottom>0?400*bottom/w:110);
 }
 const height=Math.max(240,400*Number(stage.dataset.ratio||.825));
 const safeTop=Number(stage.dataset.safeTop||50),safeBottom=Number(stage.dataset.safeBottom||110);
 const available=Math.max(1,height-safeTop-safeBottom-8);
 const scale=Math.min(1,available/290);
 const offset=safeTop+4-20*scale+Math.max(0,available-290*scale)/2;
 stage.setAttribute('viewBox','0 0 400 '+height);stage.style.aspectRatio='400 / '+height;
 document.getElementById('room-art').setAttribute('transform','translate('+(200*(1-scale))+','+offset+') scale('+scale+')');
 document.getElementById('room-wall-extension').setAttribute('height',height);
 const floor=document.getElementById('room-floor-extension');
 floor.setAttribute('y',176*scale+offset);floor.setAttribute('height',Math.max(0,height-176*scale-offset));
};
window.PawQuestDog=({state})=>{
 const next=states.has(state)?state:'idle';
 if(next!==current){stage.setAttribute('class','state-'+next);current=next;}
};
window.pawquestRender=(value)=>{
 window.pawquestResize();window.PawQuestDog({state:value.state});
 for(const [slot,id] of [['HEAD','pq-head'],['FACE','pq-face'],['NECK','pq-neck']])
  document.getElementById(id).classList.toggle('hidden',!(value.equipment||[]).includes(slot));
 for(const [slot,id] of [['BED','pq-bed'],['LAMP','pq-lamp'],['CARPET','pq-carpet']])
  document.getElementById(id).classList.toggle('hidden',!(value.room||[]).includes(slot));
};
document.getElementById('dog-whole').addEventListener('click',()=>{location.href='pawquest-pet://touch';});
window.pawquestRender({state:'idle'});
})();'''
output='''<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src data:; connect-src 'none'">
<style>'''+css+'''
html,body{margin:0;width:100%;height:auto;overflow:hidden;background:#FFD9EC;touch-action:manipulation}
svg{display:block;width:100%;height:auto;aspect-ratio:400/330;object-fit:contain}.hidden{display:none!important}
@media(prefers-reduced-motion:reduce){*{animation:none!important;transition:none!important}}
</style></head><body>'''+svg+'<script>'+script+'</script></body></html>'
assert 'fetch(' not in output and '<script src' not in output
from decor_stage import augment
output=augment(output,'puppy')
for target in [ROOT/'shared/src/androidMain/assets/pet/puppy_stage.html',ROOT/'docs/media/previews/puppy.html']:
    if '--check' in sys.argv:assert target.read_text(encoding='utf-8')==output,f'Stale {target}'
    else:target.parent.mkdir(parents=True,exist_ok=True);target.write_text(output,encoding='utf-8')
print('PASS Puppy bundle; source SVG SHA256',hashlib.sha256(raw.encode()).hexdigest())
