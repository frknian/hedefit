import { authenticateRequest } from "../../../../lib/api-auth.ts";
import { rateLimit, tooManyRequests } from "../../../../lib/rate-limit.ts";
import { socialUserClient } from "../../../../lib/social.ts";

export const runtime = "edge";

// Sosyal gizlilik tercihi: kullanıcı adıyla aramada görünme.
// Kapalıyken kullanıcı arama sonuçlarında çıkmaz; kullanıcı adını bilen biri
// yine de arkadaşlık isteği gönderebilir (bkz. 20261001120000_social_discoverability.sql).

export async function GET(request: Request) {
  const auth = await authenticateRequest(request);
  if ("error" in auth) return auth.error;
  const limited = rateLimit(`social-settings-read:${auth.user.id}`, 60, 60_000);
  if (!limited.ok) return tooManyRequests(limited.retryAfterSeconds);

  const client = socialUserClient(request);
  if (!client) return Response.json({ error: "Servis yapılandırılmamış." }, { status: 503 });

  const { data, error } = await client.rpc("hedefit_get_discoverable");
  if (error) return Response.json({ error: "Ayar yüklenemedi." }, { status: 500 });
  // Arkadaşlarla ilerleme paylaşımı (level, seri, challenge'lar). Sütun yoksa varsayılan açık.
  const share = await client.from("profiles").select("share_progress_with_friends").eq("id", auth.user.id).maybeSingle();
  return Response.json({ discoverable: data !== false, shareProgress: share.error ? true : share.data?.share_progress_with_friends !== false });
}

export async function PATCH(request: Request) {
  const auth = await authenticateRequest(request);
  if ("error" in auth) return auth.error;
  const limited = rateLimit(`social-settings-write:${auth.user.id}`, 20, 60_000);
  if (!limited.ok) return tooManyRequests(limited.retryAfterSeconds);

  const payload = await request.json().catch(() => null) as Record<string, unknown> | null;
  if (typeof payload?.shareProgress === "boolean") {
    const shareClient = socialUserClient(request);
    if (!shareClient) return Response.json({ error: "Servis yapılandırılmamış." }, { status: 503 });
    const shared = await shareClient.rpc("hedefit_set_share_progress", { p_value: payload.shareProgress });
    if (shared.error) return Response.json({ error: "Ayar kaydedilemedi." }, { status: 500 });
    return Response.json({ shareProgress: shared.data !== false });
  }
  if (typeof payload?.discoverable !== "boolean") {
    return Response.json({ error: "Geçersiz değer." }, { status: 400 });
  }

  const client = socialUserClient(request);
  if (!client) return Response.json({ error: "Servis yapılandırılmamış." }, { status: 503 });

  const { data, error } = await client.rpc("hedefit_set_discoverable", { p_value: payload.discoverable });
  if (error) {
    if (error.code === "P0002") return Response.json({ error: "Profil bulunamadı." }, { status: 404 });
    return Response.json({ error: "Ayar kaydedilemedi." }, { status: 500 });
  }
  return Response.json({ discoverable: data === true });
}
