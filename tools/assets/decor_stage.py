"""Layer the supplied 600x440 decoration artwork onto each original pet rig."""
from pathlib import Path
import json,re,copy,xml.etree.ElementTree as E
ROOT=next(parent for parent in Path(__file__).resolve().parents if (parent / 'settings.gradle.kts').is_file());NS='http://www.w3.org/2000/svg'
E.register_namespace('',NS)
ITEMS=json.loads((ROOT/'design/assets/decor/catalog.json').read_text(encoding='utf-8'))
def fragments(file):return E.fromstring(f'<svg xmlns="{NS}">'+(ROOT/file).read_text(encoding='utf-8')+'</svg>')
ART={e.get('id'):e for file in ['design/assets/decor/accessories.svg','design/assets/decor/furniture.svg'] for e in fragments(file)}
PROFILES={
 'prototype_cat':('cat',204,126,183,172,224,168,206,222),
 'puppy':('dog',202,105,180,147,235,145,207,199),
 'capybara':('capy',197,94,184,134,218,133,208,190),
 'rabbit':('bunny',198,104,177,155,235,153,203,196),
}
def augment(html,key):
    prefix,hx,hy,lx,ly,rx,ry,nx,ny=PROFILES[key]
    match=re.search(r'<svg[\s\S]*?</svg>',html);root=E.fromstring(match[0])
    find=lambda id:root.find(f".//*[@id='{id}']")
    head=find(prefix+'-head-group');body=find(prefix+'-body-group');room=find('room-art')
    assert head is not None and body is not None and room is not None,key
    back=E.Element('{'+NS+'}g',{'id':'room-furniture-back-layer','transform':'scale(0.666666667)'})
    front=E.Element('{'+NS+'}g',{'id':'room-furniture-front-layer','transform':'scale(0.666666667)'})
    # Keep the backdrop first, wall/floor props behind the pet and low side props in front.
    parents={child:parent for parent in root.iter() for child in parent}
    pet=find(prefix+'-whole');ancestor=pet
    while parents.get(ancestor) is not room:ancestor=parents[ancestor]
    room.insert(list(room).index(ancestor),back);room.append(front)
    for i in ITEMS:
        art=copy.deepcopy(ART[i['id']]);art.set('class','pq-decor hidden')
        wrapper=E.Element('{'+NS+'}g')
        if i['category']=='FURNITURE':
            if i['id']=='furn-tea-table':art.set('transform','translate(226.4,114.6) scale(.7)')
            if i['id']=='furn-gramophone':art.set('transform','translate(-29.6,51) scale(.85)')
            (front if i['zone'].startswith('front-') else back).append(art)
            continue
        if i['slot']=='HEAD':
            scale=.65
            reference=(300,194) if i['id']=='acc-headphones' else (300,140)
            target=((lx+rx)/2,(ly+ry)/2) if i['id']=='acc-headphones' else (hx,hy)
            parent=head
        elif i['slot']=='FACE':
            scale=(rx-lx)/54;reference=(301,192);target=((lx+rx)/2,(ly+ry)/2);parent=head
        elif i['slot']=='NECK':
            scale=.65;reference=(300,250);target=(nx,ny);parent=body
        else:
            scale=.65;reference=(300,260);target=(200,230);parent=body
            if i['id']=='acc-wings':
                scale={'prototype_cat':.65,'puppy':.75,'capybara':.9,'rabbit':.8}[key]
        if key=='capybara' and i['id']=='acc-monocle':
            # Side-facing capybara: keep the lens at the visible eye and the moustache at its muzzle.
            children=list(art)
            for child in children:art.remove(child)
            for nodes,source,target_point in [(children[:3],(328,195),(184,134)),(children[3:],(302,213),(252,166))]:
                part=E.SubElement(art,'{'+NS+'}g',{'transform':f'translate({target_point[0]-.8*source[0]},{target_point[1]-.8*source[1]}) scale(.8)'})
                part.extend(nodes)
            scale=1;reference=(0,0);target=(0,0)
        wrapper.set('transform',f'translate({target[0]-scale*reference[0]},{target[1]-scale*reference[1]}) scale({scale})')
        wrapper.append(art)
        if i['id']=='acc-wings':parent.insert(0,wrapper)
        else:parent.append(wrapper)
    html=html[:match.start()]+E.tostring(root,encoding='unicode')+html[match.end():]
    css=(ROOT/'design/assets/decor/animations.css').read_text(encoding='utf-8')
    html=html.replace('</style>',css+'\n.pq-decor{display:block}.pq-decor.hidden{display:none!important}\n@media(prefers-reduced-motion:reduce){*{animation:none!important;transition:none!important}}\n</style>',1)
    data=json.dumps([{k:i[k] for k in ['id','slot','category']} for i in ITEMS])
    script='''(()=>{
const items=ITEMS,stage=document.querySelector('svg');
const legacyEquipment={'item.accessory.hat':'HEAD','item.accessory.glasses':'FACE','item.accessory.collar':'NECK'};
const legacyRoom={'item.furniture.bed':'BED','item.furniture.lamp':'LAMP','item.furniture.carpet':'CARPET'};
const original=window.pawquestRender;
window.pawquestResize=(w,h,top,bottom)=>{
 if(w>0&&h>0){stage.dataset.ratio=h/w;stage.dataset.safeTop=400*(top||0)/w;stage.dataset.safeBottom=400*(bottom||0)/w;}
 const height=Math.max(330,400*Number(stage.dataset.ratio||.825));
 const t=Number(stage.dataset.safeTop||50),b=Number(stage.dataset.safeBottom||110),available=Math.max(1,height-t-b-16);
 const scale=Math.min(1,available/350),y=t+8+Math.max(0,available-350*scale)/2;
 stage.setAttribute('viewBox','0 0 400 '+height);stage.style.aspectRatio='400 / '+height;stage.style.height='auto';
 document.getElementById('room-art').setAttribute('transform','translate('+(200*(1-scale))+','+y+') scale('+scale+')');
 document.getElementById('room-wall-extension').setAttribute('height',height);
 const floor=document.getElementById('room-floor-extension');floor.setAttribute('y',176*scale+y);floor.setAttribute('height',Math.max(0,height-176*scale-y));floor.setAttribute('fill','#E8C4A3');
};
window.pawquestRender=(value)=>{
 const equipment=value.equipment||[],room=value.room||[];
 original({...value,equipment:equipment.map(v=>legacyEquipment[v]||v).filter(v=>['HEAD','FACE','NECK'].includes(v)),room:room.map(v=>legacyRoom[v]||v).filter(v=>['BED','LAMP','CARPET'].includes(v))});
 for(const i of items)document.getElementById(i.id).classList.toggle('hidden',!(i.category==='ACCESSORY'?equipment:room).includes(i.id));
 const orange=document.getElementById('head-orange');if(orange)orange.classList.toggle('hidden',equipment.some(v=>v==='HEAD'||legacyEquipment[v]==='HEAD'||items.some(i=>i.id===v&&i.slot==='HEAD')));
};
window.pawquestRender({state:'IDLE'});
})();'''.replace('ITEMS',data)
    return html.replace('</body>','<script>'+script+'</script></body>')
