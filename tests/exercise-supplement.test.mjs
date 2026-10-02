import assert from "node:assert/strict";
import { existsSync } from "node:fs";
import test from "node:test";

import exercises from "../data/exercises.json" with { type: "json" };
import supplement from "../data/exercises-supplement.json" with { type: "json" };
import { catalogExerciseNamesTr } from "../lib/exercise-names-tr.ts";
import { filterExercises, getExerciseById } from "../lib/exercise-service.ts";
import { getStandardizedExerciseById } from "../lib/training/exercise-metadata.ts";

test("ek hareketler atlasla çakışmaz, kimlikleri ve ekipmanı tutarlıdır, görselleri diskte vardır", () => {
  const atlasIds = new Set(exercises.map((exercise) => exercise.id));
  const seen = new Set();
  for (const exercise of supplement) {
    assert.ok(!atlasIds.has(exercise.id) && !seen.has(exercise.id), `${exercise.id} çakışıyor`);
    seen.add(exercise.id);
    assert.equal(exercise.source, "supplement");
    assert.ok(exercise.requiredEquipment.length > 0 && exercise.requiredEquipment.every((option) => option.length > 0), `${exercise.id}: ekipman tanımsız`);
    assert.ok(exercise.instructions.length >= 3, `${exercise.id}: talimat az`);
    assert.ok(exercise.name in catalogExerciseNamesTr, `${exercise.id}: Türkçe adı yok`);
    for (const image of exercise.images) assert.ok(existsSync(new URL(`../public${image}`, import.meta.url)), `${exercise.id}: ${image} diskte yok`);
    assert.equal(exercise.mediaStatus, exercise.images.length >= 2 ? "complete" : exercise.images.length === 1 ? "partial" : "missing");
  }
  // Servis ve plan motoru ek hareketleri de görür.
  assert.equal(getExerciseById("band-seated-row")?.source, "supplement");
  assert.equal(getStandardizedExerciseById("band-lat-pulldown")?.movementPattern, "vertical_pull");
  assert.equal(getStandardizedExerciseById("band-seated-row")?.movementPattern, "horizontal_pull");
  assert.equal(getStandardizedExerciseById("band-chest-press")?.movementPattern, "horizontal_push");
});

test("yalnız direnç bandı olan kullanıcı bütün büyük kas gruplarında bant hareketi bulur", () => {
  const bandOnly = filterExercises({ owned: ["band"] }).filter((exercise) => exercise.category === "strength");
  const muscles = new Set(bandOnly.flatMap((exercise) => exercise.primaryMuscles));
  for (const muscle of ["latissimus_dorsi", "pectoralis_major", "biceps_brachii", "triceps_brachii", "posterior_deltoid", "hamstrings", "gluteus_maximus"]) {
    assert.ok(muscles.has(muscle), `bantla ${muscle} hareketi yok`);
  }
});
