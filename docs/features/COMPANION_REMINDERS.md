# Water, companion icons and reminders

Apply migrations **014 then 015 once**, after 001–013, using the entire file in
Supabase SQL Editor. Rebuild Android afterward. No service-role key is required
in the mobile app. These migrations have been tested locally, not applied to the
hosted project by this change.

## Water

Pet Home has a Water stat and Drink action, using the original prototype's
drinking animation. Hydration ranges from 0 to 100, decays by 3 per whole hour
and gains 20 per successful Drink. Drinking costs no food or Coins and grants
no XP. A full or sleeping pet cannot drink. Inactivity never kills the pet.

`config/hydration-balance.json` generates shared rules and migration 014 through
`python tools/balance/generate_hydration.py --check`. Migration 014 introduces an
independent hydration clock so existing pets do not lose water retroactively.
The authenticated server RPC locks and updates care state atomically. Retrying
the same request UUID returns its durable result without applying a second gain.
Only a successful action triggers DRINKING, followed by IDLE.

## Deadlines and timezone

One-time quests accept an optional local date/time in Create/Edit Quest. The
shared parser converts it to a UTC instant; clearing the field writes SQL NULL.
Daily quests use the end of the current device-local calendar day instead of an
absolute deadline. Nonexistent local times during a DST gap are rejected.

The authenticated `reminder_context` RPC supplies server time and pending quests.
Only the timezone is supplied by the client; owner and current time are selected
on the server. Completed sessions suppress the corresponding reminder; cancelled
sessions do not. The private snapshot function cannot be executed by clients.
**Reward days remain UTC** and all existing reward limits remain unchanged.

## Consent and schedules

Open Profile → **Nhắc việc & icon** → **Cho phép thông báo**. Notifications start
disabled. The app explains them before requesting Android permission (Android
13+). Both app consent and OS permission are required. Switching off cancels
pending messages; OS revocation is respected. Consent and daily delivery receipts
are stored per account on this installation. Logout cancels that user's work and
notifications. Reinstalling/clearing app data resets local consent and receipts.

| Reminder | Rule |
|---|---|
| Daily quest idea | No unarchived daily quest; local 09:00–11:59 |
| Quest deadline | Due within 2 hours, including overdue quests; title or pending count |
| Hungry / bored / thirsty | Corresponding stat ≤25, while not sleeping |
| Quiet hours | No delivery from 23:00 through 07:59 |
| Delivery limit | Once per reminder type per local date, recorded after posting |

WorkManager checks approximately every 15 minutes with network available, and
the app reconciles on foreground/quest changes. Delivery is best effort: Doze,
offline periods, force-stop, disabled channels and OEM battery restrictions can
delay or prevent a reminder. This is local scheduled synchronization, not server
push or an exact alarm. Reconnecting does not create a backlog of past-day alerts.
Quest and pet-care notifications have separate channels and routed group summaries.
Tapping them opens the Quest list or Pet Home behind the authentication gate;
an unfinished quest form still prompts before discarding changes.

## Launcher icon

Original vector cat icons have Normal, Hungry and Bored variants. Hunger ≤25
takes priority over Happiness ≤25. Profile → **Icon theo tâm trạng** controls this
independently of notification consent. Icon facts are refreshed from server pet
state; offline icons can remain stale. Android launchers may cache icon changes.
Android 33+ switches aliases in one batch; older versions enable the next alias
before disabling the previous one. Logout restores Normal.

Use the shared Android Studio run configuration **PawQuest (MainActivity)** if a
default-launcher configuration targets an alias that is currently disabled.

Preview: [launcher variants](../media/previews/launcher-icons.html).
Generated vectors can be checked with `python tools/assets/generate_launcher_icons.py --check`.

Platform references: [notification permission](https://developer.android.com/develop/ui/views/notifications/notification-permission),
[notification groups](https://developer.android.com/develop/ui/views/notifications/group),
[periodic WorkManager scheduling](https://developer.android.com/develop/background-work/background-tasks/persistent-work/getting-started/define-work).

## Verification

See [TEST_REPORT.md](../TEST_REPORT.md) for executed local SQL, shared and emulator
checks and remaining device/hosted checks. Native test drivers refuse non-fixture
APKs. Local SQL fixtures use disposable accounts and never contact hosted Supabase.
