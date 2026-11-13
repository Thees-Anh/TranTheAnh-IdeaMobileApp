# Cat animation controller

Puppy is now a separate supplied SVG rig with six exact states:
idle/eating/drinking/sleeping/laughing/sad. Its Compose component is PawQuestDog;
Play/Touch map to laughing and verified Daily completion queues 4000 ms of
celebration on visible presentation. Hunger ≤25 or an unresolved missed quest
selects sad after temporary actions end. See [PUPPY.md](../features/PUPPY.md).

Migration 016 adds confirmed touch-to-wake: the server clears persisted sleep
after saving elapsed Energy, then TOUCH selects HAPPY temporarily and returns to
IDLE. A replay cannot wake a later sleep. Full-stat/empty-stock rejections show
feedback without starting a success animation. Species renderer integration and
its six-state contract are documented in [PET_COLLECTION.md](../features/PET_COLLECTION.md).

The active 2D renderer also supports **DRINKING**, using the supplied prototype's
drinking face/prop animation. Successful DRINK care results select it temporarily,
then return to IDLE; rejected or replayed outcomes do not overlap commands.
The retained inactive GLB controller accepts optional `drink` / `drinking` clips
and safely falls back when absent. No new GLB asset is assumed.

## Confirmed Pet Care integration

Android PetScene observes PetViewModel's lifecycle-collected state through the
presentation PetController. `petAnimationCommand` projects confirmed Feed/Play/
Touch results to EATING/PLAYING/HAPPY, keyed by server request UUID. An invalid,
pending, mismatched or expired reaction cannot start its requested clip. Persisted
sleep takes priority and selects SLEEPING until server reconciliation ends it.
When temporary ViewModel reactions expire, the command becomes IDLE.

Command identity is separate from animation state: repeated Compose updates with
the same token do not restart playback; a new successful request of the same
type can restart it. The existing single-player controller resets rest transforms
and one clip clock, so rapid input never creates concurrent players. Debug
commands are temporary, ignored while care is pending or a confirmed reaction/
sleep owns the scene, and cleared when confirmed care takes over. They grant no
stats or inventory effects.

Physical Touch now uses SceneView's supported `setOnGestureListener` single-tap
callback and collision hit node ancestry, accepting only the cat root/children.
The whole scene background is no longer clickable on Android. Collision picking
is bounding-volume based, not per-pixel skinned silhouette picking; small empty
areas inside model bounds can still count. An explicit Compose accessibility
action permits TalkBack users to pet the cat without coordinate picking. The
native callback is cleared on release. Camera gestures stay disabled.

Framing unions the authored all-pose sidecar with static model/accessory bounds,
then uniformly normalizes and centers that envelope. Animation switching never
changes camera parameters; viewport resize recomputes projection from the actual
width/height. A valid all-pose envelope is still required for arbitrary animated
replacement meshes/accessories; static bounds cannot prove unknown deformations.
Missing clips still fall back to Idle/rest pose through the existing controller.
No new dependency or database migration is required by this integration.

## Model contract

`PetAnimationState` is a shared domain enum: IDLE, HAPPY, EATING, SLEEPING,
PLAYING. Android maps states to named GLB clips; it never relies on clip order.

| Logical state | Preferred GLB name | Accepted alias |
| --- | --- | --- |
| IDLE | Idle | Case-insensitive |
| HAPPY | Happy | Case-insensitive |
| EATING | Eating | Eat |
| SLEEPING | Sleeping | Sleep |
| PLAYING | Playing | Play |

Names are trimmed and compared case-insensitively. Preferred names win over
aliases; duplicate names resolve to the first valid clip in asset order.
Unnamed clips, unrelated names, non-finite durations, and zero-duration clips
are ignored. If the requested clip is absent, play Idle when available;
otherwise restore the authored rest pose. Debug status reports the requested
state and the actual clip/fallback. Missing clips are never passed to native APIs.

All clips loop until a different state is requested. Reselecting the current
state preserves playback. This milestone does not add feeding, rewards, pet
stats, gameplay actions, audio, or automatic state transitions.

## Development cat

The original generator now exports five real glTF animation clips. They are
simple rigid-transform demonstrations, not production skeletal character work:
Idle breathes uniformly, Happy bounces/sways, Eating nods, Sleeping lies on its
side with gentle breathing, and Playing turns from side to side.
Run `python tools/assets/generate_dev_cat.py` to regenerate both `cat.glb` and
`cat.animation-bounds.json` in `shared/src/androidMain/assets/models`.

The GLB uses LINEAR translation/scale and quaternion rotation channels on an
authored parent pivot. All geometry remains parented below that pivot. Model
normalization is applied to gltfio's synthetic root, which clips do not target.
Uniform breathing scale preserves proportions. No new textures or dependencies
are required.

## Playback ownership and switching

`PetAnimationController` is an Android presentation state machine backed by a
small `AnimationPlayer` adapter. `FilamentAnimationPlayer` uses Filament 1.68.2's
`Animator.getAnimationName`, `getAnimationDuration`, `applyAnimation`,
`resetBoneMatrices`, and `updateBoneMatrices`, plus TransformManager to restore
the authored local transforms.

SceneView autoplay remains disabled and its `playingAnimations` collection is
empty. The scene's single `onFrame` callback advances exactly one selected clip
on the main thread. There are no per-animation coroutine jobs or overlapping
players. Every sample starts from cached rest transforms; switching clips resets
the clip clock before applying the new pose. This prevents stale TRS channels
from an earlier clip affecting a later one. Skin matrices are then updated.
Switches are immediate hard cuts; cross-fading is deliberately deferred.

The frame clock pauses on lifecycle pause and caps long frame gaps at 100 ms,
so returning from background does not skip ahead abruptly. The scene disposes
the controller and clears its frame/status callbacks before destroying the
model/native resources. Debug selection is temporary rather than persisted.
Leaving Pet Home disposes its scene; revisiting projects the current confirmed
Pet state, including an ongoing Sleep.

This milestone exercises rigid TRS clips. Skeleton APIs are wired but require
validation with a rigged production cat. Morph-target clip defaults are not
restored by the TRS reset adapter; morph animation assets require an explicit
default-weight reset extension before use when states target disjoint weights.

## Stable portrait framing

Place a sidecar beside a replacement animated GLB:

```json
{
  "min": [-1.7, -0.5, -1.7],
  "max": [1.7, 3.0, 1.7]
}
```

For `models/cat.glb`, the sidecar must be named
`models/cat.animation-bounds.json`. These numbers are an example only: export
the actual union of all animated poses (including skins, morphs and accessories)
in the GLB's asset coordinates. All values must be finite with max > min.
The generator computes a conservative envelope from every mesh box corner,
maximum uniform clip scale, and maximum translation, so every intermediate
rotation and LINEAR sample fits as well.

The scene normalizes uniformly and centers on this one all-pose envelope. Camera
distance and clipping planes are calculated once for that envelope and updated
only when viewport size changes, never when animation state changes. The
bounding-sphere fit therefore accommodates Sleeping's horizontal pose in a
portrait viewport. Gesture camera movement remains disabled. Per-mesh culling
is disabled for the pet so stale skinned/accessor boxes cannot hide parts.

Without a sidecar, the scene reserves twice the static half-extents. This is a
conservative fallback for bounded in-place clips, not a guarantee for arbitrary
root motion or large deformations. Supply a verified all-pose envelope for
production animated assets. Automatic sampled bounds would not prove extrema
for arbitrary cubic/skinned animation, so the renderer does not claim that.

## Temporary debug controls

Run the **debug** Android application and open Pet Home. Five buttons below the
viewport select Idle, Happy, Eating, Sleeping and Playing. The status line shows
the actual clip or missing-clip fallback. Controls use wrapping rows inside a
bounded scrollable panel and keep bottom navigation separate. They are enabled
by `BuildConfig.DEBUG` in MainActivity and absent from release builds.
To remove them entirely, stop passing `showDebugControls = BuildConfig.DEBUG`.

## Verification

Run `./gradlew :androidApp:assembleDebug :shared:testAndroidHostTest
:androidApp:lintDebug`. Controller tests use a fake adapter to verify reordered
and aliased clips, missing/invalid clips, rest fallback, switching, reselecting,
single-clip samples, looping, lifecycle time, and disposal.
On-device checks must include all five states at compact, 9:16, 19.5:9, and 20:9
phone sizes, rapid switches, tab removal, and background/resume. Verify the full
cat remains visible while clips run and camera framing does not jump at a switch.
Actual execution results are in `IMPLEMENTATION_LOG.md`.

API reference: [Filament 1.68.2 Animator source](https://github.com/google/filament/blob/v1.68.2/android/gltfio-android/src/main/java/com/google/android/filament/gltfio/Animator.java).
