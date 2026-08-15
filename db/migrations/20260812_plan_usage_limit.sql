-- generate-plan artık günlük kotaya tabi (bkz. app/api/generate-plan/route.ts,
-- lib/usage-limits.ts). Öncesinde yalnız 5 dakikada 5 istekle sınırlıydı;
-- kimliği doğrulanmış TEK bir kullanıcı bu pencerede istediği kadar büyük
-- prompt (istemcinin gönderdiği exerciseCatalog + ham profil JSON'u, ikisi de
-- sunucuda boyut sınırı olmadan işleniyordu) göndererek AI maliyetini
-- şişirebiliyordu. "plan" reklam ödülü bonusuna dahil DEĞİLDİR — bkz.
-- app/api/ads/reward/route.ts BONUS_ELIGIBLE_FEATURES; bu yüzden
-- grant_usage_bonus'un özellik listesi burada değişmiyor.
alter table public.usage_counters
  drop constraint if exists usage_counters_feature_check;

alter table public.usage_counters
  add constraint usage_counters_feature_check
  check (feature in ('chat', 'photo', 'text_nutrition', 'weekly_review', 'nutrition_advice', 'plan'));

create or replace function public.increment_usage_counter(p_feature text, p_limit integer)
returns table (allowed boolean, current_count integer, effective_limit integer)
language plpgsql security definer set search_path = public as $$
declare
  v_user uuid := auth.uid();
  v_count integer;
  v_bonus integer;
  v_effective_limit integer;
begin
  if v_user is null then
    raise exception 'not authenticated';
  end if;
  if p_feature not in ('chat', 'photo', 'text_nutrition', 'weekly_review', 'nutrition_advice', 'plan') then
    raise exception 'invalid feature';
  end if;

  insert into public.usage_counters (user_id, feature, usage_date, count)
  values (v_user, p_feature, current_date, 0)
  on conflict (user_id, feature, usage_date) do nothing;

  select uc.count, uc.bonus_count into v_count, v_bonus
  from public.usage_counters uc
  where uc.user_id = v_user
    and uc.feature = p_feature
    and uc.usage_date = current_date
  for update;

  v_effective_limit := p_limit + coalesce(v_bonus, 0);

  if v_count >= v_effective_limit then
    return query select false, v_count, v_effective_limit;
  end if;

  update public.usage_counters uc
  set count = uc.count + 1, updated_at = now()
  where uc.user_id = v_user
    and uc.feature = p_feature
    and uc.usage_date = current_date;

  return query select true, v_count + 1, v_effective_limit;
end;
$$;

grant execute on function public.increment_usage_counter(text, integer) to authenticated;
