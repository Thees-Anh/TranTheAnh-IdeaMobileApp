"""Loopback-only confirmed-care, shop and inventory animation fixture, never a production backend."""
from datetime import datetime,timedelta,timezone
from urllib.parse import urlparse
import json
import uuid
from reward_http_fixture import Handler as RewardHandler,ThreadingHTTPServer

PETS={}
RESULTS={}
COUNTS={action:0 for action in ('feed','play','sleep','touch')}
WALLETS={}
INVENTORIES={}

CATALOG=[
    {"id": "e1000000-0000-0000-0000-000000000001", "code": "basic_food", "name": "Cat food", "category": "FOOD", "price_coins": 10, "asset_id": "item.food.basic", "max_quantity": 1000000, "active": True},
    {"id": "e1000000-0000-0000-0000-000000000002", "code": "hat", "name": "Hat", "category": "ACCESSORY", "price_coins": 60, "asset_id": "item.accessory.hat", "max_quantity": 1, "active": True},
    {"id": "e1000000-0000-0000-0000-000000000003", "code": "glasses", "name": "Glasses", "category": "ACCESSORY", "price_coins": 80, "asset_id": "item.accessory.glasses", "max_quantity": 1, "active": True},
    {"id": "e1000000-0000-0000-0000-000000000004", "code": "collar", "name": "Collar", "category": "ACCESSORY", "price_coins": 40, "asset_id": "item.accessory.collar", "max_quantity": 1, "active": True},
    {"id": "e1000000-0000-0000-0000-000000000005", "code": "bed", "name": "Bed", "category": "FURNITURE", "price_coins": 120, "asset_id": "item.furniture.bed", "max_quantity": 1, "active": True},
    {"id": "e1000000-0000-0000-0000-000000000006", "code": "lamp", "name": "Lamp", "category": "FURNITURE", "price_coins": 70, "asset_id": "item.furniture.lamp", "max_quantity": 1, "active": True},
    {"id": "e1000000-0000-0000-0000-000000000007", "code": "carpet", "name": "Carpet", "category": "FURNITURE", "price_coins": 90, "asset_id": "item.furniture.carpet", "max_quantity": 1, "active": True},
]

def get_user_inventory(user_id):
    if user_id not in INVENTORIES:
        stamp = datetime.now(timezone.utc).isoformat()
        INVENTORIES[user_id] = [
            {
                "id": str(uuid.uuid4()),
                "user_id": user_id,
                "quantity": 1,
                "created_at": stamp,
                "item": CATALOG[0],
            }
        ]
    return INVENTORIES[user_id]

class Handler(RewardHandler):
    def do_GET(self):
        if self.path=='/__test/pet/status': self.reply(200,COUNTS)
        elif self.path=='/__test/pet/wake':
            for pet in PETS.values(): pet['sleep_until']=None; pet['energy']=100
            self.reply(200,{'awake':True})
        else: super().do_GET()

    def do_POST(self):
        path=urlparse(self.path).path
        user=self.owner()
        if not user:
            return super().do_POST()

        if path=='/rest/v1/rpc/shop_catalog':
            self.reply(200, CATALOG)
            return

        if path=='/rest/v1/rpc/inventory_items':
            self.reply(200, get_user_inventory(user['id']))
            return

        if path=='/rest/v1/rpc/shop_purchase':
            body=json.loads(self.rfile.read(int(self.headers.get('Content-Length',0))) or b'{}')
            item_id=body.get('p_item_id')
            qty=body.get('p_quantity',1)
            req_id=body.get('p_request_id')
            item=next((it for it in CATALOG if it['id']==item_id),None)
            if not item:
                self.reply(200, dict(status='item_unavailable',receipt=None))
                return

            total_cost=item['price_coins']*qty
            current_coins=WALLETS.setdefault(user['id'],150)
            if current_coins<total_cost:
                self.reply(200, dict(status='insufficient_balance',receipt=None))
                return

            inv=get_user_inventory(user['id'])
            existing=next((entry for entry in inv if entry['item']['id']==item_id),None)
            if existing and not item['code'].startswith('basic_food') and item['category']!='FOOD':
                self.reply(200, dict(status='already_owned',receipt=None))
                return

            # Deduct balance
            current_coins-=total_cost
            WALLETS[user['id']]=current_coins

            stamp=datetime.now(timezone.utc).isoformat()
            if existing:
                existing['quantity']+=qty
            else:
                inv.append(dict(id=str(uuid.uuid4()),user_id=user['id'],quantity=qty,created_at=stamp,item=item))

            if item['code']=='basic_food' and user['id'] in PETS:
                PETS[user['id']]['food']+=qty

            receipt=dict(
                id=str(uuid.uuid4()),
                user_id=user['id'],
                kind='shop_purchase',
                request_id=req_id,
                item_id=item_id,
                quantity=qty,
                unit_price=item['price_coins'],
                coins=-total_cost,
                balance_after=current_coins,
                created_at=stamp,
            )
            self.reply(200, dict(status='purchased',receipt=receipt))
            return

        if path!='/rest/v1/rpc/pet_state': return super().do_POST()

        body=json.loads(self.rfile.read(int(self.headers.get('Content-Length',0))) or b'{}')
        stamp=datetime.now(timezone.utc)
        pet=PETS.setdefault(user['id'],dict(id=user['id'],user_id=user['id'],name='Mochi',hunger=40,happiness=40,
            energy=50,xp=0,sleep_until=None,stats_updated_at=stamp.isoformat(),food=1))
        result=None
        action=body.get('p_action')
        if action:
            key=(user['id'],body['p_request_id'])
            if key in RESULTS: result=RESULTS[key]
            else:
                code='pet_sleeping' if pet['sleep_until'] else None
                if not code and action=='feed' and pet['food']==0: code='no_food'
                if not code:
                    COUNTS[action]+=1
                    if action=='feed':
                        pet['food']-=1; pet['hunger']+=15
                        # also decrement in inventory
                        inv=get_user_inventory(user['id'])
                        food_entry=next((e for e in inv if e['item']['category']=='FOOD'),None)
                        if food_entry and food_entry['quantity']>0: food_entry['quantity']-=1
                    if action=='play': pet['happiness']+=15; pet['energy']-=5
                    if action=='sleep': pet['sleep_until']=(stamp+timedelta(minutes=30)).isoformat()
                    code=dict(feed='fed',play='played',sleep='sleep_started',touch='touched')[action]
                applied=code in ('fed','played','sleep_started','touched')
                result=dict(request_id=body['p_request_id'],action=action,code=code,applied=applied,
                    food_consumed=1 if code=='fed' else 0)
                RESULTS[key]=result
        coins=WALLETS.setdefault(user['id'],150)
        self.reply(200,dict(pet=pet,coins=coins,server_now=stamp.isoformat(),food_quantity=pet['food'],care_result=result))

if __name__=='__main__':
    ThreadingHTTPServer(('127.0.0.1',55444),Handler).serve_forever()
