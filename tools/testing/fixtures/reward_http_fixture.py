"""Disposable reward UI fixture; not a production reward engine."""
import io
import json
from urllib.parse import urlparse
from focus_http_fixture import Handler as FocusHandler, SESSIONS, snapshot, ThreadingHTTPServer


class Handler(FocusHandler):
    def reply(self,status,value):
        if status==200 and isinstance(value,dict) and 'server_now' in value:
            session=value.get('session')
            offer=None
            if session:
                minutes=session['duration_minutes']
                coins,xp=next((coins,xp) for end,coins,xp in [(14,10,5),(29,20,10),(44,35,20),(59,50,30),(74,65,40),(90,80,50)] if minutes<=end)
                offer=dict(tier_coins=coins,coins=coins,pet_xp=xp,reward_day=session['ends_at'][:10],
                           rewarded_sessions=0,limit_reached=False,can_claim=value.get('eligible',False))
            value=dict(value,reward_offer=offer)
        super().reply(status,value)

    def do_POST(self):
        path=urlparse(self.path).path
        if path=='/rest/v1/rpc/reward_reconcile':
            self.path='/rest/v1/rpc/focus_reconcile'
        elif path=='/rest/v1/rpc/reward_claim':
            raw=self.rfile.read(int(self.headers.get('Content-Length',0)))
            self.rfile=io.BytesIO(raw)
            payload=json.loads(raw)
            session=SESSIONS.get(payload.get('p_session_id'))
            user=self.owner()
            if user and session and session['user_id']==user['id'] and session['status']=='claimed':
                self.reply(409,dict(code='23505',message='Reward already claimed',details=None,hint=None))
                return
            self.path='/rest/v1/rpc/focus_claim'
        super().do_POST()


if __name__=='__main__':
    ThreadingHTTPServer(('127.0.0.1',55443),Handler).serve_forever()
