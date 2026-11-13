# Puppy companion

Apply `20261106001900_puppy.sql` once after migrations 001–018, then rebuild
Android. Inventory → Pets displays Puppy, unlocked at **Lv.5 (400 total XP)**.
Selection is validated on the server and persists through restart, sharing the
existing progression, care stats, food, wallet and room. The basic food display
name becomes **Pet food**; its code, ID, price and purchase rules are unchanged.

## Original artwork and component

The user's complete CSS and SVG are retained in `design/assets/pets/puppy/puppy.css` and
`design/assets/pets/puppy/puppy.svg`. They are code-native vector assets, not a generated
replacement image. `tools/assets/build_puppy.py` bundles them into the offline Android
asset and [standalone preview](../media/previews/puppy.html). Check reproducibility with:

```powershell
python tools/assets/build_prototype_pet.py --check
python tools/assets/build_puppy.py --check
```

The Compose component is `PawQuestDog(state: PuppyAnimationState, ...)`, the
project-native counterpart of the requested `<PawQuestDog state={...}>` API.
No React runtime or replacement of KMP/Compose is introduced. Its SVG root keeps
`id="pawquest-dog-svg"` and exactly `class="state-${state}"`, with these six values:

| State | Trigger |
|---|---|
| idle | Normal resting state |
| eating | Confirmed Feed |
| drinking | Confirmed Drink |
| sleeping | Server-persisted sleep |
| laughing | Confirmed Play/Touch, or a verified Daily completion celebration |
| sad | Hunger ≤25 or an unresolved missed quest |

Screenshots: [Pet Home](../media/previews/puppy-home.png),
[compact large text](../media/previews/puppy-large-font.png), [sad](../media/previews/puppy-sad.png).

Play uses the supplied laughing state because the supplied artwork has no separate
playing clip. Rejected actions do not start a success animation. Touching the
dog's actual SVG hit area uses the existing server touch/wake operation.

The supplied character paths and keyframes are preserved. The builder adds a
uniform portrait room transform, extended pastel background and existing owned
furniture behind the dog. It wraps heart anchors so CSS transform animation does
not overwrite their SVG translation, which would move hearts to the origin.
Compose measures the speech bubble and care panel, and passes their actual
insets to the SVG viewport fit. The whole animated character and effects scale
uniformly into the space between them, including large-font compact layouts.
The offline WebView keeps network/navigation blocked and releases resources on
disposal. Reduced-motion CSS is included. Unknown state input falls back to idle.

## Server facts and 4-second celebration

`pet_companion_context(timezone)` returns server time, the latest elapsed Daily
session ID/time, and a missed-quest flag for the authenticated user. It does not
change session status, claim a reward or modify Coins/XP. The private snapshot
helper cannot be called by clients with a chosen owner or clock.

- A session only qualifies after its server `ends_at`, with active/completed/
  claimed status. Cancelled or future-ending sessions do not qualify.
- A one-time quest is missed when its optional deadline has passed and no elapsed
  successful session exists. Archived quests are ignored.
- A Daily created before today's local midnight is missed if neither yesterday
  nor today has an elapsed successful session. A newly created Daily today is
  not marked as missed. Completing it today clears this flag.
- Calendar boundaries use the device's IANA timezone and account for DST.
  Reward-day accounting remains UTC and is unchanged.

The shared PetViewModel queues a new, recent completion (within five minutes of
server time), deduplicates its ID for that ViewModel, and starts **4000 ms** of
celebration when Puppy's illustration is ready and visible. Hidden/background
time does not consume the visible celebration. Old completion history does not
trigger a new reaction on every poll; a process restart can replay a completion
that is still within the five-minute window. This has no economic effect.

State priority: persisted sleep → confirmed care reaction → Daily celebration →
sad need/missed quest → idle. After celebration, Puppy returns to idle if no need
is outstanding, otherwise sad. A sleeping pet celebrates after being woken.
Pet restoration/foreground checks and minute polling refresh facts; a connection
failure does not invent a missed/completed quest.

## Current attachment support

Puppy uses the supplied head/body rig with species-specific attachment profiles.
HEAD, FACE and NECK now support the existing hat, glasses and collar, following
the animated head; existing furniture remains supported. Equipment ownership and
saved slots are shared across appearances. See [COMPANIONS.md](COMPANIONS.md) for
the updated profiles and Capybara/Rabbit support.

See [TEST_REPORT.md](../TEST_REPORT.md) for actual build, SQL, shared and emulator results.
