# Item Case / Item Shard Exchange final report

Date: 2026-11-10. Implementation and local verification complete. Production deployment is **not** performed.

## Implemented

- Migration **027** adds authenticated `item_shard_quote(p_item_id,p_request_id)` and `item_shard_redeem(p_item_id,p_request_id)`. Both accept only catalog UUID and idempotency UUID; identity comes from auth.uid(). Existing versioned prices (30/60/120/240), catalog, Inventory, balances, ledger and redemption history are reused unchanged.
- Quote is nonfinancial, immutable and valid for 10 minutes. Quote retries cannot redeem. Explicit UI confirmation is durably saved before redeem. A changed configuration/version requires a new quote and explicit confirmation. Committed recovery precedes expiration/price/ownership checks and returns the original receipt/balance.
- Settlement locks the existing wallet first, then configuration/catalog, ownership and Item Shards; receipt, negative ledger entry, balance and Inventory grant commit together. Unique owned items are rejected; quantity increments use the existing inventory primitive and backend maximum. All 20 current eligible items, including furniture, are unique-owned; no unsupported multi-copy furniture policy is invented.
- Shop > Mystery Case and Shard Wallet now open Exchange with backend-configured items, thumbnails, rarity/category, price, ownership, confirmation, progress, safe retry, success and existing Inventory/Equip/Place actions. No second Wallet or Inventory is introduced. Item Case opening is blocked while an exchange intent is unresolved.
- Android stores project/account-scoped UUIDs and confirmation atomically in noBackupFilesDir. Confirmed uncertainty cannot be discarded; process/model restoration reuses the same request. Late pre-redemption wallet reads are invalidated so they cannot overwrite the confirmed balance.
- HTML/CSS and the existing server-driven animation are unchanged. No Pet Lucky Wheel, Pet redemption or currency interchange.

## Security verification and results

| Check | Executed result |
| --- | --- |
| Fresh local PostgreSQL migrations | PASS: all 27 migrations replayed twice on disposable databases |
| SQL integrity/RLS suites | PASS: existing gacha/shards, auth, Coins, Shop, Inventory, equipment, room, quests/rewards and new exchange suite |
| Exchange concurrency | PASS: six identical retries produce one debit/grant; competing requests cannot overspend |
| Exchange forgery/isolation | PASS: extra price argument, unquoted redemption, foreign-user reads, ordinary balance/ledger writes and unauthorized/private access rejected |
| Ownership / price change / rollback | PASS: already-owned unique item rejected, changed version rejected without charge, injected inventory failure rolls back ledger/balance/receipt |
| Existing opening/duplicate security | PASS: secure draw, forged inputs, replay, concurrent duplicate conversion, insufficient Coins, rate limits and recovery via existing SQL harness |
| Kotlin host regressions | PASS: 130 focused tests, plus one new stale-balance race regression after review; no failures |
| Android emulator | PASS: 14 focused cases for Exchange, reveal, Shop, journals and real WebView; affected Exchange test also passed after the balance-race fix |
| WebView | PASS: all 20 real winning UUIDs, centering at three widths, normal/fast motion, skip, reload, pause/resume, reduced motion, invalid metadata and forged visual notifications |
| Android build | PASS: assembleDebug, including rebuild after balance-race fix |
| Android lint | PASS: 0 errors, 43 warnings; rerun on the final balance-refresh change |

Commands: `python tools/testing/test_database.py`; focused Gradle host patterns Item/Shop/Inventory/Equipment/Room/Pet/Quest/Reward/Auth/Navigation/Focus; `:androidApp:assembleDebug :androidApp:lintDebug`; selected connected device classes. Broader passing suites were not repeatedly rerun; the final race fix was checked with its new host test and the affected Exchange UI test. Localization (768 pairs/186 static references), project layout and final APK animation-hash checks passed. The final debug APK was installed on the emulator without wiping app data. The isolated test PostgreSQL server was stopped after verification. SQL logs: build/database-tests.log.

Confirmed issue fixed: a Case wallet refresh started before redemption could overwrite a newer balance. Read revisions and a post-operation refresh now prevent that race. Existing gacha/WebView tests did not reveal another confirmed vulnerability; their protections were preserved.

## Files changed

- Backend: supabase/migrations/20261110002700_item_shard_exchange.sql; supabase/tests/item_shard_exchange.sql; tools/testing/test_database.py.
- Shared: domain/model/ItemExchange.kt; domain/repository/ItemCaseRepository.kt; data/repository/SupabaseItemCaseRepositories.kt; presentation/gacha/ItemExchangeViewModel.kt, ShardExchangePanel.kt, ItemCaseViewModel.kt, MysteryCaseContent.kt, ItemCasePanels.kt; presentation/shop/ShopScreen.kt; App.kt; di/AppModules.kt; localization/Translations.kt.
- Android wiring/persistence: data/repository/AndroidPendingItemExchangeStore.kt; data/auth/AndroidAuthentication.kt; androidApp/PawQuestApplication.kt.
- Tests: ItemExchangeViewModelTest.kt; ItemCaseRepositoryTest.kt; ItemCaseViewModelTest.kt; ItemExchangeJournalDeviceTest.kt; ShardExchangeUiDeviceTest.kt; MysteryShopUiDeviceTest.kt fixture.

## Not Tested / deployment boundary

- Full Android-to-hosted-Supabase HTTP end-to-end and real process-kill/relogin persistence were **not tested**. SQL used an isolated PostgreSQL cluster and auth shim; SDK tests used mocked authenticated HTTP; emulator used test-only fake repositories. Journal adapter recreation and ViewModel recovery were tested, not presented as a hosted full-app restart.
- Current emulator runs used 20:9; WebView width checks cover 320/360/393dp. Physical devices, full Exchange UI at 9:16/19.5:9 and full accessibility acceptance remain manual checks.
- Apply migration **027 only after approval**, after 024–026 on the target environment. No production data, permissions or banner settings were changed. Case banner activation remains separate; Exchange eligibility uses the current published `item_case` configuration and redeemable flags, not the paid-opening enabled switch.
- Authorized staging acceptance remains: open a real paid case, observe exact original-item animation and duplicate credit, redeem through quote/confirm, compare ledger/Inventory/Coins, kill/restart and recover the same receipt. Do not enable production rollout based solely on component tests.
