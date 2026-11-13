# Item Case Kotlin integration - Step 04

Step 05 follow-up: the Shop now includes native case controls and an offline Android WebView preview. See [MYSTERY_CASE_SHOP_INTEGRATION.md](MYSTERY_CASE_SHOP_INTEGRATION.md); the description below records the Step 04 scope.

The Kotlin layer now loads the existing banner/configuration, reads authoritative Coins and both shard currencies, submits the secure opening API, and represents an item grant or duplicate conversion separately from the original rolled item. HTML, database migrations and case-opening Shop controls are unchanged. The existing Shop header gains only a localized Item Shards balance label; no Shop redesign or redemption UI is included.

## Existing systems reused

There is no standalone Coins Wallet repository in this codebase. `PetRepository.restore()` supplies the existing coin snapshot, and `PetViewModel.refresh()` updates its application projection. Step 04 reuses that same repository and the existing `InventoryRepository.getInventory()`. It adds neither another Coins wallet nor another inventory implementation.

`createAndroidRepositories` constructs `SupabaseItemCaseRepository` and `SupabaseShardRepository` with the **same** SupabaseClient already used for Auth, Pet, Shop and Inventory. Koin registers those interfaces, `OpenItemCase`, a persistent pending-request store, and a lifecycle-owned ItemCaseViewModel factory. PawQuestApplication passes the new dependencies into createAppContainer. Existing callers remain compatible through unconfigured defaults; a platform without durable storage fails closed instead of allowing nonrecoverable paid requests.

## Files and responsibility

Paths below start under `shared/src/` unless explicitly rooted elsewhere.

| Path | Responsibility |
| --- | --- |
| `commonMain/kotlin/com/pawquest/domain/model/ItemCaseResult.kt` | Durable intent, loaded banner/config, API receipt, reward outcome, rejection and shard wallet models |
| `commonMain/kotlin/com/pawquest/domain/repository/ItemCaseRepository.kt` | Item Case, read-only ShardRepository and pending-journal contracts |
| `commonMain/kotlin/com/pawquest/domain/usecase/OpenItemCase.kt` | Backend-only opening operation; no local winner or duplicate amount calculation |
| `commonMain/kotlin/com/pawquest/data/repository/SupabaseItemCaseRepositories.kt` | Existing-client Supabase adapters, DTO validation, owner checks and protected reads |
| `androidMain/kotlin/com/pawquest/data/repository/AndroidPendingItemCaseStore.kt` | Atomic/fsynced, project/account-scoped durable intent under noBackupFilesDir |
| `commonMain/kotlin/com/pawquest/presentation/gacha/ItemCaseViewModel.kt` | StateFlow, safe submission/recovery, read synchronization and visual lifecycle |
| `commonMain/kotlin/com/pawquest/di/AppModules.kt` | Repository/use-case/ViewModel registration |
| `androidMain/kotlin/com/pawquest/data/auth/AndroidAuthentication.kt` | Existing-client adapter composition |
| `androidApp/src/main/kotlin/com/pawquest/PawQuestApplication.kt` | Android composition root wiring |
| `commonMain/kotlin/com/pawquest/App.kt` | Account-owned ViewModel, resume/Shop refresh and existing wallet/inventory projection refresh after receipts |
| `commonMain/kotlin/com/pawquest/presentation/shop/ShopScreen.kt` | Minimal localized Item Shards label alongside the existing coin display |
| `commonMain/kotlin/com/pawquest/localization/Translations.kt` | English/Vietnamese shard balance/loading/unavailable strings |

## Repository contract and verified backend reads

`ItemCaseRepository.getConfiguration(bannerId)` reads the existing authenticated banner, its exact published version, and the `item_case_probabilities` view filtered to that version. It constructs the existing version/tier/weight models; it does not invent another configuration RPC, item mapping or schema. A disabled banner can still be displayed, but cannot start a new opening from the ViewModel. Configuration failure never prevents recovery of an already-persisted opening.

`open(PendingItemCase)` sends exactly `p_banner_id` and `p_request_id` to `item_case_open`. Price, reward, rarity, ownership, quantity, shard amount and user identity are not client inputs. API receipts are validated for matching request/banner, UUIDs, version, outcome/quantity/shard consistency and nonnegative balances. A recovered old version is valid even if the current catalog/price has changed; validation does not compare it to today's pool.

The economic request captures its authenticated UserSession, checks that it belongs to the durable intent owner, and binds that token through the SDK's authenticated API. Supabase SDK 3.8.0's RPC DSL appends custom headers; adding an Authorization override there produced duplicate headers in a regression test. The adapter instead uses the existing client's authenticated API transport and Postgrest URL/error parser with a fixed jwtToken, ensuring one account token without mutating global session configuration or creating another client. This small adapter uses a verified SDK internal API and must retain its header/owner tests during SDK upgrades. Tokens are never journaled or logged. Response acceptance also rechecks the current account.

`ShardRepository.getBalances()` is read-only: it selects shard_balances with an explicit user_id filter in addition to existing RLS and validates all returned owners/currencies/balances. A successful read with no row means zero for that currency; network, authorization or malformed-data failures remain failures. PET_SHARD can be read as reserved data, but no Kotlin operation grants it, spends it or derives it from an Item Case. There is no shard mutation or redemption method.

## Observable state and animation seam

ItemCaseState exposes:

- `phase`: Idle, Loading, ReadyToAnimate, Animating, RewardRevealed or Error.
- Current case configuration and banner, Coins, Item Shards, reserved full shard wallet and inventory snapshot.
- Durable pending identity, authoritative receipt, rejection/retry delay, error and separate synchronizationError.
- `winningItemId`, always the original receipt.winningItemId.

The receipt keeps `winningItemId`, name/code/asset/rarity **and** `ITEM_GRANTED` or `DUPLICATE_CONVERTED`. A duplicate is represented by quantityGranted=0 and the returned positive itemShardsGranted, while the original winning item remains available to animate. No local duplicate preview/configuration value is used to award shards.

The future UI must obtain ItemCaseViewModel from its existing account-owned ViewModelStore via Koin, collect state with the existing lifecycle pattern, and call:

1. `openCase()` only for a new user-authorized opening.
2. On ReadyToAnimate, pass the original winning item/asset to the future renderer.
3. `startAnimation(transactionId)` when that receipt's animation starts.
4. `revealReward(transactionId)` when it finishes (also permits a direct reveal if rendering is skipped/unavailable).
5. `acknowledgeReward()` after the user acknowledges the revealed receipt.

Stale/mismatched animation IDs do nothing. Visual callbacks never debit Coins, grant an item, calculate shards or open another case. The journal stays until acknowledgment, so losing the app after a confirmed response but before acknowledgment still recovers that original receipt. No HTML bridge or opening button is implemented yet.

## Durable request and recovery behavior

A new request UUID is generated only from Idle with an enabled loaded banner and no pending/result. Android saves it atomically **before** any network submission. The journal stores userId, bannerId and requestId only, isolated by normalized project URL and account, excluded from backup. saveIfAbsent preserves a prior request if another adapter/ViewModel instance already saved one; compare-and-remove prevents clearing a newer request. Corruption or storage failure blocks a new paid request rather than replacing an uncertain key.

Loading, pending uncertainty and an unacknowledged receipt block repeated open actions. `retry()` on an uncertain request reuses its exact saved key. HTTP failure, timeout, cancellation, invalid response or malformed receipt never creates a replacement key. A failed journal save issues no RPC. Cancellation/account switch leaves the old account's journal intact and discards late UI updates.

On account startup/recreation, the ViewModel first reads that account's journal. If present it calls the same backend opening/recovery RPC without first requiring active configuration or sufficient locally displayed balance. This can complete an original authorized intent that was saved before its first send; it can never create a second key/opening after an uncertain request. Once confirmed, retries only refresh reads and do not resubmit the economic RPC.

Busy/rate-limited/conflicting/account-unavailable results keep the key. Definitive no-charge business rejections (insufficient Coins, disabled banner, invalid request/configuration or full inventory) can retire it only through explicit `dismissRejection()`. A failed acknowledgment/delete preserves the revealed result and blocks a new opening; acknowledgment can be retried. Journals survive logout for safe recovery when the same user returns and are never read as another account's intent.

## Wallet and inventory synchronization

Confirmed receipt balances are exposed immediately as authoritative **post-transaction** values. The ViewModel then rereads current Coins, shards, inventory and configuration through existing repositories, because a recovered receipt can be older than other purchases/rewards. The receipt itself remains unchanged. If a read fails, the result stays ReadyToAnimate/Animating/Revealed and synchronizationError reports the read failure; it is never reported as a failed purchase or retried with a new key. Unknown shard balances display loading/unavailable instead of fabricated zero; last known balances can remain visible during refresh.

App.kt observes transaction IDs and refreshes the existing PetViewModel and InventoryViewModel projections. It clears an old Shop receipt display override so that it cannot mask later coin changes. Initial/resume/Shop navigation refreshes read state; `refresh()` is also available after a future redemption, without implementing redemption now. Existing inventory equipment/room actions retain their own synchronization guards.

## Validation and remaining integration

New host tests cover real-SDK parameter/header/auth behavior, configuration reads, both outcome types, balance parsing, owner isolation, malformed receipts/statuses, cancellation and HTTP failure; ViewModel tests cover first/duplicate results, wallet/inventory synchronization, timeout-after-commit retry, repeated actions, process recreation, disabled-config recovery, account switch, journal failures and acknowledgment/read failures. Android device tests verify real AtomicFile persistence across adapter recreation, concurrent save/compare-clear isolation, and corrupt-journal failure without overwriting an uncertain intent.

The implementation requires backend migrations through 026; it adds **no new migration** and does not enable the banner or contact production during tests. HTTP tests use Ktor MockEngine, not a real user's wallet. Remaining work is the native case UI/HTML bridge, user-facing recovery presentation, and an approved staging end-to-end smoke test against deployed Supabase. iOS has no durable journal adapter yet and therefore fails closed for paid openings. Final executed results are in docs/TEST_REPORT.md.
