"""Extract supplied HTML artwork; share the offline room/equipment protocol."""
from pathlib import Path
import re,sys,runpy,xml.etree.ElementTree as E
from pet_equipment_svg import attach,PROFILES
ROOT=next(parent for parent in Path(__file__).resolve().parents if (parent / 'settings.gradle.kts').is_file())
puppy=runpy.run_path(str(ROOT/'tools/assets/build_puppy.py'))
for key,file,css_id,svg_id in [('capybara','design/prototypes/pets/capybara.html','capy','capybara'),('rabbit','design/prototypes/pets/rabbit.html','bunny','bunny')]:
    raw=(ROOT/file).read_text(encoding='utf-8')
    css=re.search(r'<style id="pawquest-'+css_id+r'-animation-styles">([\s\S]*?)</style>',raw)[1]
    svg=re.search(r'<svg id="pawquest-'+svg_id+r'-svg"[\s\S]*?</svg>',raw)[0]
    svg=re.sub(r'class="w-full h-full block state-[^"]+"','class="state-idle"',svg,count=1)
    svg=re.sub(r'<g class="(heart-[123])" transform="([^"]+)">([\s\S]*?)</g>',r'<g transform="\2"><g class="\1">\3</g></g>',svg)
    prefix=PROFILES[key][0]
    svg=svg.replace('</defs>',puppy['rug']+'</defs>',1)
    svg=svg.replace('</defs>', '</defs><rect id="room-wall-extension" width="400" height="330" fill="#FFD9EC"/><rect id="room-floor-extension" width="400" y="330" height="0" fill="#efb6df"/><g id="room-art">',1)
    svg=svg.replace(f'<g id="{prefix}-whole">',puppy['pieces']+f'<g id="{prefix}-whole">',1).replace('</svg>','</g></svg>')
    svg=attach(svg,key)
    script=puppy['script'].replace('pawquest-dog-svg','pawquest-'+svg_id+'-svg').replace('dog-whole',prefix+'-whole')
    script=script.replace('window.pawquestResize();window.PawQuestDog({state:value.state});',"window.pawquestResize();window.PawQuestDog({state:value.state});document.getElementById('head-orange')?.classList.toggle('hidden',(value.equipment||[]).includes('HEAD'));")
    output=puppy['output'][:puppy['output'].index('<style>')]+'<style>'+css+'''
html,body{margin:0;width:100%;height:auto;overflow:hidden;background:#FFD9EC}
svg{display:block;width:100%;height:auto;aspect-ratio:400/330}.hidden{display:none!important}
@media(prefers-reduced-motion:reduce){*{animation:none!important;transition:none!important}}
</style></head><body>'''+svg+'<script>'+script+'</script></body></html>'
    from decor_stage import augment
    output=augment(output,key)
    for p in [ROOT/f'shared/src/androidMain/assets/pet/{key}_stage.html',ROOT/f'docs/media/previews/{key}.html']:
        if '--check' in sys.argv:assert p.read_text(encoding='utf-8')==output
        else:p.parent.mkdir(parents=True,exist_ok=True);p.write_text(output,encoding='utf-8')
    print('PASS',key,'offline bundle')
