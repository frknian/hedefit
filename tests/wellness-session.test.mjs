import assert from "node:assert/strict";
import test from "node:test";
import { canUseModalityExercise } from "../lib/entitlements.ts";
import { getExerciseById } from "../lib/exercise-service.ts";
import { buildWellnessSession, wellnessMinutesToCount, wellnessModalitySummary } from "../lib/training/wellness-session.ts";

const KINDS = ["pilates_today", "low_impact_recovery", "posture_mobility"];

test("her tür için süreye uygun, tekrarsız, katalogda var olan hareketlerle oturum üretilir", () => {
  for (const kind of KINDS) for (const tier of ["free", "plus", "pro"]) {
    const session = buildWellnessSession({ kind, minutes: 20, tier, seed: "u1:2026-10-05" });
    assert.equal(session.exercises.length, wellnessMinutesToCount(20), `${kind}/${tier}`);
    assert.equal(new Set(session.exercises.map((item) => item.id)).size, session.exercises.length, "tekrar yok");
    for (const item of session.exercises) assert.ok(getExerciseById(item.id), item.id);
    assert.ok(session.estimatedMinutes >= 5 && session.estimatedMinutes <= 40, `${kind}: ${session.estimatedMinutes} dk`);
  }
});

test("süre arttıkça hareket sayısı artar, uçlar sınırlıdır", () => {
  const count = (minutes) => buildWellnessSession({ kind: "pilates_today", minutes, tier: "pro", seed: "s" }).exercises.length;
  assert.ok(count(10) < count(20) && count(20) < count(40));
  assert.equal(count(2), wellnessMinutesToCount(8));
  assert.equal(count(200), wellnessMinutesToCount(45));
});

test("deterministik: aynı tohum aynı oturum, farklı gün farklı sıra", () => {
  const a = buildWellnessSession({ kind: "pilates_today", minutes: 20, tier: "pro", seed: "u1:2026-10-05" });
  const b = buildWellnessSession({ kind: "pilates_today", minutes: 20, tier: "pro", seed: "u1:2026-10-05" });
  assert.deepEqual(a.exercises.map((item) => item.id), b.exercises.map((item) => item.id));
  const other = buildWellnessSession({ kind: "pilates_today", minutes: 20, tier: "pro", seed: "u1:2026-10-06" });
  assert.notDeepEqual(a.exercises.map((item) => item.id), other.exercises.map((item) => item.id));
});

test("katman kapısı: Free oturumu yalnızca Free'de açık içerikten oluşur, Barre hiç girmez", () => {
  for (const kind of KINDS) {
    const session = buildWellnessSession({ kind, minutes: 30, tier: "free", seed: "x" });
    for (const item of session.exercises) {
      const exercise = getExerciseById(item.id);
      assert.ok(canUseModalityExercise("free", exercise.modalities, exercise.subcategories), `${kind}: ${item.id} Free'de kilitli`);
      assert.ok(!exercise.modalities.every((modality) => modality === "barre"));
    }
  }
});

test("seviye: başlangıç profilinde orta seviye hareket yalnızca havuz yetmezse girer; başlangıçta çoğunluk başlangıç seviyesidir", () => {
  const session = buildWellnessSession({ kind: "pilates_today", minutes: 20, level: "beginner", tier: "pro", seed: "lvl" });
  const beginners = session.exercises.filter((item) => getExerciseById(item.id).level === "beginner").length;
  assert.ok(beginners >= Math.ceil(session.exercises.length / 2), `${beginners}/${session.exercises.length}`);
});

test("TR ve EN: başlık, ad ve doz metni dile göre; İngilizce ad her zaman korunur", () => {
  const tr = buildWellnessSession({ kind: "posture_mobility", minutes: 20, tier: "pro", seed: "z", locale: "tr" });
  const en = buildWellnessSession({ kind: "posture_mobility", minutes: 20, tier: "pro", seed: "z", locale: "en" });
  assert.equal(tr.title, "Duruş ve Mobilite");
  assert.equal(en.title, "Posture & Mobility");
  assert.deepEqual(tr.exercises.map((item) => item.id), en.exercises.map((item) => item.id));
  assert.ok(tr.exercises.every((item) => item.english.length > 0));
  assert.ok(tr.exercises.some((item) => /sn|tekrar/.test(item.reps)) && en.exercises.some((item) => /sec|reps/.test(item.reps)));
  assert.ok(wellnessModalitySummary(en, undefined, "en").length > 0);
});

test("ısınma ilk, soğuma son; ana bölümde hedef modalite öne çıkar", () => {
  const session = buildWellnessSession({ kind: "pilates_today", minutes: 24, tier: "pro", seed: "order" });
  const first = getExerciseById(session.exercises[0].id);
  const last = getExerciseById(session.exercises.at(-1).id);
  assert.ok(first.modalities.includes("pilates"));
  assert.ok(last.modalities.some((modality) => ["recovery", "mobility"].includes(modality)), "son hareket soğuma");
  const pilates = session.exercises.filter((item) => getExerciseById(item.id).modalities.includes("pilates")).length;
  assert.ok(pilates >= session.exercises.length - 2);
});
