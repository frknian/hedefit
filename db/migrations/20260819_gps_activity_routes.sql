-- "Hedefit Rota" — canlı GPS aktivite takibi için rota depolama ve özet
-- istatistik sütunları.
--
-- Bu migration yalnızca EKLEMEdir: yeni bir tablo (`activity_routes`) ve
-- mevcut `sport_activity_entries` tablosuna nullable sütunlar ekler. Var
-- olan hiçbir satır/politika değiştirilmez, bu yüzden geri dönüşü aşağıdaki
-- "down" bölümünü çalıştırmakla sınırlıdır.
--
-- ROTA DEPOLAMA KARARI: her GPS noktasını ayrı bir satırda tutmak (30 dk'lık
-- bir koşuda ~300-400 satır) hem depolama hem indeks bakımı açısından
-- gereksiz şişkinlik yaratır ve liste sayfası tek bir satırdan fazlasına
-- ihtiyaç duymaz. Bunun yerine tüm rota, Google'ın klasik polyline encoding
-- algoritmasıyla (5 ondalık hassasiyet) tek bir `text` sütununda saklanır;
-- ~360 nokta yaklaşık 1.5KB eder. Coğrafi sorgu (yakındaki aktiviteler vb.)
-- ihtiyacı olmadığından PostGIS bilinçli olarak kullanılmaz.
create table if not exists public.activity_routes (
  id uuid primary key default gen_random_uuid(),
  activity_entry_id uuid not null unique references public.sport_activity_entries(id) on delete cascade,
  -- RLS'nin `sport_activity_entries` ile join yapmadan doğrudan
  -- uygulanabilmesi için user_id burada da denormalize edilir.
  user_id uuid not null references auth.users(id) on delete cascade,
  encoded_polyline text not null check (length(encoded_polyline) <= 200000),
  point_count integer not null default 0 check (point_count >= 0),
  started_at timestamptz,
  ended_at timestamptz,
  created_at timestamptz not null default now()
);

create unique index if not exists activity_routes_activity_entry_idx
  on public.activity_routes (activity_entry_id);

alter table public.activity_routes enable row level security;
drop policy if exists "Users can read own routes" on public.activity_routes;
drop policy if exists "Users can insert own routes" on public.activity_routes;
drop policy if exists "Users can update own routes" on public.activity_routes;
drop policy if exists "Users can delete own routes" on public.activity_routes;
create policy "Users can read own routes" on public.activity_routes for select using (auth.uid() = user_id);
create policy "Users can insert own routes" on public.activity_routes for insert with check (auth.uid() = user_id);
create policy "Users can update own routes" on public.activity_routes for update using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "Users can delete own routes" on public.activity_routes for delete using (auth.uid() = user_id);

-- GPS aktivitesine özgü özet istatistikler. Manuel kayıtlarda hep null kalır.
alter table public.sport_activity_entries add column if not exists avg_speed_kmh numeric(6,2) check (avg_speed_kmh is null or avg_speed_kmh >= 0);
alter table public.sport_activity_entries add column if not exists max_speed_kmh numeric(6,2) check (max_speed_kmh is null or max_speed_kmh >= 0);
alter table public.sport_activity_entries add column if not exists avg_heart_rate integer check (avg_heart_rate is null or avg_heart_rate between 20 and 250);
alter table public.sport_activity_entries add column if not exists max_heart_rate integer check (max_heart_rate is null or max_heart_rate between 20 and 250);
-- 'ble': canlı nabız bandından; 'health': Apple Health / Health Connect'ten
-- aktivite zaman aralığı için sonradan çekilen ortalama/maksimum değer.
alter table public.sport_activity_entries add column if not exists heart_rate_source text check (heart_rate_source is null or heart_rate_source in ('ble', 'health'));

-- GPS aktivitesini ve rotasını tek istekte, atomik olarak yazar. İki ayrı
-- insert client'tan yapılsaydı, ikinci adım (rota) başarısız olduğunda
-- rotasız bir aktivite kaydı kalabilirdi; bu fonksiyon o riski ortadan
-- kaldırır. `record_streak_activity` ile aynı güvenlik/izolasyon deseni:
-- security definer + auth.uid() kontrolü, servis anahtarı gerekmez.
create or replace function public.create_gps_activity_entry(
  p_activity_type text,
  p_sport_key text,
  p_sport_name text,
  p_occurred_at timestamptz,
  p_local_date date,
  p_duration_minutes integer,
  p_intensity text,
  p_distance_km numeric,
  p_estimated_calories integer default null,
  p_steps integer default null,
  p_notes text default null,
  p_details jsonb default '{}'::jsonb,
  p_avg_speed_kmh numeric default null,
  p_max_speed_kmh numeric default null,
  p_avg_heart_rate integer default null,
  p_max_heart_rate integer default null,
  p_heart_rate_source text default null,
  p_encoded_polyline text default '',
  p_point_count integer default 0,
  p_started_at timestamptz default null,
  p_ended_at timestamptz default null
)
returns public.sport_activity_entries
language plpgsql security definer set search_path = public as $$
declare
  v_user uuid := auth.uid();
  v_entry public.sport_activity_entries;
  v_route_id uuid;
begin
  if v_user is null then raise exception 'Authentication required'; end if;
  if p_activity_type not in ('walk', 'sport') then raise exception 'Invalid activity type'; end if;

  insert into public.sport_activity_entries (
    user_id, activity_type, sport_key, sport_name, occurred_at, local_date,
    duration_minutes, intensity, distance_km, estimated_calories, steps, notes, details,
    source, avg_speed_kmh, max_speed_kmh, avg_heart_rate, max_heart_rate, heart_rate_source
  ) values (
    v_user, p_activity_type, p_sport_key, p_sport_name, p_occurred_at, p_local_date,
    p_duration_minutes, p_intensity, p_distance_km, p_estimated_calories, p_steps, p_notes,
    coalesce(p_details, '{}'::jsonb),
    'gps', p_avg_speed_kmh, p_max_speed_kmh, p_avg_heart_rate, p_max_heart_rate, p_heart_rate_source
  ) returning * into v_entry;

  insert into public.activity_routes (
    activity_entry_id, user_id, encoded_polyline, point_count, started_at, ended_at
  ) values (
    v_entry.id, v_user, coalesce(p_encoded_polyline, ''), coalesce(p_point_count, 0), p_started_at, p_ended_at
  ) returning id into v_route_id;

  update public.sport_activity_entries set route_reference = v_route_id::text where id = v_entry.id
  returning * into v_entry;

  return v_entry;
end $$;

grant execute on function public.create_gps_activity_entry(
  text, text, text, timestamptz, date, integer, text, numeric, integer, integer,
  text, jsonb, numeric, numeric, integer, integer, text, text, integer, timestamptz, timestamptz
) to authenticated;

-- ---------------------------------------------------------------------------
-- Geri alma (down)
-- ---------------------------------------------------------------------------
-- drop function if exists public.create_gps_activity_entry(
--   text, text, text, timestamptz, date, integer, text, numeric, integer, integer,
--   text, jsonb, numeric, numeric, integer, integer, text, text, integer, timestamptz, timestamptz
-- );
-- alter table public.sport_activity_entries drop column if exists heart_rate_source;
-- alter table public.sport_activity_entries drop column if exists max_heart_rate;
-- alter table public.sport_activity_entries drop column if exists avg_heart_rate;
-- alter table public.sport_activity_entries drop column if exists max_speed_kmh;
-- alter table public.sport_activity_entries drop column if exists avg_speed_kmh;
-- drop table if exists public.activity_routes;
