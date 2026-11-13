begin;
insert into auth.users(id,email) values('ef100000-0000-0000-0000-000000000001','new-pets@example.invalid');
select set_config('request.jwt.claim.sub','ef100000-0000-0000-0000-000000000001',true);
do $$
declare code text; required_xp bigint; result jsonb;
begin
    foreach code in array array['capybara','rabbit'] loop
        required_xp:=case code when 'capybara' then 900 else 1400 end;
        update public.pets set xp=required_xp-1 where user_id=auth.uid();
        begin perform public.pet_select_species(code);raise exception 'Early unlock accepted';
        exception when insufficient_privilege then null;end;
        update public.pets set xp=required_xp where user_id=auth.uid();
        begin perform public.pet_select_species(code);raise exception 'XP incorrectly grants pet';
        exception when insufficient_privilege then null;end;
        insert into public.user_pet_unlocks(user_id,species_code,source) values(auth.uid(),code,'LEGACY');
        perform public.pet_select_species(code);
        if not exists(select 1 from public.pets where user_id=auth.uid() and species_code=code and renderer_key=code and xp=required_xp) then raise exception 'Pet selection failed';end if;
        if (select coins<>0 from public.wallets where user_id=auth.uid()) then raise exception 'Appearance granted coins';end if;
    end loop;
    result:=public.pet_collection();
    if jsonb_array_length(result->'species')<>4 then raise exception 'Missing published previews';end if;
end $$;
rollback;
\echo PASS Capybara/Rabbit require ownership, not XP thresholds, and preserve selection state
