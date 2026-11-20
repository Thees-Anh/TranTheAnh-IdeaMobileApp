-- KILL SWITCH. Run explicitly in the SQL Editor of the affected project during an incident.
-- Stops NEW Lucky Wheel spins immediately (status banner_unavailable). Nothing else changes:
-- balances, tickets, shards, pets, history, prices and odds stay as they are. Committed spins
-- still replay their original receipt (request-ID recovery runs before the banner check).
-- Re-enable with enable_lucky_wheel.sql after the fix is verified.
begin;
update public.lucky_wheel_banners set enabled=false
    where id='e2800000-0000-0000-0000-000000000001' and code='lucky_wheel';
select code,enabled from public.lucky_wheel_banners where id='e2800000-0000-0000-0000-000000000001';
commit;
