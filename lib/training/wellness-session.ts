// Wellness oturum oluşturucu: Pilates, düşük etkili toparlanma, duruş ve mobilite oturumlarını DETERMİNİSTİK üretir
// (AI çağrısı yok). Adaptive Engine (adapt route'u) bunu "Pilates'e geç", "mobilite ekle" ve "toparlanmaya geç"
// eylemleri için kullanır.
//
// Cinsiyete kilitli değildir: herkes için aynı içerik; kadınlara önerilerde yalnızca daha görünür sunulur (istemci).

import type { Exercise } from "../../types/exercise";
import { canUseModalityExercise, type PlanTier } from "../entitlements.ts";
import { MODALITY_LABELS, type Modality } from "../exercise-modality.ts";
import { getAllExercises } from "../exercise-service.ts";
import { translateExerciseName } from "../exercise-translations.ts";
import type { WorkoutExerciseItem } from "./readiness-adapter.ts";

export type WellnessKind = "pilates_today" | "low_impact_recovery" | "posture_mobility";
export type WellnessLevel = "beginner" | "intermediate" | "advanced";

export interface WellnessSession {
  kind: WellnessKind;
  title: string;
  subtitle: string;
  requestedMinutes: number;
  estimatedMinutes: number;
  exercises: WorkoutExerciseItem[];
}

interface Segment { modalities: Modality[]; subs: string[] | null; weight: number }

/** Her oturum türü için sıralı bölümler: ısınma → ana bölüm → soğuma. `weight` fazladan hareketlerin dağıtımı içindir. */
const TEMPLATES: Record<WellnessKind, Segment[]> = {
  pilates_today: [
    { modalities: ["pilates"], subs: ["beginner", "short", "posture"], weight: 0 },
    { modalities: ["pilates"], subs: ["core"], weight: 3 },
    { modalities: ["pilates"], subs: ["lower_body"], weight: 2 },
    { modalities: ["pilates"], subs: ["posture", "upper_body", "full_body"], weight: 1 },
    { modalities: ["recovery", "mobility"], subs: ["full_body", "lower_body", "back"], weight: 0 },
  ],
  low_impact_recovery: [
    { modalities: ["low_impact"], subs: ["beginner", "cardio"], weight: 1 },
    { modalities: ["low_impact"], subs: ["full_body", "recovery", "beginner"], weight: 3 },
    { modalities: ["recovery"], subs: ["full_body", "lower_body"], weight: 2 },
    { modalities: ["recovery"], subs: ["breathing", "full_body", "upper_body"], weight: 0 },
  ],
  posture_mobility: [
    { modalities: ["mobility"], subs: ["back", "morning"], weight: 2 },
    { modalities: ["mobility"], subs: ["shoulder"], weight: 1 },
    { modalities: ["mobility"], subs: ["hip"], weight: 2 },
    { modalities: ["pilates"], subs: ["posture"], weight: 1 },
    { modalities: ["recovery"], subs: ["breathing", "upper_body", "full_body"], weight: 0 },
  ],
};

const TITLES: Record<WellnessKind, { tr: [string, string]; en: [string, string] }> = {
  pilates_today: { tr: ["Bugün için Pilates", "Kontrollü hareket, güçlü core"], en: ["Pilates for Today", "Controlled movement, a stronger core"] },
  low_impact_recovery: { tr: ["Düşük Etkili Toparlanma", "Eklem dostu hareket ve yumuşak esneme"], en: ["Low Impact Recovery", "Joint-friendly movement and gentle stretching"] },
  posture_mobility: { tr: ["Duruş ve Mobilite", "Sırt, omuz ve kalçayı aç"], en: ["Posture & Mobility", "Open up your back, shoulders and hips"] },
};

const AREA_TR: Record<string, string> = { core: "Core", upper_legs: "Bacak", lower_legs: "Bacak", back: "Sırt", shoulders: "Omuz", chest: "Göğüs", upper_arms: "Kol", lower_arms: "Kol", full_body: "Tüm vücut" };

const LEVEL_RANK: Record<string, number> = { beginner: 0, intermediate: 1, advanced: 2 };

/** Küçük, deterministik karıştırma anahtarı (FNV-1a): aynı tohum aynı sırayı verir, gün değişince sıra değişir. */
function hash(value: string): number {
  let h = 2166136261;
  for (let i = 0; i < value.length; i += 1) { h ^= value.charCodeAt(i); h = Math.imul(h, 16777619); }
  return h >>> 0;
}

function isHold(exercise: Exercise): boolean {
  return exercise.category === "stretching" || exercise.force === "static" || (exercise.modalities ?? []).includes("recovery");
}

export function wellnessMinutesToCount(minutes: number): number {
  return Math.max(4, Math.min(9, Math.round(minutes / 3)));
}

function prescribe(exercise: Exercise, locale: "tr" | "en", level: WellnessLevel) {
  const hold = isHold(exercise);
  const timed = hold || exercise.category === "cardio" || (exercise.modalities ?? []).includes("low_impact");
  const sets = hold ? 1 : level === "beginner" ? 2 : 3;
  const reps = timed
    ? (hold ? (locale === "en" ? "30–45 sec" : "30–45 sn") : (locale === "en" ? "40 sec" : "40 sn"))
    : (locale === "en" ? "8–10 reps" : "8–10 tekrar");
  const restSeconds = hold ? 15 : 25;
  const seconds = timed ? (hold ? 40 : 40) : 35;
  return { sets, reps, restSeconds, seconds };
}

export function buildWellnessSession(input: {
  kind: WellnessKind;
  minutes: number;
  level?: WellnessLevel;
  tier?: PlanTier;
  seed: string;
  locale?: "tr" | "en";
  catalog?: Exercise[];
}): WellnessSession {
  const locale = input.locale === "en" ? "en" : "tr";
  const level = input.level ?? "beginner";
  const tier = input.tier ?? "free";
  const minutes = Math.max(8, Math.min(45, Math.round(input.minutes || 20)));
  const target = wellnessMinutesToCount(minutes);
  const template = TEMPLATES[input.kind];
  const catalog = (input.catalog ?? getAllExercises()).filter((exercise) => exercise.isActive !== false && exercise.mediaStatus !== "missing" && (exercise.modalities?.length ?? 0) > 0);
  const maxRank = LEVEL_RANK[level] ?? 0;
  const allowed = (exercise: Exercise, strictLevel: boolean) =>
    canUseModalityExercise(tier, exercise.modalities, exercise.subcategories) && (!strictLevel || (LEVEL_RANK[exercise.level] ?? 0) <= maxRank);

  // Bölüm başına hedef sayı: her bölüm bir hareket + kalan hareketler ağırlığa göre.
  let counts = template.map(() => 1);
  if (target < template.length) {
    // Kısa oturum: ısınma (ilk) ve soğuma (son) kalır, aradakilerden en ağırlıklılar seçilir.
    const middle = template.map((segment, index) => ({ index, weight: segment.weight })).slice(1, -1).sort((a, b) => b.weight - a.weight);
    const keep = new Set([0, template.length - 1, ...middle.map((entry) => entry.index)].slice(0, target));
    counts = template.map((_, index) => (keep.has(index) ? 1 : 0));
  }
  let extras = Math.max(0, target - template.length);
  const weighted = template.map((segment, index) => ({ index, weight: segment.weight })).filter((entry) => entry.weight > 0);
  const totalWeight = weighted.reduce((sum, entry) => sum + entry.weight, 0) || 1;
  for (const entry of weighted) { const share = Math.floor((extras * entry.weight) / totalWeight); counts[entry.index] += share; }
  extras -= weighted.reduce((sum, entry) => sum + Math.floor((Math.max(0, target - template.length) * entry.weight) / totalWeight), 0);
  for (let i = 0; extras > 0 && weighted.length; i = (i + 1) % weighted.length) { counts[weighted[i].index] += 1; extras -= 1; }

  const chosen: Exercise[] = [];
  const used = new Set<string>();
  const order = (list: Exercise[]) => list.slice().sort((a, b) => hash(`${input.seed}:${input.kind}:${a.id}`) - hash(`${input.seed}:${input.kind}:${b.id}`));
  const pick = (segment: Segment, count: number) => {
    const matches = (exercise: Exercise) => !used.has(exercise.id) && exercise.modalities!.some((modality) => segment.modalities.includes(modality)) && (!segment.subs || (exercise.subcategories ?? []).some((sub) => segment.subs!.includes(sub)));
    // Önce seviyeye uygun, yetmezse seviyeyi gevşet (yalnızca erişilebilir içerik).
    for (const strict of [true, false]) {
      const pool = order(catalog.filter((exercise) => matches(exercise) && allowed(exercise, strict)));
      for (const exercise of pool) { if (count <= 0) break; chosen.push(exercise); used.add(exercise.id); count -= 1; }
      if (count <= 0) break;
    }
    return count;
  };
  let missing = 0;
  template.forEach((segment, index) => { missing += pick(segment, counts[index]); });
  if (missing > 0) {
    // Eksik kalan yerler aynı türün genel havuzundan tamamlanır.
    const union: Segment = { modalities: [...new Set(template.flatMap((segment) => segment.modalities))], subs: null, weight: 0 };
    pick(union, missing);
  }

  const exercises: WorkoutExerciseItem[] = chosen.map((exercise) => {
    const dose = prescribe(exercise, locale, level);
    const steps = locale === "en" ? exercise.instructions : exercise.instructionsTr?.length ? exercise.instructionsTr : exercise.instructions;
    return {
      id: exercise.id,
      name: translateExerciseName(exercise.name, locale),
      english: exercise.name,
      area: AREA_TR[exercise.bodyPart ?? ""] ?? "Tüm vücut",
      sets: dose.sets,
      reps: dose.reps,
      restSeconds: dose.restSeconds,
      instructions: steps.slice(0, 2).join(" "),
    };
  });
  const seconds = chosen.reduce((sum, exercise) => { const dose = prescribe(exercise, locale, level); return sum + dose.sets * (dose.seconds + dose.restSeconds); }, 0);
  const [title, subtitle] = TITLES[input.kind][locale];
  return { kind: input.kind, title, subtitle, requestedMinutes: minutes, estimatedMinutes: Math.max(5, Math.round(seconds / 60)), exercises };
}

/** Oturumdaki modalitelerin okunur özeti (ör. "Pilates · Toparlanma"); başlık altı bilgisi için. */
export function wellnessModalitySummary(session: WellnessSession, catalog: Exercise[] = getAllExercises(), locale: "tr" | "en" = "tr"): string {
  const byId = new Map(catalog.map((exercise) => [exercise.id, exercise]));
  const modalities = [...new Set(session.exercises.flatMap((item) => byId.get(item.id)?.modalities ?? []))] as Modality[];
  return modalities.map((modality) => MODALITY_LABELS[modality][locale]).join(" · ");
}

/**
 * "Mobilite ekle" eylemi için kısa bir bitiriş: katman kapısına uyan, başlangıç seviyesi mobilite hareketlerinden
 * deterministik seçim (aynı tohum aynı sonuç).
 */
export function pickMobilityFinisher(input: { count: number; tier: PlanTier; seed: string; locale?: "tr" | "en"; level?: WellnessLevel; catalog?: Exercise[] }): WorkoutExerciseItem[] {
  const locale = input.locale === "en" ? "en" : "tr";
  const level = input.level ?? "beginner";
  const pool = (input.catalog ?? getAllExercises())
    .filter((exercise) => exercise.isActive !== false && exercise.mediaStatus !== "missing" && exercise.modalities?.includes("mobility") && (LEVEL_RANK[exercise.level] ?? 0) <= 0 && canUseModalityExercise(input.tier ?? "free", exercise.modalities, exercise.subcategories))
    .sort((a, b) => hash(`${input.seed}:mobility:${a.id}`) - hash(`${input.seed}:mobility:${b.id}`));
  return pool.slice(0, Math.max(0, input.count)).map((exercise) => {
    const dose = prescribe(exercise, locale, level);
    const steps = locale === "en" ? exercise.instructions : exercise.instructionsTr?.length ? exercise.instructionsTr : exercise.instructions;
    return { id: exercise.id, name: translateExerciseName(exercise.name, locale), english: exercise.name, area: AREA_TR[exercise.bodyPart ?? ""] ?? "Tüm vücut", sets: 1, reps: dose.reps, restSeconds: 10, instructions: steps.slice(0, 2).join(" ") };
  });
}
