"""Extract the user-approved SVG/CSS only; never copy prototype gameplay or CDN scripts."""
from pathlib import Path
import re,hashlib

ROOT=next(parent for parent in Path(__file__).resolve().parents if (parent / 'settings.gradle.kts').is_file())
def build():
    raw=(ROOT/'design/prototypes/pets/mochi.html').read_text(encoding='utf-8')
    css=re.search(r'<style>(.*?)</style>',raw,re.S)[1]
    css=css[:css.index('/* Hiệu ứng nút bấm')]
    svg=re.search(r'<svg id="cat-stage-svg".*?</svg>',raw,re.S)[0]
    # Preserve source art. Extra equipment follows animated head/body groups.
    svg=svg.replace('</g> <!-- Kết thúc #cat-head-group -->','''
      <g id="pq-head" class="hidden" transform="translate(180,126) rotate(-5)">
        <path d="M3 10 Q4 -10 24 -10 Q43 -10 45 10Z" fill="#c4b5fd" stroke="#592512" stroke-width="3"/>
        <rect x="-2" y="8" width="52" height="8" rx="4" fill="#e9d5ff" stroke="#592512" stroke-width="3"/>
      </g>
      <g id="pq-face" class="hidden" fill="none" stroke="#592512" stroke-width="2.5">
        <ellipse cx="183" cy="172" rx="11" ry="10"/><ellipse cx="224" cy="168" rx="11" ry="10"/>
        <path d="M194 170Q204 163 213 168M172 170L160 167M235 166L248 161"/>
      </g>
      </g> <!-- Kết thúc #cat-head-group -->''')
    point=svg.index('<!-- Hai chân trước')
    svg=svg[:point]+'''<g id="pq-neck" class="hidden">
      <path d="M177 222Q206 234 235 220L235 227Q206 241 177 229Z" fill="#f9a8d4" stroke="#592512" stroke-width="2"/>
      <circle cx="208" cy="235" r="4" fill="#facc15" stroke="#592512" stroke-width="1.6"/>
    </g>'''+svg[point:]
    point=svg.index('<!-- Bóng đổ màu tím')
    svg=svg[:point]+'''<g id="pq-bed" class="hidden" transform="translate(25,232)">
      <rect x="0" y="-15" width="83" height="36" rx="10" fill="#bfe4df" stroke="#592512" stroke-width="3"/>
      <rect x="3" y="-10" width="77" height="25" rx="10" fill="#fffbeb" stroke="#592512" stroke-width="2"/>
      <rect x="11" y="-7" width="24" height="12" rx="6" fill="#fce7f3" stroke="#592512" stroke-width="2"/>
    </g>
    <g id="pq-lamp" class="hidden" stroke="#592512" stroke-width="2.5" stroke-linejoin="round">
      <ellipse cx="348" cy="265" rx="17" ry="5" fill="#fef3c7"/>
      <path d="M348 261V196" fill="none"/><path d="M330 167H366L375 200H321Z" fill="#fde68a"/>
      <path d="M348 163V167"/>
    </g>
    <g id="pq-carpet" class="hidden">
      <ellipse cx="208" cy="269" rx="84" ry="20" fill="#e9d5ff" stroke="#592512" stroke-width="2.5"/>
      <ellipse cx="208" cy="269" rx="74" ry="14" fill="url(#rugPlaid)" stroke="#ffffff" stroke-width="2" stroke-dasharray="4 4"/>
    </g>'''+svg[point:]
    # Extend the room, while keeping original art and animation transforms uniform.
    svg=svg.replace('</defs>', '</defs><rect id="room-wall-extension" width="400" height="330" fill="url(#wallPattern)"/><rect id="room-floor-extension" width="400" y="330" height="0" fill="#efb6df"/><g id="room-art">',1)
    svg=svg.replace('</svg>','</g></svg>')
    script='''
    (()=>{
      const stage=document.getElementById('cat-stage-svg');let current='';
      // Native viewport ratio drives the viewBox. Only uniform art scaling is used.
      window.pawquestResize=(w,h)=>{
        if(w>0 && h>0){stage.dataset.ratio=String(h/w);}
        const height=Math.max(330,400*Number(stage.dataset.ratio||0.825));
        const scale=Math.min(1,(height-110)/330);
        const offset=Math.max(0,(height-110-330*scale)*0.55);
        stage.setAttribute('viewBox','0 0 400 '+height);
        stage.style.aspectRatio='400 / '+height;stage.style.height='auto';
        document.getElementById('room-art').setAttribute('transform','translate('+(200*(1-scale))+','+offset+') scale('+scale+')');
        document.getElementById('room-wall-extension').setAttribute('height',height);
        const floor=document.getElementById('room-floor-extension');
        floor.setAttribute('y',205*scale+offset);floor.setAttribute('height',Math.max(0,height-205*scale-offset));
      };
      const cat=document.getElementById('cat-whole');
      const zoom=document.createElementNS('http://www.w3.org/2000/svg','g');
      zoom.setAttribute('transform','translate(-24,-25.2) scale(1.12)');
      cat.parentNode.insertBefore(zoom,cat);zoom.appendChild(cat);
      window.pawquestResize();
      const toggle=(id,on)=>document.getElementById(id)?.classList.toggle('hidden',!on);
      window.pawquestRender=(value)=>{
        window.pawquestResize();
        const names={DRINKING:'drinking',IDLE:'idle',HAPPY:'laughing',EATING:'eating',SLEEPING:'sleeping',PLAYING:'laughing'};
        const next=names[value.state]||'idle';
        if(current!==next){stage.setAttribute('class','state-'+next);current=next;}
        for(const [slot,id] of [['HEAD','pq-head'],['FACE','pq-face'],['NECK','pq-neck']])toggle(id,(value.equipment||[]).includes(slot));
        for(const [slot,id] of [['BED','pq-bed'],['LAMP','pq-lamp'],['CARPET','pq-carpet']])toggle(id,(value.room||[]).includes(slot));
      };
      document.getElementById('cat-whole').addEventListener('click',()=>{location.href='pawquest-pet://touch';});
      window.pawquestRender({state:'IDLE'});
    })();'''
    output='''<!doctype html><html><head><meta charset="utf-8">
    <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
    <meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src data:; connect-src 'none'">
    <style>'''+css+'''
    html,body{margin:0;width:100%;height:auto;overflow:hidden;background:#ffd6e7;touch-action:manipulation}
    svg{display:block;width:100%;height:auto;aspect-ratio:400/330;object-fit:contain}.hidden{display:none!important}
    @media(prefers-reduced-motion:reduce){*{animation:none!important;transition:none!important}}
    </style></head><body>'''+svg+'<script>'+script+'</script></body></html>'
    assert 'cdn.tailwindcss' not in output and 'fetch(' not in output and 'coins' not in script
    from decor_stage import augment
    output=augment(output,'prototype_cat')
    target=ROOT/'shared/src/androidMain/assets/pet/prototype_stage.html';target.parent.mkdir(parents=True,exist_ok=True)
    import sys
    if '--check' in sys.argv:
        assert target.read_text(encoding='utf-8') == output, 'Generated pet asset is stale; run tools/assets/build_prototype_pet.py'
    else:
        target.write_text(output,encoding='utf-8')
    print('Built offline SVG stage:',len(output.encode()),'bytes; source SHA256',hashlib.sha256(raw.encode()).hexdigest())
if __name__=='__main__':build()
