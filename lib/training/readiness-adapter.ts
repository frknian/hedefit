// Readiness and Recovery Adaptation Engine

import type { MuscleGroup, TrainingProfile } from "./types.ts";
import { getStandardizedCatalog } from "./exercise-metadata.ts";
import { isEquipmentAvailable, isSafeForLimitations } from "./exercise-selector.ts";

export interface DailyReadinessCheckin {
  energy: number; // 1-10
  sleepQuality: number; // 1-10
  fatigue: number; // 1-10
  hasSoreness: boolean;
  sorenessAreas: MuscleGroup[];
  discomfortLevel: number; // 1-10
  notes?: string;
}

export interface WorkoutExerciseItem {
  id: string;
  name: string;
  english?: string;
  area: string;
  sets: number;
  reps: string;
  restSeconds: number;
  instructions?: string;
}

export interface ReadinessAdaptationResult {
  needsAdaptation: boolean;
  recommendedIntensity: "normal" | "reduced" | "active_recovery";
  explanationTr: string;
  explanationEn: string;
  volumeReductionPercent: number;
  adaptedExercises: WorkoutExerciseItem[];
  originalExercises: WorkoutExerciseItem[];
  deloadedMuscles: MuscleGroup[];
}

export function evaluateReadinessAndAdapt(
  checkin: DailyReadinessCheckin,
  exercises: WorkoutExerciseItem[],
  profile: TrainingProfile,
): ReadinessAdaptationResult {
  const energy = Math.max(1, Math.min(10, checkin.energy));
  const sleep = Math.max(1, Math.min(10, checkin.sleepQuality));
  const fatigue = Math.max(1, Math.min(10, checkin.fatigue));
  const discomfort = Math.max(1, Math.min(10, checkin.discomfortLevel));
  const soreAreas = checkin.hasSoreness ? checkin.sorenessAreas : [];

  // Check if conditions require adaptation
  const isSeverelyFatigued = energy <= 3 || fatigue >= 8 || sleep <= 3;
  const isModeratelyFatigued = energy <= 5 || fatigue >= 6 || sleep <= 5;
  const hasHighDiscomfort = discomfort >= 7;
  const hasModerateDiscomfort = discomfort >= 5;
  const hasSoreMuscles = soreAreas.length > 0;

  const needsAdaptation = isSeverelyFatigued || isModeratelyFatigued || hasHighDiscomfort || hasModerateDiscomfort || hasSoreMuscles;

  if (!needsAdaptation) {
    return {
      needsAdaptation: false,
      recommendedIntensity: "normal",
      explanationTr: "Toparlanma durumun harika! Bugünkü antrenmanını tam kapasiteyle uygulayabilirsin.",
      explanationEn: "Your readiness is great! You are ready to complete today's workout at full capacity.",
      volumeReductionPercent: 0,
      adaptedExercises: exercises,
      originalExercises: exercises,
      deloadedMuscles: [],
    };
  }

  let recommendedIntensity: ReadinessAdaptationResult["recommendedIntensity"] = "reduced";
  let volumeReductionPercent = 0;
  const delodedMuscles: MuscleGroup[] = [];
  const explanationsTr: string[] = [];
  const explanationsEn: string[] = [];

  if (hasHighDiscomfort) {
    recommendedIntensity = "active_recovery";
    volumeReductionPercent = 40;
    explanationsTr.push("Yüksek rahatsızlık seviyesi nedeniyle eklemleri dinlendirmek için hafif toparlanma temposuna geçildi.");
    explanationsEn.push("Shifted to an active recovery tempo with lower loads to protect sensitive joints.");
  } else if (isSeverelyFatigued) {
    recommendedIntensity = "reduced";
    volumeReductionPercent = 33;
    explanationsTr.push("Düşük enerji ve yetersiz uyku nedeniyle aşırı yorgunluğu önlemek için set sayıları azaltıldı ve dinlenmeler uzatıldı.");
    explanationsEn.push("Volume scaled down by ~30% and rest periods extended to account for low energy and fatigue.");
  } else if (isModeratelyFatigued) {
    recommendedIntensity = "reduced";
    volumeReductionPercent = 20;
    explanationsTr.push("Orta seviye yorgunluk durumu gözetilerek antrenman hacmi hafifçe dengelendi.");
    explanationsEn.push("Workout volume slightly tuned down to support recovery.");
  }

  if (hasSoreMuscles) {
    explanationsTr.push(`Belirttiğin ağrılı kas bölgeleri (${soreAreas.join(", ")}) için çalışma hacmi hafifletildi.`);
    explanationsEn.push(`Workload reduced on sore muscle groups (${soreAreas.join(", ")}).`);
  }

  const catalog = getStandardizedCatalog();
  let totalOriginalSets = 0;
  let totalAdaptedSets = 0;

  // Process exercises
  const adaptedExercises: WorkoutExerciseItem[] = exercises.map((item) => {
    totalOriginalSets += item.sets;
    let newSets = item.sets;
    let newRest = item.restSeconds;

    // Check if exercise hits a sore muscle
    const matchedEx = catalog.find((c) => c.id === item.id);
    const hitsSoreMuscle = matchedEx && matchedEx.primaryMuscles.some((m) => soreAreas.includes(m));

    if (hitsSoreMuscle) {
      // Deload this exercise significantly: reduce sets to 2, increase rest
      newSets = Math.max(1, Math.min(newSets, 2));
      newRest = Math.max(newRest, 90);
      matchedEx.primaryMuscles.forEach((m) => {
        if (soreAreas.includes(m) && !delodedMuscles.includes(m)) delodedMuscles.push(m);
      });
    } else if (hasHighDiscomfort) {
      // Active recovery: deload all exercises (~40% reduction) to protect joints
      newSets = Math.max(1, Math.round(newSets * 0.6));
      newRest = newRest + 30;
    } else if (isSeverelyFatigued) {
      // Scale down sets by ~33%
      newSets = Math.max(2, Math.round(newSets * 0.67));
      newRest = newRest + 30;
    } else if (isModeratelyFatigued) {
      // Scale down sets by ~20%
      newSets = Math.max(2, Math.round(newSets * 0.8));
      newRest = newRest + 15;
    }

    totalAdaptedSets += newSets;

    return {
      ...item,
      sets: newSets,
      restSeconds: newRest,
    };
  });

  const finalVolumeReduction =
    totalOriginalSets > 0 ? Math.round(((totalOriginalSets - totalAdaptedSets) / totalOriginalSets) * 100) : volumeReductionPercent;

  return {
    needsAdaptation: true,
    recommendedIntensity,
    explanationTr: explanationsTr.join(" ") || "Bugünkü toparlanma durumuna göre antrenmanını biraz hafiflettik.",
    explanationEn: explanationsEn.join(" ") || "We tuned down today's workout based on your recovery check-in.",
    volumeReductionPercent: finalVolumeReduction,
    adaptedExercises,
    originalExercises: exercises,
    deloadedMuscles: delodedMuscles,
  };
}
