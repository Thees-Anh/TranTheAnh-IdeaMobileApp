# Shop decoration catalog

## Installation

Apply `supabase/migrations/20261109002300_decor_catalog.sql` in Supabase SQL Editor after migrations 001-022, then rebuild/run the Android app. This migration has only been exercised locally; it has not been applied to the hosted project.

The update adds 20 cosmetics and preserves the existing seven catalog entries and ownership. All prices are server-controlled. Each accessory/furniture item is uniquely owned (quantity 1); repeated purchase request IDs return the original receipt. A different request for an already-owned unique item is rejected without charging Coins. Cosmetic purchases grant no XP or stat bonuses.

## Catalog

| Item | Category | Slot | Coins |
|---|---|---|---:|
| Vương Miện Vàng | ACCESSORY | HEAD | 150 |
| Mũ Phù Thủy | ACCESSORY | HEAD | 140 |
| Mầm Cây & Hoa | ACCESSORY | HEAD | 80 |
| Tai Nghe Pastel | ACCESSORY | HEAD | 160 |
| Kính Ngôi Sao | ACCESSORY | FACE | 110 |
| Kính Quý Tộc | ACCESSORY | FACE | 130 |
| Nơ Chuông Vàng | ACCESSORY | NECK | 90 |
| Khăn Len Ấm | ACCESSORY | NECK | 95 |
| Cánh Thiên Thần | ACCESSORY | BACK | 200 |
| Balo & Bóng Bay | ACCESSORY | BACK | 175 |
| Cửa Sổ Cầu Vồng | FURNITURE | WINDOW | 220 |
| Đồng Hồ Mèo | FURNITURE | CLOCK | 150 |
| Đèn LED Sao | FURNITURE | LIGHTS | 120 |
| Kệ Sách Gỗ | FURNITURE | BOOKSHELF | 160 |
| Trụ Cào Móng | FURNITURE | CAT_TREE | 280 |
| Đệm Vương Miện | FURNITURE | BED | 210 |
| Chậu Cây Cười | FURNITURE | PLANT | 110 |
| Bàn Trà & Bánh | FURNITURE | TEA_TABLE | 190 |
| Máy Đĩa Than | FURNITURE | GRAMOPHONE | 250 |
| Đèn Hoa Linh Lan | FURNITURE | LAMP | 180 |

## Equipment and placement

HEAD, FACE, NECK and the new BACK slot each hold one item. Replacing equipment retains ownership of the previous item. The shared ownership/equipment configuration works with Mochi, Puppy, Capybara and Rabbit. Inventory offers Equip/Unequip and Place/Store using the existing repositories; Shop uses the existing authenticated purchase RPC and confirmation flow.

Furniture uses fixed anchors: WINDOW, CLOCK, LIGHTS and BOOKSHELF on the wall; CAT_TREE on the left; BED, PLANT and LAMP on the right; TEA_TABLE at the front right; GRAMOPHONE at the front left. Existing CARPET remains supported. New BED/LAMP variants replace the corresponding slot, retaining previous ownership. All ten new furniture pieces can coexist. There is no drag-and-drop placement.

The supplied SVG artwork and CSS animations are reused offline. A uniform transform adapts the source 600x440 coordinates to the portrait scene. HEAD/FACE follow each animated head; NECK/BACK follow the body. Per-species attachment profiles accommodate wide Capybara features and Rabbit ears. The Capybara orange is hidden while a HEAD accessory is worn. Furniture has separate background/foreground layers and side anchors to keep the pet visible. The scene scales uniformly within measured dialogue/control safe insets. Animations respect reduced-motion preferences. No GLB assets are invented; the retained legacy 3D path skips unsupported SVG-only decorations safely.

Shop and Inventory show generated item thumbnails, descriptions and friendly slot labels. Category changes reset the grid scroll position. Missing/legacy artwork falls back to the existing icon; unknown assets are filtered before sending scene payloads. Scene bundles remain local and use the existing WebView lifecycle/cleanup.

## Reproducibility

Canonical metadata: `design/assets/decor/catalog.json`; shapes: `design/assets/decor/accessories.svg` and `design/assets/decor/furniture.svg`; animation rules: `design/assets/decor/animations.css`.

```powershell
python tools/assets/import_decor_catalog.py --check
python tools/assets/build_decor_assets.py
python tools/assets/build_prototype_pet.py --check
python tools/assets/build_companions.py --check
```

To regenerate scene bundles, omit `--check`. Thumbnail source HTML is generated into `build/decor-thumbnails`; `node tools/assets/render_decor_thumbnails.mjs` renders PNGs using an isolated Chromium instance on CDP port 9228. Checked-in Compose PNG resources need no browser during a normal Gradle build.

Database tests: `supabase/tests/decor_catalog.sql` and `tools/testing/test_database.py`. Renderer checks: `node tools/testing/test_decor_stage.mjs` (isolated Chromium on port 9228). Native fixture smoke: `python tools/testing/test_decor_android.py` requires the disposable SQL adapter and its loopback-only APK; it refuses a production backend APK.
