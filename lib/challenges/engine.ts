// Challenge durum motoru (saf fonksiyonlar). Sunucu ve istemci aynı kuralları uygular:
//
// - İlerleme TAKVİME değil TAMAMLANAN GÜNE bağlıdır: "Gün 6 / 14" = 5 gün bitti, sıradaki 6. gün.
//   Kaçırılan gün challenge'ı bozmaz, yalnızca seriyi sıfırlar (cezalandırıcı değil).
// - Takvim günü başına en fazla bir challenge günü tamamlanır (ileri sarma yok).
// - Toparlanma günü (recovery) seriyi bozmaz ama antrenman sayılmaz; hakkı her 7 gün için 1'dir.
// - Uyarlanmış gün (adapted) normal gün gibi sayılır: hazırlık durumu yüzünden ceza yok.

import type { ChallengeTask } from "./catalog.ts";
import type { Checkin } from "../checkin.ts";
import type { SessionKind } from "../training/wellness-session.ts";

export type DayStatus = "completed" | "adapted" | "recovery";

export interface DayLog {
  dayIndex: number;
  localDate: string; // YYYY-MM-DD
  status: DayStatus;
  minutes?: number | null;
}

export interface ChallengeState {
  totalDays: number;
  doneDays: number;
  /** 1 tabanlı; tamamlandıysa totalDays. */
  currentDay: number;
  todayDone: boolean;
  streak: number;
  longestStreak: number;
  recoveryUsed: number;
  recoveryAllowance: number;
  percent: number;
  finished: boolean;
}

const DAY_MS = 86_400_000;

export function epochDay(iso: string): number | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso);
  if (!match) return null;
  const time = Date.UTC(Number(match[1]), Number(match[2]) - 1, Number(match[3]));
  return Number.isFinite(time) ? Math.round(time / DAY_MS) : null;
}

export function recoveryAllowance(totalDays: number): number {
  return Math.max(1, Math.floor(totalDays / 7));
}

/** Ardışık takvim günlerinden oluşan seriler (toparlanma günleri dahil). */
function streaks(dates: number[]): { current: number; longest: number; lastDay: number | null } {
  const sorted = [...new Set(dates)].sort((a, b) => a - b);
  let longest = 0;
  let run = 0;
  let previous: number | null = null;
  for (const day of sorted) {
    run = previous !== null && day - previous === 1 ? run + 1 : 1;
    longest = Math.max(longest, run);
    previous = day;
  }
  return { current: run, longest, lastDay: previous };
}

export function challengeState(totalDays: number, logs: DayLog[], todayIso: string): ChallengeState {
  const today = epochDay(todayIso) ?? 0;
  const valid = logs.filter((log) => epochDay(log.localDate) !== null);
  const dates = valid.map((log) => epochDay(log.localDate)!);
  const { current, longest, lastDay } = streaks(dates);
  // Seri dün ya da bugün tamamlanan günle bitiyorsa canlıdır; aksi halde sıfırdır.
  const streak = lastDay !== null && today - lastDay <= 1 ? current : 0;
  const doneDays = Math.min(totalDays, valid.length);
  const finished = doneDays >= totalDays;
  return {
    totalDays,
    doneDays,
    currentDay: finished ? totalDays : doneDays + 1,
    todayDone: dates.includes(today),
    streak,
    longestStreak: longest,
    recoveryUsed: valid.filter((log) => log.status === "recovery").length,
    recoveryAllowance: recoveryAllowance(totalDays),
    percent: totalDays > 0 ? Math.round((doneDays / totalDays) * 100) : 0,
    finished,
  };
}

/** Sıradaki görev (tamamlandıysa null). */
export function nextTask(days: ChallengeTask[], state: Pick<ChallengeState, "doneDays" | "finished">): ChallengeTask | null {
  return state.finished ? null : days[state.doneDays] ?? null;
}

export interface Adaptation {
  task: ChallengeTask;
  adapted: boolean;
  /** Kullanıcıya toparlanma günü önerilmeli mi (çok düşük hazırlık / belirgin ağrı)? */
  suggestRecovery: boolean;
  reason: "none" | "low_readiness" | "pain" | "cycle";
  message: string | null;
  /**
   * Gün tamamlanırken gönderilecek durum. "adapted" yalnızca düşük hazırlık/ağrıda: sunucu bunu o güne ait
   * check-in ile doğrular. Döngüye bağlı nazik sürüm normal "completed" sayılır (sağlık verisi doğrulamaya girmez).
   */
  completionStatus: "completed" | "adapted";
}

const roundTo = (value: number, step: number) => Math.round(value / step) * step;

/**
 * Günlük check-in'e göre bugünün görevini uyarlar. Yalnızca hareket görevleri değişir; su/öğün/check-in aynı kalır.
 * Uyarlama süreyi kısaltır ya da daha nazik bir oturuma geçer — gün yine TAM sayılır.
 */
export function adaptTask(task: ChallengeTask, checkin: Checkin | null, options: { enabled?: boolean; periodLikely?: boolean; locale?: "tr" | "en" } = {}): Adaptation {
  const locale = options.locale === "en" ? "en" : "tr";
  const unchanged: Adaptation = { task, adapted: false, suggestRecovery: false, reason: "none", message: null, completionStatus: "completed" };
  if (options.enabled === false) return unchanged;
  const movement = task.kind === "session" || task.kind === "workout" || task.kind === "steps";
  if (!movement) return unchanged;

  const pain = (checkin?.pain ?? 0) >= 6;
  const low = !!checkin && (checkin.energy <= 4 || checkin.sleepQuality <= 4 || checkin.soreness >= 7);
  const cycle = options.periodLikely === true;
  if (!pain && !low && !cycle) return unchanged;

  const suggestRecovery = !!checkin && (checkin.energy <= 2 || checkin.pain >= 8);
  const factor = pain || low ? 0.55 : 0.7;
  const reason: Adaptation["reason"] = pain ? "pain" : low ? "low_readiness" : "cycle";

  if (task.kind === "steps") {
    if (reason === "cycle") return unchanged;
    const target = Math.max(3_000, roundTo((task.target ?? 8_000) * 0.6, 500));
    const message = locale === "en"
      ? `I lowered today's step goal to ${target.toLocaleString("en-US")} based on your recovery. It still counts in full.`
      : `Toparlanma durumuna göre bugünkü adım hedefini ${target.toLocaleString("tr-TR")} adıma indirdim. Gün yine tam sayılır.`;
    return { task: { ...task, target }, adapted: true, suggestRecovery, reason, message, completionStatus: "adapted" };
  }

  const baseMinutes = task.minutes ?? (task.kind === "workout" ? 30 : 15);
  const minutes = Math.max(8, Math.round(baseMinutes * factor));
  // Ağrı varsa ya da görev kuvvet odaklıysa daha nazik bir oturuma geç; programlı antrenman günü toparlanma oturumu olur.
  const strengthKinds = new Set<SessionKind>(["band_strength", "home_strength", "core_focus"]);
  let session: SessionKind | undefined = task.session;
  if (task.kind === "workout") session = pain ? "posture_mobility" : "low_impact_recovery";
  else if (pain && session && strengthKinds.has(session)) session = "posture_mobility";
  else if (cycle && session && strengthKinds.has(session)) session = "pilates_today";
  const adaptedTask: ChallengeTask = { kind: "session", session, minutes };
  const message = locale === "en"
    ? (reason === "cycle" ? `I prepared a gentler ${minutes}-minute version for today.` : `I adapted today's workout to ${minutes} minutes based on your recovery.`)
    : (reason === "cycle" ? `Bugün için daha nazik, ${minutes} dakikalık bir sürüm hazırladım.` : `Bugünkü antrenmanı toparlanma durumuna göre ${minutes} dakikaya uyarladım.`);
  return { task: adaptedTask, adapted: true, suggestRecovery, reason, message, completionStatus: reason === "cycle" ? "completed" : "adapted" };
}
