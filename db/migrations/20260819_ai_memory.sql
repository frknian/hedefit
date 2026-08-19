-- AI koçunun kalıcı hafızası, geri bildirimi ve sağlayıcı telemetrisi.
--
-- Üç tablo da yalnızca EKLEMEdir; var olan hiçbir tabloya dokunulmaz, bu
-- yüzden veri kaybı riski yoktur ve geri alınması aşağıdaki "down" bölümünü
-- çalıştırmakla sınırlıdır.
--
-- İZOLASYON: her tabloda RLS açıktır ve politika `auth.uid() = user_id`
-- şeklindedir. Uygulama kodu kullanıcı izolasyonunu KENDİ kontrol etmez;
-- sunucu tarafındaki istemci de kullanıcının kendi jetonuyla kurulur
-- (bkz. lib/ai/memory.ts userClient), servis anahtarı kullanılmaz. Böylece
-- bir kod hatası başkasının hafızasını okutamaz.

-- ---------------------------------------------------------------------------
-- 1) Yapılandırılmış kullanıcı hafızası
-- ---------------------------------------------------------------------------
create table if not exists public.ai_memories (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  -- Serbest metin değil, dar bir küme (bkz. lib/ai/memory.ts MEMORY_TYPES).
  -- Kısıt burada da tekrarlanır: uygulama katmanı atlansa bile veritabanı
  -- çöp kategoriyi kabul etmesin.
  memory_type text not null check (memory_type in (
    'exercise_preference', 'food_preference', 'coaching_preference',
    'schedule_preference', 'goal', 'constraint', 'habit', 'equipment',
    'motivation_pattern'
  )),
  memory_key text not null check (length(memory_key) between 1 and 60),
  memory_value text not null check (length(memory_value) between 1 and 120),
  confidence numeric(3, 2) not null default 0.7 check (confidence >= 0 and confidence <= 1),
  source text not null default 'inferred' check (source in ('user_explicit', 'inferred')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  -- Tekilleştirmenin ASIL yeri. "Koşmayı sevmiyorum" iki kez söylenirse iki
  -- satır olmaz; sonraki upsert öncekini günceller.
  unique (user_id, memory_type, memory_key)
);

-- Bağlam kurucusu her sohbette kullanıcının hafızasını güncellik sırasına göre
-- okur; bu indeks o sorgunun tamamını karşılar.
create index if not exists ai_memories_user_updated_idx
  on public.ai_memories (user_id, updated_at desc);

alter table public.ai_memories enable row level security;
drop policy if exists "Users manage own ai memories" on public.ai_memories;
create policy "Users manage own ai memories" on public.ai_memories
  for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

-- ---------------------------------------------------------------------------
-- 2) Yanıt geri bildirimi (👍 / 👎)
-- ---------------------------------------------------------------------------
-- Bu tablonun amacı ileride yerel modeli uzak modelle karşılaştırabilmek ve
-- (docs/AI_FINETUNING_FUTURE.md) küratörlü bir veri kümesi kurabilmektir.
--
-- GİZLİLİK: mesajın KENDİSİ burada saklanmaz. Yalnızca hangi sağlayıcı/model/
-- prompt sürümünün beğenilip beğenilmediği tutulur. Sohbet içeriğini geri
-- bildirim tablosuna kopyalamak, sağlık verisini ikinci bir yerde çoğaltmak
-- olurdu.
create table if not exists public.ai_feedback (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  message_id text not null check (length(message_id) between 1 and 64),
  rating smallint not null check (rating in (-1, 1)),
  provider text check (length(provider) <= 40),
  model text check (length(model) <= 80),
  prompt_version text check (length(prompt_version) <= 20),
  category text check (length(category) <= 40),
  created_at timestamptz not null default now(),
  -- Aynı mesaja iki kez oy verilmez; kullanıcı fikrini değiştirirse günceller.
  unique (user_id, message_id)
);

create index if not exists ai_feedback_user_created_idx
  on public.ai_feedback (user_id, created_at desc);

alter table public.ai_feedback enable row level security;
drop policy if exists "Users manage own ai feedback" on public.ai_feedback;
create policy "Users manage own ai feedback" on public.ai_feedback
  for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

-- ---------------------------------------------------------------------------
-- 3) Sağlayıcı olayları (gözlemlenebilirlik)
-- ---------------------------------------------------------------------------
-- Yönlendirmenin gerçekte ne yaptığını ölçmek için: hangi sağlayıcı seçildi,
-- yedeğe düşüldü mü, ne kadar sürdü. İSTEK/YANIT METNİ YAZILMAZ.
create table if not exists public.ai_provider_events (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  category text not null check (length(category) <= 40),
  provider text not null check (length(provider) <= 40),
  model text check (length(model) <= 80),
  outcome text not null check (outcome in ('success', 'error', 'skipped')),
  fallback_used boolean not null default false,
  latency_ms integer check (latency_ms >= 0),
  input_tokens integer check (input_tokens >= 0),
  output_tokens integer check (output_tokens >= 0),
  prompt_version text check (length(prompt_version) <= 20),
  -- Hata SINIFI (ör. 'timeout', 'rate_limited'); sağlayıcının ham mesajı değil.
  error_kind text check (length(error_kind) <= 40),
  created_at timestamptz not null default now()
);

create index if not exists ai_provider_events_user_created_idx
  on public.ai_provider_events (user_id, created_at desc);

alter table public.ai_provider_events enable row level security;
-- Kullanıcı kendi olaylarını yazabilir ve okuyabilir; başkasınınkine erişemez.
drop policy if exists "Users manage own ai provider events" on public.ai_provider_events;
create policy "Users manage own ai provider events" on public.ai_provider_events
  for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

-- ---------------------------------------------------------------------------
-- Geri alma (down)
-- ---------------------------------------------------------------------------
-- drop table if exists public.ai_provider_events;
-- drop table if exists public.ai_feedback;
-- drop table if exists public.ai_memories;
