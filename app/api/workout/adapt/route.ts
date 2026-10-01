// API Route: Adaptive Workout Operations (Readiness, Replacement, Regeneration)

import { authenticateRequest } from "../../../../lib/api-auth.ts";
import { rateLimit, tooManyRequests } from "../../../../lib/rate-limit.ts";
import { evaluateReadinessAndAdapt, type DailyReadinessCheckin, type WorkoutExerciseItem } from "../../../../lib/training/readiness-adapter.ts";
import { replaceExercise, type ReplacementReason } from "../../../../lib/training/exercise-replacer.ts";
import { adaptWorkoutPlanOnTheFly, type PlanAdaptationParams } from "../../../../lib/training/plan-adapter.ts";
import { normalizeTrainingProfile } from "../../../../lib/training/profile-normalizer.ts";
import type { LimitationArea } from "../../../../lib/training/types.ts";

export const runtime = "edge";

export async function POST(request: Request) {
  const auth = await authenticateRequest(request);
  if ("error" in auth) return auth.error;

  const rateLimitResult = rateLimit(`workout-adapt:${auth.user.id}`, 20, 60_000);
  if (!rateLimitResult.ok) return tooManyRequests(rateLimitResult.retryAfterSeconds);

  let body: Record<string, unknown>;
  try {
    body = await request.json();
  } catch {
    return Response.json({ error: "Geçersiz istek gövdesi" }, { status: 400 });
  }

  const action = typeof body.action === "string" ? body.action : "";
  const locale = body.locale === "en" ? "en" : "tr";
  const rawProfile = body.profile && typeof body.profile === "object" ? (body.profile as Record<string, unknown>) : {};
  const profile = normalizeTrainingProfile(rawProfile);

  // 1. Readiness Check-in Adaptation
  if (action === "readiness_checkin") {
    const checkin = (body.checkin as DailyReadinessCheckin) || {
      energy: 7,
      sleepQuality: 7,
      fatigue: 3,
      hasSoreness: false,
      sorenessAreas: [],
      discomfortLevel: 1,
    };
    const exercises = Array.isArray(body.exercises) ? (body.exercises as WorkoutExerciseItem[]) : [];
    const result = evaluateReadinessAndAdapt(checkin, exercises, profile);
    return Response.json(result);
  }

  // 2. Exercise Replacement
  if (action === "replace_exercise") {
    const exerciseId = typeof body.exerciseId === "string" ? body.exerciseId : "";
    const reason = (typeof body.reason === "string" ? body.reason : "too_hard") as ReplacementReason;
    const sessionIds = Array.isArray(body.sessionExerciseIds) ? (body.sessionExerciseIds as string[]) : [];
    const discomfortArea = typeof body.discomfortArea === "string" ? (body.discomfortArea as LimitationArea) : undefined;

    const result = replaceExercise(exerciseId, reason, profile, sessionIds, discomfortArea);
    if (!result) {
      return Response.json({ error: "Uygun alternatif hareket bulunamadı" }, { status: 404 });
    }
    return Response.json(result);
  }

  // 3. Plan Adaptation (Shorten, travel, illness, etc.)
  if (action === "adapt_plan") {
    const exercises = Array.isArray(body.exercises) ? (body.exercises as WorkoutExerciseItem[]) : [];
    const params = (body.params as PlanAdaptationParams) || { trigger: "time_shortage", targetMinutes: 20 };
    const result = adaptWorkoutPlanOnTheFly(exercises, params, profile, locale);
    return Response.json(result);
  }

  return Response.json({ error: `Bilinmeyen işlem: '${action}'` }, { status: 400 });
}
