-- Account deletion also erases the pet name the user typed (release audit SEC-02).
-- Pet names are user-entered text, like quest titles and the display name, so the
-- tombstone pet takes its species' default name. Ledgers stay unchanged and anonymous.
-- CREATE OR REPLACE keeps the existing EXECUTE grant (authenticated only).
begin;

create or replace function public.delete_my_account() returns void
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
    update public.pets p set name=s.name
        from public.pet_species s
        where p.user_id=actor and s.code=p.species_code and p.name is distinct from s.name;
    update public.profiles set display_name='Deleted user', deleted_at=clock_timestamp()
        where id=actor;
    perform set_config('pawquest.erasing_user', '', true);

    -- Removes credentials and, on Supabase, cascades identities, sessions and refresh tokens.
    delete from auth.users where id=actor;
end $$;

revoke all on function public.delete_my_account() from public, anon, authenticated, service_role;
grant execute on function public.delete_my_account() to authenticated;
comment on function public.delete_my_account() is
    'Permanently deletes the caller''s sign-in identity and erases user-entered text (quests, display name, pet name). Ledgers remain anonymous.';

commit;
