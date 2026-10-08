-- Kullanıcı başına AI maliyet telemetrisi + aylık kullanım toplamı.
-- İstek/yanıt metni YAZILMAZ; yalnız özellik, plan, model, token ve tahmini maliyet.

create table if not exists public.ai_usage_events (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  feature text not null check (length(feature) <= 40),
  plan_tier text check (plan_tier in ('free', 'plus', 'pro')),
  category text not null check (length(category) <= 40),
  model text check (length(model) <= 80),
  outcome text not null check (outcome in ('success', 'error')),
  fallback_used boolean not null default false,
  input_tokens integer check (input_tokens >= 0),
  output_tokens integer check (output_tokens >= 0),
  -- Mikro-dolar (1 USD = 1.000.000); model fiyatı bilinmiyorsa NULL.
  est_cost_micro_usd bigint check (est_cost_micro_usd >= 0),
  latency_ms integer check (latency_ms >= 0),
  created_at timestamptz not null default now()
);

create index if not exists ai_usage_events_created_idx on public.ai_usage_events (created_at desc);
create index if not exists ai_usage_events_user_created_idx on public.ai_usage_events (user_id, created_at desc);

-- Yalnız service_role yazar/okur; kullanıcılar kendi kayıtlarını bile görmez.
alter table public.ai_usage_events enable row level security;
revoke all on public.ai_usage_events from anon, authenticated;

-- Panolar için özetler (SQL Editor / service_role).
create or replace view public.ai_usage_daily_by_feature as
select date_trunc('day', created_at)::date as day,
       feature,
       plan_tier,
       model,
       count(*) filter (where outcome = 'success') as calls,
       count(*) filter (where outcome = 'error') as errors,
       sum(input_tokens) as input_tokens,
       sum(output_tokens) as output_tokens,
       round(sum(est_cost_micro_usd) / 1000000.0, 4) as est_cost_usd
from public.ai_usage_events
group by 1, 2, 3, 4;

create or replace view public.ai_usage_monthly_by_user as
select date_trunc('month', created_at)::date as month,
       user_id,
       max(plan_tier) as plan_tier,
       count(*) filter (where outcome = 'success') as calls,
       round(sum(est_cost_micro_usd) / 1000000.0, 4) as est_cost_usd
from public.ai_usage_events
group by 1, 2;

revoke all on public.ai_usage_daily_by_feature from anon, authenticated;
revoke all on public.ai_usage_monthly_by_user from anon, authenticated;

-- Saklama: ham kullanıcı başına kayıtları N günden sonra siler (cron/SQL Editor).
create or replace function public.purge_ai_usage_events(p_days integer default 180)
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare v_deleted integer;
begin
  if p_days < 30 then raise exception 'p_days en az 30 olmalı'; end if;
  delete from public.ai_usage_events where created_at < now() - make_interval(days => p_days);
  get diagnostics v_deleted = row_count;
  return v_deleted;
end;
$$;
revoke all on function public.purge_ai_usage_events(integer) from public, anon, authenticated;
grant execute on function public.purge_ai_usage_events(integer) to service_role;

-- Aylık tavan için: çağıran kullanıcının bu ayki toplam kullanımı (bugün dahil).
create or replace function public.usage_month_total(p_feature text)
returns integer
language sql
stable
security definer
set search_path = public
as $$
  select coalesce(sum(c.count), 0)::integer
  from public.usage_counters c
  where c.user_id = auth.uid()
    and c.feature = p_feature
    and c.usage_date >= date_trunc('month', current_date)::date;
$$;
revoke all on function public.usage_month_total(text) from public, anon;
grant execute on function public.usage_month_total(text) to authenticated;
