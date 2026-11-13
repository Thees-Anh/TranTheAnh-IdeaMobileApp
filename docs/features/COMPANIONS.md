# Capybara, Rabbit and cross-species equipment

Apply migration `20261109002200_capybara_rabbit.sql` after 001–021, then rebuild
and run `PawQuest (MainActivity)`. No hosted migration is applied by these tools.

| Pet | Required level | Minimum account XP | Renderer |
|---|---:|---:|---|
| Mochi | 1 | 0 | prototype_cat |
| Puppy | 5 | 400 | puppy |
| Capybara | 10 | 900 | capybara |
| Rabbit | 15 | 1400 | rabbit |

Account progression, care stats, inventory, equipment and furniture remain shared
when switching appearance. The existing owner-bound `pet_select_species` RPC
enforces levels. Migration 022 only publishes two new catalog rows; it does not
grant XP, Coins, food or accessories. Published locked species remain readable
so their artwork and level requirement can be previewed.

## Supplied artwork

`design/prototypes/pets/capybara.html` and `design/prototypes/pets/rabbit.html` are preserved as source files. The generator
extracts their SVG and animation styles, never the demo gameplay scripts, CDN
libraries or remote fonts. It builds offline Android assets and standalone
previews at docs/media/previews/capybara.html and rabbit.html. All six supplied states
are supported: idle, eating, drinking, sleeping, laughing and sad. Confirmed Play
and Touch use laughing; sleep/care priorities and server-backed quest emotions
reuse the existing companion state machine through registry capabilities.

The hardened WebView owns only rendering, blocks external navigation/network,
pauses in background and releases on disposal. Asset changes recreate the native
view. Portrait framing uses the native viewport and measured speech/care insets,
with one uniform scale for the room and pet. No model proportions are stretched.

## Equipment attachment

The same owned HEAD/FACE/NECK items now render on every shipped species.
Existing ownership/equip RPCs are unchanged. `tools/assets/pet_equipment_svg.py` records
attachment profiles in each original SVG's coordinates, after inspecting its
actual head and face groups:

| Pet | Animated parent | Hat center | Glasses centers | Collar baseline |
|---|---|---|---|---|
| Puppy | dog-head-group | 202,105 | 180,147 / 235,145 | y199 |
| Capybara | capy-head-group | 197,94 | 184,134 / 218,133 | y190 |
| Rabbit | bunny-head-group | 198,104 | 177,155 / 235,153 | y196 |

Headwear, glasses and collar inherit the head and whole-body transforms instead
of remaining in a screen overlay. Capybara's original orange is hidden while a
hat is equipped and restored when unequipped. Rabbit's floppy ears retain their
own animations. Mochi's existing attachments are preserved. Existing owned room
decorations remain behind each companion, including the carpet pattern.

For another pet, add a reviewed source extraction/attachment profile, register
the shipped asset/capabilities, create its preview and publish metadata in a new
migration. Unknown renderers remain unselectable until their art is installed.

## Inventory portraits

Four static PNG portraits are generated from the actual idle SVG groups by local
headless Chrome, with animations paused and network blocked. They ship in common
Compose resources, so Inventory cards use native Image composables rather than
additional WebViews. Images remain visible in full color while locked; selection
buttons still require server unlock status and the account level. Each image has
an accessible pet name/required-level description and preserves its aspect ratio.

The Android widget can load downsampled Capybara/Rabbit portraits from the same
resources; it does not add full-size bitmaps to RemoteViews.

## Reproduction and checks

```
python tools/assets/build_companions.py
python tools/assets/build_pet_portraits.py
python tools/assets/build_companions.py --check
```

The portrait generator requires local Chrome; generated PNGs are committed assets
and ordinary Gradle builds do not require Chrome. Browser artwork checks use
`tools/testing/test_companion_art.mjs` against an isolated headless Chrome on port 9228.
They test three species, six animations, three target portrait ratios plus a
compact phone, with all three accessories equipped: 432 animation/bounds samples.
`supabase/tests/new_companions.sql` tests exact level boundaries and selection.
`tools/testing/test_new_companions_android.py` exercises the loopback SQL fixture only.
See TEST_REPORT.md for actual results and manual limitations.
