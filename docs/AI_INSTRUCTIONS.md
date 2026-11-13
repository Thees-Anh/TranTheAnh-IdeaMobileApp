# PAWQUEST 3D – AI DEVELOPMENT RULES

## Project Overview

PawQuest 3D is an Android-first productivity and 3D virtual pet application.

Users create real-life quests, complete countdown focus sessions, earn Coins and Pet XP, care for a 3D cat, purchase accessories and decorate a virtual room.

## Technology Stack

- Kotlin Multiplatform (KMP/KMM)
- Compose Multiplatform
- Kotlin Coroutines and StateFlow
- MVVM + Repository Pattern
- Koin Dependency Injection
- SceneView + Google Filament for Android 3D rendering
- Blender and GLB/glTF assets
- Supabase Auth, PostgreSQL, Storage and Edge Functions
- Gradle Kotlin DSL
- Android Studio

## Mandatory Portrait-Only Requirement

PawQuest is designed for portrait Android smartphones.

- Lock the Android main Activity to portrait orientation where supported.
- Never implement landscape gameplay or landscape UI screens.
- Use a 1080 × 1920 (9:16) reference design canvas.
- Support taller phone ratios such as 19.5:9 and 20:9.
- Use responsive Compose layouts, not fixed pixel-based production layouts.
- Respect system bars, display cutouts and safe areas.
- Ensure all buttons remain accessible on smaller portrait devices.
- Keep bottom navigation usable and visible.
- Configure SceneView camera framing for a vertical viewport.
- The 3D cat must remain centered and fully visible.
- Avoid stretching 3D models when screen dimensions change.
- Test the UI with different portrait screen sizes.
- Prioritize Android phones; large-screen Android devices may apply platform-specific orientation compatibility behavior.

## Design System

- Primary: #38BDF8
- Background: #FFFFFF
- Secondary: #EFF6FF
- Text: #0F172A
- Accent: #FACC15
- Success: #22C55E

Style: modern, clean, playful, rounded, friendly, cartoon 3D virtual pet game.

## Quest System Rules

- Users create quests.
- Quest duration must be between 5 and 90 minutes.
- Duration determines the reward tier.
- Maximum 5 rewarded quest sessions per user per day.
- Maximum 250 earned Coins per user per day.
- Only one active quest session per user.
- Countdown continues logically when the app is backgrounded, closed or the screen is locked.
- The backend stores authoritative session timestamps.
- Users can cancel a session without receiving rewards.
- Eligible sessions require an explicit Claim Reward action.
- Rewards must be granted exactly once.
- Late claims count toward the reward limits of the session completion day.
- No mandatory evidence or surveillance is required.

## Reward Table

| Duration | Coins | Pet XP |
|---|---:|---:|
| 5–14 minutes | 10 | 5 |
| 15–29 minutes | 20 | 10 |
| 30–44 minutes | 35 | 20 |
| 45–59 minutes | 50 | 30 |
| 60–74 minutes | 65 | 40 |
| 75–90 minutes | 80 | 50 |

## Pet System

- One 3D cat for MVP.
- One 3D room.
- Pet stats: Hunger, Happiness, Energy.
- Animations: Idle, Happy, Eating, Sleeping, Playing.
- Actions: Feed, Play, Sleep, Pet Touch.
- Accessories: Hat, Glasses, Collar.
- Furniture: Bed, Lamp, Carpet.
- Pets never die from inactivity.

## Architecture Rules

1. Shared business logic belongs in commonMain.
2. Android-specific 3D rendering belongs in androidMain.
3. UI must not directly access Supabase tables.
4. Backend controls reward calculation, wallet balance and purchases.
5. Use StateFlow for observable application state.
6. Use repositories and use cases for business logic.
7. Use transactions for critical operations.
8. Never put service-role secrets inside the Android app.
9. Use compatible and maintained dependency versions.
10. Do not introduce unnecessary frameworks.

## Development Workflow

1. Inspect existing files before editing.
2. Implement only the requested feature.
3. Preserve working code.
4. Avoid unrelated refactoring.
5. Run relevant tests and build checks.
6. Do not claim a test passed unless it actually ran.
7. Document new architecture decisions.
8. Update docs/IMPLEMENTATION_LOG.md after each completed task.
9. Report modified files, tests, limitations and manual verification steps.
10. Keep the application runnable after each milestone whenever the environment allows.
11. Follow docs/PROJECT_STRUCTURE.md for file placement. Put original HTML/SVG inputs under design/, tools in their responsibility folders, and feature documentation under docs/features/.
