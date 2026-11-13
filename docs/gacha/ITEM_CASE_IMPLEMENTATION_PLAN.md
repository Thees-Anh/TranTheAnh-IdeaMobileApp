# Item Case Opening ? implementation plan

Updated 2026-11-10 after Step 04. Backend foundations/API, shared repositories/use case/ViewModel, durable Android recovery and the Item Shards balance projection are implemented. The case UI/HTML bridge remains pending. The user reports applying 024/025; migration 026 has only been tested locally and is not applied to production. See [SHARD_SYSTEM_DESIGN.md](SHARD_SYSTEM_DESIGN.md) for the current schema, exact seeds, duplicate policy and validation. [ITEM_CASE_AUDIT.md](ITEM_CASE_AUDIT.md) remains the historical pre-integration audit.

## Scope and completed decisions

Preserve the existing Shop, Coins wallet, inventory, equipment, room placement, navigation, portrait orientation and bilingual presentation. Keep the supplied HTML animation unchanged in Step 02. No Pet Lucky Wheel, redemption UI or production deployment is included.

| Requirement | Step 02 result |
| --- | --- |
| Eligible rewards | 20 actual migration-023 catalog UUIDs matching HTML asset IDs. Four HTML-only items are excluded until catalog/slot/artwork support exists |
| Banner | Combined Accessories/Furniture `item_case`, immutable configuration v1, disabled pending public API/integration |
| Price | 100 Coins, backend configuration |
| Tier odds | Common 55%, Rare 30%, Epic 12%, Legendary 3%; per-item weights all 1 |
| Effective item odds | Six Common: 55/6% each; six Rare: 5% each; five Epic: 2.4% each; three Legendary: 1% each. Exact rational odds exposed |
| Unique duplicates | First grants item; later acquisition grants 5/10/20/40 ITEM_SHARD by tier, without reroll or quantity growth |
| Redemption prices | Versioned 30/60/120/240 ITEM_SHARD by tier; catalog/history ready, execution/UI deferred |
| Separate currencies | ITEM_SHARD and PET_SHARD balances/ledger; Pet price 50 stored only as a future proposal, with no pet grant/redemption logic |
| All-owned accounts | Every result converts at its configured tier rate; unchanged draw probabilities |
| Multi-copy furniture | None exists. Existing catalog enforces unique accessories/furniture. Future max_quantity > 1 must preserve copies and capacity; no conversion solely because quantity > 0 |

## Stage 1 ? database foundation complete

New migrations:

- `supabase/migrations/20261110002400_item_case_shards.sql`: configuration/version/pool structures, publication validation/immutability, odds/redemption views, shard balances and append-only ledger, immutable histories, owner RLS and private settlement primitives. Reuses and extends `wallet_transactions`; keeps shop/reward behavior. `shop_purchase` now rejects request IDs belonging to a different ledger kind.
- `supabase/migrations/20261110002500_item_case_seed.sql`: explicit verified 20-item pool, 100-Coin price, tier odds/conversion/redemption values, published version with banner disabled.

Published versions snapshot item identity, category and maximum quantity. New changes require a draft/new version. Invalid totals, empty tiers, nonpositive weights/prices, missing catalog items or unsupported/inactive catalog mappings prevent publication. No existing catalog items, prices, ownership or balances are replaced.

Migration validation uses only disposable local PostgreSQL databases. Production migrations require explicit approval and deployment-state review.

## Stage 2 - secure opening/recovery API complete locally

Migration `20261110002600_secure_item_case_open.sql` adds authenticated `public.item_case_open(p_banner_id uuid,p_request_id uuid)` over the existing private settlement primitive. It accepts no selected reward, rarity, price, shard amount, ownership or random value. The actor comes from auth.uid(); it recovers by owner/request before checking current banner, price, rate limits or drawing. A matching committed request returns the original immutable response; a conflicting banner/key returns request_conflict.

New outcomes are drawn on the server using pgcrypto bytes and bounded rejection sampling, first across tier basis points and then per-item weights. Wallet-first locking, existing unique receipt constraints and private settlement commit the coin debit and item grant or duplicate Item Shard conversion atomically. Private response snapshots preserve the name and post-opening shard balance. Separate private attempt settings/counters default to 30 new attempts per 60 seconds, while committed recovery is exempt; five-second lock timeout gives a busy retry response.

The banner remains disabled. Migration 026 does not activate it and has not been applied to production. See [ITEM_CASE_API_CONTRACT.md](ITEM_CASE_API_CONTRACT.md) for response fields, statuses, cryptographic selection, security and executed-test coverage. Published 024/025 are preserved; apply only the new migration through an approved deployment.

No pity/progression mechanic or redemption execution is added. Future redemption still needs its own atomic operation over the reserved structures. Item opening never grants Pet Shards or unlocks pets.

## Stage 3 - Kotlin repositories, state and recovery complete

SupabaseItemCaseRepository loads existing banner/version/probability data and invokes the two-input opening API with the existing authenticated client. SupabaseShardRepository provides read-only currency balances with owner/RLS validation. OpenItemCase and ItemCaseViewModel are registered in Koin and wired through the Android composition root. Existing PetRepository coin snapshots and InventoryRepository are reused; no separate wallet or inventory implementation is introduced.

ItemCaseViewModel exposes Idle/Loading/ReadyToAnimate/Animating/RewardRevealed/Error, preserves both original winning item and actual grant/conversion, and refreshes existing wallet/inventory projections. Android saves account/project-scoped request UUIDs atomically before submission. Timeout/cancellation/restart recovery keeps the same key, including when the banner is now disabled. New openings remain blocked until reconciliation or acknowledgment; synchronization failures never turn committed rewards into failed purchases. Logout/account switches isolate journals and late responses. The SDK transport binds each economic request to its captured account token.

The existing Shop gains a localized Item Shards balance label, with loading/unavailable distinguished from zero. No new case controls, redesign or HTML changes are included. No new database migration or banner activation is needed for this step; backend through 026 is required. See [ITEM_CASE_KOTLIN_INTEGRATION.md](ITEM_CASE_KOTLIN_INTEGRATION.md) for interfaces, persistence behavior, visual callbacks, tests and remaining staging work.

## Stage 4 - offline HTML presentation prepared (Step 05)

The original 24-entry artist HTML is preserved byte-for-byte under design/prototypes/gacha/. tools/assets/build_item_case.py generates the production stage with local compiled Tailwind CSS and the existing Quicksand font. Fake economy/editor controls are excluded; the original chest, card artwork and idle motion remain HTML/CSS/JS. The preview uses only the 20 supported catalog IDs and 65 cosmetic cards. No CDN or remote font is required.

Deterministic opening playback is still Step 06. Define a versioned rendering contract that accepts only a committed transaction and original item ID, validates catalog mapping, positions the exact winning card and reports visual completion without granting rewards. Unknown IDs and interrupted/repeated playback must recover through the native receipt without another purchase. Test all eligible IDs, geometry, resize, reduced motion, pause/disposal and duplicate commands.

## Stage 5 - Android WebView and Shop complete (Step 05)

The existing Shop now has four horizontally scrollable categories. Mystery Case shows local artwork, backend price, Coins, Item Shards, native purchase confirmation, rewards, effective rates, owner-scoped history, loading/errors and insufficient-funds/disabled-banner states. Shard Exchange is disabled pending Step 10. Existing catalog purchases and bottom navigation remain intact.

AndroidX WebViewAssetLoader serves exactly three local gacha files over the appassets HTTPS origin. The dedicated host blocks network/navigation, file/content privileges, storage, permissions and popups; it exposes no native bridge or credentials. Lifecycle, readiness timeout, renderer failure, retry and disposal are handled. Android content is injected through App's platform composable seam; common UI has an unavailable fallback. Portrait manifest and existing scaffold insets are preserved.

Confirmed results currently appear natively through the existing safe ViewModel. No HTML winner playback is claimed. Existing wallet/inventory refresh and durable idempotency remain authoritative. See [MYSTERY_CASE_SHOP_INTEGRATION.md](MYSTERY_CASE_SHOP_INTEGRATION.md) for files, asset generation, security and limitations.

## Stage 6 - packaging and release verification

Fresh Android debug APK inspection confirms assets/gacha/item_case_opening.html, item_case.css and quicksand.ttf match the source bundle. Shared host tests, Android build/lint and emulator WebView/Shop tests are recorded in docs/TEST_REPORT.md. Localized content and project structure validation pass.

Remaining release acceptance: full deterministic playback (Step 06), authorized staging paid opening/recovery, physical-device portrait ratios and cutouts, large fonts/TalkBack, release behavior, network loss/background/process-death/account-switch recovery and existing real purchase/equipment regression checks. Unit/repository/device fixture tests do not replace hosted economic end-to-end verification.

## Step 06 - server-driven HTML playback

Implemented the version 1 initialize/play/skip/reset contract over the preserved HTML reel. Kotlin sends only confirmed original item/transaction IDs and display metadata. Origin/frame/playback identity checks guard visual notifications; completion/failure/skip reveal the existing immutable receipt. Lifecycle and watchdog fallbacks never reopen a case. Actual DOM measurements replace hard-coded stop offsets/jitter. The 20 seeded IDs are tested independently of the 24 artist designs. See [ITEM_CASE_ANIMATION_CONTRACT.md](ITEM_CASE_ANIMATION_CONTRACT.md).

The earlier Step 05 scope notes above are historical; deterministic playback is now implemented. No production activation or hosted end-to-end verification is implied.

## Recommended next work item

Native reveal/collection/drop rates/history/Shard Wallet panels are now complete. History is bounded to 20 owner-filtered rows with immutable pool rarity; Shard Wallet is read-only and filters ITEM_SHARD. Existing Inventory actions and the animation are reused. Open Again requires explicit confirmation after durable acknowledgment. Exchange remains disabled. Verify these read queries and economic recovery against an authorized staging backend; local PostgreSQL was unavailable for this UI task. Confirm migration 026 and authorized staging banner configuration before hosted paid verification. Production deployment/banner activation still requires approval; the four excluded designs need catalog/slot/artwork decisions before eligibility can expand.


## Item Shard Exchange follow-up

Migration 027 and the authenticated quote/confirm/redeem flow now reuse the reserved redemption structures. Atomic wallet-first settlement, backend version/price binding, explicit durable confirmation and same-key recovery are tested locally. Exchange is wired into Shop/Shard Wallet and existing Inventory actions; no Pet redemption or animation change. Final status and remaining authorized staging/production deployment gates: [ITEM_CASE_FINAL_TEST_REPORT.md](ITEM_CASE_FINAL_TEST_REPORT.md).
