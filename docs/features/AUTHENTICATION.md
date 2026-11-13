# Authentication

Android uses Supabase Auth 3.8.0 with email/password authentication. Shared
domain contracts, use cases, repository, StateFlow state and ViewModel live in
`commonMain`; Android supplies OkHttp and encrypted session persistence through
Koin. Compose only calls the ViewModel, never Auth tables directly.

## Configure and run

Copy the entries from `config/examples/supabase.properties.example` into untracked
`local.properties`, alongside `sdk.dir`. Set `SUPABASE_URL` and
`SUPABASE_PUBLISHABLE_KEY` (or legacy `SUPABASE_ANON_KEY`). Environment variables
override Gradle properties, which override local.properties. Rebuild after edits.
Public keys are client configuration, not authorization; RLS remains mandatory.
Never put a secret/service-role key in the app. Gradle rejects secret keys and
legacy JWT keys whose role is not `anon`. Release connections require HTTPS.

Apply all SQL migrations in filename order to the Supabase project using
its trusted migration tooling. Enable the Email provider and configure email
confirmation/delivery in the dashboard. The app does not deploy hosted SQL.
With confirmation enabled, registration asks the user to confirm their email
and then sign in. OAuth and app deep links are outside this task.

## Password reset (in-app code)

"Forgot password?" on the sign-in card asks for the email and calls
`resetPasswordForEmail` without a redirect. The user types the emailed code
plus a new password; the app verifies it with `verifyEmailOtp(RECOVERY)` and
then `updateUser(password)`. While recovery is in progress the repository keeps
the temporary session out of the app; any failure clears it, so a half-finished
reset never signs the user in. The confirmation message never reveals whether
the email has an account.

**Hosted setup required:** in Supabase Dashboard → Authentication → Email
Templates → Reset Password, include the code, for example
`<p>Your PawQuest code: <strong>{{ .Token }}</strong></p>`. The default template
only contains a link, which this app does not handle.

## Account deletion

Profile → "Delete account" asks for the current password, re-authenticates,
then calls `public.delete_my_account()` (migration `20261112003100`). The
function deletes the `auth.users` row (email, password hash, identities,
sessions, refresh tokens), replaces quest titles/descriptions and session title
snapshots with placeholders and marks the profile as an anonymous tombstone.
Append-only Coins/shard/wheel/bistro ledgers stay for integrity, keyed only by a
random UUID with no remaining identity. Disclose this retention in the Play
Data safety form and privacy policy. The app then clears its local session.

Missing configuration shows a setup message and keeps navigation protected.
The project URL alone is insufficient: a publishable/anon key and deployed
migrations are required for a live-project smoke test.

If account preparation fails after a local UI smoke test, check that
`SUPABASE_URL` and the public key still belong to the real project. A fixture
URL such as `http://127.0.0.1:55444` requires its disposable server and is not
the hosted backend. Do not leave fixture settings in `local.properties`.
Debug builds emit `PawQuestAccount` diagnostics with exception class names and
HTTP status and a fixed transport classification only; tokens, response bodies and raw exception messages are omitted.

Corporate HTTPS inspection can also cause a connection error even when Windows
can reach Supabase. On the development emulator, a verified HTTPS probe observed
`Trust anchor for certification path not found` with a certificate issued by
Vingroup Web Gateway. Use an approved network without interception, or ask IT
for an approved Android development trust setup. Do not disable certificate or
hostname verification to make login work. A successful ping does not verify TLS.

For a development network whose public CA is already trusted on the developer's
machine, debug builds support an optional `PAWQUEST_DEBUG_CA_CERT` setting in
ignored `local.properties`, pointing to a public PEM CA file under ignored
`local-dev/`. No private key is required. The generated Android network-security
configuration adds that CA **only to the exact configured Supabase hostname**;
normal system trust and hostname/certificate validation remain enabled. Release
variants include neither this resource directory nor the debug manifest override.
Unset the setting when changing to a local HTTP fixture or a network that does
not require the development CA. Do not commit the CA or local settings.

An expired stored session may keep Supabase SDK initialization inside its network
retry loop. Explicit Retry handles RefreshFailure before awaiting initialization,
restarts the supported storage/session restore and preserves the encrypted session;
it never unlocks protected navigation before `ensure_account()` succeeds.

## Session and account lifecycle

States are Restoring, SignedOut, SignedIn and Unavailable. Navigation unlocks
only after a valid session AND successful `ensure_account()` return an account
owned by the authenticated user. Network/database initialization failures fail
closed and offer retry/sign-out. Auth refresh and expiry use the supported SDK.

Android's SessionManager writes AES-GCM ciphertext atomically to `noBackupFilesDir`;
its key lives in Android Keystore. Android backup is disabled. Passwords are
neither persisted nor kept in ViewModel/saved state, and SDK logging is disabled.
Corrupt/unreadable persisted sessions require login. Logout removes local
session data even if remote revocation fails; that case reports local-only
logout, since previously issued remote tokens may remain valid until expiry.
Protected ViewModel stores are destroyed when leaving an authenticated account.

The auth.users trigger initializes profile, zero-Coin wallet and default cat in
the registration transaction. Migration 003 adds authenticated `ensure_account()`
to repair missing rows with unique keys and ON CONFLICT DO NOTHING. Repeated or
concurrent calls preserve pet IDs, names, earned balances and XP. Clients cannot
select another user's bundle, alter reward balances or insert system records.

Forms use existing white/sky-blue tokens, scrollable single-column content,
safe-drawing and IME insets, password visibility, autofill and inline errors.
The existing protected feature screens still contain design-system demo data;
the Profile account card shows actual authenticated account values.

## Reproducible verification

```powershell
.\gradlew.bat :shared:testAndroidHostTest :androidApp:assembleDebug :androidApp:lintDebug
python tools/testing/test_database.py
```

Host tests cover validation, duplicate submission, confirmation, mapped errors,
SDK session save/restore, logout with a failed network, and account-read failure.
SQL tests cover ownership, repeated initialization and preservation of rewards;
the database runner additionally races six initialization calls.

`tools/testing/fixtures/auth_http_fixture.py` is a loopback-only disposable test
double for emulator UI checks. It accepts only `@example.invalid` accounts and
uses the fixed test key documented in its source. It is not Supabase or a
production server. Use debug build environment overrides for its endpoint;
`adb reverse tcp:55440 tcp:55440` allows `http://127.0.0.1:55440` on the emulator.
Never persist fixture configuration as production configuration. The real SDK
and encrypted Android storage are exercised against this fixture, while real
PostgreSQL behavior is tested independently.

For live verification, register a disposable account, confirm its email if
required, sign in, restart the process, verify the same cat/wallet IDs, sign out,
and verify protected navigation is inaccessible. Repeat on compact portrait
phones with the keyboard open. iOS secure persistence is not implemented here.
