import { createClient } from "@supabase/supabase-js";
import { normalizeSupabaseUrl } from "../../../../../lib/supabase/url.ts";
import { authenticateRequest } from "../../../../../lib/api-auth.ts";
import { rateLimit, tooManyRequests } from "../../../../../lib/rate-limit.ts";
import { BillingVerificationError } from "../../../../../lib/billing/play.ts";
import { interpretAppleTransaction, verifyAppleJws } from "../../../../../lib/billing/apple.ts";

export const runtime = "nodejs";

/** iOS istemcisi {signedTransaction, productId} gönderir; plan yalnız Apple imzası doğrulanınca yazılır. */
export async function POST(request: Request) {
  const auth = await authenticateRequest(request);
  if ("error" in auth) return auth.error;
  if (auth.user.isGuest) return Response.json({ error: "Satın almadan önce hesabını kaydetmelisin." }, { status: 403 });
  const limited = rateLimit(`billing-apple:${auth.user.id}`, 20, 3_600_000);
  if (!limited.ok) return tooManyRequests(limited.retryAfterSeconds);

  const url = normalizeSupabaseUrl(process.env.NEXT_PUBLIC_SUPABASE_URL);
  const secretKey = process.env.SUPABASE_SECRET_KEY;
  if (!url || !secretKey) return Response.json({ error: "Satın alma doğrulaması yapılandırılmamış." }, { status: 503 });

  let payload: { signedTransaction?: unknown; productId?: unknown };
  try { payload = (await request.json()) as typeof payload; } catch { return Response.json({ error: "İstek okunamadı." }, { status: 400 }); }
  const { signedTransaction, productId } = payload;
  if (typeof signedTransaction !== "string" || signedTransaction.length > 16_384 || typeof productId !== "string") {
    return Response.json({ error: "Geçersiz satın alma verisi." }, { status: 400 });
  }

  let interpreted;
  try {
    interpreted = interpretAppleTransaction(verifyAppleJws(signedTransaction), productId, auth.user.id);
  } catch (error) {
    if (error instanceof BillingVerificationError) {
      console.warn("[billing] apple verification rejected", { userId: auth.user.id, code: error.code });
      return Response.json({ error: "Satın alma doğrulanamadı." }, { status: error.code === "account_mismatch" ? 409 : 400 });
    }
    console.error("[billing] apple verification failed", { userId: auth.user.id, message: (error as Error).message });
    return Response.json({ error: "Doğrulama şu an yapılamadı. Lütfen yeniden dene." }, { status: 502 });
  }

  const admin = createClient(url, secretKey, { auth: { persistSession: false, autoRefreshToken: false } });
  const { data: planTier, error: applyError } = await admin.rpc("apply_play_subscription", {
    p_user: auth.user.id,
    p_token: interpreted.token,
    p_product: interpreted.productId,
    p_base_plan: interpreted.basePlanId,
    p_tier: interpreted.tier,
    p_state: interpreted.state,
    p_expires: interpreted.expiresAt,
    p_auto_renewing: interpreted.entitled,
    p_linked_token: null,
    p_is_test: interpreted.isTest,
  });
  if (applyError) {
    if (applyError.message?.includes("token_owned_by_other_user")) return Response.json({ error: "Bu satın alma başka bir hesaba bağlı." }, { status: 409 });
    console.error("[billing] apple apply failed", { userId: auth.user.id, code: applyError.code });
    return Response.json({ error: "Plan güncellenemedi. Lütfen yeniden dene." }, { status: 500 });
  }
  return Response.json({ plan_tier: planTier, state: interpreted.state, expires_at: interpreted.expiresAt, entitled: interpreted.entitled });
}
