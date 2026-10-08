import { createClient } from "@supabase/supabase-js";
import { normalizeSupabaseUrl } from "../../../../lib/supabase/url.ts";
import { authenticateRequest } from "../../../../lib/api-auth.ts";
import { rateLimit, tooManyRequests } from "../../../../lib/rate-limit.ts";
import {
  BillingVerificationError,
  PLAY_PRODUCT_TIERS,
  PlayApiError,
  acknowledgePlaySubscription,
  fetchPlaySubscription,
  interpretSubscription,
  playBillingConfigured,
} from "../../../../lib/billing/play.ts";

export const runtime = "nodejs";

const MAX_TOKEN_LENGTH = 2048;

/**
 * İstemci yalnızca {productId, purchaseToken} gönderir; plan, Google Play
 * Developer API'den doğrulanan duruma göre sunucuda yazılır. İstemcinin
 * "satın aldım" beyanı hiçbir zaman kanıt sayılmaz.
 */
export async function POST(request: Request) {
  const auth = await authenticateRequest(request);
  if ("error" in auth) return auth.error;
  if (auth.user.isGuest) {
    return Response.json({ error: "Satın almadan önce hesabını kaydetmelisin." }, { status: 403 });
  }
  const limited = rateLimit(`billing-verify:${auth.user.id}`, 20, 3_600_000);
  if (!limited.ok) return tooManyRequests(limited.retryAfterSeconds);

  const url = normalizeSupabaseUrl(process.env.NEXT_PUBLIC_SUPABASE_URL);
  const secretKey = process.env.SUPABASE_SECRET_KEY;
  if (!url || !secretKey || !playBillingConfigured()) {
    return Response.json({ error: "Satın alma doğrulaması yapılandırılmamış." }, { status: 503 });
  }

  let payload: { productId?: unknown; purchaseToken?: unknown };
  try {
    payload = (await request.json()) as typeof payload;
  } catch {
    return Response.json({ error: "İstek okunamadı." }, { status: 400 });
  }
  const { productId, purchaseToken } = payload;
  if (typeof productId !== "string" || !(productId in PLAY_PRODUCT_TIERS)) {
    return Response.json({ error: "Bilinmeyen ürün." }, { status: 400 });
  }
  if (typeof purchaseToken !== "string" || purchaseToken.length < 10 || purchaseToken.length > MAX_TOKEN_LENGTH) {
    return Response.json({ error: "Geçersiz satın alma jetonu." }, { status: 400 });
  }

  let interpreted;
  try {
    interpreted = await interpretSubscription(await fetchPlaySubscription(purchaseToken), productId, auth.user.id);
  } catch (error) {
    if (error instanceof BillingVerificationError) {
      console.warn("[billing] verification rejected", { userId: auth.user.id, code: error.code });
      return Response.json({ error: "Satın alma doğrulanamadı." }, { status: error.code === "account_mismatch" ? 409 : 400 });
    }
    if (error instanceof PlayApiError && error.status === 404) {
      return Response.json({ error: "Satın alma doğrulanamadı." }, { status: 400 });
    }
    console.error("[billing] google verification failed", { userId: auth.user.id, message: (error as Error).message });
    return Response.json({ error: "Doğrulama şu an yapılamadı. Lütfen yeniden dene." }, { status: 502 });
  }

  // Ödeme henüz tamamlanmadı (ör. nakit ödeme): yetki verme, onaylama.
  if (interpreted.state === "pending") {
    return Response.json({ status: "pending" }, { status: 202 });
  }

  const admin = createClient(url, secretKey, { auth: { persistSession: false, autoRefreshToken: false } });
  const { data: planTier, error: applyError } = await admin.rpc("apply_play_subscription", {
    p_user: auth.user.id,
    p_token: purchaseToken,
    p_product: interpreted.productId,
    p_base_plan: interpreted.basePlanId,
    p_tier: interpreted.tier,
    p_state: interpreted.state,
    p_expires: interpreted.expiresAt,
    p_auto_renewing: interpreted.autoRenewing,
    p_linked_token: interpreted.linkedToken,
    p_is_test: interpreted.isTest,
  });
  if (applyError) {
    if (applyError.message?.includes("token_owned_by_other_user")) {
      console.warn("[billing] token already bound to another user", { userId: auth.user.id });
      return Response.json({ error: "Bu satın alma başka bir hesaba bağlı." }, { status: 409 });
    }
    console.error("[billing] apply failed", { userId: auth.user.id, code: applyError.code });
    return Response.json({ error: "Plan güncellenemedi. Lütfen yeniden dene." }, { status: 500 });
  }

  // Yetki önce verilir, onay sonra: onay başarısız olursa Google 3 gün içinde iade eder,
  // ama kullanıcı ödediği hâlde plansız kalmaz. Hata loglanır; istemci yeniden deneyebilir.
  if (interpreted.entitled && interpreted.needsAcknowledge) {
    try {
      await acknowledgePlaySubscription(interpreted.productId, purchaseToken);
    } catch (error) {
      console.error("[billing] acknowledge failed", { userId: auth.user.id, message: (error as Error).message });
    }
  }

  return Response.json({
    plan_tier: planTier,
    state: interpreted.state,
    expires_at: interpreted.expiresAt,
    entitled: interpreted.entitled,
  });
}
