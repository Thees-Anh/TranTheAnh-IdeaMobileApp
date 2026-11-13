# Server rewards and portrait claim screen

Apply `20261105000700_reward_claims.sql` once after migrations 001–006, then
rebuild/run Android. Hosted SQL is not deployed by Gradle.

## Reward engine

Migration 002's ledger triggers remain the sole award implementation:

| Locked session minutes | Coins | Pet XP |
| --- | ---: | ---: |
| 5–14 | 10 | 5 |
| 15–29 | 20 | 10 |
| 30–44 | 35 | 20 |
| 45–59 | 50 | 30 |
| 60–74 | 65 | 40 |
| 75–90 | 80 | 50 |

Only eligible, non-cancelled sessions can receive rewards. The wallet row is
locked before checking/posting a claim, serializing all claims for that owner.
An append-only ledger receipt updates wallet Coins, pet XP and session claimed
status in the same transaction. A unique session receipt prevents duplicate
credit. Authenticated clients cannot choose amounts or directly mutate the
wallet/XP/ledger. RLS and authenticated-only RPC grants remain in force.

Five rewarded sessions and 250 earned Coins are allowed per **scheduled session
completion date in UTC**. Claims made later still consume that original day.
Coins clamp to the remaining allowance while XP stays at the duration tier;
an XP-only award still consumes a rewarded-session slot. Five 75-minute claims
therefore award 80/80/80/10/0 Coins and 50 XP each. A sixth is rejected without
partial changes. A session ending exactly at midnight belongs to the new day.

## Quote, claim and duplicate policy

- `reward_reconcile(session?)` wraps Focus restoration and adds a server quote:
  tier Coins, Coins remaining under cap, XP, UTC reward day, used slots,
  limit_reached and can_claim. It grants nothing. This quote shares the wallet
  lock within its transaction, but another device may claim after it returns.
- `reward_claim(session)` locks wallet/session, checks ownership, rejects an
  existing receipt with SQLSTATE `23505` (Reward already claimed), then invokes
  the established authoritative ledger path. Limits and eligibility are checked
  again, so the returned receipt is the final amount, not the earlier quote.
- On a lost response or duplicate error, Sync reads the committed receipt through
  reconciliation. The UI does not blindly retry a credit operation. Six concurrent
  calls for one session produce one success and five duplicate rejections.
- Older `claim_quest_reward`/`focus_claim` RPCs retain their idempotent receipt
  replay contract for compatibility. They cannot pay twice. The current app uses
  the new strict claim endpoint to implement duplicate rejection explicitly.

Only session UUIDs are sent on Claim; device time, Coins, XP and reward dates are
never request parameters. Private quote helpers are not executable by clients.

## Claim UI

Server-eligible/completed Focus sessions open RewardClaimScreen, driven by
FocusViewModel. Claim requires both synchronized timestamp eligibility and the
server's available-slot quote. It displays the server's potentially partial
Coins/XP, an explicit Claim Reward button, quota/error feedback and Return to
Pet Home. A missing/stale quote cannot enable Claim. At five slots the button
is disabled and the completion-day limit is explained.

Success displays actual receipt amounts/balance and removes the Claim button.
A short, one-shot Compose spring scales the existing checkmark icon; there is
no looping effect or 3D dependency. Text/live-region feedback conveys success
independently of motion/color. The white/sky-blue single-column layout scrolls,
respects safe drawing insets and supports compact portrait phones. Return to
Pet Home before Claim leaves the reward unclaimed; it never auto-claims.
Balances in this screen are server receipts; other existing design-showcase
values are outside this screen's scope.

## Reproducible tests

```powershell
.\gradlew.bat :shared:testAndroidHostTest :androidApp:assembleDebug :androidApp:lintDebug
python tools/testing/test_database.py
```

`supabase/tests/reward_engine.sql` exercises both endpoints of all six tiers
through real claims, strict duplicate denial, partial and zero-Coin awards,
five-session/250-Coin limits, late claims on each side of UTC midnight and
multiple connection timezones. An injected pet-update failure after the wallet
update proves wallet, XP, receipt and session status roll back together. The
runner replays all migrations/tests into two fresh PostgreSQL databases and
adds concurrent strict claims alongside the existing cap/session races.

Host tests verify server quote decoding, missing/stale/quota-blocked quote
gating, duplicate UI guards, partial actual receipts versus nominal previews,
and lost-response recovery. `tools/testing/fixtures/reward_http_fixture.py` is a
loopback-only disposable emulator test double, not a hosted reward engine. Its
test-only server clock can be advanced without changing production durations.
No live hosted claim or migration is performed by these local checks.
