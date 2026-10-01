-- Push / Pull şablonları da kullanıcı tarafından oluşturulan programlardır.
-- Eski enum benzeri check kısıtı bu kaynağı kabul etmediği için ekleme akışı
-- RLS hatası gibi görünen bir PostgREST mesajıyla sonlanıyordu.
alter table public.workout_program_collections
  drop constraint if exists workout_program_collections_source_check;

alter table public.workout_program_collections
  add constraint workout_program_collections_source_check
  check (source in ('regional', 'custom', 'assessment', 'push_pull_template'));

alter table public.workout_program_collections enable row level security;

drop policy if exists "Users can read own program collections" on public.workout_program_collections;
create policy "Users can read own program collections"
  on public.workout_program_collections for select
  using (auth.uid() = user_id);

drop policy if exists "Users can insert own program collections" on public.workout_program_collections;
create policy "Users can insert own program collections"
  on public.workout_program_collections for insert
  with check (auth.uid() = user_id);

drop policy if exists "Users can update own program collections" on public.workout_program_collections;
create policy "Users can update own program collections"
  on public.workout_program_collections for update
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);

drop policy if exists "Users can delete own program collections" on public.workout_program_collections;
create policy "Users can delete own program collections"
  on public.workout_program_collections for delete
  using (auth.uid() = user_id);
