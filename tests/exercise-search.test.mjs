import assert from "node:assert/strict";
import test from "node:test";
import { getExercisesForProfile, searchExercises } from "../lib/exercise-service.ts";
import { translateExerciseName } from "../lib/exercise-translations.ts";

test("Türkçe görünen hareket adı aynı Türkçe ifadeyle aranabilir", () => {
  const results = searchExercises("barfiks");
  assert.ok(results.length > 0);
  assert.ok(results.some((exercise) => translateExerciseName(exercise.name).toLocaleLowerCase("tr-TR").includes("barfiks")));
});

test("yalnız dambılı olan kullanıcıya gizli bant veya barfiks barı gerektiren hareket verilmez", () => {
  const results = getExercisesForProfile(false, "Sadece dumbell", "Evde", "Kuvvet");
  assert.ok(results.some((exercise) => exercise.equipment === "dumbbell"));
  assert.equal(results.some((exercise) => /band assisted pull-up/i.test(exercise.name)), false);
  assert.equal(results.some((exercise) => /\b(?:barbell|kettlebell|medicine ball|cable|smith)\b/i.test(exercise.name)), false);
});

test("salon ortamı yalnız dambıl beyanını tam salon erişimine çeviremez", () => {
  const results = getExercisesForProfile(true, "Sadece dumbell", "Spor salonunda", "Kuvvet");
  assert.ok(results.some((exercise) => exercise.equipment === "dumbbell"));
  assert.equal(results.some((exercise) => /band assisted pull-up/i.test(exercise.name)), false);
  assert.equal(results.some((exercise) => ["bands", "barbell", "cable", "machine"].includes(exercise.equipment ?? "")), false);
});
