# Pet dialogue and Android home widget

## Setup

Apply `supabase/migrations/20261109002100_widget_context.sql` after 001–020.
No hosted migration was applied during development. Rebuild/run Android using
the `PawQuest (MainActivity)` configuration.

In Profile → Profile → “Bạn nhỏ ngoài màn hình chính”, tap “Thêm widget ra màn
hình chính” and confirm the launcher dialog. Alternatively, long-press the home
screen → Widgets/Tiện ích → PawQuest. The requested default is 2×2; exact size
depends on the launcher grid. Older launchers may offer only the manual picker.
Adding a widget does not enable notifications or require notification permission.

## Dialogue

PetDialogues contains **44 original short Vietnamese lines**, shared by Mochi
and Puppy. There are six lines for each time period: morning 06–10, noon 11–13,
afternoon 14–17, evening 18–21 and night 22–05, using device local time. Seven
care/need topics have two lines each: hungry, thirsty, eating, drinking, playing,
happy and sleeping. Confirmed care animations take priority, then urgent needs,
then time of day. Selection excludes the immediately previous line.

Pet Home chooses a line on entry/return, animation or need change, and every
30 seconds while resumed. It pauses in background and cancels on disposal.
Speech may wrap to three lines; measured speech insets continue protecting the
pet illustration. Failed care actions retain their existing error feedback and
do not fabricate a successful reaction.

Existing quest/care notifications append an appropriate line while retaining
the factual reminder body and destination. The previous phrase is stored per
account/reminder kind only after successful delivery. Consent, OS permission,
quiet hours and once-per-kind/day deduplication remain unchanged. No extra hourly
or night notifications are scheduled. The Focus presence reminder stays unchanged.

## Widget data and interaction

The native RemoteViews/AppWidgetProvider widget shows the selected pet/name,
a needs-based mood, Hunger/fullness and Water percentages/bars, pending quests
for the device's local day, and the last successful sync time. Puppy has its own
vector illustration; cat artwork reuses existing launcher drawings. These are
static illustrations, not a second running pet animation or local game simulation.
Full-width stat rows keep percentages visible at font scale 1.5.

Tap the pet/card to open Pet Home, the quest count to open Quests, or ↻ to refresh.
The widget never performs care, purchases or reward claims itself.

The new owner-bound `widget_context(timezone)` RPC counts unarchived daily quests
not yet completed that local day, plus unfinished one-time quests with no deadline
or a deadline before the next local midnight, including overdue ones. Future
deadlines are excluded. Elapsed active, completed and claimed sessions count as
completion; cancelled/future sessions do not. One-time completion remains resolved
across days. Local-day bounds use IANA timezone rules, including DST. Reward and
streak days remain UTC and are not changed by this display feature.

## Refresh, privacy and offline behavior

WorkManager reuses the authenticated repositories and runs approximately every
15 minutes while a widget, dynamic icon or notification subscription needs it.
Widget addition, app/quest changes and manual refresh enqueue immediate work.
The provider also requests a 30-minute system update and redraws on resize and
date/timezone changes. Android battery/network policies can delay delivery;
this is not a live timer or a guarantee of an exact midnight refresh.

Only verified display facts are cached privately, with their account binding and
timestamps; no tokens or credentials are copied into widget storage. No local
stat decay or quest/reward mutation is calculated. Failed synchronization retains
verified values with an Offline timestamp. Counts from another local day or
timezone become unknown when the widget redraws until the server refreshes them.
Unknown counts use a dash, not a fabricated zero. The displayed timestamp is the
older of the pet and quest snapshots, so partial updates cannot appear fully fresh.

Logout/account changes clear the cache; coordinator generation checks prevent
old in-flight requests from restoring the previous user's data. The provider is
not exported and its refresh PendingIntent is explicit and immutable. Android
force-stop may disable widget delivery until the app is manually opened again;
ordinary background process eviction retains the last verified launcher view.

## Verification

Shared tests cover catalog size/uniqueness, non-repetition, hour boundaries, care
priority, widget moods and stale-day/timezone handling. The SQL suite covers local
midnight, a 25-hour DST day, completion without claiming, owner isolation and RPC
permissions. `tools/testing/test_dialogue_widget_android.py` runs against the isolated
loopback SQL fixture described in TEST_REPORT.md, including actual launcher
pinning, care refresh, offline recovery, Puppy and logout privacy.

Native implementation references: [Android app widgets](https://developer.android.com/develop/ui/views/appwidgets)
and [widget design overview](https://developer.android.com/develop/ui/views/appwidgets/overview).
See [executed results](../TEST_REPORT.md).

## Widget v2 (2026-11-12)

The 2x2 widget shows the pet portrait with mood badge, name and "Lv. · mood",
Food and Water bars, and three tiles: daily streak (UTC days, same as Profile),
quests done today and quests left today (local day). Streak and Done come from
`widget_context` after migration `20261112003400_widget_streak.sql`; without it
they show "—". Colours follow the launcher's light/dark mode.
