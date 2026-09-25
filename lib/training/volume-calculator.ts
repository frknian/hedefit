// Weekly Muscle Volume Calculator (Science-Based Volume Landmarks)

import type {
  GoalType,
  FitnessLevel,
  MuscleGroup,
  TrainingProfile,
  WeeklyVolumeTargets,
  ALL_MUSCLE_GROUPS,
} from "./types.ts";

interface VolumeLandmark {
  mev: number; // Minimum Effective Volume (sets/week)
  mav: number; // Maximum Adaptive Volume (sets/week)
  mrv: number; // Maximum Recoverable Volume (sets/week)
}

// Science-based landmarks based on muscle group, level, and primary goal
function getMuscleLandmarks(muscle: MuscleGroup, level: FitnessLevel, goal: GoalType): VolumeLandmark {
  const isMajor = ["chest", "back", "quadriceps", "hamstrings", "glutes"].includes(muscle);

  let mev = 6;
  let mav = 10;
  let mrv = 14;

  if (level === "beginner") {
    if (isMajor) {
      mev = 6;
      mav = goal === "hypertrophy" ? 12 : goal === "strength" ? 10 : 8;
      mrv = 14;
    } else {
      mev = 4;
      mav = goal === "hypertrophy" ? 8 : 6;
      mrv = 10;
    }
  } else if (level === "intermediate") {
    if (isMajor) {
      mev = 8;
      mav = goal === "hypertrophy" ? 16 : goal === "strength" ? 14 : 12;
      mrv = 18;
    } else {
      mev = 6;
      mav = goal === "hypertrophy" ? 12 : 10;
      mrv = 14;
    }
  } else {
    // Advanced
    if (isMajor) {
      mev = 10;
      mav = goal === "hypertrophy" ? 18 : goal === "strength" ? 16 : 14;
      mrv = 22;
    } else {
      mev = 8;
      mav = goal === "hypertrophy" ? 14 : 12;
      mrv = 16;
    }
  }

  // Weight loss: moderate volume to preserve lean mass without exceeding recovery
  if (goal === "weight_loss") {
    mav = Math.max(mev, mav - 2);
    mrv = Math.max(mav, mrv - 3);
  }

  // Endurance / General: balanced moderate volume
  if (goal === "endurance" || goal === "general_fitness") {
    mav = Math.max(mev, mav - 2);
    mrv = Math.max(mav, mrv - 2);
  }

  return { mev, mav, mrv };
}

export function calculateWeeklyVolumeTargets(profile: TrainingProfile): WeeklyVolumeTargets {
  const targets = {} as WeeklyVolumeTargets;
  const days = profile.trainingDaysPerWeek;

  const allMuscles: MuscleGroup[] = [
    "chest",
    "back",
    "shoulders",
    "quadriceps",
    "hamstrings",
    "glutes",
    "biceps",
    "triceps",
    "calves",
    "core",
  ];

  for (const muscle of allMuscles) {
    const { mev, mav, mrv } = getMuscleLandmarks(muscle, profile.fitnessLevel, profile.goal);

    let targetSets = mav;

    // Adjust for limited training days: if user has only 2 or 3 days, total weekly volume must fit into available sessions
    // Typically max 15-20 total sets per session across all muscles.
    const maxSetsForDays = days * 4; // cap per muscle so it fits in sessions
    targetSets = Math.min(targetSets, maxSetsForDays);

    // Boost priority muscles if indicated
    if (profile.priorityMuscles.includes(muscle)) {
      targetSets = Math.min(mrv, targetSets + 2);
    }

    // Reduce if detrained or has relevant limitations
    if (profile.detrained) {
      targetSets = Math.max(mev, targetSets - 2);
    }

    if (profile.limitations.includes("knee") && (muscle === "quadriceps" || muscle === "glutes")) {
      targetSets = Math.max(mev, targetSets - 2);
    }
    if (profile.limitations.includes("shoulder") && (muscle === "chest" || muscle === "shoulders")) {
      targetSets = Math.max(mev, targetSets - 2);
    }
    if (profile.limitations.includes("lower_back") && (muscle === "back" || muscle === "hamstrings")) {
      targetSets = Math.max(mev, targetSets - 2);
    }

    // Ensure minimum is at least 4 sets
    targetSets = Math.max(4, targetSets);

    targets[muscle] = {
      minWeeklySets: mev,
      targetWeeklySets: targetSets,
      maxWeeklySets: mrv,
    };
  }

  return targets;
}
