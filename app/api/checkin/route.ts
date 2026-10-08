import { authenticateRequest } from "../../../lib/api-auth.ts";
import { rateLimit, tooManyRequests } from "../../../lib/rate-limit.ts";
import { adaptiveCapabilities } from "../../../lib/entitlements.ts";
import { readinessFromCheckin, summarizeTrend, validateCheckin } from "../../../lib/checkin.ts";
import { deleteCheckins, loadCheckins, loadCycleState, saveCheckin } from "../../../lib/health-store.ts";
import { describeCycle } from "../../../lib/cycle.ts";
import { loadPlanTier } from "../../../lib/plan-tier.ts";
import { parseIsoDate } from "../../../lib/cycle.ts";
import { resolveRotationDate } from "../../../lib/training/rotation.ts";

export const runtime = "edge";

/**
 * Günlük check-in. Kullanıcının kendi jetonuyla (RLS). Yanıtlar BİLEREK günlüğe yazılmaz (sağlık verisi).
 * Geçmiş sınırı katmana bağlıdır (Free 14 / Plus 90 / Premium sınırsız); trend yalnızca Premium'dadır.
 * Tablolar kurulu değilse 503 `checkin_unavailable`: istemci özelliği sessizce gizler.
 */
const unavailable = () => Response.json({ error: "checkin_unavailable" }, { status: 503 });
const failed = () => Response.json({ error: "checkin_failed" }, { status: 500 });
const dayOf = (localDate: unknown) => resolveRotationDate(localDate).toISOString().slice(0, 10);
const DAY_MS = 86_400_000;
const shift = (iso: string, days: number) => new Date((parseIsoDate(iso) ?? 0) + days * DAY_MS).toISOString().slice(0, 10);

export async function GET(request: Request) {
  const auth = await authenticateRequest(request);
  if ("error" in auth) return auth.error;
  const limit = rateLimit(`checkin-read:${auth.user.id}`, 60, 60_000);
  if (!limit.ok) return tooManyRequests(limit.retryAfterSeconds);
  const params = new URL(request.url).searchParams;
  const today = dayOf(params.get("localDate"));
  const tier = await loadPlanTier(request, auth.user.id);
  const caps = adaptiveCapabilities(tier);
  const requested = Number.parseInt(params.get("days") ?? "14", 10);
  const days = Math.max(1, Math.min(Number.isFinite(requested) ? requested : 14, Number.isFinite(caps.checkinHistoryDays) ? caps.checkinHistoryDays : 3650));
  const loaded = await loadCheckins(request, shift(today, -(days - 1)));
  if (!loaded.ok) return loaded.reason === "unavailable" ? unavailable() : failed();
  const history = loaded.value;
  const todayCheckin = history.find((checkin) => checkin.day === today) ?? null;
  return Response.json({
    today: todayCheckin,
    history,
    historyDays: Number.isFinite(caps.checkinHistoryDays) ? caps.checkinHistoryDays : null,
    trend: caps.checkinTrends ? summarizeTrend(history, today) : null,
    tier,
  });
}

export async function PUT(request: Request) {
  const auth = await authenticateRequest(request);
  if ("error" in auth) return auth.error;
  const limit = rateLimit(`checkin-write:${auth.user.id}`, 30, 60_000);
  if (!limit.ok) return tooManyRequests(limit.retryAfterSeconds);
  let body: Record<string, unknown>;
  try {
    body = await request.json() as Record<string, unknown>;
  } catch {
    return Response.json({ error: "invalid_body" }, { status: 400 });
  }
  const today = dayOf(body.localDate);
  const validated = validateCheckin(body, today);
  if (!validated.ok) return Response.json({ error: validated.error }, { status: 400 });
  const saved = await saveCheckin(request, auth.user.id, validated.value);
  if (!saved.ok) return saved.reason === "unavailable" ? unavailable() : failed();
  // Döngü bilgisi yalnızca kullanıcı etkinleştirdiyse VE katman izin veriyorsa (Plus ve üstü) sisteme dahil edilir.
  // Kayıt/okuma her katmanda ücretsizdir; burada yalnızca adaptasyona katılması ayrılır.
  let cycle = null;
  if (adaptiveCapabilities(await loadPlanTier(request, auth.user.id)).cycleAdaptation) {
    const loaded = await loadCycleState(request);
    if (loaded.ok && loaded.value.personalization.cycleEnabled) cycle = describeCycle(loaded.value.profile, validated.value.day);
  }
  return Response.json({ checkin: validated.value, readiness: readinessFromCheckin(validated.value), cycle });
}

/** Check-in verisini kalıcı siler: `?day=YYYY-MM-DD` tek gün, `?all=true` hepsi (yanlışlıkla filtresiz silme yok). */
export async function DELETE(request: Request) {
  const auth = await authenticateRequest(request);
  if ("error" in auth) return auth.error;
  const limit = rateLimit(`checkin-write:${auth.user.id}`, 30, 60_000);
  if (!limit.ok) return tooManyRequests(limit.retryAfterSeconds);
  const params = new URL(request.url).searchParams;
  const day = params.get("day") ?? undefined;
  if (day !== undefined && parseIsoDate(day) === null) return Response.json({ error: "invalid_day" }, { status: 400 });
  if (day === undefined && params.get("all") !== "true") return Response.json({ error: "all_required" }, { status: 400 });
  const removed = await deleteCheckins(request, auth.user.id, day);
  if (!removed.ok) return removed.reason === "unavailable" ? unavailable() : failed();
  return Response.json({ deleted: true });
}
