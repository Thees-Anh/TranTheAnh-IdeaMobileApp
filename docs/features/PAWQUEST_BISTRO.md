# PawQuest Bistro

Food Shop now serves the twelve meals from `assets/food/12food.html`, ordered by backend price (10–500 Coins). Pet Home also opens Bistro. The supplied studio HTML remains unchanged; `tools/assets/build_bistro.py` extracts its SVG artwork, pet templates and animation into an offline runtime without the studio's simulated wallet or external dependencies.

## Backend deployment

Review and apply migrations **029 then 030** before testing against Supabase. They have not been applied to a hosted project by this implementation.

- `bistro_menu()` returns active catalog items with authoritative prices and configured gains.
- `bistro_feed(p_item_id, p_request_id, p_expected_price)` derives the user from authentication. Expected price only detects stale menus; it cannot override the server price.
- One transaction locks the wallet, reuses `shop_purchase` and its ledger, consumes the newly purchased unit, updates the active pet and progression, and saves an immutable receipt. Existing pantry stock is preserved; existing level-up pantry gifts still apply.
- Repeated owner/request keys replay the original receipt. Receipt RLS permits only the owner; ordinary clients cannot mutate recipes or receipts. Backend functions retain existing guarded wallet/inventory permissions.

## Presentation and recovery

Selection opens native purchase confirmation. Only a confirmed receipt starts the original SVG eating/drinking effects: food 3.2s, happy 2s, then idle; drinks 3.2s, then idle. Display callbacks never grant rewards. Android loads only allowlisted local HTML/CSS/font assets and uses origin-aware messages without credentials.

Requests are persisted in an account/project-specific no-backup AtomicFile before submission. An uncertain request blocks new purchases and exposes explicit Retry with the original key. Backgrounding, Skip or navigation cannot reverse a committed purchase or submit another meal.

Fullness and happiness cap at 100; configured XP still applies. Paid drinks provide the requested Fullness/Joy/XP, with **no additional hydration bonus** because none was specified. Existing pantry Feed and free Drink remain available. Meals are bought and served immediately, rather than stocked for later selection.

Android is implemented. iOS currently displays an explicit unsupported renderer message; iOS visual integration and hosted paid-meal testing remain outstanding.
