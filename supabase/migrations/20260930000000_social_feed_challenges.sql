-- Sosyal katman Faz 2+3: aktivite akışı (feed) + arkadaşlarla ortak haftalık
-- meydan okumalar. Feed yeni tablo eklemez, xp_events üzerinde okuma-zamanlı
-- bir RPC'dir (bkz. 20260929000000_friendships.sql hedefit_weekly_leaderboard
-- ile aynı desen).

create or replace function public.hedefit_friend_activity_feed(p_limit integer default 30, p_before timestamptz default null)
returns table (id uuid, user_id uuid, username text, display_name text, avatar_path text, source text, source_id text, amount integer, occurred_at timestamptz)
language sql stable security definer set search_path = public as $$
  with member_ids as (
    select auth.uid() as id
    union
    select case when f.requester_id = auth.uid() then f.addressee_id else f.requester_id end
    from public.friendships f
    where f.status = 'accepted' and (f.requester_id = auth.uid() or f.addressee_id = auth.uid())
  )
  select e.id, e.user_id, p.username, p.display_name, p.avatar_path, e.source, e.source_id, e.amount, e.occurred_at
  from public.xp_events e
  join member_ids m on m.id = e.user_id
  join public.profiles p on p.id = e.user_id
  where e.source in ('WORKOUT_COMPLETED', 'ROUTE_DISTANCE', 'ACHIEVEMENT_UNLOCKED', 'WEEKLY_GOAL_COMPLETED', 'WEEKLY_CHALLENGE_COMPLETED')
    and (p_before is null or e.occurred_at < p_before)
  order by e.occurred_at desc
  limit least(greatest(p_limit, 1), 50);
$$;
grant execute on function public.hedefit_friend_activity_feed(integer, timestamptz) to authenticated;

-- ---- Ortak meydan okumalar ------------------------------------------------

create table if not exists public.challenges (
  id uuid primary key default gen_random_uuid(),
  creator_id uuid not null references auth.users(id) on delete cascade,
  title text not null check (char_length(title) between 1 and 60),
  metric text not null check (metric in ('xp', 'workouts', 'distance_km')),
  target_value numeric not null check (target_value > 0),
  starts_at timestamptz not null default now(),
  ends_at timestamptz not null,
  created_at timestamptz not null default now(),
  constraint challenges_window check (ends_at > starts_at and ends_at <= starts_at + interval '30 days')
);

create table if not exists public.challenge_participants (
  challenge_id uuid not null references public.challenges(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  status text not null default 'invited' check (status in ('invited', 'joined', 'declined')),
  responded_at timestamptz,
  primary key (challenge_id, user_id)
);
create index if not exists challenge_participants_user_idx on public.challenge_participants (user_id, status);

alter table public.challenges enable row level security;
alter table public.challenge_participants enable row level security;

drop policy if exists "Yaratıcı veya katılımcı meydan okumayı görebilir" on public.challenges;
create policy "Yaratıcı veya katılımcı meydan okumayı görebilir"
  on public.challenges for select
  using (
    creator_id = auth.uid()
    or exists (select 1 from public.challenge_participants cp where cp.challenge_id = id and cp.user_id = auth.uid())
  );

drop policy if exists "Yaratıcı meydan okumayı iptal edebilir" on public.challenges;
create policy "Yaratıcı meydan okumayı iptal edebilir"
  on public.challenges for delete
  using (creator_id = auth.uid());

drop policy if exists "Kendi kaydını veya yarattığı meydan okumanın katılımcılarını görebilir" on public.challenge_participants;
create policy "Kendi kaydını veya yarattığı meydan okumanın katılımcılarını görebilir"
  on public.challenge_participants for select
  using (
    user_id = auth.uid()
    or exists (select 1 from public.challenges c where c.id = challenge_id and c.creator_id = auth.uid())
  );

drop policy if exists "Davete yanıt verebilir" on public.challenge_participants;
create policy "Davete yanıt verebilir"
  on public.challenge_participants for update
  using (user_id = auth.uid() and status = 'invited')
  with check (status in ('joined', 'declined'));

drop policy if exists "Kendi katılımından ayrılabilir" on public.challenge_participants;
create policy "Kendi katılımından ayrılabilir"
  on public.challenge_participants for delete
  using (user_id = auth.uid());

grant select, delete on public.challenges to authenticated;
grant select, update, delete on public.challenge_participants to authenticated;

-- challenges/challenge_participants'a doğrudan insert YOK: yalnızca kabul
-- edilmiş arkadaşlar davet edilebilsin diye oluşturma bu RPC üzerinden
-- yapılır (bkz. hedefit_send_friend_request ile aynı gerekçe).
create or replace function public.hedefit_create_challenge(p_title text, p_metric text, p_target numeric, p_days integer, p_friend_ids uuid[])
returns uuid
language plpgsql security definer set search_path = public as $$
declare
  v_challenge_id uuid;
  v_friend uuid;
begin
  if p_metric not in ('xp', 'workouts', 'distance_km') then
    raise exception 'invalid_metric' using errcode = '22023';
  end if;
  if p_days is null or p_days < 1 or p_days > 30 then
    raise exception 'invalid_duration' using errcode = '22023';
  end if;
  insert into public.challenges (creator_id, title, metric, target_value, starts_at, ends_at)
  values (auth.uid(), left(trim(coalesce(p_title, '')), 60), p_metric, p_target, now(), now() + make_interval(days => p_days))
  returning id into v_challenge_id;

  insert into public.challenge_participants (challenge_id, user_id, status, responded_at)
  values (v_challenge_id, auth.uid(), 'joined', now());

  foreach v_friend in array coalesce(p_friend_ids, array[]::uuid[]) loop
    if v_friend <> auth.uid() and exists (
      select 1 from public.friendships f
      where f.status = 'accepted'
        and least(f.requester_id, f.addressee_id) = least(auth.uid(), v_friend)
        and greatest(f.requester_id, f.addressee_id) = greatest(auth.uid(), v_friend)
    ) then
      insert into public.challenge_participants (challenge_id, user_id)
      values (v_challenge_id, v_friend)
      on conflict (challenge_id, user_id) do nothing;
    end if;
  end loop;

  return v_challenge_id;
end $$;
grant execute on function public.hedefit_create_challenge(text, text, numeric, integer, uuid[]) to authenticated;

-- Kendi meydan okumalarını (yarattıkları + davet edildikleri/katıldıkları)
-- katılımcı sayısıyla birlikte döner.
create or replace function public.hedefit_list_challenges()
returns table (
  id uuid, title text, metric text, target_value numeric, starts_at timestamptz, ends_at timestamptz,
  creator_id uuid, is_creator boolean, my_status text, participant_count bigint
)
language sql stable security definer set search_path = public as $$
  select
    c.id, c.title, c.metric, c.target_value, c.starts_at, c.ends_at,
    c.creator_id, (c.creator_id = auth.uid()) as is_creator,
    cp.status as my_status,
    (select count(*) from public.challenge_participants x where x.challenge_id = c.id and x.status = 'joined') as participant_count
  from public.challenges c
  join public.challenge_participants cp on cp.challenge_id = c.id and cp.user_id = auth.uid()
  order by c.created_at desc;
$$;
grant execute on function public.hedefit_list_challenges() to authenticated;

-- İlerleme sıralaması. Çağıran, o meydan okumada 'joined' olmalı — bu tek
-- yetkilendirilmiş durumda distance_km metriği için route_activities
-- cross-user okunur (RLS'i bu fonksiyon security definer olarak atlar).
create or replace function public.hedefit_challenge_progress(p_challenge_id uuid)
returns table (user_id uuid, username text, display_name text, avatar_path text, progress_value numeric)
language plpgsql stable security definer set search_path = public as $$
declare
  v_metric text;
  v_starts timestamptz;
  v_ends timestamptz;
begin
  select metric, starts_at, ends_at into v_metric, v_starts, v_ends from public.challenges where id = p_challenge_id;
  if v_metric is null then raise exception 'not_found' using errcode = 'P0002'; end if;
  if not exists (select 1 from public.challenge_participants cp where cp.challenge_id = p_challenge_id and cp.user_id = auth.uid() and cp.status = 'joined') then
    raise exception 'not_a_participant' using errcode = '42501';
  end if;

  return query
  with joined as (
    select cp.user_id from public.challenge_participants cp where cp.challenge_id = p_challenge_id and cp.status = 'joined'
  )
  select
    j.user_id, p.username, p.display_name, p.avatar_path,
    case v_metric
      when 'xp' then coalesce((select sum(e.amount) from public.xp_events e where e.user_id = j.user_id and e.occurred_at between v_starts and v_ends), 0)
      when 'workouts' then coalesce((select count(*) from public.xp_events e where e.user_id = j.user_id and e.source = 'WORKOUT_COMPLETED' and e.occurred_at between v_starts and v_ends), 0)
      else coalesce((select sum(r.distance_meters) / 1000.0 from public.route_activities r where r.user_id = j.user_id and r.started_at between v_starts and v_ends), 0)
    end as progress_value
  from joined j
  join public.profiles p on p.id = j.user_id
  order by progress_value desc, j.user_id;
end $$;
grant execute on function public.hedefit_challenge_progress(uuid) to authenticated;
