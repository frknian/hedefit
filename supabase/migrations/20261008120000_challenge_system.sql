-- Keşfet + Challenge + XP/Level sistemi.
--
-- YENİDEN KULLANILANLAR (yeni paralel yapı yok):
--   * public.xp_events      → XP transaction log'u. unique(user_id, source, source_id) duplicate XP'yi engeller;
--                             XP satırları hiçbir akışta silinmez (görev kaçırmak XP düşürmez).
--   * public.user_achievements → genişletilebilir rozet modeli (achievement_id serbest metin).
--   * public.challenges / challenge_participants → arkadaş challenge'ları (davet / kabul / ret / ilerleme).
--   * workout_sessions, daily_steps, water_logs, food_entries, daily_checkins → görev doğrulaması bu
--     tablolardan okunur; antrenman verisi kopyalanmaz.
--
-- YENİ:
--   * public.xp_rules              → merkezi, yapılandırılabilir XP değerleri (kod içinde dağınık sabit yok).
--   * public.user_challenges       → kullanıcının katıldığı challenge + plan SNAPSHOT'ı.
--   * public.user_challenge_days   → tamamlanan / uyarlanan / toparlanma günleri.
--
-- Tekrar çalıştırılabilir (idempotent). Mevcut veriye dokunmaz; yalnızca ekler ve fonksiyonları günceller.

-- 1) Merkezi XP kuralları ------------------------------------------------------------------------------
create table if not exists public.xp_rules (
  source text primary key,
  amount integer not null check (amount between 0 and 10000),
  daily_capped boolean not null default true,
  description text,
  updated_at timestamptz not null default now()
);

insert into public.xp_rules(source, amount, daily_capped, description) values
  ('CHECKIN_COMPLETED', 5, true, 'Günlük check-in'),
  ('WORKOUT_COMPLETED', 50, true, 'Antrenman tamamlandı'),
  ('STEP_GOAL_COMPLETED', 20, true, 'Günlük adım hedefi'),
  ('ROUTE_DISTANCE', 10, true, 'Rota: kilometre başına'),
  ('WATER_GOAL_COMPLETED', 10, true, 'Su hedefi'),
  ('NUTRITION_TARGET_COMPLETED', 20, true, 'Beslenme hedefi (3 öğün kaydı)'),
  ('SLEEP_GOAL_COMPLETED', 10, true, 'Uyku hedefi'),
  ('WEEKLY_GOAL_COMPLETED', 100, false, 'Haftalık aktivite hedefi'),
  ('WEEKLY_CHALLENGE_COMPLETED', 250, false, 'Haftalık mesafe görevi'),
  ('ACHIEVEMENT_UNLOCKED', 100, false, 'Rozet açıldı'),
  ('CHALLENGE_DAY_COMPLETED', 25, false, 'Challenge günü (uyarlanmış gün dahil)'),
  ('CHALLENGE_RECOVERY_DAY', 5, false, 'Challenge toparlanma günü'),
  ('CHALLENGE_STREAK_3', 30, false, 'Challenge 3 günlük seri'),
  ('CHALLENGE_STREAK_7', 100, false, 'Challenge her 7 günlük seri'),
  ('CHALLENGE_COMPLETED', 250, false, 'Challenge tamamlandı'),
  ('FRIEND_CHALLENGE_BONUS', 100, false, 'Arkadaşla challenge tamamlama bonusu')
on conflict (source) do nothing;

alter table public.xp_rules enable row level security;
drop policy if exists "xp rules authenticated read" on public.xp_rules;
create policy "xp rules authenticated read" on public.xp_rules for select to authenticated using (true);
grant select on public.xp_rules to authenticated;

alter table public.xp_events drop constraint if exists xp_events_source_check;
alter table public.xp_events add constraint xp_events_source_check check (source in (
  'WORKOUT_COMPLETED', 'STEP_GOAL_COMPLETED', 'ROUTE_DISTANCE',
  'WATER_GOAL_COMPLETED', 'NUTRITION_TARGET_COMPLETED', 'SLEEP_GOAL_COMPLETED',
  'WEEKLY_GOAL_COMPLETED', 'WEEKLY_CHALLENGE_COMPLETED', 'ACHIEVEMENT_UNLOCKED',
  'CHECKIN_COMPLETED', 'CHALLENGE_DAY_COMPLETED', 'CHALLENGE_RECOVERY_DAY',
  'CHALLENGE_STREAK_3', 'CHALLENGE_STREAK_7', 'CHALLENGE_COMPLETED', 'FRIEND_CHALLENGE_BONUS'
));

create or replace function public.hedefit_xp_amount(p_source text)
returns integer language sql stable security definer set search_path = public as $$
  select coalesce((select amount from public.xp_rules where source = p_source), 0);
$$;
revoke all on function public.hedefit_xp_amount(text) from public, anon;
grant execute on function public.hedefit_xp_amount(text) to authenticated;

-- XP yazımı: idempotent (aynı kaynak+kimlik ikinci kez yazılmaz), günlük tavan xp_rules.daily_capped'e göre.
-- Verilen XP'yi döner (duplicate ya da tavan dolu ise 0).
create or replace function public.hedefit_award_xp_amount(p_user_id uuid, p_source text, p_source_id text, p_amount integer, p_occurred_at timestamptz)
returns integer language plpgsql security definer set search_path = public as $$
declare
  v_tz text := coalesce((select timezone from public.gamification_preferences where user_id = p_user_id), 'Europe/Istanbul');
  v_day date := (p_occurred_at at time zone v_tz)::date;
  v_capped boolean := coalesce((select daily_capped from public.xp_rules where source = p_source),
                               p_source not in ('ACHIEVEMENT_UNLOCKED', 'WEEKLY_GOAL_COMPLETED', 'WEEKLY_CHALLENGE_COMPLETED'));
  v_today_xp integer := 0;
  v_amount integer := greatest(0, coalesce(p_amount, 0));
  v_inserted integer;
begin
  if exists (select 1 from public.xp_events where user_id = p_user_id and source = p_source and source_id = p_source_id) then
    return 0;
  end if;
  if v_capped then
    select coalesce(sum(e.amount), 0) into v_today_xp from public.xp_events e
      left join public.xp_rules r on r.source = e.source
      where e.user_id = p_user_id
        and coalesce(r.daily_capped, e.source not in ('ACHIEVEMENT_UNLOCKED', 'WEEKLY_GOAL_COMPLETED', 'WEEKLY_CHALLENGE_COMPLETED'))
        and (e.occurred_at at time zone v_tz)::date = v_day;
    v_amount := least(v_amount, greatest(0, 100 - v_today_xp));
  end if;
  if v_amount <= 0 then return 0; end if;
  insert into public.xp_events(user_id, source, source_id, amount, occurred_at)
  values (p_user_id, p_source, p_source_id, v_amount, p_occurred_at)
  on conflict (user_id, source, source_id) do nothing
  returning amount into v_inserted;
  return coalesce(v_inserted, 0);
end $$;
revoke all on function public.hedefit_award_xp_amount(uuid, text, text, integer, timestamptz) from public, anon, authenticated;

-- Geriye dönük uyumlu imza (mevcut tetikleyiciler bunu çağırıyor).
create or replace function public.hedefit_award_xp(p_user_id uuid, p_source text, p_source_id text, p_amount integer, p_occurred_at timestamptz)
returns void language plpgsql security definer set search_path = public as $$
begin
  perform public.hedefit_award_xp_amount(p_user_id, p_source, p_source_id, p_amount, p_occurred_at);
end $$;
revoke all on function public.hedefit_award_xp(uuid, text, text, integer, timestamptz) from public, anon, authenticated;

-- Kural tablosundaki değerle ödül.
create or replace function public.hedefit_award_rule(p_user_id uuid, p_source text, p_source_id text, p_occurred_at timestamptz, p_multiplier integer default 1)
returns integer language sql security definer set search_path = public as $$
  select public.hedefit_award_xp_amount(p_user_id, p_source, p_source_id, public.hedefit_xp_amount(p_source) * greatest(1, coalesce(p_multiplier, 1)), p_occurred_at);
$$;
revoke all on function public.hedefit_award_rule(uuid, text, text, timestamptz, integer) from public, anon, authenticated;

-- Rozet açıldığında (hangi akıştan olursa olsun) bir defaya mahsus rozet XP'si.
create or replace function public.hedefit_achievement_xp()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  perform public.hedefit_award_rule(new.user_id, 'ACHIEVEMENT_UNLOCKED', new.achievement_id, new.unlocked_at);
  return new;
end $$;
drop trigger if exists hedefit_achievement_xp on public.user_achievements;
create trigger hedefit_achievement_xp after insert on public.user_achievements for each row execute function public.hedefit_achievement_xp();

-- 2) Mevcut aktivite ödülleri: aynı mantık, değerler artık xp_rules'tan --------------------------------
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
    perform public.hedefit_award_rule(new.user_id, 'WORKOUT_COMPLETED', new.id::text, new.completed_at);
    select count(distinct (completed_at at time zone prefs.timezone)::date) into activity_count
      from public.workout_sessions where user_id = new.user_id
      and (completed_at at time zone prefs.timezone)::date between week_start and week_start + 6;
    if activity_count >= prefs.weekly_activity_goal then
      perform public.hedefit_award_rule(new.user_id, 'WEEKLY_GOAL_COMPLETED', week_start::text, new.completed_at);
    end if;
    activity_hour := extract(hour from new.completed_at at time zone prefs.timezone);
    insert into public.user_achievements(user_id, achievement_id, unlocked_at) values (new.user_id, 'first_activity', new.completed_at) on conflict do nothing;
    if (select count(*) from public.workout_sessions where user_id = new.user_id) >= 10 then
      insert into public.user_achievements(user_id, achievement_id, unlocked_at) values (new.user_id, 'workouts_10', new.completed_at) on conflict do nothing;
    end if;
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
    perform public.hedefit_award_rule(new.user_id, 'STEP_GOAL_COMPLETED', new.local_date::text, new.local_date::timestamp at time zone prefs.timezone);
    if (select coalesce(sum(steps), 0) from public.daily_steps where user_id = new.user_id) >= 100000 then
      insert into public.user_achievements(user_id, achievement_id) values (new.user_id, 'steps_100k') on conflict do nothing;
    end if;
  elsif tg_table_name = 'water_logs' then
    if new.milliliters < prefs.daily_water_goal_ml then return new; end if;
    perform public.hedefit_award_rule(new.user_id, 'WATER_GOAL_COMPLETED', new.local_date::text, new.local_date::timestamp at time zone prefs.timezone);
  elsif tg_table_name = 'daily_checkins' then
    perform public.hedefit_award_rule(new.user_id, 'CHECKIN_COMPLETED', new.day::text, new.day::timestamp at time zone prefs.timezone);
  elsif tg_table_name = 'food_entries' then
    local_day := (new.consumed_at at time zone prefs.timezone)::date;
    -- "Beslenme hedefi": o gün en az 3 farklı öğün kaydı. Günde bir kez.
    if (select count(distinct meal) from public.food_entries where user_id = new.user_id and (consumed_at at time zone prefs.timezone)::date = local_day) >= 3 then
      perform public.hedefit_award_rule(new.user_id, 'NUTRITION_TARGET_COMPLETED', local_day::text, new.consumed_at);
    end if;
  elsif tg_table_name = 'route_activities' then
    if new.status <> 'completed' then return new; end if;
    local_day := (new.started_at at time zone prefs.timezone)::date;
    week_start := date_trunc('week', local_day::timestamp)::date;
    insert into public.weekly_challenges(challenge_type, title, target, reward_xp, starts_on, ends_on)
    values ('DISTANCE', '15 km Koş/Yürü', 15, public.hedefit_xp_amount('WEEKLY_CHALLENGE_COMPLETED'), week_start, week_start + 6)
    on conflict (challenge_type, starts_on) do nothing;
    if floor(greatest(new.distance_meters, 0) / 1000) > 0 then
      perform public.hedefit_award_rule(new.user_id, 'ROUTE_DISTANCE', new.id::text, new.started_at, floor(new.distance_meters / 1000)::integer);
    end if;
    select coalesce(sum(distance_meters), 0) into week_distance from public.route_activities
      where user_id = new.user_id and status = 'completed'
      and (started_at at time zone prefs.timezone)::date between week_start and week_start + 6;
    if week_distance >= 15000 then
      perform public.hedefit_award_rule(new.user_id, 'WEEKLY_CHALLENGE_COMPLETED', 'distance:' || week_start::text, new.started_at);
    end if;
    insert into public.user_achievements(user_id, achievement_id, unlocked_at) values (new.user_id, 'first_activity', new.started_at) on conflict do nothing;
    if (select coalesce(sum(distance_meters), 0) from public.route_activities where user_id = new.user_id and status = 'completed') >= 42200 then
      insert into public.user_achievements(user_id, achievement_id, unlocked_at) values (new.user_id, 'marathon_distance', new.started_at) on conflict do nothing;
    end if;
  end if;
  return new;
end $$;

do $$ begin
  if to_regclass('public.daily_checkins') is not null then
    drop trigger if exists hedefit_checkin_rewards on public.daily_checkins;
    create trigger hedefit_checkin_rewards after insert on public.daily_checkins for each row execute function public.hedefit_activity_rewards();
  end if;
  if to_regclass('public.food_entries') is not null then
    drop trigger if exists hedefit_nutrition_rewards on public.food_entries;
    create trigger hedefit_nutrition_rewards after insert on public.food_entries for each row execute function public.hedefit_activity_rewards();
  end if;
end $$;

-- 3) Challenge tabloları ---------------------------------------------------------------------------------
create table if not exists public.user_challenges (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  template_key text not null check (template_key ~ '^[a-z0-9_:.-]{2,80}$'),
  category text not null check (category in ('workout', 'nutrition', 'steps', 'pilates', 'flexibility', 'coach')),
  plan jsonb not null,
  total_days smallint not null check (total_days between 3 and 30),
  recovery_allowance smallint not null check (recovery_allowance between 1 and 5),
  status text not null default 'active' check (status in ('active', 'completed', 'abandoned')),
  social_challenge_id uuid references public.challenges(id) on delete set null,
  started_on date not null,
  started_at timestamptz not null default now(),
  completed_at timestamptz,
  abandoned_at timestamptz,
  updated_at timestamptz not null default now()
);
-- Aynı challenge'a iki kez (aynı anda) katılınamaz; bırakılan ya da biten challenge yeniden başlatılabilir.
create unique index if not exists user_challenges_one_active on public.user_challenges (user_id, template_key) where status = 'active';
create index if not exists user_challenges_user_idx on public.user_challenges (user_id, status, started_at desc);
create index if not exists user_challenges_social_idx on public.user_challenges (social_challenge_id) where social_challenge_id is not null;
create index if not exists user_challenges_template_idx on public.user_challenges (template_key);

create table if not exists public.user_challenge_days (
  user_challenge_id uuid not null references public.user_challenges(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  day_index smallint not null check (day_index between 0 and 29),
  local_date date not null,
  status text not null check (status in ('completed', 'adapted', 'recovery')),
  minutes smallint check (minutes is null or minutes between 0 and 240),
  -- Bilerek FK değil: antrenman silinirse tamamlanmış gün (ve kazanılan XP) korunur.
  session_id uuid,
  created_at timestamptz not null default now(),
  primary key (user_challenge_id, day_index),
  unique (user_challenge_id, local_date)
);
create index if not exists user_challenge_days_user_idx on public.user_challenge_days (user_id, local_date desc);

alter table public.user_challenges enable row level security;
alter table public.user_challenge_days enable row level security;
drop policy if exists "user challenges own read" on public.user_challenges;
create policy "user challenges own read" on public.user_challenges for select using (auth.uid() = user_id);
drop policy if exists "user challenge days own read" on public.user_challenge_days;
create policy "user challenge days own read" on public.user_challenge_days for select using (auth.uid() = user_id);
-- Yazım YALNIZCA aşağıdaki RPC'lerle: doğrulama ve XP tek yerde.
grant select on public.user_challenges, public.user_challenge_days to authenticated;

-- Plan doğrulaması: lib/challenges/catalog.ts validatePlan ile aynı sınırlar. Kolaylaştırılmış hedefler reddedilir.
create or replace function public.hedefit_valid_challenge_plan(p_plan jsonb)
returns boolean language plpgsql immutable as $$
declare
  v_task jsonb;
  v_kind text;
  v_count integer;
begin
  if p_plan is null or jsonb_typeof(p_plan) <> 'object' or jsonb_typeof(p_plan->'days') <> 'array' then return false; end if;
  if coalesce(p_plan->>'key', '') !~ '^[a-z0-9_:.-]{2,80}$' then return false; end if;
  if coalesce(p_plan->>'category', '') not in ('workout', 'nutrition', 'steps', 'pilates', 'flexibility', 'coach') then return false; end if;
  if coalesce(length(p_plan->'title'->>'tr'), 0) not between 1 and 240 or coalesce(length(p_plan->'title'->>'en'), 0) not between 1 and 240 then return false; end if;
  v_count := jsonb_array_length(p_plan->'days');
  if v_count < 3 or v_count > 30 then return false; end if;
  for v_task in select value from jsonb_array_elements(p_plan->'days') loop
    v_kind := v_task->>'kind';
    if v_kind = 'session' then
      if coalesce(v_task->>'session', '') not in ('pilates_today', 'low_impact_recovery', 'posture_mobility', 'core_focus', 'band_strength', 'flexibility', 'home_strength') then return false; end if;
      if coalesce((v_task->>'minutes')::numeric, 0) not between 5 and 60 then return false; end if;
    elsif v_kind = 'workout' then
      if v_task ? 'minutes' and coalesce((v_task->>'minutes')::numeric, 0) not between 10 and 120 then return false; end if;
    elsif v_kind = 'steps' then
      if coalesce((v_task->>'target')::numeric, 0) not between 3000 and 30000 then return false; end if;
    elsif v_kind = 'water' then
      if coalesce((v_task->>'target')::numeric, 0) not between 1000 and 5000 then return false; end if;
    elsif v_kind = 'meals' then
      if coalesce((v_task->>'target')::numeric, 0) not between 2 and 6 then return false; end if;
    elsif v_kind <> 'checkin' then
      return false;
    end if;
  end loop;
  return true;
exception when others then
  return false;
end $$;

create or replace function public.hedefit_local_today(p_user_id uuid)
returns date language sql stable security definer set search_path = public as $$
  select (now() at time zone coalesce((select timezone from public.gamification_preferences where user_id = p_user_id), 'Europe/Istanbul'))::date;
$$;
revoke all on function public.hedefit_local_today(uuid) from public, anon, authenticated;

-- Katıl: plan snapshot'ı saklanır. Aynı anda en fazla 3 aktif challenge.
create or replace function public.hedefit_join_challenge(p_plan jsonb, p_local_date date, p_social_challenge_id uuid default null)
returns uuid language plpgsql security definer set search_path = public as $$
declare
  v_user uuid := auth.uid();
  v_today date;
  v_days integer;
  v_id uuid;
begin
  if v_user is null then raise exception 'not_authenticated' using errcode = '42501'; end if;
  if not public.account_is_active() then raise exception 'account_inactive' using errcode = '42501'; end if;
  if not public.hedefit_valid_challenge_plan(p_plan) then raise exception 'invalid_plan' using errcode = '22023'; end if;
  v_today := public.hedefit_local_today(v_user);
  if p_local_date is null or abs(p_local_date - v_today) > 1 then raise exception 'invalid_day' using errcode = '22023'; end if;
  if p_social_challenge_id is not null and not exists (
    select 1 from public.challenge_participants where challenge_id = p_social_challenge_id and user_id = v_user and status = 'joined'
  ) then raise exception 'not_a_participant' using errcode = '42501'; end if;
  if exists (select 1 from public.user_challenges where user_id = v_user and template_key = p_plan->>'key' and status = 'active') then
    raise exception 'already_joined' using errcode = '23505';
  end if;
  if (select count(*) from public.user_challenges where user_id = v_user and status = 'active') >= 3 then
    raise exception 'too_many_active' using errcode = '22023';
  end if;
  v_days := jsonb_array_length(p_plan->'days');
  insert into public.user_challenges(user_id, template_key, category, plan, total_days, recovery_allowance, social_challenge_id, started_on)
  values (v_user, p_plan->>'key', p_plan->>'category', p_plan, v_days, greatest(1, least(5, v_days / 7)), p_social_challenge_id, p_local_date)
  returning id into v_id;
  return v_id;
end $$;
revoke all on function public.hedefit_join_challenge(jsonb, date, uuid) from public, anon;
grant execute on function public.hedefit_join_challenge(jsonb, date, uuid) to authenticated;

-- Bırak: XP ve tamamlanan günler korunur; aynı challenge sonra yeniden başlatılabilir.
create or replace function public.hedefit_abandon_challenge(p_id uuid)
returns void language plpgsql security definer set search_path = public as $$
begin
  update public.user_challenges set status = 'abandoned', abandoned_at = now(), updated_at = now()
  where id = p_id and user_id = auth.uid() and status = 'active';
  if not found then raise exception 'not_found' using errcode = 'P0002'; end if;
end $$;
revoke all on function public.hedefit_abandon_challenge(uuid) from public, anon;
grant execute on function public.hedefit_abandon_challenge(uuid) to authenticated;

-- Gün tamamla. Görev sunucuda MEVCUT kayıtlardan doğrulanır (antrenman / adım / su / öğün / check-in);
-- "adapted" yalnızca o güne ait düşük hazırlık check-in'i varsa kabul edilir; "recovery" hak ile sınırlıdır.
-- Aynı gün ikinci kez çağrılırsa mevcut sonucu döner ve XP vermez (idempotent).
create or replace function public.hedefit_complete_challenge_day(p_user_challenge_id uuid, p_local_date date, p_status text, p_minutes integer default null, p_session_id uuid default null)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_user uuid := auth.uid();
  v_uc public.user_challenges%rowtype;
  v_tz text;
  v_today date;
  v_last date;
  v_index integer;
  v_task jsonb;
  v_kind text;
  v_target numeric;
  v_minutes integer;
  v_required_seconds integer;
  v_low boolean;
  v_ok boolean := false;
  v_xp integer := 0;
  v_streak integer := 1;
  v_prev date;
  v_row record;
  v_finished boolean := false;
  v_occurred timestamptz := now();
  v_completed_count integer;
begin
  if v_user is null then raise exception 'not_authenticated' using errcode = '42501'; end if;
  if p_status not in ('completed', 'adapted', 'recovery') then raise exception 'invalid_status' using errcode = '22023'; end if;
  select * into v_uc from public.user_challenges where id = p_user_challenge_id and user_id = v_user for update;
  if not found then raise exception 'not_found' using errcode = 'P0002'; end if;

  -- Aynı yerel güne ait kayıt varsa: idempotent dönüş.
  if exists (select 1 from public.user_challenge_days where user_challenge_id = v_uc.id and local_date = p_local_date) then
    return jsonb_build_object('alreadyDone', true, 'xp', 0, 'finished', v_uc.status = 'completed');
  end if;
  if v_uc.status <> 'active' then raise exception 'not_active' using errcode = '22023'; end if;

  v_tz := coalesce((select timezone from public.gamification_preferences where user_id = v_user), 'Europe/Istanbul');
  v_today := (now() at time zone v_tz)::date;
  -- Saat dilimi / gece yarısı payı: yalnızca dün, bugün ya da yarın (seyahat) kabul edilir.
  if p_local_date is null or abs(p_local_date - v_today) > 1 or p_local_date < v_uc.started_on then raise exception 'invalid_day' using errcode = '22023'; end if;
  select max(local_date) into v_last from public.user_challenge_days where user_challenge_id = v_uc.id;
  if v_last is not null and p_local_date <= v_last then raise exception 'invalid_day' using errcode = '22023'; end if;

  select count(*) into v_index from public.user_challenge_days where user_challenge_id = v_uc.id;
  if v_index >= v_uc.total_days then raise exception 'not_active' using errcode = '22023'; end if;
  v_task := v_uc.plan->'days'->v_index;
  v_kind := v_task->>'kind';

  v_low := exists (
    select 1 from public.daily_checkins c where c.user_id = v_user and c.day = p_local_date
      and (c.energy <= 4 or c.sleep_quality <= 4 or c.soreness >= 7 or c.pain >= 6)
  );
  if p_status = 'adapted' and not v_low then raise exception 'adaptation_not_allowed' using errcode = '22023'; end if;

  if p_status = 'recovery' then
    if (select count(*) from public.user_challenge_days where user_challenge_id = v_uc.id and status = 'recovery') >= v_uc.recovery_allowance then
      raise exception 'no_recovery_left' using errcode = '22023';
    end if;
    v_ok := true;
  elsif v_kind in ('session', 'workout') then
    v_minutes := coalesce((v_task->>'minutes')::integer, case when v_kind = 'workout' then 20 else 10 end);
    if p_status = 'adapted' then v_minutes := greatest(8, least(v_minutes, coalesce(p_minutes, 8))); end if;
    v_required_seconds := floor(least(v_minutes, 30) * 60 * 0.6);
    v_ok := coalesce((
      select sum(duration_seconds) from public.workout_sessions
      where user_id = v_user and (completed_at at time zone v_tz)::date = p_local_date
    ), 0) >= v_required_seconds;
  elsif v_kind = 'steps' then
    v_target := (v_task->>'target')::numeric;
    if p_status = 'adapted' then v_target := greatest(3000, round(v_target * 0.6 / 500) * 500); end if;
    v_ok := coalesce((select steps from public.daily_steps where user_id = v_user and local_date = p_local_date), 0) >= v_target;
  elsif v_kind = 'water' then
    v_ok := coalesce((select milliliters from public.water_logs where user_id = v_user and local_date = p_local_date), 0) >= (v_task->>'target')::numeric;
  elsif v_kind = 'meals' then
    v_ok := (select count(*) from public.food_entries where user_id = v_user and (consumed_at at time zone v_tz)::date = p_local_date) >= (v_task->>'target')::numeric;
  elsif v_kind = 'checkin' then
    v_ok := exists (select 1 from public.daily_checkins where user_id = v_user and day = p_local_date);
  end if;
  if not v_ok then raise exception 'task_not_verified' using errcode = '22023'; end if;

  insert into public.user_challenge_days(user_challenge_id, user_id, day_index, local_date, status, minutes, session_id)
  values (v_uc.id, v_user, v_index, p_local_date, p_status, p_minutes, p_session_id);

  if p_status = 'recovery' then
    v_xp := v_xp + public.hedefit_award_rule(v_user, 'CHALLENGE_RECOVERY_DAY', v_uc.id::text || ':' || v_index, v_occurred);
  else
    v_xp := v_xp + public.hedefit_award_rule(v_user, 'CHALLENGE_DAY_COMPLETED', v_uc.id::text || ':' || v_index, v_occurred);
  end if;

  -- Seri: bu güne kadar ardışık takvim günleri (toparlanma günü seriyi bozmaz).
  v_prev := p_local_date;
  for v_row in select local_date from public.user_challenge_days where user_challenge_id = v_uc.id and local_date < p_local_date order by local_date desc loop
    exit when v_row.local_date <> v_prev - 1;
    v_streak := v_streak + 1;
    v_prev := v_row.local_date;
  end loop;
  if v_streak >= 3 then
    v_xp := v_xp + public.hedefit_award_rule(v_user, 'CHALLENGE_STREAK_3', v_uc.id::text || ':s3', v_occurred);
    insert into public.user_achievements(user_id, achievement_id) values (v_user, 'streak_3') on conflict do nothing;
  end if;
  if v_streak >= 7 and v_streak % 7 = 0 then
    v_xp := v_xp + public.hedefit_award_rule(v_user, 'CHALLENGE_STREAK_7', v_uc.id::text || ':s7:' || v_streak, v_occurred);
    insert into public.user_achievements(user_id, achievement_id) values (v_user, 'streak_7') on conflict do nothing;
  end if;
  if v_streak >= 30 then
    insert into public.user_achievements(user_id, achievement_id) values (v_user, 'streak_30') on conflict do nothing;
  end if;

  if v_index + 1 >= v_uc.total_days then
    v_finished := true;
    update public.user_challenges set status = 'completed', completed_at = now(), updated_at = now() where id = v_uc.id;
    v_xp := v_xp + public.hedefit_award_rule(v_user, 'CHALLENGE_COMPLETED', v_uc.id::text, v_occurred);
    if v_uc.social_challenge_id is not null then
      v_xp := v_xp + public.hedefit_award_rule(v_user, 'FRIEND_CHALLENGE_BONUS', v_uc.social_challenge_id::text, v_occurred);
      insert into public.user_achievements(user_id, achievement_id) values (v_user, 'first_friend_challenge') on conflict do nothing;
    end if;
    insert into public.user_achievements(user_id, achievement_id) values (v_user, 'first_challenge') on conflict do nothing;
    if v_uc.category = 'pilates' then
      insert into public.user_achievements(user_id, achievement_id) values (v_user, 'first_pilates_challenge') on conflict do nothing;
    elsif v_uc.category = 'nutrition' then
      insert into public.user_achievements(user_id, achievement_id) values (v_user, 'first_nutrition_challenge') on conflict do nothing;
    end if;
    select count(*) into v_completed_count from public.user_challenges where user_id = v_user and status = 'completed';
    if v_completed_count >= 10 then
      insert into public.user_achievements(user_id, achievement_id) values (v_user, 'challenges_10') on conflict do nothing;
    end if;
  else
    update public.user_challenges set updated_at = now() where id = v_uc.id;
  end if;

  return jsonb_build_object('alreadyDone', false, 'dayIndex', v_index, 'xp', v_xp, 'streak', v_streak, 'finished', v_finished);
end $$;
revoke all on function public.hedefit_complete_challenge_day(uuid, date, text, integer, uuid) from public, anon;
grant execute on function public.hedefit_complete_challenge_day(uuid, date, text, integer, uuid) to authenticated;

-- Katalog kartlarındaki katılımcı sayısı: GERÇEK katılım sayısı (benzersiz kullanıcı). Kişisel veri dönmez.
create or replace function public.hedefit_challenge_participant_counts()
returns table (template_key text, participants bigint)
language sql stable security definer set search_path = public as $$
  select uc.template_key, count(distinct uc.user_id)
  from public.user_challenges uc
  where uc.template_key not like 'coach:%'
  group by uc.template_key;
$$;
revoke all on function public.hedefit_challenge_participant_counts() from public, anon;
grant execute on function public.hedefit_challenge_participant_counts() to authenticated;

-- 4) Arkadaş challenge'ları: mod (Birlikte Tamamla / Rekabet Et) ve katalog bağlantısı -------------------
alter table public.challenges add column if not exists mode text not null default 'compete';
alter table public.challenges add column if not exists template_key text;
alter table public.challenges drop constraint if exists challenges_mode_check;
alter table public.challenges add constraint challenges_mode_check check (mode in ('compete', 'together'));
alter table public.challenges drop constraint if exists challenges_metric_check;
alter table public.challenges add constraint challenges_metric_check check (metric in ('xp', 'workouts', 'distance_km', 'steps', 'challenge_days'));

-- Davet 72 saat içinde yanıtlanmazsa süresi dolar.
create or replace function public.hedefit_create_friend_challenge(p_title text, p_metric text, p_target numeric, p_days integer, p_friend_ids uuid[], p_mode text, p_template_key text)
returns uuid language plpgsql security definer set search_path = public as $$
declare
  v_id uuid;
begin
  if p_mode not in ('compete', 'together') then raise exception 'invalid_mode' using errcode = '22023'; end if;
  if p_metric not in ('xp', 'workouts', 'distance_km', 'steps', 'challenge_days') then raise exception 'invalid_metric' using errcode = '22023'; end if;
  if p_template_key is not null and p_template_key !~ '^[a-z0-9_]{2,40}$' then raise exception 'invalid_template' using errcode = '22023'; end if;
  if coalesce(array_length(p_friend_ids, 1), 0) = 0 then raise exception 'no_friends' using errcode = '22023'; end if;
  if p_days is null or p_days < 1 or p_days > 30 then raise exception 'invalid_duration' using errcode = '22023'; end if;
  insert into public.challenges (creator_id, title, metric, target_value, starts_at, ends_at, mode, template_key)
  values (auth.uid(), left(trim(coalesce(p_title, '')), 60), p_metric, p_target, now(), now() + make_interval(days => p_days), p_mode, p_template_key)
  returning id into v_id;
  insert into public.challenge_participants (challenge_id, user_id, status, responded_at) values (v_id, auth.uid(), 'joined', now());
  insert into public.challenge_participants (challenge_id, user_id)
  select v_id, friend from unnest(p_friend_ids) as friend
  where friend <> auth.uid() and exists (
    select 1 from public.friendships f where f.status = 'accepted'
      and least(f.requester_id, f.addressee_id) = least(auth.uid(), friend)
      and greatest(f.requester_id, f.addressee_id) = greatest(auth.uid(), friend)
  )
  on conflict (challenge_id, user_id) do nothing;
  if (select count(*) from public.challenge_participants where challenge_id = v_id) < 2 then
    raise exception 'no_friends' using errcode = '22023';
  end if;
  return v_id;
end $$;
revoke all on function public.hedefit_create_friend_challenge(text, text, numeric, integer, uuid[], text, text) from public, anon;
grant execute on function public.hedefit_create_friend_challenge(text, text, numeric, integer, uuid[], text, text) to authenticated;

drop function if exists public.hedefit_list_challenges();
create or replace function public.hedefit_list_challenges()
returns table (
  id uuid, title text, metric text, target_value numeric, starts_at timestamptz, ends_at timestamptz,
  creator_id uuid, is_creator boolean, my_status text, participant_count bigint, mode text, template_key text, created_at timestamptz
)
language sql stable security definer set search_path = public as $$
  select
    c.id, c.title, c.metric, c.target_value, c.starts_at, c.ends_at,
    c.creator_id, (c.creator_id = auth.uid()) as is_creator,
    case when cp.status = 'invited' and (c.created_at < now() - interval '72 hours' or c.ends_at < now()) then 'expired' else cp.status end as my_status,
    (select count(*) from public.challenge_participants x where x.challenge_id = c.id and x.status = 'joined') as participant_count,
    c.mode, c.template_key, c.created_at
  from public.challenges c
  join public.challenge_participants cp on cp.challenge_id = c.id and cp.user_id = auth.uid()
  order by c.created_at desc;
$$;
grant execute on function public.hedefit_list_challenges() to authenticated;

-- Süresi dolmuş daveti kabul etmeyi engelle.
drop policy if exists "Davete yanıt verebilir" on public.challenge_participants;
create policy "Davete yanıt verebilir"
  on public.challenge_participants for update
  using (
    user_id = auth.uid() and status = 'invited'
    and exists (select 1 from public.challenges c where c.id = challenge_id and c.created_at >= now() - interval '72 hours' and c.ends_at > now())
  )
  with check (status in ('joined', 'declined'));

create or replace function public.hedefit_challenge_progress(p_challenge_id uuid)
returns table (user_id uuid, username text, display_name text, avatar_path text, progress_value numeric)
language plpgsql stable security definer set search_path = public as $$
declare
  v_metric text;
  v_starts timestamptz;
  v_ends timestamptz;
begin
  select metric, starts_at, ends_at into v_metric, v_starts, v_ends from public.challenges where id = p_challenge_id;
  if v_metric is null then raise exception 'not_found' using errcode = 'P0002'; end if;
  if not exists (select 1 from public.challenge_participants cp where cp.challenge_id = p_challenge_id and cp.user_id = auth.uid() and cp.status = 'joined') then
    raise exception 'not_a_participant' using errcode = '42501';
  end if;

  return query
  with joined as (
    select cp.user_id from public.challenge_participants cp where cp.challenge_id = p_challenge_id and cp.status = 'joined'
  )
  select
    j.user_id, p.username, p.display_name, p.avatar_path,
    case v_metric
      when 'xp' then coalesce((select sum(e.amount) from public.xp_events e where e.user_id = j.user_id and e.occurred_at between v_starts and v_ends), 0)
      when 'workouts' then coalesce((select count(*) from public.xp_events e where e.user_id = j.user_id and e.source = 'WORKOUT_COMPLETED' and e.occurred_at between v_starts and v_ends), 0)
      -- Adım: yalnızca toplam sayı (günlük detay ya da başka sağlık verisi paylaşılmaz).
      when 'steps' then coalesce((select sum(s.steps) from public.daily_steps s where s.user_id = j.user_id and s.local_date between v_starts::date and v_ends::date), 0)
      when 'challenge_days' then coalesce((select count(*) from public.user_challenge_days d join public.user_challenges uc on uc.id = d.user_challenge_id where uc.user_id = j.user_id and uc.social_challenge_id = p_challenge_id), 0)
      else coalesce((select sum(r.distance_meters) / 1000.0 from public.route_activities r where r.user_id = j.user_id and r.started_at between v_starts and v_ends), 0)
    end as progress_value
  from joined j
  join public.profiles p on p.id = j.user_id
  order by progress_value desc, j.user_id;
end $$;
grant execute on function public.hedefit_challenge_progress(uuid) to authenticated;

-- 5) Arkadaş profili (gizlilik izinleri dahilinde) -------------------------------------------------------
-- Yalnızca kabul edilmiş arkadaşlar görebilir. SAĞLIK VERİSİ DÖNMEZ: kilo, kalori, döngü, uyku, beslenme,
-- check-in alanları sorguya hiç dahil edilmez. Kullanıcı ilerlemesini gizlerse yalnızca ad döner.
alter table public.profiles add column if not exists share_progress_with_friends boolean not null default true;

create or replace function public.hedefit_friend_profile(p_friend_id uuid)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
  v_profile record;
  v_share boolean;
begin
  if auth.uid() is null then raise exception 'not_authenticated' using errcode = '42501'; end if;
  if not exists (
    select 1 from public.friendships f where f.status = 'accepted'
      and least(f.requester_id, f.addressee_id) = least(auth.uid(), p_friend_id)
      and greatest(f.requester_id, f.addressee_id) = greatest(auth.uid(), p_friend_id)
  ) then raise exception 'not_friends' using errcode = '42501'; end if;
  select id, username, display_name, share_progress_with_friends into v_profile from public.profiles where id = p_friend_id;
  if not found then raise exception 'not_found' using errcode = 'P0002'; end if;
  v_share := coalesce(v_profile.share_progress_with_friends, true);
  if not v_share then
    return jsonb_build_object('id', v_profile.id, 'username', v_profile.username, 'displayName', v_profile.display_name, 'shared', false);
  end if;
  return jsonb_build_object(
    'id', v_profile.id,
    'username', v_profile.username,
    'displayName', v_profile.display_name,
    'shared', true,
    'totalXp', coalesce((select sum(amount) from public.xp_events where user_id = p_friend_id), 0),
    'completedChallenges', (select count(*) from public.user_challenges where user_id = p_friend_id and status = 'completed'),
    'activeChallenges', coalesce((
      select jsonb_agg(jsonb_build_object('title', uc.plan->'title', 'category', uc.category, 'totalDays', uc.total_days,
        'doneDays', (select count(*) from public.user_challenge_days d where d.user_challenge_id = uc.id)) order by uc.started_at desc)
      from public.user_challenges uc where uc.user_id = p_friend_id and uc.status = 'active'
    ), '[]'::jsonb),
    'bestStreak', coalesce((
      select max(run) from (
        select count(*) as run from (
          select d.user_challenge_id, d.local_date - (row_number() over (partition by d.user_challenge_id order by d.local_date))::integer as grp
          from public.user_challenge_days d where d.user_id = p_friend_id
        ) g group by g.user_challenge_id, g.grp
      ) runs
    ), 0),
    'achievements', coalesce((
      select jsonb_agg(achievement_id order by unlocked_at desc) from (
        select achievement_id, unlocked_at from public.user_achievements where user_id = p_friend_id order by unlocked_at desc limit 12
      ) a
    ), '[]'::jsonb)
  );
end $$;
revoke all on function public.hedefit_friend_profile(uuid) from public, anon;
grant execute on function public.hedefit_friend_profile(uuid) to authenticated;

create or replace function public.hedefit_set_share_progress(p_value boolean)
returns boolean language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is null then raise exception 'not_authenticated' using errcode = '42501'; end if;
  update public.profiles set share_progress_with_friends = coalesce(p_value, true) where id = auth.uid();
  return coalesce(p_value, true);
end $$;
revoke all on function public.hedefit_set_share_progress(boolean) from public, anon;
grant execute on function public.hedefit_set_share_progress(boolean) to authenticated;
