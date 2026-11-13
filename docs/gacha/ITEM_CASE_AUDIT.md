# Item Case Opening — read-only integration audit

Audit date: 2026-11-10 (Asia/Bangkok).

## Scope and conclusion

Read `docs/AI_INSTRUCTIONS.md` and `docs/PROJECT_STRUCTURE.md`, the complete animation, and the relevant local Kotlin, Gradle, manifest, SQL migration and test sources. This audit creates documentation only. No application source, animation, database, build configuration, or installed application was changed. Documentation is placed in `docs/gacha/` as explicitly requested, overriding the general feature-document placement rule.

The existing application supplies the Shop, authoritative wallet, inventory, purchase receipts, equipment and room placement needed as foundations. The HTML is a standalone design/demo workspace, not an integrated purchase system. Its visual reel can support a server-selected outcome after a small JavaScript interface change; its current opening function chooses the reward locally. There is no verified case-opening backend operation in the inspected repository.

Evidence is repository state, not proof of hosted Supabase deployment. No remote schema, data, RLS configuration or Edge Function deployment was queried. No Gradle build, SQL test, emulator or browser rendering test was run. Runtime findings below are static analysis unless explicitly identified as APK inspection.

## 1. Animation and dependencies

Source: `shared/src/androidMain/assets/gacha/item_case_opening.html`, 1,390 lines, 92,596 bytes. SHA-256 at audit: `15b97b714fc8ea0e23d3f838332acb30fe3240514be390291c790f1fed9163df`.

| Resource | Verified implementation |
| --- | --- |
| CSS | Inline `style#pawquest-gacha-styles` (lines 9–295), inline style attributes, Tailwind utility classes throughout markup and generated cards |
| Remote JavaScript | `https://cdn.tailwindcss.com` (line 7), unversioned runtime dependency; no integrity attribute |
| Application JavaScript | One inline `script#gacha-engine-script`, lines 842–1388; inline `onclick` handlers |
| Fonts | Google Fonts CSS request for Quicksand 500/600/700/800 and JetBrains Mono 400/500/600; sans-serif/monospace fallbacks. Font binaries are resolved by the remote stylesheet and are not bundled here |
| Artwork | Two inline SVG scenes (chest and room/pets/accessories/furniture), CSS gradients/patterns, Unicode emoji for reel and reward icons |
| Images/media | No `img`, SVG `image`, external image files, audio/video files or iframe tags in the document |
| Sound | Synthesized Web Audio oscillators/gains: ticks and rarity-specific fanfare; `AudioContext`/`webkitAudioContext` |
| Browser APIs | DOM, timers, CSS transforms/transitions, `requestAnimationFrame`, `getComputedStyle`, `DOMMatrixReadOnly`, deprecated `document.execCommand('copy')` in demo code viewer |
| Backend/storage | No Supabase calls, fetch/XHR, persistent wallet/inventory storage, native bridge or native completion callback |

The SVG namespace `http://www.w3.org/2000/svg` and `url(#roomCarpetPattern)` are identifiers/internal references, not network dependencies. No other external URL was found in the HTML. Transitive content of the remote Tailwind script and Google stylesheet was not downloaded or audited.

The existing offline WebView deliberately blocks all subrequests. Loading this HTML in it unchanged would block Tailwind and fonts. Missing Tailwind also removes essential layout/visibility utilities, including `hidden`, the 240px track padding and sizing; this is more than a font fallback issue.

## 2. Actual animation lifecycle

| Step | Code and behavior |
| --- | --- |
| Initialize | `window.onload` (1382): builds 65 reel entries with `GACHA_ITEMS_POOL[18]` (crown) at index 52, syncs demo equipment, renders all 24 inventory toggles, displays source code. Does not automatically spin |
| Start | `startGachaSpin(fastMode)` (1066): guards `isSpinning` and local balance, deducts 100 from `userCoins` (initially 1500), increments local count, locks two spin buttons and hides reward overlay |
| Select reward | Reads developer rarity selector, calls `rollRarityTier`, then `pickRandomItemByRarity`; assigns global `lastWonItem` before movement |
| Build track | `buildReelCards(lastWonItem)` (1025): resets transition and translation, emits 65 cards, forces the winner at zero-based index 52; other cards are independent random draws with replacement |
| Shake/open | Chest switches to shaking, waits 1100ms normally / 450ms fast, then switches to opened. Lid has its own 450ms CSS transition; it overlaps the reel, rather than being a separate awaited phase |
| Move/decelerate | CSS `transform` transition for 4800ms normally / 1800ms fast, easing `cubic-bezier(0.10, 0.82, 0.08, 1)`. There is no simulated friction/physics engine |
| Target coordinate | Card width 116px + gap 10px = pitch 126px. Winning center is `240 + 52*126 + 58 = 6850px`. Translation is `-(6850 - viewport.clientWidth/2 + jitter)` |
| Jitter | `(Math.random()-0.5)*72`, approximately -36 to +36px; within the 58px card half-width if geometry matches assumptions. A nearby comment incorrectly says 38px |
| Tick sound | Animation frame loop reads transform matrix and plays a tick when the computed card index changes; pitch is cosmetic randomness |
| Finish | Timer at `rollDuration + 120ms` cancels tick loop, highlights card 52, plays fanfare, displays `lastWonItem`, re-enables buttons. No `transitionend` or host acknowledgment |
| Equip/repeat | Demo equip merely sets `item.active=true` and toggles inline SVG. Repeat invokes the same local spin function again |

Nominal start-to-reveal time is 6020ms normally and 2370ms fast, plus scheduling delays. The fast button's “2s” label is approximate. Background throttling, resizing, WebView destruction and timer cleanup are not handled by this file. Hard-coded track measurements depend on Tailwind's layout/reset behavior; validate actual card centers on device.

### Randomness and predetermined winners

`rollRarityTier` uses `Math.random()*100`: Legendary below 3, Epic below 15, Rare below 45, otherwise Common. `pickRandomItemByRarity` selects uniformly from six entries. Per-item demo odds are therefore tier odds divided by six, not 55/30/12/3% for every item. Forced rarity is a test override.

**Predetermined stopping is structurally possible but has no supported item-ID API today.** `buildReelCards(winningItem)` accepts an object and inserts it at index 52. However, `startGachaSpin` accepts only `fastMode` and replaces the selected item using local RNG. Calling `buildReelCards` alone neither runs the opening sequence nor synchronizes `lastWonItem`. A production entry point must validate/map a trusted outcome ID, set the corresponding visual winner, and run the existing sequence without reselecting it. The timer/modal and host result must refer to the same opening identity.

## 3. The 24 items and catalog mapping

The pool contains **24 distinct visual IDs: 12 accessories and 12 furniture, six entries per rarity**. They are not 24 copies of one item. The **65 reel entries repeat** those pool items for movement. Demo `active` means visible/equipped, not owned or catalog-enabled: all 24 can be toggled without winning them.

Twenty IDs match migration `20261109002300_decor_catalog.sql` and `domain/model/DecorCatalog.kt`. HTML IDs are **catalog `asset_id`**, not database UUID `id` and not catalog `code`. For rows below, UUID prefix is `e2300000-0000-0000-0000-` followed by the exact 12-digit suffix shown.

| HTML ID / catalog asset_id | Catalog code | UUID suffix | Slot | Demo rarity |
| --- | --- | --- | --- | --- |
| acc-sprout | acc_sprout | 000000000003 | HEAD | Common |
| acc-bowtie | acc_bowtie | 000000000007 | NECK | Common |
| acc-scarf | acc_scarf | 000000000008 | NECK | Common |
| furn-plant | furn_plant | 000000000017 | PLANT | Common |
| furn-bookshelf | furn_bookshelf | 000000000014 | BOOKSHELF | Common |
| furn-lights | furn_lights | 000000000013 | LIGHTS | Common |
| acc-glasses | acc_glasses | 000000000005 | FACE | Rare |
| acc-backpack | acc_backpack | 000000000010 | BACK | Rare |
| acc-monocle | acc_monocle | 000000000006 | FACE | Rare |
| furn-clock | furn_clock | 000000000012 | CLOCK | Rare |
| furn-tea-table | furn_tea_table | 000000000018 | TEA_TABLE | Rare |
| furn-lamp | furn_lamp | 000000000020 | LAMP | Rare |
| acc-wizard-hat | acc_wizard_hat | 000000000002 | HEAD | Epic |
| acc-headphones | acc_headphones | 000000000004 | HEAD | Epic |
| furn-cat-tree | furn_cat_tree | 000000000015 | CAT_TREE | Epic |
| furn-gramophone | furn_gramophone | 000000000019 | GRAMOPHONE | Epic |
| furn-window | furn_window | 000000000011 | WINDOW | Epic |
| acc-crown | acc_crown | 000000000001 | HEAD | Legendary |
| acc-wings | acc_wings | 000000000009 | BACK | Legendary |
| furn-bed | furn_bed | 000000000016 | BED | Legendary |
| acc-sakura-kimono | Not found | — | Undefined | Epic |
| acc-celestial-halo | Not found | — | Undefined | Legendary |
| furn-sakura-fountain | Not found | — | Undefined | Legendary |
| furn-magic-throne | Not found | — | Undefined | Legendary |

The four missing IDs have SVG drawings in this HTML but no verified catalog rows, shared slot mappings, supported asset registration or production rendering support. Kimono/body and fountain/throne do not have dedicated existing slots; whether to reuse or add slots is a product/design decision. Halo placement also needs a decision. Do not fabricate UUIDs or treat demo SVG availability as production asset support.

The initial catalog also has legacy items such as code `glasses`, asset `item.accessory.glasses`, distinct from `acc_glasses` / `acc-glasses`; do not join by display name or loosely normalize IDs. Resolve server catalog UUID → exact `asset_id` → visual pool ID, checking category, active eligibility, unique mapping and supported renderer. Display names/descriptions differ between demo and catalog; use existing localization/catalog presentation rules.

## 4. Existing architecture and reuse points

Paths in this table are relative to `shared/src/` unless explicitly rooted elsewhere; abbreviated `presentation/`, `domain/`, `data/` and `localization/` paths are under `commonMain/kotlin/com/pawquest/`.

| Area | Verified files and integration relevance |
| --- | --- |
| Composition/navigation | `commonMain/kotlin/com/pawquest/App.kt`, `presentation/navigation/Destination.kt`, `PawQuestScaffold.kt`: five existing destinations, saved selected route; Shop and Inventory callbacks already connected. Protected ViewModels are cleared on logout/account switch |
| Shop | `presentation/shop/ShopScreen.kt`, `ShopViewModel.kt`: Food/Accessories/Furniture tabs, adaptive catalog grid, ownership badges, purchase confirmation, quantity, busy/error/result and same-request retry. No case-opening entry or route |
| Purchase domain | `domain/model/Purchase.kt`, `domain/repository/ItemRepository.kt`, `domain/usecase/PurchaseItem.kt`: validated item/request UUIDs, 1–100 quantity, receipt/status models. Case outcome is not represented |
| Catalog/inventory | `domain/model/Inventory.kt`, `domain/repository/InventoryRepository.kt`, `data/repository/SupabaseInventoryRepositories.kt`: `CatalogItem` has UUID id, code, category, price, asset ID, max quantity, active and description; no rarity/case weight. Reads via `shop_catalog` and `inventory_items` RPCs |
| Inventory UI/state | `presentation/inventory/InventoryScreen.kt`, `InventoryViewModel.kt`: owned items, equipment and placed items; existing equip/place commands and retry paths |
| Equipment | `domain/model/Equipment.kt`, `DecorCatalog.kt`, `data/repository/SupabaseEquipmentRepository.kt`: HEAD/FACE/NECK/BACK; `equipment_state`, `equipment_set` |
| Furniture | `domain/model/RoomFurniture.kt`, `DecorCatalog.kt`, `data/repository/SupabaseRoomRepository.kt`: BED/LAMP/CARPET/WINDOW/CLOCK/LIGHTS/BOOKSHELF/CAT_TREE/PLANT/TEA_TABLE/GRAMOPHONE; `room_state`, `room_set` |
| Wallet display | `App.kt` reads `petState.snapshot?.coins ?: account.coins`; auth initializes coins from `ensure_account`, pet snapshot refreshes via `pet_state`. `presentation/components/PawQuestBadge.kt` (`CoinBadge`) is reusable. No separate Wallet repository/ViewModel was found |
| Shop balance behavior | `ShopScreen` temporarily prefers last purchase receipt balance over supplied coins. A case result must refresh existing snapshots and avoid leaving an old shop receipt masking newer balance |
| Purchase refresh | `App.kt` observes successful shop results and calls `inventory.loadInventory()` and `pet.refresh()`. Equipment/room are projected through `LocalEquipment` and `LocalRoom` |
| DI | `commonMain/kotlin/com/pawquest/di/AppModules.kt`: Koin repository singletons, use-case/ViewModel factories, lifecycle-owned ViewModels; unconfigured repository fallbacks |
| Android backend wiring | `androidMain/kotlin/com/pawquest/data/auth/AndroidAuthentication.kt`; `androidApp/src/main/kotlin/com/pawquest/PawQuestApplication.kt`: Supabase Auth/Postgrest over OkHttp, encrypted session manager, shared repositories passed to `createAppContainer` |
| Existing HTML host | `androidMain/kotlin/com/pawquest/presentation/scene/PrototypePetView.kt`, `PawQuestDog.kt`, `FocusCompanion.kt`: WebView in `AndroidView`, IO asset loading, readiness, lifecycle pause/resume, release/destroy, JSON state projection and safe insets |
| Artwork/localization | `commonMain/kotlin/com/pawquest/presentation/components/ItemArtwork.kt`, `domain/model/DecorCatalog.kt`, `localization/`; source catalog `design/assets/decor/catalog.json` |

Business logic belongs in commonMain; Android WebView code belongs in androidMain. iOS source-set scaffolding exists and is configured on macOS, but this audit establishes no iOS case renderer. Filament/SceneView dependencies still exist; the inspected current pet path also uses offline SVG HTML.

## 5. Backend, RLS and transactional constraints

Only SQL migrations/tests are present under the inspected `supabase/` tree; no Edge Function implementation directory was found. The verified application calls PostgreSQL functions through Supabase Postgrest, not a case-specific Edge Function.

| SQL evidence | Relevant behavior |
| --- | --- |
| `20261105000100_initial_schema.sql` | `wallets` and `wallet_transactions` RLS enabled; authenticated SELECT restricted to owner. No mobile wallet/ledger write grants. Private schema access revoked |
| `20261105000200_reward_integrity.sql` and subsequent reward migrations | Server reward calculation, wallet-row locking, exactly-once quest reward receipts and append-only ledger trigger; preserve reward limits/semantics |
| `20261105000900_pet_care.sql` | Original food inventory owner-only SELECT policy, retained through inventory rename |
| `20261105001000_shop_inventory.sql` | Catalog SELECT for authenticated users; no mobile catalog writes. `user_inventory` unique `(user_id,item_id)`, catalog FK, maximum quantity, nonstackable accessories/furniture (`max_quantity=1`). Private `adjust_inventory` is inaccessible to mobile roles |
| `20261105001100_shop_purchases.sql` | Authenticated `shop_purchase(p_item_id,p_quantity,p_request_id)` derives owner via `auth.uid()`, locks wallet, replays matching receipt or rejects conflicting payload. Prepare trigger reads server price, checks ownership/capacity/balance; apply trigger grants inventory and updates wallet atomically |
| Same purchase migration | Unique `(user_id,request_id)` for purchases. Ledger shape permits `quest_reward` and `shop_purchase` only at that point, with purchase debit tied to item catalog price. No later inspected migration adds a case ledger shape/handler; pet play costs are stats rather than coin debits |
| `20261105001200_pet_equipment.sql`, `20261105001300_room_furniture.sql`, `20261109002300_decor_catalog.sql` | Slot tables readable to authenticated users; equipment/room rows owner-only SELECT; mutations through authenticated SECURITY DEFINER functions with empty search path, ownership and slot checks, wallet-first locking |

Existing `shop_purchase` is a **fixed-item purchase**, not a random draw or fixed-price case purchase. Do not select an item in JavaScript and pass it to this endpoint as a secure case opening. Its item price may differ from the demo's 100-coin charge. A debit and grant must not be split across mobile requests.

Duplicate handling is a blocking design dependency: accessories/furniture cannot stack, and direct purchases reject already-owned items. The private grant primitive silently returns an already-owned unique item; reusing that primitive without an explicit case policy could debit coins without granting anything new. Decide exclusion, compensation, rejection, or another documented policy, including odds when a tier or entire pool is exhausted.

Local tests available for reuse include `supabase/tests/shop_purchases.sql`, `shop_inventory.sql`, `pet_equipment.sql`, `room_furniture.sql`, `decor_catalog.sql`, `integrity.sql`, `reward_engine.sql`; shared `ShopPurchaseTest.kt`, `ShopCatalogTest.kt`, `InventoryRepositoryTest.kt`, `InventoryViewModelTest.kt`, `SupabaseAuthenticationRepositoryTest.kt`. The purchase SQL test explicitly covers price authority, replay, conflict, ownership, rollback and role restrictions. These tests were inspected/referenced, not executed in this audit.

## 6. APK packaging finding

`shared/build.gradle.kts` uses the Android KMP library plugin with Android resources enabled; `androidApp/build.gradle.kts` depends on `:shared`. The HTML is in the expected Android assets source location. No gacha-specific exclusion was found. Existing pet files from the same assets source tree appear in the APK, corroborating the module's assets path.

Read-only ZIP inspection of `androidApp/build/outputs/apk/debug/androidApp-debug.apk` (44,990,295 bytes; host-reported modification 2026-11-09 17:27:14) found pet assets but **no `assets/gacha/item_case_opening.html` or other gacha entries**. The HTML was added later and is untracked in Git. Therefore: placement is appropriate; the existing APK does not include it; a fresh APK's contents are unverified. No application code references the gacha asset path. Packaging alone will not make it reachable in Shop.

## 7. Security and mobile risks

| Priority | Finding | Required treatment before production |
| --- | --- | --- |
| Critical if trusted | JS random winner, local debit, forced rarity, free coins and unrestricted demo equip | Backend decides price/outcome/ownership and commits once. HTML is a visual projection only; remove demo economy/actions from runtime surface |
| High | Remote, unpinned executable Tailwind and remote fonts | Bundle compiled CSS and approved fonts; keep runtime renderer offline. Do not relax existing network blocks to accommodate demo dependencies |
| High if bridge added | No case bridge today; future bridge would expose native actions to page scripts | Use minimal validated events, bind to current opening/account/view instance; never expose wallet writes, tokens, arbitrary SQL/RPC/URLs or choose-winner commands. Restrict origins/main frame if using web messaging |
| High if dynamic data added | `innerHTML` templates interpolate item name/icon/ID and inline handlers | Prefer DOM construction/textContent; strict known asset-ID allowlist and JSON serialization. Never interpolate external strings into executable JS or HTML |
| Medium | No CSP; inline scripts/handlers complicate a restrictive policy | Design local content policy with the runtime bundle; block frames, remote scripts and unexpected navigation; avoid unsafe eval |
| Medium | No timer/audio lifecycle cleanup, rendering failure acknowledgment or replay identity | Cancel timers/RAF, suspend/close audio, release WebView, recover the committed receipt on interruption; never charge again to repair an animation |
| Medium | Layout and accessibility are demo-oriented | Remove code editor, fake inventory, pet selector and developer tools from runtime view. Support small/tall portrait screens, system insets, readable labels, adequate touch targets, TalkBack reward announcement and reduced motion |

`PrototypePetView` already disables file/content access, network loads, DOM storage, mixed content, popups and arbitrary navigation; intercepts subrequests; enables debugging only in debuggable apps; and uses an HTTPS base URL with `loadDataWithBaseURL`. It has no `addJavascriptInterface`. Reuse these practices, not its pet-specific callbacks or required `pawquestRender` contract unchanged. Its CSS pause mechanism alone does not pause this animation's timers, audio or JavaScript RAF.

The application manifest requests INTERNET for Supabase and locks `MainActivity` to portrait. Internet permission is not evidence that embedded content should receive network access. No gacha security regression is active yet because there is no host integration.

Android recommends `loadDataWithBaseURL` or `WebViewAssetLoader` for local content rather than opaque file/data origins. The existing inline-document approach avoids adding a dependency; an asset loader is an optional future choice if local subresources are introduced. See [Android local-content guidance](https://developer.android.com/develop/ui/views/layout/webapps/load-local-content). Native bridges require strict control of loaded content and message origins; see [Android native-bridge risks](https://developer.android.com/privacy-and-security/risks/insecure-webview-native-bridges).

UI review used `.agents/skills/ui-ux-pro-max/SKILL.md` and its verified `animation reduced motion` UX search. No reduced-motion query, dialog semantics/focus management, safe-area handling or host accessibility result announcement exists in this HTML. These are static findings; actual contrast, clipping, performance and assistive technology behavior require device testing.

## 8. Missing prerequisites and next step

1. Decide whether initial eligibility covers the 20 existing mapped items or all 24. Do not silently remove four items and retain the original advertised odds; tier membership/availability changes must be explicit.
2. Decide case price, combined versus category-specific pools, odds, duplicate treatment, all-owned behavior, and any progression/pity requirements. The demo's 100 coins and tier percentages are design proposals only.
3. Verify target deployment migrations/RLS through an authorized read-only backend review; local SQL cannot establish deployed state.
4. Specify a server-authoritative, atomic, idempotent opening operation and durable result recovery using existing wallet/inventory foundations. No name/signature or new table layout is approved or verified here.
5. Define the minimal Kotlin↔HTML rendering contract, offline runtime bundle, lifecycle and deterministic outcome tests.

Recommended next step: settle the pool/duplicate rules and produce the backend transaction contract before implementation. Follow the staged plan in `ITEM_CASE_IMPLEMENTATION_PLAN.md`.
