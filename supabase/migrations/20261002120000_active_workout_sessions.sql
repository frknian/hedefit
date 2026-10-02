-- Devam eden antrenmanın cihazlar arası (telefon, katlanan, tablet) taşınması için tek satırlık oturum özeti.
create table if not exists public.active_workout_sessions (
  user_id uuid primary key default auth.uid() references auth.users(id) on delete cascade,
  snapshot jsonb not null check (jsonb_typeof(snapshot) = 'object' and pg_column_size(snapshot) <= 65536),
  device_id text not null check (char_length(device_id) between 1 and 64),
  saved_at bigint not null check (saved_at > 0),
  updated_at timestamptz not null default now()
);
alter table public.active_workout_sessions enable row level security;
drop policy if exists "Users manage own active workout" on public.active_workout_sessions;
create policy "Users manage own active workout" on public.active_workout_sessions for all to authenticated using (auth.uid() = user_id) with check (auth.uid() = user_id);
revoke all on public.active_workout_sessions from anon;
grant select, insert, update, delete on public.active_workout_sessions to authenticated;
