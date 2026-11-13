begin;
create function pg_temp.deletion_assert(value boolean,message text) returns void language plpgsql as $$
begin if value is distinct from true then raise exception 'Deletion ASSERT: %',message; end if; end $$;
insert into auth.users(id,email) values
 ('f6100000-0000-0000-0000-000000000001','delete-me@example.invalid'),
 ('f6100000-0000-0000-0000-000000000002','keep-me@example.invalid');
insert into public.quests(id,user_id,title,description,duration_minutes,repeat_type,due_at) values
 ('f6200000-0000-0000-0000-000000000001','f6100000-0000-0000-0000-000000000001','Call Dr. Private','Secret notes',5,'one_time','2026-03-09T03:00:00Z'),
 ('f6200000-0000-0000-0000-000000000002','f6100000-0000-0000-0000-000000000002','Other owner','',5,'daily',null);
insert into public.quest_sessions(user_id,quest_id,duration_minutes,started_at,ends_at,status) values
 ('f6100000-0000-0000-0000-000000000001','f6200000-0000-0000-0000-000000000001',5,'2026-03-08T05:25:00Z','2026-03-08T05:30:00Z','completed'),
 ('f6100000-0000-0000-0000-000000000002','f6200000-0000-0000-0000-000000000002',5,'2026-03-08T05:25:00Z','2026-03-08T05:30:00Z','completed');

select pg_temp.deletion_assert(not has_function_privilege('anon','public.delete_my_account()','EXECUTE'),'anonymous deletion prohibited');
select pg_temp.deletion_assert(has_function_privilege('authenticated','public.delete_my_account()','EXECUTE'),'signed-in users can delete themselves');

-- Session snapshots stay frozen outside account erasure.
do $$begin
    begin update public.quest_sessions set title_snapshot='Deleted quest' where user_id='f6100000-0000-0000-0000-000000000002';
        raise exception 'Snapshot changed without erasure';
    exception when check_violation then null; end;
end $$;

select set_config('request.jwt.claim.sub','f6100000-0000-0000-0000-000000000001',true);
set local role authenticated;
select public.delete_my_account();
reset role;

select pg_temp.deletion_assert(not exists(select 1 from auth.users where id='f6100000-0000-0000-0000-000000000001'),'identity deleted');
select pg_temp.deletion_assert((select display_name='Deleted user' and deleted_at is not null from public.profiles where id='f6100000-0000-0000-0000-000000000001'),'profile is an anonymous tombstone');
select pg_temp.deletion_assert((select bool_and(title='Deleted quest' and description='' and due_at is null and archived) from public.quests where user_id='f6100000-0000-0000-0000-000000000001'),'quest text erased');
select pg_temp.deletion_assert((select bool_and(title_snapshot='Deleted quest') from public.quest_sessions where user_id='f6100000-0000-0000-0000-000000000001'),'session titles erased');
select pg_temp.deletion_assert(current_setting('pawquest.erasing_user',true)='','erasure flag cleared');

-- Other accounts are untouched.
select pg_temp.deletion_assert(exists(select 1 from auth.users where id='f6100000-0000-0000-0000-000000000002'),'other identity kept');
select pg_temp.deletion_assert((select title='Other owner' from public.quests where id='f6200000-0000-0000-0000-000000000002'),'other quests kept');
select pg_temp.deletion_assert((select bool_and(title_snapshot='Other owner') from public.quest_sessions where user_id='f6100000-0000-0000-0000-000000000002'),'other sessions kept');
select pg_temp.deletion_assert((select deleted_at is null from public.profiles where id='f6100000-0000-0000-0000-000000000002'),'other profile active');

-- A deleted identity cannot call it again; an anonymous caller is rejected.
do $$begin
    begin perform public.delete_my_account(); raise exception 'Repeated deletion accepted';
    exception when insufficient_privilege then null; end;
end $$;
select set_config('request.jwt.claim.sub','',true);
do $$begin
    begin perform public.delete_my_account(); raise exception 'Anonymous deletion accepted';
    exception when insufficient_privilege then null; end;
end $$;
rollback;
