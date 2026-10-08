import { authenticateRequest } from "../../../../../lib/api-auth.ts";
import { rateLimit, tooManyRequests } from "../../../../../lib/rate-limit.ts";
import { isUuid, socialUserClient } from "../../../../../lib/social.ts";

export const runtime = "edge";

/**
 * Arkadaş profili (yalnızca kabul edilmiş arkadaş). Level/XP, seri, challenge'lar ve rozetler döner;
 * kilo, kalori, döngü, uyku, beslenme ya da check-in gibi sağlık verileri sorguya hiç dahil değildir
 * (bkz. hedefit_friend_profile). Kullanıcı paylaşımı kapattıysa yalnızca ad döner (`shared: false`).
 */
export async function GET(request: Request, { params }: { params: Promise<{ id: string }> }) {
  const auth = await authenticateRequest(request);
  if ("error" in auth) return auth.error;
  const limited = rateLimit(`social-profile:${auth.user.id}`, 60, 60_000);
  if (!limited.ok) return tooManyRequests(limited.retryAfterSeconds);
  const { id } = await params;
  if (!isUuid(id)) return Response.json({ error: "not_found" }, { status: 404 });
  const client = socialUserClient(request);
  if (!client) return Response.json({ error: "challenges_unavailable" }, { status: 503 });
  const { data, error } = await client.rpc("hedefit_friend_profile", { p_friend_id: id });
  if (error) {
    if (error.message === "not_friends") return Response.json({ error: "not_friends" }, { status: 403 });
    if (error.message === "not_found") return Response.json({ error: "not_found" }, { status: 404 });
    return Response.json({ error: "challenge_failed" }, { status: 500 });
  }
  return Response.json({ profile: data });
}
