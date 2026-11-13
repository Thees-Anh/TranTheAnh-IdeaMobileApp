-- Disposable local database only; exercise actual grants, RLS and FK behavior.
begin;
create function pg_temp.quest_assert(value boolean, message text) returns void language plpgsql as $$
begin if value is distinct from true then raise exception 'ASSERT: %',message; end if; end $$;
create function pg_temp.quest_rejected(statement text) returns void language plpgsql as $$
begin
    begin execute statement;
    exception when insufficient_privilege or check_violation then return;
    end;
    raise exception 'Expected rejected quest write: %',statement;
end $$;
insert into auth.users(id,email) values
    ('91000000-0000-0000-0000-000000000001','quests-a@example.invalid'),
    ('91000000-0000-0000-0000-000000000002','quests-b@example.invalid');
set local role anon;
select pg_temp.quest_rejected('select * from public.quests');
reset role;
set local role authenticated;
select set_config('request.jwt.claim.sub','91000000-0000-0000-0000-000000000001',true);
insert into public.quests(title,description,category,duration_minutes,repeat_type)
    values('Read','Chapter one','Study',5,'one_time'),('Walk','','Health',90,'daily');
select pg_temp.quest_assert((select count(*)=2 from public.quests),'create and read both repeat types');
select pg_temp.quest_assert((select bool_and(created_at is not null and user_id=auth.uid()) from public.quests),'server identity/time defaults');
select pg_temp.quest_rejected($q$insert into public.quests(title,duration_minutes) values('Invalid',4)$q$);
select pg_temp.quest_rejected($q$insert into public.quests(title,duration_minutes) values('Invalid',91)$q$);
select pg_temp.quest_rejected($q$insert into public.quests(title,duration_minutes,repeat_type) values('Invalid',5,'weekly')$q$);
select pg_temp.quest_rejected($q$insert into public.quests(title,duration_minutes,category) values('Invalid',5,' ')$q$);
select pg_temp.quest_rejected($q$insert into public.quests(user_id,title,duration_minutes) values('91000000-0000-0000-0000-000000000002','Forged',5)$q$);
select pg_temp.quest_rejected($q$update public.quests set created_at=now()$q$);
update public.quests set title='Updated',description='New details',category='Focus',duration_minutes=30,repeat_type='daily' where title='Read';
select pg_temp.quest_assert((select duration_minutes=30 and category='Focus' and repeat_type='daily' from public.quests where title='Updated'),'update definition');
select pg_temp.quest_rejected($q$update public.quests set duration_minutes=91$q$);
-- Another authenticated user cannot see, update or archive A's definitions.
select set_config('request.jwt.claim.sub','91000000-0000-0000-0000-000000000002',true);
select pg_temp.quest_assert((select count(*)=0 from public.quests),'owner read isolation');
update public.quests set archived=true;
update public.quests set title='Hijacked';
select set_config('request.jwt.claim.sub','91000000-0000-0000-0000-000000000001',true);
select pg_temp.quest_assert((select count(*)=2 from public.quests where not archived),'owner write isolation');
-- An existing session survives deletion; its duration is an immutable snapshot.
select public.start_quest_session((select id from public.quests where title='Updated'));
update public.quests set duration_minutes=5 where title='Updated';
select pg_temp.quest_assert((select duration_minutes=30 from public.quest_sessions),'session snapshot unchanged');
update public.quests set archived=true where title='Updated';
select pg_temp.quest_assert((select count(*)=1 from public.quests where not archived),'archive hides definition');
select pg_temp.quest_assert((select count(*)=1 from public.quest_sessions),'archive preserves session');
select pg_temp.quest_rejected('delete from public.quests');
select pg_temp.quest_assert((select coins=0 from public.wallets),'CRUD grants no Coins');
select pg_temp.quest_assert((select xp=0 from public.pets),'CRUD grants no XP');
select pg_temp.quest_assert((select count(*)=0 from public.wallet_transactions),'CRUD creates no reward receipt');
reset role;
rollback;
\echo PASS quest CRUD, repeat metadata, ownership, constraints and no-reward side effects
