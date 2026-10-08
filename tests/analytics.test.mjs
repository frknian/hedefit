import assert from "node:assert/strict";
import test from "node:test";
import { POST } from "../app/api/analytics/route.ts";
import { ANALYTICS_EVENTS, FORBIDDEN_EVENT_PATTERN, isAnalyticsEvent } from "../lib/analytics-events.ts";
import { TEST_USER_ID, authorizedRequest, withAuthenticatedFetch, withSupabaseAuthEnv } from "./helpers/auth.mjs";

test("izin listesi: sağlık değeri taşıyan ad yok, her ad kuralına uyuyor", () => {
  for (const event of ANALYTICS_EVENTS) {
    assert.match(event, /^[a-z][a-z0-9_]{2,63}$/);
    assert.doesNotMatch(event, FORBIDDEN_EVENT_PATTERN, event);
  }
  for (const bad of ["period_day_1", "cramps_8", "pain_high", "pregnant", "random_event", "", 5, null]) assert.equal(isAnalyticsEvent(bad), false, String(bad));
  assert.ok(ANALYTICS_EVENTS.includes("menstrual_tracking_enabled") && ANALYTICS_EVENTS.includes("pilates_workout_started") && ANALYTICS_EVENTS.includes("adaptive_workout_generated"));
});

test("rota: kimlik şart, bilinmeyen olay 400, geçerli olay yalnızca olay adı + gün ile sayılır (kullanıcı kimliği yok)", async () => {
  const previousFetch = globalThis.fetch;
  const restore = withSupabaseAuthEnv();
  const previousSecret = process.env.SUPABASE_SECRET_KEY;
  process.env.SUPABASE_SECRET_KEY = "service-key";
  const rpcCalls = [];
  globalThis.fetch = withAuthenticatedFetch((input, init) => {
    if (String(input).includes("hedefit_bump_event")) { rpcCalls.push(String(init?.body ?? "")); return Response.json(null, { status: 200 }); }
    return Response.json([]);
  }, TEST_USER_ID);
  try {
    const url = "http://localhost/api/analytics";
    assert.equal((await POST(new Request(url, { method: "POST", body: "{}" }))).status, 401);
    assert.equal((await POST(authorizedRequest(url, { method: "POST", body: JSON.stringify({ event: "period_day_1" }) }))).status, 400);
    assert.equal((await POST(authorizedRequest(url, { method: "POST", body: "{" }))).status, 400);
    const ok = await POST(authorizedRequest(url, { method: "POST", body: JSON.stringify({ event: "pilates_workout_started", cycleDay: 3, userId: "x", pain: 8 }) }));
    assert.equal(ok.status, 204);
    assert.equal(rpcCalls.length, 1);
    const sent = JSON.parse(rpcCalls[0]);
    assert.deepEqual(Object.keys(sent).sort(), ["p_day", "p_event"]);
    assert.equal(sent.p_event, "pilates_workout_started");
    assert.ok(!rpcCalls[0].includes(TEST_USER_ID) && !rpcCalls[0].includes("cycleDay"));
  } finally {
    globalThis.fetch = previousFetch; restore();
    if (previousSecret === undefined) delete process.env.SUPABASE_SECRET_KEY; else process.env.SUPABASE_SECRET_KEY = previousSecret;
  }
});
