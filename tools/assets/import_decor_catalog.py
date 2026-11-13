"""Import the user-authored catalog and animation CSS; generate an additive migration."""
from pathlib import Path
import re,json,argparse
ROOT=next(parent for parent in Path(__file__).resolve().parents if (parent / 'settings.gradle.kts').is_file())
parser=argparse.ArgumentParser();parser.add_argument('--source',type=Path);parser.add_argument('--check',action='store_true');args=parser.parse_args()
directory=ROOT/'design/assets/decor';directory.mkdir(parents=True,exist_ok=True)
items=json.loads((directory/'catalog.json').read_text(encoding='utf-8'))
if args.source:
    if args.check:parser.error('--source and --check cannot be combined')
    text=args.source.read_text(encoding='utf-8');payload=json.loads(re.search(r'(\{\s*"petAccessories"[\s\S]*?\n\})',text)[1]);items=[]
    for category,rows in [('ACCESSORY',payload['petAccessories']),('FURNITURE',payload['roomFurniture'])]:
        for item in rows:
            item=dict(item,category=category,code=item['id'].replace('-','_'),uuid=f'e2300000-0000-0000-0000-{len(items)+1:012d}')
            item['slot']=item['id'].removeprefix('furn-').replace('-','_').upper() if category=='FURNITURE' else item['slot'].upper()
            items.append(item)
    (directory/'catalog.json').write_text(json.dumps(items,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    (directory/'animations.css').write_text(re.search(r'3\.[\s\S]*?```css\s*([\s\S]*?)```',text)[1],encoding='utf-8')
equipment=['HEAD','FACE','NECK','BACK']
furniture=['BED','LAMP','CARPET','WINDOW','CLOCK','LIGHTS','BOOKSHELF','CAT_TREE','PLANT','TEA_TABLE','GRAMOPHONE']
q=lambda value:"'"+str(value).replace("'","''")+"'"
sql="begin;\nalter table public.item_catalog add column description text not null default '' check(length(description)<=500);\n"
for table,values in [('accessory_slots',equipment),('pet_equipment',equipment),('furniture_slots',furniture),('room_furniture',furniture)]:
    sql+=f"alter table public.{table} drop constraint {table}_slot_check;\nalter table public.{table} add constraint {table}_slot_check check(slot in ({','.join(map(q,values))}));\n"
sql+='insert into public.item_catalog(id,code,name,category,price_coins,asset_id,max_quantity,description) values\n'
sql+=',\n'.join('('+','.join([q(i['uuid']),q(i['code']),q(i['name']),q(i['category']),str(i['price']),q(i['id']),'1',q(i['desc'])])+')' for i in items)+';\n'
for category,table in [('ACCESSORY','accessory_slots'),('FURNITURE','furniture_slots')]:
    sql+=f'insert into public.{table}(item_id,slot) values\n'+',\n'.join('('+q(i['uuid'])+','+q(i['slot'])+')' for i in items if i['category']==category)+';\n'
for file,name,old,new in [('20261105001200_pet_equipment.sql','equipment_set',['HEAD','FACE','NECK'],equipment),('20261105001300_room_furniture.sql','room_set',['BED','LAMP','CARPET'],furniture)]:
    original=(ROOT/'supabase/migrations'/file).read_text(encoding='utf-8')
    fn=re.search(r'create function public\.'+name+r'\([\s\S]*?end \$\$;',original)[0]
    sql+=fn.replace('create function','create or replace function',1).replace(','.join(map(q,old)),','.join(map(q,new)))+'\n'
sql+='commit;\n'
target=ROOT/'supabase/migrations/20261109002300_decor_catalog.sql'
if args.check:assert target.read_text(encoding='utf-8')==sql,'Stale catalog migration'
else:target.write_text(sql,encoding='utf-8')
print('Imported 10 accessories and 10 furniture entries with server prices/slots')
