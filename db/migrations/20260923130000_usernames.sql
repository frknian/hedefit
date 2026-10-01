-- Unique, profanity-filtered usernames. The database is the single source of
-- truth: the app only pre-checks format and asks hedefit_check_username().

alter table public.profiles add column if not exists username text;

create or replace function public.hedefit_username_is_clean(p text)
returns boolean language plpgsql immutable set search_path = public as $$
declare
  -- Diacritic folding, then a second leet-folded view, so "f.u.c.k", "0rospu"
  -- and "sîktir" are caught while "sik123" still splits into the token "sik".
  base text := translate(replace(lower(coalesce(p, '')), chr(775), ''), 'ışğüöçâîûéèêàáñß', 'isguocaiueeeaans');
  leet text := translate(base, '0134578@$!', 'oieastbasi');
  compacts text[];
  tokens text[];
  w text;
  -- Matched anywhere in the name (long, unambiguous words only).
  anywhere text[] := array[
    -- tr
    'orospu','orspu','orosbu','yarrak','yarak','aminakoy','aminako','amcik','sikis','sikik','siktir','sikerim','sikeyim','pezevenk','kahpe','ibne','surtuk','tasak','yavsak','gavat','kaltak','fahise','gotveren','gotlek','kerhane','kancik','dalyarak','amcuk',
    -- en
    'fuck','shit','bitch','cunt','pussy','porn','slut','whore','penis','vagina','dildo','horny','milf','blowjob','handjob','orgasm','hentai','bastard','wank','twat','motherf','nazis',
    -- de
    'ficken','ficker','scheise','scheie','fotze','schlampe','wichser','arsch','hurensohn',
    -- fr
    'merde','putain','salope','connard','encule','enfoire',
    -- es / pt / it
    'mierda','cabron','pendejo','joder','chinga','puteria','caralho','buceta','piroca','viado','stronzo','puttana','minchia','vaffanculo','coglion',
    -- ru / pl / nl / el
    'blyat','blyad','pizda','pidor','pidar','mudak','zalupa','kurwa','chuj','pierdol','jebac','jebany','kanker','malaka','poutana',
    -- ar / fa / hi / id / ja / zh
    'sharmouta','sharmuta','koskesh','chutiya','bhenchod','behenchod','madarchod','bhosdi','kontol','memek','bangsat','ngentot','chinpo','caonima',
    -- impersonation
    'hedefit','fitkoc'
  ];
  -- Only blocked as the whole name or a separated part (e.g. "am", "sik"
  -- would otherwise reject names like "samet" or "sikke").
  whole text[] := array[
    'am','amk','aq','amina','sikim','fick','nique','oc','mk','sik','sg','got','pic','meme','bok','seks','sex','sexy','xxx','ass','arse','anal','anus','cock','dick','cum','jizz','tits','orgy','nude','rape','fag','fagot','niger','niga','nazi','kike','spic','chink','dyke','trany','fuk',
    'bite','chate','pute','puta','cono','verga','polla','culo','cazo','troia','figa','pora','foda','suka','huy','hui','ebat','kos','zebi','ayre','kuss','kir','jende','kooni','kut','lul','hoer','randi','lund','gand','anjing','manko','shabi',
    'admin','administrator','moderator','mod','support','destek','root','system','official','resmi'
  ];
begin
  compacts := array[
    regexp_replace(regexp_replace(base, '[^a-z]', '', 'g'), '(.)\1+', '\1', 'g'),
    regexp_replace(regexp_replace(leet, '[^a-z]', '', 'g'), '(.)\1+', '\1', 'g')
  ];
  tokens := array(
    select regexp_replace(t, '(.)\1+', '\1', 'g')
    from unnest(regexp_split_to_array(base, '[^a-z]+') || regexp_split_to_array(leet, '[^a-z]+')) t
    where t <> ''
  );
  if position('xxx' in base) > 0 then return false; end if;
  foreach w in array anywhere loop
    w := regexp_replace(w, '(.)\1+', '\1', 'g');
    if position(w in compacts[1]) > 0 or position(w in compacts[2]) > 0 then return false; end if;
  end loop;
  foreach w in array whole loop
    w := regexp_replace(w, '(.)\1+', '\1', 'g');
    if w = any(compacts) or w = any(tokens) then return false; end if;
  end loop;
  return true;
end $$;

alter table public.profiles drop constraint if exists profiles_username_format;
alter table public.profiles add constraint profiles_username_format check (
  username is null or (username ~ '^[a-z0-9._]{3,20}$' and public.hedefit_username_is_clean(username))
);
create unique index if not exists profiles_username_unique on public.profiles (username);

create or replace function public.hedefit_check_username(p_username text)
returns text language plpgsql stable security definer set search_path = public as $$
declare u text := lower(trim(coalesce(p_username, '')));
begin
  if length(u) < 3 then return 'too_short'; end if;
  if length(u) > 20 or u !~ '^[a-z0-9._]+$' then return 'invalid'; end if;
  if not public.hedefit_username_is_clean(u) then return 'blocked'; end if;
  if exists (select 1 from public.profiles where username = u and id is distinct from auth.uid()) then return 'taken'; end if;
  return 'ok';
end $$;
revoke all on function public.hedefit_check_username(text) from public;
grant execute on function public.hedefit_check_username(text) to anon, authenticated;

-- Reserve the username atomically at signup; a clash makes the signup itself
-- fail instead of leaving an account without its chosen name.
create or replace function public.hedefit_handle_new_user()
returns trigger language plpgsql security definer set search_path = public as $$
declare u text := nullif(lower(trim(new.raw_user_meta_data ->> 'username')), '');
begin
  if u is not null then
    insert into public.profiles (id, username, display_name)
    values (new.id, u, u)
    on conflict (id) do update set username = excluded.username;
  end if;
  return new;
end $$;
drop trigger if exists hedefit_on_auth_user_created on auth.users;
create trigger hedefit_on_auth_user_created after insert on auth.users
  for each row execute function public.hedefit_handle_new_user();
