# Quest definitions and CRUD

`Quest` is a shared domain model with `id`, `userId`, `title`, `description`,
`category`, `durationMinutes`, `repeatType` and UTC `createdAt` (`kotlin.time.Instant`).
`QuestDraft` contains only editable fields; the server assigns UUID, owner and
timestamps. It has no Coins/XP fields. Category is a user-defined label rather
than a fixed taxonomy. Existing quests migrate to category `Focus`, repeat
type `ONE_TIME`.

`CreateQuest`, `UpdateQuest`, `DeleteQuest` and `GetQuests` resolve through Koin.
They depend only on `QuestRepository`. Android injects `SupabaseQuestRepository`
using the same authenticated Supabase client as Auth, so refreshed tokens and
logout apply consistently. There is no in-memory persistence fallback. Missing
configuration fails explicitly. Repository requests require a signed-in owner,
apply owner filters, and rely on database RLS for security. Update of a missing,
archived or inaccessible definition returns NOT_FOUND; delete is repeat-safe for
an existing archived definition. Network/service failures have safe messages;
coroutine cancellation propagates. Requests are not automatically replayed on
ambiguous create failures, so callers should reload before retrying a timed-out
create to avoid duplicates.

Validation runs before persistence and is also enforced by PostgreSQL:

- Title: trimmed, 1–120 Unicode code points.
- Description: at most 2000 code points; empty is allowed.
- Category: trimmed, 1–40 code points.
- Duration: integer 5–90 inclusive.
- Repeat type: `ONE_TIME` / `DAILY`, persisted as `one_time` / `daily`.

Daily quests reuse a definition for recurring executions; no daily duplicate
rows, sessions, scheduler or rewards are created by CRUD. This milestone stores
recurrence intent; execution availability and scheduling UI remain separate.
Definition updates never alter the duration snapshot in an existing session.

Delete archives a definition (`archived=true`) instead of destroying the row.
GetQuests excludes archived rows, reads in 200-row pages, and orders by server
creation time and UUID descending. This preserves session foreign keys and
reward audit history. Existing start-session RPCs refuse archived definitions;
already-running sessions are unaffected. Direct physical DELETE remains denied.
Paging is not a transactionally frozen snapshot: reload after concurrent edits.

`questRewardPreview()` is the single shared tier table (10/5, 20/10, 35/20,
50/30, 65/40, 80/50 Coins/XP). Existing design cards delegate to it. It is an
estimate before eligibility and daily caps, not an authoritative grant. Only
server reward claims can change wallets/XP. CRUD touches only `quests`.

## Deployment and use

Apply `supabase/migrations/20261105000400_quest_definitions.sql` once after
migrations 001–003. Existing owner RLS remains enabled; the migration grants
only INSERT/UPDATE on the two new columns and adds a visible-quests index.
The app's existing Supabase public configuration is reused. No hosted migration
is automatically applied by building Android.

```kotlin
val quest = koin.get<CreateQuest>()(
    QuestDraft("Read Kotlin", category = "Study", durationMinutes = 25,
        repeatType = QuestRepeatType.DAILY)
)
val definitions = koin.get<GetQuests>()()
koin.get<UpdateQuest>()(quest.id, QuestDraft("Read a chapter", durationMinutes = 30))
koin.get<DeleteQuest>()(quest.id)
```

## Portrait management UI

The Quests tab now has a vertical list, Create, Details and Edit screens driven
by a shared QuestViewModel. There was no QuestViewModel in the prior repository;
the new one composes the existing CRUD use cases without replacing persistence.
Reward previews are computed in the ViewModel through domain rules and passed
to composables as display values. Forms offer a category menu, 5–90 minute
slider with one-minute buttons, one-time/daily selection and inline validation.
The category menu preserves any existing custom category when editing.

White/sky-blue tokens and existing buttons are retained. List cards are in one
LazyColumn; the Create action stays above the list. Forms and Details have a
single vertical scroll container, IME padding and scaffold-consumed system
insets. Android system Back and visible Back/Cancel actions share navigation;
dirty forms ask before discarding. Delete requires confirmation. Inputs remain
after network/save failures; a busy guard blocks duplicate mutations. Tab
switches retain the ViewModel/form within the authenticated account scope.
Process-death draft persistence is not provided.

Start Quest now opens full-screen [Focus Mode](FOCUS.md), with server-backed
restore, countdown, cancellation and explicit Claim Reward. Migration 006 adds
request-aware Focus RPCs while retaining the existing session APIs. Starting
grants no rewards; only a verified server claim can credit Coins/XP.

Apply `20261105000500_quest_daily_progress.sql` once after migration 004 for
daily progress. This read-only, authenticated SECURITY INVOKER RPC counts the
owner's reward receipts for the server's UTC reward day under existing RLS.
The indicator shows 0/5 through 5/5, not unclaimed/running sessions. On network
failure or missing migration it shows unavailable with Retry, never a fabricated
zero. Refresh quests also refreshes progress. Hosted migrations are not applied
by running Android.

UI/UX Pro Max's Compose guidance supported one scroll container and separating
UI state from business state; its inline-validation guidance informed form
errors. Broad product/design-system searches returned marketing/LMS patterns
that did not match this native task manager; these patterns were discarded in
favor of the established PawQuest palette and native portrait layout.

## Verification

```powershell
.\gradlew.bat :shared:testAndroidHostTest :androidApp:assembleDebug :androidApp:lintDebug
python tools/testing/test_database.py
```

Domain tests exercise all valid reward durations, invalid limits/text/UUIDs,
both repeat types, normalization and use-case DI. Real-SDK/MockEngine tests
exercise CRUD readback across repository instances, exact writable payloads,
owner/archive filters, repeated delete, missing rows, errors and cancellation.
Actual PostgreSQL tests replay every migration into two fresh disposable
databases and exercise CRUD permissions, cross-user isolation, invalid database
writes, session snapshot preservation and zero wallet/XP/receipt side effects.
Additional ViewModel tests cover loading/error/retry, delayed progress updates,
CRUD navigation, computed previews, retained forms, duplicate-save prevention,
discard/delete confirmation and Start without reward side effects. The SDK
session adapter has HTTP contract coverage. PostgreSQL tests verify read-only
daily progress, owner isolation and UTC behavior across connection timezones.
`tools/testing/fixtures/quest_http_fixture.py` extends the local Auth test double
for emulator UI validation, with only disposable `@example.invalid` accounts.
These checks do not claim live hosted Supabase verification.
