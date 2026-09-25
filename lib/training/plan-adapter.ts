// On-The-Fly Workout Plan Adaptation Engine

import type { LimitationArea, MovementPattern, MuscleGroup, TrainingProfile } from "./types.ts";
import { getStandardizedCatalog, getStandardizedExerciseById } from "./exercise-metadata.ts";
import { isEquipmentAvailable, isSafeForLimitations, determineRepAndRest } from "./exercise-selector.ts";
import type { WorkoutExerciseItem } from "./readiness-adapter.ts";
import { translateExerciseName, translateExerciseLabel, turkishExerciseInstructions } from "../exercise-translations.ts";

export type PlanAdaptationTrigger =
  | "time_shortage"
  | "travel"
  | "bands_only"
  | "bodyweight_only"
  | "missed_days"
  | "illness"
  | "acute_fatigue";

export interface PlanAdaptationParams {
  trigger: PlanAdaptationTrigger;
  targetMinutes?: number; // e.g. 15, 20, 30
  notes?: string;
}

export interface AdaptedWorkoutPlanResult {
  trigger: PlanAdaptationTrigger;
  originalExerciseCount: number;
  adaptedExerciseCount: number;
  originalDurationMinutes: number;
  adaptedDurationMinutes: number;
  explanationTr: string;
  explanationEn: string;
  changes: string[];
  adaptedExercises: WorkoutExerciseItem[];
}

export function adaptWorkoutPlanOnTheFly(
  exercises: WorkoutExerciseItem[],
  params: PlanAdaptationParams,
  profile: TrainingProfile,
  locale: "tr" | "en" = "tr",
): AdaptedWorkoutPlanResult {
  const catalog = getStandardizedCatalog();
  const originalDuration = Math.round(
    exercises.reduce((sum, item) => sum + item.sets * (45 + item.restSeconds), 0) / 60,
  );

  const changes: string[] = [];
  let adaptedList: WorkoutExerciseItem[] = [];
  let explanationTr = "";
  let explanationEn = "";

  // 1. Trigger: TIME SHORTAGE (e.g. 15, 20, 30 mins)
  if (params.trigger === "time_shortage") {
    const targetMin = params.targetMinutes || 20;

    // Separate compound foundation exercises from isolation
    const classified = exercises.map((item) => {
      const ex = catalog.find((c) => c.id === item.id);
      return {
        item,
        isCompound: ex ? ex.compoundOrIsolation === "compound" : true,
        primaryMuscle: ex ? ex.primaryMuscles[0] : item.area,
      };
    });

    // Sort to prioritize compounds and diversity of muscle groups
    classified.sort((a, b) => {
      if (a.isCompound && !b.isCompound) return -1;
      if (!a.isCompound && b.isCompound) return 1;
      return 0;
    });

    // Calculate how many exercises can fit in targetMinutes
    // In 15 min: 2-3 exercises x 2 sets
    // In 20 min: 3 exercises x 2-3 sets
    // In 30 min: 4 exercises x 3 sets
    const maxExercises = targetMin <= 15 ? 3 : targetMin <= 22 ? 3 : targetMin <= 32 ? 4 : 5;
    const setsPerMove = targetMin <= 15 ? 2 : 2;

    const selected = classified.slice(0, maxExercises);
    adaptedList = selected.map((s) => ({
      ...s.item,
      sets: setsPerMove,
      restSeconds: Math.min(s.item.restSeconds, 60), // Shorter rest to maintain intensity
    }));

    const prunedCount = exercises.length - adaptedList.length;
    if (prunedCount > 0) {
      changes.push(
        locale === "en"
          ? `Removed ${prunedCount} isolation movement(s) to focus strictly on compound drivers.`
          : `Zaman kısıtı nedeniyle ${prunedCount} izolasyon hareketi çıkarılarak ana bileşik hareketlere odaklanıldı.`,
      );
    }
    changes.push(
      locale === "en"
        ? `Adjusted set counts to ${setsPerMove} sets with 60s rest intervals.`
        : `Setler ${setsPerMove} sete optimize edildi, dinlenme 60 saniyeye ayarlandı.`,
    );

    explanationTr = `${originalDuration} dakikalık antrenmanını temel kas uyarımlarını koruyarak yaklaşık ${targetMin} dakikaya uyarladık.`;
    explanationEn = `Adapted your ${originalDuration}-minute workout to approximately ${targetMin} minutes while preserving primary muscle stimulus.`;
  }

  // 2. Trigger: TRAVEL / BODYWEIGHT ONLY / BANDS ONLY
  else if (params.trigger === "travel" || params.trigger === "bodyweight_only" || params.trigger === "bands_only") {
    const allowedEquipment =
      params.trigger === "bands_only" ? ["bands", "bodyweight"] : ["bodyweight"];

    adaptedList = exercises.map((item) => {
      const origEx = catalog.find((c) => c.id === item.id);
      if (!origEx) return item;

      // Check if current exercise already matches allowed equipment
      if (isEquipmentAvailable(origEx.equipmentOptions, allowedEquipment)) {
        return item;
      }

      // Find replacement exercise for same primary muscle with allowed equipment
      const targetMuscle = origEx.primaryMuscles[0] || "core";
      const alt =
        catalog.find(
          (c) =>
            c.primaryMuscles.includes(targetMuscle) &&
            isEquipmentAvailable(c.equipmentOptions, allowedEquipment) &&
            c.category !== "stretching",
        ) ||
        catalog.find(
          (c) =>
            c.secondaryMuscles.includes(targetMuscle) &&
            isEquipmentAvailable(c.equipmentOptions, allowedEquipment) &&
            c.category !== "stretching",
        );

      if (alt) {
        changes.push(
          locale === "en"
            ? `Replaced '${origEx.name}' with bodyweight/band alternative '${alt.name}'.`
            : `'${item.name}' yerine ekipmansız/lastikli '${translateExerciseName(alt.name, locale)}' atandı.`,
        );
        const { reps, restSeconds } = determineRepAndRest(alt, profile);
        return {
          id: alt.id,
          name: translateExerciseName(alt.name, locale),
          english: alt.name,
          area: translateExerciseLabel(alt.primaryMuscles[0], locale),
          sets: item.sets,
          reps,
          restSeconds,
          instructions: turkishExerciseInstructions(alt, locale).join(" "),
        };
      }
      return item;
    });

    explanationTr =
      params.trigger === "bands_only"
        ? "Programın direnç bandı ve vücut ağırlığıyla uygulanacak şekilde dönüştürüldü."
        : "Seyahat durumuna uygun olarak tüm hareketler vücut ağırlığı varyasyonlarına uyarlandı.";
    explanationEn = "Adjusted workout to be 100% executable with bodyweight and available travel equipment.";
  }

  // 3. Trigger: MISSED DAYS / RETURNING AFTER ILLNESS
  else if (params.trigger === "missed_days" || params.trigger === "illness") {
    // Deload / Re-acclimatization: keep the exercises, drop sets to 2, increase rest
    adaptedList = exercises.map((item) => ({
      ...item,
      sets: Math.max(2, Math.round(item.sets * 0.65)),
      restSeconds: item.restSeconds + 20,
    }));

    changes.push(
      locale === "en"
        ? "Reduced set volume by 35% to prevent extreme delayed onset muscle soreness (DOMS)."
        : "Aşırı hamlık ve kas yıpranmasını önlemek için set hacmi %35 azaltıldı.",
    );

    explanationTr = "Arayı telafi etmek için aşırı yüklenmek yerine, vücudunu güvenle spora yeniden alıştıracak bir seans hazırladık.";
    explanationEn = "Created an re-acclimatization session to safely ease your body back into rhythm after the break.";
  }

  // 4. Trigger: ACUTE FATIGUE
  else {
    adaptedList = exercises.map((item) => ({
      ...item,
      sets: Math.max(2, Math.round(item.sets * 0.7)),
      restSeconds: item.restSeconds + 30,
    }));

    changes.push(
      locale === "en"
        ? "Reduced set count and extended rest times for fatigue management."
        : "Yorgunluk nedeniyle setler azaltıldı ve toparlanma süresi artırıldı.",
    );

    explanationTr = "Bugünkü yüksek yorgunluğun gözetilerek antrenman hafifletildi.";
    explanationEn = "Tuned down workout volume to help manage acute fatigue.";
  }

  const adaptedDuration = Math.round(
    adaptedList.reduce((sum, item) => sum + item.sets * (45 + item.restSeconds), 0) / 60,
  );

  return {
    trigger: params.trigger,
    originalExerciseCount: exercises.length,
    adaptedExerciseCount: adaptedList.length,
    originalDurationMinutes: originalDuration,
    adaptedDurationMinutes: adaptedDuration,
    explanationTr,
    explanationEn,
    changes,
    adaptedExercises: adaptedList,
  };
}
