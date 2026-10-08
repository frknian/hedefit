import { authenticateRequest } from "../../../../lib/api-auth.ts";
import { rateLimit, tooManyRequests } from "../../../../lib/rate-limit.ts";
import { DEFAULT_PERSONALIZATION, loadCheckins, loadCycleState, userClientFor } from "../../../../lib/health-store.ts";
import { describeCycle } from "../../../../lib/cycle.ts";
import { adaptiveCapabilities } from "../../../../lib/entitlements.ts";
import { loadPlanTier } from "../../../../lib/plan-tier.ts";
import { isUuid } from "../../../../lib/nutrition-log.ts";
import { normalizeTrainingProfile } from "../../../../lib/training/profile-normalizer.ts";
import { resolveRotationDate } from "../../../../lib/training/rotation.ts";
import { buildWellnessSession } from "../../../../lib/training/wellness-session.ts";
import { adaptTask, nextTask } from "../../../../lib/challenges/engine.ts";
import { errorCode, loadUserChallenge, statusFor } from "../../../../lib/challenges/store.ts";

export const runtime = "edge";

/**
 * Tek challenge üzerinde işlemler:
 *   "today"    → bugünün görevi; günlük check-in'e (ve izin verildiyse döngüye) göre uyarlanır. Oturum görevi
 *                mevcut wellness üreticisiyle hareket listesine çevrilir ve MEVCUT aktif antrenman ekranında yapılır.
 *   "complete" → günü tamamla (sunucu mevcut kayıtlardan doğrular, XP idempotent)
 *   "recovery" → toparlanma günü (seri bozulmaz, antrenman sayılmaz, düşük XP)
 *   "abandon"  → bırak (XP ve tamamlanan günler korunur)
 * Check-in/döngü değerleri yanıtta ya da loglarda yer almaz; yalnızca uyarlama sonucu döner.
 */
const fail = (code: string) => Response.json({ error: code }, { status: statusFor(code) });

export async function POST(request: Request, { params }: { params: Promise<{ id: string }> }) {
  const auth = await authenticateRequest(request);
  if ("error" in auth) return auth.error;
  const limit = rateLimit(`challenge-item:${auth.user.id}`, 40, 60_000);
  if (!limit.ok) return tooManyRequests(limit.retryAfterSeconds);
  const { id } = await params;
  if (!isUuid(id)) return fail("not_found");
  let body: Record<string, unknown>;
  try {
    body = await request.json() as Record<string, unknown>;
  } catch {
    return fail("invalid_body");
  }
  const client = userClientFor(request);
  if (!client) return fail("challenges_unavailable");
  const action = typeof body.action === "string" ? body.action : "";
  const today = resolveRotationDate(body.localDate).toISOString().slice(0, 10);
  const locale = body.locale === "en" ? "en" : "tr";

  if (action === "today") {
    const loaded = await loadUserChallenge(client, auth.user.id, id, today);
    if (!loaded.ok) return fail(loaded.code);
    const challenge = loaded.value;
    const task = challenge.status === "active" && !challenge.state.todayDone ? nextTask(challenge.plan.days, challenge.state) : null;
    if (!task) return Response.json({ challenge, task: null, adaptation: null, session: null });

    const tier = await loadPlanTier(request, auth.user.id);
    const caps = adaptiveCapabilities(tier);
    const [checkins, cycleState] = await Promise.all([loadCheckins(request, today, 2), loadCycleState(request)]);
    const checkin = checkins.ok ? checkins.value.find((item) => item.day === today) ?? null : null;
    const personalization = cycleState.ok ? cycleState.value.personalization : DEFAULT_PERSONALIZATION;
    const cycle = cycleState.ok && caps.cycleAdaptation && personalization.cycleEnabled ? describeCycle(cycleState.value.profile, today) : null;
    const adaptation = adaptTask(task, checkin, { enabled: personalization.adaptiveEnabled, periodLikely: cycle?.periodLikely === true, locale });

    const effective = adaptation.task;
    let session = null;
    if (effective.kind === "session" && effective.session) {
      const rawProfile = body.profile && typeof body.profile === "object" ? body.profile as Record<string, unknown> : {};
      const profile = normalizeTrainingProfile(rawProfile);
      session = buildWellnessSession({ kind: effective.session, minutes: effective.minutes ?? 15, level: profile.fitnessLevel, tier, seed: `${auth.user.id}:${id}:${today}`, locale });
    }
    return Response.json({
      challenge,
      task,
      checkinDone: checkin !== null,
      adaptation: { adapted: adaptation.adapted, task: effective, message: adaptation.message, suggestRecovery: adaptation.suggestRecovery, completionStatus: adaptation.completionStatus },
      session,
    });
  }

  if (action === "complete" || action === "recovery") {
    const status = action === "recovery" ? "recovery" : body.status === "adapted" ? "adapted" : "completed";
    const minutes = typeof body.minutes === "number" && Number.isInteger(body.minutes) && body.minutes >= 0 && body.minutes <= 240 ? body.minutes : null;
    const sessionId = typeof body.sessionId === "string" && isUuid(body.sessionId) ? body.sessionId : null;
    const { data, error } = await client.rpc("hedefit_complete_challenge_day", {
      p_user_challenge_id: id, p_local_date: today, p_status: status, p_minutes: minutes, p_session_id: sessionId,
    });
    if (error) return fail(errorCode(error));
    const loaded = await loadUserChallenge(client, auth.user.id, id, today);
    return Response.json({ result: data, challenge: loaded.ok ? loaded.value : null });
  }

  if (action === "abandon") {
    const { error } = await client.rpc("hedefit_abandon_challenge", { p_id: id });
    if (error) return fail(errorCode(error));
    return Response.json({ abandoned: true });
  }

  return fail("unknown_action");
}
