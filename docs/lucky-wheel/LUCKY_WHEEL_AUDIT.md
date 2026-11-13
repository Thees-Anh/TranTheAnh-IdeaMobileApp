# Pet Lucky Wheel: focused read-only audit

Date: 2026-11-10. Evidence is the current source and existing debug APK, not hosted Supabase state. Read all 1,355 lines of `shared/src/androidMain/assets/gacha/lucky_wheel.html` (79,893 bytes; SHA-256 `5f0234ddcc4491038a4542f33ca72eb8386d4611af45000358c645e81db64950`). No application, SQL or animation changes; no build, device or backend tests run for this audit.

## Findings and reusable infrastructure

Paths below are relative to the project root; abbreviated Kotlin paths are under `shared/src/commonMain/kotlin/com/pawquest/`.

| Area | Verified current implementation | Integration implication |
| --- | --- | --- |
| Shop/navigation | `presentation/shop/ShopScreen.kt`, `ShopViewModel.kt`: Food, Accessories, Furniture and Mystery Case; horizontal selector. `presentation/navigation/Destination.kt` has five bottom destinations. | Replace the Shop subsection selector, preserve bottom navigation and Food purchase behavior. Do not add Wheel/PET to database `ItemCategory`. |
| Item Case | Migrations `20261110002400_item_case_shards.sql` through `20261110002600_secure_item_case_open.sql`; `ItemCaseViewModel.kt`, `ItemCaseAnimation.kt`, `SupabaseItemCaseRepositories.kt`. | Reuse transaction, receipt, recovery and visual-callback patterns. Its four-rarity/item-pool schema is not an eight-outcome wheel schema. Keep its implementation intact. |
| Coins | `wallets`, append-only `wallet_transactions`; migration 011 purchase triggers, migration 024 `ledger_shape`/case debit triggers. `PetRepository.restore()` supplies Coins; there is no separate generalized currency wallet repository. | Reuse wallet-first locking and existing wallet/projection. New wheel ledger source/validation is required; current CHECK branches only admit quest reward, Shop purchase and Item Case opening. |
| Food | Migration 010: `item_catalog`, `user_inventory`, private `adjust_inventory`; migration 019 renames basic food to Pet food. | Existing `basic_food`, UUID `e1000000-0000-0000-0000-000000000001`, is stackable up to 1,000,000 and used by care. Backend reward SKU/quantity must be explicit; HTML suggests one. |
| Shards | Migration 024: `shard_currencies`, `shard_balances(user_id,currency)`, `shard_ledger`; model `ItemCase.kt`; `ShardRepository.getBalances()` reads ITEM_SHARD and PET_SHARD. | No second shard wallet is needed. However ledger reasons only cover ITEM_DUPLICATE, ITEM_REDEMPTION and ADMIN_ADJUSTMENT; Item reasons require ITEM_SHARD and item-specific receipt links. Add typed wheel/Pet-redemption reasons and validation; do not disguise game rewards as admin adjustments. |
| Exchange | Migration 027 `item_shard_quote`/`item_shard_redeem`, private immutable quotes; `ItemExchangeViewModel.kt`, `ShardExchangePanel.kt`, `domain/model/ItemExchange.kt`. | Reuse the quote/explicit-confirm/durable-key UI flow and one Exchange surface. Current receipt/quote tables and models target catalog UUIDs and item-pool FKs, so they cannot redeem species codes unchanged. |
| Pet state | Migration 017 `pet_species`, `pets.species_code`/`renderer_key`, `pet_collection()` and `pet_select_species(text)`; migrations 019/022 publish the other three pets. | One mutable pet/care state per account, **no per-user pet ownership/entitlement table**. Existing “unlocked” is calculated from XP. A durable transactional grant mechanism must be added. |
| Pet client/art | `domain/model/PetCollection.kt`, `domain/usecase/ManagePet.kt`, `PetViewModel.kt`, `SupabasePetRepository.kt`. | `PetUnlockRules.canSelect` requires both unlocked and sufficient level, plus installed art. Update server and client gates together for low-level paid/wheel unlocks. Keep shared care/XP, active selection and shipped artwork. |
| Auth/DI/recovery | `App.kt`, `di/AppModules.kt`; Android `data/auth/AndroidAuthentication.kt`, `AndroidPendingItemCaseStore.kt`, `AndroidPendingItemExchangeStore.kt`. | Reuse one Supabase client, captured account-token transport, account-scoped ViewModels and atomic no-backup journals. Exchange journals already persist explicit confirmation. New Wheel payment/operation identity must be durable. |
| WebView | Android `presentation/gacha/MysteryCasePreview.kt`; `design/prototypes/gacha/item_case_controller.js`; `tools/assets/build_item_case.py`. | Reuse exact HTTPS asset allowlisting, origin/main-frame/nonce/transaction validation, JSON serialization, lifecycle/error/native-receipt fallback. Current host is Case-specific and allows only its HTML/CSS/font; it will reject Lucky Wheel. |

### Canonical species and ownership conflict

| Required pet | Actual species / renderer key | HTML key | Current unlock | Required Pet Shard price |
| --- | --- | --- | --- | ---: |
| Default Cat | `mochi_cat` / `prototype_cat` | No jackpot card | Level 1; default `pets` columns | Not redeemable |
| Rabbit | `rabbit` / `rabbit` | `bunny` | Level 15 | 50 |
| Dog | `puppy` / `puppy` | `dog` | Level 5 | 70 |
| Capybara | `capybara` / `capybara` | `capy` | Level 10 | 90 |

All four have shipped offline renderers in `PetArtworkRegistry`. Adding another `pets` row to “grant a pet” would be the wrong model: grant a species entitlement, then reuse selection of the existing care-state row. Ownership insertion can be atomic with wallet/shard operations once an owner/species unique entitlement exists.

**Confirmed requirement:** remove automatic pet unlocks at levels 5, 10 and 15. Cat remains the default; new Rabbit, Dog and Capybara unlocks come only from Lucky Wheel or Pet Shard Exchange. Level increases must neither grant these pets nor gate selection of an owned pet. **Migration decision still required:** whether to grandfather all previously level-eligible pets or only active pets; the existing model has no ownership history to distinguish previously selected pets from mere eligibility. Preserve the active pet during transition and document the approved backfill. Do not reset active pets, XP or care, and do not merely set unlock_level=1, which would give everyone all pets. `pet_collection`, `pet_select_species`, `PetUnlockRules`, collection labels and level-unlock tests must agree.

PET_SHARD's existing `proposed_pet_redemption_price=50` is only a reserved generic proposal, not an executable price catalog. It cannot represent 50/70/90. No spendable Wheel Ticket balance/ledger exists in the 27 migrations; `p_ticket` in migration 026 denotes a random-selection ticket, not a currency.

## Complete HTML behavior

Dependencies: Tailwind CDN script at line 7, Google Fonts CSS at line 8 (Quicksand and JetBrains Mono), inline CSS/JS/SVG and emoji; synthesized Web Audio, no external image/audio files. No Supabase, persistence or native completion bridge exists. The studio includes fake resources, +5 tickets, forced outcomes, test spins, shard crafting/reset and a source-code pane. Hidden/layout utilities depend on Tailwind; blocking network without compiling utilities breaks layout and modal visibility.

| Backend outcome required | HTML ID / zero-based index | Probability | Existing demo grant |
| --- | --- | ---: | --- |
| FOOD | `slot_food` / 0 | 25% | Local food +1 |
| COINS_50 | `slot_coins_50` / 1 | 20% | Local Coins +50 |
| PET_SHARDS_5 | `slot_shards_5` / 2 | 18% | Local shards +5 |
| PET_SHARDS_10 | `slot_shards_10` / 3 | 12% | Local shards +10 |
| LUCKY_TICKET_1 | `slot_ticket_1` / 4 | 10% | Local tickets +1 |
| PET_SHARDS_20 | `slot_shards_20` / 5 | 8% | Local shards +20 |
| PET_SHARDS_50 | `slot_shards_50` / 6 | 5% | Local shards +50 |
| PET_UNLOCK | `slot_unlock_pet` / 7 | 2% | Local random pet unlock / duplicate fallback |

Eight sectors are equal **45-degree** slices despite unequal odds; probabilities sum to 100%. Geometry is presentation, not probability.

- `window.onload` (1346): renders wheel SVG, probability table, pet SVG cards, fake resources and code viewer; no auto spin.
- `spinLuckyWheel(customConfig)` (903): spends one local ticket, disables buttons, calls `rollWeightedSlotIndex` (856), which uses Math.random or an unvalidated forced index. **No 100-Coin payment choice exists.**
- `renderWheelSVG` (782) fixes the first sector center at 12 o'clock. `getPointedSlotIndexFromAngle` (870) computes `floor((((360-normalizedRotation)%360+22.5)%360)/45)`.
- Stop target: `(360-index*45)%360`, plus an offset, minus the current modulo angle, wrapped positive; add 360 if delta<90, then six base rotations. Offset is [-16.5,+16.5) degrees normally, [-16,-12) for just-past-pin, or [-2,+2) in the “center” mode. All remain within +/-22.5 sector bounds.
- `requestAnimationFrame` interpolates rotation using `smoothDecelerationEaseOut(t)=1-(1-t)^4.2`, normally 5300–5800ms. `computePointerPinBendAngle` animates peg contact; tick audio follows 45-degree crossings. At the end, a 300ms timer calls `handleSlotReward` (1072).
- `handleSlotReward` mutates fake Food/Coins/Shards/Tickets, or shows `jackpot-pet-roulette-view` and calls `triggerRandom1Of3PetUnlock` (1112). The latter chooses an unowned local `bunny/dog/capy`, or chooses an owned one when all unlocked. It highlights cards for 14 ticks at 110ms, then sets `unlocked=true`; duplicate selection adds 50 local shards. Stage two is about 1.54s, beyond the wheel's advertised duration.
- `craftPetWithShards` (1184) spends **100** local shards and unlocks a **random** pet. This conflicts with selected-species prices 50/70/90. `resetUnlockedPets`, free tickets and force/test controls are demo-only. `closeWheelModalAndSpin` immediately starts another local paid spin; production repetition must require native explicit action.
- Existing buttons unlock before the jackpot shuffle finishes. RAF/interval handles are not exposed for disposal; closing the modal does not cancel the shuffle. No skip, reset/pause contract, reduced-motion handling, transaction correlation or two-stage native finish notification exists.

Predetermined sectors are geometrically supported through forceSlot, but calling that function unchanged still charges fake tickets and selects/grants pets locally. Adapt visual execution, not these economic handlers. Keep sector 8 for PET_UNLOCK even when the backend actual outcome is +50 PET_SHARD with `selectedPetCode=null`; do not fake a duplicate pet win.

## Packaging and Shop redesign risks

The file is in the correct Android asset source location. `shared/build.gradle.kts` configures Android assets/resources and WebKit; portrait is set in the Android manifest. **The inspected existing APK `androidApp/build/outputs/apk/debug/androidApp-debug.apk` does not contain lucky_wheel.html**; its gacha entries are Case HTML/CSS/font only. Fresh-build packaging and device loading remain unverified, not a confirmed source-set defect.

1. Target subsections should be Food Shop / Item Case / Lucky Wheel / Shard Exchange, with Pets / Accessories / Furniture inside Exchange. Currently Exchange is a dialog reached through Mystery Case/Shard Wallet; lift it into one shared destination without recreating its ViewModel or dropping pending intents. `ItemCategory` remains the catalog classification; use separate UI section state.
2. Hiding direct Accessories/Furniture buttons is insufficient: migration 024's current `shop_purchase` and migration 011's `prepare_purchase` still allow them. Future server policy must reject **new** non-Food Coin purchases, while recovering already committed historical purchase keys before applying the new restriction.
3. **Do not deactivate decor catalog rows or zero their prices to remove Coin purchasing.** Case/Exchange require active, identity-consistent catalog entries; deactivation breaks rewards, and zero pricing could create free purchases. Keep catalog metadata available: Case and Exchange currently use `ShopState.catalog` for names, so blindly making `shop_catalog` Food-only would remove their name mapping.
4. Six active legacy items (`hat`, `glasses`, `collar`, `bed`, `lamp`, `carpet`) are outside the current 20-item Case pool. Current item redemption is also bound to that pool. Removing Coin buying strands their future acquisition. Decide whether to add exchange-only eligibility/configuration or explicitly retire acquisition while preserving ownership/equipment. Do not silently expand the Case RNG pool or invent rarities/artwork.
5. Existing shard UI/receipt models are Item-specific: ItemCategory, catalog UUID, ItemCase rarity/configuration and ITEM_SHARD labels/ledger filtering. Reuse one Exchange shell/coordinator, but add typed Pet targets, server price data and separate Pet receipt validation. Never route species codes through the existing item UUID endpoint.

See [LUCKY_WHEEL_IMPLEMENTATION_PLAN.md](LUCKY_WHEEL_IMPLEMENTATION_PLAN.md) for proposed APIs, changes and Tasks 02–04.
