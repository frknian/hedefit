import assert from "node:assert/strict";
import test from "node:test";
import { DELETE, GET, PUT } from "../app/api/checkin/route.ts";
import { checkinFromRow, readinessFromCheckin, summarizeTrend, validateCheckin } from "../lib/checkin.ts";
import { evaluateReadinessAndAdapt } from "../lib/training/readiness-adapter.ts";
import { normalizeTrainingProfile } from "../lib/training/profile-normalizer.ts";
import { TEST_USER_ID, authorizedRequest, withAuthenticatedFetch, withSupabaseAuthEnv } from "./helpers/auth.mjs";

const url = "http://localhost/api/checkin";
const base = { energy: 6, sleepQuality: 7, soreness: 2, pain: 0, availableMinutes: 30 };

test("doğrulama: geçerli girdi, varsayılanlar, sınırlar", () => {
  const ok = validateCheckin({ ...base, sleepHours: 7.46 }, "2026-10-05");
  assert.equal(ok.ok, true);
  assert.deepEqual(ok.value, { day: "2026-10-05", energy: 6, sleepQuality: 7, sleepHours: 7.5, soreness: 2, pain: 0, availableMinutes: 30 });
  assert.deepEqual(validateCheckin({ energy: 5, sleepQuality: 5 }, "2026-10-05").value, { day: "2026-10-05", energy: 5, sleepQuality: 5, sleepHours: null, soreness: 0, pain: 0, availableMinutes: null });
  for (const [input, error] of [
    [{ ...base, energy: 0 }, "invalid_energy"], [{ ...base, energy: 5.5 }, "invalid_energy"], [{ ...base, sleepQuality: 11 }, "invalid_sleep"],
    [{ ...base, soreness: 11 }, "invalid_soreness"], [{ ...base, pain: -1 }, "invalid_pain"], [{ ...base, sleepHours: 30 }, "invalid_sleep_hours"],
    [{ ...base, availableMinutes: 2 }, "invalid_minutes"], [{ ...base, day: "2026-09-01" }, "invalid_day"], [{ ...base, day: "bugün" }, "invalid_day"],
  ]) assert.deepEqual(validateCheckin(input, "2026-10-05"), { ok: false, error });
  assert.equal(validateCheckin({ ...base, day: "2026-10-06" }, "2026-10-05").ok, true, "saat dilimi payı: ±1 gün");
  assert.deepEqual(validateCheckin(null, "2026-10-05"), { ok: false, error: "invalid_body" });
});

test("mevcut hazırlık adaptörüne dönüşüm: düşük enerji + kötü uyku + ağrı → uyarlama; iyi gün → uyarlama yok", () => {
  const profile = normalizeTrainingProfile({ history: ["Kilo verme", "", "", "", "Başlangıç", "", "3 gün", "30 dk", "", "Evde", "Ekipman yok", "Yok"] });
  const plan = [{ id: "plank", name: "Plank", area: "Core", sets: 3, reps: "30 sn", restSeconds: 45 }];
  const bad = readinessFromCheckin({ day: "d", energy: 2, sleepQuality: 3, sleepHours: 4, soreness: 7, pain: 8, availableMinutes: 20 });
  assert.equal(bad.hasSoreness, true); assert.equal(bad.discomfortLevel, 8); assert.ok(bad.fatigue >= 8);
  assert.equal(evaluateReadinessAndAdapt(bad, plan, profile).needsAdaptation, true);
  const good = readinessFromCheckin({ day: "d", energy: 9, sleepQuality: 9, sleepHours: 8, soreness: 0, pain: 0, availableMinutes: 45 });
  assert.equal(good.discomfortLevel, 1); assert.equal(good.hasSoreness, false);
  assert.equal(evaluateReadinessAndAdapt(good, plan, profile).needsAdaptation, false);
});

test("satır eşlemesi bozuk satırı atar; trend yön ve düşük enerji serisini hesaplar", () => {
  assert.equal(checkinFromRow({ day: "2026-10-05" }), null);
  assert.equal(checkinFromRow({ day: "2026-10-05", energy: 5, sleep_quality: 6, sleep_hours: "7.5" }).sleepHours, 7.5);
  const make = (day, energy) => ({ day, energy, sleepQuality: 6, sleepHours: null, soreness: 0, pain: 0, availableMinutes: null });
  const rows = ["05", "04", "03", "02"].map((d, i) => make(`2026-10-${d}`, 4 - 0 * i)).concat(["28", "27", "26", "25"].map((d) => make(`2026-09-${d}`, 8)));
  const trend = summarizeTrend(rows, "2026-10-05");
  assert.equal(trend.direction, "down");
  assert.equal(trend.lowEnergyStreak, 4);
  assert.equal(summarizeTrend([make("2026-10-05", 6)], "2026-10-05").direction, "unknown");
});

function withStore(planTier, handler) {
  const previousFetch = globalThis.fetch;
  const restore = withSupabaseAuthEnv();
  const calls = [];
  globalThis.fetch = withAuthenticatedFetch((input, init) => {
    const href = String(input);
    calls.push({ url: href, method: init?.method ?? "GET", body: init?.body ? String(init.body) : "" });
    if (href.includes("/rest/v1/profiles")) return Response.json(planTier ? { plan_tier: planTier, is_premium: false } : null);
    return handler(href, init);
  }, TEST_USER_ID);
  return { calls, restore: () => { globalThis.fetch = previousFetch; restore(); } };
}
const rows = (n) => Array.from({ length: n }, (_, i) => ({ day: new Date(Date.UTC(2026, 9, 5) - i * 86_400_000).toISOString().slice(0, 10), energy: 6, sleep_quality: 7, sleep_hours: 7, soreness: 1, pain: 0, available_minutes: 30 }));

test("PUT: kaydeder, hazırlık eşlemesini döner; geçersiz girdi 400 ve veritabanına gidilmez", async () => {
  const { calls, restore } = withStore("free", () => Response.json([], { status: 201 }));
  try {
    const response = await PUT(authorizedRequest(url, { method: "PUT", body: JSON.stringify({ ...base, localDate: "2026-10-05" }) }));
    assert.equal(response.status, 200);
    const body = await response.json();
    assert.equal(body.checkin.day, "2026-10-05");
    assert.equal(body.readiness.discomfortLevel, 1);
    assert.ok(calls.some((call) => call.method === "POST" && call.url.includes("daily_checkins") && call.url.includes("on_conflict=user_id%2Cday")));
    const before = calls.length;
    assert.equal((await PUT(authorizedRequest(url, { method: "PUT", body: JSON.stringify({ ...base, energy: 99 }) }))).status, 400);
    assert.equal(calls.length, before);
  } finally { restore(); }
});

test("GET: geçmiş sınırı katmana bağlı, trend yalnızca Premium'da", async () => {
  const seen = {};
  for (const tier of ["free", "plus", "pro"]) {
    const { calls, restore } = withStore(tier, () => Response.json(rows(14)));
    try {
      const response = await GET(authorizedRequest(`${url}?localDate=2026-10-05&days=500`));
      assert.equal(response.status, 200);
      const body = await response.json();
      const query = decodeURIComponent(calls.find((call) => call.url.includes("daily_checkins")).url);
      seen[tier] = { gte: query.match(/day=gte\.([0-9-]+)/)[1], trend: body.trend, historyDays: body.historyDays, today: body.today?.day };
    } finally { restore(); }
  }
  assert.equal(seen.free.gte, "2026-09-22", "Free: 14 gün");
  assert.equal(seen.plus.gte, "2026-07-08", "Plus: 90 gün");
  assert.equal(seen.pro.gte, "2025-05-24", "Premium: istenen 500 günün tamamı (sınır yok)");
  assert.equal(seen.free.trend, null); assert.equal(seen.plus.trend, null);
  assert.equal(seen.pro.trend.days, 14);
  assert.equal(seen.free.historyDays, 14); assert.equal(seen.pro.historyDays, null);
  assert.equal(seen.free.today, "2026-10-05");
});

test("tablolar yoksa 503 checkin_unavailable; kimlik şart", async () => {
  const { restore } = withStore("free", () => Response.json({ code: "PGRST205", message: "Could not find the table" }, { status: 404 }));
  try {
    for (const [handler, init] of [[GET, { method: "GET" }], [PUT, { method: "PUT", body: JSON.stringify(base) }], [DELETE, { method: "DELETE" }]]) {
      const request = authorizedRequest(handler === DELETE ? `${url}?all=true` : url, init);
      assert.equal((await handler(request)).status, 503, init.method);
    }
    for (const handler of [GET, PUT, DELETE]) assert.equal((await handler(new Request(url, { method: handler.name }))).status, 401);
  } finally { restore(); }
});

test("DELETE: tek gün ya da hepsi gerçekten silinir; filtresiz çağrı reddedilir", async () => {
  const { calls, restore } = withStore("free", () => Response.json([], { status: 200 }));
  try {
    assert.equal((await DELETE(authorizedRequest(url, { method: "DELETE" }))).status, 400, "açık all=true olmadan silme yok");
    assert.equal((await DELETE(authorizedRequest(`${url}?day=yarın`, { method: "DELETE" }))).status, 400);
    assert.equal((await DELETE(authorizedRequest(`${url}?day=2026-10-04`, { method: "DELETE" }))).status, 200);
    assert.ok(calls.some((call) => call.method === "DELETE" && call.url.includes("day=eq.2026-10-04") && call.url.includes(`user_id=eq.${TEST_USER_ID}`)));
    assert.equal((await DELETE(authorizedRequest(`${url}?all=true`, { method: "DELETE" }))).status, 200);
    const all = calls.filter((call) => call.method === "DELETE").at(-1);
    assert.ok(all.url.includes(`user_id=eq.${TEST_USER_ID}`) && !all.url.includes("day=eq."));
  } finally { restore(); }
});

test("PUT: döngü bilgisi yalnızca etkinleştirilmiş + Plus/Premium'da döner; Free ve kapalıyken null", async () => {
  const cycleRow = { tracking_enabled: true, last_period_start: "2026-10-01", cycle_length_days: 28, period_length_days: 5, regularity: "regular" };
  const handler = (enabled) => (href) => {
    if (href.includes("cycle_profiles")) return Response.json(cycleRow);
    if (href.includes("personalization_settings")) return Response.json({ adaptive_enabled: true, cycle_personalization_enabled: enabled, ai_health_context_enabled: false });
    return Response.json([], { status: 201 });
  };
  const put = async (tier, enabled) => {
    const { restore } = withStore(tier, handler(enabled));
    try { return await (await PUT(authorizedRequest(url, { method: "PUT", body: JSON.stringify({ ...base, localDate: "2026-10-05" }) }))).json(); } finally { restore(); }
  };
  assert.equal((await put("free", true)).cycle, null, "Free: takip ücretsiz ama adaptasyona katılmaz");
  assert.equal((await put("plus", false)).cycle, null, "kullanıcı kapattıysa katılmaz");
  const on = await put("plus", true);
  assert.equal(on.cycle.cycleDay, 5);
  assert.equal(on.cycle.phase, "menstrual");
  assert.equal((await put("pro", true)).cycle.cycleDay, 5);
});
