# Android pet scene

Static development bed/lamp/carpet GLBs, fixed portrait anchors and incremental
resource ownership are described in [ROOM_DECORATION.md](../features/ROOM_DECORATION.md).

Original developer hat/glasses/collar GLBs and inspected rigid-node attachments
are documented in [EQUIPMENT.md](../features/EQUIPMENT.md). Current cat has no skeleton/skins;
accessories inherit its animated geometry transform and use verified all-pose
envelopes for portrait framing. Replacement rigs require a new attachment contract.

Pet Care now drives the Android single-player animation controller from confirmed
PetViewModel results. Touch uses cat collision hits instead of the full viewport.
Framing includes the union of static meshes/accessories and authored all-pose
bounds. See [animation integration and limits](ANIMATIONS.md).

## Development cat

The original low-poly ginger cat is stored at
`shared/src/androidMain/assets/models/cat.glb`. It was generated for this project
after the user requested a development model. It uses solid PBR materials and
contains no external textures or downloaded geometry. It now includes five
simple development animation clips; see [animation setup](ANIMATIONS.md).
Recreate it with Python 3 using `python tools/assets/generate_dev_cat.py`. The generator
and generated model are project assets and can be modified with the application.

Replace this file with a self-contained **binary glTF 2.0 / GLB** cat exported
from Blender to use a production model. Export the complete cat, including ears,
paws, accessories, and tail, with correct accessor bounds, +Y up, and its face
toward +Z. Keep textures and buffers embedded. Root translation/units need not
match the development model: the scene normalizes uniformly and centers the
asset's all-pose envelope (or static bounds as a fallback). Avoid placing unrelated objects far away from the cat,
since every exported object contributes to framing.

Assets are packaged at build time. Rebuild/reinstall after replacing the GLB;
the Try again action reloads packaged assets, not files outside the APK.
`PetScene(assetPath = "models/other.glb")` supports another bundled asset path
when integrating a different model. Missing and unreadable/invalid assets have
separate states. No model is fetched from the network.

## Rendering boundary

SceneView 2.3.3 and its matching Filament/gltfio/utils 1.68.2 live in
`androidMain` only. Their versions match the
[published SceneView metadata](https://repo1.maven.org/maven2/io/github/sceneview/sceneview/2.3.3/sceneview-2.3.3.pom).
The stable 2.3.3 API is pinned deliberately; upgrading to SceneView 4.x requires
reviewing its changed Compose and ownership APIs rather than replacing version
numbers independently. Keep all three Filament artifacts aligned with SceneView.

Shared navigation accepts an optional Compose content slot for Pet Home.
MainActivity supplies `PetScene`; commonMain has no Filament or Android imports.
Previews and other platforms retain their existing shared placeholder.
The renderer fills the safe scaffold content area beneath the Pet Home title;
bottom navigation stays outside the viewport. It does not live in a scroll list.

SceneView initializes a camera, directional key light, and its bundled neutral
image-based lighting with a pale blue background. Postprocessing is disabled:
the API 37 emulator's software OpenGL GPU produced blank white output with
SceneView's default postprocessing path, while basic PBR rendering works.
This milestone does not require bloom, tone mapping, or other post effects.
GLB file IO and direct-buffer allocation run on IO;
gltfio/Filament construction and GPU upload run on the main thread as required
by their native resource ownership. Large production models should be profiled
for upload latency; the development asset is intentionally small.

## Portrait framing

The cat's asset bounding box determines one **uniform** normalization scale.
The authored all-pose envelope center is translated to world origin. A camera
at +Z looks at that origin.
A bounding sphere encloses all eight box corners, including depth. Camera
distance uses the smaller horizontal/vertical half field of view with 12%
extra distance. Near/far planes enclose the entire sphere. Projection uses the
actual rendered viewport aspect, with a 45-degree vertical field of view.

`onResized` recalculates projection and distance after SceneView updates its
viewport. No fixed phone resolution or model X/Y stretching is used. This
conservative fit keeps even wide models fully visible in tall portrait windows.
Gestures and SceneView autoplay are disabled. The Android animation controller
uses one clip at a time and an authored sidecar bounds envelope encompassing
every pose. Animation switching does not recalculate the camera. glTF accessor
bounds alone cannot guarantee visibility for animated assets. See
[clip names, debug controls and bounds requirements](ANIMATIONS.md).

## Ownership and lifecycle

Each mounted PetScene creates one Android `PetSceneView`, which owns its engine,
loaders, camera, light, scene, and render surface. It observes the host lifecycle
to pause/resume frame callbacks. Tab changes remove the view; detach, lifecycle
destruction, and Compose `onRelease` all reach its idempotent `destroy()`.
The override detaches model entities and destroys the gltfio asset before calling
SceneView's native cleanup. It also explicitly releases the default environment,
reflection texture, color grading resource, and collision system. The asset
loader owns GLB entities, so they are not
independently destroyed a second time through ModelNode. Partially loaded assets
remain owned by the loader and are also released on destruction.
Asset reads use a cancellable Compose effect. Queued GPU uploads check attachment
before running. A scene is recreated when Pet Home is revisited; native scene
resources are not held by a shared ViewModel or application singleton.

## Verification

Build/test: `./gradlew :androidApp:assembleDebug :shared:testAndroidHostTest
:androidApp:lintDebug`. Android host framing tests project box corners into the
camera frustum at 9:16, 19.5:9, 20:9 and a shorter content viewport, checking
visibility and clipping planes. Existing common tests run in the same host suite.

On Android, inspect Pet Home at compact and tall portrait sizes; ears, paws and
tail should stay visible and centered. Switch away/back repeatedly, background
and resume, and recreate the Activity. `adb logcat -s PawQuestScene` reports
model load and scene destruction events. Verify there are no native crashes.
For missing-asset testing, temporarily use an absent asset path in the Activity's
PetScene call, then restore the normal path. For invalid-asset testing, use an
invalid GLB in a local debug build; never ship that fixture as the cat.
Actual execution results and limitations are in `IMPLEMENTATION_LOG.md`.
