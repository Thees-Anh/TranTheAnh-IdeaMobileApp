-- In-app account deletion (Google Play account-deletion requirement).
-- The sign-in identity (auth.users row: email, password hash, sessions, refresh tokens)
-- is deleted permanently. User-entered text is erased. Append-only financial/reward
-- ledgers stay intact for integrity, keyed only by a random UUID that no longer maps
-- to any identity; the profile becomes an anonymous tombstone.
begin;

alter table public.profiles add column deleted_at timestamptz check(deleted_at is null or isfinite(deleted_at));

-- Profiles and ledgers must outlive the deleted identity, so identity references move
-- from auth.users to the tombstone profile.
alter table public.profiles drop constraint profiles_id_fkey;
alter table public.bistro_meals drop constraint bistro_meals_user_id_fkey;
alter table public.bistro_meals add constraint bistro_meals_user_id_fkey
    foreign key(user_id) references public.profiles(id) on delete restrict;

-- Session definitions stay frozen, except that account erasure may replace the copied
-- quest title with a fixed placeholder for the erasing owner's own rows.
create or replace function pawquest_private.freeze_session_definition() returns trigger
language plpgsql security definer set search_path='' as $$
begin
    if tg_op='INSERT' then
        new.title_snapshot:=coalesce((select title from public.quests where id=new.quest_id and user_id=new.user_id),new.title_snapshot);
    elsif row(new.id,new.user_id,new.quest_id,new.duration_minutes,new.started_at,new.ends_at,new.request_id)
        is distinct from row(old.id,old.user_id,old.quest_id,old.duration_minutes,old.started_at,old.ends_at,old.request_id)
        or (new.title_snapshot is distinct from old.title_snapshot and not coalesce(
            new.title_snapshot='Deleted quest'
            and current_setting('pawquest.erasing_user',true)=old.user_id::text,false)) then
        raise exception 'Session definition is immutable' using errcode='23514';
    end if;
    return new;
end $$;

create function public.delete_my_account() returns void
language plpgsql security definer set search_path='' as $$
declare actor uuid := auth.uid();
begin
    if actor is null then raise exception 'Authentication required' using errcode='42501'; end if;
    perform 1 from auth.users where id=actor for update;
    if not found then raise exception 'Authenticated user not found' using errcode='42501'; end if;
    perform 1 from public.profiles where id=actor for update;

    perform set_config('pawquest.erasing_user', actor::text, true);
    update public.quest_sessions set title_snapshot='Deleted quest'
        where user_id=actor and title_snapshot<>'Deleted quest';
    update public.quests set title='Deleted quest', description='', due_at=null, archived=true
        where user_id=actor;
    update public.profiles set display_name='Deleted user', deleted_at=clock_timestamp()
        where id=actor;
    perform set_config('pawquest.erasing_user', '', true);

    -- Removes credentials and, on Supabase, cascades identities, sessions and refresh tokens.
    delete from auth.users where id=actor;
end $$;

revoke all on function public.delete_my_account() from public, anon, authenticated, service_role;
grant execute on function public.delete_my_account() to authenticated;
comment on function public.delete_my_account() is
    'Permanently deletes the caller''s sign-in identity and erases user-entered text. Ledgers remain anonymous.';
comment on column public.profiles.deleted_at is 'Set when the owning identity was deleted; the row is an anonymous tombstone.';

commit;
