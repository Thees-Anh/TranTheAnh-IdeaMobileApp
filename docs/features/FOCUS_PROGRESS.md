# Focus companion, habits and celebrations

Apply `supabase/migrations/20261109002000_progression.sql` after migrations
001–019 in Supabase SQL Editor (or the normal migration deployment). No hosted
migration was applied during development. Rebuild/run Android after deployment.

## Reading companion

`tools/assets/build_focus_companion.py` extracts the reading SVG/CSS from design/prototypes/focus/mochi-reading.html
into an offline illustration. The original file is unchanged. No demo scripts,
CDNs or remote fonts are loaded. Mochi is a universal study buddy in Focus;
Puppy selection on Pet Home remains unchanged. The bulb has a separate placement
anchor so its animation cannot override its position.

Android's hardened PrototypePetView blocks network/navigation, sizes the SVG from
native measurements, preserves its 400:330 viewBox, pauses on background and is
destroyed on disposal. Native sizing fixes zero CSS viewport height inside a
scrollable Compose Column. Focus retains safe insets and the existing
server-authoritative countdown with monotonic foreground display.

## Presence reminder

Profile's notification consent explains the new behavior. Both in-app consent
and Android permission are required. Leaving an active visible Focus screen for
five seconds can send “Mochi đang đợi bạn quay lại làm việc đó!” Tapping returns
to Focus. It is silent, once per account/session, between 08:00 and 22:59 local.
Returning cancels it; locked devices, finished sessions, disabled channels or
revoked permission suppress it. Logout invalidates pending work.

Only Activity lifecycle is observed; other applications are not identified and
there is no penalty. Delivery is best effort while the process is alive, so
force-stop/process termination cannot guarantee this reminder. Timer restoration
and reward eligibility remain independent of notification delivery.

## Habit rules

Server history groups elapsed active/completed/claimed sessions by `ends_at`
in **UTC**, matching reward days. Cancelled/future sessions are excluded. Multiple
completions on one date contribute one streak day. Late claims keep the original
completion day. Yesterday's streak remains current until a whole UTC day is
missed; best streak is retained. In Vietnam/Thailand a UTC day begins at 07:00
local. Existing local-time quest reminder deadlines keep their existing policy.

Home shows the flame counter. Profile Overview/Dashboard provides a Monday-first
calendar, month navigation, current/best streaks and permanent 7/30/100-day badges.
Marked days also expose date/count semantics. Week charts and daily Coins use
actual server data. Existing completion history may unlock badges after migration.
Badges grant no Coins/XP and do not bypass the five-session/250-Coins limits.

## Gifts and celebrations

The existing 100-XP level rule is unchanged. Each newly gained level grants one
existing Pet food, limited by inventory capacity. One event records the actual
total for multi-level gains. Full stock or unavailable food grants zero food
without blocking the quest reward. Migration baselines existing levels, avoiding
retroactive food refills. Gifts and progression are shared across pet appearances.

The reward ledger transaction updates wallet, XP, food and the durable event
atomically. Wallet locking serializes retries/concurrency. `progress_snapshot`
reconciles awards under the same lock; `progress_ack` only dismisses presentation.
Later defers a popup for this app session. Pending events return after restart;
acknowledged events stay dismissed. Offline errors retain the last verified data.

Level popups include finite confetti and an Open gift reveal. The locally
generated one-second chime respects Profile → Sound Effects, silent/vibrate,
notification volume and Do Not Disturb. MediaPlayer is released on completion,
background or logout. Compose effects honor animation scaling; SVG has a
reduced-motion style. Sound preferences are stored per account.

The Claim screen flies coins between the measured reward card and wallet badge
after a verified positive-Coins claim. Failed claims, zero Coins and restored
receipts do not replay it. Animation never modifies wallet state.

## Access and verification

RLS permits authenticated reads of the badge catalog and each user's own badges
and events. Clients cannot directly write these tables or the private level
cursor, or call private owner/clock helpers. Public RPCs bind auth.uid() and server
time; the display month cannot change award eligibility.

Run shared Gradle tests, Android assemble/lint, `python tools/testing/test_database.py`
against the isolated local cluster, and the two generators with `--check`.
`supabase/tests/progression.sql` covers UTC boundaries/gaps, badges, duplicates,
real level-crossing claims, stock capacity, acknowledgement and RLS.
`tools/testing/test_progression_android.py` uses the local SQL adapter described in
TEST_REPORT.md; its `--reading-layout` mode checks five viewport/font profiles.
See [actual results and remaining manual checks](../TEST_REPORT.md).
