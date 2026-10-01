alter table public.workout_program_collections
  add column if not exists training_days jsonb not null default '[]'::jsonb;

alter table public.workout_program_collections
  drop constraint if exists workout_program_collections_training_days_shape;

alter table public.workout_program_collections
  add constraint workout_program_collections_training_days_shape
  check (jsonb_typeof(training_days) = 'array' and jsonb_array_length(training_days) <= 7);
