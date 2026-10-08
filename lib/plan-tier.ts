import { bearerToken } from "./api-auth.ts";
import { normalizePlanTier, type PlanTier } from "./entitlements.ts";
import { userClientFor } from "./health-store.ts";

/**
 * Kullanıcının katmanı (profiles.plan_tier), kendi jetonuyla (RLS) okunur. Okunamazsa "free": bilinmeyen durumda
 * daha dar olan katman uygulanır. Kota SAYACI değildir; yalnızca içerik/özellik kapısı içindir.
 */
export async function loadPlanTier(request: Request, userId: string): Promise<PlanTier> {
  const client = userClientFor(request);
  if (!client || !bearerToken(request)) return "free";
  try {
    const { data, error } = await client.from("profiles").select("plan_tier, is_premium").eq("id", userId).maybeSingle();
    if (error || !data) return "free";
    const tier = normalizePlanTier((data as { plan_tier?: unknown }).plan_tier);
    return tier === "free" && (data as { is_premium?: unknown }).is_premium === true ? "pro" : tier;
  } catch {
    return "free";
  }
}
