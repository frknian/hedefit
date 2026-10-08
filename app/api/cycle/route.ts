import { authenticateRequest } from "../../../lib/api-auth.ts";
import { rateLimit, tooManyRequests } from "../../../lib/rate-limit.ts";
import { describeCycle, validateCycleProfile } from "../../../lib/cycle.ts";
import { deleteCycleData, loadCycleState, saveCycleProfile } from "../../../lib/health-store.ts";
import { resolveRotationDate } from "../../../lib/training/rotation.ts";

export const runtime = "edge";

/**
 * İsteğe bağlı döngü takibi. Her uç nokta kullanıcının kendi jetonuyla çalışır (RLS).
 * Tablolar henüz kurulmadıysa 503 `cycle_unavailable` döner; istemci özelliği gizler/atlar,
 * başka hiçbir akış etkilenmez.
 *
 * Bu uç noktanın yanıtları ve istekleri BİLEREK günlüğe yazılmaz (hassas sağlık verisi).
 */
const unavailable = () => Response.json({ error: "cycle_unavailable" }, { status: 503 });
const failed = () => Response.json({ error: "cycle_failed" }, { status: 500 });
const today = (localDate: unknown) => resolveRotationDate(localDate).toISOString().slice(0, 10);

export async function GET(request: Request) {
  const auth = await authenticateRequest(request);
  if ("error" in auth) return auth.error;
  const limit = rateLimit(`cycle-read:${auth.user.id}`, 60, 60_000);
  if (!limit.ok) return tooManyRequests(limit.retryAfterSeconds);
  const loaded = await loadCycleState(request);
  if (!loaded.ok) return loaded.reason === "unavailable" ? unavailable() : failed();
  const day = today(new URL(request.url).searchParams.get("localDate"));
  return Response.json({ profile: loaded.value.profile, personalization: loaded.value.personalization, state: describeCycle(loaded.value.profile, day) });
}

export async function PUT(request: Request) {
  const auth = await authenticateRequest(request);
  if ("error" in auth) return auth.error;
  const limit = rateLimit(`cycle-write:${auth.user.id}`, 30, 60_000);
  if (!limit.ok) return tooManyRequests(limit.retryAfterSeconds);
  let body: Record<string, unknown>;
  try {
    body = await request.json() as Record<string, unknown>;
  } catch {
    return Response.json({ error: "invalid_body" }, { status: 400 });
  }
  const day = today(body.localDate);
  const validated = validateCycleProfile(body, day);
  if (!validated.ok) return Response.json({ error: validated.error }, { status: 400 });
  const saved = await saveCycleProfile(request, auth.user.id, validated.value);
  if (!saved.ok) return saved.reason === "unavailable" ? unavailable() : failed();
  return Response.json({ profile: validated.value, state: describeCycle(validated.value, day) });
}

/** Döngü verisini kalıcı olarak siler (takibi kapatmak ayrı: PUT trackingEnabled=false veriyi saklar). */
export async function DELETE(request: Request) {
  const auth = await authenticateRequest(request);
  if ("error" in auth) return auth.error;
  const limit = rateLimit(`cycle-write:${auth.user.id}`, 30, 60_000);
  if (!limit.ok) return tooManyRequests(limit.retryAfterSeconds);
  const removed = await deleteCycleData(request, auth.user.id);
  if (!removed.ok) return removed.reason === "unavailable" ? unavailable() : failed();
  return Response.json({ deleted: true });
}
