# Initial Supabase database

Migration `20261105001300_room_furniture.sql` adds owned, fixed furniture slots
and room persistence. Apply once after 012. Authenticated read/set RPCs validate
ownership and slot compatibility; clients cannot write room tables directly.
See [ROOM_DECORATION.md](../features/ROOM_DECORATION.md).

Migration `20261105001200_pet_equipment.sql` adds owned, slot-constrained pet
equipment and authenticated read/set RPCs. Apply once after 011. See
[EQUIPMENT.md](../features/EQUIPMENT.md) for permissions and idempotent setter semantics.

Migration `20261105001100_shop_purchases.sql` extends the immutable wallet ledger
with shop debits and adds an authenticated, server-priced atomic purchase RPC.
Apply once after 010. See [SHOP_INVENTORY.md](../features/SHOP_INVENTORY.md) for request IDs,
result statuses and client retry rules. Tests: `supabase/tests/shop_purchases.sql`
and concurrent cases in `tools/testing/test_database.py`.

Migration `20261105001000_shop_inventory.sql` adds the seven-item catalog and
owner inventory, preserves existing food stock, and retains Pet Care compatibility.
Apply it once after 009. See [SHOP_INVENTORY.md](../features/SHOP_INVENTORY.md) for prices,
RLS, trusted mutation rules and verification.

## Scope and migration order

Apply these files once, in filename order, through Supabase migrations as the
trusted migration owner (`postgres`). Each file is transactional:

1. `supabase/migrations/20261105000100_initial_schema.sql`: six tables, UUID
   keys, checks/indexes, Auth-account bootstrap, RLS and column privileges.
2. `supabase/migrations/20261105000200_reward_integrity.sql`: reward ledger
   triggers, authoritative RPCs, and function access restrictions.
3. `supabase/migrations/20261105000300_auth_account_readiness.sql`: idempotent
   bootstrap and authenticated own-account readiness RPC. Calls create only
   missing profile/wallet/cat rows and never reset balances or XP; see
   [authentication](../features/AUTHENTICATION.md). Authenticated users alone can execute
   this RPC; anonymous users cannot. Existing owner-scoped table RLS remains.
4. `supabase/migrations/20261105000400_quest_definitions.sql`: category and repeat
   type constraints, restricted column grants and visible-quest index. See
   [Quest CRUD](../features/QUESTS.md) for archive deletion and verification.
5. `supabase/migrations/20261105000500_quest_daily_progress.sql`: authenticated
   read-only daily reward count, using server UTC and existing owner RLS.
6. `supabase/migrations/20261105000600_focus_sessions.sql`: immutable execution
   snapshots, idempotent Start request keys and timed Focus restore/cancel/claim
   RPCs. See [Focus lifecycle and timing](../features/FOCUS.md).
7. `supabase/migrations/20261105000700_reward_claims.sql`: authenticated server
   reward quotes and strict duplicate-rejecting claim endpoint, reusing the
   atomic ledger engine. See [reward policy and verification](../features/REWARDS.md).
8. `supabase/migrations/20261105000800_pet_state.sql`: server elapsed-time stat
   reconciliation and idempotent care, preserving protected XP and existing pets.
   See [Pet state policy](../features/PET_STATE.md).
9. `supabase/migrations/20261105000900_pet_care.sql`: owner food inventory,
   atomic validated care/results and persistent timed sleep. Generated from
   central balance configuration; see [Pet care](../features/PET_CARE.md).

Migrations deliberately fail on an already-created schema instead of silently
masking drift. Reproducibility means applying the same ordered files to a fresh
database yields the same schema; Supabase's migration history prevents replay
against an already-migrated project. Existing Auth users are backfilled during
the first migration. Future Auth inserts create profile, one cat and zero-Coin
wallet atomically. User-controlled Auth metadata is not trusted for balances,
XP, roles, or bootstrap fields.

Mobile authentication is configured separately; cloud deployment, purchases,
pet-care mutations and additional gameplay remain outside this schema.

## Tables and ownership

| Table | Key and relationships | Responsibility |
| --- | --- | --- |
| profiles | UUID `id` references `auth.users.id` | Minimal display name and server timestamps |
| pets | UUID `id`, unique UUID `user_id` → profiles | One cat, name, nonnegative XP, 0–100 stats |
| quests | UUID `id`, UUID `user_id` → profiles | Owner's title/description, 5–90 minutes, archive flag |
| quest_sessions | UUID `id`; composite `(quest_id,user_id)` → quests | Immutable duration snapshot and authoritative start/end times |
| wallets | UUID `id`, unique UUID `user_id` → profiles | Nonnegative current Coins balance |
| wallet_transactions | UUID `id`, unique UUID session reference | Append-only reward receipts: Coins, XP, day/slot and resulting balance |

The composite session/quest FK prevents cross-user quest references. A second
composite FK on receipt `(quest_session_id,user_id,reward_day)` prevents owner
or day mismatches. Receipt owner FKs also require a corresponding wallet and pet,
preventing their deletion once reward history exists. Receipts are one per session, with five unique numbered slots
per owner/day. Foreign keys use RESTRICT: automatic user deletion must not erase
an audit trail or silently detach a wallet. Account deletion/data erasure needs
a separate, explicit administrative migration/workflow; it is not implemented.
Quests are archived instead of deleted so session history remains intact.

## RLS and access policy

RLS is enabled on all six public tables. Policies target `authenticated` and
derive the owner from `(select auth.uid())`. Anonymous access has no grants or
policies. Column grants form an additional boundary beyond RLS:

| Table | Authenticated read | Permitted direct mutation |
| --- | --- | --- |
| profiles | Own row | Update own `display_name` only |
| pets | Own row | Update own `name` only |
| quests | Own rows | Insert own quest; update title, description, duration, archived |
| quest_sessions | Own rows | None; use the session RPCs |
| wallets | Own row | None |
| wallet_transactions | Own rows | None |

Clients cannot change ownership, IDs, server timestamps, pet stats, Coins, XP,
reward days, reward counts, or claimed/cancelled states. No client INSERT/DELETE
is granted for profiles/pets/wallets; no client DELETE is granted for quests.
RLS `WITH CHECK` additionally prevents assigning a new quest to another user.

`service_role` is a trusted server role that bypasses RLS, but this schema grants
it SELECT only on these tables. It still cannot directly mint reward rows or
change balances through table privileges. The public RPCs are granted only to
`authenticated`; a backend invokes them with the verified caller's user JWT.
No service-role key is required by the mobile app or included in this repository.

Private trigger helpers live in the non-exposed `pawquest_private` schema, with
all client schema/function access revoked. All SECURITY DEFINER functions use
an empty `search_path`, schema-qualified object references, and trusted migration
ownership. Public RPCs reject missing identity and derive the actor from Auth;
none accepts a client-supplied user ID, amount, start timestamp or completion day.
The migration owner can administer schema/fixtures; owner access is outside the
untrusted-client boundary. Do not grant table-write privileges to client roles
in future migrations.

## Authoritative session lifecycle

### `start_quest_session(p_quest_id uuid)`

Locks the actor's wallet row, checks quest ownership/archive state, snapshots its
duration, and assigns server `clock_timestamp()` start/end instants. It marks
any expired active session completed, then rejects another unfinished session.
A partial unique index on `user_id WHERE status='active'` independently enforces
one active row. Quest edits after starting cannot shorten or enlarge a session.

### `cancel_quest_session(p_session_id uuid)`

Locks wallet then session and checks ownership. An unfinished active session can
be cancelled with a server timestamp. Cancellation retries return the same
cancelled row. Completed/claimed sessions cannot be cancelled; cancelled sessions
can never be rewarded. No Coins or XP are posted by cancellation.

### `claim_quest_reward(p_session_id uuid)`

This is the explicit Claim Reward operation. Locks wallet then session and checks
ownership. Expired `active` and `completed` sessions are eligible; early/cancelled
claims fail. There is no auto-credit just because a countdown ends. Returning an
existing receipt makes a retry idempotent, even if limits have since been reached.

Completion is determined by `ends_at`, so it persists while the app is backgrounded,
closed, or offline. No scheduler is required to establish elapsed time. An expired
row can still say `active` until start/claim processes it; clients must use the
authoritative end instant for countdown/eligibility, not just the status label.

## Reward tiers and cap policy

| Duration (minutes) | Tier Coins | Pet XP |
| --- | ---: | ---: |
| 5–14 | 10 | 5 |
| 15–29 | 20 | 10 |
| 30–44 | 35 | 20 |
| 45–59 | 50 | 30 |
| 60–74 | 65 | 40 |
| 75–90 | 80 | 50 |

The ledger BEFORE INSERT trigger derives the tier from the session snapshot,
ignores supplied monetary/day/slot/timestamp values, and serializes against all
other claims for the user through the wallet lock. It checks the day's ledger
count and sum, rejecting a sixth reward. Each successful claim consumes a slot.

**Cap decision:** credit `min(tier Coins, 250 − day's already-earned Coins)`;
always grant full tier XP. Thus five 80-Coin sessions pay **80, 80, 80, 10, 0**
Coins and **50 XP each**. The fifth is an XP-only rewarded session. A sixth
claim fails; uncredited Coins do not roll over to another day. This explicit
partial-credit policy supports the 250-Coin cap without requiring a tier-sized
remaining allowance. Daily earned Coins are not the same as current wallet balance.

The AFTER INSERT trigger updates wallet, pet XP and session claimed state in the
same database transaction. If any operation fails, none of the receipt, credit,
XP or status changes commits. UPDATE/DELETE of ledger rows raises an exception,
including for accidental privileged mutations. There is no debit/spending path
yet; a future purchase migration must preserve ledger reconciliation and locking.

## UTC and reward days

All instants use `timestamptz`: PostgreSQL stores absolute UTC instants; query
display may use a session timezone without changing storage or reward accounting.
The generated immutable `reward_day` is `(ends_at AT TIME ZONE 'UTC')::date`.
It is the **scheduled completion day**, not start day, claim day, device timezone,
profile timezone, or database connection timezone. It applies globally to all
users and cannot be edited by clients.

Example: start at 23:50 UTC for 25 minutes → completion at 00:15 UTC next day;
the reward belongs to that next day. A claim a week later still consumes that
original day. Changing the policy later requires an explicit accounting migration;
it must not silently reassign existing receipts. There is no DST ambiguity in UTC.

## Constraints, indexes and concurrency

- UUID PKs with database defaults; unique owner wallet/pet; owner-matched FKs.
- Session duration and exact elapsed interval checks; compatible cancellation/
  claim timestamps and statuses; nonnegative wallet/XP and bounded pet stats.
- Partial active-session uniqueness; immutable UTC reward day.
- Unique session receipt and `(user_id,reward_day,reward_slot)` with slot 1–5.
- Reward cap enforced by authoritative ledger trigger under wallet row lock;
  cross-row sums cannot be safely expressed as a plain PostgreSQL CHECK.
- Owner/time indexes support history and RLS; the slot unique index supports
  daily sums/counts. Foreign-key lookup columns are covered by indexes.

All reward/session RPCs use wallet → session lock order. Simultaneous claims for
one session return the same receipt; simultaneous different-session claims cannot
consume the same remaining quota. At stricter transaction isolation levels a
serialization failure should be retried as a whole transaction. Never implement
an application-side read-balance / write-balance reward path.

## Reproducible verification

### Real Supabase local instance

If the CLI is available, run `supabase init` once to generate local CLI config
(none is hardcoded here), then `supabase start` and `supabase db reset --local`.
The reset command recreates the local database and applies the migration files;
use it only against disposable local development data.
Run `supabase/tests/integrity.sql` via psql as postgres against that local DB.
It uses the real Supabase Auth schema and rolls back all test fixture data.
**Do not run `local_auth_shim.sql` on Supabase**: it is only for empty vanilla PG.

### Standalone PostgreSQL test cluster

`tools/testing/test_database.py` uses Python stdlib and local psql. It creates two uniquely
named disposable databases, applies the Auth shim and both migrations to each,
runs integrity/RLS tests, races independent connections, and drops only those
generated databases. Run it against a disposable local cluster, as postgres:

```powershell
# First use only; skip initdb when the test cluster already exists.
& "C:/Program Files/PostgreSQL/18/bin/initdb.exe" -D build/pawquest-db-test -A trust -U postgres --encoding=UTF8 --no-locale
& "C:/Program Files/PostgreSQL/18/bin/pg_ctl.exe" -D build/pawquest-db-test -l build/pawquest-db-test.log -o "-h 127.0.0.1 -p 55439" -w start
python tools/testing/test_database.py --pg-bin "C:/Program Files/PostgreSQL/18/bin" --port 55439
& "C:/Program Files/PostgreSQL/18/bin/pg_ctl.exe" -D build/pawquest-db-test -m fast -w stop
```

The validation cluster is already initialized, so skip `initdb` for subsequent
runs. It uses trust authentication and loopback binding solely for disposable
local tests; this is not a deployment configuration. Change the port if occupied.

The Auth shim models `auth.users`, Auth roles and the JWT subject helper. Tests
set role/JWT subject directly because they execute with trusted DB test access;
this is not a client-facing authentication endpoint. This runner does not test
GoTrue, PostgREST, JWT signature validation, or Supabase deployment.

Suite coverage: RLS and column denial, bootstrap, quest duration limits,
owner-matched foreign keys, one active session, premature/cancelled claims,
immutable duration snapshots, UTC-day behavior under Auckland connection TZ,
five/250 limits, partial and XP-only claims, idempotent receipts, rollback after
failed XP credit, ledger immutability, reconciliation, six-way duplicate claim,
six-way distinct claim, and simultaneous session starts.

`supabase/tests/verification.sql` contains read-only RLS, privilege, daily-cap,
receipt/status and balance/XP reconciliation queries for operational inspection.
Violation queries should return no rows. See `IMPLEMENTATION_LOG.md` for actual
execution results and environment details.

References: [Supabase RLS](https://supabase.com/docs/guides/database/postgres/row-level-security),
[secure database functions](https://supabase.com/docs/guides/database/functions),
[PostgreSQL row locking](https://www.postgresql.org/docs/current/explicit-locking.html).
