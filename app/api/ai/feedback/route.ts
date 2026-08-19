import { createClient } from "@supabase/supabase-js";
import { authenticateRequest, bearerToken } from "../../../../lib/api-auth.ts";
import { normalizeSupabaseUrl } from "../../../../lib/supabase/url.ts";
import { rateLimit, tooManyRequests } from "../../../../lib/rate-limit.ts";

export const runtime = "edge";

/**
 * AI yanıtına 👍/👎.
 *
 * GİZLİLİK: mesajın METNİ saklanmaz — yalnızca hangi sağlayıcı/model/prompt
 * sürümünün beğenildiği. Sohbet içeriğini ikinci bir tabloya kopyalamak,
 * kullanıcının sağlık verisini gereksiz yere çoğaltmak olurdu
 * (bkz. db/migrations/20260819_ai_memory.sql ai_feedback).
 */
export async function POST(request: Request) {
  const auth = await authenticateRequest(request);
  if ("error" in auth) return auth.error;
  const limit = rateLimit(`ai-feedback:${auth.user.id}`, 60, 60_000);
  if (!limit.ok) return tooManyRequests(limit.retryAfterSeconds);

  let payload: Record<string, unknown>;
  try {
    payload = await request.json() as Record<string, unknown>;
  } catch {
    return Response.json({ error: "Geri bildirim okunamadı" }, { status: 400 });
  }

  const messageId = typeof payload.messageId === "string" ? payload.messageId.slice(0, 64) : "";
  const rating = payload.rating === 1 || payload.rating === -1 ? payload.rating : null;
  if (!messageId || rating === null) return Response.json({ error: "Geçersiz geri bildirim" }, { status: 400 });

  const url = normalizeSupabaseUrl(process.env.NEXT_PUBLIC_SUPABASE_URL);
  const anonKey = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY;
  if (!url || !anonKey) return Response.json({ error: "Servis yapılandırılmamış." }, { status: 503 });

  // Kullanıcının KENDİ jetonuyla; izolasyonu RLS sağlar, uygulama kodu değil.
  const client = createClient(url, anonKey, {
    auth: { persistSession: false, autoRefreshToken: false },
    global: { headers: { Authorization: `Bearer ${bearerToken(request)}` } },
  });

  const text = (value: unknown, max: number) => (typeof value === "string" && value ? value.slice(0, max) : null);
  const { error } = await client.from("ai_feedback").upsert({
    user_id: auth.user.id,
    message_id: messageId,
    rating,
    provider: text(payload.provider, 40),
    model: text(payload.model, 80),
    prompt_version: text(payload.promptVersion, 20),
    category: text(payload.category, 40),
  }, { onConflict: "user_id,message_id" });

  if (error) {
    // Geri bildirim bir iyileştirmedir; tablo yoksa kullanıcıya hata
    // göstermek yerine sessizce yok sayılır (sohbet akışı bozulmasın).
    console.error("[ai-feedback] upsert failed", error.code);
    return Response.json({ stored: false });
  }
  return Response.json({ stored: true });
}
