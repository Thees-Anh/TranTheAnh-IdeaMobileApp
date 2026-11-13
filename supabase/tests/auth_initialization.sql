-- Run against a disposable migrated database as postgres. All fixtures roll back.
begin;
create function pg_temp.auth_assert(value boolean, message text) returns void language plpgsql as $$
begin if value is distinct from true then raise exception 'ASSERT: %',message; end if; end $$;
create function pg_temp.auth_denied(statement text) returns void language plpgsql as $$
begin
    begin execute statement; exception when insufficient_privilege then return; end;
    raise exception 'Expected access denial';
end $$;
insert into auth.users(id,email) values
    ('81000000-0000-0000-0000-000000000001','auth-test@example.invalid'),
    ('81000000-0000-0000-0000-000000000002','auth-other@example.invalid');
insert into public.quests(id,user_id,title,duration_minutes) values
    ('82000000-0000-0000-0000-000000000001','81000000-0000-0000-0000-000000000001','Auth reward',5);
insert into public.quest_sessions(id,user_id,quest_id,duration_minutes,started_at,ends_at,status) values
    ('83000000-0000-0000-0000-000000000001','81000000-0000-0000-0000-000000000001',
    '82000000-0000-0000-0000-000000000001',5,'2026-01-01 10:00+00','2026-01-01 10:05+00','completed');
create temporary table account_ids as select p.id as pet_id,w.id as wallet_id
    from public.pets p join public.wallets w using(user_id)
    where p.user_id='81000000-0000-0000-0000-000000000001';
grant select on account_ids to authenticated;
set local role anon;
select pg_temp.auth_denied('select public.ensure_account()');
reset role;
set local role authenticated;
select set_config('request.jwt.claim.sub','',true);
select pg_temp.auth_denied('select public.ensure_account()');
select set_config('request.jwt.claim.sub','81000000-0000-0000-0000-000000000001',true);
select public.claim_quest_reward('83000000-0000-0000-0000-000000000001');
update public.profiles set display_name='Preserved name';
update public.pets set name='Preserved cat';
select public.ensure_account() from generate_series(1,10);
select pg_temp.auth_assert((select count(*)=1 from public.profiles),'one own profile');
select pg_temp.auth_assert((select count(*)=1 from public.wallets),'one own wallet');
select pg_temp.auth_assert((select count(*)=1 from public.pets),'one own cat');
select pg_temp.auth_assert((select id=(select wallet_id from account_ids) and coins=10 from public.wallets),'wallet ID and earned Coins preserved');
select pg_temp.auth_assert((select id=(select pet_id from account_ids) and xp=5 and name='Preserved cat' from public.pets),'pet ID, XP and name preserved');
select pg_temp.auth_assert(public.ensure_account()->'profile'->>'display_name'='Preserved name','profile edits preserved');
select pg_temp.auth_assert(public.ensure_account()->'profile'->>'id'='81000000-0000-0000-0000-000000000001','return own account only');
select set_config('request.jwt.claim.sub','81000000-0000-0000-0000-000000000002',true);
select pg_temp.auth_assert(public.ensure_account()->'profile'->>'id'='81000000-0000-0000-0000-000000000002','second user isolated');
select set_config('request.jwt.claim.sub','81000000-0000-0000-0000-000000000099',true);
select pg_temp.auth_denied('select public.ensure_account()');
reset role;
rollback;
\echo 'PASS auth bootstrap/idempotent restoration and ownership tests'
