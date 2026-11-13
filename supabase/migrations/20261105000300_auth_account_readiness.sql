begin;

-- Existing rows, names, balances and XP must survive retries and session restoration.
create or replace function pawquest_private.bootstrap_user() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
    insert into public.profiles(id) values(new.id) on conflict (id) do nothing;
    insert into public.wallets(user_id) values(new.id) on conflict (user_id) do nothing;
    insert into public.pets(user_id) values(new.id) on conflict (user_id) do nothing;
    return new;
end;
$$;

create function public.ensure_account() returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
    actor uuid := auth.uid();
    result jsonb;
begin
    if actor is null then raise exception 'Authentication required' using errcode='42501'; end if;
    perform 1 from auth.users where id=actor for key share;
    if not found then raise exception 'Authenticated user not found' using errcode='42501'; end if;
    insert into public.profiles(id) values(actor) on conflict (id) do nothing;
    insert into public.wallets(user_id) values(actor) on conflict (user_id) do nothing;
    insert into public.pets(user_id) values(actor) on conflict (user_id) do nothing;
    select jsonb_build_object('profile',to_jsonb(p),'wallet',to_jsonb(w),'pet',to_jsonb(c))
        into result from public.profiles p
        join public.wallets w on w.user_id=p.id join public.pets c on c.user_id=p.id
        where p.id=actor;
    return result;
end;
$$;

revoke all on function pawquest_private.bootstrap_user(), public.ensure_account()
    from public, anon, authenticated, service_role;
grant execute on function public.ensure_account() to authenticated;
comment on function public.ensure_account() is
    'Idempotent authenticated account readiness. Creates only missing rows; never resets Coins, XP or user edits.';
commit;
