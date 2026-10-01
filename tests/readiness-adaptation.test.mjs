import assert from "node:assert/strict";
import test from "node:test";
import { evaluateReadinessAndAdapt } from "../lib/training/readiness-adapter.ts";
import { normalizeTrainingProfile } from "../lib/training/profile-normalizer.ts";

const mockProfile = normalizeTrainingProfile({
  goal: "Kas geliştirmek",
  environment: "Salon",
  equipment: "Tam salon",
  level: "Orta seviye",
});

const mockExercises = [
  { id: "bench-press", name: "Barbell Bench Press", area: "Göğüs", sets: 4, reps: "8–10", restSeconds: 90 },
  { id: "bent-over-db-row", name: "Bent Over Dumbbell Row", area: "Sırt", sets: 4, reps: "10–12", restSeconds: 75 },
  { id: "squat", name: "Barbell Squat", area: "Ön Bacak", sets: 4, reps: "8–10", restSeconds: 90 },
  { id: "romanian-deadlift", name: "Romanian Deadlift", area: "Arka Bacak", sets: 3, reps: "10–12", restSeconds: 75 },
  { id: "machine-shoulder-press", name: "Machine Shoulder Press", area: "Omuz", sets: 3, reps: "12–15", restSeconds: 60 },
];

test("Readiness: Yüksek enerji ve iyi uyku durumunda adaptasyon gerekmez", () => {
  const result = evaluateReadinessAndAdapt(
    {
      energy: 9,
      sleepQuality: 8,
      fatigue: 2,
      hasSoreness: false,
      sorenessAreas: [],
      discomfortLevel: 1,
    },
    mockExercises,
    mockProfile,
  );

  assert.equal(result.needsAdaptation, false);
  assert.equal(result.recommendedIntensity, "normal");
  assert.equal(result.volumeReductionPercent, 0);
  assert.equal(result.adaptedExercises.length, mockExercises.length);
  assert.equal(result.adaptedExercises[0].sets, 4);
});

test("Readiness: Aşırı yorgunluk ve düşük enerjide set hacmi düşürülür ve dinlenme uzatılır", () => {
  const result = evaluateReadinessAndAdapt(
    {
      energy: 2, // Düşük enerji
      sleepQuality: 3, // Kötü uyku
      fatigue: 9, // Yüksek yorgunluk
      hasSoreness: false,
      sorenessAreas: [],
      discomfortLevel: 2,
    },
    mockExercises,
    mockProfile,
  );

  assert.equal(result.needsAdaptation, true);
  assert.equal(result.recommendedIntensity, "reduced");
  assert.ok(result.volumeReductionPercent >= 25, `Hacim indirimi en az %25 olmalı (${result.volumeReductionPercent}%)`);
  assert.ok(result.adaptedExercises[0].sets < 4, "4 setlik hareket daha az sete inmeli");
  assert.ok(result.adaptedExercises[0].restSeconds > 90, "Dinlenme süresi uzatılmalı");
  assert.ok(result.explanationTr.includes("azaltıldı"));
});

test("Readiness: Bacakta kas ağrısı (soreness) varken squat ve deadlift deload edilir", () => {
  const result = evaluateReadinessAndAdapt(
    {
      energy: 7,
      sleepQuality: 7,
      fatigue: 4,
      hasSoreness: true,
      sorenessAreas: ["quadriceps", "hamstrings"],
      discomfortLevel: 3,
    },
    mockExercises,
    mockProfile,
  );

  assert.equal(result.needsAdaptation, true);
  assert.ok(result.deloadedMuscles.includes("quadriceps") || result.deloadedMuscles.includes("hamstrings"));

  // Squat (quadriceps) should be reduced to 2 sets
  const squat = result.adaptedExercises.find((e) => e.name.toLowerCase().includes("squat"));
  assert.ok(squat);
  assert.equal(squat.sets, 2, "Ağrılı kas grubunun seti 2'ye indirilmeli");

  // Upper body (bench press) can keep its normal sets
  const bench = result.adaptedExercises.find((e) => e.name.toLowerCase().includes("bench"));
  assert.ok(bench);
  assert.equal(bench.sets, 4, "Ağrısız bölge normal setini korumalı");
});

test("Readiness: Yüksek rahatsızlıkta (discomfort >= 7) aktif toparlanma önerilir", () => {
  const result = evaluateReadinessAndAdapt(
    {
      energy: 5,
      sleepQuality: 6,
      fatigue: 6,
      hasSoreness: true,
      sorenessAreas: ["shoulders"],
      discomfortLevel: 8, // Yüksek rahatsızlık
    },
    mockExercises,
    mockProfile,
  );

  assert.equal(result.needsAdaptation, true);
  assert.equal(result.recommendedIntensity, "active_recovery");
  assert.ok(result.volumeReductionPercent >= 30);
});
