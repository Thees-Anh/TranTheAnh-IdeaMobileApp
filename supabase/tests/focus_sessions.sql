begin;
create function pg_temp.focus_assert(value boolean,message text) returns void language plpgsql as $$
begin if value is distinct from true then raise exception 'Focus ASSERT: %',message; end if; end $$;
create function pg_temp.focus_error(statement text,expected text) returns void language plpgsql as $$
declare actual text;
begin
    begin execute statement; exception when others then
        get stacked diagnostics actual=returned_sqlstate;
        if actual=expected then return; end if;
        raise exception 'Expected %, got %',expected,actual;
    end;
    raise exception 'Expected % but succeeded',expected;
end $$;
insert into auth.users(id,email) values
('a1000000-0000-0000-0000-000000000001','focus@example.invalid'),
('a1000000-0000-0000-0000-000000000002','other-focus@example.invalid');
insert into public.quests(id,user_id,title,duration_minutes) values
('a2000000-0000-0000-0000-000000000001','a1000000-0000-0000-0000-000000000001','Original title',5);
create temporary table focus_results(value jsonb);
grant all on focus_results to authenticated;
set local role authenticated;
select set_config('request.jwt.claim.sub','a1000000-0000-0000-0000-000000000001',true);
select pg_temp.focus_assert(public.focus_reconcile()->'session'='null'::jsonb,'empty restore');
insert into focus_results values(public.focus_start('a2000000-0000-0000-0000-000000000001','a3000000-0000-0000-0000-000000000001'));
select pg_temp.focus_assert((value->'session'->>'duration_minutes')::integer=5 and not (value->>'eligible')::boolean,'server start not eligible') from focus_results;
select pg_temp.focus_assert(public.focus_start('a2000000-0000-0000-0000-000000000001','a3000000-0000-0000-0000-000000000001')->'session'->>'id'=value->'session'->>'id','idempotent start') from focus_results;
select pg_temp.focus_error($q$select public.focus_start('a2000000-0000-0000-0000-000000000001','a3000000-0000-0000-0000-000000000002')$q$,'23505');
select pg_temp.focus_error(format('select public.focus_claim(%L)',value->'session'->>'id'),'23514') from focus_results;
update public.quests set duration_minutes=90,title='Edited title';
select pg_temp.focus_assert(public.focus_reconcile()->'session'->>'title_snapshot'='Original title','immutable title');
select pg_temp.focus_assert((public.focus_reconcile()->'session'->>'duration_minutes')::integer=5,'immutable duration');
select pg_temp.focus_assert(public.focus_reconcile()->'session'->>'id'=value->'session'->>'id','restart restore') from focus_results;
select set_config('request.jwt.claim.sub','a1000000-0000-0000-0000-000000000002',true);
select pg_temp.focus_assert(public.focus_reconcile()->'session'='null'::jsonb,'owner restore isolation');
select pg_temp.focus_error(format('select public.focus_claim(%L)',value->'session'->>'id'),'42501') from focus_results;
select pg_temp.focus_error(format('select public.focus_cancel(%L)',value->'session'->>'id'),'42501') from focus_results;
select set_config('request.jwt.claim.sub','a1000000-0000-0000-0000-000000000001',true);
select public.focus_cancel((value->'session'->>'id')::uuid) from focus_results;
select public.focus_cancel((value->'session'->>'id')::uuid) from focus_results;
select pg_temp.focus_assert(public.focus_start('a2000000-0000-0000-0000-000000000001','a3000000-0000-0000-0000-000000000001')->'session'->>'status'='cancelled','replay never restarts cancelled session');
select pg_temp.focus_assert((select coins=0 from public.wallets),'cancel no reward');
reset role;
select pg_temp.focus_error('update public.quest_sessions set duration_minutes=6,ends_at=started_at+interval ''6 minutes''','23514');
-- Historical fixtures simulate elapsed time without modifying a running session or sleeping.
insert into public.quest_sessions(id,user_id,quest_id,duration_minutes,started_at,ends_at,status) values
('a4000000-0000-0000-0000-000000000001','a1000000-0000-0000-0000-000000000001',
'a2000000-0000-0000-0000-000000000001',5,now()-interval '6 minutes',now()-interval '1 minute','completed');
set local role authenticated;
select pg_temp.focus_assert((public.focus_reconcile()->>'eligible')::boolean,'elapsed eligibility from server');
select pg_temp.focus_assert((public.focus_claim('a4000000-0000-0000-0000-000000000001')->'reward'->>'coins')::integer=10,'server claim receipt');
select pg_temp.focus_assert(public.focus_claim('a4000000-0000-0000-0000-000000000001')->'session'->>'status'='claimed','idempotent claim');
select pg_temp.focus_assert((select count(*)=1 from public.wallet_transactions),'one receipt');
select pg_temp.focus_assert((select coins=10 from public.wallets),'one credit');
select pg_temp.focus_assert((select xp=5 from public.pets),'one XP credit');
reset role;
select pg_temp.focus_assert(not has_function_privilege('anon','public.focus_reconcile(uuid)','execute'),'anonymous denial');
rollback;
\echo PASS Focus start/replay, immutable snapshot, restore, early claim denial, owner isolation, cancel and exactly-once claim
