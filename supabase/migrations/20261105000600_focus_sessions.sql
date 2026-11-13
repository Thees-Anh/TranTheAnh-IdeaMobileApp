begin;
alter table public.quest_sessions
    add column request_id uuid not null default gen_random_uuid(),
    add column title_snapshot text not null default 'Quest',
    add constraint sessions_owner_request unique(user_id,request_id);
update public.quest_sessions s set title_snapshot=q.title from public.quests q where q.id=s.quest_id;

create function pawquest_private.freeze_session_definition() returns trigger
language plpgsql security definer set search_path='' as $$
begin
    if tg_op='INSERT' then
        new.title_snapshot:=coalesce((select title from public.quests where id=new.quest_id and user_id=new.user_id),new.title_snapshot);
    elsif row(new.id,new.user_id,new.quest_id,new.duration_minutes,new.started_at,new.ends_at,new.request_id,new.title_snapshot)
        is distinct from row(old.id,old.user_id,old.quest_id,old.duration_minutes,old.started_at,old.ends_at,old.request_id,old.title_snapshot) then
        raise exception 'Session definition is immutable' using errcode='23514';
    end if;
    return new;
end $$;
create trigger session_definition_frozen before insert or update on public.quest_sessions
    for each row execute function pawquest_private.freeze_session_definition();

create function pawquest_private.focus_snapshot(p_session_id uuid) returns jsonb
language plpgsql security definer set search_path='' as $$
declare result jsonb; server_now timestamptz:=clock_timestamp();
begin
    select jsonb_build_object('session',to_jsonb(s),
        'eligible',s.status in ('active','completed') and s.ends_at<=server_now,
        'reward',(select jsonb_build_object('coins',t.coins,'pet_xp',t.pet_xp,'balance_after',t.balance_after)
            from public.wallet_transactions t where t.quest_session_id=s.id)) into result
        from public.quest_sessions s where s.id=p_session_id and s.user_id=auth.uid();
    return jsonb_build_object('server_now',server_now) || coalesce(result,'{"session":null,"eligible":false,"reward":null}'::jsonb);
end $$;

create function public.focus_start(p_quest_id uuid,p_request_id uuid) returns jsonb
language plpgsql security definer set search_path='' as $$
declare actor uuid:=auth.uid(); existing public.quest_sessions%rowtype;
    quest public.quests%rowtype; stamp timestamptz;
begin
    if actor is null then raise exception 'Authentication required' using errcode='42501'; end if;
    if p_request_id is null then raise exception 'Request UUID required' using errcode='23514'; end if;
    perform 1 from public.wallets where user_id=actor for update;
    if not found then raise exception 'Account not initialized' using errcode='23503'; end if;
    select * into existing from public.quest_sessions where user_id=actor and request_id=p_request_id;
    if found then
        if existing.quest_id<>p_quest_id then raise exception 'Request already used' using errcode='23514'; end if;
        return pawquest_private.focus_snapshot(existing.id);
    end if;
    stamp:=clock_timestamp();
    update public.quest_sessions set status='completed' where user_id=actor and status='active' and ends_at<=stamp;
    if exists(select 1 from public.quest_sessions where user_id=actor and status='active') then
        raise exception 'An active session already exists' using errcode='23505';
    end if;
    select * into quest from public.quests where id=p_quest_id and user_id=actor and not archived for share;
    if not found then raise exception 'Quest unavailable' using errcode='42501'; end if;
    insert into public.quest_sessions(user_id,quest_id,duration_minutes,started_at,ends_at,request_id)
        values(actor,quest.id,quest.duration_minutes,stamp,stamp+quest.duration_minutes*interval '1 minute',p_request_id)
        returning * into existing;
    return pawquest_private.focus_snapshot(existing.id);
end $$;

create function public.focus_reconcile(p_session_id uuid default null) returns jsonb
language plpgsql security definer set search_path='' as $$
declare actor uuid:=auth.uid(); selected_id uuid;
begin
    if actor is null then raise exception 'Authentication required' using errcode='42501'; end if;
    perform 1 from public.wallets where user_id=actor for update;
    update public.quest_sessions set status='completed' where user_id=actor and status='active' and ends_at<=clock_timestamp();
    if p_session_id is not null then
        select id into selected_id from public.quest_sessions where id=p_session_id and user_id=actor;
        if not found then raise exception 'Session unavailable' using errcode='42501'; end if;
    else
        select id into selected_id from public.quest_sessions where user_id=actor and status in ('active','completed')
            order by (status='active') desc,ends_at,id limit 1;
    end if;
    return pawquest_private.focus_snapshot(selected_id);
end $$;

create function public.focus_cancel(p_session_id uuid) returns jsonb
language plpgsql security definer set search_path='' as $$
begin
    perform public.cancel_quest_session(p_session_id);
    return pawquest_private.focus_snapshot(p_session_id);
end $$;
create function public.focus_claim(p_session_id uuid) returns jsonb
language plpgsql security definer set search_path='' as $$
begin
    perform public.claim_quest_reward(p_session_id);
    return pawquest_private.focus_snapshot(p_session_id);
end $$;
revoke all on function pawquest_private.freeze_session_definition(),pawquest_private.focus_snapshot(uuid)
    from public,anon,authenticated,service_role;
revoke all on function public.focus_start(uuid,uuid),public.focus_reconcile(uuid),public.focus_cancel(uuid),public.focus_claim(uuid)
    from public,anon,authenticated,service_role;
grant execute on function public.focus_start(uuid,uuid),public.focus_reconcile(uuid),public.focus_cancel(uuid),public.focus_claim(uuid)
    to authenticated;
commit;
