import { authenticateRequest } from "../../../../lib/api-auth.ts";
import { rateLimit, tooManyRequests } from "../../../../lib/rate-limit.ts";
import { socialUserClient } from "../../../../lib/social.ts";

export const runtime = "edge";

export async function GET(request: Request) {
  const auth = await authenticateRequest(request);
  if ("error" in auth) return auth.error;
  const limited = rateLimit(`social-feed:${auth.user.id}`, 60, 60_000);
  if (!limited.ok) return tooManyRequests(limited.retryAfterSeconds);

  const params = new URL(request.url).searchParams;
  const limitParam = Number(params.get("limit"));
  const limit = Number.isFinite(limitParam) && limitParam > 0 ? Math.min(50, Math.floor(limitParam)) : 30;
  const before = params.get("before");

  const client = socialUserClient(request);
  if (!client) return Response.json({ error: "Servis yapılandırılmamış." }, { status: 503 });

  const { data, error } = await client.rpc("hedefit_friend_activity_feed", { p_limit: limit, p_before: before || null });
  if (error) return Response.json({ error: "Akış yüklenemedi." }, { status: 500 });

  const rows = (data ?? []) as { id: string; user_id: string; username: string | null; display_name: string | null; avatar_path: string | null; source: string; source_id: string; amount: number; occurred_at: string }[];
  const items = rows.map((row) => ({
    id: row.id,
    source: row.source,
    sourceId: row.source_id,
    amount: row.amount,
    occurredAt: row.occurred_at,
    user: { id: row.user_id, username: row.username, displayName: row.display_name, avatarPath: row.avatar_path },
  }));
  return Response.json({ items });
}
