import assert from "node:assert/strict";
import test from "node:test";

const { globalSearch, matchScore, foldSearchText, MAX_SEARCH_RESULTS } = await import("../lib/global-search.ts");
const { DEFAULT_PROGRAM_EXERCISE } = await import("../lib/training-programs.ts");

const views = [
  { view: "plan", title: "Ana ekran", subtitle: "", keywords: "ana ekran özet günlük plan" },
  { view: "nutrition", title: "Kalori takibi", subtitle: "", keywords: "beslenme kalori öğün su" },
  { view: "library", title: "Hareket kütüphanesi", subtitle: "", keywords: "kütüphane hareket katalog" },
];

const programs = [
  { id: "custom-1", name: "İtiş Günü", exercises: [{ name: "Şınav", ...DEFAULT_PROGRAM_EXERCISE }], updatedAt: "2026-01-01T00:00:00.000Z" },
];

test("iki harften kısa sorgu aramayı çalıştırmaz", () => {
  // Tek harf 873 kayıtta yüzlerce eşleşme verir ve panel çöp doldururdu.
  assert.deepEqual(globalSearch("s", { programs, views }), []);
  assert.deepEqual(globalSearch("  ", { programs, views }), []);
});

test("tam eşleşen ad, içinde geçenlerin önüne çıkar", () => {
  assert.ok(matchScore("Squat", "squat") > matchScore("Bulgarian Split Squat", "squat"));
  assert.ok(matchScore("Squat Thrust", "squat") > matchScore("Bulgarian Split Squat", "squat"));
  assert.equal(matchScore("Squat", "deadlift"), 0);
});

test("Türkçe büyük/küçük harf ve aksan farkı eşleşmeyi bozmaz", () => {
  // "I" harfi Türkçe kurallarında "ı" olur; iki taraf aynı kuralla küçülmezse
  // "Incline" araması katalogdaki "incline" ile eşleşmiyordu.
  assert.equal(foldSearchText("İTİŞ"), foldSearchText("itiş"));
  assert.equal(foldSearchText("Göğüs"), foldSearchText("gogus"));
  const results = globalSearch("itiş", { programs, views });
  assert.ok(results.some((result) => result.kind === "program" && result.title === "İtiş Günü"));
});

test("kullanıcının programı, adı geçen hareketten önce gelir", () => {
  const results = globalSearch("itiş", { programs, views });
  assert.equal(results[0].kind, "program");
  assert.equal(results[0].view, "workout");
  assert.equal(results[0].programId, "custom-1");
});

test("ekran adı aranınca o ekrana giden sonuç döner", () => {
  const results = globalSearch("kalori", { programs, views });
  const screen = results.find((result) => result.kind === "view");
  assert.ok(screen, "ekran sonucu yok");
  assert.equal(screen.view, "nutrition");
});

test("hareket sonucu kütüphaneye ve doğrudan hareketin kimliğine bağlanır", () => {
  const results = globalSearch("squat", { programs, views });
  const exercise = results.find((result) => result.kind === "exercise");
  assert.ok(exercise, "katalogdan sonuç gelmedi");
  assert.equal(exercise.view, "library");
  assert.match(exercise.exerciseId, /^[a-zA-Z0-9_-]+$/);
});

test("sonuç sayısı sınırlıdır", () => {
  const results = globalSearch("press", { programs, views });
  assert.ok(results.length > 0);
  assert.ok(results.length <= MAX_SEARCH_RESULTS, `sonuç sayısı ${results.length}`);
  // Kimlikler benzersiz olmalı; React listesi aynı anahtarla çakışmasın.
  assert.equal(new Set(results.map((result) => result.id)).size, results.length);
});
