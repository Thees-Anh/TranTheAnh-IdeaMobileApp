# Quest sessions and Focus Mode

Apply `supabase/migrations/20261105000600_focus_sessions.sql` once after 001–005,
then rebuild/run Android. The app does not apply hosted SQL automatically.
The Reward Claim screen additionally requires migration 007; see
[server reward quotes and strict claims](REWARDS.md).

## Authoritative session lifecycle

Quest definitions and executions remain separate. Each execution stores its own
UUID, owner, quest UUID, immutable title/duration, started_at and ends_at. PostgreSQL
sets timestamps; the client sends only quest/session UUIDs and a Start request UUID.
The existing portrait Activity lock is unchanged.

- `focus_start(quest,request)`: wallet-row lock serializes starts; an owner-scoped
  unique request key returns the same execution on replay, even after cancellation
  or claim. A different request cannot create another unfinished active session.
- `focus_reconcile(session?)`: returns server_now, the session, server-computed
  eligibility and any actual reward receipt. With no UUID, restores an active
  execution first, then the oldest completed/unclaimed execution. Expired active
  rows are finalized logically; no background task is needed.
- `focus_cancel(session)`: delegates to the existing cancellation RPC. Only an
  unfinished active execution can be cancelled. Repeated cancellation is safe
  and produces no ledger entry, Coins or XP.
- `focus_claim(session)`: delegates to the existing authoritative claim RPC.
  The server refuses early/cancelled/unauthorized claims, enforces five rewarded
  sessions and 250 Coins per UTC completion day, and returns the original receipt
  for a repeated claim. The client never calculates or writes a grant.

The current app uses `reward_reconcile`/`reward_claim` from migration 007 for
enriched quotes and explicit duplicate rejection. The older Focus claim endpoint
remains compatible. Lost-response recovery reads the receipt through Sync.

Migration 006 adds a trigger freezing execution identity, ownership, quest,
duration, timestamps, request key and title after insert. Editing/archiving the
definition cannot change an already-started execution. Existing RLS/column
privileges remain; new RPCs are authenticated-only with empty search_path.
Private helpers are inaccessible to clients. Existing session RPCs remain
compatible, while the new app uses request-aware Focus RPCs.

## Countdown and restoration

Shared `ManageFocusSessions` and `FocusRepository` isolate business/data access.
`SupabaseFocusRepository` shares the authenticated client. `FocusViewModel` owns
the flow; `FocusCountdown` uses an injectable monotonic clock, never device wall
time. Composables render state and domain-computed previews.

At each response the countdown anchors `ends_at - server_now` to the monotonic
clock at receipt. This conservatively excludes return-path network delay rather
than displaying an early finish. Foreground display updates every 250 ms and
reconciles about every 15 seconds and when reaching zero. Only a synchronized
server eligibility response enables Claim Reward; local 00 alone never does.
The large display uses rounded-up two-digit minutes (`MM`), with a circular
remaining-duration indicator.

LifecycleResumeEffect stops the ticker on pause/lock and reconciles on resume.
There is no foreground service, alarm or continuously running background timer.
Restarted processes query the server after encrypted Auth restoration/account
readiness; neither a lost local timer nor an altered device date can grant rewards.
Cold offline launches wait for Auth/server connectivity. Already-open offline
sessions retain a monotonic estimate, label it unsynchronized and disable Claim
until reconciliation succeeds.

Mutations have busy guards. A retry within the ViewModel reuses its Start UUID;
the key/result is durable in PostgreSQL. If Start loses its response, the app
first attempts to restore a committed execution. Process restart restores before
the next Start. Lost cancel/claim responses are recovered by Sync; receipt replay
cannot duplicate rewards. No mutation is automatically replayed by a background
timer. Pending request intent is not persisted as a separate local queue.

Focus Mode hides bottom navigation and uses safe-drawing insets and one vertical
scroll container for compact phones. Cancel requires confirmation. Back returns
to Quests without cancelling; Resume Focus reopens the execution. Done after a
terminal result returns to Quests. Other unclaimed executions remain on the server
and can be recovered by subsequent reconciliation/Resume Focus. Completed sessions
do not prevent a new explicitly started execution; unfinished active ones do.
The claim result displays actual server Coins/XP/balance, including cap reductions.

## Verification

```powershell
.\gradlew.bat :shared:testAndroidHostTest :androidApp:assembleDebug :androidApp:lintDebug
python tools/testing/test_database.py
```

Tests cover monotonic rounding, elapsed time across paused/resumed/new ViewModels,
no background ticking, local zero without server eligibility, offline recovery,
stable request keys, ambiguous Start/Claim responses, explicit cancellation and
SDK request/response contracts. Real PostgreSQL tests cover immutable snapshots,
restore, early-claim denial, ownership, cancellation, exactly-once receipts and
six concurrent retries of one Start request, plus all earlier integrity suites.

`tools/testing/fixtures/focus_http_fixture.py` is a loopback-only emulator test double
on port 55442. Its clock-advance endpoint exists only in this disposable server;
production app/database time cannot be accelerated. It is not hosted Supabase
verification. Use only the fixed fixture public key and `@example.invalid`
accounts with temporary debug configuration, then restore normal configuration.
