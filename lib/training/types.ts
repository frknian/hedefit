// Training Plan Engine Core Domain Types

export type GoalType = "hypertrophy" | "strength" | "weight_loss" | "endurance" | "general_fitness";

export type FitnessLevel = "beginner" | "intermediate" | "advanced";

export type EnvironmentType = "gym" | "home" | "outdoor";

export type ConditioningLevel = "low" | "moderate" | "high";

export type MobilityLevel = "low" | "moderate" | "high";

export type MuscleGroup =
  | "chest"
  | "back"
  | "shoulders"
  | "quadriceps"
  | "hamstrings"
  | "glutes"
  | "biceps"
  | "triceps"
  | "calves"
  | "core";

export const ALL_MUSCLE_GROUPS: MuscleGroup[] = [
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

export type MovementPattern =
  | "horizontal_push"
  | "vertical_push"
  | "horizontal_pull"
  | "vertical_pull"
  | "squat"
  | "hinge"
  | "lunge"
  | "carry"
  | "core";

export type LimitationArea = "knee" | "shoulder" | "lower_back" | "neck" | "wrist" | "hip" | "elbow";

export interface TrainingProfile {
  goal: GoalType;
  rawGoalText: string;
  fitnessLevel: FitnessLevel;
  trainingDaysPerWeek: number;
  sessionDurationMinutes: number;
  equipment: string[];
  environment: EnvironmentType;
  limitations: LimitationArea[];
  priorityMuscles: MuscleGroup[];
  conditioningLevel: ConditioningLevel;
  mobilityLevel: MobilityLevel;
  detrained: boolean;
  age?: number;
  gender?: string;
  heightCm?: number;
  weightKg?: number;
  userRequestedExercises: string[];
  clientFingerprint: string;
}

export interface StandardizedExercise {
  id: string;
  name: string;
  primaryMuscles: MuscleGroup[];
  secondaryMuscles: MuscleGroup[];
  movementPattern: MovementPattern;
  equipment: string[];
  /** Alternatives: the exercise is doable if every item of any one option is available ([] = no equipment). */
  equipmentOptions: string[][];
  difficulty: FitnessLevel;
  compoundOrIsolation: "compound" | "isolation";
  contraindications: LimitationArea[];
  estimatedDurationSeconds: number;
  category: string;
  force: string | null;
  mechanic: string | null;
  instructions: string[];
  /** RepDB-derived goal fit (muscle_gain/strength/fat_loss/general_fitness/endurance/mobility). Optional: legacy fallback rows may not have it. */
  goalCompatibility?: string[];
}

export interface MuscleVolumeTarget {
  minWeeklySets: number;
  targetWeeklySets: number;
  maxWeeklySets: number;
}

export type WeeklyVolumeTargets = Record<MuscleGroup, MuscleVolumeTarget>;

export type SplitType = "full_body" | "upper_lower" | "push_pull_legs" | "hybrid";

export type SessionFocus = "full_body" | "upper" | "lower" | "push" | "pull" | "legs" | "core_cardio";

export interface SessionMuscleBudget {
  muscle: MuscleGroup;
  exerciseCount: number;
  setsPerExercise: number;
  preferredPattern?: MovementPattern;
}

export interface PlannedSessionSlot {
  dayIndex: number;
  dayName: string; // "Pazartesi" / "Monday"
  focus: SessionFocus;
  focusDisplayName: string;
  durationMinutes: number;
  muscleBudgets: SessionMuscleBudget[];
}

export interface TrainingSplitPlan {
  splitType: SplitType;
  name: string;
  description: string;
  sessions: PlannedSessionSlot[];
}

export interface PlannedExerciseInstance {
  exercise: StandardizedExercise;
  sets: number;
  reps: string;
  restSeconds: number;
  order: number;
}

export interface PlannedWorkoutSession {
  dayIndex: number;
  dayName: string;
  focus: string;
  durationMinutes: number;
  exercises: PlannedExerciseInstance[];
}

export interface ValidationIssue {
  rule: string;
  severity: "critical" | "warning";
  message: string;
  sessionIndex?: number;
  exerciseId?: string;
  muscle?: MuscleGroup;
}

export interface ValidationResult {
  valid: boolean;
  issues: ValidationIssue[];
  metrics: {
    weeklySetsByMuscle: Record<MuscleGroup, number>;
    pushPullRatio: number;
    quadHamstringRatio: number;
    totalExercises: number;
    averageSessionDuration: number;
    duplicateCount: number;
    contraindicationViolations: number;
  };
}

export interface CompleteWorkoutPlan {
  profile: TrainingProfile;
  volumeTargets: WeeklyVolumeTargets;
  split: TrainingSplitPlan;
  sessions: PlannedWorkoutSession[];
  validation: ValidationResult;
  repaired: boolean;
  repairNotes?: string[];
}
