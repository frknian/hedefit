import type { SupabaseClient } from "@supabase/supabase-js";
import {
  BillingVerificationError,
  PlayApiError,
  fetchPlaySubscription,
  interpretSubscription,
} from "./play.ts";

export type SyncResult = "applied" | "unknown_token" | "expired_by_google" | "skipped";

type SubscriptionRow = { purchase_token: string; user_id: string; product_id: string; base_plan_id: string | null; plan_tier: string; expires_at: string | null };

/**
 * Bilinen bir abonelik jetonunu Google ile eşitler: durumu yeniden okur, kaydı ve planı günceller.
 * - Jeton bizde yoksa (istemci henüz /verify çağırmadı) hiçbir şey yapmaz: o akış zaten doğrular.
 * - Google jetonu tanımıyorsa (süresi uzun önce dolmuş) kaydı süresi dolmuş işaretler.
 * Geçici hatalarda fırlatır; çağıran yeniden dener.
 */
export async function syncSubscriptionToken(admin: SupabaseClient, purchaseToken: string, now = Date.now()): Promise<SyncResult> {
  const { data: row, error } = await admin
    .from("subscriptions")
    .select("purchase_token,user_id,product_id,base_plan_id,plan_tier,expires_at")
    .eq("purchase_token", purchaseToken)
    .maybeSingle<SubscriptionRow>();
  if (error) throw new Error(`subscription_lookup_failed:${error.code}`);
  if (!row) return "unknown_token";

  let interpreted;
  try {
    interpreted = await interpretSubscription(await fetchPlaySubscription(purchaseToken), row.product_id, row.user_id, now);
  } catch (caught) {
    if (caught instanceof PlayApiError && caught.status === 404) {
      await applyRow(admin, row, { state: "expired", expiresAt: row.expires_at, autoRenewing: false, linkedToken: null, isTest: false });
      return "expired_by_google";
    }
    // Ürün/hesap tutarsızlığı bu jeton için kalıcıdır; yeniden denemek yardımcı olmaz.
    if (caught instanceof BillingVerificationError) {
      console.warn("[billing] sync skipped", { code: caught.code });
      return "skipped";
    }
    throw caught;
  }
  await applyRow(admin, row, {
    state: interpreted.state,
    expiresAt: interpreted.expiresAt,
    autoRenewing: interpreted.autoRenewing,
    linkedToken: interpreted.linkedToken,
    isTest: interpreted.isTest,
  });
  return "applied";
}

async function applyRow(
  admin: SupabaseClient,
  row: SubscriptionRow,
  next: { state: string; expiresAt: string | null; autoRenewing: boolean; linkedToken: string | null; isTest: boolean },
) {
  const { error } = await admin.rpc("apply_play_subscription", {
    p_user: row.user_id,
    p_token: row.purchase_token,
    p_product: row.product_id,
    p_base_plan: row.base_plan_id,
    p_tier: row.plan_tier,
    p_state: next.state,
    p_expires: next.expiresAt,
    p_auto_renewing: next.autoRenewing,
    p_linked_token: next.linkedToken,
    p_is_test: next.isTest,
  });
  if (error) throw new Error(`apply_failed:${error.code}`);
}
