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
