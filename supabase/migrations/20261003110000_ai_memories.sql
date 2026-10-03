-- Koç hafızası tablosu (ai_memories). Daha önce yalnız db/migrations/20260819_ai_memory.sql içindeydi ve
-- supabase/migrations'a hiç alınmamıştı; hafıza özelliği (sohbetten çıkarım, Ayarlar → Koç hafızası) buna bağlı.
-- Tekrar çalıştırılabilir: tablo/index/politika zaten varsa değişmez.
-- İZOLASYON: RLS açık, politika auth.uid() = user_id; sunucu kullanıcının kendi jetonuyla çalışır.

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
