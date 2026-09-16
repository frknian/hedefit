// Exercise Replacement Engine: Intelligently finds alternative exercises based on user reason

import type {
  LimitationArea,
  MovementPattern,
  MuscleGroup,
  StandardizedExercise,
  TrainingProfile,
} from "./types.ts";
import {
  getRegressionExerciseId,
  getProgressionExerciseId,
  findInChain,
} from "./exercise-chains.ts";
import {
  getStandardizedCatalog,
  getStandardizedExerciseById,
} from "./exercise-metadata.ts";
import {
  isEquipmentAvailable,
  isSafeForLimitations,
  isDifficultySuitable,
  determineRepAndRest,
} from "./exercise-selector.ts";

export type ReplacementReason =
  | "too_hard"
  | "too_easy"
  | "no_equipment"
  | "cant_do"
  | "dont_understand"
  | "pain_discomfort"
  | "disliked";

export interface ExerciseReplacementResult {
  originalExercise: StandardizedExercise;
  replacementExercise: StandardizedExercise;
  reason: ReplacementReason;
  explanationTr: string;
  explanationEn: string;
  sets: number;
  reps: string;
  restSeconds: number;
  progressionType: "regression" | "progression" | "lateral" | "equipment_swap" | "safety_swap";
}

export function replaceExercise(
  currentExerciseId: string,
  reason: ReplacementReason,
  profile: TrainingProfile,
  sessionExerciseIds: string[] = [],
  discomfortArea?: LimitationArea,
  catalogOverride?: StandardizedExercise[],
): ExerciseReplacementResult | null {
  const catalog = catalogOverride || getStandardizedCatalog();
  const current = getStandardizedExerciseById(currentExerciseId) || catalog.find((e) => e.id === currentExerciseId);

  if (!current) return null;

  const currentMuscle = current.primaryMuscles[0] || "quadriceps";
  const usedIds = new Set(sessionExerciseIds);
  usedIds.add(current.id);

  let candidate: StandardizedExercise | null = null;
  let progressionType: ExerciseReplacementResult["progressionType"] = "lateral";
  let explanationTr = "";
  let explanationEn = "";

  // 1. Reason: TOO HARD (Regress)
  if (reason === "too_hard" || reason === "cant_do") {
    progressionType = "regression";
    // Check regression chain first
    const regressionId = getRegressionExerciseId(current.id);
    if (regressionId) {
      const regEx = getStandardizedExerciseById(regressionId);
      if (regEx && isEquipmentAvailable(regEx.equipment, profile.equipment) && !usedIds.has(regEx.id)) {
        candidate = regEx;
      }
    }

    // If chain didn't yield, look for beginner/isolation alternative in same muscle group
    if (!candidate) {
      candidate =
        catalog.find(
          (e) =>
            !usedIds.has(e.id) &&
            e.primaryMuscles.includes(currentMuscle) &&
            (e.difficulty === "beginner" || e.compoundOrIsolation === "isolation") &&
            isEquipmentAvailable(e.equipment, profile.equipment) &&
            isSafeForLimitations(e, profile.limitations) &&
            e.category !== "stretching",
        ) ||
        catalog.find(
          (e) =>
            !usedIds.has(e.id) &&
            (e.primaryMuscles.includes(currentMuscle) || e.secondaryMuscles.includes(currentMuscle)) &&
            isEquipmentAvailable(e.equipment, profile.equipment) &&
            isSafeForLimitations(e, profile.limitations) &&
            e.category !== "stretching",
        ) ||
        catalog.find(
          (e) =>
            !usedIds.has(e.id) &&
            e.difficulty === "beginner" &&
            isEquipmentAvailable(e.equipment, profile.equipment) &&
            isSafeForLimitations(e, profile.limitations) &&
            e.category !== "stretching",
        ) ||
        null;
    }

    explanationTr = `'${current.name}' zorlayıcı geldiği için hareket kalıbını bozmadan daha kontrollü ve stabil bir alternatif seçildi.`;
    explanationEn = `Since '${current.name}' was too challenging, a more controlled and stable regression was selected.`;
  }

  // 2. Reason: TOO EASY (Progress)
  else if (reason === "too_easy") {
    progressionType = "progression";
    const progressionId = getProgressionExerciseId(current.id);
    if (progressionId) {
      const progEx = getStandardizedExerciseById(progressionId);
      if (progEx && isEquipmentAvailable(progEx.equipment, profile.equipment) && !usedIds.has(progEx.id)) {
        candidate = progEx;
      }
    }

    if (!candidate) {
      candidate =
        catalog.find(
          (e) =>
            !usedIds.has(e.id) &&
            e.primaryMuscles.includes(currentMuscle) &&
            e.compoundOrIsolation === "compound" &&
            e.id !== current.id &&
            isEquipmentAvailable(e.equipment, profile.equipment) &&
            isSafeForLimitations(e, profile.limitations) &&
            e.category !== "stretching",
        ) || null;
    }

    explanationTr = `'${current.name}' kolay geldiği için daha yüksek uyarım ve kas yükü sağlayan bir varyasyon atandı.`;
    explanationEn = `Since '${current.name}' felt too easy, a higher-load progressive variation was assigned.`;
  }

  // 3. Reason: NO EQUIPMENT (Equipment Swap)
  else if (reason === "no_equipment") {
    progressionType = "equipment_swap";
    // Find exercise for same muscle using available equipment (or bodyweight)
    candidate =
      catalog.find(
        (e) =>
          !usedIds.has(e.id) &&
          e.primaryMuscles.includes(currentMuscle) &&
          e.movementPattern === current.movementPattern &&
          isEquipmentAvailable(e.equipment, profile.equipment) &&
          isSafeForLimitations(e, profile.limitations) &&
          e.category !== "stretching",
      ) ||
      catalog.find(
        (e) =>
          !usedIds.has(e.id) &&
          e.primaryMuscles.includes(currentMuscle) &&
          isEquipmentAvailable(e.equipment, profile.equipment) &&
          isSafeForLimitations(e, profile.limitations) &&
          e.category !== "stretching",
      ) ||
      null;

    explanationTr = `Gerekli ekipman bulunmadığı için mevcut ekipmanına (${profile.equipment.join(", ")}) tam uyumlu alternatif atandı.`;
    explanationEn = `Adjusted to an exercise fully compatible with your available equipment (${profile.equipment.join(", ")}).`;
  }

  // 4. Reason: PAIN OR DISCOMFORT (Safety Swap)
  else if (reason === "pain_discomfort") {
    progressionType = "safety_swap";
    const painList = discomfortArea ? [...profile.limitations, discomfortArea] : profile.limitations;

    // Filter candidate completely avoiding current contraindications and pain area
    candidate =
      catalog.find((e) => {
        if (usedIds.has(e.id)) return false;
        if (!e.primaryMuscles.includes(currentMuscle)) return false;
        if (e.category === "stretching") return false;
        if (!isEquipmentAvailable(e.equipment, profile.equipment)) return false;
        if (!isSafeForLimitations(e, painList)) return false;
        // Also avoid same aggressive movement pattern if it caused discomfort
        if (e.movementPattern === current.movementPattern && (discomfortArea === "knee" || discomfortArea === "shoulder")) {
          return e.compoundOrIsolation === "isolation";
        }
        return true;
      }) ||
      catalog.find(
        (e) =>
          !usedIds.has(e.id) &&
          e.primaryMuscles.includes(currentMuscle) &&
          isEquipmentAvailable(e.equipment, profile.equipment) &&
          isSafeForLimitations(e, painList) &&
          e.category !== "stretching",
      ) ||
      null;

    explanationTr = `Eklem veya kas rahatsızlığını önlemek için hassas bölgeyi zorlamayan güvenli bir alternatif hareket atandı.`;
    explanationEn = `Replaced with a joint-friendly variation to protect against discomfort.`;
  }

  // 5. Reason: DISLIKED OR DON'T UNDERSTAND (Lateral Swap)
  else {
    progressionType = "lateral";
    // Find popular alternative in same muscle group and pattern
    candidate =
      catalog.find(
        (e) =>
          !usedIds.has(e.id) &&
          e.primaryMuscles.includes(currentMuscle) &&
          e.movementPattern === current.movementPattern &&
          e.id !== current.id &&
          isEquipmentAvailable(e.equipment, profile.equipment) &&
          isSafeForLimitations(e, profile.limitations) &&
          e.category !== "stretching",
      ) ||
      catalog.find(
        (e) =>
          !usedIds.has(e.id) &&
          e.primaryMuscles.includes(currentMuscle) &&
          e.id !== current.id &&
          isEquipmentAvailable(e.equipment, profile.equipment) &&
          isSafeForLimitations(e, profile.limitations) &&
          e.category !== "stretching",
      ) ||
      null;

    explanationTr = `'${current.name}' yerine aynı kas grubunu etkili şekilde hedefleyen alternatif bir hareket seçildi.`;
    explanationEn = `Replaced '${current.name}' with an effective alternative targeting the same muscle group.`;
  }

  if (!candidate) {
    // Ultimate fallback: any safe exercise for same muscle
    candidate =
      catalog.find(
        (e) =>
          !usedIds.has(e.id) &&
          (e.primaryMuscles.includes(currentMuscle) || e.secondaryMuscles.includes(currentMuscle)) &&
          isEquipmentAvailable(e.equipment, profile.equipment) &&
          isSafeForLimitations(e, profile.limitations) &&
          e.category !== "stretching",
      ) || null;
  }

  if (!candidate) return null;

  // Recalculate sets, reps, rest for the replacement exercise
  const { reps, restSeconds } = determineRepAndRest(candidate, profile);
  const sets = profile.fitnessLevel === "beginner" ? 3 : candidate.compoundOrIsolation === "compound" ? 4 : 3;

  return {
    originalExercise: current,
    replacementExercise: candidate,
    reason,
    explanationTr,
    explanationEn,
    sets,
    reps,
    restSeconds,
    progressionType,
  };
}
