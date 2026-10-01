import { authenticateRequest } from "../../../../../lib/api-auth.ts";
import { rateLimit, tooManyRequests } from "../../../../../lib/rate-limit.ts";
import { socialUserClient } from "../../../../../lib/social.ts";

export const runtime = "edge";

export async function GET(request: Request) {
  const auth = await authenticateRequest(request);
  if ("error" in auth) return auth.error;
  const limited = rateLimit(`social-search:${auth.user.id}`, 30, 60_000);
  if (!limited.ok) return tooManyRequests(limited.retryAfterSeconds);

  const query = new URL(request.url).searchParams.get("q") ?? "";
  if (query.trim().length < 2) return Response.json({ users: [] });

  const client = socialUserClient(request);
  if (!client) return Response.json({ error: "Servis yapılandırılmamış." }, { status: 503 });

  const { data, error } = await client.rpc("hedefit_search_users", { p_query: query });
  if (error) return Response.json({ error: "Kullanıcı aranamadı." }, { status: 500 });

  const users = (data ?? []).map((row: { id: string; username: string | null; display_name: string | null; avatar_path: string | null }) => ({
    id: row.id,
    username: row.username,
    displayName: row.display_name,
    avatarPath: row.avatar_path,
  }));
  return Response.json({ users });
}
