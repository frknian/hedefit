import assert from "node:assert/strict";
import test from "node:test";

const {
  DEFAULT_PROGRAM_EXERCISE,
  PROGRAM_EXERCISE_LIMITS,
  estimateProgramMinutes,
  moveProgramExercise,
  normalizeCustomPrograms,
  normalizeProgramExercise,
  programExerciseNames,
} = await import("../lib/training-programs.ts");

test("eski kayıtlar (yalnız hareket adı) reçeteli biçime taşınır", () => {
  // Kullanıcının cihazında ZATEN kayıtlı programlar var; sürüm atlarken
  // silinmemeliler. Eski biçim `exerciseNames: string[]` idi.
  const [program] = normalizeCustomPrograms([
    { id: "custom-1", name: "Push", exerciseNames: ["Şınav", "Dambıl Omuz Press"], updatedAt: "2026-01-01T00:00:00.000Z" },
  ]);
  assert.equal(program.exercises.length, 2);
  assert.deepEqual(programExerciseNames(program), ["Şınav", "Dambıl Omuz Press"]);
  // Reçete verilmediyse varsayılan uygulanır, hareket kaybolmaz.
  assert.deepEqual(program.exercises[0], { name: "Şınav", ...DEFAULT_PROGRAM_EXERCISE });
});

test("yeni biçim okunur ve aynı hareket iki kez eklenmez", () => {
  const [program] = normalizeCustomPrograms([
    {
      id: "custom-1",
      name: "Alt",
      exercises: [
        { name: "Squat", sets: 5, reps: "8-10", restSeconds: 120, dropSet: true },
        { name: "Squat", sets: 3, reps: "12", restSeconds: 60 },
      ],
      updatedAt: "2026-01-01T00:00:00.000Z",
    },
  ]);
  assert.equal(program.exercises.length, 1);
  assert.deepEqual(program.exercises[0], { name: "Squat", sets: 5, reps: "8-10", restSeconds: 120, dropSet: true });
});

test("sınır dışı değerler kaydedilmeden önce kırpılır", () => {
  // Sayı alanına elle 999 yazılabiliyor; bunu diske yazmak seans ekranında
  // bitmeyen bir antrenman üretirdi.
  const tooMany = normalizeProgramExercise({ name: "Plank", sets: 99, restSeconds: 9999, reps: "x".repeat(50) });
  assert.equal(tooMany.sets, PROGRAM_EXERCISE_LIMITS.sets.max);
  assert.equal(tooMany.restSeconds, PROGRAM_EXERCISE_LIMITS.rest.max);
  assert.equal(tooMany.reps.length, PROGRAM_EXERCISE_LIMITS.reps.maxLength);

  const tooFew = normalizeProgramExercise({ name: "Plank", sets: 0, restSeconds: -30 });
  assert.equal(tooFew.sets, PROGRAM_EXERCISE_LIMITS.sets.min);
  assert.equal(tooFew.restSeconds, PROGRAM_EXERCISE_LIMITS.rest.min);

  assert.equal(normalizeProgramExercise({ sets: 3 }), null, "adsız kayıt atılmalı");
  assert.equal(normalizeProgramExercise("  "), null);
});

test("sıralama taşıması listeyi bozmaz", () => {
  const list = ["a", "b", "c"].map((name) => ({ name, ...DEFAULT_PROGRAM_EXERCISE }));
  assert.deepEqual(moveProgramExercise(list, 0, 2).map((item) => item.name), ["b", "c", "a"]);
  assert.deepEqual(moveProgramExercise(list, 2, 0).map((item) => item.name), ["c", "a", "b"]);
  // Sınır dışı istek listeyi aynen döndürür (kopya bile üretmez).
  assert.equal(moveProgramExercise(list, 0, 9), list);
  assert.equal(moveProgramExercise(list, -1, 0), list);
});

test("süre tahmini tekrar ve dinlenmeyi birlikte sayar", () => {
  const light = [{ name: "A", sets: 3, reps: "10", restSeconds: 60, dropSet: false }];
  const heavy = [{ name: "A", sets: 5, reps: "10", restSeconds: 120, dropSet: false }];
  assert.ok(estimateProgramMinutes(heavy) > estimateProgramMinutes(light), "daha çok set ve dinlenme daha uzun sürmeli");

  // Süreli hareket ("30 sn") tekrar gibi ölçeklenmemeli.
  const timed = estimateProgramMinutes([{ name: "Plank", sets: 3, reps: "30 sn", restSeconds: 60, dropSet: false }]);
  const reps30 = estimateProgramMinutes([{ name: "Squat", sets: 3, reps: "30", restSeconds: 60, dropSet: false }]);
  assert.ok(timed < reps30, "30 sn, 30 tekrardan kısa sürmeli");

  assert.equal(estimateProgramMinutes([]), 0);
  // Son setten sonra dinlenme sayılmaz: tek setlik hareket yalnız iş süresidir.
  assert.equal(estimateProgramMinutes([{ name: "A", sets: 1, reps: "10", restSeconds: 300, dropSet: false }]), 1);
});
