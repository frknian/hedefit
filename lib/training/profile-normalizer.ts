// Training Profile Normalizer

import { QUESTION } from "../onboarding-questions.ts";
import { normalizeAnswers } from "../goal-plan.ts";
import { extractSessionMinutes, extractWeeklyDays } from "../training-profile.ts";
import type {
  ConditioningLevel,
  EnvironmentType,
  FitnessLevel,
  GoalType,
  LimitationArea,
  MobilityLevel,
  MuscleGroup,
  TrainingProfile,
} from "./types.ts";

function safeString(val: unknown): string {
  return typeof val === "string" ? val.trim() : "";
}

function safeNumber(val: unknown): number | undefined {
  if (typeof val === "number" && Number.isFinite(val)) return val;
  if (typeof val === "string") {
    const parsed = Number(val.replace(",", "."));
    if (Number.isFinite(parsed)) return parsed;
  }
  return undefined;
}

export function normalizeGoal(text: string): GoalType {
  const lower = text.toLowerCase();
  if (lower.includes("kas") || lower.includes("hacim") || lower.includes("büyüt") || lower.includes("hypertrophy") || lower.includes("muscle") || lower.includes("bodybuilding")) {
    return "hypertrophy";
  }
  if (lower.includes("güç") || lower.includes("kuvvet") || lower.includes("strength") || lower.includes("power")) {
    return "strength";
  }
  if (lower.includes("kilo ver") || lower.includes("zayıf") || lower.includes("weight loss") || lower.includes("yağ yak") || lower.includes("fat loss")) {
    return "weight_loss";
  }
  if (lower.includes("kondisyon") || lower.includes("dayanık") || lower.includes("endurance") || lower.includes("stamina") || lower.includes("cardio") || lower.includes("koşu")) {
    return "endurance";
  }
  return "general_fitness";
}

export function normalizeFitnessLevel(experienceText: string, levelText: string, recentFrequency: string): { level: FitnessLevel; detrained: boolean } {
  const expLower = `${experienceText} ${levelText}`.toLowerCase();
  const freqLower = recentFrequency.toLowerCase();
  const detrained = freqLower.startsWith("0 gün") || freqLower.includes("0 gun") || freqLower.includes("hiç") || freqLower.includes("ara ver");

  let baseLevel: FitnessLevel = "beginner";
  if (expLower.includes("ileri") || expLower.includes("advanced") || expLower.includes("uzun")) {
    baseLevel = "advanced";
  } else if (expLower.includes("orta") || expLower.includes("intermediate") || expLower.includes("düzenli")) {
    baseLevel = "intermediate";
  }

  // Conflict Resolution: If user claims advanced or intermediate but hasn't trained in past 3 months
  if (detrained && baseLevel !== "beginner") {
    return { level: "beginner", detrained: true };
  }

  return { level: baseLevel, detrained };
}

export function normalizeLimitations(injuryText: string, painAreasFromAdaptation: string[] = []): LimitationArea[] {
  const combined = `${injuryText} ${painAreasFromAdaptation.join(" ")}`.toLowerCase();
  const list = new Set<LimitationArea>();

  if (combined.includes("diz") || combined.includes("knee")) list.add("knee");
  if (combined.includes("omuz") || combined.includes("shoulder")) list.add("shoulder");
  if (combined.includes("bel") || combined.includes("lower back") || combined.includes("sırt") || combined.includes("omurga")) list.add("lower_back");
  if (combined.includes("boyun") || combined.includes("neck")) list.add("neck");
  if (combined.includes("bilek") || combined.includes("wrist")) list.add("wrist");
  if (combined.includes("kalça") || combined.includes("hip")) list.add("hip");
  if (combined.includes("dirsek") || combined.includes("elbow")) list.add("elbow");

  return Array.from(list);
}

export function normalizeEnvironment(envText: string): EnvironmentType {
  const lower = envText.toLowerCase();
  if (lower.includes("salon") || lower.includes("gym") || lower.includes("fitness center")) return "gym";
  if (lower.includes("açık") || lower.includes("dış") || lower.includes("park") || lower.includes("outdoor")) return "outdoor";
  return "home";
}

export function normalizeEquipment(equipText: string, env: EnvironmentType): string[] {
  const lower = equipText.toLowerCase();
  if (lower.includes("ekipman yok") || lower.includes("no equipment") || lower.trim() === "yok") {
    return ["bodyweight"];
  }

  if (env === "gym") {
    return ["gym", "barbell", "dumbbell", "kettlebell", "bands", "cable", "machine", "bodyweight", "bench", "pull-up bar"];
  }

  const list = new Set<string>(["bodyweight"]);

  if (lower.includes("dambıl") || lower.includes("dumbbell")) list.add("dumbbell");
  if (lower.includes("barbell") || lower.includes("halter")) list.add("barbell");
  if (lower.includes("lastik") || lower.includes("band") || lower.includes("direnç")) list.add("bands");
  if (lower.includes("kettlebell")) list.add("kettlebell");
  if (lower.includes("sehpa") || lower.includes("bench")) list.add("bench");
  if (lower.includes("makara") || lower.includes("cable")) list.add("cable");
  if (lower.includes("barfiks") || lower.includes("pull-up bar") || lower.includes("pull_up_bar")) list.add("pull-up bar");
  if (lower.includes("trx") || lower.includes("halka") || lower.includes("suspension") || lower.includes("rings")) list.add("suspension");
  if (lower.includes("pilates topu") || lower.includes("denge topu") || lower.includes("stability ball") || lower.includes("stability_ball")) list.add("stability ball");
  if (lower.includes("atlama ipi") || lower.includes("ip atla") || lower.includes("jump rope") || lower.includes("jump_rope")) list.add("jump rope");
  if (lower.includes("tekerlek") || lower.includes("ab wheel") || lower.includes("ab_wheel")) list.add("ab wheel");
  if (lower.includes("dip")) list.add("dip station");

  return Array.from(list);
}

export function normalizePriorityMuscles(goalText: string, noteText: string): MuscleGroup[] {
  const combined = `${goalText} ${noteText}`.toLowerCase();
  const priorities: MuscleGroup[] = [];

  if (combined.includes("göğüs") || combined.includes("chest")) priorities.push("chest");
  if (combined.includes("sırt") || combined.includes("kanat") || combined.includes("back") || combined.includes("lat")) priorities.push("back");
  if (combined.includes("omuz") || combined.includes("shoulder")) priorities.push("shoulders");
  if (combined.includes("bacak") || combined.includes("quad") || combined.includes("leg")) priorities.push("quadriceps");
  if (combined.includes("kalça") || combined.includes("glute") || combined.includes("popo")) priorities.push("glutes");
  if (combined.includes("kol") || combined.includes("biceps") || combined.includes("triceps") || combined.includes("arm")) {
    priorities.push("biceps");
    priorities.push("triceps");
  }
  if (combined.includes("karın") || combined.includes("core") || combined.includes("six pack") || combined.includes("abs")) priorities.push("core");

  return priorities;
}

export function normalizeTrainingProfile(payload: Record<string, unknown>): TrainingProfile {
  const history = Array.isArray(payload.history) ? payload.history.map(safeString) : [];
  const goalPlan = normalizeAnswers(payload.goalPlan);

  const rawGoal = `${history[QUESTION.goal] || ""} ${safeString(payload.goal)}`;
  const goal = normalizeGoal(rawGoal);

  const experience = history[QUESTION.experience] || safeString(payload.experience);
  const levelText = history[QUESTION.level] || "";
  const recentFrequency = history[QUESTION.recentFrequency] || "";
  const { level: fitnessLevel, detrained } = normalizeFitnessLevel(experience, levelText, recentFrequency);

  const freqText = goalPlan ? `${goalPlan.weeklyDays} gün` : history[QUESTION.availableDays] || recentFrequency || "3 gün";
  let trainingDaysPerWeek = goalPlan ? goalPlan.weeklyDays : extractWeeklyDays(freqText, 3);
  trainingDaysPerWeek = Math.max(2, Math.min(6, trainingDaysPerWeek));

  const sessionDurationMinutes = goalPlan
    ? goalPlan.sessionMinutes
    : extractSessionMinutes(history[QUESTION.sessionMinutes] || safeString(payload.sessionMinutes), 50);

  const environment = normalizeEnvironment(safeString(payload.environment) || safeString(payload.trainingPlace) || history[QUESTION.location] || "");
  const equipment = normalizeEquipment(safeString(payload.equipment) || safeString(payload.equipmentAccess) || history[QUESTION.equipment] || "", environment);

  const adaptation = payload.adaptation && typeof payload.adaptation === "object" ? (payload.adaptation as Record<string, unknown>) : null;
  const painAreasFromAdaptation = Array.isArray(adaptation?.painAreas) ? adaptation.painAreas.map(safeString) : [];
  const limitations = normalizeLimitations(safeString(payload.painAreas) || history[QUESTION.injuries] || "", painAreasFromAdaptation);

  const freeNote = history[QUESTION.freeNote] || safeString(payload.note);
  const priorityMuscles = normalizePriorityMuscles(rawGoal, freeNote);

  const dailyMovement = (history[QUESTION.dailyMovement] || "").toLowerCase();
  const conditioningLevel: ConditioningLevel =
    dailyMovement.includes("yüksek") || dailyMovement.includes("fiziksel") ? "high"
    : dailyMovement.includes("orta") ? "moderate"
    : "low";

  const sleep = (history[QUESTION.sleep] || "").toLowerCase();
  const mobilityLevel: MobilityLevel =
    sleep.includes("iyi") || sleep.includes("düzenli") ? "high"
    : sleep.includes("orta") ? "moderate"
    : "low";

  const userRequestedExercises = Array.isArray(payload.requestedExercises) ? payload.requestedExercises.map(safeString).filter(Boolean) : [];

  const rawFingerprint = JSON.stringify({
    goal,
    fitnessLevel,
    trainingDaysPerWeek,
    sessionDurationMinutes,
    environment,
    equipment,
    limitations,
    detrained,
  });
  const clientFingerprint = [...rawFingerprint].reduce((acc, char) => (acc * 31 + char.charCodeAt(0)) % 1000007, 7).toString(36).toUpperCase();

  return {
    goal,
    rawGoalText: rawGoal,
    fitnessLevel,
    trainingDaysPerWeek,
    sessionDurationMinutes: Math.max(20, Math.min(120, sessionDurationMinutes)),
    equipment,
    environment,
    limitations,
    priorityMuscles,
    conditioningLevel,
    mobilityLevel,
    detrained,
    age: safeNumber(payload.age),
    gender: safeString(payload.gender) || undefined,
    heightCm: safeNumber(payload.height),
    weightKg: safeNumber(payload.weight),
    userRequestedExercises,
    clientFingerprint,
  };
}
