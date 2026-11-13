-- Read-only UI progress, using the server's UTC reward day and existing owner RLS.
begin;
create function public.quest_daily_reward_count() returns integer
language plpgsql stable security invoker set search_path = '' as $$
begin
    if auth.uid() is null then raise exception 'Authentication required' using errcode = '42501'; end if;
    return (select count(*)::integer from public.wallet_transactions
        where user_id = auth.uid() and reward_day = (now() at time zone 'UTC')::date);
end;
$$;
revoke all on function public.quest_daily_reward_count() from public, anon, authenticated, service_role;
grant execute on function public.quest_daily_reward_count() to authenticated;
commit;
