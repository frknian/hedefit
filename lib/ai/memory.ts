// Yapılandırılmış uzun vadeli kullanıcı hafızası.
//
// Amaç: "Hedefit beni zamanla tanıyor" hissi. Bunun için HER CÜMLEYİ saklamak
// yanlış yoldur — bağlamı şişirir, maliyeti artırır ve "bugün biraz yorgunum"
// gibi geçici bir ifade aylar sonra kalıcı gerçek gibi geri döner. Bu yüzden
// hafıza dar bir kategori kümesiyle sınırlıdır ve her kayıt tekilleştirilir.
//
// KULLANICI İZOLASYONU uygulama kodunda DEĞİL, veritabanı seviyesinde RLS ile
// sağlanır (db/migrations/20260819_ai_memory.sql). Buradaki istemci her zaman
// kullanıcının kendi erişim jetonuyla kurulur; servis anahtarı kullanılmaz.

import { createClient, type SupabaseClient } from "@supabase/supabase-js";
import { normalizeSupabaseUrl } from "../supabase/url.ts";
import { bearerToken } from "../api-auth.ts";

export const MEMORY_TYPES = [
  "exercise_preference",
  "food_preference",
  "coaching_preference",
  "schedule_preference",
  "goal",
  "constraint",
  "habit",
  "equipment",
  "motivation_pattern",
] as const;

export type MemoryType = (typeof MEMORY_TYPES)[number];

export type MemorySource = "user_explicit" | "inferred";

export type UserMemory = {
  id?: string;
  type: MemoryType;
  /** Neyle ilgili: "running", "gluten", "evening". Küçük harfe normalize edilir. */
  key: string;
  /** Değer: "like", "dislike", serbest kısa metin. */
  value: string;
  confidence: number;
  source: MemorySource;
  createdAt?: string;
  updatedAt?: string;
};

// Bağlamın hafızayla dolmasını engelleyen sert sınırlar.
export const MAX_MEMORIES_PER_USER = 60;
const MAX_KEY_LENGTH = 60;
const MAX_VALUE_LENGTH = 120;
// Bu eşiğin altındaki çıkarımlar saklanmaz: yanlış bir "kullanıcı X sevmiyor"
// kaydı, doğru olanın hiç olmamasından daha zararlıdır.
export const MIN_CONFIDENCE = 0.6;

function isMemoryType(value: unknown): value is MemoryType {
  return typeof value === "string" && (MEMORY_TYPES as readonly string[]).includes(value);
}

/**
 * Modelden veya istemciden gelen ham nesneyi doğrular.
 *
 * ASLA modelin JSON'una güvenmeyiz: `generateObject` şemaya uymayan çıktı
 * üretebilir (bkz. lib/ai/providers/openai-compatible.ts açıklaması) ve bu
 * veri doğrudan veritabanına yazılacak. Geçersizse `null` döner — atılır.
 */
export function sanitizeMemory(value: unknown): UserMemory | null {
  if (!value || typeof value !== "object") return null;
  const record = value as Record<string, unknown>;
  if (!isMemoryType(record.type)) return null;

  const key = typeof record.key === "string" ? record.key.trim().toLocaleLowerCase("tr-TR").slice(0, MAX_KEY_LENGTH) : "";
  const memoryValue = typeof record.value === "string" ? record.value.trim().slice(0, MAX_VALUE_LENGTH) : "";
  if (!key || !memoryValue) return null;

  const rawConfidence = typeof record.confidence === "number" && Number.isFinite(record.confidence) ? record.confidence : 0.7;
  const confidence = Math.min(1, Math.max(0, rawConfidence));
  if (confidence < MIN_CONFIDENCE) return null;

  return {
    type: record.type,
    key,
    value: memoryValue,
    confidence: Number(confidence.toFixed(2)),
    source: record.source === "user_explicit" ? "user_explicit" : "inferred",
  };
}

/**
 * Aynı (type, key) ikilisi tek bir gerçektir. "Koşmayı sevmiyorum" dedikten
 * sonra "aslında koşuyorum" derse ikinci kayıt birincinin YERİNE geçmeli;
 * yoksa bağlamda birbiriyle çelişen iki satır modele gider.
 *
 * Çakışmada kazanan: önce kaynak (kullanıcının açık ifadesi çıkarımı yener),
 * sonra güven skoru, sonra sonradan gelen.
 */
export function dedupeMemories(memories: UserMemory[]): UserMemory[] {
  const byKey = new Map<string, UserMemory>();
  for (const memory of memories) {
    const identity = `${memory.type}:${memory.key}`;
    const existing = byKey.get(identity);
    if (!existing) {
      byKey.set(identity, memory);
      continue;
    }
    const incomingRank = memory.source === "user_explicit" ? 1 : 0;
    const existingRank = existing.source === "user_explicit" ? 1 : 0;
    if (incomingRank > existingRank || (incomingRank === existingRank && memory.confidence >= existing.confidence)) {
      byKey.set(identity, memory);
    }
  }
  return [...byKey.values()];
}

/**
 * Bağlama girecek hafızaları seçer. Hepsini göndermeyiz (bkz. bağlam bütçesi):
 * güveni yüksek ve kullanıcının açıkça söylediği kayıtlar öne alınır.
 */
export function rankMemories(memories: UserMemory[], limit: number): UserMemory[] {
  return dedupeMemories(memories)
    .slice()
    .sort((a, b) => {
      const sourceDelta = (b.source === "user_explicit" ? 1 : 0) - (a.source === "user_explicit" ? 1 : 0);
      if (sourceDelta) return sourceDelta;
      return b.confidence - a.confidence;
    })
    .slice(0, limit);
}

/** Hafızayı modele gösterilecek kısa satırlara çevirir. */
export function formatMemories(memories: UserMemory[]): string[] {
  return memories.map((memory) => `${memory.type}/${memory.key}: ${memory.value}`);
}

/**
 * Mesajda kalıcı bir tercih OLABİLECEĞİNİ ucuza tahmin eder.
 *
 * Neden? Hafıza çıkarımı ikinci bir model çağrısıdır. Her mesaj için
 * çalıştırmak, kullanıcının sorduğu her soruyu iki kat pahalı hale getirirdi —
 * oysa mesajların çoğu ("kaç kalorim kaldı?") hiçbir kalıcı tercih içermez.
 * Bu ön eleme, ücretli çağrıyı yalnızca gerçekten aday olan mesajlara ayırır.
 *
 * Yanlış NEGATİF kabul edilebilir (tercih bir sonraki sefer yakalanır);
 * yanlış pozitifin bedeli yalnızca boşa giden bir çağrıdır.
 */
export function mayContainMemory(message: string): boolean {
  const value = (message || "").toLocaleLowerCase("tr-TR");
  if (value.length < 8) return false;
  return /sev(mi|i)yorum|sevmem|hoşlanm|nefret ed|tercih ed|yapamıyorum|yapamam|zorlanıyorum|alerji|sakatlık|sakatlan|ağrı(m|yor) var|ekipman|salona? git|evde çalış|sabah|akşam|vejet|vegan|glutensiz|laktoz|yemiyorum|yiyemiyorum|hedefim|istiyorum ki|antrenman günlerim/.test(value)
    || /don'?t like|dislike|hate|prefer|can'?t do|cannot do|allergic|injur|equipment|vegetarian|vegan|gluten|lactose|my goal is/.test(value);
}

function userClient(request: Request): SupabaseClient | null {
  const url = normalizeSupabaseUrl(process.env.NEXT_PUBLIC_SUPABASE_URL);
  const anonKey = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY;
  const token = bearerToken(request);
  if (!url || !anonKey || !token) return null;
  return createClient(url, anonKey, {
    auth: { persistSession: false, autoRefreshToken: false },
    global: { headers: { Authorization: `Bearer ${token}` } },
  });
}

type MemoryRow = { id: string; memory_type: string; memory_key: string; memory_value: string; confidence: number; source: string; created_at: string; updated_at: string };

function fromRow(row: MemoryRow): UserMemory {
  return {
    id: row.id,
    type: row.memory_type as MemoryType,
    key: row.memory_key,
    value: row.memory_value,
    confidence: Number(row.confidence),
    source: row.source === "user_explicit" ? "user_explicit" : "inferred",
    createdAt: row.created_at,
    updatedAt: row.updated_at,
  };
}

/**
 * Hafıza okuma. Tablo henüz yoksa (migration uygulanmamış kurulum) hata
 * FIRLATMAZ, boş liste döner: hafıza bir iyileştirmedir, koçun çalışmasının
 * ön koşulu değildir.
 */
export async function loadMemories(request: Request, limit = MAX_MEMORIES_PER_USER): Promise<UserMemory[]> {
  const client = userClient(request);
  if (!client) return [];
  const { data, error } = await client
    .from("ai_memories")
    .select("id, memory_type, memory_key, memory_value, confidence, source, created_at, updated_at")
    .order("updated_at", { ascending: false })
    .limit(limit);
  if (error || !data) return [];
  return (data as MemoryRow[]).map(fromRow);
}

/**
 * Hafıza yazma. (user_id, memory_type, memory_key) benzersiz olduğu için
 * upsert doğal tekilleştirmeyi VERİTABANINDA yapar; uygulama kodundaki
 * dedupeMemories yalnızca aynı toplu iş içindeki çakışmaları çözer.
 */
export async function saveMemories(request: Request, userId: string, memories: UserMemory[]): Promise<number> {
  const client = userClient(request);
  if (!client || !memories.length) return 0;
  const rows = dedupeMemories(memories).map((memory) => ({
    user_id: userId,
    memory_type: memory.type,
    memory_key: memory.key,
    memory_value: memory.value,
    confidence: memory.confidence,
    source: memory.source,
    updated_at: new Date().toISOString(),
  }));
  const { error } = await client.from("ai_memories").upsert(rows, { onConflict: "user_id,memory_type,memory_key" });
  if (error) {
    // Kimliği loglamıyoruz; yalnız hata kodu. (Gizlilik: hafıza içeriği
    // kullanıcının sağlık/beslenme tercihleridir, log'a yazılmaz.)
    console.error("[ai-memory] upsert failed", error.code);
    return 0;
  }
  return rows.length;
}

/** Kullanıcının kendi hafızasını silmesi. RLS başkasının satırına dokunmayı engeller. */
export async function deleteMemory(request: Request, memoryId: string): Promise<boolean> {
  const client = userClient(request);
  if (!client) return false;
  const { error } = await client.from("ai_memories").delete().eq("id", memoryId);
  return !error;
}
