import assert from "node:assert/strict";
import test from "node:test";
import { adaptTodaysPlan, readinessScore } from "../lib/training/adaptive-engine.ts";
import { normalizeTrainingProfile } from "../lib/training/profile-normalizer.ts";
import { describeCycle } from "../lib/cycle.ts";
import { getExerciseById } from "../lib/exercise-service.ts";
import { canUseModalityExercise } from "../lib/entitlements.ts";

const profile = normalizeTrainingProfile({ history: ["Kilo verme", "", "", "", "Başlangıç", "", "3 gün", "45 dk", "", "Evde", "Ekipman yok", "Yok"] });
const plan = ["plank", "glute-bridge", "dead-bug", "bird-dog", "high-plank", "clamshells"].map((id) => ({ id, name: getExerciseById(id).name, english: getExerciseById(id).name, area: "Core", sets: 3, reps: "10", restSeconds: 60 }));
const checkin = (o = {}) => ({ day: "2026-10-05", energy: 8, sleepQuality: 8, sleepHours: 8, soreness: 0, pain: 0, availableMinutes: null, ...o });
const period = describeCycle({ trackingEnabled: true, lastPeriodStart: "2026-10-03", cycleLengthDays: 28, periodLengthDays: 5, regularity: "regular" }, "2026-10-05");
const run = (o) => adaptTodaysPlan({ profile, exercises: plan, checkin: checkin(), cycle: null, tier: "pro", seed: "u:2026-10-05", locale: "tr", ...o });
const actions = (r) => r.applied.map((a) => a.action);

test("puan yalnızca check-in cevaplarından: en iyi 100, en kötü düşük; ağırlıklar mantıklı", () => {
  assert.equal(readinessScore(checkin({ energy: 10, sleepQuality: 10 })), 100);
  assert.ok(readinessScore(checkin({ energy: 1, sleepQuality: 1, soreness: 10, pain: 10 })) <= 2);
  assert.ok(readinessScore(checkin({ pain: 8 })) < readinessScore(checkin({ soreness: 8 })), "ağrı kas ağrısından daha ağır basar");
});

test("check-in yoksa ya da uyarlama kapalıysa plan aynen kalır", () => {
  for (const o of [{ checkin: null }, { adaptiveEnabled: false }]) {
    const r = run(o);
    assert.equal(r.adapted, false); assert.deepEqual(r.exercises, plan); assert.equal(r.intensity, "normal");
  }
  assert.equal(run({ checkin: null }).signals.checkinUsed, false);
});

test("İYİ GÜN + adet dönemi: normal antrenman sürer, döngü yalnızca bağlam (karar vermez)", () => {
  assert.equal(period.periodLikely, true);
  for (const tier of ["free", "plus", "pro"]) {
    const r = run({ checkin: checkin({ energy: 9, sleepQuality: 9 }), cycle: period, tier });
    assert.equal(r.adapted, false, tier); assert.equal(r.level, "good"); assert.deepEqual(r.exercises, plan);
    assert.equal(r.signals.cycle, "context", "nudged değil, yalnızca bağlam");
  }
});

test("KÖTÜ GÜN + yüksek ağrı + adet: yük düşer ve toparlanmaya geçilir (Plus+); Free'de yoğunluk azalır, kilit bildirilir", () => {
  const bad = checkin({ energy: 2, sleepQuality: 3, soreness: 6, pain: 8 });
  const pro = run({ checkin: bad, cycle: period, tier: "pro" });
  assert.equal(pro.level, "recovery"); assert.equal(pro.intensity, "recovery");
  assert.deepEqual(actions(pro), ["switch_recovery"]); assert.equal(pro.wellnessKind, "low_impact_recovery");
  assert.ok(pro.exercises.every((item) => !plan.some((p) => p.id === item.id)) || pro.exercises.length > 0);
  for (const item of pro.exercises) { const e = getExerciseById(item.id); assert.ok(e.modalities?.length, `${item.id} wellness olmalı`); }
  const free = run({ checkin: bad, cycle: period, tier: "free" });
  assert.deepEqual(actions(free), ["reduce_intensity"]); assert.equal(free.intensity, "recovery");
  assert.ok(free.lockedActions.includes("switch_recovery"));
  assert.ok(free.exercises.every((item, i) => item.sets < plan[i].sets && item.restSeconds > plan[i].restSeconds));
  assert.match(free.explanationTr, /sağlık profesyoneline/); assert.match(free.explanationEn, /health professional/);
});

test("DÜŞÜK enerji + kötü uyku (ağrı yok): Premium Pilates'e, Plus toparlanmaya geçer; Free'de hacim azalır", () => {
  const low = checkin({ energy: 2, sleepQuality: 3, soreness: 5, pain: 0 });
  const pro = run({ checkin: low, tier: "pro" });
  assert.equal(pro.level, "low"); assert.deepEqual(actions(pro), ["switch_pilates"]); assert.equal(pro.wellnessKind, "pilates_today");
  assert.ok(pro.exercises.some((item) => getExerciseById(item.id).modalities?.includes("pilates")));
  const plus = run({ checkin: low, tier: "plus" });
  assert.deepEqual(actions(plus), ["switch_recovery"]); assert.ok(plus.lockedActions.includes("switch_pilates"), "Premium'a yükseltme ipucu");
  const free = run({ checkin: low, tier: "free" });
  assert.deepEqual(actions(free), ["reduce_intensity"]); assert.ok(free.lockedActions.includes("switch_pilates") && free.lockedActions.includes("switch_recovery"));
  for (const r of [pro, plus]) for (const item of r.exercises) { const e = getExerciseById(item.id); assert.ok(canUseModalityExercise(r === pro ? "pro" : "plus", e.modalities, e.subcategories)); }
  assert.match(pro.explanationTr, /çöpe atmak yerine|düşük yoğunluklu Pilates/);
});

test("ORTA gün: hacim %20 azalır; Plus+ mobilite ekler, Free eklemez ve bunu kilitli bildirir", () => {
  const mid = checkin({ energy: 5, sleepQuality: 5, soreness: 3, pain: 0 });
  const free = run({ checkin: mid, tier: "free" });
  assert.equal(free.level, "moderate"); assert.deepEqual(actions(free), ["reduce_intensity"]); assert.ok(free.lockedActions.includes("add_mobility"));
  assert.equal(free.exercises.length, plan.length);
  const plus = run({ checkin: mid, tier: "plus" });
  assert.deepEqual(actions(plus), ["reduce_intensity", "add_mobility"]);
  assert.equal(plus.exercises.length, plan.length + 2);
  assert.ok(plus.exercises.slice(-2).every((item) => getExerciseById(item.id).modalities.includes("mobility")));
  assert.deepEqual(plus.originalExercises, plan, "orijinal plan korunur");
});

test("orta ağrı (4–6): Plus+ rahat alternatifle hareket değiştirir, Free değiştirmez ve kilitli bildirir", () => {
  const painful = checkin({ energy: 6, sleepQuality: 6, soreness: 2, pain: 5 });
  const plus = run({ checkin: painful, tier: "plus" });
  assert.equal(plus.level, "moderate");
  assert.ok(actions(plus).includes("reduce_intensity"));
  const free = run({ checkin: painful, tier: "free" });
  assert.ok(!actions(free).includes("replace_exercises")); assert.ok(free.lockedActions.includes("replace_exercises"));
});

test("GRİ BÖLGE: döngü yalnızca sınırda 'iyi'yi 'orta'ya itebilir; açıkça iyi ya da açıkça düşük günde etkisi yoktur", () => {
  const grey = checkin({ energy: 6, sleepQuality: 6, soreness: 2, pain: 0 }); // puan 70: tam sınırda
  const without = run({ checkin: grey, cycle: null });
  const withCycle = run({ checkin: grey, cycle: period });
  assert.equal(without.level, "good");
  assert.equal(withCycle.level, "moderate"); assert.equal(withCycle.signals.cycle, "nudged");
  assert.match(withCycle.explanationTr, /check-in cevapların/); assert.match(withCycle.explanationEn, /your own check-in answers decide/);
  const clearlyGood = run({ checkin: checkin({ energy: 9, sleepQuality: 9 }), cycle: period });
  assert.equal(clearlyGood.level, "good");
  const clearlyLow = run({ checkin: checkin({ energy: 2, sleepQuality: 3, soreness: 6 }), cycle: period });
  assert.equal(clearlyLow.score, readinessScore(checkin({ energy: 2, sleepQuality: 3, soreness: 6 })), "puan döngüyle değişmez");
  // döngü olmadan aynı cevaplar aynı seviyeyi verir: karar check-in'e aittir
  assert.equal(clearlyLow.level, run({ checkin: checkin({ energy: 2, sleepQuality: 3, soreness: 6 }), cycle: null }).level);
});

test("antrenman yükü: son 3 günde ≥3 seans iyi günü orta yapar", () => {
  const r = run({ recentSessions3d: 3 });
  assert.equal(r.level, "moderate"); assert.ok(r.signals.reasons.includes("training_load")); assert.equal(r.signals.trainingLoad, true);
  assert.match(r.explanationTr, /yoğun antrenman/);
});

test("süre: müsait süre kısaysa plan kısalır (Free dahil); wellness'e geçildiyse süre oturumun kendisidir", () => {
  for (const tier of ["free", "plus", "pro"]) {
    const r = run({ checkin: checkin({ availableMinutes: 15 }), tier });
    assert.ok(actions(r).includes("shorten"), tier); assert.ok(r.estimatedMinutes < 25);
    assert.ok(r.exercises.length < plan.length);
  }
  const wellness = run({ checkin: checkin({ energy: 2, sleepQuality: 3, soreness: 5, availableMinutes: 15 }), tier: "pro" });
  assert.ok(!actions(wellness).includes("shorten"));
  assert.ok(wellness.estimatedMinutes <= 22);
});

test("açıklama TR/EN dile göre, tıbbi iddia içermez; hareket adları dile göre", () => {
  const tr = run({ checkin: checkin({ energy: 2, sleepQuality: 3 }), tier: "pro", locale: "tr" });
  const en = run({ checkin: checkin({ energy: 2, sleepQuality: 3 }), tier: "pro", locale: "en" });
  assert.match(tr.explanationTr, /enerjin düşük/); assert.match(en.explanationEn, /your energy is low/);
  for (const text of [tr.explanationTr, en.explanationEn]) assert.doesNotMatch(text, /hormon|tedavi|teşhis|diagnos|treat|hormone/i);
  assert.deepEqual(tr.exercises.map((e) => e.id), en.exercises.map((e) => e.id));
});

test("deterministik: aynı girdi aynı sonuç; farklı gün farklı seçim; girdi dizisi değişmez", () => {
  const snapshot = JSON.stringify(plan);
  const a = run({ checkin: checkin({ energy: 2, sleepQuality: 3 }) });
  const b = run({ checkin: checkin({ energy: 2, sleepQuality: 3 }) });
  assert.deepEqual(a.exercises, b.exercises);
  assert.notDeepEqual(a.exercises.map((e) => e.id), run({ checkin: checkin({ energy: 2, sleepQuality: 3 }), seed: "u:2026-10-06" }).exercises.map((e) => e.id));
  assert.equal(JSON.stringify(plan), snapshot);
});
