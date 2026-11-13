begin;
alter table public.pets add column stats_updated_at timestamptz not null default clock_timestamp() check(isfinite(stats_updated_at));
create table pawquest_private.pet_care_requests (
    user_id uuid not null references public.profiles(id), request_id uuid not null,
    action text not null check(action in ('feed','play','sleep','touch')),
    primary key(user_id,request_id)
);
revoke all on pawquest_private.pet_care_requests from public,anon,authenticated,service_role;

create function public.pet_state(p_action text default null,p_request_id uuid default null) returns jsonb
language plpgsql security definer set search_path='' as $$
declare actor uuid:=auth.uid(); p public.pets%rowtype; stamp timestamptz;
    hours bigint; bounded integer; previous text; balance bigint;
begin
    if actor is null then raise exception 'Authentication required' using errcode='42501'; end if;
    if p_action is not null and (p_action not in ('feed','play','sleep','touch') or p_request_id is null) then
        raise exception 'Valid care action and request UUID required' using errcode='23514';
    end if;
    -- Match reward posting lock order, and return wallet/pet from one transaction.
    select coins into balance from public.wallets where user_id=actor for update;
    if not found then raise exception 'Account not initialized' using errcode='23503'; end if;
    select * into p from public.pets where user_id=actor for update;
    if not found then raise exception 'Pet not initialized' using errcode='23503'; end if;
    stamp:=clock_timestamp();
    hours:=greatest(0,floor(extract(epoch from (stamp-p.stats_updated_at))/3600))::bigint;
    bounded:=least(hours,100)::integer;
    if hours>0 then
        update public.pets set hunger=greatest(0,hunger-2*bounded),happiness=greatest(0,happiness-bounded),
            energy=greatest(0,energy-bounded),stats_updated_at=stats_updated_at+hours*interval '1 hour'
            where id=p.id returning * into p;
    end if;
    if p_action is not null then
        select action into previous from pawquest_private.pet_care_requests where user_id=actor and request_id=p_request_id;
        if found then
            if previous<>p_action then raise exception 'Request UUID already used' using errcode='23514'; end if;
        else
            insert into pawquest_private.pet_care_requests values(actor,p_request_id,p_action);
            update public.pets set
                hunger=least(100,hunger+case when p_action='feed' then 15 else 0 end),
                happiness=least(100,happiness+case when p_action='play' then 15 when p_action='touch' then 5 else 0 end),
                energy=greatest(0,least(100,energy+case when p_action='sleep' then 20 when p_action='play' then -5 else 0 end))
                where id=p.id returning * into p;
        end if;
    end if;
    return jsonb_build_object('pet',to_jsonb(p),'coins',balance,'server_now',stamp);
end $$;
revoke all on function public.pet_state(text,uuid) from public,anon,authenticated,service_role;
grant execute on function public.pet_state(text,uuid) to authenticated;
commit;
