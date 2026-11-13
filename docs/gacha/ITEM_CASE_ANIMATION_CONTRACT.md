# Step 06 - confirmed-result HTML playback

The runtime asset is generated from the preserved artist HTML and `design/prototypes/gacha/item_case_controller.js` using `tools/assets/build_item_case.py`. Chest SVG, 65-card renderer, slot 52, CSS shake/open/highlight and original easing are reused. All 24 designs remain in the original input; only the 20 verified catalog items are eligible. Cosmetic filler uses initialized display metadata. No winner RNG, local wallet, grant or equip operation remains in the runtime.

## JavaScript interface (version 1)

- `PawQuestCase.initialize({version:1, playbackId, items:[{itemId,assetId,name,rarity}], fast:false, reducedMotion:false})`: display metadata only. UUID identities must be unique; rarity must be supported. Names are escaped before entering the preserved card template. Missing art uses a generic star while retaining the original item ID/name.
- `PawQuestCase.play({transactionId,winningItemId})`: requires initialized metadata. The confirmed item is inserted at slot 52. Same transaction/item repeated is a no-op; a different or conflicting transaction is rejected until reset. Unknown IDs return false without substituting a random item.
- `skip()`: centers/highlights the confirmed item immediately and reports completion once.
- `reset()`: cancels timers/listeners, reports interrupted visual completion if needed, and prepares an idle reel. Never opens another case.
- `pause(boolean)` and read-only `status()` support lifecycle and verification.

Normal shake/roll durations remain 1100/4800ms; fast display mode uses 450/1800ms. The original cubic-bezier(0.10,0.82,0.08,1) is retained. Actual card/indicator rectangles determine the final translation; random stop jitter is removed for exact centering. Transition end is primary, with a bounded timer fallback. Resize finishes/recenters the same receipt; reduced motion skips travel. Audio remains disabled as in the offline Step 05 runtime.

## Native contract and recovery

`ItemCaseAnimation` carries the existing immutable receipt, display pool and UI callbacks through App/Shop's platform composable seam. Only display metadata, transaction ID, original winning ID and an ephemeral playback UUID enter JavaScript. Coins, shard amounts, ownership, credentials and backend methods do not enter the page. A recovered receipt supplies its own historical winning identity even if the current banner pool changed.

The existing secure RPC/idempotency flow is unchanged. Kotlin retains the receipt and refreshes Wallet, Inventory and Item Shards independently of animation. Only matching `startAnimation`/`revealReward` transitions run from presentation notifications. Compose reveal uses the original receipt, including authoritative duplicate shard amounts; its acknowledgment never creates a new opening. Open Case remains blocked until acknowledgment. Skip, background, navigation/disposal, load failure and a 15-second native watchdog reveal the confirmed result without a new transaction. Process restoration reuses the existing journal key.

Completion uses AndroidX WebKit's origin-aware message listener at the exact appassets HTTPS origin. Native checks the main frame, current page, message size/shape/version, playback UUID, transaction ID and original item ID. Repeated notifications are ignored. Unsupported messaging uses native reveal fallback. Callbacks are untrusted visual notifications and never grant/deduct anything. Existing exact asset allowlist, CSP, network/file/content/storage/permission/navigation restrictions remain. No addJavascriptInterface is added.

## Verification

Focused host tests cover existing economic/recovery paths plus duplicate/stale/repeated visual notifications without another opening. Real WebView tests exercise all 20 seeded IDs at 320/360/393dp, exact centering, normal/fast easing, skip/reset/repeat, reduced motion, invalid IDs, escaped names, missing art, forged notifications and reload. A test-only Compose Shop fixture checks explicit opening, Skip Animation and authoritative duplicate reveal. See `docs/TEST_REPORT.md` for executed outcomes. No production migration, banner activation, hosted paid opening, Pet Lucky Wheel or redemption is part of this step.
