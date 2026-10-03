-- Google Play Billing: sunucu tarafında doğrulanmış abonelikler.
-- İstemci plan yazamaz; plan_tier yalnız apply_play_subscription() ile (service_role)
-- ve Google'a sorulduktan sonra değişir. Bkz. app/api/billing/verify/route.ts.

alter table public.profiles add column if not exists plan_source text not null default 'manual';
alter table public.profiles drop constraint if exists profiles_plan_source_check;
alter table public.profiles add constraint profiles_plan_source_check check (plan_source in ('manual', 'play'));

-- plan_source da ayrıcalıklı sütun: kullanıcı kendi satırında değiştiremez.
create or replace function public.profiles_guard_privileged_columns()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if coalesce(auth.role(), '') <> 'service_role' then
    if tg_op = 'INSERT' then
      new.is_premium := false;
      new.plan_tier := 'free';
      new.plan_source := 'manual';
      new.account_status := 'active';
    else
      if new.is_premium is distinct from old.is_premium then
        new.is_premium := old.is_premium;
      end if;
      if new.plan_tier is distinct from old.plan_tier then
        new.plan_tier := old.plan_tier;
      end if;
      if new.plan_source is distinct from old.plan_source then
        new.plan_source := old.plan_source;
      end if;
      if new.account_status is distinct from old.account_status
        and new.account_status = 'deletion_pending' then
        new.account_status := old.account_status;
      end if;
    end if;
  end if;
  return new;
end;
$$;
revoke all on function public.profiles_guard_privileged_columns() from public, anon, authenticated;

create table if not exists public.subscriptions (
  purchase_token text primary key,
  user_id        uuid not null references auth.users(id) on delete cascade,
  product_id     text not null,
  base_plan_id   text,
  plan_tier      text not null check (plan_tier in ('plus', 'pro')),
  -- active | grace | canceled (süre bitene kadar erişim sürer) | on_hold | paused | expired | pending | replaced
  state          text not null check (state in ('active', 'grace', 'canceled', 'on_hold', 'paused', 'expired', 'pending', 'replaced')),
  expires_at     timestamptz,
  auto_renewing  boolean not null default false,
  linked_token   text,
  is_test        boolean not null default false,
  created_at     timestamptz not null default now(),
  updated_at     timestamptz not null default now()
);
create index if not exists subscriptions_user_id_idx on public.subscriptions (user_id);

alter table public.subscriptions enable row level security;
drop policy if exists "Users read own subscriptions" on public.subscriptions;
create policy "Users read own subscriptions" on public.subscriptions
  for select to authenticated using (auth.uid() = user_id);
-- Yazma politikası yok: yalnız service_role (RLS'i atlar) yazabilir.
revoke all on public.subscriptions from anon, authenticated;
grant select on public.subscriptions to authenticated;

-- Kullanıcının planını aktif aboneliklerden yeniden hesaplar.
--  * Erişim: active, grace veya canceled (iptal edilmiş ama süresi bitmemiş) ve expires_at > now().
--  * Birden fazla aktif abonelikte en yükseği geçerli.
--  * Elle verilmiş (manual) daha yüksek plan, daha düşük bir abonelikle ezilmez.
--  * Play kaynaklı plan, aktif abonelik kalmayınca free'ye düşer; manual plana dokunulmaz.
create or replace function public.recompute_plan_tier(p_user uuid)
returns text
language plpgsql
security definer
set search_path = public
as $$
declare
  v_rank integer;
  v_tier text;
  v_current text;
  v_source text;
begin
  select coalesce(max(case s.plan_tier when 'pro' then 2 else 1 end), 0) into v_rank
  from public.subscriptions s
  where s.user_id = p_user and s.state in ('active', 'grace', 'canceled') and s.expires_at > now();

  select p.plan_tier, p.plan_source into v_current, v_source from public.profiles p where p.id = p_user for update;
  if not found then
    raise exception 'profile_missing' using errcode = 'P0002';
  end if;

  -- Tetikleyici, service_role dışındakilerin plan sütunlarını geri alır; bu işlem için rolü tanıt.
  perform set_config('request.jwt.claim.role', 'service_role', true);
  perform set_config('request.jwt.claims', '{"role":"service_role"}', true);

  if v_rank > 0 then
    v_tier := case v_rank when 2 then 'pro' else 'plus' end;
    if v_source = 'manual' and v_current = 'pro' and v_tier = 'plus' then
      return v_current;
    end if;
    update public.profiles set plan_tier = v_tier, is_premium = (v_tier = 'pro'), plan_source = 'play' where id = p_user;
    return v_tier;
  end if;

  if v_source = 'play' then
    update public.profiles set plan_tier = 'free', is_premium = false, plan_source = 'manual' where id = p_user;
    return 'free';
  end if;
  return v_current;
end;
$$;

-- Doğrulanmış bir Play aboneliğini kaydeder ve planı yeniden hesaplar.
-- Aynı purchase_token başka bir kullanıcıya bağlıysa 'token_owned_by_other_user' ile reddeder.
create or replace function public.apply_play_subscription(
  p_user uuid,
  p_token text,
  p_product text,
  p_base_plan text,
  p_tier text,
  p_state text,
  p_expires timestamptz,
  p_auto_renewing boolean,
  p_linked_token text,
  p_is_test boolean
)
returns text
language plpgsql
security definer
set search_path = public
as $$
begin
  if p_tier not in ('plus', 'pro') then
    raise exception 'invalid_tier' using errcode = '22023';
  end if;

  insert into public.subscriptions as s
    (purchase_token, user_id, product_id, base_plan_id, plan_tier, state, expires_at, auto_renewing, linked_token, is_test)
  values
    (p_token, p_user, p_product, p_base_plan, p_tier, p_state, p_expires, coalesce(p_auto_renewing, false), p_linked_token, coalesce(p_is_test, false))
  on conflict (purchase_token) do update
    set product_id = excluded.product_id,
        base_plan_id = excluded.base_plan_id,
        plan_tier = excluded.plan_tier,
        state = excluded.state,
        expires_at = excluded.expires_at,
        auto_renewing = excluded.auto_renewing,
        linked_token = coalesce(excluded.linked_token, s.linked_token),
        is_test = excluded.is_test,
        updated_at = now()
    where s.user_id = excluded.user_id;

  -- Koşul sağlanmadıysa satır başka kullanıcıya aittir: hiçbir şey yazılmadı.
  if not found then
    raise exception 'token_owned_by_other_user' using errcode = 'P0001';
  end if;

  -- Yükseltme/yeniden abonelikte eski token'ı devre dışı bırak.
  if p_linked_token is not null then
    update public.subscriptions
      set state = 'replaced', updated_at = now()
      where purchase_token = p_linked_token and user_id = p_user and state <> 'replaced';
  end if;

  return public.recompute_plan_tier(p_user);
end;
$$;

revoke all on function public.recompute_plan_tier(uuid) from public, anon, authenticated;
revoke all on function public.apply_play_subscription(uuid, text, text, text, text, text, timestamptz, boolean, text, boolean) from public, anon, authenticated;
grant execute on function public.recompute_plan_tier(uuid) to service_role;
grant execute on function public.apply_play_subscription(uuid, text, text, text, text, text, timestamptz, boolean, text, boolean) to service_role;
