import { createClient } from "@supabase/supabase-js";
import { normalizeSupabaseUrl } from "../../../../lib/supabase/url.ts";
import { authenticateRequest } from "../../../../lib/api-auth.ts";
import { rateLimit, tooManyRequests } from "../../../../lib/rate-limit.ts";
import { SSV_ALLOWED_FEATURES } from "../../../../lib/ads/ssv.ts";

export const runtime = "nodejs";

/** Günlük reklam bonusu tavanı; SQL grant_usage_bonus_for_user ile aynı olmalı. */
const MAX_BONUS_PER_DAY = 3;

/**
 * Reklam izledikten sonra istemci, AdMob'un sunucuya iletecağı doğrulamayı BEKLERKEN bu ucu yoklar.
 * Yalnız OKUR: hak vermez. Hak yalnızca imzalı AdMob callback'iyle verilir (app/api/ads/ssv);
 * "reklam izlendi" diyen bir POST kanıt sayılmaz, bu yüzden POST yoktur.
 */
export async function GET(request: Request) {
  const auth = await authenticateRequest(request);
  if ("error" in auth) return auth.error;
  const limited = rateLimit(`ad-bonus-read:${auth.user.id}`, 60, 60_000);
  if (!limited.ok) return tooManyRequests(limited.retryAfterSeconds);

  const feature = SSV_ALLOWED_FEATURES.find((candidate) => candidate === new URL(request.url).searchParams.get("feature"));
  if (!feature) return Response.json({ error: "Geçersiz özellik." }, { status: 400 });

  const url = normalizeSupabaseUrl(process.env.NEXT_PUBLIC_SUPABASE_URL);
  const secretKey = process.env.SUPABASE_SECRET_KEY;
  if (!url || !secretKey) return Response.json({ error: "Servis yapılandırılmamış." }, { status: 503 });

  const admin = createClient(url, secretKey, { auth: { persistSession: false, autoRefreshToken: false } });
  const { data, error } = await admin.rpc("ad_bonus_today", { p_user_id: auth.user.id, p_feature: feature });
  if (error || typeof data !== "number") return Response.json({ error: "Bonus okunamadı." }, { status: 503 });
  return Response.json({ bonusCount: data, maxBonus: MAX_BONUS_PER_DAY });
}
