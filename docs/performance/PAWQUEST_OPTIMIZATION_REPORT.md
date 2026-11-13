# Optimization report — 2026-11-11

## Outcome

Reduced measured unchanged-update overhead and removed redundant display work without changing visual assets, economy rules, probabilities, authentication or navigation behavior. **No overall FPS improvement, stable 60 FPS or production-readiness certification is demonstrated.** Software-rendered interaction tail intervals remain a problem and require physical-device investigation.

## Application changes

| File | Change and reason |
|---|---|
| `shared/src/androidMain/kotlin/com/pawquest/presentation/scene/PrototypePetView.kt` | Compare state/equipment/room before JSON creation. Copy display lists so later caller mutation remains detectable. Deduplicate viewport updates, coalesce posted resize work and cancel it on release. Replace per-node inline pause styling with one per-document stylesheet/root class; deduplicate lifecycle calls. View-owned display data only, no authoritative ownership/balance cache. |
| `shared/src/commonMain/kotlin/com/pawquest/presentation/gacha/MysteryCaseContent.kt` | Unmount the obscured 320dp inline WebView during fullscreen confirmed playback; preserve its layout slot. One case renderer instead of two during that phase. |
| `shared/src/androidMain/kotlin/com/pawquest/presentation/gacha/LuckyWheelPreview.kt` | Check unchanged receipt before constructing configuration. Initialize identical idle configuration once per document; reload resets it. Keep origin checks, confirmed identities, skip and completion behavior. |

No Filament quality, model, animation-clock or engine ownership changes were made. No speculative Compose stability annotations, architecture rewrite, new runtime dependency, R8 enablement or fabricated Baseline Profile was introduced.

## Reproducible evidence

New test-only `PetPerformanceActivity`/`PetPerformanceDeviceTest` and manifest entry live in `shared/src/androidDeviceTest`. `tools/testing/profile_pet.py` builds no app data, uses no credentials and performs no economic RPC. It records a 30-second idle SVG fixture, 30 seconds of visual state changes every 500ms, a 20,000-identical-update burst, four-asset lifecycle smoke and a bounded Perfetto scheduling/gfx trace.

Raw numeric samples are retained in [pet_renderer_measurements.json](pet_renderer_measurements.json). Full traces/device logs are ignored local artifacts under `build/performance/before` and `build/performance/after`; the first post-change sample/trace is also retained there as `sample1.*`.

Same AVD, software graphics, 60 Hz/1080×2400 configuration and debug fixture were used. One baseline and two post-change samples were collected; the second post-change run investigated worse interaction P95. This is not a statistically controlled benchmark: host scheduling, Chromium/JIT warm-up and renderer-process/host GPU CPU were not controlled or included in app-only counters.

| Metric | Before | After (two runs) | Improvement |
|---|---:|---:|---|
| Average presented FPS | N/A | N/A | N/A; no presentation measurement |
| P95 presented frame time | N/A | N/A | N/A |
| Android deadline-based janky frames | N/A | N/A | N/A |
| App PSS, idle | 125,199 kB | 125,056–125,384 kB | −0.15% to +0.11%; no memory benefit established |
| App CPU, idle (one core=100%) | 80.42% | 83.75–84.52% | −5.10% to −4.14%; no CPU benefit |
| SVG rAF cadence, idle | 46.98 Hz | 46.97–47.67 Hz | −0.04% to +1.47%; no clear gain |
| SVG rAF cadence, interaction | 40.02 Hz | 39.26–39.63 Hz | −1.90% to −0.97%; worse in these samples |
| SVG callback P95, idle | 33.40 ms | 33.40 ms | No improvement |
| SVG callback P95, interaction | 33.50 ms | 49.90–50.00 ms | Worse; cannot establish code versus environmental causality here |
| SVG callback P99, interaction | 50.00 ms | 50.10 ms | No meaningful improvement |
| Callback intervals >25ms, idle | 26.61% | 24.76–25.35% | 1.26–1.85 percentage points fewer; not Android jank |
| Callback intervals >25ms, interaction | 44.84% | 44.83–46.79% | −1.95 to +0.01 percentage points; inconsistent |
| App GC count per 30s scenario | 1 | 1 | Unchanged |
| 20,000 unchanged native update calls | 148.86 ms | 41.38–59.10 ms | **60.30–72.20% less elapsed cost** |

rAF cadence is calculated as 1000 / mean callback interval; it is **not displayed FPS**. P95/P99 are interval percentiles, not GPU execution times. CPU may exceed 100% when multiple app threads run. App PSS excludes the sandboxed WebView process and host emulator graphics memory. Main CPU times and full percentiles are in the JSON. Neither these PSS samples nor passing lifecycle tests prove absence of leaks.

The repeat confirms cheaper unchanged updates, but not better interactive smoothness. Interaction P95 regressed in both after samples, even though mean interval changed by about 1–2% and P99 remained around 50ms. Retain this as an unresolved performance acceptance item; do not describe it as a 60-FPS fix. Removing the obscured Case WebView is a lifecycle/resource reduction verified structurally; its frame-time benefit was not measured in the pet fixture.

## Validation

Executed one post-optimization application build (`:androidApp:assembleDebug`) with focused host suites: `Pet*Test`, `PuppyTest`, `FocusTest`, `ItemCaseViewModelTest`, `LuckyWheelViewModelTest`, `InventoryViewModelTest`; passed. A separate focused host invocation passed all 11 `PetAnimationControllerTest`/`PetCareAnimationTest` cases. Test APK assembly was also required for baseline and post-change instrumentation.

Profiling fixture passed before edits and twice afterward, including four packaged pet SVGs and view pause/resume/release. **All 17 additional emulator regression cases passed**: cache/lifecycle (1), Case renderer (5), Wheel renderer (4), Case reveal (2), Wheel/Shop/Pet Exchange (4), Item Exchange (1). The cache/lifecycle test checks duplicate JS dispatch suppression, detection of caller list mutation and pause/resume root state. All use isolated fake economy data. No real paid spin, case opening, redemption or production migration is used.

Not Tested: physical ARM device performance, release-like/debug comparison, actual presented FPS/FrameTimeline jank, full authenticated navigation baseline, backend interaction latency/pet selection, real process-death recovery, retained-heap leak analysis and native Filament GPU profiling. Existing backend/functional tests are not substitutes for those measurements.

## Repeat and release gates

Build the test APK with `./gradlew :shared:assembleAndroidDeviceTest`, then use `python tools/testing/profile_pet.py --label before --serial <device>` and `--label after` against the corresponding source/build revisions. Labels only select output filenames; they do not switch source versions. Preserve baseline binaries/revision information before rebuilding, keep device/build/graphics identical, and take multiple samples after warm-up. Do not compare a SwiftShader debug run with a hardware-rendered release run as a before/after result.

For a physical-device acceptance pass, use Android Studio System Trace/Perfetto with SurfaceFlinger FrameTimeline and main/RenderThread/WebView tracks, then CPU and allocation profiling for actual Pet Home. Record presented frame durations at the device's refresh interval, UI versus backend latency separately, GC, and memory across repeated navigation/background cycles. Keep the 16.67ms target for a 60 Hz display; no unlimited render loop is needed.

Before production release, validate the currently disabled minification/resource shrinking with actual serializers, Supabase/Koin and native dependencies. Add an AGP-compatible Macrobenchmark/Baseline Profile producer only with a safe authenticated test journey, then verify profiles cover the actual `MainActivity`/SVG route and normal navigation. Neither startup improvement nor a valid production Baseline Profile can be inferred from this test-only activity. See the audit for prioritized remaining investigation.
