# Item Case and Shard System — Step 02

Current follow-up: migration 027 and the Kotlin Exchange flow are implemented and tested locally; see [ITEM_CASE_FINAL_TEST_REPORT.md](ITEM_CASE_FINAL_TEST_REPORT.md). Production migration/activation was not performed. Earlier Step 02 scope notes below are historical.
Step 02 foundation implemented on 2026-11-10. Step 04 now adds shared Kotlin repositories, a recovery-safe ViewModel and durable Android intent storage; see [ITEM_CASE_KOTLIN_INTEGRATION.md](ITEM_CASE_KOTLIN_INTEGRATION.md). The user subsequently reported applying migrations 024/025. Step 03 now adds a secure public opening/recovery RPC in migration 026; see [ITEM_CASE_API_CONTRACT.md](ITEM_CASE_API_CONTRACT.md). Migration 026 is tested locally only. No redemption endpoint/UI, Pet Lucky Wheel or HTML change is included.

## Decisions based on the real catalog

The HTML contains 24 distinct visual IDs, but only 20 resolve to actual database items from `20261109002300_decor_catalog.sql`. Its 65 moving reel cards are repeated visual entries. No database items are created merely because they appear in the animation.

The initial combined Accessories/Furniture banner uses those 20 existing UUIDs. Rarity is version-specific reward metadata: the original `item_catalog` has no rarity column. Each of the four tiers has actual active, slot-mapped catalog items, validated when publishing configuration. `item_case_pool` references the original catalog and snapshots code, asset ID, category and max quantity; it does not replace it.

Excluded pending catalog/slot/production-artwork support: `acc-sakura-kimono`, `acc-celestial-halo`, `furn-sakura-fountain`, `furn-magic-throne`. No UUIDs, slot assignments or production art are invented for them. Legacy catalog items (`hat`, `glasses`, `bed`, etc.) remain purchasable and owned as before but are outside this initial visual pool.

## Seeded configuration and exact odds

Banner UUID: `e2400000-0000-0000-0000-000000000001`, code `item_case`. Version 1 UUID: `e2400000-0000-0000-0000-000000000002`. Price: **100 Coins**. Version is published, but banner `enabled=false`; this does not expose a chargeable action.

| Tier | Basis points | Actual items | Weight per item | Effective chance per item | Duplicate Item Shards | Redemption Item Shards |
| --- | ---: | ---: | ---: | --- | ---: | ---: |
| Common | 5500 | 6 | 1 | 55/6% (about 9.166667%) | 5 | 30 |
| Rare | 3000 | 6 | 1 | 5% | 10 | 60 |
| Epic | 1200 | 5 | 1 | 2.4% | 20 | 120 |
| Legendary | 300 | 3 | 1 | 1% | 40 | 240 |

The tier odds retain 55/30/12/3%. Unlike the demo's six-per-tier pool, the supported catalog has five Epic and three Legendary entries, so each remaining item's effective chance differs. Ownership does not remove rewards or change these probabilities: duplicates convert instead of rerolling. Fully collected users receive Item Shards on every result.

For item i in tier t:

`P(i) = (tier_probability_bps / 10000) × (item_weight / sum(weights in tier t))`

`item_case_probabilities` exposes the numerator, denominator and numeric fraction (0–1). Exact rational fields prevent rounding from obscuring true odds; UI percentages should multiply the fraction by 100 and clearly round only for display. Weights are strictly positive integers, not percentages. A publication requires exactly four distinct allowed tiers, a total of 10,000 basis points, at least one item per tier and valid catalog/slot references. Negative/zero probabilities, weights and shard prices/rewards are rejected.

### Actual item mapping

Every UUID below has prefix `e2300000-0000-0000-0000-`, followed by the exact 12-digit suffix shown. These are explicit migration-023 references, not generated IDs for new items.

| Asset ID / HTML ID | Catalog code | UUID suffix | Tier |
| --- | --- | --- | --- |
| acc-sprout | acc_sprout | 000000000003 | Common |
| acc-bowtie | acc_bowtie | 000000000007 | Common |
| acc-scarf | acc_scarf | 000000000008 | Common |
| furn-plant | furn_plant | 000000000017 | Common |
| furn-bookshelf | furn_bookshelf | 000000000014 | Common |
| furn-lights | furn_lights | 000000000013 | Common |
| acc-glasses | acc_glasses | 000000000005 | Rare |
| acc-backpack | acc_backpack | 000000000010 | Rare |
| acc-monocle | acc_monocle | 000000000006 | Rare |
| furn-clock | furn_clock | 000000000012 | Rare |
| furn-tea-table | furn_tea_table | 000000000018 | Rare |
| furn-lamp | furn_lamp | 000000000020 | Rare |
| acc-wizard-hat | acc_wizard_hat | 000000000002 | Epic |
| acc-headphones | acc_headphones | 000000000004 | Epic |
| furn-cat-tree | furn_cat_tree | 000000000015 | Epic |
| furn-gramophone | furn_gramophone | 000000000019 | Epic |
| furn-window | furn_window | 000000000011 | Epic |
| acc-crown | acc_crown | 000000000001 | Legendary |
| acc-wings | acc_wings | 000000000009 | Legendary |
| furn-bed | furn_bed | 000000000016 | Legendary |

## Versioning and configuration administration

Create a draft `item_case_versions` row, then its four tier rules and pool entries. Publish by setting `published_at`; the trigger validates the entire configuration and stamps server time. Only then can `item_case_banners.current_version` point to it. Draft configuration is hidden by RLS. All published version rows, tier rules and pool entries become immutable, including price, rarity, duplicate reward, redemption price, weight and snapshot identity. Rebalance by publishing a new version and switching the banner pointer; never rewrite historical rows.

Draft edits and publication lock the version row to serialize them. Configuration changes require trusted database administration/migrations; mobile and service roles receive no write grants. No admin UI is added.

Catalog name/localization and direct-shop price remain in the existing catalog. They do not determine case price. A changed/inactive pool catalog member makes private settlement reject the whole pool rather than quietly filtering items and changing odds; publish an explicit new pool version. Historical odds and identities remain readable regardless of the current banner pointer. No hosted deployment/schema state is assumed from local SQL.

## Database structures and reuse

| Structure | Purpose and integrity |
| --- | --- |
| Existing `item_catalog`, `user_inventory` | Canonical item IDs and ownership/quantity. Preserved without schema or data changes |
| Existing `wallets`, `wallet_transactions` | Canonical Coins and append-only coin history. New `item_case_opening` ledger branch links to the opening; original shop/reward branches remain |
| `item_case_banners` | Configurable code, enabled flag and current version; disabled by default |
| `item_case_versions` | UUID, banner/version uniqueness, server price, publication timestamp |
| `item_case_tiers` | Version/rarity PK, tier probability and duplicate/redemption Item Shard prices |
| `item_case_pool` | Version/item PK, catalog FK, tier FK, positive weight, redeemable flag, item identity/quantity snapshot |
| `item_case_probabilities` | Security-invoker view of exact per-item odds and tier economy values |
| `item_shard_redemption_catalog` | Versioned redeemable items, explicit ITEM_SHARD currency and tier price. Discovery data, not an executable offer endpoint |
| `item_case_openings` | Immutable opening receipts, owner/request uniqueness, original version/item/outcome, preexisting quantity, grant/conversion, coin cost/balance, coin and optional shard ledger links |
| `shard_currencies` | Two allowed identities only. Pet's proposed 50-shard redemption value is non-executable metadata |
| `shard_balances` | PK `(user_id,currency)`, nonnegative bigint. Missing row means zero; no starter shard grant/backfill |
| `shard_ledger` | Append-only postings, nonzero delta, nonnegative resulting balance, unique owner/request and one posting per source opening/redemption. Reason/source/currency shape checks |
| `item_shard_redemptions` | Reserved immutable Item Shard receipt history: owner/request, version/item, price, grant quantity, ledger link. Pricing/eligibility guard and deferred receipt-link validation; no execution API |

No generalized multi-currency wallet existed: `wallets.coins` is intentionally preserved. Shards use the smallest separate currency-keyed balance/ledger pair, not another Coins system. User history indexes lead with user ID (plus currency for shard ledger); source IDs and catalog references have indexes or supporting unique indexes. UUID FKs preserve historical references; no destructive cascade or starter-stock refill is introduced.

## Currency separation and duplicate ownership policy

`ITEM_SHARD` and `PET_SHARD` have separate balance rows, enum/domain values, and ledger currency keys. An Item Case duplicate or Item redemption posting is constrained to ITEM_SHARD. There is no conversion/exchange endpoint, and no Item Case path can grant Pet Shards. A trusted `ADMIN_ADJUSTMENT` posting can repair either currency with a required note; it is not exposed to clients/service roles and must not be used as an application reward API.

Pet Shards start at zero (absent balance row). The proposed price of 50 is stored in `shard_currencies.proposed_pet_redemption_price` for PET_SHARD only. There is no pet reward pool, unlock logic, wheel, pet redemption catalog or Pet Shard grant operation. A later pet phase must version its real offers/policies rather than treat this proposal as an active offer.

The existing catalog check constraint enforces `max_quantity=1` for every accessory and furniture piece and 1,000,000 for Food; unique `(user_id,item_id)` and inventory quantity checks enforce actual ownership. Equipment/room placement is separate from ownership: an unequipped item still counts as owned.

- `max_quantity=1`, no owned copy: grant one inventory item, zero shards.
- `max_quantity=1`, an owned copy: preserve its quantity/equipment/room placement and grant the version's tier-specific Item Shards, zero additional inventory.
- `max_quantity>1`: preserve legitimate additional copies up to capacity; no conversion just because an earlier copy exists. The settlement classification uses max quantity, not category or a visible/equipped flag. At capacity it rejects atomically.

There is **no currently supported multi-copy furniture** to migrate or enable. The existing catalog and Kotlin `CatalogItem` explicitly disallow it; neither is loosened in this step. The new reward snapshot model expresses the future distinction, but actual multi-copy support would need a coordinated catalog constraint/inventory/domain/equipment review in a later migration. Do not seed a hypothetical multi-copy item or relabel existing furniture as stackable. The four excluded new items' ownership/slots remain unresolved until they become real catalog items.

## Atomicity, request identities and access

`pawquest_private.settle_item_case(user,version,item,request)` is an internal deterministic settlement building block. It is revoked from PUBLIC, anon, authenticated and service_role; the private schema already denies those roles access. Its selected item parameter must never become a client-controlled winner. There was no public opening RPC in Step 02; Step 03 adds public.item_case_open in migration 026 without granting access to this private helper.

Settlement locks the existing owner's wallet first, as shop/reward/equipment operations already do. It returns a matching original receipt before checking current banner availability, or rejects a conflicting request. It verifies current published/enabled configuration and the full pool's catalog identity/activity, checks owned quantity, computes backend price/conversion, then inserts the opening, coin debit and inventory grant or shard credit in one transaction. Shard posting follows wallet → currency balance locking, preventing lost increments and mixed-operation overspending. Failure rolls back all effects. No pity, exclusion or reroll modifies probabilities.

Coin ledger receipts reference their opening and openings reference their coin receipt. Duplicate/redemption shard receipts likewise cross-link. Deferred FK/constraint triggers check owner, request, kind, source and amounts at commit, supporting atomic insertion without admitting cross-user source links. Ledgers/histories reject UPDATE/DELETE. Shard balance calculations are performed in private ledger triggers; caller-supplied `balance_after` is replaced with the actual result and negative/overflowing posts fail.

The existing unique `(user_id,request_id)` coin constraint is reused across shop/case debits. `shop_purchase` now also checks the ledger kind when replaying, returning request_conflict for a case request; without that guard a same-item case could have been misreported as a direct purchase. Existing shop item pricing, results and quest reward behavior otherwise remain unchanged. Shard and receipt tables also have unique owner/request identifiers and unique source links.

Authenticated users can read published configuration/odds and only their own balances, ledger and histories. They cannot write configuration, balances, receipts or ledgers, nor execute private settlement/posting functions. Views use security_invoker to preserve underlying RLS. Anon cannot read these structures. No public keys/secrets or session tokens are added.

The lock and view strategy follows PostgreSQL's [row-lock behavior](https://www.postgresql.org/docs/17/explicit-locking.html) and [security-invoker view semantics](https://www.postgresql.org/docs/current/sql-createview.html). Local role/RLS tests verify the application-specific behavior.

## Step 03 opening API and remaining execution phases

The public `item_case_open(banner,request)` API is implemented in migration 026. It authenticates with auth.uid(), recovers before drawing, uses cryptographic tier/item selection and calls private settlement atomically. Its only inputs are banner UUID and idempotency UUID. Private immutable response snapshots stabilize the item name and post-opening Item Shard balance; private rate settings/counters bound new attempts. See [ITEM_CASE_API_CONTRACT.md](ITEM_CASE_API_CONTRACT.md) for the full contract and tests. The banner remains disabled; production migration application/activation was not performed.

The future Item Shard redemption RPC must choose/validate an allowed current offer, reject unique items already owned, validate capacity and configured price, lock wallet/balance consistently, insert the reserved history, post the Item Shard debit and grant inventory atomically, and replay the original receipt on retries. The reserved receipt trigger validates versioned pricing/ownership but does not execute the debit or grant. Published historical offers are retained for interpretation; the future RPC must enforce which version is currently offered. No client may write a redemption receipt to trigger a reward.

Only after the opening API is approved/deployed and client integration is verified should the banner be activated. The HTML still needs adaptation to display authoritative receipts. Production migration application and activation need explicit approval. Expanding to the four excluded visual items needs real catalog/slot/artwork decisions first. The 20-item pool, 100-Coin price, given rates, unchanged ownership and duplicate conversion are implemented as the requested initial foundation without blocking on those missing designs.

## Files and automated validation

- `supabase/migrations/20261110002400_item_case_shards.sql`
- `supabase/migrations/20261110002500_item_case_seed.sql`
- `supabase/tests/item_case_shards.sql`
- `tools/testing/test_database.py` (includes the new suite on both fresh replays and private/mixed-operation concurrency tests)
- `shared/src/commonMain/kotlin/com/pawquest/domain/model/ItemCase.kt`
- `shared/src/commonTest/kotlin/com/pawquest/ItemCaseTest.kt`

The SQL suite checks actual tier membership, total/exact/unequal-weight probabilities, missing catalog references, Food rejection, active and slot-mapped catalog publication, published immutability, draft visibility, prices/conversion configuration, nonnegative balances, first grants and duplicates in every tier, same-request replay/conflict, disabled-banner recovery, inactive-pool rejection, no Pet Shards from Item Cases, currency isolation, duplicate ledger IDs, inventory/shard failure rollback, owner/anon/write-denial RLS, and reserved redemption price/history/ledger links. Stackable Food behavior is preserved and the absence of multi-copy furniture is asserted. A test-only trusted redemption fixture exercises reserved history and debit structures; it is not a production endpoint.

The runner creates randomly named disposable databases on the loopback PostgreSQL test server, applies all migrations, runs all existing SQL suites plus the new suite twice, then checks concurrent case retries/duplicate credits and mixed case/shop/reward operations. It drops only its generated databases. Shared Kotlin tests cover invalid tier/weight/price configurations, exact weighted odds, unique-versus-multi-copy previews, and separate currency balances. See the Step 02 entry in `docs/TEST_REPORT.md` for final executed results and limitations.
