import assert from "node:assert/strict";
import test from "node:test";
import { STARTER_SUBCATEGORIES, adaptiveCapabilities, canUseAdaptiveAction, canUseModalityExercise } from "../lib/entitlements.ts";

test("katman ayrımı: adaptasyon eylemleri Free ⊂ Plus ⊂ Premium", () => {
  const free = adaptiveCapabilities("free").adaptiveActions;
  const plus = adaptiveCapabilities("plus").adaptiveActions;
  const pro = adaptiveCapabilities("pro").adaptiveActions;
  assert.deepEqual([...free], ["shorten", "reduce_intensity"]);
  for (const action of free) assert.ok(plus.includes(action));
  for (const action of plus) assert.ok(pro.includes(action));
  assert.ok(plus.length > free.length && pro.length > plus.length);
  assert.equal(canUseAdaptiveAction("free", "switch_pilates"), false);
  assert.equal(canUseAdaptiveAction("plus", "switch_pilates"), false, "Pilates oturumuna geçiş yalnız Premium");
  assert.equal(canUseAdaptiveAction("plus", "switch_recovery"), true);
  assert.equal(canUseAdaptiveAction("pro", "switch_pilates"), true);
});

test("döngü takibi her katmanda ücretsiz; adaptasyona katılması Plus ve üstü, koç farkındalığı yalnız Premium", () => {
  assert.equal(adaptiveCapabilities("free").cycleAdaptation, false);
  assert.equal(adaptiveCapabilities("plus").cycleAdaptation, true);
  assert.equal(adaptiveCapabilities("pro").cycleAdaptation, true);
  assert.equal(adaptiveCapabilities("plus").coachCycleAware, false);
  assert.equal(adaptiveCapabilities("pro").coachCycleAware, true);
  assert.equal(adaptiveCapabilities("plus").aiAdaptiveCoach, false);
  assert.equal(adaptiveCapabilities("pro").aiAdaptiveCoach, true);
});

test("check-in geçmişi ve beslenme kişiselleştirme katmanla genişler", () => {
  assert.deepEqual(["free", "plus", "pro"].map((tier) => adaptiveCapabilities(tier).checkinHistoryDays), [14, 90, Number.POSITIVE_INFINITY]);
  assert.deepEqual(["free", "plus", "pro"].map((tier) => adaptiveCapabilities(tier).nutritionPersonalization), ["basic", "training_load", "full"]);
  assert.equal(adaptiveCapabilities("pro").checkinTrends, true);
  assert.equal(adaptiveCapabilities("plus").checkinTrends, false);
});

test("modalite içeriği: Free başlangıç seti, Barre Plus'tan itibaren; klasik hareketler etkilenmez", () => {
  assert.equal(canUseModalityExercise("free", undefined, undefined), true, "modalitesiz hareket serbest");
  assert.equal(canUseModalityExercise("free", [], []), true);
  assert.equal(canUseModalityExercise("free", ["pilates"], ["beginner", "core"]), true);
  assert.equal(canUseModalityExercise("free", ["mobility"], ["morning"]), true);
  assert.equal(canUseModalityExercise("free", ["pilates"], ["lower_body"]), false, "başlangıç dışı kilitli");
  assert.equal(canUseModalityExercise("free", ["barre"], ["beginner"]), false, "Barre Free'de kapalı");
  assert.equal(canUseModalityExercise("plus", ["pilates"], ["lower_body"]), true);
  assert.equal(canUseModalityExercise("plus", ["barre"], ["lower_body"]), true);
  assert.equal(canUseModalityExercise("pro", ["barre"], ["balance_posture"]), true);
  assert.ok(STARTER_SUBCATEGORIES.includes("beginner") && !STARTER_SUBCATEGORIES.includes("lower_body"));
});
