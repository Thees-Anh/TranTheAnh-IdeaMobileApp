-- Quest definitions remain independent from execution/reward records.
begin;
alter table public.quests
    add column category text not null default 'Focus'
        check (length(btrim(category)) between 1 and 40),
    add column repeat_type text not null default 'one_time'
        check (repeat_type in ('one_time', 'daily'));

grant insert (category, repeat_type), update (category, repeat_type)
    on public.quests to authenticated;
-- Existing owner RLS and column restrictions apply to both new fields.
-- Delete is an archive update: preserve session FKs and reward receipts.
create index quests_visible_owner_created on public.quests(user_id, created_at desc, id desc)
    where archived = false;
commit;
