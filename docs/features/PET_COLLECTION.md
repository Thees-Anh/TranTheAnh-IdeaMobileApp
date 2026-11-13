# Pet feedback, waking and future companions

Apply **016_pet_wake then 017_pet_collection** once after migrations 001–015.
Rebuild Android afterward. No hosted migration is applied automatically by the
repository. All client configuration continues to use the public Supabase key.

## Care feedback

Full Hunger/Water/Energy and empty food no longer disable care buttons.
An action synchronizes with the server, then a visible dialog explains its
rejection without consuming inventory or playing a false success animation.
Only an in-flight operation temporarily disables the care buttons. Pet touch
during synchronization gives a wait/retry explanation and does not start a
conflicting request.

Migration 018 removes the visible Joy meter and its Play limit. Play consumes
Energy/Hunger/Water; see [current care rules](PET_CARE.md). Hidden mood remains
for icon/reminder compatibility and does not restrict play.

No food with zero Coins explains the Quest → Claim Reward → Shop flow and offers
**Earn Coins**. With Coins, it offers **Visit Shop**. Water remains free and does
not require a shop item. The Shop displays an empty-wallet explanation and a
Quest shortcut; the confirmation body scrolls on compact phones. Confirming an
unaffordable purchase returns the backend's visible insufficient-balance result.
Server price, transaction and idempotency protections are unchanged.

Inventory no longer announces an item was used before the server confirms care.
Confirmed care refreshes inventory; failures use the same app-level feedback dialog.

## Waking

The existing `pet_state('touch', request_uuid)` RPC now returns `woken` when the
pet is asleep. It reconciles whole elapsed sleep minutes, clears all three sleep
fields, and starts the Energy decay clock at wake time. It does not refill Energy,
grant XP/Coins or consume food. A confirmed wake produces HAPPY then IDLE.

Requests retain durable outcomes. Retrying one wake UUID cannot apply recovery
twice or cancel a later sleep. Other care during sleep explains that the user
should tap the pet first. Repeated Sleep cannot extend the ongoing rest.

## Collection architecture

The existing single pet row represents **shared account progression and care**.
`pet_species` defines published companion appearances with a code, display name,
unlock level and renderer key. The pet row saves its active species and renderer
under a composite foreign key. Switching preserves its ID, XP, Hunger, Joy,
Water, Energy, sleep, wallet, inventory and saved equipment/room. Each species does
not have separate stats. XP is server-owned and never reduced by normal gameplay,
so reaching a milestone keeps it available without client-controlled ownership grants.

`pet_collection()` derives unlock eligibility from authoritative XP. Selection
uses `pet_select_species(code)` with authenticated ownership, published-catalog and
level checks, wallet → pet lock order and atomic persistence. Repeated selection
is idempotent. Clients can read published catalog rows but cannot modify metadata,
active species, XP or stats directly. No public grant/unlock endpoint is added.

Inventory → **Pets** shows the selected companion, locked/unlocked published pets
and upcoming milestones. Mochi is the starter; migration 019 adds the supplied
**Puppy at Lv.5**. Other empty milestones indicate that no pet has been added yet;
they do not grant fictional animals or reuse cat art under a different name.
See [PUPPY.md](PUPPY.md) for the six supplied states and quest reactions.

## Adding a real pet later

1. Prepare its offline HTML/SVG illustration using the renderer contract below.
2. Add its renderer key/path to `PetArtworkRegistry` in shared domain metadata.
   Set `supportsEquipment=false` until its attachment art is ready. Saved equipment
   is preserved but not displayed on incompatible artwork.
3. Add a new versioned migration inserting a `pet_species` row. Set `unlock_level`
   to 5, 10, 15, 20, etc. Keep `published=false` while assets are unfinished.
4. Test all six animations, touch, compact portrait framing, equipment and restart.
   Release the client artwork before publishing the row.

Example metadata (replace with a real reviewed asset/key; do not run as-is):

```sql
insert into public.pet_species(code,name,unlock_level,renderer_key,published)
values ('new_species','New companion',5,'new_renderer',false);
```

The registry is an allowlist of bundled assets, never a remote URL or arbitrary
database file path. Old clients show **Update the app** and prevent choosing an
unknown renderer. If a saved renderer or bundled file is unavailable, Pet Home
shows a recoverable illustration error instead of silently drawing a different
animal; Inventory remains available to select a supported pet.

The illustration implements `pawquestRender({state,equipment,room})` and
`pawquestResize(width,height)`, takes IDLE/HAPPY/EATING/DRINKING/PLAYING/SLEEPING,
and emits `pawquest-pet://touch` from the animal hit area. Existing hardened WebView
settings block navigation/network access. Keep all dependencies inline, retain
the offline CSP, and use uniform scaling plus safe room/control bounds. Release
and recreate the native renderer when its bundled asset changes.

See [TEST_REPORT.md](../TEST_REPORT.md) for actual execution and remaining checks.
