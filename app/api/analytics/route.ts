import { createClient } from "@supabase/supabase-js";
import { authenticateRequest } from "../../../lib/api-auth.ts";
import { rateLimit, tooManyRequests } from "../../../lib/rate-limit.ts";
import { normalizeSupabaseUrl } from "../../../lib/supabase/url.ts";
import { isAnalyticsEvent } from "../../../lib/analytics-events.ts";

export const runtime = "edge";

/**
 * Olay SAYACI: gövde yalnızca `{ event }` okunur (başka alan yok sayılır). Kimlik doğrulaması yalnızca kötüye kullanımı
 * sınırlamak (hız sınırı) içindir; sayaç satırına kullanıcı kimliği YAZILMAZ. Başarısızlık sessizdir (204): analitik
 * hiçbir akışı bozmaz. İstek ve yanıt günlüğe yazılmaz.
 */
export async function POST(request: Request) {
  const auth = await authenticateRequest(request);
  if ("error" in auth) return auth.error;
  const limit = rateLimit(`analytics:${auth.user.id}`, 60, 60_000);
  if (!limit.ok) return tooManyRequests(limit.retryAfterSeconds);
  const body = (await request.json().catch(() => null)) as { event?: unknown } | null;
  if (!body || !isAnalyticsEvent(body.event)) return Response.json({ error: "unknown_event" }, { status: 400 });
  const url = normalizeSupabaseUrl(process.env.NEXT_PUBLIC_SUPABASE_URL);
  const secretKey = process.env.SUPABASE_SECRET_KEY;
  if (!url || !secretKey) return new Response(null, { status: 204 });
  try {
    const admin = createClient(url, secretKey, { auth: { persistSession: false, autoRefreshToken: false } });
    await admin.rpc("hedefit_bump_event", { p_day: new Date().toISOString().slice(0, 10), p_event: body.event });
  } catch {
    // Sessizce yut: analitik başarısızlığı kullanıcıya yansımaz.
  }
  return new Response(null, { status: 204 });
}
