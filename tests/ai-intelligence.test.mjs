import assert from "node:assert/strict";
import test from "node:test";
import { analyze } from "../lib/ai/intelligence.ts";

const PROFILE = { age: 30, sex: "male", heightCm: 180, weightKg: 85 };

test("kalori hedefi ve kalan kalori LLM'e değil motora aittir", () => {
  const facts = analyze({
    profile: PROFILE,
    goal: { goalType: "lose", targetWeightKg: 78 },
    today: { totals: { calories: 1_840, protein: 90, carbs: 200, fat: 60 } },
  });
  assert.ok(typeof facts.goals.calorieTarget === "number");
  // Kalan = hedef − tüketilen. Motor hesaplar, model yalnızca anlatır.
  assert.equal(facts.today.remainingCalories, facts.goals.calorieTarget - 1_840);
  assert.equal(facts.today.caloriesConsumed, 1_840);
  assert.equal(facts.goals.weightToGoalKg, 7);
});

test("kayıtlı beslenme hedefi varsa yeniden hesaplanmaz", () => {
  // Kullanıcı hedefini elle değiştirmiş olabilir; motor onu EZMEMELİ.
  const savedGoal = {
    goalType: "maintain", calorieTarget: 2_100, proteinGrams: 150, carbsGrams: 200,
    fatGrams: 70, bmr: 1_700, tdee: 2_100, calorieAdjustment: 0, activityFactor: 1.4,
    workoutDays: 3, isManual: true,
  };
  const facts = analyze({ profile: PROFILE, goal: { savedGoal } });
  assert.equal(facts.goals.calorieTarget, 2_100);
  assert.equal(facts.goals.proteinTargetGrams, 150);
});

test("BMI profil ölçülerinden türetilir ve sınıflandırılır", () => {
  const facts = analyze({ profile: { heightCm: 180, weightKg: 85 } });
  assert.equal(facts.profile.bmi, 26.2);
  assert.equal(facts.profile.bmiCategory, "overweight");
});

test("veri yoksa değer UYDURULMAZ, eksik olarak işaretlenir", () => {
  const facts = analyze({});
  assert.equal(facts.goals.calorieTarget, undefined);
  assert.equal(facts.today.remainingCalories, undefined);
  assert.equal(facts.profile.bmi, undefined);
  assert.ok(facts.missing.includes("bmi"));
  assert.ok(facts.missing.includes("calorieTarget"));
  assert.ok(facts.missing.includes("weightTrend"));
});

test("kilo trendi yalnız yeterli aralık varken hesaplanır", () => {
  const day = (offset) => new Date(Date.now() - offset * 86_400_000).toISOString().slice(0, 10);
  const enough = analyze({ profile: PROFILE, measurements: [
    { measuredAt: day(7), weightKg: 86 },
    { measuredAt: day(0), weightKg: 85 },
  ] });
  assert.equal(enough.trends.weeklyRateKg, -1);
  assert.equal(enough.trends.weightChange7dKg, -1);

  // Pencere günün saatine göre KAYMAMALI: tam 7 gün önceki ölçüm her zaman
  // içeride kalır. (Daha önce an bazında karşılaştırılıyordu ve aynı veri
  // öğleden önce/sonra farklı sonuç veriyordu.)
  assert.equal(enough.trends.trendDays, 7);

  // İki gün arayla iki ölçüm trend için yetersiz (bkz. calculateWeeklyWeightTrend),
  // ama ham 7 günlük fark yine de gösterilebilir.
  const tooShort = analyze({ profile: PROFILE, measurements: [
    { measuredAt: day(2), weightKg: 86 },
    { measuredAt: day(0), weightKg: 85.6 },
  ] });
  assert.equal(tooShort.trends.weeklyRateKg, undefined);
  assert.equal(tooShort.trends.weightChange7dKg, -0.4);
});

test("7 günlük ortalamalar yalnız veri varken üretilir", () => {
  const facts = analyze({ recentCalories: [2_000, 2_200, 1_800], recentSteps: [] });
  assert.equal(facts.trends.averageCalories7d, 2_000);
  assert.equal(facts.trends.averageSteps7d, undefined);
});

test("protein kalanı negatife düşmez", () => {
  const facts = analyze({
    profile: PROFILE,
    goal: { goalType: "lose" },
    today: { totals: { calories: 1_000, protein: 900, carbs: 10, fat: 10 } },
  });
  assert.equal(facts.today.proteinRemainingGrams, 0);
});
