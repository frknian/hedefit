import { createClient, type SupabaseClient } from "@supabase/supabase-js";
import { normalizeSupabaseUrl } from "../supabase/url.ts";
import { playBillingConfigured } from "./play.ts";
import { syncSubscriptionToken } from "./sync.ts";

const BATCH_LIMIT = 100;
const STALE_AFTER_MS = 3 * 86_400_000;
const EXPIRING_WITHIN_MS = 86_400_000;

export type ReconcileSummary = { checked: number; applied: number; failed: number };

/**
 * RTDN kaçarsa (Pub/Sub kesintisi, yanlış yapılandırma) planların gerçekle uyuşmasını sağlayan
 * günlük güvenlik ağı. Süresi 1 gün içinde dolacak ya da 3+ gündür güncellenmemiş ve hâlâ
 * erişim veren/askıdaki abonelikleri Google ile eşitler; ayrıca süresi dolan planları düşürür.
 */
export async function reconcileSubscriptions(admin: SupabaseClient, now = Date.now()): Promise<ReconcileSummary> {
  const soon = new Date(now + EXPIRING_WITHIN_MS).toISOString();
  const stale = new Date(now - STALE_AFTER_MS).toISOString();
  const { data, error } = await admin
    .from("subscriptions")
    .select("purchase_token,user_id")
    .in("state", ["active", "grace", "canceled", "on_hold", "paused"])
    .or(`expires_at.lt.${soon},updated_at.lt.${stale}`)
    .order("updated_at", { ascending: true })
    .limit(BATCH_LIMIT);
  if (error) throw new Error(`reconcile_query_failed:${error.code}`);

  const summary: ReconcileSummary = { checked: 0, applied: 0, failed: 0 };
  const users = new Set<string>();
  for (const row of data ?? []) {
    summary.checked += 1;
    users.add(row.user_id as string);
    try {
      const result = await syncSubscriptionToken(admin, row.purchase_token as string, now);
      if (result === "applied" || result === "expired_by_google") summary.applied += 1;
    } catch (error) {
      summary.failed += 1;
      console.error("[billing] reconcile item failed", { message: (error as Error).message });
    }
  }
  // Süresi dolmuş ama Google'a sorulmayan satırlar için planı yeniden hesapla.
  for (const userId of users) {
    const { error: recomputeError } = await admin.rpc("recompute_plan_tier", { p_user: userId });
    if (recomputeError) console.error("[billing] recompute failed", { code: recomputeError.code });
  }
  return summary;
}

/** Worker'ın zamanlanmış (cron) görevi: yapılandırma yoksa sessizce atlar. */
export async function runScheduledReconcile(): Promise<ReconcileSummary | null> {
  const url = normalizeSupabaseUrl(process.env.NEXT_PUBLIC_SUPABASE_URL);
  const secretKey = process.env.SUPABASE_SECRET_KEY;
  if (!url || !secretKey || !playBillingConfigured()) return null;
  const admin = createClient(url, secretKey, { auth: { persistSession: false, autoRefreshToken: false } });
  const summary = await reconcileSubscriptions(admin);
  // Eski RTDN kayıtlarını da temizle (en iyi çaba).
  await admin.rpc("purge_billing_events", { p_days: 90 });
  console.info("[billing] reconcile done", summary);
  return summary;
}
