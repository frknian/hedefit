-- Google Play RTDN (Pub/Sub) için: tekrar önleme, iade, saklama. Bkz. app/api/billing/rtdn/route.ts.

-- İade edilen/iptal edilen satın alma durumu.
alter table public.subscriptions drop constraint if exists subscriptions_state_check;
alter table public.subscriptions add constraint subscriptions_state_check
  check (state in ('active', 'grace', 'canceled', 'on_hold', 'paused', 'expired', 'pending', 'replaced', 'refunded'));

-- Pub/Sub aynı mesajı tekrar teslim edebilir; message_id ile tek kez işlenir.
create table if not exists public.billing_events (
  message_id text primary key,
  purchase_token text,
  notification_type text,
  received_at timestamptz not null default now(),
  processed_at timestamptz
);
create index if not exists billing_events_received_idx on public.billing_events (received_at);
alter table public.billing_events enable row level security;
revoke all on public.billing_events from anon, authenticated;

-- true: bu mesajı işle (yeni ya da önceki deneme tamamlanmamış). false: zaten işlendi.
create or replace function public.begin_billing_event(p_message_id text, p_token text, p_type text)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare v_pending boolean;
begin
  insert into public.billing_events (message_id, purchase_token, notification_type)
  values (p_message_id, p_token, p_type)
  on conflict (message_id) do nothing;
  select processed_at is null into v_pending from public.billing_events where message_id = p_message_id;
  return coalesce(v_pending, true);
end;
$$;

create or replace function public.finish_billing_event(p_message_id text)
returns void
language sql
security definer
set search_path = public
as $$
  update public.billing_events set processed_at = now() where message_id = p_message_id and processed_at is null;
$$;

-- İade/iptal (voided purchase): erişimi hemen keser, planı yeniden hesaplar.
-- Bilinmeyen jeton için null döner.
create or replace function public.void_play_subscription(p_token text)
returns text
language plpgsql
security definer
set search_path = public
as $$
declare v_user uuid;
begin
  update public.subscriptions
    set state = 'refunded', expires_at = least(coalesce(expires_at, now()), now()), auto_renewing = false, updated_at = now()
    where purchase_token = p_token
    returning user_id into v_user;
  if v_user is null then
    return null;
  end if;
  return public.recompute_plan_tier(v_user);
end;
$$;

create or replace function public.purge_billing_events(p_days integer default 90)
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare v_deleted integer;
begin
  if p_days < 7 then raise exception 'p_days en az 7 olmalı'; end if;
  delete from public.billing_events where received_at < now() - make_interval(days => p_days);
  get diagnostics v_deleted = row_count;
  return v_deleted;
end;
$$;

revoke all on function public.begin_billing_event(text, text, text) from public, anon, authenticated;
revoke all on function public.finish_billing_event(text) from public, anon, authenticated;
revoke all on function public.void_play_subscription(text) from public, anon, authenticated;
revoke all on function public.purge_billing_events(integer) from public, anon, authenticated;
grant execute on function public.begin_billing_event(text, text, text) to service_role;
grant execute on function public.finish_billing_event(text) to service_role;
grant execute on function public.void_play_subscription(text) to service_role;
grant execute on function public.purge_billing_events(integer) to service_role;
