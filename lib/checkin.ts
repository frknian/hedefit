// Günlük check-in: bilerek kısa (enerji, uyku, kas ağrısı, ağrı, müsait süre). Döngü bilgisi burada TUTULMAZ;
// yalnızca kullanıcı etkinleştirdiyse cycle_profiles'tan okunur (bkz. lib/cycle.ts).
//
// Saf fonksiyonlar: doğrulama, satır eşlemesi, mevcut hazırlık adaptörüne dönüşüm ve trend.

import type { DailyReadinessCheckin } from "./training/readiness-adapter.ts";
import { parseIsoDate } from "./cycle.ts";

export interface Checkin {
  /** YYYY-MM-DD (kullanıcının yerel günü). */
  day: string;
  energy: number; // 1–10
  sleepQuality: number; // 1–10
  sleepHours: number | null;
  soreness: number; // 0–10
  pain: number; // 0–10
  availableMinutes: number | null;
}

type Validation = { ok: true; value: Checkin } | { ok: false; error: string };

const DAY_MS = 86_400_000;
const int = (value: unknown, min: number, max: number): number | null => (typeof value === "number" && Number.isInteger(value) && value >= min && value <= max ? value : null);

/** Gün: bugünden en fazla 1 gün ileri/geri (saat dilimi payı). Geçmişe dönük toplu yazım yoktur. */
export function validateCheckin(raw: unknown, todayIso: string): Validation {
  if (!raw || typeof raw !== "object") return { ok: false, error: "invalid_body" };
  const input = raw as Record<string, unknown>;
  const day = typeof input.day === "string" ? input.day : todayIso;
  const dayTime = parseIsoDate(day);
  const today = parseIsoDate(todayIso);
  if (dayTime === null || today === null || Math.abs(dayTime - today) > DAY_MS) return { ok: false, error: "invalid_day" };
  const energy = int(input.energy, 1, 10);
  const sleepQuality = int(input.sleepQuality, 1, 10);
  const soreness = input.soreness === undefined ? 0 : int(input.soreness, 0, 10);
  const pain = input.pain === undefined ? 0 : int(input.pain, 0, 10);
  if (energy === null) return { ok: false, error: "invalid_energy" };
  if (sleepQuality === null) return { ok: false, error: "invalid_sleep" };
  if (soreness === null) return { ok: false, error: "invalid_soreness" };
  if (pain === null) return { ok: false, error: "invalid_pain" };
  let sleepHours: number | null = null;
  if (input.sleepHours !== undefined && input.sleepHours !== null) {
    if (typeof input.sleepHours !== "number" || !Number.isFinite(input.sleepHours) || input.sleepHours < 0 || input.sleepHours > 24) return { ok: false, error: "invalid_sleep_hours" };
    sleepHours = Math.round(input.sleepHours * 10) / 10;
  }
  let availableMinutes: number | null = null;
  if (input.availableMinutes !== undefined && input.availableMinutes !== null) {
    availableMinutes = int(input.availableMinutes, 5, 240);
    if (availableMinutes === null) return { ok: false, error: "invalid_minutes" };
  }
  return { ok: true, value: { day, energy, sleepQuality, sleepHours, soreness, pain, availableMinutes } };
}

type Row = { day?: string; energy?: number; sleep_quality?: number; sleep_hours?: number | string | null; soreness?: number; pain?: number; available_minutes?: number | null };

export function checkinFromRow(row: Row): Checkin | null {
  if (typeof row.day !== "string" || typeof row.energy !== "number" || typeof row.sleep_quality !== "number") return null;
  const hours = row.sleep_hours === null || row.sleep_hours === undefined ? null : Number(row.sleep_hours);
  return {
    day: row.day, energy: row.energy, sleepQuality: row.sleep_quality,
    sleepHours: hours !== null && Number.isFinite(hours) ? hours : null,
    soreness: row.soreness ?? 0, pain: row.pain ?? 0, availableMinutes: row.available_minutes ?? null,
  };
}

export const checkinToRow = (userId: string, checkin: Checkin) => ({
  user_id: userId, day: checkin.day, energy: checkin.energy, sleep_quality: checkin.sleepQuality, sleep_hours: checkin.sleepHours,
  soreness: checkin.soreness, pain: checkin.pain, available_minutes: checkin.availableMinutes,
});

/**
 * Kısa check-in'i mevcut hazırlık adaptörünün girdisine çevirir. Yorgunluk, enerji ve kas ağrısından türetilir;
 * rahatsızlık düzeyi ağrıdır (adaptör en az 1 bekler). Kas bölgesi sorulmaz: ağrı bölgesi sakatlık profilinden gelir.
 */
export function readinessFromCheckin(checkin: Checkin): DailyReadinessCheckin {
  const fatigue = Math.max(1, Math.min(10, Math.round((11 - checkin.energy) * 0.6 + checkin.soreness * 0.4)));
  return {
    energy: checkin.energy,
    sleepQuality: checkin.sleepQuality,
    fatigue,
    hasSoreness: checkin.soreness >= 3,
    sorenessAreas: [],
    discomfortLevel: Math.max(1, checkin.pain),
  };
}

export interface CheckinTrend {
  days: number;
  averageEnergy7: number | null;
  averageSleep7: number | null;
  averageEnergyPrevious7: number | null;
  direction: "up" | "down" | "flat" | "unknown";
  lowEnergyStreak: number;
}

const avg = (values: number[]) => (values.length ? Math.round((values.reduce((a, b) => a + b, 0) / values.length) * 10) / 10 : null);

/** Son 7 gün ve öncesindeki 7 günün karşılaştırması (Premium). Yetersiz veride yön "unknown". */
export function summarizeTrend(checkins: Checkin[], todayIso: string): CheckinTrend {
  const today = parseIsoDate(todayIso) ?? 0;
  const age = (checkin: Checkin) => Math.round((today - (parseIsoDate(checkin.day) ?? today)) / DAY_MS);
  const recent = checkins.filter((checkin) => age(checkin) >= 0 && age(checkin) < 7);
  const previous = checkins.filter((checkin) => age(checkin) >= 7 && age(checkin) < 14);
  const a = avg(recent.map((checkin) => checkin.energy));
  const b = avg(previous.map((checkin) => checkin.energy));
  const direction = a === null || b === null || recent.length < 3 || previous.length < 3 ? "unknown" : a - b >= 0.8 ? "up" : b - a >= 0.8 ? "down" : "flat";
  const sorted = checkins.slice().sort((x, y) => (parseIsoDate(y.day) ?? 0) - (parseIsoDate(x.day) ?? 0));
  let lowEnergyStreak = 0;
  for (const checkin of sorted) { if (checkin.energy <= 4) lowEnergyStreak += 1; else break; }
  return { days: checkins.length, averageEnergy7: a, averageSleep7: avg(recent.map((checkin) => checkin.sleepQuality)), averageEnergyPrevious7: b, direction, lowEnergyStreak };
}
