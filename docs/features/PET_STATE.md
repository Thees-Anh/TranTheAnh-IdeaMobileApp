# Shared pet state

Apply `20261105000800_pet_state.sql` once after migrations 001–007, then rebuild
Android. The migration preserves existing pet IDs, names, XP and stats. Existing
pets begin elapsed-time accounting at migration time; no retroactive inactivity
penalty is charged for time before this policy existed.

Migration 009 supersedes the free/immediate care policy below: Feed consumes
food, Sleep recovers over time, Touch is temporary-only and actions validate
sleep/energy/stock. See [current care rules](PET_CARE.md). The current app requires
migration 009's inventory response; 008 alone is not the current care protocol.

## Models and progression

`Pet`, `PetStats`, `PetProgress` and `PetCareAction` live in commonMain domain.
They import no Android, Compose, SceneView or Filament APIs. PetRepository is the
persistence contract; ManagePet is the shared use case; PetViewModel exposes a
read-only StateFlow. Koin injects SupabasePetRepository using the authenticated
client already shared by Auth/quests/rewards.

- Hunger is a fullness meter: 100 means fed, 0 means hungry.
- Hunger/Happiness/Energy are integers in 0–100. Domain constructors and database
  checks reject invalid states; care/time operations clamp into this range.
- Total XP is a nonnegative Long, persisted by the authoritative reward engine.
- Level starts at 1; every 100 total XP adds one level. XP within level is total
  XP modulo 100; XP to next level is 100 minus that remainder. Calculations stay
  safe even at Long.MAX_VALUE. Level is derived, not a client-writable column.
- Zero stats never delete the pet, reduce XP/level, block rewards or create a
  death state. Feed, Play, Sleep and Touch can recover stats after inactivity.

## Server time and care policy

The authenticated `pet_state()` RPC locks wallet then pet (matching reward lock
order), applies elapsed **whole hours** and persists a snapshot. Per hour Hunger
falls by 2, Happiness by 1 and Energy by 1. Only the consumed whole hours advance
stats_updated_at, preserving fractional time. Frequent polling/care/name changes
cannot reset the decay clock. Long inactivity clamps safely at zero. Device
time is never an input; UTC instants/elapsed seconds avoid timezone/daylight
saving differences. A server clock rollback produces no negative decay.

Basic care reuses the existing Pet Home demo magnitudes as the persisted policy:

| Action | Effect, after elapsed-time reconciliation |
| --- | --- |
| Feed | Hunger +15 |
| Play | Happiness +15, Energy -5 |
| Sleep | Energy +20 |
| Touch | Happiness +5 |

Care is currently free and grants **no XP or Coins**. No spending, inventory,
cooldowns or reward calculation were introduced. These effects are server-owned;
the pure domain functions document/test the same rules and are not write bypasses.

Care sends only action and request UUID. A private owner/request table prevents
concurrent or delayed replays from applying the same care twice. Reusing a key
for a different action is rejected. The ledger/XP grants remain unchanged; clients
can neither directly write pet stats/XP nor execute private helpers. An ambiguous
care operation keeps its UUID for explicit Retry and blocks a different care
until resolved. Request intent is not a durable offline queue; after process
restart the client simply restores the already-persisted state.

## Lifecycle and UI binding

PetViewModel restores on creation/resume, polls each minute only while resumed,
and refreshes after a successful reward claim. Polling stops on pause/disposal;
elapsed server time still applies on the next read. A refresh requested during
care is queued, so reward-earned XP/wallet changes are not lost in a busy-state
race. Refreshes cannot hide an unresolved care error or discard its retry key.

Pet Home reads through a presentation-only PetController CompositionLocal. Its
local mutable stat counters are removed; stats, level, XP and wallet badge come
from server snapshots. Rendering/animation callbacks remain presentation concerns.
Existing design previews retain static sample values without a controller.
Unknown initial runtime state shows loading/retry rather than fabricated stats;
offline errors retain the last snapshot and do not optimistically grant care.
Cold offline restoration still needs Auth/server connectivity. Other design
showcase screens are not converted into complete features by this milestone.

## Verification

```powershell
.\gradlew.bat :shared:testAndroidHostTest :androidApp:assembleDebug :androidApp:lintDebug
python tools/testing/test_database.py
```

Host tests exercise all care boundary values, long inactivity, negative inputs,
XP thresholds/Long overflow, restoration across ViewModels/repository instances,
offline care identity, busy/queued refreshes, foreground-only polling, and SDK
ID/action-only payloads. Domain source inspection finds no rendering/platform
imports. PostgreSQL tests replay all migrations twice and verify fractional-hour
accounting, clamped recovery after long inactivity, owner isolation, direct-write
denial, XP earned via real reward claims, and six simultaneous Feed retries with
one effect and zero XP grant. These are local database/SDK tests, not hosted
Supabase or emulator end-to-end verification for this milestone.
