# Lucky Wheel backend — Task 02

Implemented by [migration 028](../../supabase/migrations/20261110002800_lucky_wheel.sql), after migrations 001–027. No hosted database was changed. The published version is seeded with **enabled=false**; deployment and subsequent activation require approval. No Wheel UI, HTML changes or starter/free tickets are included.

## Authenticated RPCs

Use the existing Supabase client/session and `POST /rest/v1/rpc/<name>`. The verified JWT supplies `auth.uid()`; no endpoint accepts another user's ID. Invalid UUID syntax/unknown named parameters are rejected by PostgreSQL/PostgREST. Never send service credentials to Android or JavaScript.

| RPC | JSON inputs | Result |
| --- | --- | --- |
| `lucky_wheel_config` | `p_banner_id` UUID | Current published configuration, enabled flag, version, price, ticket cost, sectors/weights/reward amounts/Food UUID and fallback shards; null if unavailable |
| `lucky_wheel_balances` | `{}` | `{coins,tickets,pet_shards}`; null for uninitialized account; missing Ticket/PET balance rows mean zero |
| `lucky_wheel_spin` | `p_banner_id`, `p_payment_method` (`COINS` or `TICKET`), `p_request_id` UUID | `status=spun` plus immutable receipt, or rejection status |
| `lucky_wheel_history` | Optional `p_before` timestamp **and** `p_before_id` UUID together; `p_limit` default 20, clamped 1–50 | Owner-only receipt array, newest `(created_at,id)` first; pass both fields from the last receipt for next page |
| `pet_collection` | `{}` | Existing collection API, now reporting persisted ownership in `unlocked`; level is informational |
| `pet_shard_catalog` | `{}` | Current species/version/prices/name/renderer/owned array; Cat excluded |
| `pet_shard_quote` | `p_species_code`, `p_request_id` UUID | `status=quoted`, immutable quote `{request_id,version_id,species_code,price,expires_at}`, or original redeemed receipt/rejection |
| `pet_shard_redeem` | Same species/key as explicit confirmed quote | `status=redeemed`, receipt with transaction/request/version/species, shards spent, PET balance after and timestamp |

Banner UUID: `e2800000-0000-0000-0000-000000000001`; initial version UUID: `e2800000-0000-0000-0000-000000000002`. Canonical pet identifiers are **rabbit**, **puppy** (Dog), **capybara**; HTML aliases bunny/dog/capy are display mappings only.

Initial probabilities are integer basis points: FOOD 2500, COINS_50 2000, PET_SHARDS_5 1800, PET_SHARDS_10 1200, LUCKY_TICKET_1 1000, PET_SHARDS_20 800, PET_SHARDS_50 500, PET_UNLOCK 200. Exactly eight sectors and 10,000 total are required before publication. `probability_bps` equals weight; divide by 100 for percentage display. Amounts, price and all-owned replacement are configured on immutable versions; change them by publishing a new version. Sector names identify animation positions even if a future configured amount changes.

## Spin contract

```json
{
  "p_banner_id": "e2800000-0000-0000-0000-000000000001",
  "p_payment_method": "COINS",
  "p_request_id": "29f1888a-5fe4-44ce-bb34-4b5e04d3b724"
}
```

Successful response contains `status: "spun"` and `receipt` with:

- `transaction_id` (also `id`), `request_id`, `banner_id`, `version_id`, `configuration_version`, `created_at`.
- `original_sector_id` (also `sector_id`), `payment_method`, `coins_spent`, `tickets_spent`.
- `actual_reward_type`: `FOOD`, `COINS`, `PET_SHARDS`, `TICKET`, `PET_UNLOCKED` or `PET_UNLOCK_CONVERTED_TO_SHARDS`.
- `food_item_id` nullable, `food_quantity`, `selected_pet_id` nullable, `pet_shards_earned`, `tickets_earned`, `coins_earned`.
- `coins_balance_after`, `pet_shards_balance_after`, `tickets_balance_after`.

Receipts also include the authenticated `user_id`; it is never accepted as mutation input. Balances in recovered receipts are historical snapshots, not a fresh wallet query. Read `lucky_wheel_balances` after recovery for current totals; reconcile newer receipts/read revisions before updating shared UI state. Kotlin decoding should ignore additional JSON fields.

For a Pet grant, `original_sector_id=PET_UNLOCK`, `actual_reward_type=PET_UNLOCKED`, `selected_pet_id` is the exact canonical species and shard award is zero. Selection is uniform over the remaining unowned Rabbit/Dog/Capybara. If all are owned, sector remains PET_UNLOCK, actual type is PET_UNLOCK_CONVERTED_TO_SHARDS, selected pet is null and `pet_shards_earned=50` in the initial version. The HTML must stop on sector 7 in both cases. A grant never automatically changes the active pet or care/XP state.

FOOD initially grants **one existing basic_food**, UUID `e1000000-0000-0000-0000-000000000001`. Its active FOOD identity and inventory capacity are checked **before drawing**. If Food cannot fit, the entire spin is rejected without payment or draw, even if another sector could have won. No conditional rerolls or fabricated replacement items.

## Payment, ledgers and recovery

- Entry requires the full configured 100 Coins or exactly one Ticket **before** RNG. No payment fallback; a prize cannot fund entry. A Ticket-paid Ticket win records debit -1 and credit +1 with zero net change. Tickets have no Item Case/Shop/Exchange spending endpoint.
- Coins reuse `wallet_transactions`: separate `lucky_wheel` PAYMENT and REWARD legs, unique `(lucky_wheel_spin_id,wheel_leg)`, server-generated leg request UUIDs. The original client key is reserved in private `wheel_requests` and the immutable spin. A Coin-paid COINS_50 result records -100 and +50 (net -50); Ticket-paid COINS_50 records only +50 Coins.
- PET awards/redeems reuse **the existing** `shard_balances`/`shard_ledger`, with `WHEEL_REWARD`/`PET_REDEMPTION` reasons and validated source links. ITEM_SHARD logic and existing Item redemption signatures remain unchanged. No currency conversion is exposed.
- Each successful operation locks the existing wallet first, then settles payment, reward, balances, linked ledgers, ownership/inventory and history in one PostgreSQL transaction. Any unexpected failure rolls back the entire mutation. Deferred receipt checks reject incomplete settlement.
- Replay matching user/key/banner/payment returns the original spin before current balance, throttle, banner availability or randomness checks. Pet replay likewise precedes current price/ownership/expiry checks. Conflicting parameters/operation return request_conflict. Legacy Shop/Case/Item ledger reuse of a reserved key is blocked at posting, even where an old RPC surfaces a SQL conflict instead of JSON.
- A timeout/connection loss is **uncertain**, not a failed purchase. Persist account/project, original key, banner and payment before dispatch; recover with the same request. Never silently create a new spin or switch payment. Confirmation of another spin is a separate user action.
- New spins/quotes share the existing private Case request limiter (default 30 attempts/60 seconds); original receipt recovery is exempt. Lock wait limit is 5 seconds (`busy`). Unknown SQL/transport errors require same-key recovery.

Ordinary JSON rejections: `invalid_request`, `account_unavailable`, `request_conflict`, `rate_limited`, `busy`, `banner_unavailable`, `configuration_unavailable`, `insufficient_coins`, `insufficient_tickets`, `inventory_full`; Pet exchange additionally `pet_unavailable`, `already_owned`, `insufficient_shards`, `quote_required`, `quote_expired`, `price_changed`. Rejections carry no receipt. Missing auth raises SQLSTATE 42501; malformed inputs and unexpected faults are errors, never fake success.

## Pet ownership and shared Exchange

`user_pet_unlocks(user_id,species_code)` is unique, server-owned and owner-readable. Cat is granted on account initialization. Migration cutover preserves already level-eligible and active species as LEGACY entitlements. **Subsequent level 5/10/15 transitions grant no pets**. Pet selection checks ownership, not XP; Kotlin's selection rule is updated accordingly. Legacy unlock_level metadata stays for compatibility, not authorization. Active species, XP, care, equipment and inventory are preserved.

Pet prices are versioned at rabbit=50, puppy=70, capybara=90 PET_SHARD. Cat is free/default and rejected from redemption. Quote does not spend/grant; it expires after 10 minutes. Explicit confirmation calls redeem with the same species/key. Changed version/price requires a new quote and explicit confirmation; never automatically confirm a replacement. Already-owned species are rejected; Wheel and redemption serialize under the same wallet lock. The Exchange remains one product/UI; species-specific receipts/FKs are necessary because existing Item receipts require catalog UUIDs. Pet Exchange remains available independently of the paid Wheel banner's enabled flag, matching Item Exchange behavior.

## Security and client follow-up

All new tables have RLS; authenticated users can only read their own tickets/ledger/spins/ownership/redemptions. Configuration and prices are exposed by authenticated read RPCs. No ordinary-client writes or private-helper execution; no RNG override parameter/test hook exists in migration 028. Production entropy reuses 026's private rejection-sampled cryptographic helper. Published configuration, economic receipts and ledgers are immutable. No service credentials or random seeds are returned.

Task 03 now implements these repository contracts with captured-session transport, expected-owner checks before dispatch and durable pending journals, extends the shared Exchange UI, refreshes Coins/Food/PET/tickets/collection and removes old level-unlock labels/placeholders. Its renderer controls the original HTML with confirmed sector/species only; animation callbacks cannot authorize grants. See [Task 03 notes](LUCKY_WHEEL_TASK03_NOTES.md) for files, verification and deployment prerequisites. The SQL API itself is unchanged.

## Executed verification

- `python tools/testing/test_lucky_wheel_database.py`: disposable local PostgreSQL replay/backfill, deterministic eight-sector tests, exact 10,000-position boundaries, both payments/gross ledgers, pet price/grant/fallback/low-level selection, rollback, RLS/auth/forged input, same-key and competing-spin/redemption concurrency. Deterministic RNG exists only in disposable test fixtures, never production migrations.
- `python tools/testing/test_database.py`: existing full SQL/RLS suite over two fresh replays plus economy/quest/care/inventory concurrency; legacy level-unlock tests now assert persisted ownership.
- `:shared:testAndroidHostTest` filtered to LuckyWheelTest, NewCompanionsTest, PetFeedbackCollectionTest: **12 tests passed**; `:shared:compileAndroidMain`: **passed**.
- Hosted Supabase/PostgREST, emulator/UI/HTML and production rollout: **Not Tested / not performed**. SQL tests use the existing local auth shim, not hosted JWT verification. Ticket stocks begin at zero; no admin-credit RPC or free-ticket offer was added.

Files changed for this task:

- Backend: `supabase/migrations/20261110002800_lucky_wheel.sql` (new).
- Kotlin: `domain/model/LuckyWheel.kt`, `domain/repository/LuckyWheelRepository.kt` (new); `domain/model/PetCollection.kt` (ownership selection rule).
- Tests: new `supabase/tests/lucky_wheel.sql`, `tools/testing/test_lucky_wheel_database.py`, `shared/src/commonTest/kotlin/com/pawquest/LuckyWheelTest.kt`; updated `supabase/tests/pet_collection_wake.sql`, `puppy.sql`, `new_companions.sql`, `tools/testing/test_database.py`, and common tests `NewCompanionsTest.kt`, `PetFeedbackCollectionTest.kt` for the deliberate ownership transition.
- Documentation: this contract, `LUCKY_WHEEL_IMPLEMENTATION_PLAN.md`, `docs/IMPLEMENTATION_LOG.md`. Other pre-existing working-tree changes belong to earlier tasks and were preserved.

## Pet-care gate (2026-11-12)

Draws are refused with status `pet_needs_care` (`need`: hungry, thirsty or
hungry_thirsty) while Hunger or Water is ≤25 after decay, before throttle,
charge or draw; committed requests still replay their receipt. The Shop shows
a card with Feed/Drink and disables the draw buttons. Migration:
`20261112003300_gacha_pet_care_gate.sql`.
