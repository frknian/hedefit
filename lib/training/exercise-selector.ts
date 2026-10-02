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
import { getCoreEntry } from "./core-pool.ts";
import { seededUnit } from "./rotation.ts";

/** TrainingProfile.goal -> RepDB-derived StandardizedExercise.goalCompatibility vocabulary. */
const GOAL_TO_COMPATIBILITY: Record<GoalType, string> = {
  hypertrophy: "muscle_gain",
  strength: "strength",
  weight_loss: "fat_loss",
  endurance: "endurance",
  general_fitness: "general_fitness",
};

// Rotation weights, tuned against the other score terms (pattern match 40,
// compound 30, loaded equipment 20–25): enough to reshuffle near-equal
// accessories, never enough to override equipment fit or the main lifts.
// Raised from 35: with rotation penalising recently done accessories, repeated main
// lifts would otherwise refill the freed budgets and recur on several days of one plan.
const CROSS_SESSION_REPEAT_PENALTY = 55;
const STAPLE_BONUS = 6;
// Main lifts win near-ties against rotating accessories for the same muscle budget
// (a row beats a rear-delt fly for a back slot even when the jitter favours the fly).
const MAIN_ROLE_BONUS = 10;
const RECENT_ACCESSORY_PENALTY = 40;
const ROTATION_JITTER = 25;
// A second move from the same slot in one session is redundant (two flat
// presses, two squats); steer the next budget to a different slot instead.
const SAME_SLOT_IN_SESSION_PENALTY = 45;

// Vertical and horizontal pulls (and pushes) are interchangeable when the
// person's equipment has no move for the exact pattern: a home dumbbell user has
// no pulldown, but a row still fills the "back / vertical pull" budget.
const SAME_FAMILY_PATTERN_BONUS = 25;
const PATTERN_FAMILY: Partial<Record<MovementPattern, string>> = {
  vertical_pull: "pull", horizontal_pull: "pull", vertical_push: "push", horizontal_push: "push",
};

const LOADED_EQUIPMENT = ["dumbbell", "kettlebell", "barbell", "machine", "cable"];

export function isEquipmentAvailable(equipmentOptions: string[][], userEquipment: string[]): boolean {
  if (userEquipment.includes("gym")) return true;
  return equipmentOptions.some((option) => option.every((item) => item === "bodyweight" || userEquipment.includes(item)));
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
  usedSlotsInSession?: Set<string>,
): StandardizedExercise | null {
  // Step 1: Filter eligible exercises
  const eligible = catalog.filter((ex) => {
    // 1. Must match target muscle
    if (!ex.primaryMuscles.includes(targetMuscle)) return false;

    // 2. Resistance training must not include stretching
    if (ex.category === "stretching") return false;

    // 3. Equipment must be available
    if (!isEquipmentAvailable(ex.equipmentOptions, profile.equipment)) return false;

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
      if (!isEquipmentAvailable(ex.equipmentOptions, profile.equipment)) return false;
      if (!isSafeForLimitations(ex, profile.limitations)) return false;
      if (!isDifficultySuitable(ex.difficulty, profile.fitnessLevel)) return false;
      if (alreadySelectedIds.has(ex.id)) return false;
      return true;
    });
    if (fallback.length === 0) {
      fallback = catalog.filter((ex) => {
        if (ex.category === "stretching") return false;
        if (!isEquipmentAvailable(ex.equipmentOptions, profile.equipment)) return false;
        if (!isSafeForLimitations(ex, profile.limitations)) return false;
        if (!isDifficultySuitable(ex.difficulty, profile.fitnessLevel)) return false;
        if (alreadySelectedIds.has(ex.id)) return false;
        return true;
      });
    }
    if (fallback.length === 0) {
      fallback = catalog.filter((ex) => {
        if (ex.category === "stretching") return false;
        if (!isEquipmentAvailable(ex.equipmentOptions, profile.equipment)) return false;
        if (!isSafeForLimitations(ex, profile.limitations)) return false;
        if (alreadySelectedIds.has(ex.id)) return false;
        return true;
      });
    }
    if (fallback.length > 0) return fallback[0];
    return null;
  }

  // Prefer the curated core pool; fall back to the whole eligible set only when
  // the pool has nothing for this muscle under the person's equipment/limits.
  // Metcon-style fillers (jumping jacks, burpees…) only suit conditioning-minded
  // goals; in a hypertrophy/strength session they would pad a calf or core budget.
  const strengthFocused = profile.goal === "hypertrophy" || profile.goal === "strength";
  const coreEligible = eligible.filter((ex) => {
    const entry = getCoreEntry(ex.id);
    return entry && !(strengthFocused && entry.slot === "conditioning");
  });
  const candidates = coreEligible.length > 0 ? coreEligible : eligible;
  const recent = profile.recentExerciseIds?.length ? new Set(profile.recentExerciseIds) : null;

  // Step 2: Score candidates based on compound priority, pattern match, user request, and pattern diversity
  const scored = candidates.map((exercise) => {
    let score = 0;

    // Core-pool slots: staples lead by default; accessories rotate (main lifts
    // stay put so progressive overload keeps its reference), recently done
    // accessories step back, and a per-block seed reshuffles near-ties.
    const core = getCoreEntry(exercise.id);
    if (core) {
      if (usedSlotsInSession?.has(core.slot)) score -= SAME_SLOT_IN_SESSION_PENALTY;
      if (core.tier === "staple") score += STAPLE_BONUS;
      if (core.role === "main") score += MAIN_ROLE_BONUS;
      if (core.role !== "main") {
        if (recent?.has(exercise.id)) score -= RECENT_ACCESSORY_PENALTY;
        if (profile.rotationSeed) score += seededUnit(profile.rotationSeed, exercise.id) * ROTATION_JITTER;
      }
    }

    // Preferred movement pattern match
    if (preferredPattern && exercise.movementPattern === preferredPattern) {
      score += 40;
    } else if (preferredPattern && PATTERN_FAMILY[preferredPattern] && PATTERN_FAMILY[preferredPattern] === PATTERN_FAMILY[exercise.movementPattern]) {
      score += SAME_FAMILY_PATTERN_BONUS;
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
      score -= CROSS_SESSION_REPEAT_PENALTY;
    }

    // Foundation exercise preference
    if (/bench press|squat|deadlift|overhead press|barbell row|pull-up|lat pulldown|hip thrust|leg press|dip/i.test(exercise.name)) {
      score += 10;
    }

    // Use the best tool the person actually has: loaded moves beat band/bodyweight
    // versions when weights are available, and gym sessions favour gym equipment.
    const loaded = LOADED_EQUIPMENT.filter((item) => profile.equipment.includes(item) || profile.equipment.includes("gym"));
    const usesLoaded = exercise.equipmentOptions.some((option) => option.some((item) => loaded.includes(item)));
    const lightOnly = exercise.equipmentOptions.every((option) => option.every((item) => item === "bodyweight" || item === "bands"));
    if (loaded.length > 0 && usesLoaded) score += profile.environment === "gym" ? 25 : 20;
    if (loaded.length > 0 && lightOnly && exercise.compoundOrIsolation === "compound") score -= 15;
    // Someone who said they own a band (or TRX, ball…) should see it used, not only bodyweight moves.
    const ownedGear = profile.equipment.filter((item) => item !== "bodyweight" && item !== "gym");
    if (!usesLoaded && exercise.equipmentOptions.some((option) => option.length > 0 && option.every((item) => ownedGear.includes(item)))) score += 18;

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
  const usedSlots = new Set<string>();

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
      usedSlots,
    );

    if (exercise) {
      selectedIds.add(exercise.id);
      const coreSlot = getCoreEntry(exercise.id)?.slot;
      if (coreSlot) usedSlots.add(coreSlot);
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
