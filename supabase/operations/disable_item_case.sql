-- KILL SWITCH. Run explicitly in the SQL Editor of the affected project during an incident.
-- Stops NEW Mystery Box openings immediately. Nothing else changes: balances, inventory,
-- shards, history, prices and odds stay as they are. Already committed openings still
-- replay their original receipt (request-ID recovery runs before the banner check), so no
-- player loses a paid result. The app shows the Mystery Box as unavailable.
-- Re-enable with enable_item_case.sql after the fix is verified.
begin;
update public.item_case_banners set enabled=false
    where id='e2400000-0000-0000-0000-000000000001' and code='item_case';
select code,enabled from public.item_case_banners where id='e2400000-0000-0000-0000-000000000001';
commit;
