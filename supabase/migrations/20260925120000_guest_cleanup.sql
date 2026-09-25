-- Misafir (anonim) hesap temizliği.
-- "Üye olmadan dene" akışı Supabase anonim oturumları kullanır. Hesabını hiç
-- kaydetmeyen ve 30 gündür oturum açmayan misafirleri sileriz; kullanıcı
-- tablolarındaki satırlar auth.users'a bağlı ON DELETE CASCADE ile gider.
-- Not: Anonim girişin Supabase panelinde (Authentication → Sign In / Providers →
-- "Allow anonymous sign-ins") açılması gerekir; bu dosya yalnız temizliği ekler.

create or replace function public.hedefit_cleanup_stale_guests(p_days integer default 30)
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
  removed integer;
begin
  delete from auth.users
  where is_anonymous = true
    and coalesce(last_sign_in_at, created_at) < now() - make_interval(days => p_days);
  get diagnostics removed = row_count;
  return removed;
end;
$$;

revoke all on function public.hedefit_cleanup_stale_guests(integer) from public, anon, authenticated;

-- pg_cron varsa her gece 03:30'da çalıştır.
do $$
begin
  if exists (select 1 from pg_extension where extname = 'pg_cron') then
    perform cron.schedule('hedefit-cleanup-stale-guests', '30 3 * * *', 'select public.hedefit_cleanup_stale_guests(30)');
  end if;
end;
$$;
