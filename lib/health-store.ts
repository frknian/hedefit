// İsteğe bağlı sağlık verisi (döngü profili, kişiselleştirme ayarları) için veri erişimi.
//
// Her çağrı KULLANICININ KENDİ erişim jetonuyla yapılır; başka birinin satırına erişim RLS ile
// veritabanı düzeyinde engellenir (bkz. supabase/migrations/20261005120000_adaptive_fitness.sql).
// Tablolar henüz oluşturulmamışsa ("unavailable") çağıran taraf özelliği sessizce kapalı sayar;
// hiçbir mevcut akış bu tabloya bağlı değildir.

import { createClient, type SupabaseClient } from "@supabase/supabase-js";
import { bearerToken } from "./api-auth.ts";
import { normalizeSupabaseUrl } from "./supabase/url.ts";
import { emptyCycleProfile, type CycleProfile, type CycleRegularity } from "./cycle.ts";
import { checkinFromRow, checkinToRow, type Checkin } from "./checkin.ts";

export type StoreResult<T> = { ok: true; value: T } | { ok: false; reason: "unavailable" | "error" };

export interface PersonalizationSettings {
  adaptiveEnabled: boolean;
  cycleEnabled: boolean;
  aiHealthContextEnabled: boolean;
}

export const DEFAULT_PERSONALIZATION: PersonalizationSettings = { adaptiveEnabled: true, cycleEnabled: false, aiHealthContextEnabled: false };

export function userClientFor(request: Request): SupabaseClient | null {
  const url = normalizeSupabaseUrl(process.env.NEXT_PUBLIC_SUPABASE_URL);
  const anonKey = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY;
  const token = bearerToken(request);
  if (!url || !anonKey || !token) return null;
  return createClient(url, anonKey, { auth: { persistSession: false, autoRefreshToken: false }, global: { headers: { Authorization: `Bearer ${token}` } } });
}

/** PostgREST, tablo yokken PGRST205 (şema önbelleği) ya da 42P01 (undefined_table) döner. */
export function isMissingTable(error: { code?: string; message?: string } | null | undefined): boolean {
  if (!error) return false;
  return error.code === "PGRST205" || error.code === "42P01" || /could not find the table|does not exist/i.test(error.message ?? "");
}

const REGULARITIES: CycleRegularity[] = ["regular", "somewhat_irregular", "irregular", "unknown"];

type CycleRow = { tracking_enabled?: boolean; last_period_start?: string | null; cycle_length_days?: number | null; period_length_days?: number | null; regularity?: string };

export function cycleProfileFromRow(row: CycleRow | null | undefined): CycleProfile {
  if (!row) return emptyCycleProfile();
  return {
    trackingEnabled: row.tracking_enabled === true,
    lastPeriodStart: typeof row.last_period_start === "string" ? row.last_period_start : null,
    cycleLengthDays: typeof row.cycle_length_days === "number" ? row.cycle_length_days : null,
    periodLengthDays: typeof row.period_length_days === "number" ? row.period_length_days : null,
    regularity: REGULARITIES.find((value) => value === row.regularity) ?? "unknown",
  };
}

export function cycleProfileToRow(userId: string, profile: CycleProfile) {
  return {
    user_id: userId,
    tracking_enabled: profile.trackingEnabled,
    last_period_start: profile.lastPeriodStart,
    cycle_length_days: profile.cycleLengthDays,
    period_length_days: profile.periodLengthDays,
    regularity: profile.regularity,
  };
}

export function personalizationFromRow(row: { adaptive_enabled?: boolean; cycle_personalization_enabled?: boolean; ai_health_context_enabled?: boolean } | null | undefined): PersonalizationSettings {
  if (!row) return { ...DEFAULT_PERSONALIZATION };
  return {
    adaptiveEnabled: row.adaptive_enabled !== false,
    cycleEnabled: row.cycle_personalization_enabled === true,
    aiHealthContextEnabled: row.ai_health_context_enabled === true,
  };
}

export async function loadCycleState(request: Request): Promise<StoreResult<{ profile: CycleProfile; personalization: PersonalizationSettings }>> {
  const client = userClientFor(request);
  if (!client) return { ok: false, reason: "error" };
  try {
    const cycle = await client.from("cycle_profiles").select("tracking_enabled, last_period_start, cycle_length_days, period_length_days, regularity").maybeSingle();
    if (isMissingTable(cycle.error)) return { ok: false, reason: "unavailable" };
    if (cycle.error) return { ok: false, reason: "error" };
    const settings = await client.from("personalization_settings").select("adaptive_enabled, cycle_personalization_enabled, ai_health_context_enabled").maybeSingle();
    if (isMissingTable(settings.error)) return { ok: false, reason: "unavailable" };
    if (settings.error) return { ok: false, reason: "error" };
    return { ok: true, value: { profile: cycleProfileFromRow(cycle.data as CycleRow | null), personalization: personalizationFromRow(settings.data) } };
  } catch {
    return { ok: false, reason: "error" };
  }
}

/** Döngü profilini yazar ve kişiselleştirme bayrağını AYNI kullanıcı eylemine bağlar (açık rıza). */
export async function saveCycleProfile(request: Request, userId: string, profile: CycleProfile): Promise<StoreResult<null>> {
  const client = userClientFor(request);
  if (!client) return { ok: false, reason: "error" };
  try {
    const cycle = await client.from("cycle_profiles").upsert(cycleProfileToRow(userId, profile), { onConflict: "user_id" });
    if (isMissingTable(cycle.error)) return { ok: false, reason: "unavailable" };
    if (cycle.error) return { ok: false, reason: "error" };
    const settings = await client.from("personalization_settings").upsert({ user_id: userId, cycle_personalization_enabled: profile.trackingEnabled }, { onConflict: "user_id" });
    if (isMissingTable(settings.error)) return { ok: false, reason: "unavailable" };
    if (settings.error) return { ok: false, reason: "error" };
    return { ok: true, value: null };
  } catch {
    return { ok: false, reason: "error" };
  }
}

/** Döngü verisini GERÇEKTEN siler (satır silinir, kapatmak yetmez) ve kişiselleştirmeyi kapatır. */
export async function deleteCycleData(request: Request, userId: string): Promise<StoreResult<null>> {
  const client = userClientFor(request);
  if (!client) return { ok: false, reason: "error" };
  try {
    const removed = await client.from("cycle_profiles").delete().eq("user_id", userId);
    if (isMissingTable(removed.error)) return { ok: false, reason: "unavailable" };
    if (removed.error) return { ok: false, reason: "error" };
    const settings = await client.from("personalization_settings").upsert({ user_id: userId, cycle_personalization_enabled: false }, { onConflict: "user_id" });
    if (settings.error && !isMissingTable(settings.error)) return { ok: false, reason: "error" };
    return { ok: true, value: null };
  } catch {
    return { ok: false, reason: "error" };
  }
}

// --- Günlük check-in --------------------------------------------------------------------------------------

const CHECKIN_COLUMNS = "day, energy, sleep_quality, sleep_hours, soreness, pain, available_minutes";

export async function saveCheckin(request: Request, userId: string, checkin: Checkin): Promise<StoreResult<null>> {
  const client = userClientFor(request);
  if (!client) return { ok: false, reason: "error" };
  try {
    const { error } = await client.from("daily_checkins").upsert(checkinToRow(userId, checkin), { onConflict: "user_id,day" });
    if (isMissingTable(error)) return { ok: false, reason: "unavailable" };
    if (error) return { ok: false, reason: "error" };
    return { ok: true, value: null };
  } catch {
    return { ok: false, reason: "error" };
  }
}

/** `sinceDay` (dahil) sonrası check-in'ler, yeniden eskiye. Katman geçmiş sınırı çağıran tarafta hesaplanır. */
export async function loadCheckins(request: Request, sinceDay: string, limit = 400): Promise<StoreResult<Checkin[]>> {
  const client = userClientFor(request);
  if (!client) return { ok: false, reason: "error" };
  try {
    const { data, error } = await client.from("daily_checkins").select(CHECKIN_COLUMNS).gte("day", sinceDay).order("day", { ascending: false }).limit(limit);
    if (isMissingTable(error)) return { ok: false, reason: "unavailable" };
    if (error || !Array.isArray(data)) return { ok: false, reason: "error" };
    return { ok: true, value: (data as Parameters<typeof checkinFromRow>[0][]).map(checkinFromRow).filter((item): item is Checkin => item !== null) };
  } catch {
    return { ok: false, reason: "error" };
  }
}

/** Check-in verisini GERÇEKTEN siler: tek gün (`day`) ya da hepsi. */
export async function deleteCheckins(request: Request, userId: string, day?: string): Promise<StoreResult<null>> {
  const client = userClientFor(request);
  if (!client) return { ok: false, reason: "error" };
  try {
    const query = client.from("daily_checkins").delete().eq("user_id", userId);
    const { error } = await (day ? query.eq("day", day) : query);
    if (isMissingTable(error)) return { ok: false, reason: "unavailable" };
    if (error) return { ok: false, reason: "error" };
    return { ok: true, value: null };
  } catch {
    return { ok: false, reason: "error" };
  }
}

// --- Kişiselleştirme ayarları ve toplu silme -----------------------------------------------------------

/** Yalnızca verilen bayrakları yazar (kısmi güncelleme); boş gövde hiçbir şey yazmaz. */
export async function savePersonalization(request: Request, userId: string, patch: Partial<PersonalizationSettings>): Promise<StoreResult<null>> {
  const client = userClientFor(request);
  if (!client) return { ok: false, reason: "error" };
  const row: Record<string, unknown> = { user_id: userId };
  if (typeof patch.adaptiveEnabled === "boolean") row.adaptive_enabled = patch.adaptiveEnabled;
  if (typeof patch.cycleEnabled === "boolean") row.cycle_personalization_enabled = patch.cycleEnabled;
  if (typeof patch.aiHealthContextEnabled === "boolean") row.ai_health_context_enabled = patch.aiHealthContextEnabled;
  if (Object.keys(row).length === 1) return { ok: true, value: null };
  try {
    const { error } = await client.from("personalization_settings").upsert(row, { onConflict: "user_id" });
    if (isMissingTable(error)) return { ok: false, reason: "unavailable" };
    if (error) return { ok: false, reason: "error" };
    return { ok: true, value: null };
  } catch {
    return { ok: false, reason: "error" };
  }
}

/** Tüm sağlık verisini GERÇEKTEN siler: döngü profili, tüm check-in'ler ve ayar satırı (varsayılanlara döner). */
export async function deleteAllHealthData(request: Request, userId: string): Promise<StoreResult<null>> {
  const client = userClientFor(request);
  if (!client) return { ok: false, reason: "error" };
  try {
    for (const [table, column] of [["cycle_profiles", "user_id"], ["daily_checkins", "user_id"], ["personalization_settings", "user_id"]] as const) {
      const { error } = await client.from(table).delete().eq(column, userId);
      if (isMissingTable(error)) return { ok: false, reason: "unavailable" };
      if (error) return { ok: false, reason: "error" };
    }
    return { ok: true, value: null };
  } catch {
    return { ok: false, reason: "error" };
  }
}
