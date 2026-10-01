import assert from "node:assert/strict";
import test from "node:test";
import { replaceExercise } from "../lib/training/exercise-replacer.ts";
import { normalizeTrainingProfile } from "../lib/training/profile-normalizer.ts";

const gymProfile = normalizeTrainingProfile({
  goal: "Kas geliştirmek",
  environment: "Salon",
  equipment: "Tam salon",
  level: "Orta seviye",
});

const homeDumbbellProfile = normalizeTrainingProfile({
  goal: "Kas geliştirmek",
  environment: "Ev",
  equipment: "Dambıl seti",
  level: "Başlangıç",
});

const kneeInjuryProfile = normalizeTrainingProfile({
  goal: "Sağlık ve zindelik",
  environment: "Salon",
  equipment: "Tam salon",
  level: "Orta seviye",
  limitations: ["Diz ağrısı / menisküs"],
});

test("Exercise Replacement: 'too_hard' reason regresses exercise along movement chain", () => {
  // "squat" (RepDB id) is the barbell back squat; regression is a bodyweight/kettlebell squat variant.
  const result = replaceExercise("squat", "too_hard", gymProfile);
  assert.ok(result, "Replacement should not be null");
  assert.equal(result.progressionType, "regression");
  assert.notEqual(result.replacementExercise.id, "squat");
  assert.ok(result.explanationTr.includes("zorlayıcı geldiği için"));
});

test("Exercise Replacement: 'too_easy' reason progresses exercise", () => {
  // push-up progression is a barbell/dumbbell bench press or weighted variant
  const result = replaceExercise("push-up", "too_easy", gymProfile);
  assert.ok(result, "Replacement should not be null");
  assert.equal(result.progressionType, "progression");
  assert.notEqual(result.replacementExercise.id, "push-up");
  assert.ok(result.explanationTr.includes("kolay geldiği için"));
});

test("Exercise Replacement: 'no_equipment' reason adapts to user's available equipment", () => {
  // Barbell bench press requested for someone with homeDumbbellProfile
  const result = replaceExercise("bench-press", "no_equipment", homeDumbbellProfile);
  assert.ok(result, "Replacement should not be null");
  assert.equal(result.progressionType, "equipment_swap");
  // Replacement must not require a barbell
  assert.ok(!result.replacementExercise.equipment.includes("barbell"), "Must not require barbell");
  // Should use dumbbell or bodyweight
  const usesAllowed = result.replacementExercise.equipment.some((eq) =>
    ["dumbbell", "bodyweight", "bands"].includes(eq),
  );
  assert.ok(usesAllowed, "Should use available home equipment");
});

test("Exercise Replacement: 'pain_discomfort' reason respects injury limitations", () => {
  // Dumbbell lunges can aggravate knees; user has knee limitation
  const result = replaceExercise("db-lunge", "pain_discomfort", kneeInjuryProfile, [], "knee");
  assert.ok(result, "Replacement should not be null");
  assert.equal(result.progressionType, "safety_swap");
  // Replacement must NOT have knee contraindications
  assert.ok(!result.replacementExercise.contraindications.includes("knee"));
  assert.ok(result.explanationTr.includes("rahatsızlığını önlemek için"));
});

test("Exercise Replacement: 'disliked' performs lateral swap in same muscle group", () => {
  const result = replaceExercise("squat", "disliked", gymProfile);
  assert.ok(result, "Replacement should not be null");
  assert.equal(result.progressionType, "lateral");
  assert.notEqual(result.replacementExercise.id, "squat");
  // Must target same primary muscle (quadriceps)
  assert.ok(result.replacementExercise.primaryMuscles.includes("quadriceps"));
});
