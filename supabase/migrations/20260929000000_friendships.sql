-- Sosyal katman v1: karşılıklı arkadaşlık (istek/onay) + haftalık XP lider
-- tablosu. Aktivite akışı ve ortak meydan okumalar sonraki fazda.

create table if not exists public.friendships (
  id uuid primary key default gen_random_uuid(),
  requester_id uuid not null references auth.users(id) on delete cascade,
  addressee_id uuid not null references auth.users(id) on delete cascade,
  status text not null default 'pending' check (status in ('pending', 'accepted', 'declined')),
  created_at timestamptz not null default now(),
  responded_at timestamptz,
  constraint friendships_no_self check (requester_id <> addressee_id)
);

-- Bir çift kullanıcı arasında yön fark etmeksizin tek kayıt: A->B isteği
-- varken B->A isteği de gönderilemesin.
create unique index if not exists friendships_unique_pair
  on public.friendships (least(requester_id, addressee_id), greatest(requester_id, addressee_id));

create index if not exists friendships_requester_idx on public.friendships (requester_id, status);
create index if not exists friendships_addressee_idx on public.friendships (addressee_id, status);

alter table public.friendships enable row level security;

drop policy if exists "Kullanıcı kendi arkadaşlık kayıtlarını görebilir" on public.friendships;
create policy "Kullanıcı kendi arkadaşlık kayıtlarını görebilir"
  on public.friendships for select
  using (auth.uid() = requester_id or auth.uid() = addressee_id);

drop policy if exists "Kullanıcı istek gönderebilir" on public.friendships;
create policy "Kullanıcı istek gönderebilir"
  on public.friendships for insert
  with check (auth.uid() = requester_id and public.account_is_active());

drop policy if exists "Alıcı isteğe yanıt verebilir" on public.friendships;
create policy "Alıcı isteğe yanıt verebilir"
  on public.friendships for update
  using (auth.uid() = addressee_id and status = 'pending')
  with check (status in ('accepted', 'declined'));

drop policy if exists "Taraflar arkadaşlığı sonlandırabilir" on public.friendships;
create policy "Taraflar arkadaşlığı sonlandırabilir"
  on public.friendships for delete
  using (auth.uid() = requester_id or auth.uid() = addressee_id);

grant select, insert, update, delete on public.friendships to authenticated;

-- Bekleyen/kabul edilmiş arkadaşlığın karşı tarafını ve profil bilgilerini
-- (username, ad, avatar) tek sorguda döner; hassas alan (e-posta vb.) yok.
create or replace function public.hedefit_list_friendships(p_status text default null)
returns table (
  id uuid,
  status text,
  created_at timestamptz,
  responded_at timestamptz,
  is_incoming boolean,
  friend_id uuid,
  username text,
  display_name text,
  avatar_path text
)
language sql stable security definer set search_path = public as $$
  select
    f.id, f.status, f.created_at, f.responded_at,
    (f.addressee_id = auth.uid()) as is_incoming,
    case when f.requester_id = auth.uid() then f.addressee_id else f.requester_id end as friend_id,
    p.username, p.display_name, p.avatar_path
  from public.friendships f
  join public.profiles p
    on p.id = case when f.requester_id = auth.uid() then f.addressee_id else f.requester_id end
  where (f.requester_id = auth.uid() or f.addressee_id = auth.uid())
    and (p_status is null or f.status = p_status)
  order by f.created_at desc;
$$;
grant execute on function public.hedefit_list_friendships(text) to authenticated;

-- Kısmi eşleşmeli kullanıcı arama (yalnız kamuya açık, kimliği ifşa etmeyen
-- alanlar). En fazla 20 sonuç; kullanıcının kendisi hariç tutulur.
create or replace function public.hedefit_search_users(p_query text)
returns table (id uuid, username text, display_name text, avatar_path text)
language sql stable security definer set search_path = public as $$
  select p.id, p.username, p.display_name, p.avatar_path
  from public.profiles p
  where p.username is not null
    and p.username ilike left(regexp_replace(lower(trim(coalesce(p_query, ''))), '[^a-z0-9._]', '', 'g'), 20) || '%'
    and p.id <> auth.uid()
    and length(trim(coalesce(p_query, ''))) >= 2
  order by p.username
  limit 20;
$$;
grant execute on function public.hedefit_search_users(text) to authenticated;

-- profiles RLS yalnızca kendi satırını görmeye izin verir, bu yüzden
-- username -> id çözümlemesi de security definer içinde yapılır; istemcinin
-- friendships tablosuna doğrudan insert atması (requester_id = auth.uid()
-- RLS'i altında) yine güvenli ama hedef kullanıcıyı username'den bulmak için
-- bu RPC gerekir.
create or replace function public.hedefit_send_friend_request(p_username text)
returns table (id uuid, status text, created_at timestamptz)
language plpgsql security definer set search_path = public as $$
declare
  v_target uuid;
  v_row public.friendships;
begin
  select p.id into v_target from public.profiles p where p.username = lower(trim(coalesce(p_username, '')));
  if v_target is null then
    raise exception 'user_not_found' using errcode = 'P0002';
  end if;
  if v_target = auth.uid() then
    raise exception 'cannot_friend_self' using errcode = '22023';
  end if;
  if exists (
    select 1 from public.friendships f
    where least(f.requester_id, f.addressee_id) = least(auth.uid(), v_target)
      and greatest(f.requester_id, f.addressee_id) = greatest(auth.uid(), v_target)
  ) then
    raise exception 'already_exists' using errcode = '23505';
  end if;
  insert into public.friendships (requester_id, addressee_id)
  values (auth.uid(), v_target)
  returning * into v_row;
  return query select v_row.id, v_row.status, v_row.created_at;
end $$;
grant execute on function public.hedefit_send_friend_request(text) to authenticated;

-- Cari haftanın (Pazartesi başlangıçlı) XP toplamı: kullanıcı + kabul edilmiş
-- arkadaşları. xp_events zaten user_id/amount/occurred_at taşıyor
-- (bkz. 20260828180000_tasks_rewards.sql); burada yeni yazım yok, salt okuma.
create or replace function public.hedefit_weekly_leaderboard()
returns table (user_id uuid, username text, display_name text, avatar_path text, weekly_xp bigint)
language sql stable security definer set search_path = public as $$
  with member_ids as (
    select auth.uid() as id
    union
    select case when f.requester_id = auth.uid() then f.addressee_id else f.requester_id end
    from public.friendships f
    where f.status = 'accepted' and (f.requester_id = auth.uid() or f.addressee_id = auth.uid())
  )
  select
    m.id as user_id, p.username, p.display_name, p.avatar_path,
    coalesce(sum(e.amount), 0)::bigint as weekly_xp
  from member_ids m
  join public.profiles p on p.id = m.id
  left join public.xp_events e
    on e.user_id = m.id and e.occurred_at >= date_trunc('week', now())
  group by m.id, p.username, p.display_name, p.avatar_path
  order by weekly_xp desc, m.id;
$$;
grant execute on function public.hedefit_weekly_leaderboard() to authenticated;
