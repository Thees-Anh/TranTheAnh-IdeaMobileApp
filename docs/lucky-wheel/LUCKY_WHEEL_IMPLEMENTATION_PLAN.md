# Pet Lucky Wheel implementation plan

Updated 2026-11-10. **Task 02 backend and Task 03 Kotlin/Shop/Exchange/HTML integration are implemented locally.** Task 03 includes the renderer work originally planned under Task 04; remaining release work is hosted acceptance and approved rollout. See [LUCKY_WHEEL_TASK03_NOTES.md](LUCKY_WHEEL_TASK03_NOTES.md) and [LUCKY_WHEEL_API_CONTRACT.md](LUCKY_WHEEL_API_CONTRACT.md). The audit is a historical source snapshot. Item Case and Item Exchange are preserved; no hosted migration/activation was performed.

## Task 02: backend configuration, entitlement and atomic APIs

1. Implemented: remove level-based pet unlocks, grandfather pre-transition eligibility plus active pets, and grant default Cat. The legacy-item acquisition decision is deferred to the Shop redesign. Reuse `pet_species`, the account's existing `pets` row, `wallets`, `wallet_transactions`, `user_inventory`, `shard_balances` and `shard_ledger`. Add only versioned wheel configuration, immutable spin receipts/history, species entitlements, Wheel-exclusive ticket balance/ledger, and Pet redemption price/receipt/quote data that have no current equivalent. Keep published versions immutable and historical outcomes interpretable.
2. Proposed `user_pet_unlocks(user_id,species_code)` unique ownership: default `mochi_cat` for new and existing accounts, plus an explicitly approved backfill for existing accounts (see migration decision below). All grants are server-only. Update `pet_collection()` and `pet_select_species()` to consult persisted ownership under wallet-first locking, without level 5/10/15 grants or selection gates; preserve the same active pet/care state. A Wheel grant should unlock, not silently select/reset the pet.
3. Seed disabled, backend-configurable wheel version: 100 Coins or one explicitly selected Wheel Ticket. Store these sector codes/weights and reward metadata:

| Sector | HTML index | Basis points | Backend reward configuration |
| --- | ---: | ---: | --- |
| FOOD | 0 | 2500 | Existing basic_food UUID; quantity 1, implemented |
| COINS_50 | 1 | 2000 | +50 Coins |
| PET_SHARDS_5 | 2 | 1800 | +5 PET_SHARD |
| PET_SHARDS_10 | 3 | 1200 | +10 PET_SHARD |
| LUCKY_TICKET_1 | 4 | 1000 | +1 Wheel Ticket |
| PET_SHARDS_20 | 5 | 800 | +20 PET_SHARD |
| PET_SHARDS_50 | 6 | 500 | +50 PET_SHARD |
| PET_UNLOCK | 7 | 200 | Uniform selection among unowned rabbit/puppy/capybara; otherwise +50 PET_SHARD |

Validate eight unique sectors, positive weights totaling 10,000, valid published species/artwork, Food identity/capacity and separate payment types. Use the existing private cryptographic bounded-random helper from migration 026; neither outcome nor selected pet comes from the client. When one pet remains, it is selected with certainty conditional on PET_UNLOCK. Preserve PET_UNLOCK as the original sector when all are owned; actual reward is PET_SHARD and selected pet is null.

4. Extend **existing** ledger validation narrowly. `shard_ledger` needs owner/source-linked Wheel credit and Pet redemption reasons, PET_SHARD-only where appropriate; its existing Item receipt FKs cannot stand in for Pet/spin receipts. Reuse prepare/apply lock/balance patterns, not ADMIN_ADJUSTMENT as a gameplay shortcut. Preserve all existing Item/quest branches and RLS.
5. Decide Coins ledger representation before implementation: existing unique `(user_id,request_id)` prevents using the same key for separate spin debit and prize credit rows. **Implemented choice:** separate gross PAYMENT/REWARD legs linked to the spin receipt, with server-generated leg request UUIDs and unique spin/leg constraints. The original client key is reserved separately; it is not reused for both legs. The earlier net-posting suggestion is superseded. A Coins-paid COINS_50 spin costs 100 and rewards 50 (net -50); Ticket-paid COINS_50 is +50 without a Coins debit. If separate legs are selected instead, give them server-owned leg identities and uniqueness per spin/leg. Never disguise wheel credit as quest rewards or change quest reward limits/XP.
6. Tickets are spendable only by Wheel, never food inventory, Item Case, Item Exchange or a shard conversion. Deduct ticket payment and award any ticket prize atomically; a Ticket-paid ticket win has net zero tickets but still records a completed spin. Validate the full entry balance **before** drawing: at least the configured 100 Coins or one Ticket, even when the prize reduces the net debit. A prize cannot fund its own entry. Prevent negative balances and replayed credits.

### Proposed API contract

| Operation | Client inputs | Confirmed server output |
| --- | --- | --- |
| `lucky_wheel_config` | Banner identifier, if multiple banners are supported | Published version, 100-Coin configurable price, ticket cost, sector weights, rewards, Pet prices |
| `lucky_wheel_spin` | Banner ID, `COINS` or `TICKET`, request UUID | Spin/request/version IDs, original sector code, payment/costs, actual reward type and amounts, Food item/quantity or unlocked species, updated Coins/PET_SHARD/tickets, timestamp |
| Wheel history / ticket reads | Owner derived from session; bounded cursor/limit | Owner-only committed receipts and ticket balance/ledger |
| `pet_shard_quote` / `pet_shard_redeem` | Canonical species code and request UUID | Server price/expiry, then immutable Pet grant receipt and updated PET_SHARD balance |

Spin must derive auth.uid(), lock the existing wallet first, recover a matching committed key **before** current configuration/funding/draw checks, then validate explicit payment, lock ticket/shard/entitlement/inventory resources consistently, draw and settle everything in one PostgreSQL transaction. Different payment/banner with the same key is a conflict; never silently fall back from Tickets to Coins. Return ordinary no-charge errors distinctly from ambiguous failures. Rate-limit new operations, exempt recovery, and retain original response snapshots. Reject economic request-key collisions with Case, Shop and Exchange ledger records rather than reinterpreting an existing operation. UI/animation completion never authorizes money or pet grants.

Pet redemption prices are backend-configured per species: rabbit 50, puppy 70, capybara 90 PET_SHARD. Reuse migration 027's separate quote/confirm/redeem pattern, immutable price/version binding and confirmed-intent recovery. Preserve existing item RPC signatures and pending Item journals. One common Exchange UI is appropriate; Pet-specific source records/FKs and typed targets are necessary, not a second wallet/exchange product.

RLS: authenticated read-only configuration; owner-only balances, ownership and history; no client writes to authoritative rows or private helpers. Preserve minimum execute grants and empty search_path patterns. Coordinate wallet-first locking with Shop, Case, pet selection, XP changes and both exchanges. Before RNG, establish that every configured destination can accept its reward; do not turn a full-Food result into a free reroll.

## Task 03: Kotlin state and shared Shop/Exchange UI

- Add typed Wheel configuration/receipt/payment states and a Wheel repository/ViewModel using the **existing** Supabase client/auth and captured account-token transport. Reuse atomic account/project-scoped request persistence; include payment identity. Restore uncertain requests with the same key; never resubmit a new spin after timeout. Preserve original sector separately from actual prize.
- Extend `ShardRepository` balance/ledger access to PET_SHARD without changing Item behavior; its current getItemLedger is Item-only. Generalize the existing Exchange shell/coordinator to typed Item/Pet targets. Current PendingItemExchange uses UUID item IDs; version the journal safely or keep compatible typed journals, rather than casting species strings to UUIDs. Extend shared quote/confirm/retry UI and existing thumbnails/PetArtworkRegistry.
- Update `PetSpecies`/`PetUnlockRules`/ManagePet and collection UI to remove level-based pet unlocks and locked-until-level messaging entirely; selection uses authoritative ownership and installed art. XP/level progression and unrelated level rewards remain intact. Continue using `PetViewModel.loadCollection/selectSpecies`, with existing art and care-state preservation.
- Shop selector becomes **Food Shop / Item Case / Lucky Wheel / Shard Exchange**; Exchange subsections become **Pets / Accessories / Furniture**. Lift the existing Exchange panel out of Mystery Case into that single section, retaining its ViewModel and outstanding intents. Keep convenience links, history/drop rates and the existing bottom navigation/insets.
- Keep a complete metadata catalog for Case/Exchange names. Filter direct purchases to Food in UI **and backend**; reject new non-Food purchases in shop_purchase/prepare_purchase, after historical idempotent recovery. Do not deactivate item_catalog rows, modify Item Case probabilities or reset owned/equipped furniture/accessories. Gate release on the six legacy items' acquisition decision.
- On confirmed spin, refresh existing Pet Coins snapshot, Food Inventory, shard/ticket projections and pet collection. Clear retained Shop purchase balance snapshots as Case already does, and apply revision guards so stale reads cannot overwrite newer receipts. A pending Wheel/Exchange operation must block overlapping new submissions without blocking historical recovery.

## Task 04: deterministic HTML adapter, Android host and acceptance

Preserve the supplied studio input under design/ when implementation starts; prepare an offline production renderer at the existing Android asset path. Reuse SVG, colors, `renderWheelSVG`, angle/peg math, RAF ease-out and pet-card shuffle. Compile local Tailwind utilities using the existing asset tooling; reuse Quicksand, remove remote dependencies and studio/fake economy/crafting/forced controls. No Compose recreation of the wheel.

Proposed versioned `window.PawQuestWheel`:

- `initialize(displayConfig)`: approved sector/species mapping, labels, ownership display and motion/audio preferences; no credentials or financial methods.
- `play({transactionId,sectorCode,selectedPetCode,actualOutcome})`: confirmed display data only; code maps to the fixed sector index. Reuse current rotation-target math, preferably zero offset for exact center. Preserve normal monotonic easing and peg motion. No outcome/pet RNG or resource mutations.
- For PET_UNLOCK: stop at index 7, then run the existing pet-card lighting to the backend-selected pet using rabbit->bunny, puppy->dog, capybara->capy. If all-owned fallback, retain sector 7 and present the confirmed +50 PET_SHARD outcome without selecting an owned pet. The native receipt owns amounts.
- `skip()`, `reset()`, pause/dispose: track/cancel RAF, interval, timeout and audio handles. Skip completes both stages using the same receipt. Repeated play is idempotent; stale completion cannot release a new transaction. Emit stage notifications and one final visual completion only after both stages; native UI retains receipt/error fallback throughout.

Reuse Case's WebViewAssetLoader/security pattern with a Wheel-specific exact asset allowlist and listener/channel. Current Case allowlist and HOME must remain unchanged. Verify origin, main frame, message shape/version, playback nonce, spin ID, sector and stage; safely serialize JSON and escape labels used in SVG/innerHTML. Block remote navigation/content, file/content access, popups, storage and privileged JS interfaces. Handle unsupported messaging, renderer death, background/recreation and reduced motion with native result recovery. Audio failure must not block rewards.

### Acceptance tests

| Layer | Focused checks |
| --- | --- |
| SQL/economy | Exact sector boundaries/10,000 total, CSPRNG; both explicit payment modes; ticket award/net-zero case; correct Food/+50 Coins/PET_SHARD rewards; insufficient funds; all pet-ownership subsets; default Cat; all-owned +50 fallback retaining PET_UNLOCK; ownership migration/backfill and low-level selection; crossing levels 5/10/15 grants no pets |
| Concurrency/security | Same-key retries/conflicting payment, double taps, Wheel vs pet redemption/selection/Case/Shop, no duplicate pet, wallet/ticket/shard nonnegativity, ledger links, injected rollback, price changes, RLS cross-user/direct-write/unauthorized tests; reject forged sector, pet, price, amount and ownership |
| Kotlin/UI | Timeout and confirmed process recovery, account switch/late results, no automatic second spin/payment fallback, both currencies correctly labeled, one shared Exchange and pending-state preservation, legacy purchase recovery and metadata names |
| Real WebView | All 8 fixed sectors and all 3 selected pets; all-owned fallback; angle/edge/centering; two-stage final notification; skip/reset/replay; missing art/invalid codes; offline load, no privileges, spoofed callbacks, reduced motion/audio, renderer/process interruptions |
| Packaging/regression | Fresh APK ZIP includes exact Wheel HTML/CSS/font, pixel rendering, 9:16/19.5:9/20:9 and small phones/insets; Food purchase/feed, Case + duplicate conversion, Item Exchange, Inventory/equipment/room, Pet Home/selection, Coins, quests/rewards, auth and bottom navigation |

Use only disposable/local or authorized staging accounts for paid end-to-end spins and restart verification. Keep migration/application/banners staged; production rollout requires approval. Audit did not execute these tests or verify hosted schemas.

## Resolved choices and remaining Shop decision

1. Resolved: remove level unlocks, grandfather existing level-eligible and active species, and preserve Cat for everyone. Only future Wheel/Pet Exchange grants unlock new nondefault pets. Kotlin ownership selection gating is updated; legacy collection labels/placeholders are still Task 03 UI work.
2. Resolved: one real basic_food initially, backend-configured. Reject upfront before RNG if its reward quantity cannot fit; no conditional rerolls.
3. Decide acquisition of the six legacy catalog items before removing Coin purchase. Preferred product direction is continued access through the shared Exchange, which needs eligibility/pricing independent of blindly adding them to Case RNG; otherwise explicitly approve retirement of acquisition only.
4. Resolved: separate gross Coins and Ticket ledger legs; Tickets start at zero, nonnegative bigint balance with bounded configurable per-spin awards. No starter/free/admin-credit API is added; never copy the HTML's initial 15 or +5 test button. Operational stock caps/promotions need a later explicit feature decision.

Removal of level 5/10/15 pet unlocks, default Cat, new nondefault unlocks through Wheel/Pet Shard Exchange, required outcome percentages, explicit payment choice, per-species 50/70/90 prices, no duplicate pet, and all-owned +50 fallback are fixed requirements, not open decisions.
