-- Adaptive Fitness: kişiselleştirme ayarları, İSTEĞE BAĞLI döngü takibi ve günlük check-in.
--
-- YALNIZCA EKLEME: mevcut tablolara, sütunlara ya da verilere dokunmaz. Mevcut kullanıcılar için hiçbir
-- satır oluşturulmaz; döngü takibi ve kişiselleştirme kullanıcı açıkça etkinleştirene kadar kapalıdır.
-- Tekrar çalıştırılabilir (idempotent).
--
-- GİZLİLİK: üç tabloda da RLS açık, politika auth.uid() = user_id. Üçü de auth.users'a `on delete cascade`
-- ile bağlıdır; hesap silinince veri gider. Döngü FAZI ve TAHMİNİ SAKLANMAZ: tarihten hesaplanır
-- (lib/cycle.ts), böylece kullanıcı veriyi düzenlediğinde eski türetilmiş değer ortada kalmaz.

create or replace function public.hedefit_touch_updated_at() returns trigger
language plpgsql as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

-- 1) Kişiselleştirme ayarları -------------------------------------------------------------------------
create table if not exists public.personalization_settings (
  user_id uuid primary key references auth.users(id) on delete cascade,
  -- Check-in ve toparlanmaya göre antrenmanı uyarlama.
  adaptive_enabled boolean not null default true,
  -- Döngü bilgisinin antrenman/beslenme kararlarına katılması (varsayılan KAPALI; açık rıza).
  cycle_personalization_enabled boolean not null default false,
  -- Sağlık/yaşam tarzı cevaplarının üçüncü taraf AI sağlayıcısına gönderilmesi (varsayılan KAPALI).
  ai_health_context_enabled boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

-- 2) Döngü profili (isteğe bağlı) ---------------------------------------------------------------------
create table if not exists public.cycle_profiles (
  user_id uuid primary key references auth.users(id) on delete cascade,
  tracking_enabled boolean not null default false,
  last_period_start date check (last_period_start is null or last_period_start >= date '2000-01-01'),
  cycle_length_days smallint check (cycle_length_days is null or cycle_length_days between 21 and 45),
  period_length_days smallint check (period_length_days is null or period_length_days between 1 and 10),
  regularity text not null default 'unknown' check (regularity in ('regular', 'somewhat_irregular', 'irregular', 'unknown')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

-- 3) Günlük check-in ----------------------------------------------------------------------------------
-- Bilerek kısa: enerji, uyku, ağrı/kas ağrısı ve süre. Döngü bilgisi BURADA tutulmaz; yalnızca
-- cycle_profiles'tan (kullanıcı etkinleştirdiyse) okunur.
create table if not exists public.daily_checkins (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  day date not null,
  energy smallint not null check (energy between 1 and 10),
  sleep_quality smallint not null check (sleep_quality between 1 and 10),
  sleep_hours numeric(3, 1) check (sleep_hours is null or (sleep_hours >= 0 and sleep_hours <= 24)),
  soreness smallint not null default 0 check (soreness between 0 and 10),
  pain smallint not null default 0 check (pain between 0 and 10),
  available_minutes smallint check (available_minutes is null or available_minutes between 5 and 240),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  -- Gün başına tek check-in; ikinci gönderim öncekini günceller.
  unique (user_id, day)
);

create index if not exists daily_checkins_user_day_idx on public.daily_checkins (user_id, day desc);

-- Row Level Security + updated_at ---------------------------------------------------------------------
alter table public.personalization_settings enable row level security;
drop policy if exists "Users manage own personalization settings" on public.personalization_settings;
create policy "Users manage own personalization settings" on public.personalization_settings
  for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

alter table public.cycle_profiles enable row level security;
drop policy if exists "Users manage own cycle profile" on public.cycle_profiles;
create policy "Users manage own cycle profile" on public.cycle_profiles
  for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

alter table public.daily_checkins enable row level security;
drop policy if exists "Users manage own daily checkins" on public.daily_checkins;
create policy "Users manage own daily checkins" on public.daily_checkins
  for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

drop trigger if exists personalization_settings_touch on public.personalization_settings;
create trigger personalization_settings_touch before update on public.personalization_settings
  for each row execute function public.hedefit_touch_updated_at();
drop trigger if exists cycle_profiles_touch on public.cycle_profiles;
create trigger cycle_profiles_touch before update on public.cycle_profiles
  for each row execute function public.hedefit_touch_updated_at();
drop trigger if exists daily_checkins_touch on public.daily_checkins;
create trigger daily_checkins_touch before update on public.daily_checkins
  for each row execute function public.hedefit_touch_updated_at();
