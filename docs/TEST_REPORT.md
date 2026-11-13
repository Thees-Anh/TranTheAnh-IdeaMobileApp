# PawQuest MVP integration test report

## 2026-11-12 - Widget redesign and gacha pet-care gate

| Check | Actual result |
|---|---|
| Shared host tests | 225 passed, 0 failures (new PetCareGateTest) |
| Database runner | All migrations replayed twice; new gacha_pet_care_gate.sql (hungry/thirsty/both, no charge, replay unaffected, decay applied) and widget_streak.sql passed with the full suite incl. existing Item Case and Lucky Wheel tests |
| Generator | generate_gacha_care_gate.py --check passes |
| Android build/lint | assembleRelease (R8) and lintDebug: 0 errors |
| Emulator, hosted project (before new migrations) | Redesigned widget renders in light and dark; streak/done show "—" until migration 20261112003400; Mystery Box and Lucky Wheel show "Your pet is thirsty" with Drink and disabled draw buttons |

NOT TESTED on device: widget numbers after applying the new hosted migrations,
server-side pet_needs_care response in the app (UI gate prevented the call).

## 2026-11-12 - Widget v2 and pet-care gate

| Check | Actual result |
|---|---|
| Database runner | All migrations replayed twice; full suite incl. new `gacha_pet_care_gate.sql` and `widget_streak.sql` passed |
| Emulator widget | New layout renders (Puppy, Lv. 5 · Thirsty, Food/Water bars, tiles); Streak/Done show "—" because hosted lacks migration 003400 |
| Emulator Shop | With Water 21%, Mystery Box and Lucky Wheel show "Your pet is thirsty" with Drink and keep Open Case / Spin disabled |
| Shared tests / build / lint | 225 passed, 0 failures; assembleRelease (R8) OK; lintDebug 0 errors, 85 warnings; localization, structure and gacha-gate generator checks passed |

NOT TESTED: hosted migrations 20261112003300/20261112003400 not yet applied;
Feed/Drink from the card not exercised on the real account.

## 2026-11-12 - P1: reminders, Focus notifications, offline, dark mode

| Check | Actual result |
|---|---|
| Shared host tests | 222 passed, 0 failures (new: ReminderScheduleTest 4, OfflineCacheTest 2, Focus notification rules) |
| Database runner | All migrations incl. reminder_schedule replayed twice on local PostgreSQL 18; new reminder_schedule.sql passed with the full suite |
| Android build/lint | assembleDebug, assembleRelease (R8) and lintDebug passed; 0 errors, 66 warnings (new ones are style: apply()/KTX) |
| Emulator API 37, dark mode | Pet Home and Quests render with the night palette and light system-bar icons |
| Emulator, airplane mode + cold start | Entered from the cached account; pet, wallet and quests shown with the Offline banner; Focus did not block the app |
| Reconnect | Disabling airplane mode refreshed automatically (banner removed, room/equipment reloaded) without user action |

| Hosted reminder schedule (after migration 20261112003200) | App armed REMINDER_ALARM for 09:00:30 next day (daily idea); exact window=0 with permission |
| Alarm-fired quest reminder | Test quest due 17:50 → alarm 15:50:30 → "A little more focus" notification posted 15:50:33; next alarm re-armed |
| Focus in background | Ongoing "Puppy is focusing with you" countdown (silent, ongoing); at the end replaced by high-priority "Focus finished!" alert |
| Cleanup | Reward NOT claimed, test quest deleted (session history kept), exact-alarm permission reset to default |

Without exact-alarm access Android showed a 1-hour delivery window for a far alarm; reminders now re-arm in
hops (unit-tested worst case) so they land within ~10 minutes. NOT TESTED on device: reboot restore and the
inexact hop chain end-to-end (needs real elapsed hours).

## 2026-11-12 - Release-readiness (P0) fixes

| Check | Actual result |
|---|---|
| Shared host tests | 216 passed, 0 failures/errors/skips (6 new in AccountLifecycleTest) |
| Database runner | All migrations incl. account deletion replayed twice on local PostgreSQL 18; full SQL/RLS/concurrency suite and new account_deletion.sql passed |
| Android build/lint | assembleDebug, assembleRelease (R8) and lintDebug passed; 0 lint errors, 62 warnings |
| APK size | debug 46.4 MB, release (R8 + shrinkResources) 28.8 MB |
| Release on emulator (API 37) | R8 build signed with the debug key restored the hosted session and loaded pet/wallet data; Profile showed Delete account and its password dialog (cancelled; no account deleted) |
| Large screen | 2560x1600 @320dpi landscape: system ignored the portrait lock and the app rendered as a centered portrait column |
| Localization audit | PASS 890 pairs |

NOT TESTED: a real password-reset email/code round trip and a real account
deletion against the hosted project (migration and email template not applied),
physical tablets/foldables, and TalkBack on the new forms.

## 2026-11-11 - Performance profiling and regressions

PASS: focused Pet/Puppy/Focus/Case/Wheel/Inventory host suites; 11 native animation host cases; Android debug assembly. Baseline plus two post-change 30s idle/30s interaction SVG fixture runs passed. All 17 additional emulator cases passed (display cache/lifecycle, Case/Wheel rendering, Case reveal, Shop and both Exchanges), using synthetic receipts and no real purchases. New coverage checks duplicate JS dispatch, caller-list mutation and pause/resume behavior. Four real packaged SVG assets were smoke-tested.

Measured unchanged-update microbenchmark: 148.86ms before versus 41.38–59.10ms after (20,000 identical calls). No overall FPS gain established: rAF cadence remained approximately 40–47Hz on SwiftShader, and interaction P95 worsened to about 50ms. Presented FPS, Android deadline jank, physical/release-like performance, full authenticated baseline and heap-leak certification are NOT TESTED. Reports and raw samples: docs/performance/. App data preserved; no production database or economy changes.

## 2026-11-09 - Repository organization

| Check | Actual result |
|---|---|
| Shared tests | 137 passed, 0 failures/errors |
| Android build/lint | assembleDebug and lintDebug passed; 0 lint errors, 42 warnings |
| Database runner after relocation | All 23 migrations replayed twice; full SQL integrity, RLS and concurrent-request tests passed |
| Source preservation | 23 migrations and 10 original artwork/metadata inputs verified byte-identical to the pre-move snapshot |
| Generated assets | Mochi, Puppy, Capybara, Rabbit and Focus bundles passed --check without regeneration; catalog, care/hydration/play constants, launcher icons and sound checks passed |
| Legacy models | Accessory rig/framing and room GLB validators passed |
| Tool syntax | 45 Python scripts parsed and 5 Node scripts passed --check |
| Documentation/layout | Project structure, internal document links and concrete tool/design references passed the new audit |
| Localization | 694 paired entries, 152 static UI references and all 20 decoration names/descriptions passed |

Evidence: build/structure-gradle.log, build/structure-database.log, build/database-tests.log and build/reorganization-protected.json. The temporary local PostgreSQL instance started for validation was stopped afterwards. No hosted Supabase project was modified.

No additional emulator UI journey or iOS build was run for this file organization; runtime source/resource paths and module boundaries remain standard, and scene reproduction checks passed. Manual follow-up: review the new Project tree in Android Studio/VS Code and use the updated commands in [the tooling guide](../tools/README.md). See [directory map](PROJECT_STRUCTURE.md).


## 2026-11-09 - Vietnamese / English localization

| Check | Actual result |
|---|---|
| Shared host tests | 137 passed, 0 failures/errors/skips, including five localization tests |
| Android device regression | LanguageDeviceTest: 1 test passed on the existing Medium Phone emulator (Android 17) |
| Android | assembleDebug, assembleAndroidDeviceTest and lintDebug succeeded; 0 lint errors, 43 warnings |
| Copy audit | 694 bilingual pairs, 152 static UI references and all 20 decoration names/descriptions passed |
| Auth UI | Vietnamese/English switching on sign-in; fixture registration succeeded |
| Vietnamese UI | Home stats/actions, five navigation labels, quest form, Shop legacy item labels and pet collection verified through native hierarchy/screenshots |
| Persistence | Vietnamese restored after force-stop/relaunch |
| English UI | Switching back restored English navigation, care labels, catalog labels and pet dialogue |

Native tests used the loopback-only SQL fixture and a disposable account. No real Supabase data, prices or rewards were changed. Initial native launch caught an Android ICU closing-brace regex incompatibility that host tests did not detect; it was fixed and a device regression test added. The UI harness was resumed after replacing scrolled-off-header assertions and adding scrolling for catalog items. Intermittent UiAutomator null-root warnings appeared; the resumed native run completed all assertions recorded in build/language-ui/results.json.

Evidence: build/language-final-checks.log, build/language-device-checks.log, build/language-native.log, build/language-native-resume.log, shared/build/test-results/testAndroidHostTest and docs/media/previews/language-*.png. Shared templates also cover all pet dialogue/care messages and authentication errors. The static audit covers direct literal references, not arbitrary runtime/user data.

Remaining manual checks: every Focus/Reward and error state in both languages; permission-controlled background notification delivery and widget rendering after switching; physical-device and increased-font/compact-screen review, including keyboard-open forms and TalkBack. User-entered quest content and personal names are intentionally preserved. No migration is required; see LOCALIZATION.md.


## 2026-11-09 - Expanded accessories and furniture

| Check | Actual result |
|---|---|
| Shared tests | 132 passed, 0 failures |
| Android build and lint | assembleDebug and lintDebug passed; 0 lint errors, 40 warnings |
| Database | All 23 migrations replayed twice; SQL integrity/RLS/concurrency suite passed |
| Catalog | All 20 new unique items, exact prices and descriptions verified; legacy entries retained |
| Purchases | Repeated requests, duplicate ownership and invalid quantity tested; one debit/grant under concurrent BACK-item purchase |
| Placement | Concurrent BACK equip and WINDOW placement converge to one row; ownership and wrong-slot rejection verified |
| Renderer | 960 sampled scene cases passed: four species x four portrait viewport profiles x six states x ten accessories, with all ten furniture pieces visible |
| Resources | 20 item thumbnails generated from supplied SVG; offline bundle checks passed |
| Native emulator | Crown, wings, window and clock purchased through Compose confirmation; simultaneous HEAD/BACK and wall furniture visibility verified in the actual WebView |
| Native replacement/restart | Wizard hat replaces crown while BACK stays; 9,140 fixture Coins, retained crown ownership, equipment and furniture restored after process restart |

The first native harness attempts required card-matcher corrections and an interrupted accessibility dump retry. The resumed run completed all assertions; Android emitted a transient null-root accessibility warning before the successful restoration check. Authentication used a local fixture; wallet/inventory operations executed against disposable PostgreSQL.

Renderer checks use Chromium and sampled animation phases. They do not establish every intermediate animation pose or physical-device performance. Remaining manual checks: long-running animation and battery/performance on physical phones; TalkBack and larger font sizes; visual review of every outfit combination. No hosted migration was applied. See DECOR_CATALOG.md for migration 023 instructions.

Evidence: build/decor-final-checks.log, build/decor-database.log, build/decor-ui/results.json and docs/media/previews/decor-*.png.


## 2026-11-09 - New companions and equipment

| Check | Actual result |
|---|---|
| Shared tests | 130 passed, 0 failures/errors |
| Android | assembleDebug and lintDebug passed; 0 lint errors/40 warnings |
| Database | All 22 migrations replayed twice with the full SQL integrity/RLS/concurrency suite |
| Unlocks | Capybara rejects Lv.9 and accepts Lv.10; Rabbit rejects Lv.14 and accepts Lv.15; selection preserves XP and grants no Coins |
| Renderer | 432 animation/bounds samples passed across Puppy/Capybara/Rabbit, six states and four portrait viewport profiles with HEAD/FACE/NECK visible and attached |
| Source assets | Offline bundles reproduced with --check; static portraits rendered from actual supplied SVG groups |
| Native locked previews | Lv.1 Inventory displayed supplied Capybara/Rabbit portraits and their level requirements |
| Native switching/equipment | Capybara, Rabbit and Puppy selected through Inventory; all three owned accessories visible and attached to their animated head groups |
| Native restoration | Selected Puppy and all three equipped slots restored after process restart, with the pending level-up popup handled separately |

Evidence: build/companions-final-checks.log, build/companions-database.log,
build/companion-art-tests.log and the equipped renderer screenshots in build.
The renderer checks run in Chromium; completed native selection/restart results
are recorded separately in build/companions-ui/results.json,
build/companions-native-selection.log and build/companions-native-restore.log.
Screenshots are in docs/media/previews/capybara-equipped.png, rabbit-equipped.png,
puppy-equipped.png and pet-collection-preview.png. No hosted migration
or real account XP/inventory was changed. The local SQL auth adapter is a test
double, not evidence of a live Supabase authentication run.

Remaining physical-device checks: OEM WebView rendering, TalkBack image labels,
long-duration animation performance, extreme font sizes and widget portrait
presentation for the two new species. Apply migration 022 before hosted use.

Cleanup completed: normal-config APK reinstalled without clearing app data;
fixture database dropped and test PostgreSQL/adapter/headless browser stopped;
ADB reverse/debug forward removed. Emulator display settings were unchanged and
the emulator remains running. The hosted project still needs migration 022.

## 2026-11-09 - Dialogue and 2×2 widget

| Check | Actual result |
|---|---|
| Shared tests | 129 passed, 0 failures/errors/skips |
| Android build/lint | Normal-config assembleDebug/lintDebug passed; 0 lint errors, 40 warnings |
| Database | All 21 migrations replayed twice with full integrity/RLS/concurrency suite |
| Widget SQL | Pending daily/undated/overdue/current-deadline quests, future/archived exclusion, completion before claim, local midnight, 25-hour DST day, owner isolation, invalid timezone and private/anonymous permissions passed |
| Dialogue rules | 44 unique short lines, all hour boundaries, immediate repetition avoidance and care/need priority passed |
| Native launcher | Profile opened the launcher pin confirmation labeled 2×2; real widget showed Mochi, Hungry, 20% Hunger/Water and four pending quests |
| Native interaction | Quest count opened Quests; successful Feed changed persisted Hunger and the widget to 35% |
| Background/offline | Background process eviction retained display; failed refresh kept verified values with Offline timestamp; refresh recovered while notification permission was denied |
| Pet/account state | Selected Puppy changed name/art; logout replaced identity/stats/count with signed-out/unknown state |
| Large text | Final stacked stat layout visibly preserves Hunger/Water percentages at font scale 1.5 |

Evidence: build/widget-final-checks.log, build/widget-database.log,
build/database-tests.log, build/widget-native-exercise.log and
build/widget-ui/results.json. Screenshots are in build/widget-ui and
docs/media/previews/pet-dialogue.png, pet-widget.png and pet-widget-large-text.png.
Earlier horizontal-row screenshots exposed large-text truncation; final rows
were stacked and retested. An initial force-stop harness check was replaced with
background process eviction: Android force-stop can disable widget delivery
until manual app launch, which is documented rather than treated as app behavior.

All native data came from the loopback auth double + migrated PostgreSQL adapter.
No hosted migration or real account was modified. Remaining manual checks:
OEM/third-party launchers, API 24–30 manual picker, resize extremes/font scale 2,
TalkBack, reboot/Doze scheduling, and observing live notification wording over
multiple reminder days. New notification phrase selection is unit tested; a
multi-day live delivery test was not run. Apply migration 021 before hosted use.

Cleanup completed: temporary widget removed, original font scale/notification
permission restored, normal-config APK installed without clearing data, reverse
port removed, fixture database dropped and local test services stopped. Emulator
remains running. The widget can be added again from Profile after migration 021.

## 2026-11-09 - Focus companion and progression

| Check | Actual result |
|---|---|
| Shared tests | 124 passed, 0 failures/errors |
| Normal Android build/lint | assembleDebug and lintDebug passed; 0 errors/38 warnings |
| Database | All 20 migrations replayed twice, full integrity/RLS/concurrency suite passed |
| New progression SQL | UTC midnight/gaps, same-day duplicates, 7/30/100 badges, original earned date, real XP99→104 claim, one food, duplicate rejection, inventory capacity, invalid month, owner RLS and idempotent ack passed |
| Concurrency | Six simultaneous progress_snapshot calls granted one level event and exactly three food for a three-level increase |
| Native Focus consent | No nudge without in-app consent despite OS grant; consented exit sent one nudge; repeated exit sent none |
| Native gift | Claim crossed Lv.2, popup opened one-food gift; inventory persisted after restart, no duplicate event/popup |
| Native reading layout | Visible cat/head/body/tail/book/bulb geometry checked on 9:16, 19.5:9, 20:9, compact 320×568dp and font scale 1.5; controls reachable by scrolling |
| Native coin flight | Second real SQL-backed claim credited 80 Coins/50 XP; screenshot sequence visibly captured coins arriving at wallet balance 90 |
| Asset reproduction | Reading SVG/CSS and original generated WAV checks passed |

Native tests used emulator-5554 and the loopback auth double + real migrated
PostgreSQL adapter, never a hosted account. Clock acceleration was installed only
in its disposable database. A zero-height WebView SVG discovered by screenshot
review was fixed using native measurements, then all five reading profiles were
retested with positive dimensions and element bounds. Early empty-frame captures
are diagnostic artifacts, not final visual evidence.

Evidence: build/progression-final-checks.log, build/progression-database.log,
build/database-tests.log, build/progression-native.log,
build/progression-native-layout.log and build/progression-ui/results.json.
Screenshots: docs/media/previews/focus-reading-portrait.png, level-up.png,
habit-calendar.png and reward-coin-flight.png. The calendar capture precedes a
minor singular-day label/completion-dot polish; that polish is build checked.

Remaining manual checks: audio audibility/volume/silent/DND on a physical device,
screen-lock and OEM background policies, notification tap navigation under all
process states, TalkBack traversal and system reduced-motion settings. Native
badge popups at 7/30/100 were not exercised; their awarding was SQL tested.
Hosted migration 020 and real-provider end-to-end behavior remain unverified.
See FOCUS_PROGRESS.md for deployment and UTC policy.

Cleanup completed: normal hosted-config APK rebuilt and installed without
clearing app data; fixture process stopped and disposable database dropped;
test PostgreSQL stopped; ADB reverse/debug forward removed; physical emulator
1080×2400/density 420 and font scale 1.0 restored. The emulator remains running.
Android notification permission was granted for the consent tests; in-app
notification consent remains per account and is still required.

## 2026-11-06 - Supplied Puppy SVG companion

| Check | Actual result |
|---|---|
| Shared tests | 118 passed, 0 failures/errors/skips |
| Android build/lint | `:shared:testAndroidHostTest :androidApp:assembleDebug :androidApp:lintDebug` succeeded; 0 lint errors/35 warnings |
| Database | All 19 migrations replayed twice with the complete integrity/RLS/concurrency suite |
| Source reproduction | Original user SVG/CSS retained; Puppy and cat bundle checks passed |
| Native selection | Puppy locked at Lv.1, available at Lv.5; Inventory selection and process-restart restoration passed |
| Native care | Confirmed eating/drinking/laughing, persisted sleeping and actual dog-head touch-to-wake passed |
| Native emotion | Low Hunger and missed Daily selected sad; a verified Daily completion played laughing then returned idle |
| Immediate use after swap | Mochi → Puppy → Play without restart passed; Energy/Hunger/Water costs verified against real SQL state |

Build: `build/puppy-final-checks.log`. SQL: `build/puppy-database.log` and
`supabase/tests/puppy.sql`. Native evidence is staged in `build/puppy-ui/results.json`,
`build/puppy-native-scope.log`, `build/puppy-native-final-layout.log` and
`build/puppy-switch-final.log`. Exact 4000 ms timing, visibility queuing,
same-completion deduplication, care-before-celebration priority and offline safety
are checked with the shared coroutine test clock; native execution confirms the
visible laugh→idle transition rather than measuring wall-clock frame timing.

These were resumable runs, not a single uninterrupted green test. Initial driver
corrections included checking the disabled button's parent (its text node is still
enabled), waiting for care availability, using one materialized fixture timestamp
for session start/end, returning from restored Focus Mode before looking for the
dog, retrying the newly created debug WebView, and accepting its `about:blank`
debug URL while verifying the actual dog SVG ID. Disposable completion fixtures
were removed before isolated layout testing so restored Reward screens could not
replace the pet during a display change.

Testing also exposed stale busy presentation after species changes. Tracked
composition locals, explicit current controller input and accepting the already
reconciled selection snapshot removed the redundant busy restore; the final swap
and immediate-Play rerun passed. Temporary diagnostic output was removed.

The original six-state framing run passed 96 samples per profile at 9:16,
19.5:9, 20:9, compact 320dp and 320dp/1.5× font. Visual review then found head/bubble
clearance was tight at large text. The renderer now receives the **measured**
speech/action panel insets and uniformly fits the supplied character/effects
between them. The final stricter checks are logged in `build/puppy-insets-native.log`.
All five final profiles passed **96 samples each (480 total)**, including visible
props/effects and measured speech/action overlay boundaries. Profiles were
360×640 (9:16), 360×780 (19.5:9), 360×800 (20:9), 320×568 and 320dp/1.5× font.
The final native driver exited 0 and restored the display/font settings.
Previews: [Puppy Home](media/previews/puppy-home.png),
[large text](media/previews/puppy-large-font.png), [sad reaction](media/previews/puppy-sad.png).

Hosted Supabase was not modified. Auth is doubled locally while SQL RPCs/RLS run
against real migrated PostgreSQL in disposable databases. Apply migration **019**
after 001–018 for hosted testing. Remaining manual checks: physical OEM/WebView
versions, TalkBack/reduced-motion settings, hosted unlock/care/quest flows, long-term
performance and dog accessory artwork. Puppy supports owned furniture; accessories
remain saved but are not drawn without dog-specific attachment illustrations.

Setup, priorities and source files: [PUPPY.md](features/PUPPY.md).

After testing, the normal-config APK was reinstalled without clearing user data.
Fixture forwarding/debug connections and the disposable database were removed;
the hidden adapter and local test PostgreSQL server were stopped. Display settings
were verified at 1080×2400, density 420, font scale 1.0. The emulator remains open.
No hosted account XP/balance, service-role secrets or local.properties were changed.

---

## 2026-11-06 - Three-need Play costs and no Joy meter

- `:shared:testAndroidHostTest :androidApp:assembleDebug :androidApp:lintDebug`
  passed: **112 shared tests**, 0 failures/errors/skips; lint 0 errors/35 warnings.
  Log: `build/play-costs-checks.log`.
- All **18 migrations** replayed on two fresh disposable PostgreSQL databases;
  the full SQL/RLS/integrity/concurrency suite passed. New checks cover Play at
  full hidden mood, repeated Play, exact Energy/Hunger/Water costs, reaching zero,
  each insufficient need with no partial deduction, UUID retries and unchanged
  food/Coins/XP. Six concurrent retries deduct once; six distinct requests with
  one Play's resources allow one success with no negative stats.
  Log: `build/play-costs-database.log`; test: `supabase/tests/play_costs.sql`.
- `python tools/balance/generate_play_balance.py --check` passed. Historical migrations
  001–017 were preserved. An initial SQL privilege assertion was run as a role
  without private-schema visibility; moving that assertion to the test owner
  fixed the test setup, and the complete database suite was rerun successfully.
- Active Pet Home now renders Hunger, Water and Energy only. No new emulator
  interaction or portrait-ratio run was performed for this small UI removal;
  prior screenshots/results below are historical. Manual follow-up: apply 018
  to hosted Supabase, verify Play from full needs yields 98 Hunger / 97 Water /
  95 Energy, repeat, and verify each low-need dialog plus PLAYING → IDLE.

Hosted Supabase was not changed. Hidden mood remains solely for the existing
bored icon/reminder behavior and compatibility; it does not limit Play. Sleeping
pets still need touch-to-wake. See [PET_CARE.md](features/PET_CARE.md) for current rules.

---

## 2026-11-06 - Explicit care feedback, waking and pet collection

- Shared tests: **109 passed**, 0 failures/errors. New tests cover all four
  full-stat rejection messages, no-food feedback with empty/nonempty wallet,
  wake → HAPPY → IDLE, level-5 XP boundary, unsupported artwork gating,
  selection restoration and failed-selection feedback.
- Android `:shared:testAndroidHostTest :androidApp:assembleDebug :androidApp:lintDebug`
  passed. Lint: 0 errors, 35 warnings. Final log: `build/pet-feedback-final.log`.
- All **17 migrations** replayed on two fresh disposable PostgreSQL databases;
  the complete SQL/RLS/integrity/concurrency suite passed. New SQL tests verify
  waking saves partial Energy recovery, wake UUID replay does not double-apply
  or wake a later sleep, level 4/5 selection boundary, owner isolation,
  unpublished species filtering, forbidden metadata/direct-selection writes and
  unchanged Coins/XP/stats. Evidence: `build/pet-feedback-database.log` and
  `supabase/tests/pet_collection_wake.sql`.
- Native emulator checks passed: all four full-stat buttons show dialogs; empty
  food/wallet explains earning Coins and opens Quests; unaffordable purchase
  returns a visible server rejection; an actual SVG head tap wakes the sleeping
  cat and plays HAPPY, with the awake state retained after process restart;
  Inventory → Pets shows selected Mochi and the upcoming level-5 milestone.
  Logs: `build/pet-feedback-native.log`, `build/pet-feedback-native-resume.log`.
- These native checks ran in stages. The initial driver could not obtain an
  idle accessibility hierarchy during sleeping SVG motion; the resumed driver
  pauses only test CSS while measuring hit bounds. Its first assertion also
  assumed Energy stayed exactly 20 despite real elapsed recovery. Accepting the
  recovered amount below 100 fixed that test assumption; SQL separately verifies
  exact five-minute recovery (20→30). The resumed native checks passed.
- Compact collection check passed at **320dp portrait, 1.5× font** with scrolling
  to selection and refresh controls. Log: `build/pet-feedback-compact-final.log`.
  Earlier attempts assumed the selection button was already above the fold or
  kept the Pets tab across a configuration change. The final driver opens Pets
  explicitly and scrolls; the app now saves the Items/Pets selection. Visual
  review also exposed a solid shadow behind transparent outlined buttons; the
  shared button now draws that shadow only with a filled background, and the
  final emulator screenshot confirms readable Items/Pets labels.

Previews: [empty-food feedback](media/previews/pet-care-no-food.png),
[compact pet collection](media/previews/pet-collection.png). Setup/extension contract:
[PET_COLLECTION.md](features/PET_COLLECTION.md).

Hosted Supabase was not modified. Tests use fake local authentication with actual
migrated SQL RPCs in disposable databases. Apply migrations **016 then 017** after
001–015 to test the real project. New pet artwork is not included; unknown artwork
remains gated. Physical-device/TalkBack verification, a second real pet's six
animations/equipment/framing, hosted selection/restart and long-term performance
remain manual checks. Earlier full portrait-ratio checks are recorded below;
they are not presented as new execution for this change.

After verification, the normal-config APK was reinstalled without clearing user
data; fixture ADB reverse/debug forwarding and the disposable database were
removed, the hidden SQL adapter and local PostgreSQL server were stopped, and
display size/density/font scale restored to 1080×2400 / 420 / 1.0. The emulator
remains running. No `local.properties` or hosted user balances were changed.

---

## 2026-11-06 - Hydration, launcher moods and opt-in reminders

The new backend changes are migrations 014 and 015. Hosted Supabase was not
modified. Emulator tests use a loopback auth double and the actual SQL RPCs in
a disposable PostgreSQL database, so these results do not prove hosted Auth,
email delivery or hosted migration status.

| Check | Executed result |
|---|---|
| Shared rules / ViewModels | 104 tests, 0 failures/errors/skips |
| Android assemble + lint | BUILD SUCCESSFUL; 0 lint errors, 35 warnings |
| SQL migrations / integrity | All 15 replayed twice; full existing database suite plus hydration/reminder tests passed |
| Concurrent Drink | Six requests with one UUID: one hydration gain, no Coins/XP change |
| Native Drink | Successful animation, 20→40 Water, unchanged food/Coins/XP, request replay and process-restart restoration passed |
| Native deadline form | Local input persisted UTC due_at; authoritative reminder context included the quest |
| Notification consent | In-app explanation and Android deny/allow executed; denial suppressed posts, allowing posted expected kinds |
| Receipt persistence / next day | Repeated same-day refresh retained receipts; next local day posted again |
| Notification navigation | Collapsed Quest group opened the list after leaving details; shared dirty-form discard guard passed |
| Launcher / revocation | Hungry/Bored/Normal aliases, icon toggle, resolved hunger cancellation, OS revocation and independent opt-out passed |
| Logout | Stopped user work, cleared notifications and restored Normal launcher |
| Portrait / animation | 360×640 (9:16), 360×780 (19.5:9), 360×800 (20:9), 320×568 and 320dp with 1.5× font passed; 48 samples/screen across six states |

Build log: `build/companion-final-checks.log`. Lint warnings include upgrade
suggestions, KTX/style suggestions, intentional immediate preference persistence
off the main thread, and existing orientation/resource warnings.
SQL evidence: `build/companion-database-run.log`, `build/database-tests.log`.
Native evidence: `build/companion-drink-native.log`,
`build/companion-deadline-native.log`, `build/companion-consent-native-retry.log`,
`build/companion-navigation-native.log`, `build/companion-mood-native.log`.
Logout evidence: `build/companion-logout-native.log` (Python exit 0).
Portrait evidence: `build/companion-portrait-native.log` and
`build/room-hud-ui/layout-results.json`. All four care actions have at least 48dp
targets inside the room above navigation. Full cat/equipment/furniture containment,
uniform proportions and unknown-state fallback passed. At large text scale, stat
labels shorten with ellipsis and bottom tabs use accessible icon-only labels;
the care buttons remain readable and available. Device display/font settings
were restored after the driver completed.

Native tests were run in resumable stages, not one uninterrupted green run.
The first permission driver assumed a straight apostrophe in Android's “Don't
allow” label; it now accepts the actual curly-apostrophe label. Navigation testing
exposed retained Quest details and Android's automatic notification group launch
behavior. Explicit routed group summaries and the Quest ViewModel entry action
fixed those issues; final navigation rerun passed. Consent checks had already
passed before the old navigation assertion in their log failed.

SQL tests cover bounds, sleeping/full-water rejections, replay, unchanged economic
state, private-function ACLs, owner isolation, finite/deadline constraints, clearing
deadlines, two-hour boundaries, completed versus cancelled quests, next-day daily
quests and DST spring/fall calendar boundaries. Shared rules cover icon priority,
quiet hours, thresholds, local-day deduplication, permission gates and deadline parsing.

Previews: [Drink](media/previews/drinking.png), [launcher variants](media/previews/launcher-icons.png).
Setup and delivery policy: [COMPANION_REMINDERS.md](features/COMPANION_REMINDERS.md).

Remaining manual checks: apply 014→015 to the hosted project; real authenticated
end-to-end care/deadline/reminder requests; Android 24–32 alias behavior; physical
OEM launcher caches, battery/Doze and long-duration background delivery; TalkBack;
notification channel overrides and device-timezone changes. WorkManager delivery
is best effort with network, never an exact 09:00 alarm. No server push is included.

The final normal-config APK was reinstalled without clearing app data. Test-only
ADB reverse/debug forwarding was removed, Android notification permission flags
were returned to the original denied/unset state, the disposable SQL adapter and
database were removed, and the test PostgreSQL server was stopped. The emulator
remains running. `local.properties` was not edited.

---

## 2026-11-06 - Expanded pet room and cartoon icons

- Final `:shared:testAndroidHostTest :androidApp:assembleDebug :androidApp:lintDebug`: BUILD SUCCESSFUL; 92 tests, zero failures/errors; lint zero errors/17 warnings. Log: `build/room-hud-final-checks.log`.
- Native visual checks: 360x640, 360x780, 360x800 and 320x568 logical portrait sizes, plus 320dp with 1.5x font. Room ends close to navigation; all care actions sit inside it, above the tabs, with minimum 48dp touch targets. All five tab accessible names remain available. Each profile passed 40 samples across five animation states, full cat/accessory/furniture containment, uniform proportions and clearance above the action bar. Logs: `build/room-hud-layout-tests.log`, `build/room-hud-compact-tests.log` and `build/room-hud-ui/layout-results.json`.
- Layout verification completed in two runs: the compact size assertion initially used full physical screen height (including system bars), then was corrected to use usable app area. Large-font visual inspection revealed the speech bubble covering the hat; limiting it to one line fixed this. The final bubble/head clearance passed separately in `build/room-hud-large-font-final.log`.
- Native care regression with the in-room controls passed Feed/eating/idle, disabled empty-food repeat, Play, actual SVG-head hit-testing and Sleep restoration after process restart. Log: `build/room-hud-care-tests.log`, Python exit 0. The head hit-test driver now uses actual SVG head bounds rather than a fixed viewport percentage.
- [Expanded Pet Home screenshot](media/previews/pet-home-expanded.png) uses disposable fixture data. Original design/prototypes/pets/mochi.html is unchanged; `tools/assets/build_prototype_pet.py --check` passed. No hosted backend, migrations or gameplay balance changed. The final normal-config APK was reinstalled and temporary fixture/debug connections removed; display and font settings restored.

Remaining manual checks: physical/OEM phones and WebView versions, TalkBack, text sizes beyond 1.5x, sustained performance/battery testing, and hosted end-to-end verification. These local visual checks do not establish hosted database/authentication correctness.

---

## 2026-11-06 - Prototype 2D UI regression

The Android presentation now uses the approved SVG cat and pastel/cocoa Compose theme. These checks concern the visual change; the historical SQL-backed MVP results below were not rerun or relabeled as new results. No hosted Supabase data or production migration was changed.

| Check | Actual result |
|---|---|
| `:shared:testAndroidHostTest :androidApp:assembleDebug :androidApp:lintDebug` | Final normal-configuration build successful; 92 tests, 0 failures/errors; lint 0 errors, 17 warnings. Includes new text-contrast coverage. |
| Offline asset regeneration | `python tools/assets/build_prototype_pet.py --check` passed; original HTML retained unchanged. |
| Native navigation and screenshots | Five tabs reachable at 360x640, 360x780, 360x800 and 320x568 logical sizes (physical 1080x1920/2340/2400 and 960x1704 at 480dpi); bottom navigation bounds stay within each viewport. |
| Compact form / IME / Focus | Created fixture quest, description entered with software keyboard, scrolled to visible Save action while IME open, saved and started Focus; circular timer and Cancel action reachable. |
| SVG animation geometry | Final Android renderer: 40 sampled frames across IDLE/HAPPY/EATING/SLEEPING/PLAYING, head/body/tail and all six customizations inside stage, correct aspect ratio, roots not clipping SVG, attachment parenting correct. Unknown animation falls back to idle; hidden/unowned parts and offline CSP checked. |
| Reward presentation | Fixture session completed using test-server clock advance; ready screen, explicit Claim, success screen and Return to Pet Home passed. This is a visual/SDK check, not a new database-integrity test. |
| Pet Care native integration | Feed -> eating -> idle; second Feed with no food sends no new request; Play -> happy/bounce; physical tap on SVG cat -> happy; Sleep restored after force-stop and restart. Fixture counters confirm actions. |
| Final compact polish / unknown art | 320dp care buttons/caption reachable; unsupported room identifier safely skipped with notice; valid room restored. |

Evidence: `build/prototype-ui/`, `build/prototype-final-checks.log`, `build/prototype-last-runtime.log`, `build/prototype-forms-smoke-retry.log`, `build/prototype-care-smoke.log` and `build/prototype-care-sleep-resume.log` and `build/prototype-reward-ui.log`. A durable Pet Home capture is [pet-home-2d.png](media/previews/pet-home-2d.png); its values and owned items are disposable fixture data, not a live account.

The native checks completed in stages, not a single uninterrupted all-green driver run. An initial form driver used a field index that vanished with the IME; it was changed to target the description label. A competing UI hierarchy request interrupted an attempt. Sleep restoration needed the driver to wait for Pet Home/WebView readiness after process restart. These driver issues were corrected/resumed. During display overrides the emulator launcher also became black; rebooting the emulator recovered it. Separately, a real zero-height HTML/SVG root bug was fixed and regression checks now assert both SVG and root heights.

The final verified APK was reinstalled with the normal project Supabase configuration. The visual fixture process and its adb reverse/debug forwarding were stopped/removed; display overrides were reset. No local.properties changes were needed.

Remaining manual verification: hosted authentication/claim/purchase journey with a real test account, physical/OEM devices and WebView versions, TalkBack and large-font review, sustained animation/battery/memory profiling. The current local visual fixture is not proof of hosted authentication or database reward integrity. See the historical SQL-backed report below for earlier business-rule tests. iOS renderer parity is not implemented by this Android-focused change.

---

Date: 2026-11-05 (Asia/Bangkok).

## Scope and environment

Executed on Windows with PostgreSQL 18, the existing Gradle wrapper/JDK/Android
SDK, and Android emulator `emulator-5554` (API 37, x86_64). Tests use debug builds. No hosted
Supabase account, wallet or inventory was modified.

There are three complementary layers:

1. Shared/Android host tests exercise domain rules, real Supabase SDK contracts,
   ViewModels, cancellation, monotonic countdowns and camera fitting.
2. Two fresh disposable PostgreSQL databases replay all 13 migrations, SQL/RLS
   suites and concurrent requests. These execute the actual database functions.
3. The Android app uses a new **local SQL-backed test adapter** on localhost:55447.
   Its Auth/token provider and HTTP routing are test doubles, but account triggers,
   quests, sessions, claims, purchases, care, equipment and room operations run
   through the migrated PostgreSQL schema and authenticated SQL role/RLS.

The local adapter advances a clock only inside its randomly named disposable
database. Production migrations and application code contain no test clock or
reward shortcut. This is not a live GoTrue/PostgREST end-to-end certification.

## Commands and actual results

| Check | Actual result |
|---|---|
| `gradlew.bat :shared:testAndroidHostTest :androidApp:assembleDebug :androidApp:lintDebug --rerun-tasks --console=plain` | BUILD SUCCESSFUL; 91 tests, zero failures/errors; lint zero errors and 17 warnings |
| `python tools/testing/test_database.py` | PASS: two fresh migration replays, SQL/RLS suites and all concurrent cases, including the new spending-at-cap regression |
| `python tools/testing/test_mvp_api.py` | PASS: SQL-backed journey, insufficient balance, early/duplicate claims, simultaneous-start denial, repeated/lost-response purchase and Auth-double logout/login |
| `python tools/validation/verify_accessory_rig.py` | PASS: actual rigid cat hierarchy, five clips, accessory fingerprints and analytic all-pose envelopes |
| `python tools/validation/verify_room_assets.py` | PASS: three fixed props, 444 triangles / 34,468 bytes, within portrait envelope |
| Local Khronos glTF validator | All six accessory/furniture GLBs: zero errors and warnings |
| ADB native runner + continuation/extra stages | 15 checkpoints PASS; staged execution, not an uninterrupted green invocation of the full driver |

ADB journey execution and screenshot observations are recorded below. Raw local
evidence is in `build/mvp-gradle-checks.log`, `build/mvp-database-results.log`,
`build/database-tests.log`, `build/mvp-api-results.log`, `build/mvp-android-results.log`
and `build/mvp-ui/`. Build artifacts are disposable and may be removed by clean.

## Main journey

The UI journey creates a fresh `@example.invalid` fixture account and a daily
90-minute quest. It repeats Start → Complete → Claim three times to fund the
required items through earnings, without crediting the wallet through test SQL.
Only the isolated server clock is accelerated; the app still needs server
eligibility and explicit Claim Reward.

Executed flow: Register → Create Quest → Start Focus → close app during countdown
→ restore reduced remaining time → Complete → Claim → Buy Food → Feed (restart
after database commit, before response) → Buy Hat → Equip → earn two more rewards
→ Buy Bed → Place → Restart → Restore.

The native journey's database assertions passed:

| State | Expected and observed |
|---|---|
| Reward receipts | 3 × 80 Coins, 3 × 50 XP |
| Purchases | food 10 + hat 60 + bed 120 = 190 Coins |
| Wallet | 50 Coins |
| Pet | 150 XP; care state restored |
| Food | starter 3 + purchase 1 − Feed 1 = 3 |
| Equipment | owned hat in HEAD |
| Room | owned bed in BED |

The independent API journey has the same 50-Coin checkpoint, followed by an
additional lost-response food purchase/retry leaving 40 Coins for that API user.
Its account is separate from the native journey account.

## Required coverage

“PASS” below refers to the stated local test layer, not an unperformed hosted or
physical-device test.

| # | Requirement | Executed evidence / result |
|---:|---|---|
| 1 | Duplicate reward claims | PASS: strict `reward_claim` rejects repeats; six concurrent claims yield one credit and five duplicate rejections. Legacy idempotent RPCs return the existing receipt without a second credit. |
| 2 | Early claims | PASS: Focus SQL suite and SQL-backed API reject claims before ends_at (`23514`); host tests prevent local zero from enabling a reward. |
| 3 | Simultaneous sessions | PASS: concurrent starts leave exactly one active session; repeated same request returns its original session. |
| 4 | More than five rewarded sessions/day | PASS: sixth distinct claim rejected; concurrent six-claim race grants exactly five. |
| 5 | More than 250 earned Coins/day | PASS: partial fourth award, zero-Coin/full-XP fifth award, sixth rejection. New regression spends 100 after earning 250 and verifies allowance does not reopen. |
| 6 | App closed during countdown | PASS: native force-stop, isolated server advance, cold start and reduced remaining minutes; original session/end timestamp retained. Host tests also restore without a background ticker. |
| 7 | Network interruption | PASS: committed purchase response dropped, exact ID retry produces one debit/grant; ambiguous claim/care scenarios covered by host tests. Native ADB reverse-port removal produces a visible inventory error; restoring it and Retry recovers. |
| 8 | Insufficient Coins | PASS: real SQL purchase rejection leaves wallet, stock and ledger unchanged; API also attempts an unaffordable bed. |
| 9 | Duplicate purchases | PASS: same-ID replay, changed-payload conflict, six concurrent duplicate purchases, unique ownership races and overspending races. |
| 10 | Restart during Pet Care | PASS: native Feed SQL commit with response held, force-stop/restart restores food 3 with one consumption. Host tests also retry a lost Feed response safely. |
| 11 | Midnight claims | PASS: seeded completion timestamps at 23:59:59.999 UTC / 00:00:00 UTC; late claims retain completion day, including checks under Honolulu/Bangkok database timezones. Not a real overnight device wait. |
| 12 | Authentication/logout | PASS for SDK/Auth-double, native signup/login and SQL initialization. Native logout followed by process restart remains signed out; login restores the account. Hosted email/confirmation/GoTrue refresh remain manual. |
| 13 | Activity orientation | PASS: emulator landscape user_rotation request leaves the Activity portrait, confirmed by screenshot dimensions. Real sensor/device variants remain manual. |
| 14 | 9:16 | PASS: 1080×1920, density 480, Focus, Pet Home Idle/Sleeping and all five bottom destinations. Compact login also exercised at density 540 (320dp width). |
| 15 | 19.5:9 | PASS: 1080×2340, density 480, Focus, Pet Home Idle/Sleeping and all five bottom destinations. |
| 16 | 20:9 | PASS: 1080×2400, density 480, Focus, Pet Home Idle/Sleeping and all five bottom destinations. |
| 17 | Focus Timer layout | PASS: title, circular MM countdown, preview and Cancel reachable across all three ratios. Short 9:16 content is scrollable; Back to quests can require scrolling. |
| 18 | Pet Home framing | PASS: analytic envelope checks and native Idle/Sleeping screenshots show full cat, hat, ears and feet at all three ratios, with the placed bed. Uses bundled development assets. |
| 19 | Bottom navigation accessibility | PASS: all five labels/enabled targets and destination navigation checked at each ratio. TalkBack/large-font audit remains manual. |
| 20 | Keyboard/forms | PASS: real IME shown on Quest form; Save is reachable by scrolling. Compact login succeeds with keyboard open after scrolling to Password/Sign in. |

## UI execution status

All 15 native checkpoints in `build/mvp-ui/results.json` passed through staged
execution: the main runner, `--resume-countdown`, `--finish-login`, and
`tools/testing/test_mvp_android_extra.py`. Database assertions after the extra cancel
check confirmed three claimed sessions, one cancelled session and the same
50-Coin wallet. The independent API runner was re-executed successfully with an
explicit assertion that its lost-response request actually lost the HTTP response.

The ADB driver needed corrections for the scrollable viewport under the IME,
slider touch-track bounds, an already-restored Focus screen and dynamically
visible login fields. Initial full-driver invocations exited with these harness
failures; the journey continued from saved test state. The corrected full driver
was **not rerun as one uninterrupted invocation**. These driver failures did not
establish an application defect. No application code or production migration was
changed by this testing task.

Inspected evidence includes `quest-keyboard.png`, `compact-login-keyboard.png`,
`focus-1920.png`, `focus-2400.png`, `focus-1920-scrolled.png`, Pet Home short/tall
Idle captures, and all three `pet-sleep-*.png` captures. On 9:16, the Focus Back
action is initially partly outside the viewport but scrolls fully above the
system navigation area; the extra test exercised that scroll and Cancel.
Compact forms require scrolling with the IME, and compact Pet Home intentionally
uses conservative camera fitting that makes the cat smaller. No cropped cat or
blocked main journey control was observed in the inspected captures.

## Remaining manual validation

- Run against a dedicated hosted Supabase test project with migrations 001–013:
  real email/password registration, email confirmation/redirect, login, expiry,
  refresh and logout. Verify real PostgREST schema cache and deployed grants/RLS.
  The local adapter does not test these hosted services or network TLS trust.
- Wait a real minimum-duration session on a physical phone with screen locked,
  background/Doze, process death and reboot; reconnect across real Wi-Fi/mobile
  loss. Accelerated local time does not certify OS power-management behavior.
- Test physical portrait phones, rotation sensors, OEM system bars/cutouts,
  gesture and three-button navigation, different IMEs and 200% font scale.
- Enable TalkBack and verify reading order, announcements, focus and touch
  targets. UIAutomator label/reachability checks are not a full accessibility audit.
- Benchmark release builds on representative GPUs: sustained frame timing,
  thermal behavior and memory after repeated scene/equipment/room changes.
- Verify final production GLBs, sockets, all animations and actual missing-file
  packages. Bundled original development assets do not certify replacement rigs.

## Reproduction and cleanup

Start the existing disposable local PostgreSQL cluster on loopback port 55439.
Run the database test runner, then start `tools/testing/fixtures/mvp_sql_backend.py`.
It creates a `pawquest_e2e_<random>` database and records its name in
`build/mvp-sql-backend.json`. Never use this adapter with hosted/user databases.

In a temporary shell, set `SUPABASE_URL=http://127.0.0.1:55447` and the test-only
key `SUPABASE_PUBLISHABLE_KEY=sb_publishable_pawquest_fixture_only`; build the debug
APK and reverse ADB port 55447. Run `tools/testing/test_mvp_api.py`, then
`tools/testing/test_mvp_android.py` on emulator-5554. The latter installs the fixture APK
without clearing user app data; test sessions use the existing URL-scoped store.
It saves only fixture email/title metadata, not passwords or tokens. Its
`--resume-countdown` mode continues an interrupted harness at that explicit stage;
`--finish-login` continues the final compact-login check. Run
`tools/testing/test_mvp_android_extra.py` after the main journey for Focus scrolling,
cancel and Sleeping camera captures.

Stop the adapter, run `mvp_sql_backend.py --cleanup` to drop only its validated
random test DB, remove the reverse port and stop the disposable cluster. Remove
temporary environment overrides, rebuild/install the normal-config APK and restore
emulator size/density/rotation/IME settings. Do not change local.properties for
these tests, publish the test adapter, or deploy its clock rewrite.

Cleanup for this run: fixture services stopped, disposable databases dropped,
ADB reverse removed, emulator settings restored, and the normal hosted-config
APK rebuilt and installed. local.properties was not changed. Hosted migration
state and live-provider behavior remain unverified; no new migration is required
for the added regression tests or this report.


## 2026-11-10 - Step 02 Item Case and Shard foundation

Implemented migrations 024 (schema, RLS, immutable versioned configuration, exact odds views, shard balance/ledger, histories, private deterministic atomic settlement and case Coins ledger branch) and 025 (20 verified catalog UUIDs, disabled published v1, 100 Coins, 55/30/12/3 tier odds, configured duplicate/redemption prices). Shared domain models and four ItemCaseTest methods added. No public opening/redemption RPC, HTML/UI changes, Pet Lucky Wheel or hosted deployment.

Executed:

- `python tools/testing/test_database.py`: PASS. All 25 migrations replayed into fresh disposable loopback PostgreSQL 18 databases twice. Existing SQL integrity/RLS suites plus item_case_shards.sql passed. Existing concurrency regressions passed; new six-way same-request case settlement and six distinct duplicate settlements produced one first inventory grant and exact Item Shard credits. Concurrent case/shop/reward transactions preserved the expected wallet and shard balances. Generated databases were dropped by the runner.
- `.\gradlew.bat :shared:testAndroidHostTest --console=plain`: BUILD SUCCESSFUL; 141 tests, zero failures/errors/skips, including 4 ItemCaseTest tests. Evidence: shared/build/test-results/testAndroidHostTest/TEST-com.pawquest.ItemCaseTest.xml and other XML reports. Three preexisting unnecessary-safe-call compiler warnings remain. Initial sandbox access to the external Gradle cache failed; the test succeeded with approved cache access.
- Seed-to-source verification: all 20 explicit UUIDs resolve to migration-023 catalog assets and match HTML rarity; no synthesized catalog entries. HTML SHA-256 remains 15b97b714fc8ea0e23d3f838332acb30fe3240514be390291c790f1fed9163df.
- `git diff --check`: passed (Git only reports existing CRLF normalization notices).

New SQL tests cover invalid totals/tiers/weights/references/prices, published immutability, draft hiding, exact and unequal-weight odds, unique ownership and preserved stackable Food, every rarity's first/duplicate award, replay/conflicts, disabled-banner recovery, unavailable pools, negative/duplicate ledger posts, separate currencies, ordinary-client write denial, cross-user/anon isolation, reserved redemption history and atomic rollback on inventory/shard failure. Test-only trusted Pet balance/redemption fixtures are rolled back and expose no application API.

Limitations: no production/deployed schema verification or migration application; no public server RNG/recovery API, redemption execution or Android visual integration yet; banner remains disabled. No full Android assemble/lint or device/iOS UI tests were needed/run for this data/model step. Four HTML-only items remain excluded pending catalog/slot/artwork decisions. Multi-copy furniture is not currently permitted by the real catalog and was not enabled. See docs/gacha/SHARD_SYSTEM_DESIGN.md.


## 2026-11-10 - Step 03 secure Item Case opening API

Implemented new migration 026 only over the existing 024/025 foundation. Authenticated public.item_case_open accepts banner UUID and request UUID, derives auth.uid(), recovers before drawing, uses pgcrypto bytes with unbiased integer rejection sampling and exact tier/item weight mapping, and calls existing private settlement atomically. Added immutable private name/shard-balance snapshots, configurable per-account attempt controls and bounded lock waits. Banner remains disabled; no production migration, HTML, Kotlin/UI, redemption or Pet Lucky Wheel change.

Executed validation:

- `python tools/testing/test_database.py`: PASS, exit 0. All 26 migrations replayed into two fresh randomly named disposable loopback PostgreSQL 18 databases; all existing SQL integrity/RLS/economy suites plus item_case_open.sql passed on both replays. The runner removed its generated databases. Evidence: build/database-tests.log.
- New SQL API suite: actual cryptographic byte source and 1,024 real bounded draws; rejection of the modulo-biased tail; exhaustive mapping of all 10,000 rarity tickets; equal and 3:1 item weights; first acquisition; Common/Rare/Epic/Legendary duplicate conversion; zero Pet Shards; stable full-JSON replay after item name, balance and banner changes; insufficient Coins; rate window/failed-attempt accounting and recovery exemption; forged reward/shard arguments rejected; authenticated/anon/private-helper and cross-user isolation; full rollback on injected inventory and shard failures; exact response fields and two-parameter signature.
- Multi-copy furniture path: PASS in a test-only rolled-back fixture that temporarily relaxes the production catalog's unique-only constraint. Additional copies increment quantity, grant no shards, and reject at capacity without charging. Production still disallows multi-copy furniture.
- Public RPC concurrency with real pgcrypto entropy: PASS. Six identical requests returned one identical original response and one debit; six distinct requests preserved grant/conversion totals; six all-owned results each posted one configured shard credit; six requests against 100 Coins produced one opening and five insufficient_coins results. Concurrent public opening/Shop purchase/quest reward preserved the expected wallet. With quota 2, six new requests produced two openings and four rate_limited results. A wallet held for 10 seconds produced the expected busy result after the five-second lock timeout, with no economic change. Recovery retained its original response after throttle and banner disable.
- Existing private settlement, Shop, reward, stock, equipment, room, care, hydration, progression and related concurrency regressions all passed.
- Gacha documentation links and unchanged HTML SHA-256 verified: 15b97b714fc8ea0e23d3f838332acb30fe3240514be390291c790f1fed9163df. git diff --check passed (only existing line-ending normalization notices).

The runner's deterministic/fault helpers are privileged temporary test replacements restored inside a rolled-back SQL transaction. Production code includes no forced outcome, client entropy or test-mode API. No hosted Supabase/Postgrest/JWT HTTP smoke test was run; local tests use the existing Auth shim. No Gradle/device/iOS tests were run because this step changes SQL and documentation only. Later staging verification must check pgcrypto availability, authenticated HTTP transport/schema cache and approved banner activation. Contract/security details: docs/gacha/ITEM_CASE_API_CONTRACT.md.


## 2026-11-10 - Step 04 Item Case repositories, ViewModel and shard projection

Executed `.\gradlew.bat :shared:testAndroidHostTest :androidApp:assembleDebug :androidApp:lintDebug :shared:connectedAndroidDeviceTest --console=plain` successfully. Host XML reports: 158 tests, zero failures/errors/skips; device XML: 145 tests on Medium_Phone Android 17 emulator, zero failures/errors/skips. New host coverage: 6 ItemCaseRepositoryTest and 11 ItemCaseViewModelTest methods. Device suite also runs these applicable shared tests plus 2 ItemCaseJournalDeviceTest methods. Evidence: shared/build/test-results/testAndroidHostTest and shared/build/outputs/androidTest-results/connected. Android build succeeded; lint XML has 0 fatal/errors and 42 warnings. Existing unnecessary-safe-call warnings remain.

Verified through real Supabase SDK + MockEngine: authenticated two-field requests, exactly one account-bound Authorization header, configuration-view filters, item/duplicate receipt decoding, both currencies, absent-row zero versus malformed/failed read, wrong request/owner rejection, unknown statuses, cancellation and HTTP timeout handling. ViewModel tests cover first acquisition, duplicate conversion, original winning ID, updated Coins/Item Shards, inventory synchronization, simulated timeout after backend commit with same-key recovery and one recorded commit, repeated buttons, failed readback preserving a confirmed result, persistence failure preventing submission, acknowledgment failure blocking another opening, disabled/unavailable-config process restoration and account switching.

Actual Android AtomicFile tests: recreating adapters reads the same request; competing saves preserve one key; compare-clear cannot remove another request; accounts stay separate; corrupt JSON is not treated as empty and cannot be replaced. Tests use unique test project/account UUIDs and remove only their own test journals. Earlier header regression test failed on duplicate Authorization values; corrected to a captured-token SDK authenticated transport and the final suite passed.

`python tools/validation/audit_localization.py`: PASS 697 bilingual pairs, 152 static UI references, all 20 decor names/descriptions. HTML source SHA-256 remains 15b97b714fc8ea0e23d3f838332acb30fe3240514be390291c790f1fed9163df. No SQL migration or production data mutation was performed; no database tests were rerun because schemas/backend code are unchanged. iOS/macOS build and live Supabase wallet/HTTP end-to-end tests were not run. Pending native case UI/HTML bridge and staging recovery smoke are documented in docs/gacha/ITEM_CASE_KOTLIN_INTEGRATION.md. The banner remains disabled.


## 2026-11-10 - Step 05 Mystery Case Shop / Android WebView

Final executed command: `.\gradlew.bat :shared:testAndroidHostTest :androidApp:assembleDebug :androidApp:lintDebug :shared:connectedAndroidDeviceTest --console=plain`.

- PASS: 160 host tests, 0 failures/errors/skips.
- PASS: 150 connected emulator tests on Android 17, 0 failures/errors/skips, at the restored 1080x2400 (20:9) display size.
- PASS: Android debug APK assembly and lint (0 errors, 43 warnings). Existing compiler warnings remain outside this integration.
- PASS: separate actual Shop UI runs at 1080x1920 (9:16) and 1080x2340 (19.5:9), one test per run. Display overrides were reset afterward.
- PASS: real local WebView document, compiled gradient CSS, 65 cards, no horizontal document overflow at 320/360/393dp, no Android/Supabase globals or purchase buttons; blocked remote/private/traversal/query URLs; restrictive settings; pause/resume and disposal.
- PASS: actual native category taps through Food, Accessories, Furniture and Mystery Case with fixture catalog content, authoritative-state shard label and preserved bottom navigation. A screenshot pixel assertion verifies that the purple HTML stage actually paints. The fixture Activity is test-only and uses fake repositories, with opening disabled; it never purchases from a hosted account.
- PASS: new host coverage for category preservation and authenticated owner-filtered opening history reads, together with existing repository/ViewModel financial/recovery coverage.
- PASS: APK ZIP contains HTML, compiled CSS and Quicksand font, each byte-identical to its runtime source. Preserved original artist HTML SHA-256 remains 15b97b714fc8ea0e23d3f838332acb30fe3240514be390291c790f1fed9163df.
- PASS: offline asset generation; localization audit (729 bilingual pairs, 173 static references); project structure/document references; git diff whitespace validation.

During development the native UI harness initially missed Compose accessibility nodes and an edge swipe invoked system Back; traversal and an interior gesture corrected the harness. A later visual check caught a genuinely blank preview despite DOM readiness: viewport CSS heights resolved to zero in the attached lazy layout. The final renderer loads after measurement, fits its HTML height on load/resize and passes the added actual-paint regression assertion. The complete suite above was rerun successfully after that fix.

Limits: no production database changes, banner activation, hosted paid opening, release build, physical-device/TalkBack/large-font acceptance or deterministic winning animation test. Full result-driven playback is Step 06; Shard Exchange remains disabled. Existing purchasing logic remains unchanged and its repository/host regressions pass; real hosted Shop purchases were not exercised.


## 2026-11-10 - Step 06 server-driven HTML animation

Executed one explicit application compile task, `:androidApp:compileDebugKotlin`: PASS. Focused `:shared:testAndroidHostTest --tests '*ItemCase*' --tests '*ShopCatalogTest'`: 27 tests, 0 failures/errors/skips (repository 7, model/configuration 4, ViewModel 12, Shop 4).

Initial focused emulator command selected ItemCaseAnimationDeviceTest, ItemCaseRevealDeviceTest, MysteryCaseWebViewDeviceTest and MysteryShopUiDeviceTest: 7 of 8 passed; the normal/fast easing test failed because phase and computed style were sampled separately around completion. The test now samples both in one JavaScript evaluation; only that failed method was rerun and passed. A subsequently added active pause/resume regression test also passed in isolation. In total all 9 distinct selected/new emulator tests passed after correction; the complete suite was not redundantly rerun. Final WebView source compiled as a dependency of the new device test.

Verified on Android 17 emulator: all 20 actual seeded UUIDs at 320/360/393dp, winning-card center within 1.5 CSS pixels of the indicator, original normal/fast scrolling easing, repeated play/reset, skip, reduced motion, invalid IDs, missing artwork fallback retaining identity, escaped display names, duplicate original-item preservation, forged origin/frame/playback messages, reload, active pause/resume and Compose explicit opening/duplicate reveal without resubmission. Existing offline WebView and four-tab Shop tests passed. Fixture repositories never contact hosted Supabase or spend real Coins. ViewModel tests verify repeated/stale visual callbacks, refresh, interruption recovery, retained durable key and an explicit new opening only after acknowledgment.

Additional checks passed: JS syntax, generated runtime without local economy/outcome selection, preserved original HTML SHA-256, 20 fixture IDs matching migration 025, test APK HTML/CSS/font byte equality, localization (730 bilingual pairs/173 static references), project structure/document links and whitespace checks.

Not tested in this step: hosted Supabase paid opening, production deployment/banner activation, release APK, physical devices or full accessibility acceptance. UI runs used the emulator's 20:9 display; the WebView was additionally measured at three portrait widths. Prior Step 05 ratio checks are historical, not rerun here. No migration was applied and no redemption/Pet Lucky Wheel was implemented.


## 2026-11-10 - Item Case product panels

PASS: one explicit `:androidApp:compileDebugKotlin` invocation. Focused host tests selected ItemCase, ShopCatalog, Inventory, Equipment and Room patterns: 52 tests, 0 failures/errors/skips. Coverage includes new/duplicate presentation, UUID collection/ownership mapping, non-default weighted percentages, Open Again durable retirement/failure/repeated taps, wallet/inventory refresh, history read filtering/snapshot rarity and Item Shard ledger owner/currency/bound checks. Existing Inventory actions and framing regressions pass.

Android 17 emulator: 3 distinct selected UI tests passed after correction. Initial run passed duplicate/Shard Wallet/Open Again and existing Shop navigation; the new-item test timed out reopening history because its coordinate click targeted a recycled/offscreen node. A diagnostic rerun confirmed no history request occurred. The helper now scrolls the node into view and uses its semantic click; its temporary accessibility constant compile error was corrected. Only the failed new-item method was rerun and passed. It verifies collection, configured drop rates, empty/completed history, New Item, existing Inventory/Equip callbacks and Close without resubmission. The complete passing checks were not redundantly rerun.

PASS: localization (755 paired strings, 182 static references), structure/document links, whitespace and unchanged HTML/CSS hashes. No animation assets/controller were changed. UI testing used the emulator's existing 20:9 display; current-step physical device, large-font/accessibility and other display-ratio runs were not performed. Layouts retain portrait scaffold insets and use bounded scrolling panels.

NOT TESTED: real backend RLS/cross-user queries and hosted paid opening. The local PostgreSQL test port 55439 returned no response; repository mocks reject foreign user/currency rows and ViewModel tests reject late account responses, but these do not substitute for deployed RLS verification. History's existing composite FK join and ledger reads require authorized staging validation. No production migration, schema/permission change, redemption or Pet Lucky Wheel.

## 2026-11-11 - Bistro

PASS: :shared:testAndroidHostTest selected Bistro*, SupabasePetRepositoryTest, PetCareTest, ShopPurchaseTest and LocalizationTest (27 tests, zero failures). :androidApp:assembleDebug and :shared:assembleAndroidDeviceTest passed; subsequent lifecycle fix rebuilt both successfully. Only the corrected reload device test required another test-APK build.

PASS: python tools/testing/test_bistro_database.py --port 55441 on a disposable local PostgreSQL cluster: exact twelve recipes and gains, stat caps, unchanged hydration, owner RLS, forbidden client mutations, price guards, sleep/insufficient funds, replay, concurrent same/different keys, full rollback and forged parameters. python tools/testing/test_database.py --port 55441 also passed both fresh migration/SQL replays and existing concurrent economy/care regressions. Its first run exposed an obsolete fixed Shop catalog count; assertions now verify inactive exclusion and the actual active count.

PASS Android 17 emulator, isolated fake repository (no real account/Coins): all twelve SVG IDs and 3200ms eating/drinking timing on cat/dog/rabbit/capybara, food 2000ms happy phase, portrait overflow check; native confirmation, one debit/XP grant, Skip and reload without resubmission. First reload attempts raced destroyed JS evaluation contexts; helper now polls the new initialized document, and only that failed method was rerun and passed. Visual preview inspected at the existing 20:9 ratio. Main debug APK installed preserving data.

NOT TESTED: hosted Supabase Bistro purchase, production migration deployment, physical devices, other ratios, iOS visuals and release APK. Migrations 029 and 030 require user deployment before real backend testing. Existing original studio HTML is preserved.
