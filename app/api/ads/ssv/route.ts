import { createClient } from "@supabase/supabase-js";
import { normalizeSupabaseUrl } from "../../../../lib/supabase/url.ts";
import { SSV_ALLOWED_FEATURES, verifyAdMobSsv } from "../../../../lib/ads/ssv.ts";

export const runtime = "nodejs";

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** AdMob ad_unit parametresi yalnız sayısal kısımdır ("ca-app-pub-123/456" → "456"). */
function unitSuffix(value: string) {
  return value.includes("/") ? value.slice(value.lastIndexOf("/") + 1) : value;
}

/**
 * AdMob ödüllü reklam SSV callback'i (AdMob → sunucu, GET). Kullanıcı oturumu yok; kimlik, Google'ın
 * ECDSA imzasıdır. Hak yalnız imzalı, taze ve ilk kez görülen transaction_id için verilir.
 * İstemcinin kendi beyanı ile hak verilmez (bkz. app/api/ads/reward).
 *
 * Yanıtlar: 200 = işlendi/yok sayıldı (AdMob yeniden denemesin), 400 = imza geçersiz,
 * 503 = anahtar listesi/veritabanı geçici hatası (AdMob yeniden dener).
 */
export async function GET(request: Request) {
  const url = normalizeSupabaseUrl(process.env.NEXT_PUBLIC_SUPABASE_URL);
  const secretKey = process.env.SUPABASE_SECRET_KEY;
  if (!url || !secretKey) return Response.json({ error: "Reklam ödülü servisi yapılandırılmamış." }, { status: 503 });

  let verified;
  try {
    verified = await verifyAdMobSsv(request.url);
  } catch (error) {
    console.error("[ads-ssv] verifier keys unavailable", { message: (error as Error).message });
    return Response.json({ error: "Doğrulama anahtarları alınamadı." }, { status: 503 });
  }
  if (!verified.valid) {
    console.warn("[ads-ssv] rejected", { reason: verified.reason });
    return Response.json({ error: "Geçersiz imza." }, { status: 400 });
  }
  const { adUnit, customData, transactionId, userId } = verified.params;

  const expectedUnit = process.env.ADMOB_REWARDED_AD_UNIT_ID;
  if (expectedUnit && (!adUnit || unitSuffix(adUnit) !== unitSuffix(expectedUnit))) {
    return Response.json({ ok: true, ignored: "other_ad_unit" });
  }
  const feature = SSV_ALLOWED_FEATURES.find((candidate) => candidate === customData);
  if (!transactionId || !userId || !UUID.test(userId) || !feature) {
    return Response.json({ ok: true, ignored: "invalid_params" });
  }

  const admin = createClient(url, secretKey, { auth: { persistSession: false, autoRefreshToken: false } });
  const { data, error } = await admin.rpc("grant_ad_reward", { p_transaction_id: transactionId, p_user_id: userId, p_feature: feature });
  if (error) {
    console.error("[ads-ssv] grant failed", { code: error.code });
    return Response.json({ error: "Ödül verilemedi." }, { status: 503 });
  }
  console.info("[ads-ssv] processed", { outcome: typeof data === "string" ? data.split(":")[0] : "unknown" });
  return Response.json({ ok: true, outcome: data });
}
