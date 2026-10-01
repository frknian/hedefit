import assert from "node:assert/strict";
import test from "node:test";
import exercises from "../data/exercises.json" with { type: "json" };
import { filterExercises } from "../lib/exercise-service.ts";
import { generateWorkoutPlan } from "../lib/training/plan-orchestrator.ts";
import { normalizeTrainingProfile } from "../lib/training/profile-normalizer.ts";
import { getStandardizedExerciseById } from "../lib/training/exercise-metadata.ts";
import { QUESTION, emptyHistory } from "../lib/onboarding-questions.ts";

const history = (answers) => { const list = emptyHistory(); for (const [k, v] of Object.entries(answers)) list[QUESTION[k]] = v; return list; };
const LEVELS = ["beginner", "intermediate", "advanced"];

test("Every exercise carries audited equipment, environment and level", () => {
  assert.equal(exercises.length, 601);
  for (const e of exercises) {
    assert.ok(Array.isArray(e.requiredEquipment) && e.requiredEquipment.length > 0, `${e.id} has no requiredEquipment`);
    assert.ok(LEVELS.includes(e.level), `${e.id} level ${e.level}`);
    const bodyweight = e.requiredEquipment.some((option) => option.length === 0);
    assert.equal(e.isBodyweight, bodyweight, `${e.id} isBodyweight mismatch`);
    if (!bodyweight && e.requiredEquipment.every((option) => option.some((g) => ["cable", "machine", "plates", "gym_gear", "cardio_machine", "dip_station", "plyo_box"].includes(g)))) {
      assert.deepEqual(e.environment, ["gym"], `${e.id} needs gym equipment but is tagged ${e.environment}`);
    }
  }
});

test("Hidden equipment is declared (bench presses need a bench, captain's chair needs a station)", () => {
  const byId = Object.fromEntries(exercises.map((e) => [e.id, e]));
  assert.deepEqual(byId["db-bench-press"].requiredEquipment, [["bench", "dumbbell"]]);
  assert.deepEqual(byId["captains-chair-leg-raise"].requiredEquipment, [["dip_station"]]);
  assert.ok(byId["goblet-squat"].requiredEquipment.some((o) => o.join() === "dumbbell"));
  assert.equal(byId["locust-pose"].category, "stretching");
  assert.equal(byId.snatch.level, "advanced");
});

test("Owned filter: a band at home never returns dumbbell, barbell or machine work", () => {
  for (const e of filterExercises({ owned: ["band"] })) {
    assert.ok(e.requiredEquipment.some((option) => option.every((g) => g === "band")), `${e.id} needs ${JSON.stringify(e.requiredEquipment)}`);
  }
});

for (const [label, environment, equipment, level] of [
  ["home band beginner", "Evde", "Direnç bandı", "Yeni başlıyorum"],
  ["home dumbbell, no bench", "Evde", "Dambıl", "Düzenli antrenman yapıyorum"],
  ["home kettlebell + TRX", "Evde", "Kettlebell, TRX / halka", "Uzun süredir ve ileri seviyede"],
  ["gym beginner", "Salon", "Tam salon", "Yeni başlıyorum"],
]) {
  test(`FitKoç plan respects equipment and level: ${label}`, () => {
    const payload = { age: 30, gender: "Erkek", height: 178, weight: 82, environment, equipment, goal: "Kas geliştirmek",
      history: history({ goal: "Kas geliştirmek", experience: level, level, availableDays: "3–4 gün", sessionMinutes: "45 dakika", location: environment, equipment, injuries: "Yok" }) };
    const profile = normalizeTrainingProfile(payload);
    const plan = generateWorkoutPlan(payload, undefined, "tr");
    const moves = (plan.days ?? plan.sessions ?? []).flatMap((day) => day.exercises ?? []);
    assert.ok(moves.length > 0);
    for (const move of moves) {
      const atlas = getStandardizedExerciseById(move.id);
      assert.ok(atlas, move.id);
      const doable = profile.equipment.includes("gym") || atlas.equipmentOptions.some((o) => o.every((i) => i === "bodyweight" || profile.equipment.includes(i)));
      assert.ok(doable, `${move.id} needs ${JSON.stringify(atlas.equipmentOptions)} but user has ${profile.equipment}`);
      assert.ok(LEVELS.indexOf(atlas.difficulty) <= LEVELS.indexOf(profile.fitnessLevel), `${move.id} is ${atlas.difficulty} for a ${profile.fitnessLevel}`);
      assert.notEqual(atlas.category, "stretching", `${move.id} is a stretch`);
    }
  });
}
