"""Loopback-only equipment/GLB smoke fixture. Not a production backend."""
import json
from datetime import datetime,timezone
from urllib.parse import urlparse
from pet_animation_http_fixture import Handler as CareHandler,ThreadingHTTPServer,CATALOG,get_user_inventory

EQUIPMENT={}
MISSING=False
def state(user):
    if user not in EQUIPMENT:
        stamp=datetime.now(timezone.utc).isoformat()
        inv=get_user_inventory(user)
        for i,slot in [(1,'HEAD'),(2,'FACE'),(3,'NECK')]:
            inv.append(dict(id='fixture-'+slot,user_id=user,quantity=1,created_at=stamp,item=CATALOG[i]))
        EQUIPMENT[user]=[dict(slot=slot,item_id=CATALOG[i]['id'],asset_id=CATALOG[i]['asset_id'],equipped_at=stamp)
            for i,slot in [(1,'HEAD'),(2,'FACE'),(3,'NECK')]]
    return [dict(row,asset_id='missing.fixture.asset') if MISSING and row['slot']=='HEAD' else row for row in EQUIPMENT[user]]

class Handler(CareHandler):
    def do_GET(self):
        global MISSING
        if self.path=='/__test/equipment/missing': MISSING=True;self.reply(200,{'missing':True})
        elif self.path=='/__test/equipment/restore': MISSING=False;self.reply(200,{'missing':False})
        else: super().do_GET()
    def do_POST(self):
        user=self.owner();path=urlparse(self.path).path
        if user and path in ('/rest/v1/rpc/equipment_state','/rest/v1/rpc/equipment_set'):
            uid=user['id'];state(uid)
            if path.endswith('equipment_set'):
                body=json.loads(self.rfile.read(int(self.headers.get('Content-Length',0))) or b'{}')
                EQUIPMENT[uid]=[row for row in EQUIPMENT[uid] if row['slot']!=body['p_slot']]
                if body.get('p_item_id'):
                    item=next(x for x in CATALOG if x['id']==body['p_item_id'])
                    EQUIPMENT[uid].append(dict(slot=body['p_slot'],item_id=item['id'],asset_id=item['asset_id'],equipped_at=datetime.now(timezone.utc).isoformat()))
            self.reply(200,state(uid));return
        if user and path=='/rest/v1/rpc/inventory_items': state(user['id'])
        super().do_POST()

if __name__=='__main__': ThreadingHTTPServer(('127.0.0.1',55445),Handler).serve_forever()
