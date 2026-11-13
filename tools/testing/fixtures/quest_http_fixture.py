"""Disposable loopback-only Quest UI test double, never a production backend."""
from datetime import datetime, timezone
from urllib.parse import urlparse, parse_qs
import json
import uuid

from auth_http_fixture import Handler as AuthHandler, ThreadingHTTPServer, ACCESS, KEY, LOCK

QUESTS = {}
STARTS = []


class Handler(AuthHandler):
    def owner(self):
        return ACCESS.get(self.headers.get('Authorization', '').removeprefix('Bearer '))

    def do_GET(self):
        parsed = urlparse(self.path)
        if parsed.path == '/rest/v1/quests':
            user = self.owner()
            if not user:
                self.reply(401, {})
                return
            rows = [q for q in QUESTS.values() if q['user_id'] == user['id'] and not q['archived']]
            self.reply(200, sorted(rows, key=lambda q: q['created_at'], reverse=True))
        elif self.path == '/__test/quests':
            self.reply(200, {'quests': len(QUESTS), 'visible': sum(not q['archived'] for q in QUESTS.values()),
                             'starts': len(STARTS)})
        else:
            super().do_GET()

    def do_POST(self):
        path = urlparse(self.path).path
        if path not in ['/rest/v1/quests', '/rest/v1/rpc/quest_daily_reward_count', '/rest/v1/rpc/start_quest_session']:
            return super().do_POST()
        with LOCK:
            user = self.owner()
            if not user or self.headers.get('apikey') != KEY:
                self.reply(401, {})
                return
            payload = json.loads(self.rfile.read(int(self.headers.get('Content-Length', 0))) or b'{}')
            if path.endswith('quest_daily_reward_count'):
                self.reply(200, 0)
            elif path.endswith('start_quest_session'):
                quest = QUESTS.get(payload.get('p_quest_id'))
                if not quest or quest['user_id'] != user['id'] or quest['archived']:
                    self.reply(403, {'code': '42501', 'message': 'Unavailable', 'details': None, 'hint': None})
                elif STARTS:
                    self.reply(409, {'code': '23505', 'message': 'An active session already exists', 'details': None, 'hint': None})
                else:
                    result = dict(id=str(uuid.uuid4()), quest_id=quest['id'], ends_at='2026-11-05T23:00:00Z')
                    STARTS.append(result)
                    self.reply(200, result)
            else:
                fields = payload[0] if isinstance(payload, list) else payload
                assert set(fields) == {'title', 'description', 'category', 'duration_minutes', 'repeat_type'}
                quest = dict(fields, id=str(uuid.uuid4()), user_id=user['id'], archived=False,
                             created_at=datetime.now(timezone.utc).isoformat())
                QUESTS[quest['id']] = quest
                self.reply(201, [quest])

    def do_PATCH(self):
        user = self.owner()
        query = parse_qs(urlparse(self.path).query)
        quest_id = query.get('id', [''])[0].removeprefix('eq.')
        quest = QUESTS.get(quest_id)
        if not user or not quest or quest['user_id'] != user['id']:
            self.reply(200, [])
            return
        fields = json.loads(self.rfile.read(int(self.headers.get('Content-Length', 0))) or b'{}')
        assert set(fields) <= {'title', 'description', 'category', 'duration_minutes', 'repeat_type', 'archived'}
        quest.update(fields)
        self.reply(200, [quest])


if __name__ == '__main__':
    ThreadingHTTPServer(('127.0.0.1', 55441), Handler).serve_forever()
