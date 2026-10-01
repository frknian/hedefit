import { authenticateRequest } from "../../../../../lib/api-auth.ts";
import { rateLimit, tooManyRequests } from "../../../../../lib/rate-limit.ts";
import { isUuid, socialUserClient } from "../../../../../lib/social.ts";

export const runtime = "edge";

export async function PATCH(request: Request, { params }: { params: Promise<{ id: string }> }) {
  const auth = await authenticateRequest(request);
  if ("error" in auth) return auth.error;
  const limited = rateLimit(`social-friends-respond:${auth.user.id}`, 30, 60_000);
  if (!limited.ok) return tooManyRequests(limited.retryAfterSeconds);

  const { id } = await params;
  if (!isUuid(id)) return Response.json({ error: "Geçersiz istek kimliği." }, { status: 400 });

  const payload = await request.json().catch(() => null) as Record<string, unknown> | null;
  const status = payload?.status === "accepted" || payload?.status === "declined" ? payload.status : null;
  if (!status) return Response.json({ error: "Geçersiz durum." }, { status: 400 });

  const client = socialUserClient(request);
  if (!client) return Response.json({ error: "Servis yapılandırılmamış." }, { status: 503 });

  // RLS: yalnızca bekleyen isteğin alıcısı güncelleyebilir (bkz. migration
  // 20260929000000_friendships.sql).
  const { data, error } = await client.from("friendships")
    .update({ status, responded_at: new Date().toISOString() })
    .eq("id", id)
    .eq("addressee_id", auth.user.id)
    .eq("status", "pending")
    .select("id, status, responded_at")
    .maybeSingle();
  if (error) return Response.json({ error: "İstek güncellenemedi." }, { status: 500 });
  if (!data) return Response.json({ error: "Bekleyen istek bulunamadı." }, { status: 404 });
  return Response.json({ request: { id: data.id, status: data.status, respondedAt: data.responded_at } });
}

export async function DELETE(request: Request, { params }: { params: Promise<{ id: string }> }) {
  const auth = await authenticateRequest(request);
  if ("error" in auth) return auth.error;
  const limited = rateLimit(`social-friends-remove:${auth.user.id}`, 30, 60_000);
  if (!limited.ok) return tooManyRequests(limited.retryAfterSeconds);

  const { id } = await params;
  if (!isUuid(id)) return Response.json({ error: "Geçersiz kayıt kimliği." }, { status: 400 });

  const client = socialUserClient(request);
  if (!client) return Response.json({ error: "Servis yapılandırılmamış." }, { status: 503 });

  const { data, error } = await client.from("friendships")
    .delete()
    .eq("id", id)
    .or(`requester_id.eq.${auth.user.id},addressee_id.eq.${auth.user.id}`)
    .select("id")
    .maybeSingle();
  if (error) return Response.json({ error: "Arkadaşlık sonlandırılamadı." }, { status: 500 });
  if (!data) return Response.json({ error: "Kayıt bulunamadı." }, { status: 404 });
  return new Response(null, { status: 204 });
}
