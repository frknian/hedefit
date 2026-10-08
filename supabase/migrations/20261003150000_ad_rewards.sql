-- Ödüllü reklam sunucu doğrulaması (AdMob SSV). İstemcinin "reklam izlendi" beyanı kanıt değildir;
-- hak yalnız Google'ın imzalı callback'inden (app/api/ads/ssv) ve her transaction_id için BİR kez verilir.

create table if not exists public.ad_reward_events (
  transaction_id text primary key check (length(transaction_id) between 1 and 128),
  user_id uuid not null references auth.users(id) on delete cascade,
  feature text not null check (feature in ('chat', 'text_nutrition')),
  -- Hak verildi mi? false: kullanıcı ücretli plandaydı ya da hesap etkin değildi (olay yine de tekrarı önlemek için kaydedilir).
  granted boolean not null,
  created_at timestamptz not null default now()
);
create index if not exists ad_reward_events_user_created_idx on public.ad_reward_events (user_id, created_at desc);
alter table public.ad_reward_events enable row level security;
revoke all on public.ad_reward_events from anon, authenticated;

-- Callback'ten çağrılır (service_role). Dönüş: yeni bonus sayısı; yinelenen işlemde 'duplicate',
-- uygun olmayan hesapta 'ineligible' döner.
create or replace function public.grant_ad_reward(p_transaction_id text, p_user_id uuid, p_feature text)
returns text
language plpgsql
security definer
set search_path = public
as $$
declare
  v_inserted integer;
  v_tier text;
  v_status text;
  v_bonus integer;
begin
  if auth.role() <> 'service_role' then raise exception 'service role required'; end if;
  if p_feature not in ('chat', 'text_nutrition') then raise exception 'invalid feature'; end if;
  if not exists (select 1 from auth.users where id = p_user_id) then return 'unknown_user'; end if;

  select plan_tier, account_status into v_tier, v_status from public.profiles where id = p_user_id;
  -- Ücretli planın zaten bu limitleri yüksek; hesap dondurulmuş/silinecekse hak verilmez.
  if coalesce(v_tier, 'free') <> 'free' or coalesce(v_status, 'active') <> 'active' then
    insert into public.ad_reward_events (transaction_id, user_id, feature, granted)
      values (p_transaction_id, p_user_id, p_feature, false)
      on conflict (transaction_id) do nothing;
    return 'ineligible';
  end if;

  insert into public.ad_reward_events (transaction_id, user_id, feature, granted)
    values (p_transaction_id, p_user_id, p_feature, true)
    on conflict (transaction_id) do nothing;
  get diagnostics v_inserted = row_count;
  if v_inserted = 0 then return 'duplicate'; end if;

  -- Günlük tavan (3) grant_usage_bonus_for_user içinde uygulanır.
  v_bonus := public.grant_usage_bonus_for_user(p_user_id, p_feature);
  return 'granted:' || v_bonus::text;
end;
$$;
revoke all on function public.grant_ad_reward(text, uuid, text) from public, anon, authenticated;
grant execute on function public.grant_ad_reward(text, uuid, text) to service_role;

-- Bugünkü bonus sayısı (istemcinin ödülü beklerken yoklaması için; yalnız service_role).
create or replace function public.ad_bonus_today(p_user_id uuid, p_feature text)
returns integer
language sql
stable
security definer
set search_path = public
as $$
  select coalesce((select bonus_count from public.usage_counters
    where user_id = p_user_id and feature = p_feature and usage_date = current_date), 0);
$$;
revoke all on function public.ad_bonus_today(uuid, text) from public, anon, authenticated;
grant execute on function public.ad_bonus_today(uuid, text) to service_role;

create or replace function public.purge_ad_reward_events(p_days integer default 90)
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare v_deleted integer;
begin
  if p_days < 7 then raise exception 'p_days en az 7 olmalı'; end if;
  delete from public.ad_reward_events where created_at < now() - make_interval(days => p_days);
  get diagnostics v_deleted = row_count;
  return v_deleted;
end;
$$;
revoke all on function public.purge_ad_reward_events(integer) from public, anon, authenticated;
grant execute on function public.purge_ad_reward_events(integer) to service_role;
