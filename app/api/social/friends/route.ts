import { authenticateRequest } from "../../../../lib/api-auth.ts";
import { rateLimit, tooManyRequests } from "../../../../lib/rate-limit.ts";
import { isValidUsername, socialUserClient, toFriendJson, type FriendshipRow } from "../../../../lib/social.ts";

export const runtime = "edge";

export async function GET(request: Request) {
  const auth = await authenticateRequest(request);
  if ("error" in auth) return auth.error;
  const limited = rateLimit(`social-friends-list:${auth.user.id}`, 60, 60_000);
  if (!limited.ok) return tooManyRequests(limited.retryAfterSeconds);

  const client = socialUserClient(request);
  if (!client) return Response.json({ error: "Servis yapılandırılmamış." }, { status: 503 });

  const { data, error } = await client.rpc("hedefit_list_friendships");
  if (error) return Response.json({ error: "Arkadaşlar yüklenemedi." }, { status: 500 });

  const rows = (data ?? []) as FriendshipRow[];
  return Response.json({
    friends: rows.filter((r) => r.status === "accepted").map(toFriendJson),
    incomingRequests: rows.filter((r) => r.status === "pending" && r.is_incoming).map(toFriendJson),
    outgoingRequests: rows.filter((r) => r.status === "pending" && !r.is_incoming).map(toFriendJson),
  });
}

export async function POST(request: Request) {
  const auth = await authenticateRequest(request);
  if ("error" in auth) return auth.error;
  const limited = rateLimit(`social-friends-request:${auth.user.id}`, 20, 60_000);
  if (!limited.ok) return tooManyRequests(limited.retryAfterSeconds);

  const payload = await request.json().catch(() => null) as Record<string, unknown> | null;
  const username = typeof payload?.username === "string" ? payload.username.trim().toLowerCase() : "";
  if (!isValidUsername(username)) return Response.json({ error: "Geçersiz kullanıcı adı." }, { status: 400 });

  const client = socialUserClient(request);
  if (!client) return Response.json({ error: "Servis yapılandırılmamış." }, { status: 503 });

  const { data, error } = await client.rpc("hedefit_send_friend_request", { p_username: username }).maybeSingle();
  if (error) {
    if (error.code === "P0002") return Response.json({ error: "Kullanıcı bulunamadı." }, { status: 404 });
    if (error.code === "22023") return Response.json({ error: "Kendine istek gönderemezsin." }, { status: 400 });
    if (error.code === "23505") return Response.json({ error: "Bu kullanıcıyla zaten bir arkadaşlık kaydın var." }, { status: 409 });
    return Response.json({ error: "İstek gönderilemedi." }, { status: 500 });
  }
  const row = data as { id: string; status: string; created_at: string };
  return Response.json({ request: { id: row.id, status: row.status, createdAt: row.created_at } }, { status: 201 });
}
