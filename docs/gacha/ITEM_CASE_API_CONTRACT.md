# Item Case Opening API — Step 03

Implemented in `supabase/migrations/20261110002600_secure_item_case_open.sql`. Requires the already released migrations 001–025. Migration 026 is additive: it does not rewrite 024/025, change the catalog or enable a banner. No production migration was applied during development.

## Calling the operation

Authenticated Supabase Postgrest RPC:

```text
POST /rest/v1/rpc/item_case_open
Authorization: Bearer <signed-in user's access token>
apikey: <project publishable/anon key>
Content-Type: application/json
```

```json
{
  "p_banner_id": "e2400000-0000-0000-0000-000000000001",
  "p_request_id": "a4188051-b65f-499d-8518-f7200206656b"
}
```

The SQL signature is exactly `public.item_case_open(p_banner_id uuid, p_request_id uuid) returns jsonb`. It derives the actor with `auth.uid()` from the verified Supabase session. Use POST: this is a volatile, mutating operation. No Edge Function, service-role key or Android-held privileged credential is required.

Only banner ID and the client's freshly generated request UUID are accepted. There is no reward, rarity, price, random value/seed, shard amount, ownership, user ID, quantity or configuration-version parameter. Additional named arguments do not match the RPC signature. Invalid UUID syntax is rejected by PostgreSQL before the function executes; null IDs return invalid_request.

The seeded banner ID above remains disabled. Migration 026 does not activate it. Apply the new migration and later activate the banner only through an approved deployment/configuration change; this task did neither on production. Four unsupported HTML-only items remain outside the 20-item pool.

## Successful response

Illustrative Rare duplicate result; IDs, balance and timestamp are examples:

```json
{
  "status": "opened",
  "receipt": {
    "transaction_id": "6ce01308-4060-4fa7-b05a-5ded329033df",
    "request_id": "a4188051-b65f-499d-8518-f7200206656b",
    "banner_id": "e2400000-0000-0000-0000-000000000001",
    "configuration_version_id": "e2400000-0000-0000-0000-000000000002",
    "configuration_version": 1,
    "winning_item_id": "e2300000-0000-0000-0000-000000000005",
    "item_name": "Kính Ngôi Sao",
    "item_code": "acc_glasses",
    "asset_id": "acc-glasses",
    "rarity": "RARE",
    "opening_cost_coins": 100,
    "reward_outcome": "DUPLICATE_CONVERTED",
    "quantity_granted": 0,
    "item_shards_granted": 10,
    "coins_balance_after": 900,
    "item_shards_balance_after": 10,
    "created_at": "2026-11-10T05:00:00.000000Z"
  }
}
```

| Field | Meaning |
| --- | --- |
| transaction_id | Original `item_case_openings.id`, not a new ID for each retry |
| request_id / banner_id | Bound to the authenticated actor's original request/banner |
| configuration_version_id / configuration_version | Immutable historical rule version used for price, tier odds, item weights and duplicate conversion |
| winning_item_id | Real `item_catalog.id` originally selected by the server, retained even for a duplicate |
| item_name | Catalog name captured at opening. Later catalog renames do not change a replay |
| item_code / asset_id | Versioned catalog/visual identifiers. Use asset_id to map to the HTML visual ID; do not treat the HTML index as a database ID |
| rarity | COMMON, RARE, EPIC or LEGENDARY from the selected pool entry |
| opening_cost_coins | Original backend price; currently 100, independent of direct Shop price |
| reward_outcome | ITEM_GRANTED or DUPLICATE_CONVERTED |
| quantity_granted | 1 on an item grant, 0 on duplicate conversion |
| item_shards_granted | 0 on an item grant; configured 5/10/20/40 on a unique duplicate |
| coins_balance_after / item_shards_balance_after | Confirmed balances immediately after this transaction, preserved on replay, not a live balance query |
| created_at | Original opening timestamp, formatted as UTC ISO text with microseconds |

An ITEM_GRANTED result has one granted item and zero Item Shards. A DUPLICATE_CONVERTED result has zero granted copies and a positive configured Item Shard amount. Both retain the rolled item ID, rarity and visual asset so a later HTML integration can stop on the actual roll. No Pet Shard or pet unlock is granted.

## Rejections and retries

Normal business rejections return `{"status":"...","receipt":null}`. Busy/rate-limited responses also include a positive `retry_after_seconds` integer. These statuses are JSON business results, not a promise of distinct HTTP error codes.

| Status | Meaning / caller action |
| --- | --- |
| invalid_request | Null banner/request UUID; fix the request |
| account_unavailable | Authenticated account has no initialized wallet; repair account readiness |
| request_conflict | Key already belongs to another banner or an existing coin/shard operation; do not reinterpret that operation as this purchase |
| banner_unavailable | Unknown/disabled banner or no published current version; no draw/charge |
| insufficient_coins | Authoritative wallet cannot afford the current backend price; no draw/charge |
| configuration_unavailable | Published pool is missing/changed/inactive or lacks required slot/configuration data; no filtering or reroll; operator must repair/version configuration |
| inventory_full | Selected multi-copy item is at capacity; no debit/grant/conversion/reroll. Currently production accessories/furniture are unique-owned, so this is a future-compatible branch |
| rate_limited | Too many new attempts in this account's window; wait retry_after_seconds |
| busy | A required row lock could not be acquired within five seconds; no economic effect; retry the same key after a short delay |

Authentication failure raises SQLSTATE 42501. Anon/service roles have no execution grant. PostgreSQL signature/UUID errors reject malformed calls before economic work. Unexpected settlement/storage/entropy failures raise P0001 with a generic retry message and roll back the complete call. Serialization/deadlock errors (40001/40P01), query cancellation and transport failures also require retry with the same key; never assume a lost response means no commitment.

Normal rejected attempts do not create opening history or reserve a successful financial receipt. Retrying a rejected key can succeed later if balance/banner/capacity changes. Failed business attempts still count against the attempt limit. Unexpected exceptions roll back the attempt counter too. A committed successful key is permanent and always returns its original receipt.

Persist the request UUID before sending the request, scoped to the signed-in account. If the result is ambiguous, resend exactly the same banner/key; never generate a replacement key to recover. A new key represents a new paid opening. UI animation completion or failure has no authority over granting or paying.

## Atomicity and concurrency

The RPC runs as a single PostgreSQL transaction through the existing architecture:

1. Require authentication and non-null identifiers, then lock the actor's existing wallet row.
2. Recover a matching prior opening **before** rate limits, active banner/current version, balance checks or randomness. Reject a different banner with the same key.
3. Apply server-side attempt controls and reject key collisions with other coin/shard operations.
4. Lock/read the active published banner version and backend price; check Coins.
5. Validate and lock all catalog/slot mappings. Do not remove owned items, silently filter inactive items, or change advertised odds.
6. Select the rarity and item using cryptographic weighted draws, then call the existing private `settle_item_case` under the same wallet lock.
7. Settlement reads actual inventory: grant the first unique item or convert its duplicate using the version's tier rule. Multi-copy inventory continues to increment up to its existing capacity.
8. Commit the original winner/outcome/history, request UUID, existing coin ledger debit, inventory or Item Shard ledger/balance effect, and response snapshot together.

The original unique owner/request and ledger source constraints remain in force. Concurrent same-key callers serialize and recover the same receipt. Concurrent different keys cannot overspend or lose shard increments. The wallet-first lock order also serializes against Shop purchases and quest rewards. If any grant, shard post, coin update, history or response snapshot write fails, all economic effects roll back. Deferred FK/source checks are validated before the surrounding transaction commits.

Migration 026 adds only private operational metadata: `item_case_api_settings`, `item_case_request_limits` and `item_case_response_snapshots`. The last table freezes item name and post-opening Item Shard balance, which were absent from the immutable 024 receipt. It does not replace any economic ledger. Original price/outcome/coin balance still come from the original opening; visual identity/rarity come from its immutable version/pool.

Private settlements predating this public API have no earlier public response to replay. On their first recovery the name is frozen from the then-current catalog; duplicate shard balances use the linked ledger and first-item shard balances use the latest preceding Item Shard ledger entry. This is legacy reconstruction, not a claim that an unavailable historical item name was recovered. New public calls capture the exact name and balance under the wallet lock.

## Cryptographic selection and probabilities

`pgcrypto.gen_random_bytes` provides cryptographically strong random bytes. Migration 026 locates pgcrypto's installed schema and binds a private schema-qualified wrapper; an existing extension is not relocated. No SQL random(), UUID-derived randomness, clock seed, client-supplied seed or floating-point draw is used. See [PostgreSQL pgcrypto documentation](https://www.postgresql.org/docs/current/pgcrypto.html).

The private sampler uses seven bytes for a uniform integer in [0, 2^56). For bound N it rejects values at or above `2^56 - (2^56 % N)` before taking modulo N. This avoids modulo bias. Bounds are 1 through 2^56; an invalid bound or repeated entropy failure aborts. The sampler is capped at 128 rejection iterations and never falls back to weaker randomness.

Two independent bounded draws select:

- A ticket in [0,10000) across tier basis points, ordered Common/Rare/Epic/Legendary.
- A ticket in [0,sum(weights in selected tier)) across that tier's items, ordered by catalog UUID.

Thus `P(item) = tier_bps/10000 × item_weight/tier_weight_sum`. The seeded pool's actual effective probabilities remain Common 55/6% each, Rare 5% each, Epic 2.4% each and Legendary 1% each. Ownership never affects selection probabilities; it determines only item versus Item Shards after the roll. Fully collected accounts receive duplicate conversions without rerolls. Random bytes/tickets, internal seeds and inventory-before information are not returned.

## Abuse controls and security notes

Defaults: **30 new attempts per account per 60-second fixed window**, across banners. Expected rejected fresh attempts count too; committed receipt recovery is exempt so throttling cannot prevent resolving an uncertain charge. Counters are serialized under the wallet lock. A fixed-window boundary can admit two adjacent bursts; these controls are an economic/application throttle, not a network-wide DDoS defense. Existing gateway/auth limits remain necessary for traffic and repeated reads.

Operators can change max_attempts (1–600) and window_seconds (1–3600) only in `pawquest_private.item_case_api_settings`. Clients cannot read/write counters/settings or supply replacements. Lock waits are capped at five seconds and busy responses carry retry guidance; no unbounded queue is intentionally imposed by this RPC.

The only new public operation is the two-argument authenticated RPC. All cryptographic helpers, ticket mappers, response helpers, settlement, snapshots and limit structures deny PUBLIC/anon/authenticated/service_role access. Private tables also enable RLS without client policies. Existing user-owned public tables keep owner-only reads and no mobile writes. SECURITY DEFINER functions use an empty search_path with qualified relation/function references, following [Supabase database-function guidance](https://supabase.com/docs/guides/database/functions).

No privileged credentials, direct backend calls from HTML, forced-rarity controls or client award/grant interfaces are introduced. Production pgcrypto availability and schema/migration history should be checked at deployment. Published migrations 024/025 must not be edited or rerun as upgrades; apply 026 once after approval.

## Automated evidence and limits

`supabase/tests/item_case_open.sql` exercises the real cryptographic source, bounded sampling, rejection of the biased tail, exhaustive 10,000-ticket tier mapping, equal/unequal item weights, first acquisition, all four duplicate conversions, stable replay, changed names/banner state, insufficient balance, quotas, forged extra arguments, ordinary-role permissions, cross-user isolation and full rollback on inventory/shard failures.

Deterministic outcome/fault tests replace private helpers only under trusted test ownership inside a rolled-back test transaction, save/restore their real definitions, and leave no production test hook. A multi-copy furniture fixture temporarily relaxes the existing unique-only catalog constraint inside that same rolled-back transaction. It verifies quantity increments, no inappropriate conversion and capacity rejection; it does not enable multi-copy furniture in production.

`tools/testing/test_database.py` includes this suite on both fresh migration replays, plus real-entropy authenticated races for same-key recovery, distinct openings, all-owned duplicate conversion, limited balance, quotas and mixed Shop/reward mutations. All existing economy/regression tests remain included. Results actually executed are recorded in the Step 03 entry of `docs/TEST_REPORT.md` and `build/database-tests.log`.

Tests use a disposable loopback PostgreSQL cluster with the existing local Auth shim, not hosted Supabase or a live Postgrest gateway. JWT verification and HTTP transport/schema-cache behavior require a later staging smoke test. Android, HTML animation, shard redemption and Pet Lucky Wheel were not implemented or changed in this step.
