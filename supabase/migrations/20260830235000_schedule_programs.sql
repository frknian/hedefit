alter table public.workout_schedule
  add column if not exists program_id uuid,
  add column if not exists program_name text;

alter table public.workout_schedule
  drop constraint if exists workout_schedule_program_name_length;

alter table public.workout_schedule
  add constraint workout_schedule_program_name_length
  check (program_name is null or char_length(program_name) between 2 and 120);
