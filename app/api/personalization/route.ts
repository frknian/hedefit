import { authenticateRequest } from "../../../lib/api-auth.ts";
import { rateLimit, tooManyRequests } from "../../../lib/rate-limit.ts";
import { DEFAULT_PERSONALIZATION, deleteAllHealthData, loadCycleState, savePersonalization } from "../../../lib/health-store.ts";

export const runtime = "edge";

/**
 * Kişiselleştirme ayarları (uyarlama, döngü, AI sağlık bağlamı) ve tüm sağlık verisini silme. Kullanıcının kendi jetonuyla
 * (RLS). İstek/yanıtlar BİLEREK günlüğe yazılmaz. Tablolar yoksa 503 `personalization_unavailable`.
 */
const unavailable = () => Response.json({ error: "personalization_unavailable" }, { status: 503 });
const failed = () => Response.json({ error: "personalization_failed" }, { status: 500 });

export async function GET(request: Request) {
  const auth = await authenticateRequest(request);
  if ("error" in auth) return auth.error;
  const limit = rateLimit(`personalization-read:${auth.user.id}`, 60, 60_000);
  if (!limit.ok) return tooManyRequests(limit.retryAfterSeconds);
  const loaded = await loadCycleState(request);
  if (!loaded.ok) return loaded.reason === "unavailable" ? Response.json({ personalization: DEFAULT_PERSONALIZATION, available: false }) : failed();
  return Response.json({ personalization: loaded.value.personalization, available: true });
}

export async function PUT(request: Request) {
  const auth = await authenticateRequest(request);
  if ("error" in auth) return auth.error;
  const limit = rateLimit(`personalization-write:${auth.user.id}`, 30, 60_000);
  if (!limit.ok) return tooManyRequests(limit.retryAfterSeconds);
  const body = (await request.json().catch(() => null)) as Record<string, unknown> | null;
  if (!body || typeof body !== "object") return Response.json({ error: "invalid_body" }, { status: 400 });
  // Döngü bayrağı yalnızca döngü uç noktasından (profil + rıza birlikte) değişir; burada yalnızca KAPATILABİLİR.
  const patch = {
    ...(typeof body.adaptiveEnabled === "boolean" ? { adaptiveEnabled: body.adaptiveEnabled } : {}),
    ...(typeof body.aiHealthContextEnabled === "boolean" ? { aiHealthContextEnabled: body.aiHealthContextEnabled } : {}),
    ...(body.cycleEnabled === false ? { cycleEnabled: false } : {}),
  };
  if (!Object.keys(patch).length) return Response.json({ error: "nothing_to_update" }, { status: 400 });
  const saved = await savePersonalization(request, auth.user.id, patch);
  if (!saved.ok) return saved.reason === "unavailable" ? unavailable() : failed();
  const loaded = await loadCycleState(request);
  return Response.json({ personalization: loaded.ok ? loaded.value.personalization : { ...DEFAULT_PERSONALIZATION, ...patch } });
}

/** Tüm sağlık verisini (döngü, check-in'ler, ayarlar) kalıcı siler. */
export async function DELETE(request: Request) {
  const auth = await authenticateRequest(request);
  if ("error" in auth) return auth.error;
  const limit = rateLimit(`personalization-write:${auth.user.id}`, 30, 60_000);
  if (!limit.ok) return tooManyRequests(limit.retryAfterSeconds);
  const removed = await deleteAllHealthData(request, auth.user.id);
  if (!removed.ok) return removed.reason === "unavailable" ? unavailable() : failed();
  return Response.json({ deleted: true });
}
