# Performance audit — 2026-11-11

## Actual rendering and build

- Android application: `androidApp`; shared KMP library: `shared`. Kotlin 2.4.21, Compose Multiplatform 1.12.2, AGP 9.1.1, SDK/target 37, minimum 24; versions read from `gradle/libs.versions.toml`.
- **Production entry point currently chooses SVG/WebView, not Filament**: `androidApp/.../MainActivity.kt` passes `PrototypePetScene` to `App`. `PrototypePetScene.kt` and `PawQuestDog.kt` host `PrototypePetView`. Cat, Dog, Rabbit and Capybara use packaged SVG/HTML. Do not attribute their current performance to 3D model/shadow settings.
- Optional native path: `PetScene.kt`, `PetSceneView.kt`, `PetAnimationController.kt`, `FilamentAnimationPlayer.kt`. SceneView 2.3.3 / Filament 1.68.2; only Cat has a native pet GLB. Bytes/bounds/equipment loading uses IO dispatchers; native GPU creation remains on its owning thread. One SceneView frame callback updates time-based animation; clip/token equality avoids restarts. Authored transforms/bones are intentionally restored each sample. Engine/model/environment cleanup is explicit. No global GPU cache or quality reduction is justified by this run.
- Release minification is currently disabled; resource shrinking and project Baseline/Startup Profile generation are not configured. Manifest does not disable Android hardware acceleration. Debug tooling/development trust are debug-specific. No dependency/plugin upgrade or security setting change was made.

## Device and capabilities

Measured runtime: Medium_Phone, Android 17/API 37, x86_64, 1080×2400, 420 dpi, 60 Hz. SurfaceFlinger reports **Google SwiftShader**, not host GPU rendering. Native accelerated graphics previously crashed on Intel/Vulkan; this task keeps the recovered software configuration unchanged. Guest `/proc/meminfo` reported 4,006,168 kB total and approximately 2.2 GB available at inspection. Host/guest background load is not controlled like a lab device.

ADB, UI automation, `dumpsys gfxinfo/meminfo/cpuinfo`, runtime GC counters and Perfetto are available. Android Studio profiler UI was not controlled; no trace-processor binary was available. The real app initially required authentication, so baseline profiling uses a **test-only activity with the exact packaged SVG renderer**, no account, backend requests or economic mutations. This is not a complete Pet Home Compose-screen benchmark.

`tools/testing/profile_pet.py` records repeatable 30-second idle and interaction scenarios, app process CPU, main-thread CPU time, app PSS and GC counts. It captures a bounded scheduling/gfx Perfetto trace. The fixture then smokes all four assets and pause/resume/release. The 20,000 unchanged-update burst is a synthetic allocation/dispatch microbenchmark, not a normal workload.

### Baseline (before application edits)

| Measured metric | Idle, 30 s | State changes every 500 ms, 30 s |
|---|---:|---:|
| rAF callback cadence | 46.98 Hz | 40.02 Hz |
| Mean callback interval | 21.28 ms | 24.99 ms |
| P50 / P90 | 16.70 / 33.40 ms | 16.70 / 33.40 ms |
| P95 / P99 | 33.40 / 33.50 ms | 33.50 / 50.00 ms |
| Callback intervals >25 ms | 26.61% | 44.84% |
| App CPU (100%=one logical core) | 80.42% | 105.53% |
| Main-thread CPU time | 1,882.79 ms | 1,667.32 ms |
| App PSS (excludes renderer child/host GPU) | 125,199 kB | 123,696 kB |
| Runtime GC count delta | 1 | 1 |

Unchanged-update burst: **148.86 ms / 20,000 calls**. These callback measurements are not presented FPS, GPU execution durations or Android deadline-based jank. `gfxinfo` after fixture destruction did not provide usable per-scenario window samples; those metrics are N/A. Perfetto is retained for offline analysis, not claimed as an analyzed GPU bottleneck proof.

## Findings and priorities

1. **Environment limitation:** CPU software graphics is a major confounder. The callback data contains missed 60 Hz intervals, but cannot apportion them to host scheduling, SVG paint or composition without deeper tracing. Do not promise 60 FPS or lower visual quality to hide this limitation.
2. **Unchanged pet updates allocate JSON before checking equality.** `PrototypePetView.update` already suppresses duplicate JS commands, but still constructs JSONObject/JSONArray/string values on the main thread. Avoid this work before serialization; retain immutable display snapshots only.
3. **Lifecycle pause traverses every DOM node and writes inline styles.** Replace with one injected stylesheet and a root pause-class toggle, preserving CSS animation timing and per-view lifecycle. Coalesce repeated viewport updates and remove pending callbacks on release.
4. **Case fullscreen playback leaves the inline animated preview mounted.** Remove that obscured WebView while the existing fullscreen renderer runs; preserve the preview slot and existing reveal/idempotency behavior. Wheel already suppresses its inline preview during play.
5. **Wheel idle initialization repeats on Compose updates.** Deduplicate serialized preview configuration per document and check the existing transaction before rebuilding metadata. Reset the cache on page reload. This is display metadata, never a wallet/ownership cache.

## Other inspected areas

- `App.kt` collects multiple screen models at a broad scope; a live Focus countdown may invalidate that scope. No measured recomposition counts were available, so no blanket `@Stable`/`remember` refactor was attempted.
- Quest and Shop/Inventory grids use keyed lazy items; Profile is scrollable. No evidence here justifies a visual redesign, blur/shadow removal or wholesale list replacement.
- `PetViewModel` uses a bounded request deadline and separate collection updates (the previously fixed stale-state race remains covered). Interaction responses are server-authoritative; removing that boundary for apparent speed would be incorrect. Periodic refresh/request count under a real session remains unmeasured.
- Pet assets are loaded with `remember(assetPath)`/IO in Compose. Per-view native release and per-WebView pause are retained; global `WebView.pauseTimers()` would affect unrelated views and is not introduced.
- Case/Wheel use trusted asset origins, origin-checked completion, deterministic confirmed identities and render-failure fallback. Performance changes must preserve those contracts.

## Coverage and release gates

| Requested scenario | Coverage in this task |
|---|---|
| A: Pet Home idle 30 s | Real SVG renderer fixture; authenticated full-screen baseline N/A |
| B: interactions 30 s | Synthetic visual state changes; real backend latency N/A |
| C: animation transitions | Same renderer plus four-asset lifecycle smoke; native timing tests |
| D: all navigation screens | Existing isolated Shop/navigation flows; full authenticated Quest/Profile navigation baseline N/A |
| E: Case/Wheel open/close | Existing isolated renderer/reveal/Shop regression tests; no paid requests |
| F: background/foreground | Per-view pause/resume/release checks; full process-death acceptance N/A |

Before release: profile a capable physical Android device and a release-like build under the same scenarios; include actual SurfaceFlinger FrameTimeline/FrameMetrics and touch-to-feedback traces, renderer-process memory, repeated navigation leak checks and real pet selection. Audit R8 rules and shrinker behavior before enabling minification. Baseline Profiles are appropriate for `androidApp`, but generating meaningful authenticated startup/navigation journeys needs a dedicated Macrobenchmark/profile producer and safe test-account flow. Do not ship a fixture-generated profile as the app's production Baseline Profile. No release-like comparison or production-ready/60-FPS certification is claimed here.
