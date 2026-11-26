-- Server-controlled minimum app version (forced update after a critical bug).
-- public.app_status() is callable before sign-in (anon) and reveals only whether this
-- version code is still supported. Changing the minimum is an operator action:
-- supabase/operations/set_min_supported_version.sql.
begin;

create table public.app_config (
    singleton boolean primary key default true check(singleton),
    min_supported_version_code integer not null default 1 check(min_supported_version_code>=1),
    updated_at timestamptz not null default clock_timestamp() check(isfinite(updated_at))
);
insert into public.app_config(singleton,min_supported_version_code) values(true,1);
alter table public.app_config enable row level security;
revoke all on public.app_config from public,anon,authenticated,service_role;

create function public.app_status(p_version_code integer) returns jsonb
language sql stable security definer set search_path='' as $$
    select jsonb_build_object(
        'update_required', coalesce(p_version_code,0) < c.min_supported_version_code,
        'min_supported_version_code', c.min_supported_version_code)
    from public.app_config c where c.singleton
$$;
revoke all on function public.app_status(integer) from public,anon,authenticated,service_role;
grant execute on function public.app_status(integer) to anon,authenticated;
comment on function public.app_status(integer) is 'Whether the given Android versionCode is below the supported minimum. Public by design.';

commit;
