import assert from "node:assert/strict";
import test from "node:test";
import { adaptWorkoutPlanOnTheFly } from "../lib/training/plan-adapter.ts";
import { normalizeTrainingProfile } from "../lib/training/profile-normalizer.ts";

const gymProfile = normalizeTrainingProfile({
  goal: "Kas geliştirmek",
  environment: "Salon",
  equipment: "Tam salon",
  level: "Orta seviye",
});

const mockWorkoutExercises = [
  { id: "Barbell_Bench_Press_-_Medium_Grip", name: "Barbell Bench Press", area: "Göğüs", sets: 4, reps: "8–10", restSeconds: 90 },
  { id: "Incline_Dumbbell_Press", name: "Incline Dumbbell Press", area: "Göğüs", sets: 3, reps: "10–12", restSeconds: 75 },
  { id: "Dumbbell_Incline_Row", name: "Dumbbell Incline Row", area: "Sırt", sets: 4, reps: "10–12", restSeconds: 75 },
  { id: "Alternating_Cable_Shoulder_Press", name: "Cable Shoulder Press", area: "Omuz", sets: 3, reps: "12–15", restSeconds: 60 },
  { id: "Barbell_Curl", name: "Barbell Curl", area: "Pazu", sets: 3, reps: "12–15", restSeconds: 60 },
  { id: "Triceps_Pushdown", name: "Triceps Pushdown", area: "Arka Kol", sets: 3, reps: "12–15", restSeconds: 60 },
];

test("Workout Adaptation: Time shortage (15m) keeps compound movements and prunes isolation", () => {
  const result = adaptWorkoutPlanOnTheFly(
    mockWorkoutExercises,
    { trigger: "time_shortage", targetMinutes: 15 },
    gymProfile,
    "tr",
  );

  assert.equal(result.trigger, "time_shortage");
  assert.ok(result.adaptedDurationMinutes <= 18, `Duration should be around 15 mins (got ${result.adaptedDurationMinutes})`);
  assert.ok(result.adaptedExercises.length <= 3, "Only 2-3 exercises should fit in 15 mins");
  // Compound movement like Bench Press or Row should be preserved
  const hasCompound = result.adaptedExercises.some((e) =>
    e.name.toLowerCase().includes("bench") || e.name.toLowerCase().includes("row"),
  );
  assert.ok(hasCompound, "Must preserve compound movement drivers");
  assert.ok(result.changes.length > 0);
});

test("Workout Adaptation: Travel / Bodyweight-only converts exercises to bodyweight equivalents", () => {
  const result = adaptWorkoutPlanOnTheFly(
    mockWorkoutExercises,
    { trigger: "travel" },
    gymProfile,
    "tr",
  );

  assert.equal(result.trigger, "travel");
  assert.ok(result.adaptedExercises.length > 0);
  assert.ok(/vücut ağırlığı|ekipmansız/i.test(result.explanationTr));
});

test("Workout Adaptation: Acute fatigue reduces volume and increases rest", () => {
  const result = adaptWorkoutPlanOnTheFly(
    mockWorkoutExercises,
    { trigger: "acute_fatigue" },
    gymProfile,
    "tr",
  );

  assert.equal(result.trigger, "acute_fatigue");
  const totalOriginalSets = mockWorkoutExercises.reduce((sum, e) => sum + e.sets, 0);
  const totalAdaptedSets = result.adaptedExercises.reduce((sum, e) => sum + e.sets, 0);
  assert.ok(totalAdaptedSets < totalOriginalSets, "Fatigue adaptation should decrease total sets");
});
