-- Güvenlik açığı: "Users can update own profile" politikası sütun kısıtı
-- içermiyor (bkz. auth.uid() = id). profiles.is_premium tüm günlük AI kullanım
-- limitlerinin (bkz. lib/usage-limits.ts) tek kaynağı; herhangi bir
-- kimliği doğrulanmış kullanıcı kendi satırında bu sütunu PATCH ile true
-- yapıp premium limitlere sıçrayabiliyordu. Aynı şekilde account_status'u
-- 'deletion_pending' yapmak (yalnız sunucunun/servisin karar vermesi gereken
-- bir geçiş) da mümkündü.
--
-- account_status'un 'active' <-> 'frozen' arasında SERBEST kalması gerekiyor:
-- components/ProfileManager.tsx freezeAccount/unfreezeAccount bunu doğrudan
-- istemciden, service_role olmadan günceller (bkz. freezeAccount, satır 180).
-- Bu yüzden yalnız is_premium'u ve 'deletion_pending' geçişini kilitliyoruz;
-- dondurma/açma akışını bozmuyoruz.
create or replace function public.profiles_guard_privileged_columns()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if coalesce(auth.role(), '') <> 'service_role' then
    if new.is_premium is distinct from old.is_premium then
      new.is_premium := old.is_premium;
    end if;
    if new.account_status is distinct from old.account_status and new.account_status = 'deletion_pending' then
      new.account_status := old.account_status;
    end if;
  end if;
  return new;
end;
$$;

drop trigger if exists profiles_guard_privileged_columns on public.profiles;
create trigger profiles_guard_privileged_columns
before update on public.profiles
for each row execute function public.profiles_guard_privileged_columns();
