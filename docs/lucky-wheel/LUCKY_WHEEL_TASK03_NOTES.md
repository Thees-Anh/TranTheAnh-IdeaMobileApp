# Lucky Wheel Task 03 integration

2026-11-10. Shop/Exchange and the existing HTML renderer are integrated. No database migration, production configuration or reward probability was changed in this task.

## Behavior

- Shop has Food Shop / Item Case / Lucky Wheel / Shard Exchange; the five bottom destinations stay intact. Food still uses the existing purchase flow. Accessory/Furniture Coin buttons are removed, including legacy ViewModel purchase entry points; catalog, owned stock, equipment, placement, purchase history and backend purchase APIs remain.
- One shared Exchange shell has Pets / Accessories / Furniture. The Item panel, repository, confirmation journal and actions are reused. Pet prices/ownership come from the backend, with quote, explicit confirmation, success/recovery and PET balance/collection refresh. Cat is excluded; unlocking does not select a pet.
- Wheel displays configured price/odds/rewards, Coins/Tickets/PET balances, explicit payment buttons, bounded history and a Pet Exchange link. Insufficient funds and disabled configuration prevent new requests. Open Again only dismisses the confirmed result; another payment choice is explicit.
- A single account/project-scoped atomic no-backup journal holds Wheel or Pet Exchange intent. Confirmed uncertain requests cannot be discarded. Recovery retains key/payment/species; a confirmed spin restores directly to reveal. The SDK verifies the journal's expected owner before dispatch, pins that account's access token, and rejects responses after account changes. Owner IDs never enter RPC JSON.
- The original studio HTML is preserved under design; packaged HTML retains its SVG, eight 45-degree sectors, quartic easing, peg/pointer effects and three pet-card cycle. No client draw, grants, studio buttons or CDN dependency remains. PET_UNLOCK stops at sector 7, then resolves the exact rabbit/puppy/capybara; all-owned replacement highlights no new pet. Native callbacks reveal only a committed receipt.
- The separate Wheel host follows Case's HTTPS asset allowlist/origin/main-frame/version/nonce/transaction checks, blocked network/file/popups/permissions, lifecycle cleanup and native fallback. Case HTML/host are preserved. Skip, reduced motion and interruption do not start requests. The stage scales to available portrait height and scrolls into view on a confirmed spin.
- Fixed an observed Chromium lazy-layout bug: CSS percentage/viewport heights could stay zero despite positive innerHeight. The renderer now synchronizes root/SVG pixel height to its actual viewport, including resize; a compositor pixel test guards against a blank-but-JS-ready WebView.
- Removed old level-unlock labels/milestone cards from collection; selection uses authoritative ownership. English/Vietnamese system copy is included.

## Files

Kotlin paths below are under `shared/src/commonMain/kotlin/com/pawquest/` unless noted.

- New transport/state: `data/repository/SupabaseLuckyWheelRepository.kt`, `domain/model/PendingLuckyOperation.kt`, `domain/repository/PendingLuckyOperationStore.kt`, `presentation/gacha/LuckyWheelViewModel.kt`. Updated `domain/repository/LuckyWheelRepository.kt` with client-only expected-owner binding.
- New UI: `presentation/gacha/LuckyWheelContent.kt`, `ShopShardExchange.kt`. Updated `presentation/gacha/ShardExchangePanel.kt`, `presentation/shop/ShopScreen.kt`, `ShopViewModel.kt`, `presentation/inventory/InventoryScreen.kt`, `PetCollectionContent.kt`, `localization/Translations.kt`.
- Wiring: `App.kt`, `di/AppModules.kt`; Android `data/auth/AndroidAuthentication.kt`; `androidApp/src/main/kotlin/com/pawquest/MainActivity.kt`, `PawQuestApplication.kt`.
- New Android classes: `shared/src/androidMain/kotlin/com/pawquest/data/repository/AndroidPendingLuckyOperationStore.kt`, `presentation/gacha/LuckyWheelPreview.kt`.
- Renderer: preserved `design/prototypes/gacha/lucky_wheel.html`, new `lucky_wheel_controller.js`; `tools/assets/build_lucky_wheel.py`; packaged `shared/src/androidMain/assets/gacha/lucky_wheel.html`, `lucky_wheel.css` (existing local Quicksand reused).
- New common tests: `LuckyWheelRepositoryTest.kt`, `LuckyWheelViewModelTest.kt`; updated `ShopCatalogTest.kt`, `ShopPurchaseTest.kt`.
- New device tests/fixtures: `LuckyWheelDeviceTest.kt`, `LuckyWheelShopDeviceTest.kt`, `LuckyOperationJournalDeviceTest.kt`, `LuckyShopFixture.kt`; updated `MysteryShopUiDeviceTest.kt`, `ShardExchangeUiDeviceTest.kt`, test AndroidManifest.
- New tools: `tools/testing/test_lucky_wheel_stage.mjs`, `test_lucky_wheel_portraits.py`. Documentation: this note, implementation plan/API status and `docs/IMPLEMENTATION_LOG.md`.

## Verification

- Focused host batch: 59 tests across Wheel/Shop/Case/Item Exchange, with the three repository fixture failures fixed and rerun successfully; five additional localization tests passed. The affected repository/ViewModel tests passed again after owner-binding hardening.
- Node renderer tests passed: all eight exact centers, each selected pet, two-stage completion, all-owned fallback, repeated play, skip/reset/cancellation, invalid IDs, reduced motion and no local economy/network.
- Device fixture tests cover actual local WebView, signed-origin callbacks, all eight centers at 320/360/393 CSS widths, pet cards, pause/reload, atomic journal restore/isolation/confirmation, Coins/Ticket/reveal, Pet and Item Exchange, preserved Case reveal/renderer and bottom navigation. Initial test-route/string issues were fixed; the blank compositor issue above was found and fixed using real pixel checks.
- All 18 distinct focused device cases passed after the described fixes/retries. `tools/testing/test_lucky_wheel_portraits.py` additionally passed two UI flows at each of 9:16, 19.5:9 and 20:9 (six profile executions), including real painted pixels. Logs are under `build/reports/lucky-wheel-portraits/`; the emulator's original display size was restored.
- `:androidApp:assembleDebug`, shared Android compilation and `:shared:assembleAndroidDeviceTest` passed. APK ZIP inspection verified the exact packaged Wheel/Case HTML/CSS and local font. No unrelated modules were rebuilt.
- Final debug APK installed on `emulator-5554` with `adb install -r`; existing app data was preserved. The real app was not driven through a hosted paid spin.
- Hosted Supabase paid transactions/JWT validation, a real account process-death recovery and production rollout: **Not Tested**. Device tests use isolated repositories, never the user's Coins/pets. Local backend transaction/RLS tests belong to Task 02; they were not rerun for this UI-only backend contract integration.

## Deployment prerequisites and known catalog limit

Apply reviewed migration 028 in the intended environment if not already applied. Its banner is deliberately disabled. Only after rollout approval may an operator activate it:

```sql
update public.lucky_wheel_banners set enabled = true
where code = 'lucky_wheel' returning id, enabled, current_version;
```

The application never runs this operation. Refresh the Wheel after activation. Tickets start at zero; this task adds no free tickets.

The six legacy SKUs `hat`, `glasses`, `collar`, `bed`, `lamp`, `carpet` remain outside the existing 20-item Case/Item Exchange pool. Owned copies still work and backend APIs/history remain. No shard prices or new Case odds were invented for them; offering new copies in Exchange requires an explicit backend eligibility/pricing decision.
