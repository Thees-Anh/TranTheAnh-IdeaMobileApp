# Prototype-based 2D presentation

The user selected the supplied `design/prototypes/pets/mochi.html` **2D cat**, rather than the proposed 3D interpretation, on 2026-11-06. This decision replaces the earlier sky-blue/3D visual direction in AI_INSTRUCTIONS.md for the active Android presentation. Kotlin Multiplatform, shared Compose screens, MVVM/repositories, portrait orientation and all authoritative Supabase rules remain in place.

## Visual system

- Quicksand is bundled in Compose resources; its SIL Open Font License is in `docs/licenses/Quicksand-OFL.txt`.
- Pink `#F9A8D4`, blush `#FDF2F8`, cocoa `#592512`, cream, lavender and peach replace the previous palette. White cards have rounded cocoa outlines; primary actions have a pressed state and offset outline shadow.
- Authentication, quests/forms/details, Focus/Reward, Shop, Inventory, Profile and the five-destination scaffold share these tokens. Forms retain IME padding and scrolling. Catalog grids become one column on compact phones. Bottom destination labels remain visible from 320dp at normal font size; accessible names remain available at larger font sizes.
- Pet Home uses a compact wallet/XP header and a room that fills the remaining portrait height down to the bottom navigation. Feed/Play/Sleep live in a compact native overlay at the bottom of the room, with minimum 52dp controls. Error/retry and rejected-action feedback appear before the room when needed; overflow remains scrollable.
- Original multi-color cartoon vectors provide the food bowl, happy heart, energy bolt, play ball, sleepy moon, cat house, quest book, shop bag, backpack and cat avatar. Tab and stat labels/semantics remain intact; vector colors are preserved with Color.Unspecified.
- The original artwork uses 400x330 logical SVG units. The viewport viewBox now adapts to the actual native width/height; wall/floor layers extend vertically, original art scales uniformly, and the cat has a uniform 1.12x emphasis. Reserved space above the native action bar keeps the cat and accessories clear of controls. These are illustration coordinates, not physical screen pixel dimensions.

## Renderer boundary

`PrototypePetScene` is Android-specific. A hardened WebView renders **only the offline SVG/CSS illustration**, not the application UI. Native Compose owns navigation, forms, controls and feedback; shared ViewModels own game state. No third-party WebView library or remote web app is introduced.

`tools/assets/build_prototype_pet.py` extracts the original SVG and animation CSS into `shared/src/androidMain/assets/pet/prototype_stage.html`. It does not copy the prototype's simulated Coins, quests, stat mutations, Tailwind/CDN or other game JavaScript. Regenerate with:

```powershell
python tools/assets/build_prototype_pet.py
python tools/assets/build_prototype_pet.py --check
```

The renderer accepts only a logical animation state and allowlisted equipment/furniture slot names, serialized as JSON. It receives no credentials, tokens, user text, balances or backend URL. File/content access, network loads, external navigation, storage and popups are disabled; CSP blocks network connections. There is no `addJavascriptInterface`. A cat SVG click produces the single local `pawquest-pet://touch` event, which invokes the existing PetViewModel action. Debugging is enabled only for a debuggable application.

Native measured viewport dimensions determine the adaptive viewBox and CSS aspect ratio. HTML roots and the SVG retain `height:auto`, avoiding the zero-height root clipping found during the first implementation. Parent transforms use uniform scaling to preserve cat proportions. Lifecycle pause/resume stops/restarts CSS animations; disposal destroys the WebView and releases callbacks.

## Animation and customization

| Domain state | Source animation |
|---|---|
| IDLE | idle / blink / breathe |
| HAPPY | laughing / hearts |
| EATING | eating / food prop |
| SLEEPING | sleeping / breathing / Zzz |
| PLAYING | laughing / bouncing / hearts |

The existing success-only `petAnimationCommand` projection supplies temporary reactions and persistent sleeping state. Rejected care actions never trigger success reactions. Existing ViewModel guards serialize requests. The debug-only preview is collapsed by default and does not change stats or inventory. There is no new thirst/drinking gameplay because the backend has no such action.

Owned catalog items map to illustrated hat, glasses and collar under the animated head/body groups. Bed, lamp and carpet occupy fixed room layers behind the cat. Unequipping/removing hides those parts; restore uses the existing persisted equipment/room snapshots. Unknown identifiers are skipped with a friendly notice. Missing illustration files show a retry state. These drawings replace GLB presentation for the existing MVP item identifiers; arbitrary future GLBs are not converted automatically.

The old SceneView renderer and assets remain in the repository as inactive reference/rollback material. MainActivity uses the new 2D renderer. No database migration is required. iOS renderer parity and device accessibility/performance profiling remain separate verification work; the runnable target is Android.

## Verification tools

- Existing shared tests and Android build/lint: `:shared:testAndroidHostTest :androidApp:assembleDebug :androidApp:lintDebug`.
- `tools/testing/test_room_hud_android.py`: expanded room/action bounds, 48dp touch targets, tab accessibility and animation clearance on three portrait ratios, 320dp and 1.5x text. `--compact-only` reruns the last two profiles.
- `tools/testing/test_prototype_android.py`: native navigation/screens at three portrait ratios plus 320dp, using the loopback-only room fixture and a disposable account. Never run its assumptions against a real backend.
- `tools/testing/test_prototype_care_android.py`: Feed/Play/cat hit-testing and Sleep restoration against the 55446 fixture; `--resume-sleep` completes the restart checkpoint when needed.
- `tools/testing/test_prototype_stage.mjs`: Node 22+ visual checks through the debug WebView socket forwarded to localhost:9229. Samples five animation states, full cat/equipment/furniture bounds, inherited attachment transforms, unknown-state fallback, hidden unowned parts and offline CSP. It changes renderer state only, then restores it.

Actual execution results and remaining checks are recorded in TEST_REPORT.md and IMPLEMENTATION_LOG.md. [Current expanded Pet Home preview](../media/previews/pet-home-expanded.png) uses disposable visual-test data.
