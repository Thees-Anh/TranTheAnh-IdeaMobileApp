"""Loopback-only Supabase HTTP test double for emulator auth UI checks.

Not an auth service and never a production/demo fallback. It accepts only
@example.invalid accounts and a fixed test-only public key. Real application
authentication always uses the configured Supabase project and SDK.
"""
import base64
import hashlib
import hmac
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import secrets
import threading
import time
import uuid

KEY = 'sb_publishable_pawquest_fixture_only'
LOCK = threading.RLock()
USERS, ACCESS, REFRESH, ACCOUNTS = {}, {}, {}, {}
COUNTERS = {'registrations': 0, 'logins': 0, 'account_reads': 0, 'logouts': 0}


def encoded(value):
    return base64.urlsafe_b64encode(json.dumps(value, separators=(',', ':')).encode()).rstrip(b'=').decode()


def session(user):
    now = int(time.time())
    prefix = encoded({'alg': 'HS256', 'typ': 'JWT'}) + '.' + encoded({
        'sub': user['id'], 'aud': 'authenticated', 'role': 'authenticated', 'exp': now+3600, 'iat': now,
    })
    signature = base64.urlsafe_b64encode(hmac.digest(b'local-test-fixture-only', prefix.encode(), 'sha256')).rstrip(b'=').decode()
    access = prefix + '.' + signature
    refresh = secrets.token_urlsafe(32)
    ACCESS[access] = user
    REFRESH[refresh] = user
    return dict(access_token=access, refresh_token=refresh, expires_in=3600, token_type='bearer', user=public_user(user))


def public_user(user):
    return dict(id=user['id'], aud='authenticated', role='authenticated', email=user['email'],
                app_metadata={'provider': 'email'}, user_metadata={}, identities=[],
                created_at='2026-01-01T00:00:00Z')


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_):
        pass  # Never log credentials, tokens or request bodies.

    def reply(self, status, value):
        data = json.dumps(value).encode()
        self.send_response(status)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        if self.path == '/__test/status':
            self.reply(200, dict(COUNTERS, users=len(USERS), accounts=len(ACCOUNTS)))
        else:
            self.reply(404, {})

    def do_POST(self):
        if self.headers.get('apikey') != KEY:
            self.reply(401, {'code': 'invalid_api_key'})
            return
        try:
            payload = json.loads(self.rfile.read(int(self.headers.get('Content-Length', 0))) or b'{}')
        except (ValueError, json.JSONDecodeError):
            self.reply(400, {})
            return
        with LOCK:
            path = self.path.split('?')[0]
            if path == '/auth/v1/signup':
                email, password = payload.get('email', ''), payload.get('password', '')
                if not email.endswith('@example.invalid') or len(password) < 8:
                    self.reply(400, {'code': 'email_address_invalid', 'msg': 'Fixture accounts only'})
                    return
                if email not in USERS:
                    USERS[email] = dict(id=str(uuid.uuid4()), email=email,
                                        password_hash=hashlib.sha256(password.encode()).hexdigest())
                    COUNTERS['registrations'] += 1
                self.reply(200, session(USERS[email]))
            elif path == '/auth/v1/token':
                if 'refresh_token' in payload:
                    user = REFRESH.get(payload['refresh_token'])
                else:
                    user = USERS.get(payload.get('email'))
                    if user and user['password_hash'] != hashlib.sha256(payload.get('password', '').encode()).hexdigest():
                        user = None
                if not user:
                    self.reply(400, {'code': 'invalid_credentials', 'error_code': 'invalid_credentials', 'msg': 'Invalid login credentials'})
                    return
                COUNTERS['logins'] += 1
                self.reply(200, session(user))
            else:
                token = self.headers.get('Authorization', '').removeprefix('Bearer ')
                user = ACCESS.get(token)
                if not user:
                    self.reply(401, {'code': 'bad_jwt', 'msg': 'No fixture session'})
                    return
                if path == '/rest/v1/rpc/ensure_account':
                    COUNTERS['account_reads'] += 1
                    if user['id'] not in ACCOUNTS:
                        ACCOUNTS[user['id']] = dict(
                            profile={'id': user['id'], 'display_name': 'Adventurer'},
                            wallet={'id': str(uuid.uuid4()), 'coins': 0},
                            pet={'id': str(uuid.uuid4()), 'name': 'Mochi', 'xp': 0},
                        )
                    self.reply(200, ACCOUNTS[user['id']])
                elif path == '/auth/v1/logout':
                    COUNTERS['logouts'] += 1
                    ACCESS.pop(token, None)
                    for key in [k for k, v in REFRESH.items() if v['id'] == user['id']]:
                        REFRESH.pop(key)
                    self.reply(200, {})
                else:
                    self.reply(404, {})


if __name__ == '__main__':
    print('PawQuest emulator HTTP fixture on 127.0.0.1:55440 (test accounts only)', flush=True)
    ThreadingHTTPServer(('127.0.0.1', 55440), Handler).serve_forever()
