begin;
create function pg_temp.config_assert(value boolean,message text) returns void language plpgsql as $$
begin if value is distinct from true then raise exception 'App config ASSERT: %',message; end if; end $$;

select pg_temp.config_assert(has_function_privilege('anon','public.app_status(integer)','EXECUTE'),'callable before sign-in');
select pg_temp.config_assert(has_function_privilege('authenticated','public.app_status(integer)','EXECUTE'),'callable when signed in');
select pg_temp.config_assert(not has_table_privilege('anon','public.app_config','SELECT')
    and not has_table_privilege('authenticated','public.app_config','UPDATE'),'clients cannot read or change the table directly');

-- Default minimum 1: every real build is supported.
set local role anon;
select pg_temp.config_assert((public.app_status(10000)->>'update_required')::boolean=false,'1.0.0 supported by default');
select pg_temp.config_assert((public.app_status(null)->>'update_required')::boolean=true,'unknown version needs update');
reset role;

update public.app_config set min_supported_version_code=10001 where singleton;
set local role authenticated;
select pg_temp.config_assert((public.app_status(10000)->>'update_required')::boolean,'older build must update');
select pg_temp.config_assert((public.app_status(10001)->>'update_required')::boolean=false,'minimum itself is supported');
select pg_temp.config_assert((public.app_status(10001)->>'min_supported_version_code')::int=10001,'reports the minimum');
reset role;

do $$begin
    begin insert into public.app_config(singleton,min_supported_version_code) values(false,5); raise exception 'second config row accepted';
    exception when check_violation then null; end;
end $$;
rollback;
