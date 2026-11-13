begin;
create function pg_temp.streak_assert(value boolean,message text) returns void language plpgsql as $$
begin if value is distinct from true then raise exception 'Widget streak ASSERT: %',message; end if; end $$;
insert into auth.users(id,email) values ('fe300000-0000-0000-0000-000000000001','widget-streak@example.invalid');
insert into public.quests(id,user_id,title,duration_minutes,repeat_type,archived) values
('fe400000-0000-0000-0000-000000000001','fe300000-0000-0000-0000-000000000001','Daily',5,'daily',false),
('fe400000-0000-0000-0000-000000000002','fe300000-0000-0000-0000-000000000001','Once',5,'one_time',false),
('fe400000-0000-0000-0000-000000000003','fe300000-0000-0000-0000-000000000001','Gone',5,'one_time',true);
select pg_temp.streak_assert((select v->>'current_streak'='0' and v->>'completed_today'='0' and v->>'pending_count'='2'
    from (select pawquest_private.widget_context('fe300000-0000-0000-0000-000000000001','2026-11-09 03:00Z','Asia/Bangkok') v) x),'fresh account');
insert into public.quest_sessions(user_id,quest_id,duration_minutes,started_at,ends_at,status) values
('fe300000-0000-0000-0000-000000000001','fe400000-0000-0000-0000-000000000001',5,'2026-11-07 02:00Z','2026-11-07 02:05Z','completed'),
('fe300000-0000-0000-0000-000000000001','fe400000-0000-0000-0000-000000000001',5,'2026-11-08 02:00Z','2026-11-08 02:05Z','completed'),
('fe300000-0000-0000-0000-000000000001','fe400000-0000-0000-0000-000000000001',5,'2026-11-09 02:00Z','2026-11-09 02:05Z','completed'),
('fe300000-0000-0000-0000-000000000001','fe400000-0000-0000-0000-000000000003',5,'2026-11-09 02:20Z','2026-11-09 02:25Z','completed');
insert into public.quest_sessions(user_id,quest_id,duration_minutes,started_at,ends_at,status,cancelled_at) values
('fe300000-0000-0000-0000-000000000001','fe400000-0000-0000-0000-000000000002',5,'2026-11-09 02:10Z','2026-11-09 02:15Z','cancelled','2026-11-09 02:12Z');
create temp table w as select pawquest_private.widget_context('fe300000-0000-0000-0000-000000000001','2026-11-09 03:00Z','Asia/Bangkok') v;
select pg_temp.streak_assert((select v->>'current_streak'='3' from w),'three consecutive UTC days');
select pg_temp.streak_assert((select v->>'completed_today'='2' from w),'distinct completed quests today incl. archived, excluding cancelled');
select pg_temp.streak_assert((select v->>'pending_count'='1' from w),'pending count unchanged');
-- Streak survives the following day until a day is missed, matching Profile.
select pg_temp.streak_assert((pawquest_private.widget_context('fe300000-0000-0000-0000-000000000001','2026-11-10 12:00Z','Asia/Bangkok')->>'current_streak')='3','yesterday keeps streak');
select pg_temp.streak_assert((pawquest_private.widget_context('fe300000-0000-0000-0000-000000000001','2026-11-11 12:00Z','Asia/Bangkok')->>'current_streak')='0','missed day resets');
select pg_temp.streak_assert((pawquest_private.widget_context('fe300000-0000-0000-0000-000000000001','2026-11-10 12:00Z','Asia/Bangkok')->>'completed_today')='0','new local day');
rollback;
