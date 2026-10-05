// Adaptive Training Engine: bugünün planını, kullanıcının O GÜNKÜ durumuna göre uyarlar; programı ÇÖPE ATMAZ.
//
// GİRDİLER: profil, bugünkü check-in (enerji, uyku, kas ağrısı, ağrı, müsait süre), antrenman geçmişi (son günlerdeki
// seans sayısı), ekipman (profil), katman ve isteğe bağlı döngü bilgisi.
//
// TEMEL İLKELER
//  1. Check-in cevapları ÖNCELİKLİ girdidir. Puan yalnızca onlardan hesaplanır.
//  2. Döngü tek başına karar VERMEZ. Yalnızca puan "gri bölgedeyken" (iyi ile orta arası) küçük bir etken olabilir;
//     iyi bir gün (yüksek enerji, iyi uyku, ağrı yok) adet döneminde bile NORMAL antrenmanla devam eder.
//  3. Ağrı güvenlik girdisidir: yüksek ağrıda (≥7) yük azaltılır ve toparlanmaya geçilir.
//  4. Eylemler: kısalt, yoğunluğu azalt, hareket değiştir, mobilite ekle, toparlanmaya / Pilates'e geç.
//     Katmanın izin vermediği eylem UYGULANMAZ; en yakın izinli eylem uygulanır ve kilitli olan `lockedActions`'ta bildirilir.
//  5. Tıbbi iddia yok; açıklamalar yalnızca kullanıcının kendi cevaplarını yansıtır.

import type { Checkin } from "../checkin.ts";
import type { CycleState } from "../cycle.ts";
import { canUseAdaptiveAction, type AdaptiveAction, type PlanTier } from "../entitlements.ts";
import { getExerciseById } from "../exercise-service.ts";
import { translateExerciseName } from "../exercise-translations.ts";
import { replaceExercise } from "./exercise-replacer.ts";
import { adaptWorkoutPlanOnTheFly } from "./plan-adapter.ts";
import type { WorkoutExerciseItem } from "./readiness-adapter.ts";
import type { TrainingProfile } from "./types.ts";
import { buildWellnessSession, pickMobilityFinisher, type WellnessKind } from "./wellness-session.ts";

export type AdaptiveLevel = "good" | "moderate" | "low" | "recovery";

export interface AdaptiveInput {
  profile: TrainingProfile;
  exercises: WorkoutExerciseItem[];
  checkin: Checkin | null;
  /** Yalnızca kullanıcı etkinleştirdiyse VE katman izin veriyorsa çağıran taraf verir; aksi halde null. */
  cycle?: CycleState | null;
  tier: PlanTier;
  adaptiveEnabled?: boolean;
  /** Son 3 günde tamamlanan seans sayısı (antrenman yükü bağlamı). */
  recentSessions3d?: number;
  locale?: "tr" | "en";
  seed: string;
}

export interface AppliedAction { action: AdaptiveAction; detailTr: string; detailEn: string }

export interface AdaptiveResult {
  adapted: boolean;
  level: AdaptiveLevel;
  /** 0–100; yalnızca check-in cevaplarından (döngü hariç) hesaplanır, gri bölgede küçük bir bağlam düzeltmesi alabilir. */
  score: number;
  intensity: "normal" | "reduced" | "recovery";
  applied: AppliedAction[];
  /** İstenen ama katmanda kilitli eylemler (istemci yükseltme önerisi gösterir). */
  lockedActions: AdaptiveAction[];
  exercises: WorkoutExerciseItem[];
  originalExercises: WorkoutExerciseItem[];
  estimatedMinutes: number;
  wellnessKind: WellnessKind | null;
  signals: { checkinUsed: boolean; reasons: string[]; cycle: "none" | "context" | "nudged"; trainingLoad: boolean };
  explanationTr: string;
  explanationEn: string;
}

const estimate = (items: WorkoutExerciseItem[]) => Math.round(items.reduce((sum, item) => sum + item.sets * (45 + item.restSeconds), 0) / 60);

/** Check-in'den 0–100 hazırlık puanı. Ağırlıklar: enerji 35, uyku 25, ağrı 25, kas ağrısı 15. */
export function readinessScore(checkin: Checkin): number {
  const part = (value: number, min: number, max: number) => (value - min) / (max - min);
  return Math.round(
    part(checkin.energy, 1, 10) * 35 + part(checkin.sleepQuality, 1, 10) * 25 + (1 - checkin.pain / 10) * 25 + (1 - checkin.soreness / 10) * 15,
  );
}

export const LEVEL_THRESHOLDS = { good: 70, moderate: 48, painRecovery: 7, cycleGreyZone: [55, 72] as const, cycleNudge: 6 };

const scaleVolume = (items: WorkoutExerciseItem[], factor: number, restBonus: number): WorkoutExerciseItem[] =>
  items.map((item) => ({ ...item, sets: Math.max(1, Math.round(item.sets * factor)), restSeconds: item.restSeconds + restBonus }));

export function adaptTodaysPlan(input: AdaptiveInput): AdaptiveResult {
  const locale = input.locale === "en" ? "en" : "tr";
  const original = input.exercises;
  const base: AdaptiveResult = {
    adapted: false, level: "good", score: 100, intensity: "normal", applied: [], lockedActions: [], exercises: original, originalExercises: original,
    estimatedMinutes: estimate(original), wellnessKind: null, signals: { checkinUsed: false, reasons: [], cycle: "none", trainingLoad: false },
    explanationTr: "", explanationEn: "",
  };
  if (input.adaptiveEnabled === false || !input.checkin || original.length === 0) {
    return { ...base, explanationTr: input.checkin ? "Uyarlama kapalı: planın aynen geçerli." : "Bugünkü check-in yok: planın aynen geçerli.", explanationEn: input.checkin ? "Adaptation is off: your plan stays as written." : "No check-in today: your plan stays as written." };
  }

  const checkin = input.checkin;
  let score = readinessScore(checkin);
  const reasons: string[] = [];
  if (checkin.energy <= 4) reasons.push("low_energy");
  if (checkin.sleepQuality <= 4) reasons.push("poor_sleep");
  if (checkin.soreness >= 6) reasons.push("sore");
  if (checkin.pain >= 4) reasons.push("pain");

  // Döngü: yalnızca puan gri bölgedeyse ve adet dönemi/öncesindeyse küçük bir bağlam düzeltmesi. Aksi halde yalnızca bilgi.
  let cycleSignal: AdaptiveResult["signals"]["cycle"] = "none";
  if (input.cycle) {
    cycleSignal = "context";
    const [low, high] = LEVEL_THRESHOLDS.cycleGreyZone;
    if ((input.cycle.periodLikely || input.cycle.preMenstrualWindow) && score >= low && score < high) { score -= LEVEL_THRESHOLDS.cycleNudge; cycleSignal = "nudged"; }
  }

  let level: AdaptiveLevel = checkin.pain >= LEVEL_THRESHOLDS.painRecovery ? "recovery" : score >= LEVEL_THRESHOLDS.good ? "good" : score >= LEVEL_THRESHOLDS.moderate ? "moderate" : "low";
  const trainingLoad = (input.recentSessions3d ?? 0) >= 3;
  if (trainingLoad && level === "good") { level = "moderate"; reasons.push("training_load"); }

  const tier = input.tier;
  const locked: AdaptiveAction[] = [];
  const applied: AppliedAction[] = [];
  const want = (action: AdaptiveAction) => { const ok = canUseAdaptiveAction(tier, action); if (!ok && !locked.includes(action)) locked.push(action); return ok; };

  let exercises = original;
  let intensity: AdaptiveResult["intensity"] = "normal";
  let wellnessKind: WellnessKind | null = null;
  const minutes = checkin.availableMinutes;
  const wellnessMinutes = Math.max(12, Math.min(30, minutes ?? 20));

  const switchTo = (kind: WellnessKind, action: AdaptiveAction, detailTr: string, detailEn: string) => {
    const session = buildWellnessSession({ kind, minutes: wellnessMinutes, level: input.profile.fitnessLevel, tier, seed: input.seed, locale });
    exercises = session.exercises; wellnessKind = kind; intensity = "recovery";
    applied.push({ action, detailTr: `${wellnessMinutes} dakikalık ${detailTr}`, detailEn: `${wellnessMinutes}-minute ${detailEn}` });
  };

  if (level === "recovery" || level === "low") {
    const painLow = checkin.pain < 4;
    if (level === "low" && painLow && want("switch_pilates")) switchTo("pilates_today", "switch_pilates", "düşük yoğunluklu Pilates oturumu", "low-intensity Pilates session");
    else if (want("switch_recovery")) switchTo("low_impact_recovery", "switch_recovery", "düşük etkili toparlanma oturumu", "low-impact recovery session");
    else {
      // Free: plan korunur, yük belirgin azaltılır (yüksek ağrıda daha çok).
      exercises = scaleVolume(original, level === "recovery" ? 0.6 : 0.67, level === "recovery" ? 30 : 30);
      intensity = level === "recovery" ? "recovery" : "reduced";
      applied.push({ action: "reduce_intensity", detailTr: `set sayısı yaklaşık %${level === "recovery" ? 40 : 33} azaltıldı, dinlenmeler uzatıldı`, detailEn: `sets reduced by about ${level === "recovery" ? 40 : 33}% and rest extended` });
    }
  } else if (level === "moderate") {
    intensity = "reduced";
    exercises = scaleVolume(original, 0.8, 15);
    applied.push({ action: "reduce_intensity", detailTr: "set sayısı yaklaşık %20 azaltıldı, dinlenmeler biraz uzatıldı", detailEn: "sets reduced by about 20% with slightly longer rest" });
    if (checkin.pain >= 4 && want("replace_exercises")) {
      const ids = exercises.map((item) => item.id);
      const swapped: string[] = [];
      exercises = exercises.map((item) => {
        if (swapped.length >= 2) return item;
        const result = replaceExercise(item.id, "pain_discomfort", input.profile, ids);
        const replacement = result ? getExerciseById(result.replacementExercise.id) : null;
        if (!result || !replacement || replacement.id === item.id) return item;
        swapped.push(item.name);
        return { ...item, id: replacement.id, name: translateExerciseName(replacement.name, locale), english: replacement.name, sets: Math.min(item.sets, result.sets), reps: result.reps, restSeconds: Math.max(item.restSeconds, result.restSeconds) };
      });
      if (swapped.length) applied.push({ action: "replace_exercises", detailTr: `${swapped.length} hareket daha rahat alternatifle değiştirildi`, detailEn: `${swapped.length} exercise(s) swapped for gentler alternatives` });
    } else if (checkin.pain >= 4) want("replace_exercises");
    if (want("add_mobility")) {
      const finisher = pickMobilityFinisher({ count: 2, tier, seed: input.seed, locale, level: input.profile.fitnessLevel });
      if (finisher.length) { exercises = [...exercises, ...finisher]; applied.push({ action: "add_mobility", detailTr: "sona kısa bir mobilite bitirişi eklendi", detailEn: "a short mobility finisher was added" }); }
    }
  }

  // Süre: müsait süre planın süresinden belirgin kısaysa (Free dahil herkes için açık).
  const plannedMinutes = estimate(exercises);
  if (!wellnessKind && minutes !== null && minutes + 5 <= plannedMinutes && want("shorten")) {
    const shortened = adaptWorkoutPlanOnTheFly(exercises, { trigger: "time_shortage", targetMinutes: minutes }, input.profile, locale);
    if (shortened.adaptedExercises.length) {
      exercises = shortened.adaptedExercises;
      applied.push({ action: "shorten", detailTr: `plan yaklaşık ${minutes} dakikaya kısaltıldı`, detailEn: `plan shortened to about ${minutes} minutes` });
    }
  }

  const adapted = applied.length > 0;
  const explanation = explain({ level, reasons, applied, cycle: cycleSignal, trainingLoad, wellnessKind });
  return {
    adapted, level, score, intensity, applied, lockedActions: locked.filter((action) => !applied.some((entry) => entry.action === action)), exercises, originalExercises: original,
    estimatedMinutes: estimate(exercises), wellnessKind,
    signals: { checkinUsed: true, reasons, cycle: cycleSignal, trainingLoad }, explanationTr: explanation.tr, explanationEn: explanation.en,
  };
}

const REASON_TR: Record<string, string> = { low_energy: "enerjin düşük", poor_sleep: "uykun zayıf", sore: "kasların ağrılı", pain: "ağrı bildirdin", training_load: "son günlerde yoğun antrenman yaptın" };
const REASON_EN: Record<string, string> = { low_energy: "your energy is low", poor_sleep: "your sleep was poor", sore: "your muscles are sore", pain: "you reported pain", training_load: "you trained hard in the last few days" };
const list = (items: string[], and: string) => (items.length <= 1 ? items.join("") : `${items.slice(0, -1).join(", ")} ${and} ${items.at(-1)}`);

function explain(input: { level: AdaptiveLevel; reasons: string[]; applied: AppliedAction[]; cycle: AdaptiveResult["signals"]["cycle"]; trainingLoad: boolean; wellnessKind: WellnessKind | null }) {
  if (input.applied.length === 0) {
    return { tr: "Bugünkü check-in'in iyi görünüyor: planını aynen uygulayabilirsin.", en: "Your check-in looks good today: you can follow your plan as written." };
  }
  const why = input.reasons.length ? input.reasons : [];
  const whyTr = why.length ? `Bugün ${list(why.map((r) => REASON_TR[r] ?? r), "ve")}.` : "Bugünkü durumuna göre planı hafiflettik.";
  const whyEn = why.length ? `Today ${list(why.map((r) => REASON_EN[r] ?? r), "and")}.` : "We eased the plan to fit how you feel today.";
  const doTr = input.wellnessKind
    ? `Programını tamamen çöpe atmak yerine bugünlük ${input.applied.map((a) => a.detailTr).join(", ")} uyarladık.`
    : `Programını korudum: ${input.applied.map((a) => a.detailTr).join("; ")}.`;
  const doEn = input.wellnessKind
    ? `Instead of scrapping your program, we adapted today into a ${input.applied.map((a) => a.detailEn).join(", ")}.`
    : `I kept your program and: ${input.applied.map((a) => a.detailEn).join("; ")}.`;
  const cycleTr = input.cycle === "nudged" ? " Döngü bilgin yalnızca küçük bir bağlam olarak dikkate alındı; asıl belirleyici check-in cevapların." : "";
  const cycleEn = input.cycle === "nudged" ? " Your cycle information was only a small piece of context; your own check-in answers decide." : "";
  const safetyTr = input.level === "recovery" ? " Ağrı devam ederse antrenmanı bırak ve gerekiyorsa bir sağlık profesyoneline danış." : "";
  const safetyEn = input.level === "recovery" ? " If the pain continues, stop training and consider talking to a health professional." : "";
  return { tr: `${whyTr} ${doTr}${cycleTr}${safetyTr}`, en: `${whyEn} ${doEn}${cycleEn}${safetyEn}` };
}
