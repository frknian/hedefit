// Fit Koç challenge üreticisi. Bilinen profil verisini (hedef, seviye, ekipman, kısıtlamalar, son check-in'ler,
// antrenman geçmişi) kullanıcının İSTEĞE BAĞLI tercihleriyle birleştirip kişisel bir plan üretir.
//
// Deterministiktir (AI çağrısı yok): aynı girdi aynı planı verir. Böylece önizlenen plan, "Başla" denince
// sunucuda yeniden üretilip aynen kaydedilir — istemciden gelen plana güvenmek gerekmez. Sağlık verisi
// (döngü dahil) plana YAZILMAZ; günlük uyarlama engine.adaptTask ile o gün yapılır.

import type { ChallengePlan, ChallengeTask, Difficulty, Equipment, L10n } from "./catalog.ts";
import { validatePlan } from "./catalog.ts";
import type { SessionKind } from "../training/wellness-session.ts";

export type CoachFocus = "core" | "full_body" | "pilates" | "flexibility" | "steps";
export type CoachEquipment = "none" | "band" | "gym";

export interface CoachPreferences {
  focus?: CoachFocus;
  days?: number;
  minutes?: number;
  equipment?: CoachEquipment;
  level?: Difficulty;
}

export interface CoachKnownProfile {
  goal?: string; // TrainingProfile.goal
  fitnessLevel?: string;
  equipmentText?: string;
  environment?: string;
  limitations?: string[];
  priorityMuscles?: string[];
  mobilityLevel?: string;
  sessionDurationMinutes?: number;
  /** Son 7 günün ortalama enerji/uyku değeri (1–10), yoksa null. */
  averageEnergy?: number | null;
  averageSleep?: number | null;
  /** Son 14 gündeki antrenman sayısı. */
  recentWorkouts?: number;
}

export interface ResolvedCoachInputs {
  focus: CoachFocus;
  days: number;
  minutes: number;
  equipment: CoachEquipment;
  level: Difficulty;
  gentleStart: boolean;
  lowImpact: boolean;
}

const DAY_OPTIONS = [7, 14, 21, 30];
const MINUTE_OPTIONS = [10, 15, 20, 30];
const clampOption = (value: number | undefined, options: number[], fallback: number) =>
  typeof value === "number" && Number.isFinite(value) ? options.reduce((best, option) => (Math.abs(option - value) < Math.abs(best - value) ? option : best), options[0]) : fallback;

/** Kullanıcı seçmediyse bilinen veriden tamamlar; böylece zaten bilinen bilgi tekrar sorulmaz. */
export function resolveCoachInputs(preferences: CoachPreferences, known: CoachKnownProfile): ResolvedCoachInputs {
  const limitations = known.limitations ?? [];
  const lowImpact = limitations.some((area) => area === "knee" || area === "lower_back" || area === "hip");
  const levels: Difficulty[] = ["beginner", "intermediate", "advanced"];
  const knownLevel = levels.includes(known.fitnessLevel as Difficulty) ? (known.fitnessLevel as Difficulty) : "beginner";
  const level = levels.includes(preferences.level as Difficulty) ? (preferences.level as Difficulty) : knownLevel;

  const knownEquipment: CoachEquipment = /salon|gym|full|tam/i.test(`${known.equipmentText ?? ""} ${known.environment ?? ""}`) ? "gym"
    : /band|bant|lastik/i.test(known.equipmentText ?? "") ? "band" : "none";
  const equipment = (["none", "band", "gym"] as CoachEquipment[]).includes(preferences.equipment as CoachEquipment) ? (preferences.equipment as CoachEquipment) : knownEquipment;

  let focus: CoachFocus | undefined = (["core", "full_body", "pilates", "flexibility", "steps"] as CoachFocus[]).find((value) => value === preferences.focus);
  if (!focus) {
    if (known.priorityMuscles?.includes("core")) focus = "core";
    else if (lowImpact || known.mobilityLevel === "low") focus = "pilates";
    else if (known.goal === "weight_loss") focus = "steps";
    else focus = "full_body";
  }

  const knownMinutes = Math.round(Math.min(30, Math.max(10, (known.sessionDurationMinutes ?? 45) / 3)));
  const minutes = clampOption(preferences.minutes, MINUTE_OPTIONS, clampOption(knownMinutes, MINUTE_OPTIONS, 15));
  const days = clampOption(preferences.days, DAY_OPTIONS, 14);
  // Düşük enerji/uyku ya da uzun süre ara verdiyse ilk hafta daha yumuşak başlar.
  const gentleStart = (known.averageEnergy ?? 10) <= 5 || (known.averageSleep ?? 10) <= 5 || (known.recentWorkouts ?? 3) === 0;
  return { focus, days, minutes, equipment, level, gentleStart, lowImpact };
}

const FOCUS_NAMES: Record<CoachFocus, L10n> = {
  core: { tr: "Core", en: "Core" },
  full_body: { tr: "Tüm Vücut", en: "Full Body" },
  pilates: { tr: "Pilates", en: "Pilates" },
  flexibility: { tr: "Esneklik", en: "Flexibility" },
  steps: { tr: "Adım", en: "Steps" },
};

/** Odak + ekipmana göre dönen oturum döngüsü. Her 4. gün aktif toparlanmadır (adım odağı hariç). */
function rotation(inputs: ResolvedCoachInputs): SessionKind[] {
  const strength: SessionKind = inputs.equipment === "band" ? "band_strength" : "home_strength";
  switch (inputs.focus) {
    case "core": return inputs.equipment === "band" ? ["band_strength", "core_focus", "core_focus"] : ["core_focus", inputs.lowImpact ? "pilates_today" : strength, "core_focus"];
    case "pilates": return ["pilates_today", "pilates_today", inputs.lowImpact ? "posture_mobility" : "core_focus"];
    case "flexibility": return ["flexibility", "posture_mobility", "flexibility"];
    case "steps": return ["home_strength"];
    case "full_body":
    default: return inputs.lowImpact ? ["pilates_today", "low_impact_recovery", "core_focus"] : [strength, "core_focus", strength];
  }
}

function titleFor(inputs: ResolvedCoachInputs): L10n {
  const focus = FOCUS_NAMES[inputs.focus];
  const withBand = inputs.equipment === "band" && (inputs.focus === "core" || inputs.focus === "full_body");
  const name = withBand ? { tr: `Band & ${focus.tr}`, en: `Band & ${focus.en}` } : focus;
  return { tr: `${inputs.days} Gün ${name.tr}`, en: `${inputs.days}-Day ${name.en}` };
}

export function buildCoachChallenge(preferences: CoachPreferences, known: CoachKnownProfile): { plan: ChallengePlan; inputs: ResolvedCoachInputs } {
  const inputs = resolveCoachInputs(preferences, known);
  const kinds = rotation(inputs);
  const start = inputs.gentleStart ? Math.max(8, Math.round(inputs.minutes * 0.6)) : Math.max(8, Math.round(inputs.minutes * 0.8));
  const levelSteps: Record<Difficulty, number> = { beginner: 6_000, intermediate: 8_000, advanced: 10_000 };
  const days: ChallengeTask[] = Array.from({ length: inputs.days }, (_, index) => {
    const progress = inputs.days > 1 ? index / (inputs.days - 1) : 1;
    const minutes = Math.round(start + (inputs.minutes - start) * Math.min(1, progress * 1.4));
    if (inputs.focus === "steps") {
      // Adım odağı: kademeli artan adım hedefi; her 4. gün kısa evde güç seansı.
      if (index % 4 === 3) return { kind: "session", session: "home_strength", minutes: Math.max(10, Math.min(20, minutes)) };
      const base = inputs.gentleStart ? levelSteps[inputs.level] - 1_000 : levelSteps[inputs.level];
      return { kind: "steps", target: Math.max(3_000, Math.round((base + 2_000 * progress) / 500) * 500) };
    }
    if (index % 4 === 3) return { kind: "session", session: inputs.focus === "flexibility" ? "low_impact_recovery" : "posture_mobility", minutes: Math.max(8, Math.min(12, minutes)) };
    return { kind: "session", session: kinds[index % kinds.length], minutes };
  });
  const equipment: Equipment = inputs.equipment === "band" ? "band" : inputs.focus === "pilates" || inputs.focus === "flexibility" ? "mat" : "none";
  const minuteText = inputs.focus === "steps" ? null : `${start}–${inputs.minutes}`;
  const description: L10n = {
    tr: `Fit Koç bu planı hedefin, seviyen ve ekipmanına göre hazırladı${minuteText ? `; günde ${minuteText} dakika` : ""}. Her gün check-in'ine göre uyarlanır.`,
    en: `Fit Coach built this plan around your goal, level and equipment${minuteText ? `; ${minuteText} minutes a day` : ""}. It adapts to your daily check-in.`,
  };
  const key = `coach:${inputs.focus}:${inputs.equipment}:${inputs.level}:${inputs.days}:${inputs.minutes}`;
  const plan: ChallengePlan = { key, version: 1, source: "coach", category: "coach", difficulty: inputs.level, equipment, title: titleFor(inputs), description, days };
  if (!validatePlan(plan)) throw new Error("coach_plan_invalid");
  return { plan, inputs };
}
