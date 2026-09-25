-- Yönetici: bir hesabın paketini e-postayla değiştirir.
-- Kullanım (Supabase SQL Editor): select public.set_plan_tier('kullanici@ornek.com', 'plus');
-- profiles_guard_privileged_columns tetikleyicisi plan_tier/is_premium değişikliklerini yalnız
-- service_role için kabul eder; fonksiyon bu rolü yalnız kendi işlemi boyunca tanıtır.

create or replace function public.set_plan_tier(p_email text, p_tier text)
returns table (email text, plan_tier text, is_premium boolean)
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user uuid;
begin
  if p_tier not in ('free', 'plus', 'pro') then
    raise exception 'Geçersiz paket: % (free, plus veya pro olmalı)', p_tier;
  end if;

  select u.id into v_user from auth.users u where lower(u.email) = lower(trim(p_email));
  if v_user is null then
    raise exception 'Bu e-postayla kayıtlı hesap yok: %', p_email;
  end if;

  perform set_config('request.jwt.claim.role', 'service_role', true);
  perform set_config('request.jwt.claims', '{"role":"service_role"}', true);

  update public.profiles p
  set plan_tier = p_tier, is_premium = (p_tier = 'pro')
  where p.id = v_user;

  if not found then
    raise exception 'Hesabın profili henüz oluşmamış: %', p_email;
  end if;

  return query
    select p_email, p.plan_tier, p.is_premium from public.profiles p where p.id = v_user;
end;
$$;

-- Uygulama kullanıcıları (anon/authenticated) çağıramaz; yalnız SQL Editor / service_role.
revoke all on function public.set_plan_tier(text, text) from public, anon, authenticated;
