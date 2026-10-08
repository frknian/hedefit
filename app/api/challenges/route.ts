import { authenticateRequest } from "../../../lib/api-auth.ts";
import { rateLimit, tooManyRequests } from "../../../lib/rate-limit.ts";
import { loadCheckins, userClientFor } from "../../../lib/health-store.ts";
import { normalizeTrainingProfile } from "../../../lib/training/profile-normalizer.ts";
import { resolveRotationDate } from "../../../lib/training/rotation.ts";
import { parseIsoDate } from "../../../lib/cycle.ts";
import { CHALLENGE_TEMPLATES, fitFor, minutesRange, planFromTemplate, recommendTemplates } from "../../../lib/challenges/catalog.ts";
import { buildCoachChallenge, type CoachPreferences } from "../../../lib/challenges/coach.ts";
import { joinChallenge, loadParticipantCounts, loadUserChallenges, loadXpRules, statusFor } from "../../../lib/challenges/store.ts";
import { challengeRewardXp } from "../../../lib/challenges/xp-rules.ts";

export const runtime = "edge";

/**
 * Challenge merkezi.
 *   action "hub"           → XP kuralları, katalog (gerçek katılımcı sayısı, uygunluk, "Sana Özel"), kullanıcının challenge'ları
 *   action "join"          → katalogdan katıl (plan sunucuda üretilir; istemci planı kabul edilmez)
 *   action "coach_preview" → Fit Koç kişisel planı (kaydetmeden)
 *   action "coach_start"   → aynı girdilerle planı sunucuda yeniden üretip başlat
 * Hatalar makine kodudur; metin istemcide TR/EN yerelleştirilir.
 */
const fail = (code: string) => Response.json({ error: code }, { status: statusFor(code) });
const DAY_MS = 86_400_000;

export async function POST(request: Request) {
  const auth = await authenticateRequest(request);
  if ("error" in auth) return auth.error;
  let body: Record<string, unknown>;
  try {
    body = await request.json() as Record<string, unknown>;
  } catch {
    return fail("invalid_body");
  }
  const action = typeof body.action === "string" ? body.action : "";
  const limit = rateLimit(`challenges-${action === "hub" ? "read" : "write"}:${auth.user.id}`, action === "hub" ? 60 : 20, 60_000);
  if (!limit.ok) return tooManyRequests(limit.retryAfterSeconds);

  const client = userClientFor(request);
  if (!client) return fail("challenges_unavailable");
  const today = resolveRotationDate(body.localDate).toISOString().slice(0, 10);
  const rawProfile = body.profile && typeof body.profile === "object" ? body.profile as Record<string, unknown> : {};
  const profile = normalizeTrainingProfile(rawProfile);
  const equipmentText = `${typeof rawProfile.equipment === "string" ? rawProfile.equipment : ""} ${typeof rawProfile.environment === "string" ? rawProfile.environment : ""}`;

  if (action === "hub") {
    const [rules, counts, mine] = await Promise.all([loadXpRules(client), loadParticipantCounts(client), loadUserChallenges(client, auth.user.id, today)]);
    if (!mine.ok) return fail(mine.code);
    const recommended = recommendTemplates({
      goal: profile.goal, fitnessLevel: profile.fitnessLevel, equipmentText, priorityMuscles: profile.priorityMuscles,
      mobilityLevel: profile.mobilityLevel, limitations: profile.limitations, wellnessProminent: body.wellnessProminent === true,
    });
    const templates = CHALLENGE_TEMPLATES.map((template) => ({
      key: template.key,
      category: template.category,
      difficulty: template.difficulty,
      equipment: template.equipment,
      title: template.title,
      description: template.description,
      days: template.days,
      totalDays: template.days.length,
      minutes: minutesRange(template),
      rewardXp: challengeRewardXp(template.days.length, rules),
      participants: counts[template.key] ?? 0,
      popular: template.popular === true,
      fit: fitFor(template, { fitnessLevel: profile.fitnessLevel, equipmentText }),
      recommended: recommended.includes(template.key),
    }));
    return Response.json({ rules, templates, challenges: mine.value, today });
  }

  if (action === "join") {
    const key = typeof body.key === "string" ? body.key : "";
    const plan = planFromTemplate(key);
    if (!plan) return fail("not_found");
    const joined = await joinChallenge(client, plan, today);
    if (!joined.ok) return fail(joined.code);
    return Response.json({ id: joined.id }, { status: 201 });
  }

  if (action === "coach_preview" || action === "coach_start") {
    const coachLimit = rateLimit(`challenges-coach:${auth.user.id}`, 20, 3_600_000);
    if (!coachLimit.ok) return tooManyRequests(coachLimit.retryAfterSeconds);
    const preferences = (body.preferences && typeof body.preferences === "object" ? body.preferences : {}) as CoachPreferences;
    // Son 7 günün check-in ortalaması: yalnızca planın başlangıç yoğunluğu için; plana sağlık verisi yazılmaz.
    const since = new Date((parseIsoDate(today) ?? Date.now()) - 6 * DAY_MS).toISOString().slice(0, 10);
    const checkins = await loadCheckins(request, since, 7);
    const recent = checkins.ok ? checkins.value : [];
    const average = (values: number[]) => (values.length ? values.reduce((a, b) => a + b, 0) / values.length : null);
    const recentWorkouts = typeof body.recentWorkouts14d === "number" && Number.isFinite(body.recentWorkouts14d) ? Math.max(0, Math.min(60, Math.floor(body.recentWorkouts14d))) : undefined;
    const { plan, inputs } = buildCoachChallenge(preferences, {
      goal: profile.goal, fitnessLevel: profile.fitnessLevel, equipmentText, environment: profile.environment,
      limitations: profile.limitations, priorityMuscles: profile.priorityMuscles, mobilityLevel: profile.mobilityLevel,
      sessionDurationMinutes: profile.sessionDurationMinutes, averageEnergy: average(recent.map((item) => item.energy)),
      averageSleep: average(recent.map((item) => item.sleepQuality)), recentWorkouts,
    });
    const rules = await loadXpRules(client);
    if (action === "coach_preview") {
      return Response.json({ plan, inputs, rewardXp: challengeRewardXp(plan.days.length, rules), minutes: minutesRange(plan) });
    }
    const joined = await joinChallenge(client, plan, today);
    if (!joined.ok) return fail(joined.code);
    return Response.json({ id: joined.id, plan }, { status: 201 });
  }

  return fail("unknown_action");
}
