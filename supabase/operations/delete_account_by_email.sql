-- Operator procedure for a web/email account-deletion request (Google Play requirement).
-- 1. Confirm the request came FROM the account's own email address (reply-to check).
-- 2. Replace the address below, run once in the SQL Editor of the PRODUCTION project.
-- 3. Reply to the user and record the date (no personal data) in the deletion log.
-- It runs the same public.delete_my_account() as the in-app flow, acting as that user
-- for this transaction only, so erasure rules stay identical. Irreversible.
begin;
do $$
declare
    target_email text := 'user@example.com';   -- <<< replace
    target uuid;
begin
    select id into target from auth.users where lower(email)=lower(btrim(target_email));
    if target is null then raise exception 'No account for %', target_email; end if;
    perform set_config('request.jwt.claim.sub', target::text, true);
    perform set_config('request.jwt.claims', jsonb_build_object('sub',target,'role','authenticated')::text, true);
    perform public.delete_my_account();
    if exists(select 1 from auth.users where id=target) then raise exception 'Identity still present'; end if;
    raise notice 'Deleted account % (profile is now an anonymous tombstone)', target;
end $$;
commit;
