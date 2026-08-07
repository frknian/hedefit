import { authenticateRequest } from "../../../../lib/api-auth.ts";
import { rateLimit, tooManyRequests } from "../../../../lib/rate-limit.ts";
import { grantAdBonus, type UsageFeature } from "../../../../lib/usage-limits.ts";

export const runtime = "edge";

const BONUS_ELIGIBLE_FEATURES: readonly UsageFeature[] = ["chat", "text_nutrition"];

export async function POST(request: Request) {
  const auth = await authenticateRequest(request);
  if ("error" in auth) return auth.error;
  const rateLimitResult = rateLimit(`ads-reward:${auth.user.id}`, 20, 60_000);
  if (!rateLimitResult.ok) return tooManyRequests(rateLimitResult.retryAfterSeconds);

  let payload: { feature?: unknown };
  try {
    payload = await request.json() as { feature?: unknown };
  } catch {
    return Response.json({ error: "İstek okunamadı" }, { status: 400 });
  }

  const feature = payload.feature as UsageFeature;
  if (!BONUS_ELIGIBLE_FEATURES.includes(feature)) {
    return Response.json({ error: "Bu özellik için reklam ödülü desteklenmiyor" }, { status: 400 });
  }

  const result = await grantAdBonus(request, feature);
  if ("error" in result) return result.error;
  return Response.json(result);
}
