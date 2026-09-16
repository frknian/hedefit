// Muscle Distribution Engine: Determines muscle & set budget before exercise selection

import type {
  MovementPattern,
  MuscleGroup,
  PlannedSessionSlot,
  SessionMuscleBudget,
  TrainingProfile,
  TrainingSplitPlan,
  WeeklyVolumeTargets,
} from "./types.ts";

export function calculateSessionBudgets(
  slot: PlannedSessionSlot,
  slotIndex: number,
  profile: TrainingProfile,
  volumeTargets: WeeklyVolumeTargets,
): SessionMuscleBudget[] {
  const duration = profile.sessionDurationMinutes;
  const isBeginner = profile.fitnessLevel === "beginner" || profile.detrained;
  const defaultSets = isBeginner ? 3 : duration >= 50 ? 4 : 3;

  // Max exercises per session based on duration
  const maxExercises = duration <= 25 ? 3 : duration <= 40 ? 4 : duration <= 55 ? 5 : 6;

  const budgets: SessionMuscleBudget[] = [];

  const addBudget = (muscle: MuscleGroup, preferredPattern?: MovementPattern, sets = defaultSets) => {
    if (budgets.length >= maxExercises) return;
    budgets.push({
      muscle,
      exerciseCount: 1,
      setsPerExercise: sets,
      preferredPattern,
    });
  };

  switch (slot.focus) {
    case "full_body": {
      // Rotate variations across A, B, C for balanced movement patterns
      const variation = slotIndex % 3;
      if (variation === 0) {
        // Full Body A: Squat + Horizontal Push + Horizontal Pull + Hamstring + Core
        addBudget("quadriceps", "squat");
        addBudget("chest", "horizontal_push");
        addBudget("back", "horizontal_pull");
        addBudget("hamstrings", "hinge");
        if (maxExercises >= 5) addBudget("shoulders", "vertical_push");
        if (maxExercises >= 6) addBudget("core", "core", isBeginner ? 2 : 3);
      } else if (variation === 1) {
        // Full Body B: Hinge + Vertical Push + Vertical Pull + Lunge/Glute + Arm/Core
        addBudget("hamstrings", "hinge");
        addBudget("shoulders", "vertical_push");
        addBudget("back", "vertical_pull");
        addBudget("quadriceps", "lunge");
        if (maxExercises >= 5) addBudget("chest", "horizontal_push");
        if (maxExercises >= 6) addBudget("triceps", "horizontal_push", isBeginner ? 2 : 3);
      } else {
        // Full Body C: Glute/Quad + Chest + Back + Arm + Core
        addBudget("glutes", "hinge");
        addBudget("quadriceps", "squat");
        addBudget("chest", "horizontal_push");
        addBudget("back", "horizontal_pull");
        if (maxExercises >= 5) addBudget("biceps", "horizontal_pull", isBeginner ? 2 : 3);
        if (maxExercises >= 6) addBudget("core", "core", isBeginner ? 2 : 3);
      }
      break;
    }

    case "upper": {
      const variation = slotIndex % 2;
      if (variation === 0) {
        // Upper A: Heavy Horizontal Push + Heavy Pull + Vertical Push + Vertical Pull + Arms
        addBudget("chest", "horizontal_push");
        addBudget("back", "horizontal_pull");
        addBudget("shoulders", "vertical_push");
        addBudget("back", "vertical_pull");
        if (maxExercises >= 5) addBudget("chest", "horizontal_push");
        if (maxExercises >= 6) addBudget("biceps", "horizontal_pull", isBeginner ? 2 : 3);
      } else {
        // Upper B: Vertical Pull + Incline/Push + Horizontal Row + Lateral/Delt + Triceps
        addBudget("back", "vertical_pull");
        addBudget("chest", "horizontal_push");
        addBudget("back", "horizontal_pull");
        addBudget("shoulders", "vertical_push");
        if (maxExercises >= 5) addBudget("triceps", "horizontal_push", isBeginner ? 2 : 3);
        if (maxExercises >= 6) addBudget("core", "core", isBeginner ? 2 : 3);
      }
      break;
    }

    case "lower": {
      const variation = slotIndex % 2;
      if (variation === 0) {
        // Lower A: Squat + Hinge + Lunge/Single leg + Calves + Core
        addBudget("quadriceps", "squat");
        addBudget("hamstrings", "hinge");
        addBudget("glutes", "lunge");
        addBudget("quadriceps", "squat");
        if (maxExercises >= 5) addBudget("calves", "squat", isBeginner ? 2 : 3);
        if (maxExercises >= 6) addBudget("core", "core", isBeginner ? 2 : 3);
      } else {
        // Lower B: Hinge/Deadlift + Squat/Leg press + Hamstrings + Glutes + Core
        addBudget("hamstrings", "hinge");
        addBudget("quadriceps", "squat");
        addBudget("glutes", "hinge");
        addBudget("hamstrings", "hinge");
        if (maxExercises >= 5) addBudget("calves", "squat", isBeginner ? 2 : 3);
        if (maxExercises >= 6) addBudget("core", "core", isBeginner ? 2 : 3);
      }
      break;
    }

    case "push": {
      // Chest (2) + Shoulders (2) + Triceps (1-2)
      addBudget("chest", "horizontal_push");
      addBudget("shoulders", "vertical_push");
      addBudget("chest", "horizontal_push");
      addBudget("shoulders", "vertical_push");
      if (maxExercises >= 5) addBudget("triceps", "horizontal_push");
      if (maxExercises >= 6) addBudget("core", "core", isBeginner ? 2 : 3);
      break;
    }

    case "pull": {
      // Back (3: vertical, horizontal, rear) + Biceps (2) + Core (1)
      addBudget("back", "vertical_pull");
      addBudget("back", "horizontal_pull");
      addBudget("back", "horizontal_pull");
      addBudget("biceps", "horizontal_pull");
      if (maxExercises >= 5) addBudget("biceps", "horizontal_pull", isBeginner ? 2 : 3);
      if (maxExercises >= 6) addBudget("core", "core", isBeginner ? 2 : 3);
      break;
    }

    case "legs": {
      // Quads (2) + Hamstrings (2) + Glutes (1) + Calves (1)
      addBudget("quadriceps", "squat");
      addBudget("hamstrings", "hinge");
      addBudget("quadriceps", "lunge");
      addBudget("glutes", "hinge");
      if (maxExercises >= 5) addBudget("hamstrings", "hinge");
      if (maxExercises >= 6) addBudget("calves", "squat", isBeginner ? 2 : 3);
      break;
    }

    default: {
      addBudget("chest", "horizontal_push");
      addBudget("back", "horizontal_pull");
      addBudget("quadriceps", "squat");
      addBudget("hamstrings", "hinge");
      if (maxExercises >= 5) addBudget("core", "core");
    }
  }

  return budgets;
}

export function populateSplitBudgets(
  split: TrainingSplitPlan,
  profile: TrainingProfile,
  volumeTargets: WeeklyVolumeTargets,
): TrainingSplitPlan {
  // `calculateSessionBudgets` alternates between an "A" and "B" template per
  // focus (e.g. Lower A / Lower B) via `slotIndex % 2`. The GLOBAL session
  // index breaks that alternation whenever same-focus sessions land on the
  // same parity — e.g. an Upper/Lower/Upper/Lower split puts both "lower"
  // sessions at odd global indices (1, 3), so they'd both get "Lower B" and
  // never "Lower A", permanently skewing that focus's muscle coverage. A
  // per-focus counter alternates correctly regardless of where each focus
  // falls in the week.
  const focusCounters = new Map<string, number>();
  const sessions = split.sessions.map((slot) => {
    const focusIndex = focusCounters.get(slot.focus) ?? 0;
    focusCounters.set(slot.focus, focusIndex + 1);
    return {
      ...slot,
      muscleBudgets: calculateSessionBudgets(slot, focusIndex, profile, volumeTargets),
    };
  });

  return {
    ...split,
    sessions,
  };
}
