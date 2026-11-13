"""Exercise the main journey through the local adapter and actual migrated SQL RPCs.
Run only with tools/testing/fixtures/mvp_sql_backend.py (localhost:55447).
Auth is a test double and session time is explicitly accelerated in the test DB.
"""
import json,urllib.request,urllib.error,uuid

BASE='http://127.0.0.1:55447'
KEY='sb_publishable_pawquest_fixture_only'
token=None
def request(path,data=None,status=200):
    headers={'apikey':KEY,'Content-Type':'application/json'}
    if token: headers['Authorization']='Bearer '+token
    req=urllib.request.Request(BASE+path,data=None if data is None else json.dumps(data).encode(),headers=headers)
    try:
        with urllib.request.urlopen(req,timeout=20) as r: actual=r.status;body=json.load(r)
    except urllib.error.HTTPError as e: actual=e.code;body=json.load(e)
    assert actual==status,(path,actual,status)
    return body
def rpc(name,args=None,status=200):return request('/rest/v1/rpc/'+name,args or {},status)
def uid():return str(uuid.uuid4())

def main():
    global token
    email='api-'+uuid.uuid4().hex[:8]+'@example.invalid'
    session=request('/auth/v1/signup',dict(email=email,password='FixturePass123!'))
    token=session['access_token'];actor=session['user']['id']
    account=rpc('ensure_account');rpc('ensure_account')
    assert account['wallet']['coins']==0
    quest=request('/rest/v1/quests',dict(title='MVP integration',description='Real SQL journey',category='Focus',duration_minutes=90,repeat_type='daily'),201)[0]
    for cycle in range(3):
        intent=dict(p_quest_id=quest['id'],p_request_id=uid())
        started=rpc('focus_start',intent);sid=started['session']['id']
        assert rpc('focus_start',intent)['session']['id']==sid
        assert rpc('reward_claim',dict(p_session_id=sid),400)['code']=='23514'
        assert rpc('focus_start',dict(p_quest_id=quest['id'],p_request_id=uid()),409)['code']=='23505'
        request('/__test/advance?seconds=5401')
        receipt=rpc('reward_claim',dict(p_session_id=sid))
        assert receipt['reward']['coins']==80 and receipt['reward']['pet_xp']==50
        assert rpc('reward_claim',dict(p_session_id=sid),409)['code']=='23505'
        if cycle==0:
            food=dict(p_item_id='e1000000-0000-0000-0000-000000000001',p_quantity=1,p_request_id=uid())
            bought=rpc('shop_purchase',food)
            assert bought['receipt']['balance_after']==70
            assert rpc('shop_purchase',food)['receipt']['id']==bought['receipt']['id']
            assert rpc('shop_purchase',dict(p_item_id='e1000000-0000-0000-0000-000000000005',p_quantity=1,p_request_id=uid()))['status']=='insufficient_balance'
            care=dict(p_action='feed',p_request_id=uid())
            fed=rpc('pet_state',care);assert fed['care_result']['applied']
            assert rpc('pet_state',care)['food_quantity']==fed['food_quantity']
            hat=rpc('shop_purchase',dict(p_item_id='e1000000-0000-0000-0000-000000000002',p_quantity=1,p_request_id=uid()))
            assert hat['receipt']['balance_after']==10
            assert rpc('equipment_set',dict(p_slot='HEAD',p_item_id='e1000000-0000-0000-0000-000000000002'))[0]['slot']=='HEAD'
    assert rpc('shop_purchase',dict(p_item_id='e1000000-0000-0000-0000-000000000005',p_quantity=1,p_request_id=uid()))['status']=='purchased'
    rpc('room_set',dict(p_slot='BED',p_item_id='e1000000-0000-0000-0000-000000000005'))
    pet=rpc('pet_state');assert pet['coins']==50 and pet['pet']['xp']==150 and pet['food_quantity']==3
    assert len(rpc('equipment_state'))==1 and len(rpc('room_state'))==1
    print('PASS SQL-backed API journey: register/initialize -> quest -> 3 server-timed rewards -> food/feed -> hat/equip -> bed/place; 50 Coins, 150 XP, food 3')
    request('/__test/drop_next?rpc=shop_purchase')
    intent=dict(p_item_id='e1000000-0000-0000-0000-000000000001',p_quantity=1,p_request_id=uid())
    lost=False
    try: rpc('shop_purchase',intent)
    except (ConnectionError,OSError): lost=True
    assert lost,'Expected a dropped response after SQL commit'
    retried=rpc('shop_purchase',intent)
    assert retried['receipt']['balance_after']==40 and rpc('pet_state')['food_quantity']==4
    print('PASS committed purchase with lost HTTP response -> same request retry: one debit/grant')
    request('/auth/v1/logout',{})
    rpc('ensure_account',status=401)
    token=request('/auth/v1/token?grant_type=password',dict(email=email,password='FixturePass123!'))['access_token']
    assert rpc('ensure_account')['profile']['id']==actor
    assert rpc('pet_state')['coins']==40 and len(rpc('room_state'))==1
    print('PASS Auth-double logout denies protected requests; login restores same database state')

if __name__=='__main__':main()
