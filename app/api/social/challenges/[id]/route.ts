import { authenticateRequest } from "../../../../../lib/api-auth.ts";
import { rateLimit, tooManyRequests } from "../../../../../lib/rate-limit.ts";
import { isUuid, socialUserClient } from "../../../../../lib/social.ts";
import { planFromTemplate } from "../../../../../lib/challenges/catalog.ts";
import { joinChallenge } from "../../../../../lib/challenges/store.ts";
import { resolveRotationDate } from "../../../../../lib/training/rotation.ts";

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
  if (!data) return Response.json({ error: "Bekleyen davet bulunamadı ya da süresi doldu." }, { status: 404 });
  // Katalog challenge'ı ise kabul eden de aynı planla başlar (ilerleme bu bağlantı üzerinden izlenir).
  let userChallengeId: string | null = null;
  if (status === "joined") {
    const challenge = await client.from("challenges").select("template_key").eq("id", id).maybeSingle();
    const plan = typeof challenge.data?.template_key === "string" ? planFromTemplate(challenge.data.template_key) : null;
    if (plan) {
      const joined = await joinChallenge(client, plan, resolveRotationDate(payload?.localDate).toISOString().slice(0, 10), id);
      if (joined.ok) userChallengeId = joined.id;
    }
  }
  return Response.json({ challengeId: data.challenge_id, status: data.status, userChallengeId });
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
