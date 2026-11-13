# Pet care interactions

## Current Play rules (migration 018)

Pet Home shows **Hunger, Water and Energy**; Joy is no longer a visible meter.
Play is not capped by Happiness. Each successful Play costs **5 Energy,
2 Hunger and 3 Water**, all deducted atomically on the server. Playing requires
an awake pet with enough of all three needs; insufficient Energy/Hunger/Water
returns a specific rest/feed/drink explanation with no partial deductions.
There is no food-item, Coins or XP cost/reward for Play. Repeated request UUIDs
return the saved outcome without applying costs twice.

The hidden happiness field remains for existing bored icons/reminders and
backward-compatible data. Play can raise that internal mood even at its cap;
it never blocks another Play. No existing migration is rewritten.

`config/play-balance.json` is the active source for these three costs. Run:

```powershell
python tools/balance/generate_play_balance.py --check
```

It generates shared PlayBalance and forward migration
`20261106001800_play_costs.sql` from `supabase/templates/play.sql.in`.
Apply 018 once after 001–017, then rebuild/run Android. Later balance changes
require a new forward migration after deployment.

Latest changes: full-stat/empty-stock taps display explicit feedback; touching a
sleeping pet wakes it through the server while preserving elapsed recovery.
Apply migrations 016 and 017 after 001–015. See [feedback, waking and collection](PET_COLLECTION.md).

Water is added by migration 014 after existing migrations, with Drink +20,
decay 3/hour, no item/Coins cost and no XP. Full/sleeping pets cannot drink.
The server persists hydration and deduplicates care request UUIDs. See
[companion rules](COMPANION_REMINDERS.md) for installation and reminders.

Apply `20261105000900_pet_care.sql` once after migrations 001–008, then rebuild
Android. Existing pet stats/XP remain. Existing successful request IDs from 008
remain historical successes; replaying them never consumes food or reapplies care.

## Rules

`config/pet-care-balance.json` is the source for balance values. The generator
produces PetCareBalance in shared domain and migration 009 from its SQL template:

```powershell
python tools/balance/generate_pet_care_balance.py --check
```

Use the generator without --check when preparing changes **before deployment**.
After a migration is installed, balance changes require a new versioned migration;
do not rewrite/reapply installed migration 009. Kotlin gameplay rules and server
policy are generated from the same source, not chosen by composables or clients.

| Action | Server effect | Rejection conditions |
| --- | --- | --- |
| Feed | Consume 1 basic food, Hunger +15 (max 100) | Sleeping, already full, or no food |
| Play | Energy -5, Hunger -2, Water -3; hidden mood +15 (max 100) | Sleeping, Energy below 5, Hunger below 2 or Water below 3 |
| Sleep | Start rest; recover 2 Energy per whole minute | Already sleeping or Energy full |
| Pet Touch | Temporary happy reaction for 2500 ms; no permanent stat boost | Sleeping |

Each profile receives **3 basic foods once** via a profile trigger; existing
profiles receive the same one-time allocation at migration. Repeated login,
initialization or pet restoration does not refill inventory. No shop, purchase,
food crafting or client refill endpoint is included. Trusted administration may
stock food during development; mobile users cannot write inventory quantities.

## Atomicity, validation and results

`pet_state(action,requestUUID)` remains authenticated-only and derives ownership
from Auth. It locks wallet, pet and that owner's food row in this order, then
reconciles elapsed time and validates the action. Food debit, stat/sleep update
and durable request outcome commit in one transaction. A failed pet write rolls
back food and the request record as well. Owner RLS exposes only one's own stock;
clients have no INSERT/UPDATE/DELETE on inventory and cannot edit pet stats/XP.

The same owner/request key returns its original outcome and never reapplies the
action, including after app restarts. Reusing a key for a different action is an
error. Valid business rejections return a terminal structured result such as
no_food, not_enough_energy or pet_sleeping, without spending resources; they are
also remembered. A later new action gets a new UUID. Retrying a rejected no_food
request after restocking still returns the original rejection without spending.
Unknown actions/missing UUIDs are rejected structurally.

PetViewModel exposes lastActionResult (code/message, applied, foodConsumed),
current stock, sleep deadline and transient reaction. A terminal business result
clears pending intent; network ambiguity keeps the same UUID for explicit Retry.
Different actions stay blocked while an ambiguous request is unresolved. There
is no optimistic local inventory/stat update. Care grants no Coins or XP.

## Sleep, lifecycle and animation

Sleep records server start, deadline and starting Energy. Restoration derives
Energy from elapsed whole minutes, so polling cannot accelerate it and closing
the app does not interrupt it. Rest ends automatically at the scheduled deadline
when reconciled; maximum empty-to-full duration is 50 minutes under this balance.
Feed/Play/Touch and a second Sleep are rejected while sleeping.

Hunger/Happiness continue hourly decay. Energy decay pauses during sleep and
resumes from the sleep deadline with its own fractional-hour anchor. If the app
returns long after rest ended, post-rest inactivity is also applied; sleep does
not permanently freeze Energy at 100. Stats remain 0–100 and pets never die.

The shared ViewModel holds a short confirmed-action reaction. Pet Home maps
successful Feed/Play/Touch to EATING/PLAYING/HAPPY in presentation only, then
returns to IDLE after 2500 ms. SLEEPING follows persisted sleep state rather than
a temporary timer. Invalid requests do not trigger their requested animation.
SceneView is not imported by domain. Food count and friendly result text appear
on Pet Home; existing debug animation controls remain visual-only.
Android scene commands now carry confirmed request identity, with persisted
Sleep priority and single-player switching. Physical Touch uses collision hit
testing on cat nodes; see [animation integration](../rendering/ANIMATIONS.md).

Cold restore needs server/Auth connectivity. No background service or continuously
running sleep timer is required. Temporary reaction is intentionally not persisted;
pet state, sleep schedule, inventory and server request outcomes are persisted.

## Verification

```powershell
python tools/balance/generate_pet_care_balance.py --check
.\gradlew.bat :shared:testAndroidHostTest :androidApp:assembleDebug :androidApp:lintDebug
python tools/testing/test_database.py
```

Tests cover validation boundaries, gradual sleep, temporary Touch expiry,
terminal rejection handling, lost-response Feed retry, restoration, and SDK food/
result/sleep decoding. PostgreSQL tests cover no double consumption, request
replay after restock, energy gates, sleep restoration without waiting, no polling
acceleration, and injected rollback after a food debit. Concurrent tests cover
same-request retry and six distinct requests competing for one food. These are
local tests; hosted migration/actions and emulator animation playback were not
verified in this milestone.
