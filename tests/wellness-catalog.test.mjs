import assert from "node:assert/strict";
import test from "node:test";
import { existsSync } from "node:fs";
import { filterByModality, presentExercise } from "../lib/exercise-presenter.ts";
import { MODALITIES, MODALITY_SUBCATEGORIES, validateAsset } from "../lib/exercise-modality.ts";
import { getAllExercises, getExerciseById } from "../lib/exercise-service.ts";
import { translateExerciseName } from "../lib/exercise-translations.ts";

const wellness = getAllExercises().filter((exercise) => exercise.source === "wellness");

test("her modalitede yeterli hareket var ve katalogda aktif (özgün + RepDB etiketli)", () => {
  assert.ok(wellness.length >= 28, `en az 28 Hedefit'e özel hareket: ${wellness.length}`);
  const minimum = { pilates: 20, mobility: 25, barre: 10, low_impact: 12, recovery: 15 };
  for (const modality of MODALITIES) {
    const count = getAllExercises().filter((exercise) => exercise.modalities?.includes(modality)).length;
    assert.ok(count >= minimum[modality], `${modality}: ${count} < ${minimum[modality]}`);
  }
  assert.ok(wellness.every((exercise) => exercise.isActive !== false));
});

test("etiketli hareketler (RepDB dahil) görselli ve lisans kaydı geçerli; çift ad yok", () => {
  const tagged = getAllExercises().filter((exercise) => exercise.modalities?.length);
  const names = new Set();
  for (const exercise of tagged) {
    assert.equal(validateAsset(exercise.asset), null, exercise.id);
    assert.notEqual(exercise.mediaStatus, "missing", exercise.id);
    assert.ok(!names.has(exercise.name.toLowerCase()), `çift ad: ${exercise.name}`);
    names.add(exercise.name.toLowerCase());
  }
  const all = getAllExercises();
  const seen = new Map();
  for (const exercise of all) { const key = exercise.name.toLowerCase(); assert.ok(!seen.has(key), `katalogda çift ad: ${exercise.name} (${seen.get(key)}, ${exercise.id})`); seen.set(key, exercise.id); }
});

test("RepDB etiketi atıf içerir; kamu malı fotoğraflar ayrı kaydedilir", () => {
  assert.equal(getExerciseById("cat-cow").asset.attribution, "Exercise data by RepDB (repdb.co)");
  assert.equal(getExerciseById("band-shoulder-press").asset.source, "Free Exercise DB");
  assert.deepEqual(getExerciseById("pilates-saw").modalities, ["pilates"]);
  assert.ok(getExerciseById("pigeon-stretch").modalities.includes("mobility") && getExerciseById("pigeon-stretch").modalities.includes("recovery"));
});

test("lisans kaydı: her wellness hareketi Hedefit'e özel, ticari kullanıma açık, kaynak ve lisansı belli", () => {
  for (const exercise of wellness) {
    assert.equal(validateAsset(exercise.asset), null, exercise.id);
    assert.equal(exercise.asset.source, "Hedefit original illustration", exercise.id);
    assert.equal(exercise.asset.attribution, "", exercise.id);
  }
});

test("görseller: başlangıç ve tepe karesi diskte var, yollar güvenli, medya tamam", () => {
  for (const exercise of wellness) {
    assert.equal(exercise.mediaStatus, "complete", exercise.id);
    assert.equal(exercise.images.length, 2, exercise.id);
    for (const image of exercise.images) assert.ok(existsSync(`public${image}`), `${exercise.id}: ${image}`);
    assert.ok(exercise.imageStart && exercise.imageEnd, exercise.id);
  }
});

test("alt kategoriler yalnızca kendi modalitesine ait; kas etiketleri katalogdakilerle uyumlu", () => {
  const known = new Set(getAllExercises().filter((exercise) => exercise.source === "repdb").flatMap((exercise) => [...exercise.primaryMuscles, ...exercise.secondaryMuscles]));
  for (const exercise of wellness) {
    const allowed = new Set(exercise.modalities.flatMap((modality) => MODALITY_SUBCATEGORIES[modality]));
    for (const sub of exercise.subcategories) assert.ok(allowed.has(sub), `${exercise.id}: ${sub}`);
    assert.ok(exercise.subcategories.length > 0, exercise.id);
    for (const muscle of [...exercise.primaryMuscles, ...exercise.secondaryMuscles]) assert.ok(known.has(muscle), `${exercise.id}: bilinmeyen kas ${muscle}`);
  }
});

test("iki dil: her hareketin TR adı, TR/EN talimatı ve açıklaması var; adlar çevrilir", () => {
  for (const exercise of wellness) {
    assert.ok(exercise.nameTr && exercise.descriptionTr && exercise.descriptionEn, exercise.id);
    assert.ok(exercise.instructions.length >= 3 && exercise.instructions.length === exercise.instructionsTr.length, exercise.id);
    assert.equal(translateExerciseName(exercise.name, "tr"), exercise.nameTr);
    assert.equal(translateExerciseName(exercise.name, "en"), exercise.name);
  }
});

test("mevcut katalog bozulmadı: RepDB sayısı ve eski hareketler aynen çözülür", () => {
  assert.equal(getAllExercises().filter((exercise) => exercise.source === "repdb").length, 601);
  assert.ok(getExerciseById("dead-bug"));
  assert.equal(getExerciseById("wl-pilates-hundred")?.modalities?.[0], "pilates");
});

test("API sunumu: modalite ve alt kategori filtresi, yazılmış talimat, TR/EN dil", () => {
  const all = getAllExercises();
  const pilates = filterByModality(all, "pilates", "");
  assert.ok(pilates.length >= 20 && pilates.every((item) => item.modalities.includes("pilates")));
  const core = filterByModality(all, "pilates", "core");
  assert.ok(core.length > 0 && core.length < pilates.length && core.every((item) => item.subcategories.includes("core")));
  const plie = getExerciseById("wl-barre-plie");
  const tr = presentExercise(plie, "tr");
  const en = presentExercise(plie, "en");
  assert.equal(tr.name, "Barre Plié");
  assert.ok(tr.instructions[0].includes("dik dur"), "TR talimat");
  assert.ok(en.instructions[0].startsWith("Stand tall"), "EN talimat");
  assert.ok(tr.description && en.description && tr.subcategoryLabels.length && en.subcategoryLabels.length);
  assert.notEqual(tr.subcategoryLabels.join(), en.subcategoryLabels.join());
  // modalitesiz klasik hareket: boş alanlar, şablon talimat korunur
  const classic = presentExercise(getExerciseById("ab-wheel-rollout"), "en");
  assert.deepEqual(classic.modalities, []);
  assert.equal(classic.impact, null);
  assert.ok(classic.instructions.length > 0);
});

test("antrenman motoru: yeni hareketler standart katalogda ve değiştirici/uyarlayıcı ile kullanılabilir", async () => {
  const { getStandardizedCatalog } = await import("../lib/training/exercise-metadata.ts");
  const { replaceExercise } = await import("../lib/training/exercise-replacer.ts");
  const { normalizeTrainingProfile } = await import("../lib/training/profile-normalizer.ts");
  const { evaluateReadinessAndAdapt } = await import("../lib/training/readiness-adapter.ts");
  const engineIds = new Set(getStandardizedCatalog().map((entry) => entry.id));
  for (const exercise of wellness) assert.ok(engineIds.has(exercise.id), `${exercise.id} motor kataloğunda olmalı`);
  const profile = normalizeTrainingProfile({ history: ["Kilo verme", "", "", "", "Başlangıç", "", "3 gün", "30 dk", "Pilates", "Evde", "Ekipman yok", "Yok"] });
  const replaced = replaceExercise("wl-pilates-hundred", "too_hard", profile, []);
  assert.ok(replaced, "yeni hareket değiştirilebilir olmalı");
  const swapped = replaceExercise("plank", "too_hard", profile, []);
  assert.ok(swapped, "klasik hareket yine değiştirilebilir");
  const plan = [{ id: "wl-pilates-hundred", name: "Pilates Hundred", area: "Core", sets: 3, reps: "10", restSeconds: 45 }];
  const adapted = evaluateReadinessAndAdapt({ energy: 3, sleepQuality: 4, fatigue: 8, hasSoreness: false, sorenessAreas: [], discomfortLevel: 1 }, plan, profile);
  assert.equal(adapted.needsAdaptation, true);
});
