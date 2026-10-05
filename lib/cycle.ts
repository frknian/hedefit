// İsteğe bağlı döngü takibi: yalnızca SAF fonksiyonlar (veritabanı, ağ ya da saat bağımlılığı yok).
//
// İLKELER
//  - Tamamen opsiyonel: takip kapalıysa ya da veri eksikse sonuç `null`; hiçbir akış bunu zorunlu kılmaz.
//  - Faz ve tahmin SAKLANMAZ, her seferinde tarihten hesaplanır (kullanıcı veriyi düzenleyince eski
//    türetilmiş değer ortada kalmaz). Bkz. supabase/migrations/20261005120000_adaptive_fitness.sql.
//  - Tıbbi/hormonal iddia yok: yalnızca kaba, açıklanabilir bir sinyal üretir. Düzensiz döngüde faz
//    TAHMİN EDİLMEZ. Antrenman kararını tek başına belirlemek bu modülün işi değildir; kullanıcının
//    check-in cevapları öncelikli girdidir (bkz. lib/training/adaptive-engine.ts).

export type CycleRegularity = "regular" | "somewhat_irregular" | "irregular" | "unknown";
export type CyclePhase = "menstrual" | "follicular" | "ovulatory" | "luteal";

export interface CycleProfile {
  trackingEnabled: boolean;
  /** YYYY-MM-DD, kullanıcının bildirdiği son adet başlangıcı. */
  lastPeriodStart: string | null;
  cycleLengthDays: number | null;
  periodLengthDays: number | null;
  regularity: CycleRegularity;
}

export const CYCLE_LIMITS = {
  cycleLengthDays: { min: 21, max: 45, fallback: 28 },
  periodLengthDays: { min: 1, max: 10, fallback: 5 },
} as const;

/** Son kaydın üzerinden bu kadar döngü geçtiyse tahmin güvenilmez; kullanıcıdan güncelleme istenir. */
const STALE_AFTER_CYCLES = 2;

export interface CycleState {
  /** 1 tabanlı döngü günü. */
  cycleDay: number;
  /** Düzensiz döngüde ya da bayat veride null: tahmin etme. */
  phase: CyclePhase | null;
  /** Adet günlerinde (yalnızca faz biliniyorsa) true. */
  periodLikely: boolean;
  /** Adetten önceki son 3 gün (yalnızca düzenli/az düzensiz döngüde). */
  preMenstrualWindow: boolean;
  nextPeriodStart: string;
  daysToNextPeriod: number;
  confidence: "high" | "low";
  /** Son kayıt çok eski: kullanıcıdan güncellemesi istenmeli. */
  stale: boolean;
}

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;
const DAY_MS = 86_400_000;

export function parseIsoDate(value: string): number | null {
  if (!ISO_DATE.test(value)) return null;
  const time = Date.parse(`${value}T00:00:00Z`);
  if (Number.isNaN(time)) return null;
  // 2026-02-31 gibi taşan günleri reddet (Date.parse bunları kaydırır).
  return new Date(time).toISOString().slice(0, 10) === value ? time : null;
}

export function toIsoDate(time: number): string {
  return new Date(time).toISOString().slice(0, 10);
}

export function emptyCycleProfile(): CycleProfile {
  return { trackingEnabled: false, lastPeriodStart: null, cycleLengthDays: null, periodLengthDays: null, regularity: "unknown" };
}

type Validation = { ok: true; value: CycleProfile } | { ok: false; error: string };

/** İstemciden gelen ham girdiyi doğrular. Takip kapalıysa kalan alanlar zorunlu değildir. */
export function validateCycleProfile(raw: unknown, todayIso: string): Validation {
  if (!raw || typeof raw !== "object") return { ok: false, error: "invalid_body" };
  const input = raw as Record<string, unknown>;
  const trackingEnabled = input.trackingEnabled === true;
  const regularity: CycleRegularity = (["regular", "somewhat_irregular", "irregular", "unknown"] as const).find((value) => value === input.regularity) ?? "unknown";

  const intField = (key: "cycleLengthDays" | "periodLengthDays"): number | null | "invalid" => {
    const value = input[key];
    if (value === undefined || value === null || value === "") return null;
    if (typeof value !== "number" || !Number.isInteger(value)) return "invalid";
    const { min, max } = CYCLE_LIMITS[key];
    return value >= min && value <= max ? value : "invalid";
  };
  const cycleLengthDays = intField("cycleLengthDays");
  const periodLengthDays = intField("periodLengthDays");
  if (cycleLengthDays === "invalid") return { ok: false, error: "invalid_cycle_length" };
  if (periodLengthDays === "invalid") return { ok: false, error: "invalid_period_length" };

  let lastPeriodStart: string | null = null;
  if (input.lastPeriodStart !== undefined && input.lastPeriodStart !== null && input.lastPeriodStart !== "") {
    if (typeof input.lastPeriodStart !== "string") return { ok: false, error: "invalid_last_period_start" };
    const time = parseIsoDate(input.lastPeriodStart);
    const today = parseIsoDate(todayIso);
    if (time === null || today === null) return { ok: false, error: "invalid_last_period_start" };
    // Bugünden en fazla 1 gün ileri (saat dilimi farkı payı), en fazla 1 yıl geri.
    if (time > today + DAY_MS || time < today - 366 * DAY_MS) return { ok: false, error: "invalid_last_period_start" };
    lastPeriodStart = input.lastPeriodStart;
  }
  if (periodLengthDays !== null && cycleLengthDays !== null && periodLengthDays >= cycleLengthDays) return { ok: false, error: "invalid_period_length" };

  return { ok: true, value: { trackingEnabled, lastPeriodStart, cycleLengthDays, periodLengthDays, regularity } };
}

/**
 * Bugünkü döngü durumu. Takip kapalıysa, tarih yoksa ya da tarih gelecekteyse `null`.
 * Çağıran taraf `null` aldığında hiçbir şeyi değiştirmemelidir (opsiyonel özellik).
 */
export function describeCycle(profile: CycleProfile | null | undefined, todayIso: string): CycleState | null {
  if (!profile || !profile.trackingEnabled || !profile.lastPeriodStart) return null;
  const start = parseIsoDate(profile.lastPeriodStart);
  const today = parseIsoDate(todayIso);
  if (start === null || today === null) return null;
  const daysSince = Math.round((today - start) / DAY_MS);
  if (daysSince < 0) return null;

  const cycleLength = profile.cycleLengthDays ?? CYCLE_LIMITS.cycleLengthDays.fallback;
  const periodLength = Math.min(profile.periodLengthDays ?? CYCLE_LIMITS.periodLengthDays.fallback, cycleLength - 1);
  const cyclesElapsed = Math.floor(daysSince / cycleLength);
  const cycleDay = (daysSince % cycleLength) + 1;
  const nextStart = start + (cyclesElapsed + 1) * cycleLength * DAY_MS;
  const daysToNextPeriod = Math.round((nextStart - today) / DAY_MS);

  const stale = cyclesElapsed >= STALE_AFTER_CYCLES;
  const irregular = profile.regularity === "irregular";
  const canEstimate = !stale && !irregular;

  let phase: CyclePhase | null = null;
  if (canEstimate) {
    const ovulationDay = cycleLength - 14;
    if (cycleDay <= periodLength) phase = "menstrual";
    else if (Math.abs(cycleDay - ovulationDay) <= 1) phase = "ovulatory";
    else if (cycleDay < ovulationDay) phase = "follicular";
    else phase = "luteal";
  }

  return {
    cycleDay,
    phase,
    periodLikely: phase === "menstrual",
    preMenstrualWindow: canEstimate && daysToNextPeriod <= 3 && daysToNextPeriod > 0,
    nextPeriodStart: toIsoDate(nextStart),
    daysToNextPeriod,
    confidence: profile.regularity === "regular" && canEstimate ? "high" : "low",
    stale,
  };
}
