import { authenticateRequest } from "../../../../lib/api-auth.ts";
import { rateLimit, tooManyRequests } from "../../../../lib/rate-limit.ts";
import { adaptiveCapabilities } from "../../../../lib/entitlements.ts";
import { loadPlanTier } from "../../../../lib/plan-tier.ts";
import { loadCycleState, DEFAULT_PERSONALIZATION } from "../../../../lib/health-store.ts";
import { describeCycle } from "../../../../lib/cycle.ts";
import { resolveRotationDate } from "../../../../lib/training/rotation.ts";
import { buildNutritionWellness, normalizeDiet } from "../../../../lib/nutrition-wellness.ts";

export const runtime = "edge";

const LEVELS = ["good", "moderate", "low", "recovery"] as const;
const KINDS = ["pilates_today", "low_impact_recovery", "posture_mobility"] as const;

/**
 * Antrenmana ve toparlanmaya göre beslenme ipuçları. İstemci yalnızca KABA antrenman bağlamı gönderir; döngü bilgisi
 * istemciden alınmaz — sunucu kendi doğruladığı izin (cycleEnabled) ve katmana (full) göre ekler. Hiçbir sağlık verisi
 * günlüğe yazılmaz ya da modele gitmez (bu uç nokta model çağırmaz).
 */
export async function POST(request: Request) {
  const auth = await authenticateRequest(request);
  if ("error" in auth) return auth.error;
  const limit = rateLimit(`nutrition-wellness:${auth.user.id}`, 30, 60_000);
  if (!limit.ok) return tooManyRequests(limit.retryAfterSeconds);
  const body = (await request.json().catch(() => null)) as Record<string, unknown> | null;
  if (!body || typeof body !== "object") return Response.json({ error: "Invalid request / Geçersiz istek" }, { status: 400 });
  const locale = body.locale === "en" ? "en" : "tr";
  const training = body.training && typeof body.training === "object" ? body.training as Record<string, unknown> : {};
  const tier = await loadPlanTier(request, auth.user.id);
  const caps = adaptiveCapabilities(tier);

  let cycle = null;
  if (caps.nutritionPersonalization === "full") {
    const state = await loadCycleState(request);
    const personalization = state.ok ? state.value.personalization : DEFAULT_PERSONALIZATION;
    if (state.ok && personalization.cycleEnabled && caps.cycleAdaptation) {
      const described = describeCycle(state.value.profile, resolveRotationDate(body.localDate).toISOString().slice(0, 10));
      if (described) cycle = { periodLikely: described.periodLikely, preMenstrualWindow: described.preMenstrualWindow };
    }
  }

  const minutes = Number(training.minutes);
  const result = buildNutritionWellness({
    tier: caps.nutritionPersonalization,
    locale,
    diet: normalizeDiet(body.diet),
    training: {
      workedOutToday: training.workedOutToday === true,
      level: LEVELS.find((item) => item === training.level),
      sessionKind: KINDS.find((item) => item === training.sessionKind),
      minutes: Number.isFinite(minutes) ? Math.max(0, Math.min(240, minutes)) : undefined,
    },
    cycle,
  });
  return Response.json(result);
}
