"""Extract the supplied Bistro art; package an offline, presentation-only renderer."""
from pathlib import Path
import json,re,subprocess,shutil,sys
ROOT=Path(__file__).resolve().parents[2]
SOURCE=ROOT/'shared/src/androidMain/assets/food/12food.html'
OUT=SOURCE.parent
source=SOURCE.read_text(encoding='utf-8')
catalog=re.search(r'const PAWQUEST_FOOD_MENU\s*=\s*\[(.*?)\];',source,re.S).group(1)
fields=['id','name','tier','type','description']
numbers=['order','price','hungerRestore','happinessBonus','expBonus']
menu=[]
for block in re.findall(r'\{(.*?)\}',catalog,re.S):
    item={key:re.search(r'\b'+key+r":\s*'([^']*)'",block).group(1) for key in fields}
    item.update({key:int(re.search(r'\b'+key+r':\s*(\d+)',block).group(1)) for key in numbers})
    menu.append(item)
expected=[(10,15,5,5),(15,20,5,8),(20,15,12,10),(35,30,15,15),(50,38,18,22),(65,25,30,28),
 (90,45,35,40),(120,60,40,55),(160,70,50,75),(200,45,65,90),(300,90,80,140),(500,100,100,250)]
assert len(menu)==12 and [(m['price'],m['hungerRestore'],m['happinessBonus'],m['expBonus']) for m in menu]==expected
assert [m['order'] for m in menu]==list(range(1,13))
assert [m['order'] for m in menu if m['type']=='drink']==[3,6,10]
defs=re.search(r'<svg id="pawquest-food-svg-defs".*?</svg>',source,re.S).group(0).replace('class="hidden"','width="0" height="0" style="position:absolute;overflow:hidden"')
stage=re.search(r'<svg id="bistro-stage-svg".*?</svg>',source,re.S).group(0)
css=re.search(r'<style id="pawquest-bistro-styles">(.*?)</style>',source,re.S).group(1)
templates=source[source.index('const PET_TEMPLATES ='):source.index('// 3.',source.index('const PET_TEMPLATES ='))]
assert all(f'id="{m["id"]}"' in defs for m in menu)
assert not re.search(r'<script|on\w+=|(?:href|src)="https?',defs+stage,re.I)
controller=(ROOT/'design/prototypes/food/bistro_controller.js').read_text(encoding='utf-8')
runtime='''<!doctype html><html lang="vi"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; font-src 'self'; img-src 'self'; connect-src 'none'; frame-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'">
<title>PawQuest Bistro</title><link rel="stylesheet" href="bistro.css"><style>'''+css+'''
@font-face{font-family:Quicksand;src:url('../gacha/quicksand.ttf')}html,body{margin:0;width:100%;background:#fff5f8;overflow-x:hidden}
main{padding:8px}#stage{height:clamp(210px,34vh,300px);position:relative;border:2px solid #4b2412;border-radius:20px;overflow:hidden;background:#ffc8dd}
#bistro-stage-svg{position:relative;width:100%;height:100%}#menu{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px;padding-top:12px;padding-bottom:20px}
.meal{min-width:0;border:2px solid #4b2412;border-radius:18px;background:white;padding:10px;display:flex;flex-direction:column;gap:6px}
.meal svg{width:100%;height:100px}.meal h3{font-size:13px;font-weight:800;min-height:34px}.meal p{font-size:11px}.meal button{margin-top:auto;min-height:44px;border:2px solid #4b2412;border-radius:12px;background:#fcd34d;font-weight:800}
.meal button:disabled{opacity:.5}#meal-status{font-size:13px;font-weight:700;text-align:center;padding:6px}.pq-paused *,.offscreen *{animation-play-state:paused!important}
#floating-buff-container{position:absolute;top:28px;right:12px;pointer-events:none}.buff{display:block;background:#fff9f5;border:2px solid #4b2412;border-radius:14px;padding:8px;font-size:12px;font-weight:800}
@media(prefers-reduced-motion:reduce){*,*::before,*::after{animation:none!important;transition:none!important}}
</style></head><body>'''+defs+'<main><section id="stage" aria-label="Pet dining animation">'+stage+'''<div id="floating-buff-container"></div></section><p id="meal-status" aria-live="polite"></p><section id="menu"></section></main><script>'''+templates+'\n'+controller+'</script></body></html>'
assert not any(token in runtime for token in ['playerCoins','petExp +=','feedPetWithItem','retriggerCurrentMeal','cdn.tailwindcss.com','fonts.googleapis.com'])
OUT.mkdir(parents=True,exist_ok=True)
(OUT/'bistro.html').write_text(runtime,encoding='utf-8')
prototype=ROOT/'design/prototypes/food';prototype.mkdir(parents=True,exist_ok=True)
shutil.copyfile(SOURCE,prototype/'12food.html')
(prototype/'menu.json').write_text(json.dumps(menu,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def quote(v):return "'"+v.replace("'","''")+"'"
seed=['begin;','-- Exact supplied 12-item menu; existing pantry food and Wheel Food stay unchanged.']
for m in menu:
    item=f"e2900000-0000-0000-0000-{m['order']:012d}"
    tier='BASIC' if m['order']<=3 else 'TASTY' if m['order']<=6 else 'PREMIUM' if m['order']<=10 else 'ROYAL'
    seed.append('insert into public.item_catalog(id,code,name,category,price_coins,asset_id,max_quantity) values ('+
      ','.join([quote(item),quote('bistro_'+m['id'][5:].replace('-','_')),quote(m['name']),"'FOOD'",str(m['price']),quote(m['id']),'1000000'])+');')
    seed.append('insert into public.bistro_foods(item_id,menu_order,tier,meal_type,hunger_gain,happiness_gain,xp_gain) values ('+
      ','.join([quote(item),str(m['order']),quote(tier),quote(m['type']),str(m['hungerRestore']),str(m['happinessBonus']),str(m['expBonus'])])+');')
seed.append('commit;')
seed_path=ROOT/'supabase/migrations/20261111003000_bistro_menu.sql'
seed_text='\n'.join(seed)+'\n'
if seed_path.exists():
    assert seed_path.read_text(encoding='utf-8')==seed_text,'Do not rewrite an existing migration; create a new versioned menu migration.'
else:seed_path.write_text(seed_text,encoding='utf-8')
toolchain=ROOT/'build/gacha-toolchain'
(toolchain/'bistro-input.css').write_text('@tailwind base;\n@tailwind components;\n@tailwind utilities;\n')
(toolchain/'bistro-tailwind.config.cjs').write_text('module.exports={content:['+repr(SOURCE.as_posix())+'],theme:{extend:{borderWidth:{3:"3px"}}}};')
subprocess.run(['node',str(toolchain/'node_modules/tailwindcss/lib/cli.js'),'-c',str(toolchain/'bistro-tailwind.config.cjs'),'-i',str(toolchain/'bistro-input.css'),'-o',str(OUT/'bistro.css'),'--minify'],check=True,cwd=ROOT)
print('PASS 12 exact recipes, original SVG/stage/four pets, offline display-only Bistro')
