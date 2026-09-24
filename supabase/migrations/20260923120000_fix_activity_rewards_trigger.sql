-- PL/pgSQL resolves every NEW.<field> in an IF expression even when the
-- tg_table_name check is false, so `tg_table_name = 'daily_steps' and new.steps`
-- failed on water_logs rows ("record new has no field steps"). Each branch now
-- only reads its own table's columns.
create or replace function public.hedefit_activity_rewards()
returns trigger language plpgsql security definer set search_path = public as $$
declare
  prefs public.gamification_preferences%rowtype;
  local_day date;
  week_start date;
  activity_count integer;
  week_distance numeric;
  activity_hour integer;
begin
  select * into prefs from public.gamification_preferences where user_id = new.user_id;
  if not found then
    insert into public.gamification_preferences(user_id) values (new.user_id) on conflict do nothing;
    select * into prefs from public.gamification_preferences where user_id = new.user_id;
  end if;

  if tg_table_name = 'workout_sessions' then
    local_day := (new.completed_at at time zone prefs.timezone)::date;
    week_start := date_trunc('week', local_day::timestamp)::date;
    perform public.hedefit_award_xp(new.user_id, 'WORKOUT_COMPLETED', new.id::text, 50, new.completed_at);
    select count(distinct (completed_at at time zone prefs.timezone)::date) into activity_count
      from public.workout_sessions where user_id = new.user_id
      and (completed_at at time zone prefs.timezone)::date between week_start and week_start + 6;
    if activity_count >= prefs.weekly_activity_goal then
      perform public.hedefit_award_xp(new.user_id, 'WEEKLY_GOAL_COMPLETED', week_start::text, 100, new.completed_at);
    end if;
    activity_hour := extract(hour from new.completed_at at time zone prefs.timezone);
    insert into public.user_achievements(user_id, achievement_id, unlocked_at) values (new.user_id, 'first_activity', new.completed_at) on conflict do nothing;
    if (select count(*) from public.workout_sessions where user_id = new.user_id) >= 50 then
      insert into public.user_achievements(user_id, achievement_id, unlocked_at) values (new.user_id, 'workouts_50', new.completed_at) on conflict do nothing;
    end if;
    if activity_hour < 8 and (select count(*) from public.workout_sessions where user_id = new.user_id and extract(hour from completed_at at time zone prefs.timezone) < 8) >= 10 then
      insert into public.user_achievements(user_id, achievement_id, unlocked_at) values (new.user_id, 'early_bird', new.completed_at) on conflict do nothing;
    end if;
    if activity_hour >= 20 and (select count(*) from public.workout_sessions where user_id = new.user_id and extract(hour from completed_at at time zone prefs.timezone) >= 20) >= 10 then
      insert into public.user_achievements(user_id, achievement_id, unlocked_at) values (new.user_id, 'night_athlete', new.completed_at) on conflict do nothing;
    end if;
  elsif tg_table_name = 'daily_steps' then
    if new.steps < prefs.daily_step_goal then return new; end if;
    perform public.hedefit_award_xp(new.user_id, 'STEP_GOAL_COMPLETED', new.local_date::text, 20, new.local_date::timestamp at time zone prefs.timezone);
    if (select coalesce(sum(steps), 0) from public.daily_steps where user_id = new.user_id) >= 100000 then
      insert into public.user_achievements(user_id, achievement_id) values (new.user_id, 'steps_100k') on conflict do nothing;
    end if;
  elsif tg_table_name = 'water_logs' then
    if new.milliliters < prefs.daily_water_goal_ml then return new; end if;
    perform public.hedefit_award_xp(new.user_id, 'WATER_GOAL_COMPLETED', new.local_date::text, 10, new.local_date::timestamp at time zone prefs.timezone);
  elsif tg_table_name = 'route_activities' then
    if new.status <> 'completed' then return new; end if;
    local_day := (new.started_at at time zone prefs.timezone)::date;
    week_start := date_trunc('week', local_day::timestamp)::date;
    insert into public.weekly_challenges(challenge_type, title, target, reward_xp, starts_on, ends_on)
    values ('DISTANCE', '15 km Koş/Yürü', 15, 250, week_start, week_start + 6)
    on conflict (challenge_type, starts_on) do nothing;
    if floor(greatest(new.distance_meters, 0) / 1000) > 0 then
      perform public.hedefit_award_xp(new.user_id, 'ROUTE_DISTANCE', new.id::text, floor(new.distance_meters / 1000)::integer * 10, new.started_at);
    end if;
    select coalesce(sum(distance_meters), 0) into week_distance from public.route_activities
      where user_id = new.user_id and status = 'completed'
      and (started_at at time zone prefs.timezone)::date between week_start and week_start + 6;
    if week_distance >= 15000 then
      perform public.hedefit_award_xp(new.user_id, 'WEEKLY_CHALLENGE_COMPLETED', 'distance:' || week_start::text, 250, new.started_at);
    end if;
    insert into public.user_achievements(user_id, achievement_id, unlocked_at) values (new.user_id, 'first_activity', new.started_at) on conflict do nothing;
    if (select coalesce(sum(distance_meters), 0) from public.route_activities where user_id = new.user_id and status = 'completed') >= 42200 then
      insert into public.user_achievements(user_id, achievement_id, unlocked_at) values (new.user_id, 'marathon_distance', new.started_at) on conflict do nothing;
    end if;
  end if;
  return new;
end $$;
