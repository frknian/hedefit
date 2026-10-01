// Plan Repair Engine: Repairs invalid training plans by swapping or adjusting exercises

import type {
  MovementPattern,
  MuscleGroup,
  PlannedExerciseInstance,
  PlannedWorkoutSession,
  StandardizedExercise,
  TrainingProfile,
  ValidationResult,
  WeeklyVolumeTargets,
} from "./types.ts";
import { isEquipmentAvailable, isSafeForLimitations, isDifficultySuitable, determineRepAndRest } from "./exercise-selector.ts";
import { validatePlan } from "./plan-validator.ts";

export interface RepairResult {
  repaired: boolean;
  sessions: PlannedWorkoutSession[];
  validation: ValidationResult;
  repairNotes: string[];
}

export function repairPlan(
  sessions: PlannedWorkoutSession[],
  profile: TrainingProfile,
  targets: WeeklyVolumeTargets,
  catalog: StandardizedExercise[],
  maxIterations = 5,
): RepairResult {
  let currentSessions: PlannedWorkoutSession[] = JSON.parse(JSON.stringify(sessions));
  const repairNotes: string[] = [];
  let iterations = 0;

  let currentValidation = validatePlan(currentSessions, profile, targets);

  while (!currentValidation.valid && iterations < maxIterations) {
    iterations++;
    let madeChanges = false;

    // Process critical issues first
    const criticalIssues = currentValidation.issues.filter((i) => i.severity === "critical");

    for (const issue of criticalIssues) {
      if (issue.sessionIndex !== undefined && issue.exerciseId) {
        const session = currentSessions[issue.sessionIndex];
        if (!session) continue;

        const exIndex = session.exercises.findIndex((e) => e.exercise.id === issue.exerciseId);
        if (exIndex === -1) continue;

        const problematic = session.exercises[exIndex].exercise;
        const targetMuscle = problematic.primaryMuscles[0] || "core";

        // Find existing exercise IDs in this session
        const existingIds = new Set<string>(session.exercises.map((e) => e.exercise.id));
        existingIds.delete(problematic.id);

        // Find candidate replacements
        const candidates = catalog.filter((candidate) => {
          if (existingIds.has(candidate.id)) return false;
          if (!candidate.primaryMuscles.includes(targetMuscle)) return false;
          if (!isEquipmentAvailable(candidate.equipmentOptions, profile.equipment)) return false;
          if (!isSafeForLimitations(candidate, profile.limitations)) return false;
          if (!isDifficultySuitable(candidate.difficulty, profile.fitnessLevel)) return false;
          return true;
        });

        if (candidates.length > 0) {
          // Choose candidate with matching compound/isolation type if possible
          const bestCandidate = candidates.find((c) => c.compoundOrIsolation === problematic.compoundOrIsolation) || candidates[0];
          const { reps, restSeconds } = determineRepAndRest(bestCandidate, profile);

          repairNotes.push(
            `Seans ${issue.sessionIndex + 1}: Kural '${issue.rule}' nedeniyle '${problematic.name}' yerine '${bestCandidate.name}' konuldu.`
          );

          session.exercises[exIndex] = {
            exercise: bestCandidate,
            sets: session.exercises[exIndex].sets,
            reps,
            restSeconds,
            order: session.exercises[exIndex].order,
          };
          madeChanges = true;
        } else {
          // If no direct primary muscle replacement, look for secondary muscle match or safe general exercise
          const fallbackCandidates = catalog.filter((candidate) => {
            if (existingIds.has(candidate.id)) return false;
            if (!candidate.secondaryMuscles.includes(targetMuscle) && !candidate.primaryMuscles.includes("core")) return false;
            if (!isEquipmentAvailable(candidate.equipmentOptions, profile.equipment)) return false;
            if (!isSafeForLimitations(candidate, profile.limitations)) return false;
            if (!isDifficultySuitable(candidate.difficulty, profile.fitnessLevel)) return false;
            return true;
          });

          if (fallbackCandidates.length > 0) {
            const fallback = fallbackCandidates[0];
            const { reps, restSeconds } = determineRepAndRest(fallback, profile);
            repairNotes.push(
              `Seans ${issue.sessionIndex + 1}: Alternatif '${problematic.name}' yerine yedek egzersiz '${fallback.name}' atandı.`
            );
            session.exercises[exIndex] = {
              exercise: fallback,
              sets: session.exercises[exIndex].sets,
              reps,
              restSeconds,
              order: session.exercises[exIndex].order,
            };
            madeChanges = true;
          }
        }
      } else if (issue.rule === "number_of_exercises" && issue.sessionIndex !== undefined) {
        // Less than 3 exercises in this session, add suitable exercises
        const session = currentSessions[issue.sessionIndex];
        if (session && session.exercises.length < 3) {
          const existingIds = new Set<string>(session.exercises.map((e) => e.exercise.id));
          const neededMuscles: MuscleGroup[] = ["core", "glutes", "back", "chest"];

          for (const m of neededMuscles) {
            if (session.exercises.length >= 3) break;
            const extra = catalog.find(
              (c) =>
                !existingIds.has(c.id) &&
                c.primaryMuscles.includes(m) &&
                isEquipmentAvailable(c.equipmentOptions, profile.equipment) &&
                isSafeForLimitations(c, profile.limitations) &&
                isDifficultySuitable(c.difficulty, profile.fitnessLevel)
            );
            if (extra) {
              existingIds.add(extra.id);
              const { reps, restSeconds } = determineRepAndRest(extra, profile);
              session.exercises.push({
                exercise: extra,
                sets: 3,
                reps,
                restSeconds,
                order: session.exercises.length + 1,
              });
              repairNotes.push(`Seans ${issue.sessionIndex + 1}: Yetersiz egzersiz sayısını tamamlamak için '${extra.name}' eklendi.`);
              madeChanges = true;
            }
          }
        }
      }
    }

    currentValidation = validatePlan(currentSessions, profile, targets);
    if (!madeChanges) break;
  }

  // Handle duplicate exercises across single sessions if any remain
  currentSessions.forEach((session, sIdx) => {
    const seen = new Set<string>();
    session.exercises.forEach((inst, eIdx) => {
      if (seen.has(inst.exercise.id)) {
        // Duplicate found! Replace it
        const replacement = catalog.find(
          (c) =>
            !seen.has(c.id) &&
            c.primaryMuscles.includes(inst.exercise.primaryMuscles[0] || "core") &&
            isEquipmentAvailable(c.equipmentOptions, profile.equipment) &&
            isSafeForLimitations(c, profile.limitations) &&
            isDifficultySuitable(c.difficulty, profile.fitnessLevel)
        );
        if (replacement) {
          const { reps, restSeconds } = determineRepAndRest(replacement, profile);
          session.exercises[eIdx] = {
            exercise: replacement,
            sets: inst.sets,
            reps,
            restSeconds,
            order: inst.order,
          };
          seen.add(replacement.id);
          repairNotes.push(`Seans ${sIdx + 1}: Tekrar eden '${inst.exercise.name}' yerine '${replacement.name}' konuldu.`);
        }
      } else {
        seen.add(inst.exercise.id);
      }
    });
  });

  currentValidation = validatePlan(currentSessions, profile, targets);

  return {
    repaired: repairNotes.length > 0,
    sessions: currentSessions,
    validation: currentValidation,
    repairNotes,
  };
}
