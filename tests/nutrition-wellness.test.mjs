import assert from "node:assert/strict";
import test from "node:test";
import { POST } from "../app/api/nutrition/wellness/route.ts";
import { buildNutritionWellness, normalizeDiet } from "../lib/nutrition-wellness.ts";
import { TEST_USER_ID, authorizedRequest, withAuthenticatedFetch, withSupabaseAuthEnv } from "./helpers/auth.mjs";

const base = { locale: "tr", diet: "standard", training: { workedOutToday: true, level: "good", minutes: 45 }, cycle: null };
const ids = (tier, extra = {}) => buildNutritionWellness({ ...base, tier, ...extra }).tips.map((tip) => tip.id);

test("katmanlar: basic yalnız genel, training_load antrenman/toparlanma, full tercih + döngü", () => {
  assert.deepEqual(ids("basic"), ["basic-hydration"]);
  assert.ok(ids("training_load").includes("load-post-workout") && !ids("training_load").includes("full-protein-sources"));
  const full = ids("full", { cycle: { periodLikely: true, preMenstrualWindow: false } });
  assert.ok(full.includes("full-protein-sources") && full.includes("full-cycle-iron"));
  assert.ok(!ids("training_load", { cycle: { periodLikely: true, preMenstrualWindow: false } }).includes("full-cycle-iron"), "döngü ipucu Premium'a özgü");
  assert.ok(buildNutritionWellness({ ...base, tier: "basic" }).lockedTipCount > 0);
});

test("güvenlik: ipuçları kısıtlama, hormon iddiası, doz ve tıbbi diyet içermez; döngü ipucu kesin değil", () => {
  for (const locale of ["tr", "en"]) for (const diet of ["standard", "vegan", "vegetarian", "pescatarian"]) for (const level of ["good", "low", "recovery"]) for (const cycle of [null, { periodLikely: true, preMenstrualWindow: false }, { periodLikely: false, preMenstrualWindow: true }]) {
    const result = buildNutritionWellness({ tier: "full", locale, diet, training: { workedOutToday: true, level, minutes: 20 }, cycle });
    // Yalnızca "kısmana gerek yok" gibi olumsuzlanmış ifadeler serbest; kısıtlama önerisi yasak.
    const text = result.tips.map((tip) => `${tip.title} ${tip.body}`).join(" ").replace(/no need to eat less|yemeği kısmana gerek yok/gi, "");
    assert.doesNotMatch(text, /\{|\}/, "yer tutucu kalmamış");
    assert.doesNotMatch(text, /\b\d+\s?(mg|mcg|µg|iu)\b|takviye al|supplement|kalori(ni)? (azalt|kıs)|eat less|skip (a )?meal|östrojen|estrogen|hormon düzey/i, text);
    assert.ok(result.proteinBonusGrams >= 0 && result.proteinBonusGrams <= 10);
    if (cycle) assert.match(text, /Bazı kişiler|Some people/);
  }
});

test("tercih: vegan öneride hayvansal ürün çıkmaz; toparlanma gününde yemeği kısmaya gerek yok der, protein bonusu yok", () => {
  const vegan = buildNutritionWellness({ ...base, tier: "full", diet: "vegan", cycle: { periodLikely: true, preMenstrualWindow: false } });
  assert.doesNotMatch(vegan.tips.map((tip) => tip.body).join(" "), /(^|[^\p{L}])(tavuk|yumurta|yoğurt|peynir|et|balık|chicken|eggs?|yogurt|cheese|fish|meat)(?![\p{L}])/iu);
  const recovery = buildNutritionWellness({ ...base, tier: "training_load", training: { workedOutToday: false, level: "recovery" } });
  assert.ok(recovery.tips.some((tip) => tip.id === "load-recovery-day") && recovery.proteinBonusGrams === 0);
  assert.equal(normalizeDiet("Vejetaryen"), "vegetarian"); assert.equal(normalizeDiet("x"), "standard");
});

function withBackend(planTier, cycleRow, settingsRow) {
  const previousFetch = globalThis.fetch;
  const restore = withSupabaseAuthEnv();
  globalThis.fetch = withAuthenticatedFetch((input) => {
    const url = String(input);
    if (url.includes("/profiles")) return Response.json({ plan_tier: planTier });
    if (url.includes("cycle_profiles")) return Response.json(cycleRow);
    if (url.includes("personalization_settings")) return Response.json(settingsRow);
    return Response.json([]);
  }, TEST_USER_ID);
  return () => { globalThis.fetch = previousFetch; restore(); };
}
const cycleRow = { tracking_enabled: true, last_period_start: "2026-10-01", cycle_length_days: 28, period_length_days: 5, regularity: "regular" };
const call = (body) => POST(authorizedRequest("http://localhost/api/nutrition/wellness", { method: "POST", body: JSON.stringify({ locale: "en", localDate: "2026-10-05", training: { workedOutToday: true, level: "good" }, ...body }) }));

test("rota: kimlik şart; Premium + döngü izni → döngü ipucu; Plus ya da izin yoksa yok; istemci döngü gönderemez", async () => {
  let restore = withBackend("pro", cycleRow, {});
  try { assert.equal((await POST(new Request("http://localhost/api/nutrition/wellness", { method: "POST", body: "{}" }))).status, 401); } finally { restore(); }
  restore = withBackend("pro", cycleRow, { adaptive_enabled: true, cycle_personalization_enabled: true, ai_health_context_enabled: false });
  try {
    const body = await (await call({ cycle: { periodLikely: true } })).json();
    assert.equal(body.tier, "full"); assert.ok(body.tips.some((tip) => tip.id === "full-cycle-iron"));
  } finally { restore(); }
  restore = withBackend("plus", cycleRow, { cycle_personalization_enabled: true });
  try {
    const body = await (await call({})).json();
    assert.equal(body.tier, "training_load"); assert.ok(!body.tips.some((tip) => tip.id.includes("cycle")));
  } finally { restore(); }
  restore = withBackend("pro", cycleRow, { adaptive_enabled: true, cycle_personalization_enabled: false });
  try {
    assert.ok(!(await (await call({ cycle: { periodLikely: true } })).json()).tips.some((tip) => tip.id.includes("cycle")), "izin kapalıyken yok");
  } finally { restore(); }
});
