# Habit-first navigation redesign

Date: 2026-11-13 · Status: implemented (phases 1-3), verified on emulator

## Intent

Leader feedback: PawQuest's UI/UX leads with the game (pet room as the start tab, 3 of 5 tabs for pet/shop/inventory) while the product goal is building a daily task habit. The redesign keeps every feature and all backend rules but reorders the presentation so the user first sees *what to do today*; the pet becomes a motivating companion, not the main content.

Source: the user's HTML prototype screenshots (Home, Quests, Pet Room, Explore, Settings sheet, Progress sheet).

## Goals

- Four tabs: **Home · Quests · Pet Room · Explore**. Home is the start destination.
- Home: greeting + motivational line, streak + Coins chips, companion card (mood + "Visit room"), today's progress (completed/total quests, Coins earned/250), "What's next" suggested quest with a Start Focus button, weekly streak/habit section at the bottom, "View progress" opens the Progress sheet.
- Quests: progress card (completed/total today), filter chips (All / To do / Done / Daily), compact rows with a play button, `+` FAB opens the create form. Fixed category list: Study, Work, Health, Other.
- Pet Room: the existing room/care screen plus the wardrobe (inventory) in the same tab, pet level shown.
- Explore: wallet card and a grid of Paw Bistro, Cozy Shop, Mystery Box, Lucky Wheel, Exchange, Collection — each opens the existing feature.
- Avatar button opens a **Settings sheet**: account info, companion reminders, language, appearance, sound, sign out, delete account, version.
- **Progress sheet**: streak, rewarded sessions, Coins today, daily-limit note, weekly completion chart.

## Non-goals

- Visual refresh added on request (2026-11-13): use the ui-ux-pro-max skill to redesign the look. Applied at token level (see docs/design/PROTOTYPE_UI.md); the room/pet illustrations are unchanged.
- No new Strict Focus or reduced-motion features (prototype demo toggles).
- No database migration, no change to reward/streak rules.

## Anti-goals

- The game must not regain the first screen; nothing may weaken server-authoritative rewards.

## Approaches

- A. One big rework — fastest, but nothing demoable until all done and one huge review.
- **B. Three phases (chosen)** — 1) navigation skeleton, Pet Room + wardrobe, Explore hub, Settings sheet, Profile removed; 2) new Home + Progress sheet; 3) Quests upgrades. Each phase keeps the app runnable so the leader can review the structure early.

## Decisions (builder's calls, marked)

- *Call:* Home/Quests "today" counts exclude one-time quests finished on an earlier day; those still show as Done in the list.
- *Call:* "Done today" per quest is derived from the user's own `quest_sessions` rows (status completed/claimed): daily quests count when `reward_day` is today (UTC, same day rule as the habit calendar and reward limits); one-time quests count once completed on any day. Read-only select under the existing `sessions_read_own` RLS policy.
- *Call:* Pet Room shows the room first and the wardrobe as a second segment ("Room" / "Wardrobe") in the same tab, because the room renderer fills the viewport and the inventory is a lazy grid; nesting them in one scroll would break both.
- *Call:* Explore cards map to existing features: Bistro → Food shop, Cozy Shop → decor/accessory shard exchange, Mystery Box → item case, Lucky Wheel → wheel, Exchange → pet shard exchange, Collection → pet collection. There is no separate Coins decor shop today.
- *Call:* Old categories (Focus, Personal, custom) display as "Other" in the selector; stored values are untouched.
- *Call:* Home's companion card uses the existing static pet portraits with a mood line from pet stats; the animated WebView stays in Pet Room only.
- Notification routes `pet_home`, `shop`, `inventory`, `profile` keep working by mapping to the new tabs.
