"""Loopback-only Focus UI fixture. Clock advancing is test-server-only, never app code."""
from datetime import datetime, timedelta, timezone
from urllib.parse import urlparse, parse_qs
import json
import uuid
from quest_http_fixture import Handler as QuestHandler, QUESTS, ThreadingHTTPServer, LOCK

SESSIONS={}
REQUESTS={}
OFFSET=0
CLAIMS=0


def now():
    return datetime.now(timezone.utc)+timedelta(seconds=OFFSET)


def snapshot(value):
    stamp=now()
    if value and value['status']=='active' and stamp>=datetime.fromisoformat(value['ends_at']):
        value['status']='completed'
    return dict(server_now=stamp.isoformat(),session=value,
                eligible=bool(value and value['status']=='completed'),
                reward=dict(coins=10,pet_xp=5,balance_after=10) if value and value['status']=='claimed' else None)


class Handler(QuestHandler):
    def do_GET(self):
        global OFFSET
        parsed=urlparse(self.path)
        if parsed.path=='/__test/focus/advance':
            OFFSET+=int(parse_qs(parsed.query).get('seconds',['0'])[0])
            self.reply(200,{'advanced':True})
        elif parsed.path=='/__test/focus/status':
            self.reply(200,{'sessions':len(SESSIONS),'claims':CLAIMS})
        else:
            super().do_GET()

    def do_POST(self):
        global CLAIMS
        path=urlparse(self.path).path
        if not path.startswith('/rest/v1/rpc/focus_'):
            return super().do_POST()
        user=self.owner()
        if not user:
            self.reply(401,{})
            return
        body=json.loads(self.rfile.read(int(self.headers.get('Content-Length',0))) or b'{}')
        with LOCK:
            value=SESSIONS.get(body.get('p_session_id'))
            if path.endswith('focus_start'):
                key=(user['id'],body['p_request_id'])
                if key in REQUESTS:
                    value=SESSIONS[REQUESTS[key]]
                else:
                    quest=QUESTS[body['p_quest_id']]
                    stamp=now()
                    value=dict(id=str(uuid.uuid4()),user_id=user['id'],quest_id=quest['id'],
                               title_snapshot=quest['title'],duration_minutes=quest['duration_minutes'],
                               started_at=stamp.isoformat(),ends_at=(stamp+timedelta(minutes=quest['duration_minutes'])).isoformat(),
                               status='active')
                    SESSIONS[value['id']]=value
                    REQUESTS[key]=value['id']
            elif path.endswith('focus_reconcile'):
                if not value:
                    values=[v for v in SESSIONS.values() if v['user_id']==user['id'] and v['status'] in ('active','completed')]
                    value=values[-1] if values else None
            elif path.endswith('focus_cancel'):
                value['status']='cancelled'
            elif path.endswith('focus_claim'):
                snapshot(value)
                if value['status'] not in ('completed','claimed'):
                    self.reply(400,{'code':'23514','message':'Session is not eligible','details':None,'hint':None})
                    return
                if value['status']!='claimed':
                    CLAIMS+=1
                value['status']='claimed'
            else:
                self.reply(404,{})
                return
            self.reply(200,snapshot(value))


if __name__=='__main__':
    ThreadingHTTPServer(('127.0.0.1',55442),Handler).serve_forever()
