# Step 05 - Mystery Case Shop integration

## Delivered behavior

The existing Shop retains Food, Accessories and Furniture and adds Mystery Case in a horizontally scrollable selector. Mystery Case is a UI selection, not a new database ItemCategory. Existing purchasing and bottom navigation remain in place. A vertically scrolling native panel contains the HTML artwork, backend configuration price, existing Coins projection, read-only Item Shards, availability/insufficient-funds/loading/error states and a native purchase confirmation.

View Rewards and Drop Rates use the loaded versioned reward pool and effective probabilities. Opening History reads the owner's latest 20 existing item_case_openings records under RLS, without a privileged RPC or schema change. Shard Exchange is visibly disabled. A committed opening is shown as its real native item or duplicate-conversion receipt; acknowledgment uses the existing durable ViewModel recovery flow. No JS callback grants rewards or submits purchases.

## Relevant paths

- shared/src/commonMain/kotlin/com/pawquest/presentation/shop/ShopScreen.kt and ShopViewModel.kt: category integration.
- shared/src/commonMain/kotlin/com/pawquest/presentation/gacha/MysteryCaseContent.kt: case controls and dialogs.
- shared/src/androidMain/kotlin/com/pawquest/presentation/gacha/MysteryCasePreview.kt: Android-only WebView host.
- App.kt and androidApp/src/main/kotlin/com/pawquest/MainActivity.kt: existing injected platform-content seam.
- SupabaseItemCaseRepositories.kt, ItemCaseRepository.kt, ItemCaseResult.kt and ItemCaseViewModel.kt: owner-scoped history read/state.
- shared/src/androidMain/assets/gacha/: packaged HTML, compiled CSS and local Quicksand font.
- tools/assets/build_item_case.py: reproducible offline stage extraction.
- design/prototypes/gacha/item_case_opening.html: byte-preserved original artist input (SHA-256 15b97b714fc8ea0e23d3f838332acb30fe3240514be390291c790f1fed9163df).

## HTML preservation and boundaries

The runtime keeps the supplied HTML/CSS/JS chest, SVG artwork, reel cards, gradients and idle motion. It is not rewritten in Compose. The preserved full design contains 24 entries. The production preview filters against the existing decor catalog's 20 supported IDs, then builds the original 65-card visual reel; repeated cards are cosmetic.

The artist workspace, fake wallet, force-rarity controls, fake inventory/equip actions and client-selected outcome controls are excluded from the production stage. No real roll is currently played in HTML. Step 06 must implement deterministic winner positioning, completion and recovery using the committed original winning item ID. Cosmetic filler RNG does not select or grant a reward. The native receipt remains usable even when the artwork fails.

To regenerate, install Tailwind CSS 3.4.17 into the ignored build/gacha-toolchain directory as documented in the generator, then run `python tools/assets/build_item_case.py`. The compiled CSS includes static/template utilities and border-3. Font reuse follows the existing project font licensing; Tailwind's MIT license is in docs/licenses/tailwindcss.txt. Gradle packages generated assets directly; Node/npm is not needed to build a checked-in bundle.

## WebView security and lifecycle

AndroidX WebKit 1.15.0 is added to androidMain. WebViewAssetLoader serves HTTPS appassets content. The host intercepts and allows only the exact HTML, CSS and font paths; other origins, schemes, queries and asset paths receive 403. Remote network access, mixed content, file/content access, file URL privileges, DOM storage, geolocation, permission requests, file pickers, popups and navigation are blocked. CSP also denies connections, frames, objects and forms. Inline scripts/styles are limited to the trusted bundled design.

There is no addJavascriptInterface, credential/session injection, wallet bridge, privileged method or backend request in the page. JavaScript is enabled only for this renderer. Loading starts after a nonzero native measurement. The page fits explicit pixel heights to innerHeight on load/resize because viewport CSS units resolved to zero in the attached lazy layout during testing. A screenshot assertion verifies actual purple stage pixels, in addition to DOM readiness. Loading readiness is checked, with a 10-second timeout, error/retry UI and renderer-process failure handling. Backgrounding pauses CSS/WebView activity; disposal destroys the instance. Production Activity remains portrait-only. Native LazyColumn content respects the existing scaffold's insets/bottom navigation, while the 320dp-high preview adapts its width and prevents horizontal document overflow.

## Verification and remaining scope

See docs/TEST_REPORT.md for final executed test totals. Device tests verify local packaged loading, stylesheet application, 65 cards, widths 320/360/393dp, absence of privileged globals and purchase buttons, settings, allowlist rejection and pause/resume. Native Shop instrumentation uses a test-only Activity with fake repositories; it never spends a real user's Coins. Host tests cover category preservation and authenticated history reads, in addition to existing economic/recovery tests.

APK ZIP inspection verifies all three runtime files and byte equality with source. This step does not activate the banner, deploy a migration, exercise hosted paid openings, implement redemption, or implement Pet Lucky Wheel. Real backend integration requires existing migration 026 and authorized staging configuration. Step 06 should add the strict deterministic rendering contract before production banner activation. Full TalkBack, large-font, physical-device and release acceptance remain required.

## Pet-care gate (2026-11-12)

Draws are refused with status `pet_needs_care` (`need`: hungry, thirsty or
hungry_thirsty) while Hunger or Water is ≤25 after decay, before throttle,
charge or draw; committed requests still replay their receipt. The Shop shows
a card with Feed/Drink and disables the draw buttons. Migration:
`20261112003300_gacha_pet_care_gate.sql`.
