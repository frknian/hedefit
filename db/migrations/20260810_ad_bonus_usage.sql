-- Ödüllü reklamla ekstra kullanım hakkı: ücretsiz kullanıcı günlük limitine
-- ulaştığında reklam izleyerek o özellik için ekstra hak kazanabilir (bkz.
-- lib/usage-limits.ts grantAdBonus, app/api/ads/reward/route.ts). Bonus,
-- "count" ile aynı satırda ayrı bir sütunda tutulur ve efektif limit
-- (p_limit + bonus_count) olarak increment_usage_counter içinde uygulanır.
-- Bonus artışı da kendi SECURITY DEFINER fonksiyonu (grant_usage_bonus)
-- üzerinden, günlük tavanla (p_max_bonus) sınırlı biçimde yapılır; istemci
-- tabloya doğrudan yazamaz.

alter table public.usage_counters add column if not exists bonus_count integer not null default 0;

-- increment_usage_counter'ın dönüş sütunları değişiyor (effective_limit
-- eklendi); Postgres, OUT parametreleriyle tanımlı bir fonksiyonun dönüş
-- tipini create or replace ile değiştirmeye izin vermiyor, önce düşürmek
-- gerekiyor.
drop function if exists public.increment_usage_counter(text, integer);

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
  if p_feature not in ('chat', 'photo', 'text_nutrition', 'weekly_review', 'nutrition_advice') then
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

create or replace function public.grant_usage_bonus(p_feature text, p_bonus integer, p_max_bonus integer)
returns integer
language plpgsql security definer set search_path = public as $$
declare
  v_user uuid := auth.uid();
  v_new_bonus integer;
begin
  if v_user is null then
    raise exception 'not authenticated';
  end if;
  if p_feature not in ('chat', 'photo', 'text_nutrition', 'weekly_review', 'nutrition_advice') then
    raise exception 'invalid feature';
  end if;

  insert into public.usage_counters (user_id, feature, usage_date, count)
  values (v_user, p_feature, current_date, 0)
  on conflict (user_id, feature, usage_date) do nothing;

  update public.usage_counters uc
  set bonus_count = least(uc.bonus_count + greatest(p_bonus, 0), p_max_bonus),
      updated_at = now()
  where uc.user_id = v_user
    and uc.feature = p_feature
    and uc.usage_date = current_date
  returning uc.bonus_count into v_new_bonus;

  return v_new_bonus;
end;
$$;

grant execute on function public.grant_usage_bonus(text, integer, integer) to authenticated;
