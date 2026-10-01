-- challenges ve challenge_participants SELECT policy'leri birbirine EXISTS
-- alt sorgusuyla bakıyordu: her ikisi de RLS altında olduğundan Postgres
-- "infinite recursion detected in policy for relation" (42P17) ile hata
-- veriyordu (bkz. 20260930000000_social_feed_challenges.sql). Çapraz
-- kontrolleri security definer fonksiyonlara taşımak RLS'i bu iki sorgu
-- için atlatır ve döngüyü kırar — Supabase'in önerdiği standart çözüm.

create or replace function public.hedefit_is_challenge_creator(p_challenge_id uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.challenges c where c.id = p_challenge_id and c.creator_id = auth.uid());
$$;
grant execute on function public.hedefit_is_challenge_creator(uuid) to authenticated;

create or replace function public.hedefit_is_challenge_participant(p_challenge_id uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.challenge_participants cp where cp.challenge_id = p_challenge_id and cp.user_id = auth.uid());
$$;
grant execute on function public.hedefit_is_challenge_participant(uuid) to authenticated;

drop policy if exists "Yaratıcı veya katılımcı meydan okumayı görebilir" on public.challenges;
create policy "Yaratıcı veya katılımcı meydan okumayı görebilir"
  on public.challenges for select
  using (creator_id = auth.uid() or public.hedefit_is_challenge_participant(id));

drop policy if exists "Kendi kaydını veya yarattığı meydan okumanın katılımcılarını görebilir" on public.challenge_participants;
create policy "Kendi kaydını veya yarattığı meydan okumanın katılımcılarını görebilir"
  on public.challenge_participants for select
  using (user_id = auth.uid() or public.hedefit_is_challenge_creator(challenge_id));
