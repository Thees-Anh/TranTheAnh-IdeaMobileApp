-- FORCED UPDATE. Run explicitly in the SQL Editor of the production project.
-- Every app build with a lower versionCode shows "Please update PawQuest" and nothing else.
-- Only do this AFTER the fixed version is live (100% rollout) on Google Play, otherwise
-- users are locked out with no update available. Lower it again to undo.
begin;
update public.app_config
    set min_supported_version_code = 10000,   -- <<< replace with the fixed build's versionCode
        updated_at = clock_timestamp()
    where singleton;
select min_supported_version_code, updated_at from public.app_config;
commit;
