import { authenticateRequest } from "../../../../lib/api-auth.ts";
import { rateLimit, tooManyRequests } from "../../../../lib/rate-limit.ts";
import { socialUserClient } from "../../../../lib/social.ts";

export const runtime = "edge";

export async function GET(request: Request) {
  const auth = await authenticateRequest(request);
  if ("error" in auth) return auth.error;
  const limited = rateLimit(`social-leaderboard:${auth.user.id}`, 60, 60_000);
  if (!limited.ok) return tooManyRequests(limited.retryAfterSeconds);

  const client = socialUserClient(request);
  if (!client) return Response.json({ error: "Servis yapılandırılmamış." }, { status: 503 });

  const { data, error } = await client.rpc("hedefit_weekly_leaderboard");
  if (error) return Response.json({ error: "Sıralama yüklenemedi." }, { status: 500 });

  const rows = (data ?? []) as { user_id: string; username: string | null; display_name: string | null; avatar_path: string | null; weekly_xp: number }[];
  const entries = rows.map((row, index) => ({
    rank: index + 1,
    weeklyXp: row.weekly_xp,
    isCurrentUser: row.user_id === auth.user.id,
    user: { id: row.user_id, username: row.username, displayName: row.display_name, avatarPath: row.avatar_path },
  }));
  return Response.json({ entries });
}
