// API Route: Adaptive Workout Operations (Readiness, Replacement, Regeneration)

import { authenticateRequest } from "../../../../lib/api-auth.ts";
import { rateLimit, tooManyRequests } from "../../../../lib/rate-limit.ts";
import { evaluateReadinessAndAdapt, type DailyReadinessCheckin, type WorkoutExerciseItem } from "../../../../lib/training/readiness-adapter.ts";
import { replaceExercise, type ReplacementReason } from "../../../../lib/training/exercise-replacer.ts";
import { adaptWorkoutPlanOnTheFly, type PlanAdaptationParams } from "../../../../lib/training/plan-adapter.ts";
import { normalizeTrainingProfile } from "../../../../lib/training/profile-normalizer.ts";
import type { LimitationArea } from "../../../../lib/training/types.ts";
import { buildWellnessSession, type WellnessKind } from "../../../../lib/training/wellness-session.ts";
import { loadPlanTier } from "../../../../lib/plan-tier.ts";
import { adaptTodaysPlan } from "../../../../lib/training/adaptive-engine.ts";
import { validateCheckin } from "../../../../lib/checkin.ts";
import { describeCycle } from "../../../../lib/cycle.ts";
import { adaptiveCapabilities } from "../../../../lib/entitlements.ts";
import { explainAdaptation } from "../../../../lib/ai/adaptive-explainer.ts";
import { hasRemoteProvider } from "../../../../lib/ai/providers/openai-compatible.ts";
import { loadCheckins, loadCycleState, DEFAULT_PERSONALIZATION } from "../../../../lib/health-store.ts";
import { resolveRotationDate } from "../../../../lib/training/rotation.ts";

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

  // 1b. Wellness oturumu: Pilates / düşük etkili toparlanma / duruş ve mobilite (AI yok, deterministik).
  if (action === "wellness_session") {
    const kinds: WellnessKind[] = ["pilates_today", "low_impact_recovery", "posture_mobility"];
    const kind = kinds.find((value) => value === body.kind);
    if (!kind) return Response.json({ error: "invalid_kind" }, { status: 400 });
    const minutes = typeof body.minutes === "number" && Number.isFinite(body.minutes) ? body.minutes : 20;
    const day = resolveRotationDate(body.localDate).toISOString().slice(0, 10);
    const tier = await loadPlanTier(request, auth.user.id);
    const session = buildWellnessSession({ kind, minutes, level: profile.fitnessLevel, tier, seed: `${auth.user.id}:${day}`, locale });
    return Response.json({ session, tier });
  }

  // 1c. Adaptive Training: bugünün planını check-in + (isteğe bağlı) döngü + geçmiş + katmana göre uyarlar.
  if (action === "adaptive_plan") {
    const exercises = Array.isArray(body.exercises) ? (body.exercises as WorkoutExerciseItem[]).slice(0, 20) : [];
    if (!exercises.length) return Response.json({ error: "no_exercises" }, { status: 400 });
    const day = resolveRotationDate(body.localDate).toISOString().slice(0, 10);
    const tier = await loadPlanTier(request, auth.user.id);
    const caps = adaptiveCapabilities(tier);
    // Check-in: istekte doğrulanmış olarak gelir ya da bugünkü kayıt veritabanından okunur.
    let checkin = null;
    if (body.checkin !== undefined && body.checkin !== null) {
      const validated = validateCheckin({ ...(body.checkin as object), day }, day);
      if (!validated.ok) return Response.json({ error: validated.error }, { status: 400 });
      checkin = validated.value;
    } else {
      const stored = await loadCheckins(request, day, 5);
      if (stored.ok) checkin = stored.value.find((item) => item.day === day) ?? null;
    }
    // Ayarlar ve döngü: tablolar yoksa varsayılan (uyarlama açık, döngü kapalı). Döngü yalnızca açık rıza + izinli katmanda kullanılır.
    const state = await loadCycleState(request);
    const personalization = state.ok ? state.value.personalization : DEFAULT_PERSONALIZATION;
    const cycle = state.ok && caps.cycleAdaptation && personalization.cycleEnabled ? describeCycle(state.value.profile, day) : null;
    const recent = typeof body.recentSessions3d === "number" && Number.isFinite(body.recentSessions3d) ? Math.max(0, Math.min(10, Math.floor(body.recentSessions3d))) : 0;
    const result = adaptTodaysPlan({ profile, exercises, checkin, cycle, tier, adaptiveEnabled: personalization.adaptiveEnabled, recentSessions3d: recent, locale, seed: `${auth.user.id}:${day}` });
    // Premium: kişiye özel AI açıklaması şablonun ÜZERİNE eklenir; başarısızlıkta şablon geçerli kalır.
    let aiExplanation: string | null = null;
    if (body.explain === true && caps.aiAdaptiveCoach && result.adapted && hasRemoteProvider() && rateLimit(`adapt-explain:${auth.user.id}`, 6, 3_600_000).ok) {
      aiExplanation = await explainAdaptation({ result, locale });
    }
    return Response.json({ result, tier, aiExplanation });
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
