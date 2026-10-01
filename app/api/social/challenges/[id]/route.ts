import { authenticateRequest } from "../../../../../lib/api-auth.ts";
import { rateLimit, tooManyRequests } from "../../../../../lib/rate-limit.ts";
import { isUuid, socialUserClient } from "../../../../../lib/social.ts";

export const runtime = "edge";

export async function PATCH(request: Request, { params }: { params: Promise<{ id: string }> }) {
  const auth = await authenticateRequest(request);
  if ("error" in auth) return auth.error;
  const limited = rateLimit(`social-challenges-respond:${auth.user.id}`, 30, 60_000);
  if (!limited.ok) return tooManyRequests(limited.retryAfterSeconds);

  const { id } = await params;
  if (!isUuid(id)) return Response.json({ error: "Geçersiz meydan okuma kimliği." }, { status: 400 });

  const payload = await request.json().catch(() => null) as Record<string, unknown> | null;
  const status = payload?.status === "joined" || payload?.status === "declined" ? payload.status : null;
  if (!status) return Response.json({ error: "Geçersiz durum." }, { status: 400 });

  const client = socialUserClient(request);
  if (!client) return Response.json({ error: "Servis yapılandırılmamış." }, { status: 503 });

  const { data, error } = await client.from("challenge_participants")
    .update({ status, responded_at: new Date().toISOString() })
    .eq("challenge_id", id)
    .eq("user_id", auth.user.id)
    .eq("status", "invited")
    .select("challenge_id, status")
    .maybeSingle();
  if (error) return Response.json({ error: "Davet güncellenemedi." }, { status: 500 });
  if (!data) return Response.json({ error: "Bekleyen davet bulunamadı." }, { status: 404 });
  return Response.json({ challengeId: data.challenge_id, status: data.status });
}

export async function DELETE(request: Request, { params }: { params: Promise<{ id: string }> }) {
  const auth = await authenticateRequest(request);
  if ("error" in auth) return auth.error;
  const limited = rateLimit(`social-challenges-leave:${auth.user.id}`, 30, 60_000);
  if (!limited.ok) return tooManyRequests(limited.retryAfterSeconds);

  const { id } = await params;
  if (!isUuid(id)) return Response.json({ error: "Geçersiz meydan okuma kimliği." }, { status: 400 });

  const client = socialUserClient(request);
  if (!client) return Response.json({ error: "Servis yapılandırılmamış." }, { status: 503 });

  // Yaratıcıysa "challenges" satırı silinir (cascade ile katılımcılar da gider);
  // değilse yalnız kendi katılım kaydı silinir (ayrılma).
  const deletedChallenge = await client.from("challenges").delete().eq("id", id).eq("creator_id", auth.user.id).select("id").maybeSingle();
  if (deletedChallenge.error) return Response.json({ error: "Meydan okuma silinemedi." }, { status: 500 });
  if (deletedChallenge.data) return new Response(null, { status: 204 });

  const left = await client.from("challenge_participants").delete().eq("challenge_id", id).eq("user_id", auth.user.id).select("challenge_id").maybeSingle();
  if (left.error) return Response.json({ error: "Meydan okumadan ayrılınamadı." }, { status: 500 });
  if (!left.data) return Response.json({ error: "Kayıt bulunamadı." }, { status: 404 });
  return new Response(null, { status: 204 });
}
