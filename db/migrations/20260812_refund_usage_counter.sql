-- Sayaç önceden artırılıyor (bkz. increment_usage_counter), AI çağrısı
-- ise SONRA yapılıyor (bkz. app/api/chat/route.ts, app/api/nutrition/parse-text/route.ts).
-- AI çağrısı zaman aşımına uğrar, hata verir veya doğrulanamayan bir sonuç
-- dönerse route güvenli yerel yanıta düşüyor ama kullanıcının günlük hakkı
-- zaten harcanmış oluyordu. Bu fonksiyon o hakkı, aynı gün için, negatife
-- düşmeden geri iade eder.
create or replace function public.refund_usage_counter(p_feature text, p_amount integer default 1)
returns integer
language plpgsql security definer set search_path = public as $$
declare
  v_user uuid := auth.uid();
  v_new_count integer;
begin
  if v_user is null then
    raise exception 'not authenticated';
  end if;
  if p_feature not in ('chat', 'photo', 'text_nutrition', 'weekly_review', 'nutrition_advice', 'plan') then
    raise exception 'invalid feature';
  end if;

  update public.usage_counters uc
  set count = greatest(uc.count - greatest(p_amount, 0), 0), updated_at = now()
  where uc.user_id = v_user
    and uc.feature = p_feature
    and uc.usage_date = current_date
  returning uc.count into v_new_count;

  return coalesce(v_new_count, 0);
end;
$$;

grant execute on function public.refund_usage_counter(text, integer) to authenticated;
