-- Gizlilik dostu ürün analitiği: yalnızca İZİN LİSTESİNDEKİ olay adının günlük SAYACI. Kullanıcı kimliği, cihaz,
-- ek veri YOK; bir satırdan hiçbir kişi çıkarılamaz. RLS açık ve politika yok: yalnızca service role yazar/okur.
create table if not exists public.analytics_event_counts (
  day date not null,
  event text not null check (event ~ '^[a-z][a-z0-9_]{2,63}$'),
  count bigint not null default 0 check (count >= 0),
  primary key (day, event)
);
alter table public.analytics_event_counts enable row level security;

create or replace function public.hedefit_bump_event(p_day date, p_event text)
returns void language sql security definer set search_path = public as $$
  insert into public.analytics_event_counts (day, event, count) values (p_day, p_event, 1)
  on conflict (day, event) do update set count = public.analytics_event_counts.count + 1;
$$;
revoke all on function public.hedefit_bump_event(date, text) from public, anon, authenticated;
grant execute on function public.hedefit_bump_event(date, text) to service_role;
