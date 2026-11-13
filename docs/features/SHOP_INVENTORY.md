# Shop catalog and inventory

Owned accessory equip/unequip and Android GLB attachments are implemented in
migration 012 and the shared equipment flow; see [EQUIPMENT.md](EQUIPMENT.md).
Furniture placement uses the separate persisted room flow in migration 013;
see [ROOM_DECORATION.md](ROOM_DECORATION.md).

Apply `supabase/migrations/20261105001000_shop_inventory.sql` **once**, after
migrations 001–009. It preserves existing food counts, including zero, and does
not spend Coins, grant XP or implement purchasing.

## Catalog

`item_catalog` stores UUID identity, stable code, name, category, integer Coin
price, logical asset identifier, active flag, maximum quantity and a
`timestamptz` creation instant. Display timestamps using an explicit timezone.

| Code | Category | Coins | Asset ID |
|---|---|---:|---|
| basic_food | FOOD | 10 | item.food.basic |
| hat | ACCESSORY | 60 | item.accessory.hat |
| glasses | ACCESSORY | 80 | item.accessory.glasses |
| collar | ACCESSORY | 40 | item.accessory.collar |
| bed | FURNITURE | 120 | item.furniture.bed |
| lamp | FURNITURE | 70 | item.furniture.lamp |
| carpet | FURNITURE | 90 | item.furniture.carpet |

These are initial MVP prices. Asset IDs reserve names for future integration;
they do not assert that model/image files exist. Deactivate owned items rather
than deleting them. Inactive items are omitted from the shop but remain readable
in inventory.

## Ownership and Pet Care

`user_inventory` has a UUID primary key, profile/catalog foreign keys and a
unique `(user_id, item_id)` key. FOOD stacks from 0 to 1,000,000. ACCESSORY and
FURNITURE have exactly one unit per ownership row. A composite catalog foreign
key prevents bypassing an item's quantity limit by changing inventory metadata.
Zero-food rows remain available for locking/refill but are omitted by the
owned-items RPC.

Migration 010 extends and renames the food table. The `pet_food_inventory`
security-invoker view exposes the same basic-food row for existing `pet_state`
atomic consumption. The updated profile trigger grants starter food once to
new users. Migration and account retries do not refill existing stock.

## Access and operations

| Object | Authenticated access | Anonymous access |
|---|---|---|
| item_catalog | SELECT metadata | none |
| user_inventory | SELECT own rows through RLS | none |
| pet_food_inventory view | SELECT own food through RLS | none |
| shop_catalog() | active catalog | none |
| inventory_items() | own positive stock with nested item metadata | none |
| pawquest_private.adjust_inventory(...) | no execute | none |

Read RPCs use invoker rights and an empty search path. Inventory requests accept
no user ID. Clients cannot modify inventory, prices or ownership. No service-role
secrets are added to the app.

The private `adjust_inventory` primitive is for trusted database code only. It
locks wallet then inventory, matching Pet Care. It rejects underflow, overflow,
inactive grants and invalid unique-item quantities. Repeated unique grants
return the existing row; stack grants are additive, not request-idempotent.
The purchase RPC in migration 011 adds authorization, durable successful request
identities and atomic wallet/ledger posting around this primitive. The primitive
itself does no payment validation and remains unavailable to client/service-role
API callers. Pet Care continues to expose food consumption to the mobile client.

Shared `ItemRepository.getItems()` and `InventoryRepository.getInventory()`
decode the read RPCs into validated domain models. Android registers their
Supabase implementations in Koin. Signed-out requests fail before reading
protected data, cancellation propagates, and returned inventory ownership is
checked in the repository. Shop/Inventory screens are outside this task.

## Secure purchases (migration 011)

Apply `20261105001100_shop_purchases.sql` once after 010. The only purchase
endpoint is `shop_purchase(p_item_id uuid, p_quantity integer, p_request_id uuid)`.
Only authenticated users can execute it. Ownership is derived from `auth.uid()`;
the API accepts no price, balance or target-user argument.

The server validates active catalog membership and quantities (1–100 FOOD units,
exactly 1 for unique items), current stock capacity, unique ownership and balance.
It locks the wallet, catalog row and inventory before posting an append-only
`shop_purchase` debit into `wallet_transactions`. Triggers read the current
server price, calculate cost, grant inventory and update the wallet in the same
transaction. Any error rolls back all changes. No purchase grants XP.

The ledger retains the original reward constraints for `quest_reward` rows.
Purchases have negative or zero Coins, zero XP, and no session/reward day/slot.
Their nullable reward day excludes spending from daily reward sums and counts.
Existing ledger RLS and append-only protection apply to purchase receipts too.

Each successful purchase stores a unique `(user_id, request_id)`. Retrying with
the same item and quantity returns the original receipt, even after a catalog
price/active change. Reusing that ID with a different payload returns
`request_conflict`. A new ID is an intentional new consumable purchase; owning
an accessory/furniture item rejects a new purchase without charging again.

The response contains `status` and a receipt only for `purchased`. Other statuses:
`invalid_quantity`, `account_unavailable`, `item_unavailable`, `already_owned`,
`inventory_full`, `insufficient_balance`, `request_conflict`. Expected business
rejections make no writes and do not reserve a request ID; retrying one after
the relevant state changes can succeed. Transport/server errors are ambiguous:
retry the exact original ID rather than starting a new purchase.

`ItemRepository.purchase`, `PurchaseItem` and the account-scoped `ShopViewModel`
expose typed results through StateFlow. The ViewModel ignores overlapping taps,
retains the pending request on an unconfirmed response, and blocks a new purchase
until `retry()` resolves that request. Invalid local input never reaches the RPC.
The pending ViewModel request lives in memory; a future screen that restores an
unconfirmed purchase across process death must persist its request ID and payload.
The repository already accepts an explicit ID for this purpose. Scope/clear the
ViewModel on account changes, as with the existing account-specific ViewModels.

Receipts are historical: their `balanceAfter` is the balance at that transaction,
not necessarily today's balance on replay. Refresh Pet/wallet and inventory after
success rather than treating a replayed receipt as the latest account snapshot.
This task supplies purchase state/results for UI integration; it does not replace
the existing placeholder Shop screen or add a new design.

## Verification

`python tools/testing/test_database.py` applies all migrations to two separate fresh
local test databases. It checks depleted-food preservation, constraints/RLS,
existing Pet Care and reward regressions, concurrent unique grants and stack
consumption. It never contacts hosted Supabase. SQL fixtures in
`supabase/tests/shop_inventory.sql` run transactionally and roll back; use a
test database with all migrations applied.

Run `./gradlew :shared:testAndroidHostTest :androidApp:assembleDebug :androidApp:lintDebug`
for repository/model tests and Android checks.
