// Exercise Selector: Selects optimal exercises for muscle budgets adhering to all constraints

import type {
  GoalType,
  MovementPattern,
  MuscleGroup,
  PlannedExerciseInstance,
  PlannedSessionSlot,
  PlannedWorkoutSession,
  StandardizedExercise,
  TrainingProfile,
} from "./types.ts";

/** TrainingProfile.goal -> RepDB-derived StandardizedExercise.goalCompatibility vocabulary. */
const GOAL_TO_COMPATIBILITY: Record<GoalType, string> = {
  hypertrophy: "muscle_gain",
  strength: "strength",
  weight_loss: "fat_loss",
  endurance: "endurance",
  general_fitness: "general_fitness",
};

export function isEquipmentAvailable(exerciseEquipment: string[], userEquipment: string[]): boolean {
  if (userEquipment.includes("gym")) return true;
  // If exercise requires bodyweight only, it's always available
  if (exerciseEquipment.length === 1 && exerciseEquipment[0] === "bodyweight") return true;

  // Check if every equipment piece required by exercise is in user equipment
  return exerciseEquipment.every((item) => userEquipment.includes(item) || item === "bodyweight");
}

export function isSafeForLimitations(exercise: StandardizedExercise, limitations: string[]): boolean {
  if (limitations.length === 0) return true;
  for (const limit of exercise.contraindications) {
    if (limitations.includes(limit)) return false;
  }
  return true;
}

export function isDifficultySuitable(exerciseDifficulty: string, userLevel: string): boolean {
  if (userLevel === "advanced") return true;
  if (userLevel === "intermediate") return exerciseDifficulty !== "advanced";
  // beginner: only beginner exercises
  return exerciseDifficulty === "beginner";
}

export function determineRepAndRest(
  exercise: StandardizedExercise,
  profile: TrainingProfile,
): { reps: string; restSeconds: number } {
  const isBeginner = profile.fitnessLevel === "beginner" || profile.detrained;
  const isStrength = profile.goal === "strength";
  const isEndurance = profile.goal === "endurance" || profile.goal === "weight_loss";
  const isCompound = exercise.compoundOrIsolation === "compound";

  if (isStrength) {
    if (isCompound) return { reps: isBeginner ? "6–8" : "4–6", restSeconds: 120 };
    return { reps: "8–10", restSeconds: 90 };
  }

  if (isEndurance) {
    if (isCompound) return { reps: "12–15", restSeconds: 60 };
    return { reps: "15–20", restSeconds: 45 };
  }

  // Hypertrophy / General
  if (isCompound) {
    return { reps: isBeginner ? "8–10" : "8–12", restSeconds: isBeginner ? 75 : 90 };
  }

  // Isolation
  return { reps: "10–14", restSeconds: 60 };
}

export function selectExerciseForBudget(
  targetMuscle: MuscleGroup,
  preferredPattern: MovementPattern | undefined,
  catalog: StandardizedExercise[],
  profile: TrainingProfile,
  alreadySelectedIds: Set<string>,
  usedPatternsInSession: Set<MovementPattern>,
  order: number,
  previouslyUsedIdsAcrossSessions?: Set<string>,
): StandardizedExercise | null {
  // Step 1: Filter eligible exercises
  const eligible = catalog.filter((ex) => {
    // 1. Must match target muscle
    if (!ex.primaryMuscles.includes(targetMuscle)) return false;

    // 2. Resistance training must not include stretching
    if (ex.category === "stretching") return false;

    // 3. Equipment must be available
    if (!isEquipmentAvailable(ex.equipment, profile.equipment)) return false;

    // 4. Must be safe for limitations / injuries
    if (!isSafeForLimitations(ex, profile.limitations)) return false;

    // 5. Difficulty must match user level
    if (!isDifficultySuitable(ex.difficulty, profile.fitnessLevel)) return false;

    // 6. Must not be already used in this session
    if (alreadySelectedIds.has(ex.id)) return false;

    return true;
  });

  if (eligible.length === 0) {
    // Fallback relaxation: allow secondary muscle match if no primary is available, preserving difficulty
    let fallback = catalog.filter((ex) => {
      if (ex.category === "stretching") return false;
      if (!ex.secondaryMuscles.includes(targetMuscle) && !ex.primaryMuscles.includes(targetMuscle)) return false;
      if (!isEquipmentAvailable(ex.equipment, profile.equipment)) return false;
      if (!isSafeForLimitations(ex, profile.limitations)) return false;
      if (!isDifficultySuitable(ex.difficulty, profile.fitnessLevel)) return false;
      if (alreadySelectedIds.has(ex.id)) return false;
      return true;
    });
    if (fallback.length === 0) {
      fallback = catalog.filter((ex) => {
        if (ex.category === "stretching") return false;
        if (!isEquipmentAvailable(ex.equipment, profile.equipment)) return false;
        if (!isSafeForLimitations(ex, profile.limitations)) return false;
        if (!isDifficultySuitable(ex.difficulty, profile.fitnessLevel)) return false;
        if (alreadySelectedIds.has(ex.id)) return false;
        return true;
      });
    }
    if (fallback.length === 0) {
      fallback = catalog.filter((ex) => {
        if (ex.category === "stretching") return false;
        if (!isEquipmentAvailable(ex.equipment, profile.equipment)) return false;
        if (!isSafeForLimitations(ex, profile.limitations)) return false;
        if (alreadySelectedIds.has(ex.id)) return false;
        return true;
      });
    }
    if (fallback.length > 0) return fallback[0];
    return null;
  }

  // Step 2: Score candidates based on compound priority, pattern match, user request, and pattern diversity
  const scored = eligible.map((exercise) => {
    let score = 0;

    // Preferred movement pattern match
    if (preferredPattern && exercise.movementPattern === preferredPattern) {
      score += 40;
    }

    // Compound priority in early exercise slots
    if (order <= 2 && exercise.compoundOrIsolation === "compound") {
      score += 30;
    }

    // Isolation preferred in later slots
    if (order > 3 && exercise.compoundOrIsolation === "isolation") {
      score += 15;
    }

    // User requested exercise bonus
    if (profile.userRequestedExercises.some((req) => exercise.name.toLowerCase().includes(req.toLowerCase()))) {
      score += 50;
    }

    // Avoid duplicate movement patterns within the same session
    if (usedPatternsInSession.has(exercise.movementPattern)) {
      score -= 20;
    }

    // Penalize exercises already used in previous sessions of the plan to promote variety across days
    if (previouslyUsedIdsAcrossSessions && previouslyUsedIdsAcrossSessions.has(exercise.id)) {
      score -= 35;
    }

    // Foundation exercise preference
    if (/bench press|squat|deadlift|overhead press|barbell row|pull-up|lat pulldown|hip thrust|leg press|dip/i.test(exercise.name)) {
      score += 10;
    }

    // RepDB-derived goal fit (see scripts/import-repdb.mjs GOAL_MAP)
    if (exercise.goalCompatibility?.includes(GOAL_TO_COMPATIBILITY[profile.goal])) {
      score += 12;
    }

    return { exercise, score };
  });

  scored.sort((a, b) => b.score - a.score || a.exercise.name.localeCompare(b.exercise.name));
  return scored[0]?.exercise || null;
}

export function selectExercisesForSession(
  slot: PlannedSessionSlot,
  catalog: StandardizedExercise[],
  profile: TrainingProfile,
  previouslyUsedIdsAcrossSessions?: Set<string>,
): PlannedWorkoutSession {
  const instances: PlannedExerciseInstance[] = [];
  const selectedIds = new Set<string>();
  const usedPatterns = new Set<MovementPattern>();

  slot.muscleBudgets.forEach((budget, index) => {
    const exercise = selectExerciseForBudget(
      budget.muscle,
      budget.preferredPattern,
      catalog,
      profile,
      selectedIds,
      usedPatterns,
      index,
      previouslyUsedIdsAcrossSessions,
    );

    if (exercise) {
      selectedIds.add(exercise.id);
      usedPatterns.add(exercise.movementPattern);
      const { reps, restSeconds } = determineRepAndRest(exercise, profile);

      instances.push({
        exercise,
        sets: budget.setsPerExercise,
        reps,
        restSeconds,
        order: index + 1,
      });
    }
  });

  return {
    dayIndex: slot.dayIndex,
    dayName: slot.dayName,
    focus: slot.focusDisplayName,
    durationMinutes: slot.durationMinutes,
    exercises: instances,
  };
}
