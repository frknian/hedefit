-- Hedefit Rota: kayak aktivitesi.
alter table public.route_activities drop constraint if exists route_activities_activity_type_check;
alter table public.route_activities add constraint route_activities_activity_type_check
  check (activity_type in ('Koşu', 'Yürüyüş', 'Doğa Yürüyüşü', 'Trail Koşusu', 'Bisiklet', 'Kayak'));
