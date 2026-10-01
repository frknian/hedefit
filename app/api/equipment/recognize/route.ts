import { authenticateRequest } from "../../../../lib/api-auth.ts";
import { recognizeGymEquipment } from "../../../../lib/ai-equipment-recognizer.ts";
import { hasRemoteProvider, parseImageDataUrl } from "../../../../lib/ai/providers/openai-compatible.ts";
import { rateLimit, tooManyRequests } from "../../../../lib/rate-limit.ts";
import { checkAndConsumeUsage, outputTokenLimit, refundUsage, usageLimitExceeded } from "../../../../lib/usage-limits.ts";

export const runtime = "edge";

export async function POST(request: Request) {
  const auth = await authenticateRequest(request); if ("error" in auth) return auth.error;
  const limited = rateLimit(`equipment-recognition:${auth.user.id}`, 5, 60_000); if (!limited.ok) return tooManyRequests(limited.retryAfterSeconds);
  const body = await request.json().catch(() => ({})) as { imageDataUrl?: unknown };
  const image = typeof body.imageDataUrl === "string" ? parseImageDataUrl(body.imageDataUrl) : null;
  if (!image || image.mimeType !== "image/jpeg") return Response.json({ error: "Geçerli ve sıkıştırılmış bir JPEG ekipman fotoğrafı gönder." }, { status: 400 });
  if (!hasRemoteProvider()) return Response.json({ error: "Ekipman tanıma servisi yapılandırılmamış.", code: "VISION_NOT_CONFIGURED" }, { status: 503 });
  const usage = await checkAndConsumeUsage(request, "photo", auth.user.id); if ("error" in usage) return usage.error;
  if (!usage.allowed) return usageLimitExceeded("photo", usage.used, usage.limit);
  try {
    const recognition = await recognizeGymEquipment(image, Math.min(800, outputTokenLimit("photo", usage.planTier)));
    return Response.json({ ...recognition, usage: { used: usage.used, limit: usage.limit } });
  } catch (error) {
    console.error("[equipment-recognition] vision request failed", error instanceof Error ? error.name : "unknown");
    if (Number.isFinite(usage.limit)) await refundUsage(auth.user.id, "photo");
    return Response.json({ error: "Görüntü modelinden geçerli bir tanıma yanıtı alınamadı.", code: "VISION_INVALID_RESPONSE" }, { status: 502 });
  }
}
