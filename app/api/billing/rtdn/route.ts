import { createClient } from "@supabase/supabase-js";
import { normalizeSupabaseUrl } from "../../../../lib/supabase/url.ts";
import { bearerToken } from "../../../../lib/api-auth.ts";
import { verifyGoogleOidcToken } from "../../../../lib/billing/google-oidc.ts";
import { parsePubSubPush } from "../../../../lib/billing/rtdn.ts";
import { playBillingConfigured } from "../../../../lib/billing/play.ts";
import { syncSubscriptionToken } from "../../../../lib/billing/sync.ts";

export const runtime = "nodejs";

/**
 * Google Play RTDN: Pub/Sub push abonesi bu uca POST eder.
 *
 * Güvenlik: kullanıcı oturumu yok; kimlik, Pub/Sub'ın eklediği Google imzalı OIDC jetonudur
 * (audience + beklenen servis hesabı e-postası doğrulanır). Bildirimin kendisine GÜVENİLMEZ:
 * yalnız "şu jetonu yeniden kontrol et" tetikleyicisidir; durum Google'dan yeniden okunur.
 *
 * Yanıt kodları Pub/Sub yeniden-deneme davranışını belirler: 2xx = onaylandı (bozuk/zehirli
 * mesaj dahil, sonsuz döngü olmasın), 401 = yetkisiz, 5xx = geçici hata (yeniden denenir).
 */
export async function POST(request: Request) {
  const audience = process.env.GOOGLE_PLAY_RTDN_AUDIENCE;
  const pushEmail = process.env.GOOGLE_PLAY_RTDN_SERVICE_ACCOUNT_EMAIL;
  const url = normalizeSupabaseUrl(process.env.NEXT_PUBLIC_SUPABASE_URL);
  const secretKey = process.env.SUPABASE_SECRET_KEY;
  if (!audience || !pushEmail || !url || !secretKey || !playBillingConfigured()) {
    return Response.json({ error: "RTDN yapılandırılmamış." }, { status: 503 });
  }

  const token = bearerToken(request);
  let authorized = false;
  try {
    authorized = token !== "" && await verifyGoogleOidcToken(token, { audience, email: pushEmail });
  } catch (error) {
    console.error("[billing] rtdn jwks unavailable", { message: (error as Error).message });
    return Response.json({ error: "Kimlik doğrulanamadı." }, { status: 503 });
  }
  if (!authorized) return Response.json({ error: "Yetkisiz." }, { status: 401 });

  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return Response.json({ ok: true, ignored: "malformed" });
  }
  const event = parsePubSubPush(body);
  if (!event) return Response.json({ ok: true, ignored: "malformed" });
  if (event.packageName && event.packageName !== (process.env.GOOGLE_PLAY_PACKAGE_NAME || "com.hedefit.app")) {
    return Response.json({ ok: true, ignored: "other_package" });
  }
  if (event.kind === "test") {
    console.info("[billing] rtdn test notification received");
    return Response.json({ ok: true });
  }
  if (event.kind === "other" || !event.purchaseToken) return Response.json({ ok: true, ignored: event.kind });

  const admin = createClient(url, secretKey, { auth: { persistSession: false, autoRefreshToken: false } });
  try {
    const { data: shouldProcess, error: beginError } = await admin.rpc("begin_billing_event", {
      p_message_id: event.messageId,
      p_token: event.purchaseToken,
      p_type: event.notificationType,
    });
    if (beginError) throw new Error(`begin_failed:${beginError.code}`);
    if (shouldProcess === false) return Response.json({ ok: true, duplicate: true });

    let outcome: string;
    if (event.kind === "voided") {
      const { data: tier, error } = await admin.rpc("void_play_subscription", { p_token: event.purchaseToken });
      if (error) throw new Error(`void_failed:${error.code}`);
      outcome = tier === null ? "unknown_token" : "voided";
    } else {
      outcome = await syncSubscriptionToken(admin, event.purchaseToken);
    }

    const { error: finishError } = await admin.rpc("finish_billing_event", { p_message_id: event.messageId });
    if (finishError) console.error("[billing] finish event failed", { code: finishError.code });
    console.info("[billing] rtdn processed", { type: event.notificationType, outcome });
    return Response.json({ ok: true, outcome });
  } catch (error) {
    // Geçici hata: 5xx → Pub/Sub aynı mesajı yeniden teslim eder (olay "işlenmedi" kaldı).
    console.error("[billing] rtdn processing failed", { type: event.notificationType, message: (error as Error).message });
    return Response.json({ error: "İşlenemedi." }, { status: 500 });
  }
}
