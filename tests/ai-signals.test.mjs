import assert from "node:assert/strict";
import test from "node:test";
import { sanitizeCoachSignals } from "../lib/ai/signals.ts";
import { analyze } from "../lib/ai/intelligence.ts";

test("istemci TÜRETİLMİŞ değer gönderemez; sunucu kendisi hesaplar", () => {
  // Göç öncesinde istemci BMI'yı kendisi hesaplayıp gönderiyordu ve ölçü
  // eksikse yerine sabit "22.4" yazıyordu — modele uydurulmuş bir gerçek.
  const signals = sanitizeCoachSignals({
    profile: { heightCm: 180, weightKg: 85, bmi: 22.4 },
    today: { totals: { calories: 1_800 }, remainingCalories: 99_999 },
  });
  assert.equal(signals.profile.bmi, undefined, "istemcinin BMI'ı kabul edilmemeli");
  assert.equal(signals.today.remainingCalories, undefined, "istemcinin kalan kalorisi kabul edilmemeli");

  const facts = analyze({ ...signals, goal: { goalType: "lose" } });
  assert.equal(facts.profile.bmi, 26.2, "BMI sunucuda ölçülerden türetilmeli");
});

test("aralık dışı değerler kırpılmaz, ATILIR", () => {
  // Kırpmak sessizce yanlış bir gerçek üretirdi (ör. 900 kg → 400 kg).
  const signals = sanitizeCoachSignals({
    profile: { age: 500, heightCm: 5, weightKg: 900 },
    today: { steps: -50 },
  });
  assert.equal(signals.profile.age, undefined);
  assert.equal(signals.profile.heightCm, undefined);
  assert.equal(signals.profile.weightKg, undefined);
  assert.equal(signals.today.steps, undefined);
});

test("geçerli değerler korunur", () => {
  const signals = sanitizeCoachSignals({
    profile: { age: 30, sex: "male", heightCm: 180, weightKg: 85 },
    goal: { goalType: "lose", targetWeightKg: 78 },
    today: { totals: { calories: 1_840, protein: 90, carbs: 200, fat: 60 }, steps: 7_230, workoutCompleted: true },
    activity: { workoutsThisWeek: 3 },
    recentCalories: [2_000, 2_100],
  });
  assert.equal(signals.profile.age, 30);
  assert.equal(signals.goal.goalType, "lose");
  assert.equal(signals.today.totals.calories, 1_840);
  assert.equal(signals.today.workoutCompleted, true);
  assert.equal(signals.activity.workoutsThisWeek, 3);
  assert.deepEqual(signals.recentCalories, [2_000, 2_100]);
});

test("uydurma hedef türü reddedilir", () => {
  assert.equal(sanitizeCoachSignals({ goal: { goalType: "süper-diyet" } }).goal.goalType, undefined);
});

test("bozuk ölçüm kayıtları elenir, geçerliler kalır", () => {
  const signals = sanitizeCoachSignals({ measurements: [
    { measuredAt: "2026-08-01", weightKg: 86 },
    { measuredAt: "hatalı-tarih", weightKg: 85 },
    { measuredAt: "2026-08-08", weightKg: 9_000 },
    { measuredAt: "2026-08-08", weightKg: 85 },
  ] });
  assert.deepEqual(signals.measurements, [
    { measuredAt: "2026-08-01", weightKg: 86 },
    { measuredAt: "2026-08-08", weightKg: 85 },
  ]);
});

test("eski istemci (yalnız düz metin bağlam) çökertmez", () => {
  // Capacitor ile yayımlanmış eski bir sürüm kullanıcının telefonunda
  // güncellenmemiş olabilir; sunucu onunla da çalışmalı.
  const signals = sanitizeCoachSignals(undefined, "profil: 30 yaş, 85 kg");
  assert.deepEqual(signals, {});
  const facts = analyze(signals);
  assert.ok(facts.missing.includes("bmi"), "veri yoksa eksik olarak işaretlenir, uydurulmaz");
});

test("düz metin veya dizi gövde güvenle reddedilir", () => {
  assert.deepEqual(sanitizeCoachSignals("düz metin"), {});
  assert.deepEqual(sanitizeCoachSignals([1, 2, 3]), {});
  assert.deepEqual(sanitizeCoachSignals(null), {});
});
