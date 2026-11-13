# PawQuest

Android-first habit-building app with focus quests, rewards and virtual companions. Built with Kotlin Multiplatform, Compose Multiplatform, Coroutines/StateFlow, Koin and Supabase.

## Features

- Email/password authentication, quest CRUD, recurring quests and server-timed focus sessions.
- Server-authoritative Coins/XP, daily limits, streaks, habit calendar and celebrations.
- Mochi, Puppy, Capybara and Rabbit; care actions, shop, inventory, equipment and room furniture.
- Opt-in reminders, Android widgets and Vietnamese/English language selection.

The active pet presentation uses offline animated 2D SVG/CSS with native Compose controls. The earlier SceneView/Filament implementation remains an inactive Android reference. See [architecture](docs/ARCHITECTURE.md).

## Repository layout

```text
androidApp/   Android host, Activity, notifications, widgets and resources
shared/       KMP domain/data/presentation, platform adapters, resources and tests
iosApp/       iOS host/scaffold; builds require macOS
supabase/     Ordered migrations, SQL tests and generator templates
config/       Canonical gameplay balance and safe configuration examples
design/       Original HTML/SVG/CSS inputs for prototypes and asset generators
tools/        Asset/balance generators, validators and integration test harnesses
docs/         Setup, architecture, feature contracts, evidence and archived walkthroughs
gradle/       Gradle wrapper, JVM configuration and dependency version catalog
```

[Detailed structure and file placement](docs/PROJECT_STRUCTURE.md) | [Documentation index](docs/README.md) | [Tool guide](tools/README.md)

## Setup and run

1. Open the repository root in Android Studio. Use JDK 21 and Android SDK 37; install the build tools Gradle requests. Versions are declared in [the version catalog](gradle/libs.versions.toml).
2. Set `sdk.dir` in untracked `local.properties`. Follow [the Supabase configuration example](config/examples/supabase.properties.example) to add the URL and publishable/anon key to `local.properties` or environment variables. Never use a secret/service-role key in the mobile app.
3. Apply every file in `supabase/migrations/` in filename order (including `20261112003100_account_deletion.sql`) and add the recovery code to the password-reset email template. See [database setup](docs/backend/DATABASE.md) and [authentication](docs/features/AUTHENTICATION.md).
4. Select **PawQuest (MainActivity)**, choose a portrait phone/emulator and Run. The explicit Activity run configuration supports mood-dependent launcher aliases.

```powershell
.\gradlew.bat :shared:testAndroidHostTest :androidApp:assembleDebug :androidApp:lintDebug
.\gradlew.bat :androidApp:installDebug
```

Use `./gradlew` on macOS/Linux. iOS remains a scaffold; no iOS build result is claimed for this Windows environment. Local configuration, signing keys, build outputs and caches are excluded from Git.

## Validation

```powershell
python tools/validation/check_project_structure.py
python tools/validation/audit_localization.py
python tools/assets/build_prototype_pet.py --check
python tools/assets/build_companions.py --check
python tools/assets/build_focus_companion.py --check
python tools/assets/import_decor_catalog.py --check
python tools/balance/generate_pet_care_balance.py --check
python tools/balance/generate_hydration.py --check
python tools/balance/generate_play_balance.py --check
```

See [tools](tools/README.md) for PostgreSQL, browser and Android fixture requirements. Integration fixtures use disposable local data. Actual executed results and remaining manual checks are recorded in [the test report](docs/TEST_REPORT.md).

Read [development rules](docs/AI_INSTRUCTIONS.md) before changes. Record completed work in [the implementation log](docs/IMPLEMENTATION_LOG.md).
