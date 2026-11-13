# PawQuest mascot identity

Original project artwork generated with the built-in `imagegen` tool, 2026-11-11. No external stock or remote runtime assets. Transparent masters: `masters/`; launcher previews: `previews/`. PNGs in Android resources are bounded technical exports, not full-resolution masters.

Art direction: premium softly lit 3D cream/apricot cat, lavender scarf and gold star, clean silhouette and readable eyes at launcher size. Launcher retains the normal/hungry/bored aliases. Widget retains the selected species and verified mood; cats use matching expressions, all species get explicit happy/hungry/thirsty/tired/sleeping badges. Other species keep a consistent neutral portrait rather than inventing a new backend mood.

Prompt set (built-in generation, then reference-based edits; no CLI/API fallback):

- Base: one premium cozy-game kitten head and short upper chest, cream-white/apricot calico patch, dark chocolate eyes, pink nose, small happy smile, lavender scarf/gold star clasp, soft clay/animated-film rendering, transparent square, centered silhouette with clearance, no text/tile/border/collage.
- Hungry: same reference kitten identity/material/accessory/framing; worried inner eyebrows, pleading eyes, small open mouth and lowered ears; adorable and healthy, no bowl/props.
- Bored: same reference kitten; half-lidded eyes, tiny neutral pout, relaxed ears, healthy and calm; no props.
- Puppy: reference style/accessory/lighting only; new golden-caramel/cream floppy-eared dog, cream muzzle, warm dark eyes and happy smile.
- Rabbit: reference style/accessory/lighting only; new cream-white rabbit, two complete upright ears, pink nose and dark glossy eyes.
- Capybara: reference style/accessory/lighting only; new caramel capybara, broad rounded rectangular muzzle, high small round ears, calm smile, recognizable species anatomy.

Every output: one isolated transparent portrait, no text, frame, background or collage; readable at 48px. Original generated images are preserved in `masters/`.

Rebuild on Windows: `python tools/assets/generate_launcher_icons.py`; verify with `--check`. Uses built-in System.Drawing only for deterministic PNG sizing/density exports, then writes adaptive/legacy/themed resource wrappers and small vector status symbols. No paid image API is called during rebuild. The debug-only `com.pawquest/.branding.BrandingPreviewActivity` shows actual Android adaptive masks and the real 2×2 widget layout with synthetic data; it is excluded from release.
