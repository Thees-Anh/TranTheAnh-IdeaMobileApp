# Fixed room decoration

Apply `supabase/migrations/20261105001300_room_furniture.sql` **once after 012**.
Buy furniture, open Inventory → Home, and select Place or Store. Changes appear
in Pet Home after server confirmation. This is fixed-slot decoration; there is
no drag-and-drop or client-supplied transform API.

## Shared state and ownership

`RoomFurniture` contains slot (BED/LAMP/CARPET), item ID, logical asset ID and
placement timestamp. RoomRepository, PlaceFurniture and StoreFurniture live in
commonMain without rendering dependencies. Android supplies the Supabase
repository through Koin. InventoryViewModel publishes confirmed room state;
LocalRoom passes that state into the existing Android PetScene.

`furniture_slots` defines allowed catalog item/slot combinations. `room_furniture`
has one row per user/slot and foreign keys to both allowed slot metadata and the
owner's unique inventory row. Category triggers keep mapped items as FURNITURE.
Authenticated users can SELECT their own room through RLS; direct client writes
are denied. Slot metadata is authenticated read-only. Anonymous access is denied.

`room_set(p_slot,p_item_id)` derives the user from `auth.uid()`, locks the wallet
to match purchase/care ordering, verifies slot compatibility and locks owned stock.
A null item ID stores/removes the furniture from that slot. The same setter can
be retried safely; placement does not spend Coins, consume ownership or grant XP.
Owned inactive catalog items can remain placed. A new owned variant mapped to the
same slot replaces its previous item; both items remain in inventory.
Timestamps use timestamptz instants, parsed as shared Instant values.

The UI blocks overlapping room/equipment operations. Ambiguous network failures
retain the exact setter command for Retry, without pretending the appearance
changed. Terminal ownership/slot rejection preserves the prior room. A revision
guard prevents old refresh responses from overwriting a confirmed mutation.
Startup, app resume and Inventory entry reload authoritative room state, so
configuration survives app restart. Account changes destroy protected ViewModels.

## Fixed Android anchors and portrait view

Coordinates below are normalized **scene units**, not screen pixels. +Y is up;
the camera is on positive Z. Furniture is a static scene sibling of the cat,
so cat animation cannot drag the bed, lamp or carpet around.

| Slot | Anchor (x, y, z) | Local maximum half extents |
|---|---|---|
| BED | (-0.85, -0.94, -0.70) | (0.34, 0.22, 0.25) |
| LAMP | (0.85, -0.94, -0.80) | (0.19, 0.48, 0.19) |
| CARPET | (0, -0.97, -0.70) | (0.97, 0.03, 0.52) |

The bed is lower-left, the lamp lower-right and the carpet below/behind the cat.
Local prop geometry is authored around X/Z zero, with its bottom at local Y zero.
The renderer rejects out-of-budget, off-origin or invalid bounds. Furniture's
front edge must remain behind Z=-0.12. It retains authored proportions at scale 1.
The three bundled props fit within the current cat's conservative all-animation
envelope, preserving its prominence without increasing camera distance.

The camera keeps the cat center as its target. When furniture exists, it gains
a small elevation (sin(angle)=0.18, about 10.4 degrees) to reveal the carpet top.
Distance is preserved under this rotation; the existing enclosing-sphere fit
supports any camera orientation. Projection uses the actual viewport aspect and
unions room, pet and accessory bounds. This supports portrait 9:16, 19.5:9 and
20:9 phones and shorter embedded panels. Conservative fitting makes the cat
smaller in short panels; it prioritizes keeping all poses visible.

SceneView nodes are non-editable and camera manipulation stays disabled.
Furniture is not a cat descendant, so its collision hits do not trigger Pet Touch.
Compose controls remain outside the native scene viewport and respect existing
system insets. Furniture is background decoration; the cat can occlude it.

## Assets and resource lifetime

Original simple development assets are bundled at `models/furniture/bed.glb`,
`lamp.glb` and `carpet.glb`. Regenerate with `python tools/assets/generate_dev_furniture.py`.
They use solid PBR materials and no textures, extra lights, skins or animations.
The lamp is decorative geometry; it does not add a dynamic light source.
These are development props, not final room art.

Only three allowlisted logical asset IDs resolve to local file paths. Missing,
unknown, malformed or incorrectly sized assets produce a preview-unavailable
message while keeping the cat and remaining furniture visible. Server placement
is preserved so installing a corrected asset can restore its preview.
Adding a new variant requires an owned catalog item/slot mapping, a reviewed local
asset mapping and footprint, and extension of the shared code-to-slot selector.

File reads run on Dispatchers.IO. A scene-local cache holds at most three GLB
buffers. Filament calls run on the main thread. Native nodes are diffed by slot
and asset ID: unchanged props are retained; changed/removed models detach from
the scene before gltfio resource destruction. Destroying the scene clears all
furniture before the shared loaders/engine. No furniture work runs per animation
frame and there is no continuously running room timer.

## Actual verification

- `python tools/testing/test_database.py`: two fresh replays of all 13 migrations and
  SQL suites; ownership, wrong slots, same-slot variant replacement, RLS,
  idempotent place/store and concurrent placement tests passed.
- `python tools/validation/verify_room_assets.py`: actual GLB bounds match the Android
  anchors and remain within the pet envelope. Total: 444 triangles, 34,468 bytes,
  no textures/animations/external resources.
- Local Khronos glTF validator: all three props have zero errors/warnings.
- Android host tests cover RPC payloads/restoration, confirmed UI state and
  retry behavior, plus straight/elevated portrait frustum bounds.
- Emulator fixture smoke: Place/Store UI, process restart restoration,
  unchanged-resume native reuse, unavailable bed with other props retained,
  and restoring only the bed model passed. Cat touch remained functional;
  furniture hits did not trigger pet care. Idle/Sleeping captures at all three
  portrait ratios were produced; compact Idle and tall Sleeping were inspected.

The emulator checks establish basic functional/resource behavior, not a measured
physical-device FPS or GPU-memory benchmark. Test production art on representative
phones before increasing footprint, triangle count, materials or texture sizes.
The local HTTP fixture is only a test double and must never be deployed or left
in the app's configuration. No live-project placement is performed by these tests.
