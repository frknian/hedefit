-- Performans: her korumalı AI isteği önceden authenticateRequest() (getUser),
-- profiles.is_premium okuma ve increment_usage_counter olmak üzere 3 ardışık
-- ağ turu yapıyordu. Bu fonksiyon premium okuma ile sayaç artırmayı TEK bir
-- round trip'te birleştirir; getUser() ayrı kalır (o, jetonun kendisini
-- doğrulayan farklı bir uç noktadır). auth.uid() ile SECURITY DEFINER
-- kullanıldığı için istemci tarafında .eq("id", ...) ile ekstra bir savunma
-- katmanına da gerek kalmaz — fonksiyon yapısal olarak yalnız çağıranın
-- KENDİ satırını okur.
create or replace function public.check_and_consume_usage(p_feature text, p_free_limit integer, p_premium_limit integer)
returns table (allowed boolean, current_count integer, effective_limit integer, is_premium boolean)
language plpgsql security definer set search_path = public as $$
declare
  v_user uuid := auth.uid();
  v_is_premium boolean;
  v_limit integer;
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

  select coalesce(p.is_premium, false) into v_is_premium from public.profiles p where p.id = v_user;
  v_is_premium := coalesce(v_is_premium, false);
  v_limit := case when v_is_premium then p_premium_limit else p_free_limit end;

  insert into public.usage_counters (user_id, feature, usage_date, count)
  values (v_user, p_feature, current_date, 0)
  on conflict (user_id, feature, usage_date) do nothing;

  select uc.count, uc.bonus_count into v_count, v_bonus
  from public.usage_counters uc
  where uc.user_id = v_user
    and uc.feature = p_feature
    and uc.usage_date = current_date
  for update;

  v_effective_limit := v_limit + coalesce(v_bonus, 0);

  if v_count >= v_effective_limit then
    return query select false, v_count, v_effective_limit, v_is_premium;
  end if;

  update public.usage_counters uc
  set count = uc.count + 1, updated_at = now()
  where uc.user_id = v_user
    and uc.feature = p_feature
    and uc.usage_date = current_date;

  return query select true, v_count + 1, v_effective_limit, v_is_premium;
end;
$$;

grant execute on function public.check_and_consume_usage(text, integer, integer) to authenticated;
