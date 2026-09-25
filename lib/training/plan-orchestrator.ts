// Plan Orchestrator: Complete pipeline from FitnessTestResult to validated FinalPlan

import { normalizeTrainingProfile } from "./profile-normalizer.ts";
import { calculateWeeklyVolumeTargets } from "./volume-calculator.ts";
import { generateTrainingSplit } from "./split-generator.ts";
import { populateSplitBudgets } from "./muscle-distribution.ts";
import { getStandardizedCatalog, getStandardizedExerciseById } from "./exercise-metadata.ts";
import { selectExercisesForSession } from "./exercise-selector.ts";
import { validatePlan } from "./plan-validator.ts";
import { repairPlan } from "./plan-repair-engine.ts";
import { translateExerciseLabel, translateExerciseName, turkishExerciseInstructions } from "../exercise-translations.ts";
import type {
  CompleteWorkoutPlan,
  MuscleGroup,
  PlannedWorkoutSession,
  StandardizedExercise,
  TrainingProfile,
  ValidationResult,
  WeeklyVolumeTargets,
} from "./types.ts";

export interface FormattedWorkoutExercise {
  id: string;
  name: string;
  english: string;
  area: string;
  sets: number;
  reps: string;
  restSeconds: number;
  instructions: string;
}

export interface FormattedWorkoutSession {
  dayIndex: number;
  dayName: string;
  focus: string;
  durationMinutes: number;
  exercises: FormattedWorkoutExercise[];
}

export interface GeneratedPlanOutput {
  title: string;
  profileSummary: string;
  rationale: string;
  safetyNote: string;
  analysis: {
    experienceLevel: string;
    weeklyFrequency: string;
    sessionMinutes: number;
    primaryGoal: string;
    intensity: string;
    equipmentMode: string;
    focusAreas: string[];
    adaptations: string[];
  };
  weeklySchedule: Array<{
    day: string;
    focus: string;
    durationMinutes: number;
  }>;
  progression: string[];
  workouts: FormattedWorkoutExercise[];
  sessions: FormattedWorkoutSession[];
  profile: TrainingProfile;
  split: TrainingSplitPlan;
  volumeTargets: WeeklyVolumeTargets;
  validation: ValidationResult;
  repaired: boolean;
  repairNotes?: string[];
}

function formatExercise(
  inst: PlannedWorkoutSession["exercises"][number],
  locale: "tr" | "en",
): FormattedWorkoutExercise {
  return {
    id: inst.exercise.id,
    name: translateExerciseName(inst.exercise.name, locale),
    english: inst.exercise.name,
    area: translateExerciseLabel(inst.exercise.primaryMuscles[0], locale),
    sets: inst.sets,
    reps: inst.reps,
    restSeconds: inst.restSeconds,
    instructions: turkishExerciseInstructions(
      {
        name: inst.exercise.name,
        category: inst.exercise.category,
        force: inst.exercise.force,
        primaryMuscles: inst.exercise.primaryMuscles,
      },
      locale,
    ).join(" "),
  };
}

function buildConsolidatedWorkouts(
  sessions: PlannedWorkoutSession[],
  profile: TrainingProfile,
  locale: "tr" | "en",
): FormattedWorkoutExercise[] {
  const targetCount =
    profile.sessionDurationMinutes <= 25 ? 3
    : profile.sessionDurationMinutes >= 60 ? 6
    : profile.sessionDurationMinutes >= 45 ? 5 : 4;

  if (sessions.length === 0) return [];
  if (sessions.length <= 3 && sessions[0].focus.toLowerCase().includes("vücut")) {
    return sessions[0].exercises.slice(0, targetCount).map((e) => formatExercise(e, locale));
  }

  // Consolidate major compound exercises across sessions for the primary workouts array
  const picked: PlannedWorkoutSession["exercises"][number][] = [];
  const majorOrder: MuscleGroup[] = ["quadriceps", "chest", "back", "hamstrings", "shoulders", "glutes", "core"];

  for (const muscle of majorOrder) {
    if (picked.length >= targetCount) break;
    for (const session of sessions) {
      const match = session.exercises.find(
        (inst) => inst.exercise.primaryMuscles.includes(muscle) && !picked.some((p) => p.exercise.id === inst.exercise.id),
      );
      if (match) {
        picked.push(match);
        break;
      }
    }
  }

  for (const session of sessions) {
    for (const inst of session.exercises) {
      if (picked.length >= targetCount) break;
      if (!picked.some((p) => p.exercise.id === inst.exercise.id)) {
        picked.push(inst);
      }
    }
  }

  return picked.map((e) => formatExercise(e, locale));
}

export function generateWorkoutPlan(
  payload: Record<string, unknown>,
  clientCatalog?: unknown[],
  locale: "tr" | "en" = "tr",
): GeneratedPlanOutput {
  // Step 1: Normalize profile
  const profile = normalizeTrainingProfile(payload);

  // Step 2: Calculate weekly volume targets
  const volumeTargets = calculateWeeklyVolumeTargets(profile);

  // Step 3: Generate optimal training split
  const rawSplit = generateTrainingSplit(profile, locale);

  // Step 4: Determine session muscle budgets before exercise selection
  const splitWithBudgets = populateSplitBudgets(rawSplit, profile, volumeTargets);

  // Step 5: Exercise selection from standardized catalog
  const fullCatalog = getStandardizedCatalog();
  let catalog = fullCatalog;
  if (Array.isArray(clientCatalog) && clientCatalog.length > 0) {
    const clientIds = new Set(
      clientCatalog.map((c) => (typeof c === "object" && c && "id" in c ? String(c.id) : "")).filter(Boolean),
    );
    const filtered = fullCatalog.filter((ex) => clientIds.has(ex.id));
    if (filtered.length >= 3) {
      catalog = filtered;
    }
  }
  const usedExerciseIdsAcrossSplit = new Set<string>();
  let plannedSessions: PlannedWorkoutSession[] = splitWithBudgets.sessions.map((slot) => {
    const session = selectExercisesForSession(slot, catalog, profile, usedExerciseIdsAcrossSplit);
    session.exercises.forEach((e) => usedExerciseIdsAcrossSplit.add(e.exercise.id));
    return session;
  });

  // Step 6: Validate plan against 13 core physiological rules
  let validation = validatePlan(plannedSessions, profile, volumeTargets);
  let repaired = false;
  let repairNotes: string[] = [];

  // Step 7: Repair if needed
  if (!validation.valid || validation.issues.some((i) => i.severity === "critical")) {
    const repairResult = repairPlan(plannedSessions, profile, volumeTargets, catalog);
    plannedSessions = repairResult.sessions;
    validation = repairResult.validation;
    repaired = repairResult.repaired;
    repairNotes = repairResult.repairNotes;
  }

  // Step 8: Format sessions
  const formattedSessions: FormattedWorkoutSession[] = plannedSessions.map((session) => ({
    dayIndex: session.dayIndex,
    dayName: session.dayName,
    focus: session.focus,
    durationMinutes: session.durationMinutes,
    exercises: session.exercises.map((e) => formatExercise(e, locale)),
  }));

  // Workouts representation
  const primaryWorkouts = buildConsolidatedWorkouts(plannedSessions, profile, locale);

  const focusAreas = Array.from(
    new Set(
      plannedSessions
        .flatMap((s) => s.exercises)
        .flatMap((e) => e.exercise.primaryMuscles)
        .map((m) => translateExerciseLabel(m, locale))
        .filter(Boolean),
    ),
  );

  const goalText =
    locale === "en"
      ? profile.goal === "hypertrophy" ? "Hypertrophy & Muscle Growth"
        : profile.goal === "strength" ? "Strength Development"
        : profile.goal === "weight_loss" ? "Fat Loss & Conditioning"
        : profile.goal === "endurance" ? "Endurance & Stamina"
        : "General Fitness"
      : profile.goal === "hypertrophy" ? "Kas Geliştirme & Hacim"
        : profile.goal === "strength" ? "Kuvvet & Güçlenme"
        : profile.goal === "weight_loss" ? "Kilo Verme & Yağ Yakımı"
        : profile.goal === "endurance" ? "Kondisyon & Dayanıklılık"
        : "Genel Fitness";

  const safetyNote =
    profile.limitations.length > 0
      ? locale === "en"
        ? `Adjusted for ${profile.limitations.join(", ")} restrictions. Stop immediately if you experience joint pain and maintain strict form.`
        : `${profile.limitations.map((l) => (l === "knee" ? "Diz" : l === "shoulder" ? "Omuz" : l === "lower_back" ? "Bel" : l)).join(", ")} hassasiyeti gözetilerek riskli hareketler elendi. Eklemlere binen baskıyı kontrol altında tut.`
      : locale === "en"
        ? "Ensure thorough warm-up before working sets and rest adequately between compound movements."
        : "Çalışma setlerinden önce ısınma turlarını ihmal etme ve formun bozulduğu ağırlıklardan kaçın.";

  const title =
    locale === "en"
      ? `${profile.trainingDaysPerWeek}-Day ${splitWithBudgets.name}`
      : `${profile.trainingDaysPerWeek} Günlük ${splitWithBudgets.name}`;

  const profileSummary =
    locale === "en"
      ? `Tailored ${profile.sessionDurationMinutes}-minute periodized routine for ${goalText.toLowerCase()}.`
      : `${goalText} hedefin için ${profile.sessionDurationMinutes} dakikalık periyotlandırılmış antrenman programı.`;

  const rationale =
    locale === "en"
      ? `Systematically balanced using ${splitWithBudgets.name} with optimal 48-72h recovery between sessions, calibrated set volume, and push/pull ratio balance.`
      : `${splitWithBudgets.name} bölünmesiyle seanslar arası 48-72 saatlik toparlanma süresi korundu. İtme/çekme ve ön/arka bacak dengesi bilimsel hacim hedefleriyle optimize edildi.`;

  const adaptations =
    locale === "en"
      ? [
          `Matched exactly to ${profile.trainingDaysPerWeek} days/week and ${profile.sessionDurationMinutes} minutes/session.`,
          `Calibrated weekly set volume according to ${profile.fitnessLevel} MEV/MAV thresholds.`,
          profile.limitations.length > 0
            ? `Protected ${profile.limitations.join(", ")} by eliminating contraindicative movements.`
            : "Movement patterns structured with push/pull and knee/hip balance.",
        ]
      : [
          `Haftalık ${profile.trainingDaysPerWeek} gün ve seans başına ${profile.sessionDurationMinutes} dakika taahhüdüne uyarlandı.`,
          `${profile.fitnessLevel === "beginner" ? "Başlangıç" : profile.fitnessLevel === "intermediate" ? "Orta" : "İleri"} seviye MEV/MAV hacim eşiklerine göre set sayıları belirlendi.`,
          profile.limitations.length > 0
            ? `${profile.limitations.map((l) => (l === "knee" ? "Diz" : l === "shoulder" ? "Omuz" : l === "lower_back" ? "Bel" : l)).join(", ")} bölgesi için riskli açı ve hareketler elendi.`
            : "İtme, çekme, çömelme ve menteşe kalıpları dengeli dağıtıldı.",
        ];

  const weeklySchedule = splitWithBudgets.sessions.map((slot) => ({
    day: slot.dayName,
    focus: slot.focusDisplayName,
    durationMinutes: slot.durationMinutes,
  }));

  const progression =
    locale === "en"
      ? [
          "Week 1: Focus on mastering technique, movement tempo, and finding proper working weights.",
          "Week 2: Complete all planned sets and repetitions with solid control.",
          "Week 3: Gradually add 1-2 repetitions or micro-load within the designated rep range.",
          "Week 4: Consolidate strength gains or perform a light recovery deload if fatigue accumulates.",
        ]
      : [
          "1. Hafta: Hareket formlarına, tempoya ve doğru çalışma ağırlıklarını bulmaya odaklan.",
          "2. Hafta: Belirlenen tüm set ve tekrar sayılarını formunu bozmadan eksiksiz tamamla.",
          "3. Hafta: Tekrar aralığının üst sınırına ulaştığında ağırlığı kontrollü şekilde artır.",
          "4. Hafta: Gelişimi sabitle ve yüksek yorgunluk hissedersen hafif deload uygula.",
        ];

  return {
    title,
    profileSummary,
    rationale,
    safetyNote,
    analysis: {
      experienceLevel:
        profile.fitnessLevel === "beginner"
          ? locale === "en" ? "Beginner" : "Başlangıç"
          : profile.fitnessLevel === "intermediate"
          ? locale === "en" ? "Intermediate" : "Orta seviye"
          : locale === "en" ? "Advanced" : "İleri seviye",
      weeklyFrequency: `${profile.trainingDaysPerWeek} ${locale === "en" ? "days" : "gün"}`,
      sessionMinutes: profile.sessionDurationMinutes,
      primaryGoal: goalText,
      intensity:
        profile.fitnessLevel === "beginner" || profile.detrained
          ? locale === "en" ? "Low-Moderate" : "Düşük-orta"
          : locale === "en" ? "Moderate-High" : "Orta-yüksek",
      equipmentMode:
        profile.equipment.includes("gym")
          ? locale === "en" ? "Gym Full Equipment" : "Salon Ekipmanı"
          : profile.equipment.length > 0
          ? profile.equipment.join(" · ")
          : locale === "en" ? "Bodyweight" : "Vücut Ağırlığı",
      focusAreas,
      adaptations,
    },
    weeklySchedule,
    progression,
    workouts: primaryWorkouts,
    sessions: formattedSessions,
    profile,
    split: splitWithBudgets,
    volumeTargets,
    validation,
    repaired,
    repairNotes,
  };
}
