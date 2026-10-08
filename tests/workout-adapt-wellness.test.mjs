import assert from "node:assert/strict";
import test from "node:test";
import { POST } from "../app/api/workout/adapt/route.ts";
import { canUseModalityExercise } from "../lib/entitlements.ts";
import { getExerciseById } from "../lib/exercise-service.ts";
import { TEST_USER_ID, authorizedRequest, withAuthenticatedFetch, withSupabaseAuthEnv } from "./helpers/auth.mjs";

const url = "http://localhost/api/workout/adapt";
const post = (body) => POST(authorizedRequest(url, { method: "POST", body: JSON.stringify(body) }));

function withProfile(planTier) {
  const previousFetch = globalThis.fetch;
  const restore = withSupabaseAuthEnv();
  globalThis.fetch = withAuthenticatedFetch((input) => {
    if (String(input).includes("/rest/v1/profiles")) return Response.json(planTier ? { plan_tier: planTier, is_premium: false } : null);
    throw new TypeError("beklenmeyen istek");
  }, TEST_USER_ID);
  return () => { globalThis.fetch = previousFetch; restore(); };
}

test("wellness_session: katmana göre içerik, TR/EN dil ve dönen katman", async () => {
  for (const tier of ["free", "plus", "pro"]) {
    const restore = withProfile(tier);
    try {
      const response = await post({ action: "wellness_session", kind: "pilates_today", minutes: 20, locale: "tr", localDate: "2026-10-05" });
      assert.equal(response.status, 200);
      const body = await response.json();
      assert.equal(body.tier, tier);
      assert.equal(body.session.title, "Bugün için Pilates");
      assert.ok(body.session.exercises.length >= 5);
      for (const item of body.session.exercises) {
        const exercise = getExerciseById(item.id);
        assert.ok(canUseModalityExercise(tier, exercise.modalities, exercise.subcategories), `${tier}: ${item.id}`);
      }
    } finally { restore(); }
  }
  const restore = withProfile("pro");
  try {
    const en = await (await post({ action: "wellness_session", kind: "low_impact_recovery", minutes: 15, locale: "en" })).json();
    assert.equal(en.session.title, "Low Impact Recovery");
  } finally { restore(); }
});

test("wellness_session: profil okunamazsa Free'ye düşer; geçersiz tür 400; kimlik şart", async () => {
  const restore = withProfile(null);
  try {
    assert.equal((await (await post({ action: "wellness_session", kind: "posture_mobility" })).json()).tier, "free");
    assert.equal((await post({ action: "wellness_session", kind: "nope" })).status, 400);
    assert.equal((await POST(new Request(url, { method: "POST", body: "{}" }))).status, 401);
  } finally { restore(); }
});

const planBody = ["plank", "glute-bridge", "dead-bug", "bird-dog"].map((id) => ({ id, name: id, area: "Core", sets: 3, reps: "10", restSeconds: 60 }));
const cycleRow = { tracking_enabled: true, last_period_start: "2026-10-03", cycle_length_days: 28, period_length_days: 5, regularity: "regular" };

function withAll(planTier, { cycleEnabled = true, adaptiveEnabled = true, stored = [] } = {}) {
  const previousFetch = globalThis.fetch;
  const restore = withSupabaseAuthEnv();
  globalThis.fetch = withAuthenticatedFetch((input) => {
    const href = String(input);
    if (href.includes("/rest/v1/profiles")) return Response.json({ plan_tier: planTier, is_premium: false });
    if (href.includes("cycle_profiles")) return Response.json(cycleRow);
    if (href.includes("personalization_settings")) return Response.json({ adaptive_enabled: adaptiveEnabled, cycle_personalization_enabled: cycleEnabled, ai_health_context_enabled: false });
    if (href.includes("daily_checkins")) return Response.json(stored);
    throw new TypeError(`beklenmeyen istek: ${href}`);
  }, TEST_USER_ID);
  return () => { globalThis.fetch = previousFetch; restore(); };
}

test("adaptive_plan: istekteki check-in ile uyarlar; döngü yalnızca Plus+ ve açık rızada kullanılır", async () => {
  const body = { action: "adaptive_plan", exercises: planBody, checkin: { energy: 6, sleepQuality: 6, soreness: 2, pain: 0 }, localDate: "2026-10-05" };
  const results = {};
  for (const tier of ["free", "plus"]) {
    const restore = withAll(tier);
    try { results[tier] = (await (await post(body)).json()).result; } finally { restore(); }
  }
  assert.equal(results.free.signals.cycle, "none", "Free: döngü adaptasyona katılmaz");
  assert.equal(results.free.level, "good");
  assert.equal(results.plus.signals.cycle, "nudged", "Plus: gri bölgede küçük bağlam");
  assert.equal(results.plus.level, "moderate");
  const off = withAll("plus", { cycleEnabled: false });
  try { assert.equal((await (await post(body)).json()).result.signals.cycle, "none", "kullanıcı kapattıysa katılmaz"); } finally { off(); }
});

test("adaptive_plan: bugünkü check-in veritabanından okunur; yoksa ya da kapalıysa plan aynen kalır; geçersiz girdi 400", async () => {
  const day = "2026-10-05";
  const row = { day, energy: 2, sleep_quality: 3, sleep_hours: 5, soreness: 6, pain: 0, available_minutes: null };
  let restore = withAll("pro", { stored: [row] });
  try {
    const { result } = await (await post({ action: "adaptive_plan", exercises: planBody, localDate: day })).json();
    assert.equal(result.signals.checkinUsed, true); assert.equal(result.adapted, true); assert.equal(result.level, "low");
  } finally { restore(); }
  restore = withAll("pro", { stored: [] });
  try { assert.equal((await (await post({ action: "adaptive_plan", exercises: planBody, localDate: day })).json()).result.adapted, false); } finally { restore(); }
  restore = withAll("pro", { stored: [row], adaptiveEnabled: false });
  try { assert.equal((await (await post({ action: "adaptive_plan", exercises: planBody, localDate: day })).json()).result.adapted, false, "uyarlama kapalı"); } finally { restore(); }
  restore = withAll("pro");
  try {
    assert.equal((await post({ action: "adaptive_plan", exercises: [] })).status, 400);
    assert.equal((await post({ action: "adaptive_plan", exercises: planBody, checkin: { energy: 99, sleepQuality: 5 } })).status, 400);
  } finally { restore(); }
});
