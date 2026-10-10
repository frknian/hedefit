import { createClient } from "@supabase/supabase-js";
import { normalizeSupabaseUrl } from "../../../../../lib/supabase/url.ts";
import { APPLE_PRODUCT_TIERS, verifyAppleJws, type AppleTransaction } from "../../../../../lib/billing/apple.ts";

export const runtime = "nodejs";

type NotificationPayload = {
  notificationType?: string;
  subtype?: string;
  notificationUUID?: string;
  data?: { bundleId?: string; signedTransactionInfo?: string; signedRenewalInfo?: string };
};
type RenewalInfo = { autoRenewStatus?: number; gracePeriodExpiresDate?: number; isInBillingRetryPeriod?: boolean };

const VOID_TYPES = new Set(["REFUND", "REVOKE"]);
const EXPIRE_TYPES = new Set(["EXPIRED", "GRACE_PERIOD_EXPIRED"]);

/**
 * App Store Server Notifications V2. Uç nokta App Store Connect'te üretim ve sandbox URL'si olarak girilir.
 *
 * Güvenlik: kullanıcı oturumu yok; kimlik, Apple'ın imzaladığı JWS'tir (x5c zinciri Apple Root CA G3'e kadar
 * doğrulanır). Abonelik yalnız /apple/verify ile daha önce bağlanmış (apple:<originalTransactionId>) ise güncellenir;
 * bilinmeyen işlemler yok sayılır. Yanıt: 2xx = işlendi/yok sayıldı, 401 = imza geçersiz, 5xx = Apple yeniden dener.
 */
export async function POST(request: Request) {
  const url = normalizeSupabaseUrl(process.env.NEXT_PUBLIC_SUPABASE_URL);
  const secretKey = process.env.SUPABASE_SECRET_KEY;
  if (!url || !secretKey) return Response.json({ error: "Yapılandırılmamış." }, { status: 503 });

  let signedPayload: unknown;
  try { signedPayload = ((await request.json()) as { signedPayload?: unknown }).signedPayload; } catch { return Response.json({ ok: true, ignored: "malformed" }); }
  if (typeof signedPayload !== "string" || signedPayload.length > 65_536) return Response.json({ ok: true, ignored: "malformed" });

  let payload: NotificationPayload;
  let tx: AppleTransaction | null = null;
  let renewal: RenewalInfo = {};
  try {
    payload = verifyAppleJws(signedPayload) as unknown as NotificationPayload;
    if (payload.data?.signedTransactionInfo) tx = verifyAppleJws(payload.data.signedTransactionInfo);
    if (payload.data?.signedRenewalInfo) renewal = verifyAppleJws(payload.data.signedRenewalInfo) as unknown as RenewalInfo;
  } catch (error) {
    console.warn("[billing] apple notification rejected", { message: (error as Error).message });
    return Response.json({ error: "Yetkisiz." }, { status: 401 });
  }

  const expectedBundle = process.env.APPLE_BUNDLE_ID ?? "com.hedefit.app";
  if (payload.data?.bundleId && payload.data.bundleId !== expectedBundle) return Response.json({ ok: true, ignored: "other_bundle" });
  const type = payload.notificationType ?? "";
  if (type === "TEST") return Response.json({ ok: true });
  if (!tx?.originalTransactionId || !payload.notificationUUID) return Response.json({ ok: true, ignored: "no_transaction" });

  const token = `apple:${tx.originalTransactionId}`;
  const admin = createClient(url, secretKey, { auth: { persistSession: false, autoRefreshToken: false } });
  try {
    const { data: shouldProcess, error: beginError } = await admin.rpc("begin_billing_event", { p_message_id: payload.notificationUUID, p_token: token, p_type: `apple:${type}` });
    if (beginError) throw new Error(`begin_failed:${beginError.code}`);
    if (shouldProcess === false) return Response.json({ ok: true, duplicate: true });

    let outcome: string;
    const { data: row, error: lookupError } = await admin.from("subscriptions").select("user_id,product_id,plan_tier").eq("purchase_token", token).maybeSingle<{ user_id: string; product_id: string; plan_tier: string }>();
    if (lookupError) throw new Error(`lookup_failed:${lookupError.code}`);

    if (!row) outcome = "unknown_token"; // istemci henüz /verify çağırmadı; o akış zaten doğrular
    else if (VOID_TYPES.has(type) || typeof tx.revocationDate === "number") {
      const { error } = await admin.rpc("void_play_subscription", { p_token: token });
      if (error) throw new Error(`void_failed:${error.code}`);
      outcome = "voided";
    } else if (tx.productId in APPLE_PRODUCT_TIERS) {
      const now = Date.now();
      const graceUntil = renewal.gracePeriodExpiresDate ?? null;
      const expiresMs = tx.expiresDate ?? null;
      let state: "active" | "grace" | "canceled" | "expired" = "active";
      let expiresAt = expiresMs ? new Date(expiresMs).toISOString() : null;
      if (EXPIRE_TYPES.has(type) || (expiresMs !== null && expiresMs <= now && !(graceUntil && graceUntil > now))) state = "expired";
      else if (type === "DID_FAIL_TO_RENEW" && graceUntil && graceUntil > now) { state = "grace"; expiresAt = new Date(graceUntil).toISOString(); }
      else if (renewal.autoRenewStatus === 0) state = "canceled";
      const { error } = await admin.rpc("apply_play_subscription", {
        p_user: row.user_id,
        p_token: token,
        p_product: tx.productId,
        p_base_plan: tx.productId.endsWith("_yearly") ? "yearly" : "monthly",
        p_tier: APPLE_PRODUCT_TIERS[tx.productId],
        p_state: state,
        p_expires: expiresAt,
        p_auto_renewing: renewal.autoRenewStatus === 1,
        p_linked_token: null,
        p_is_test: tx.environment === "Sandbox" || tx.environment === "Xcode",
      });
      if (error) throw new Error(`apply_failed:${error.code}`);
      outcome = state;
    } else outcome = "unknown_product";

    const { error: finishError } = await admin.rpc("finish_billing_event", { p_message_id: payload.notificationUUID });
    if (finishError) console.error("[billing] apple finish event failed", { code: finishError.code });
    console.info("[billing] apple notification processed", { type, subtype: payload.subtype, outcome });
    return Response.json({ ok: true, outcome });
  } catch (error) {
    console.error("[billing] apple notification failed", { type, message: (error as Error).message });
    return Response.json({ error: "İşlenemedi." }, { status: 500 });
  }
}
