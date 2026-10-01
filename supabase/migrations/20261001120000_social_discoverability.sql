-- Arkadaş aramasında görünme tercihi.
--
-- `discoverable = false` olan kullanıcı hedefit_search_users sonuçlarında
-- çıkmaz. Kullanıcı adını bilen biri yine de hedefit_send_friend_request ile
-- istek gönderebilir: ad kullanıcının bilerek paylaştığı bir tanıtıcıdır ve
-- bu ayar aramadan gizleme içindir, istek engelleme değildir. Kabul edilmiş
-- arkadaşlıklar, sıralama, akış ve meydan okumalar etkilenmez.
--
-- Varsayılan TRUE: ayar eklenmeden önceki davranışı (herkes aranabilir)
-- mevcut hesaplar için değiştirmemek için. Yeni hesaplar da aynı varsayılanı
-- alır; kapatmak kullanıcının tercihidir.

alter table public.profiles add column if not exists discoverable boolean not null default true;

create or replace function public.hedefit_search_users(p_query text)
returns table (id uuid, username text, display_name text, avatar_path text)
language sql stable security definer set search_path = public as $$
  select p.id, p.username, p.display_name, p.avatar_path
  from public.profiles p
  where p.username is not null
    and p.discoverable
    and p.username ilike left(regexp_replace(lower(trim(coalesce(p_query, ''))), '[^a-z0-9._]', '', 'g'), 20) || '%'
    and p.id <> auth.uid()
    and length(trim(coalesce(p_query, ''))) >= 2
  order by p.username
  limit 20;
$$;
grant execute on function public.hedefit_search_users(text) to authenticated;

create or replace function public.hedefit_get_discoverable()
returns boolean
language sql stable security definer set search_path = public as $$
  select coalesce((select p.discoverable from public.profiles p where p.id = auth.uid()), true);
$$;

create or replace function public.hedefit_set_discoverable(p_value boolean)
returns boolean
language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is null then
    raise exception 'not_authenticated' using errcode = '42501';
  end if;
  if p_value is null then
    raise exception 'invalid_value' using errcode = '22023';
  end if;
  update public.profiles set discoverable = p_value where id = auth.uid();
  if not found then
    raise exception 'not_found' using errcode = 'P0002';
  end if;
  return p_value;
end $$;

revoke all on function public.hedefit_get_discoverable() from public, anon;
revoke all on function public.hedefit_set_discoverable(boolean) from public, anon;
grant execute on function public.hedefit_get_discoverable() to authenticated;
grant execute on function public.hedefit_set_discoverable(boolean) to authenticated;
