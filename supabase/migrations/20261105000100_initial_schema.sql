-- Apply through Supabase migrations as postgres. All persisted instants are timestamptz.
begin;

create schema if not exists pawquest_private;
revoke all on schema pawquest_private from public, anon, authenticated, service_role;

create table public.profiles (
    id uuid primary key references auth.users(id) on delete restrict,
    display_name text not null default 'Adventurer' check (length(btrim(display_name)) between 1 and 80),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table public.pets (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null unique references public.profiles(id) on delete restrict,
    name text not null default 'Mochi' check (length(btrim(name)) between 1 and 40),
    species text not null default 'cat' check (species = 'cat'),
    xp bigint not null default 0 check (xp >= 0),
    hunger smallint not null default 100 check (hunger between 0 and 100),
    happiness smallint not null default 100 check (happiness between 0 and 100),
    energy smallint not null default 100 check (energy between 0 and 100),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table public.quests (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null default auth.uid() references public.profiles(id) on delete restrict,
    title text not null check (length(btrim(title)) between 1 and 120),
    description text not null default '' check (length(description) <= 2000),
    duration_minutes smallint not null check (duration_minutes between 5 and 90),
    archived boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (id, user_id)
);

create table public.wallets (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null unique references public.profiles(id) on delete restrict,
    coins bigint not null default 0 check (coins >= 0),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table public.quest_sessions (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references public.profiles(id) on delete restrict,
    quest_id uuid not null,
    duration_minutes smallint not null check (duration_minutes between 5 and 90),
    started_at timestamptz not null,
    ends_at timestamptz not null,
    -- Immutable global policy: completion day in UTC, never the claimant's device timezone.
    reward_day date generated always as ((ends_at at time zone 'UTC')::date) stored,
    status text not null default 'active' check (status in ('active', 'completed', 'cancelled', 'claimed')),
    cancelled_at timestamptz,
    claimed_at timestamptz,
    created_at timestamptz not null default now(),
    foreign key (quest_id, user_id) references public.quests(id, user_id) on delete restrict,
    check (ends_at = started_at + duration_minutes * interval '1 minute'),
    check ((status = 'cancelled') = (cancelled_at is not null)),
    check ((status = 'claimed') = (claimed_at is not null)),
    check (cancelled_at is null or (cancelled_at >= started_at and cancelled_at < ends_at)),
    check (claimed_at is null or claimed_at >= ends_at),
    unique (id, user_id, reward_day)
);
create unique index quest_sessions_one_active_per_user on public.quest_sessions(user_id) where status = 'active';
create index quests_owner_created on public.quests(user_id, created_at desc);
create index quest_sessions_owner_created on public.quest_sessions(user_id, created_at desc);
create index quest_sessions_quest_owner on public.quest_sessions(quest_id, user_id);

create table public.wallet_transactions (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references public.profiles(id) on delete restrict,
    quest_session_id uuid not null unique,
    kind text not null default 'quest_reward' check (kind = 'quest_reward'),
    reward_day date not null,
    reward_slot smallint not null check (reward_slot between 1 and 5),
    coins integer not null check (coins between 0 and 80),
    pet_xp integer not null check (pet_xp in (5, 10, 20, 30, 40, 50)),
    balance_after bigint not null check (balance_after >= coins),
    created_at timestamptz not null default now(),
    foreign key (quest_session_id, user_id, reward_day)
        references public.quest_sessions(id, user_id, reward_day) on delete restrict,
    foreign key (user_id) references public.wallets(user_id) on delete restrict,
    foreign key (user_id) references public.pets(user_id) on delete restrict,
    unique (user_id, reward_day, reward_slot)
);
-- The unique slot index also supports daily sums/counts; unique session index supports replay.
create index wallet_transactions_owner_created on public.wallet_transactions(user_id, created_at desc);

create function pawquest_private.touch_updated_at() returns trigger
language plpgsql set search_path = '' as $$
begin
    new.updated_at := clock_timestamp();
    return new;
end;
$$;
create trigger profiles_updated before update on public.profiles for each row execute function pawquest_private.touch_updated_at();
create trigger pets_updated before update on public.pets for each row execute function pawquest_private.touch_updated_at();
create trigger quests_updated before update on public.quests for each row execute function pawquest_private.touch_updated_at();
create trigger wallets_updated before update on public.wallets for each row execute function pawquest_private.touch_updated_at();

create function pawquest_private.bootstrap_user() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
    insert into public.profiles(id) values (new.id);
    insert into public.pets(user_id) values (new.id);
    insert into public.wallets(user_id) values (new.id);
    return new;
end;
$$;
create trigger pawquest_auth_user_created after insert on auth.users
for each row execute function pawquest_private.bootstrap_user();
-- Reproducible initialization for accounts that predate this migration.
insert into public.profiles(id) select id from auth.users on conflict (id) do nothing;
insert into public.pets(user_id) select id from public.profiles on conflict (user_id) do nothing;
insert into public.wallets(user_id) select id from public.profiles on conflict (user_id) do nothing;

alter table public.profiles enable row level security;
alter table public.pets enable row level security;
alter table public.quests enable row level security;
alter table public.quest_sessions enable row level security;
alter table public.wallets enable row level security;
alter table public.wallet_transactions enable row level security;

revoke all on public.profiles, public.pets, public.quests, public.quest_sessions,
    public.wallets, public.wallet_transactions from public, anon, authenticated, service_role;
grant select on public.profiles, public.pets, public.quests, public.quest_sessions,
    public.wallets, public.wallet_transactions to authenticated, service_role;
grant update (display_name) on public.profiles to authenticated;
grant update (name) on public.pets to authenticated;
grant insert (user_id, title, description, duration_minutes, archived) on public.quests to authenticated;
grant update (title, description, duration_minutes, archived) on public.quests to authenticated;

create policy profiles_read_own on public.profiles for select to authenticated using (id = (select auth.uid()));
create policy profiles_update_own on public.profiles for update to authenticated
    using (id = (select auth.uid())) with check (id = (select auth.uid()));
create policy pets_read_own on public.pets for select to authenticated using (user_id = (select auth.uid()));
create policy pets_rename_own on public.pets for update to authenticated
    using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));
create policy quests_read_own on public.quests for select to authenticated using (user_id = (select auth.uid()));
create policy quests_insert_own on public.quests for insert to authenticated with check (user_id = (select auth.uid()));
create policy quests_update_own on public.quests for update to authenticated
    using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));
create policy sessions_read_own on public.quest_sessions for select to authenticated using (user_id = (select auth.uid()));
create policy wallets_read_own on public.wallets for select to authenticated using (user_id = (select auth.uid()));
create policy transactions_read_own on public.wallet_transactions for select to authenticated using (user_id = (select auth.uid()));

revoke all on function pawquest_private.touch_updated_at(), pawquest_private.bootstrap_user()
    from public, anon, authenticated, service_role;
commit;
