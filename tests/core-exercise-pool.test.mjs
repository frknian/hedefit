import assert from "node:assert/strict";
import test from "node:test";

import corePool from "../data/core-exercises.json" with { type: "json" };
import exercises from "../data/exercises.json" with { type: "json" };
import supplement from "../data/exercises-supplement.json" with { type: "json" };
import { POST, buildLocalPlan, profileSignals } from "../app/api/generate-plan/route.ts";
import { getExercisesForProfile } from "../lib/exercise-service.ts";
import { catalogExerciseNamesTr } from "../lib/exercise-names-tr.ts";
import { translateExerciseName } from "../lib/exercise-translations.ts";
import { QUESTION, emptyHistory } from "../lib/onboarding-questions.ts";
import { coreCooldownIds, coreExerciseIds, coreSlotMates, coreWarmupIds, getCoreEntry } from "../lib/training/core-pool.ts";
import { replaceExercise } from "../lib/training/exercise-replacer.ts";
import { getStandardizedExerciseById } from "../lib/training/exercise-metadata.ts";
import { normalizeTrainingProfile } from "../lib/training/profile-normalizer.ts";
import { DEFAULT_ROTATION_PERIOD, isNewBlockDue, loadRecentExerciseIds, normalizeRotationPeriod, resolveRotationDate, rotationBlockId, rotationInfo, rotationSeed, seededUnit } from "../lib/training/rotation.ts";
import { authorizedRequest, withAuthenticatedFetch, withSupabaseAuthEnv } from "./helpers/auth.mjs";

const atlas = new Map([...exercises, ...supplement].map((exercise) => [exercise.id, exercise]));

function history(answers) {
  const list = emptyHistory();
  for (const [key, value] of Object.entries(answers)) list[QUESTION[key]] = value;
  return list;
}

const profiles = {
  salon: { age: 28, gender: "Erkek", height: 178, weight: 80, environment: "Salon", equipment: "Tam donanımlı salon", goal: "Kas geliştirmek", history: history({ goal: "Kas geliştirmek", experience: "Düzenli", level: "Orta seviye", recentFrequency: "3–4 gün", availableDays: "4–5 gün", sessionMinutes: "60+ dakika", location: "Salon", equipment: "Tam donanımlı salon" }) },
  evDambil: { age: 30, gender: "Kadın", height: 165, weight: 60, environment: "Evde", equipment: "Ayarlanabilir dambıl", goal: "Yağ yakmak", history: history({ goal: "Yağ yakmak", experience: "Hayır, spora yeni başlıyorum", level: "Yeni başlıyorum", recentFrequency: "0 gün", availableDays: "3–4 gün", sessionMinutes: "45 dakika", location: "Evde", equipment: "Ayarlanabilir dambıl" }) },
  evEkipmansiz: { age: 35, gender: "Erkek", height: 175, weight: 85, environment: "Evde", equipment: "Ekipmanım yok", goal: "Güçlenmek", history: history({ goal: "Güçlenmek", experience: "Düzenli", level: "Orta seviye", recentFrequency: "3–4 gün", availableDays: "3–4 gün", sessionMinutes: "45 dakika", location: "Evde", equipment: "Ekipmanım yok" }) },
};

// Route'un yerel plan için kurduğu katalogla aynı (kırpılmamış).
function plan(profileName, variety = {}) {
  const payload = profiles[profileName];
  const catalog = getExercisesForProfile(/salon|gym/i.test(payload.environment), payload.equipment, payload.environment, "", Number.POSITIVE_INFINITY);
  return buildLocalPlan(profileSignals(payload), catalog, "tr", variety);
}
const planIds = (result) => result.sessions.flatMap((session) => session.exercises.map((exercise) => exercise.id));
const isMain = (id) => getCoreEntry(id)?.role === "main";
const accessories = (ids) => ids.filter((id) => !isMain(id));
const overlap = (a, b) => { const set = new Set(b); return a.filter((id) => set.has(id)).length / Math.max(1, a.length); };

// -----------------------------------------------------------------------------
// Havuzun bütünlüğü
// -----------------------------------------------------------------------------
test("çekirdek havuzdaki her hareket atlasta, aktif ve tam görselli", () => {
  const seen = new Set();
  for (const slot of corePool.slots) {
    for (const entry of slot.exercises) {
      const exercise = atlas.get(entry.id);
      assert.ok(exercise, `${entry.id} atlasta yok`);
      assert.notEqual(exercise.isActive, false, `${entry.id} aktif değil`);
      // Görseli henüz çekilmemiş, elle yazılmış ek hareketler "missing" olabilir (bkz. aşağıdaki ek hareket testi).
      if (exercise.source === "supplement") assert.ok(["complete", "missing"].includes(exercise.mediaStatus), `${entry.id} yarım görselli`);
      else assert.equal(exercise.mediaStatus, "complete", `${entry.id} görseli eksik`);
      assert.ok(!seen.has(entry.id), `${entry.id} birden çok slotta`);
      seen.add(entry.id);
    }
  }
  assert.equal(seen.size, corePool.summary.strength);
  assert.deepEqual([...seen].sort(), coreExerciseIds().sort());
});

test("havuz ileri seviye hareket içermez ve her slotun staple'ları başlangıç/orta seviyedir", () => {
  for (const slot of corePool.slots) {
    for (const entry of slot.exercises) assert.notEqual(atlas.get(entry.id).level, "advanced", `${entry.id} ileri seviye`);
    assert.deepEqual(slot.exercises.slice(0, 2).map((entry) => entry.tier), ["staple", "staple"].slice(0, Math.min(2, slot.exercises.length)));
  }
});

test("her slot rotasyon için en az üç alternatif ve evde yapılabilen hareket taşır (bilinen boşluklar hariç)", () => {
  // Atlasta karşılığı olmayan, rapora yazılmış boşluklar; yeni boşluk eklenirse test kırılır.
  const knownHomeGaps = new Set(["lower_back"]);
  for (const slot of corePool.slots) {
    assert.ok(slot.exercises.length >= 3, `${slot.key}: ${slot.exercises.length} alternatif`);
    if (slot.role === "conditioning") continue;
    const home = slot.exercises.filter((entry) => (atlas.get(entry.id).environment || []).includes("home")).length;
    if (!knownHomeGaps.has(slot.key)) assert.ok(home >= 2, `${slot.key}: evde yapılabilen ${home}`);
  }
});

test("havuz hareketlerinin Türkçe adı vardır (kasıtlı aynı bırakılan spor terimleri dahil)", () => {
  for (const id of coreExerciseIds()) {
    const { name } = atlas.get(id);
    assert.ok(name in catalogExerciseNamesTr || translateExerciseName(name, "tr") !== name, `${name}: Türkçe adı yok`);
  }
});

test("ısınma ve soğuma listesindeki hareketler atlasta vardır", () => {
  assert.ok(coreWarmupIds().length >= 5 && coreCooldownIds().length >= 10);
  for (const id of [...coreWarmupIds(), ...coreCooldownIds()]) assert.ok(atlas.get(id), `${id} atlasta yok`);
});

// -----------------------------------------------------------------------------
// Plan üretimi: havuz + çeşitlilik
// -----------------------------------------------------------------------------
test("planlar çekirdek havuzdan seçilir", () => {
  for (const [name, minimumShare] of [["salon", 1], ["evDambil", 1], ["evEkipmansiz", 0.8]]) {
    const ids = planIds(plan(name, { rotationSeed: "u:0" }));
    const share = ids.filter((id) => getCoreEntry(id)).length / ids.length;
    assert.ok(share >= minimumShare, `${name}: çekirdek payı ${share.toFixed(2)}`);
  }
});

test("hipertrofi ve kuvvet seanslarına kondisyon dolgusu (jumping jacks, burpee…) girmez", () => {
  for (const name of ["salon", "evEkipmansiz"]) {
    for (const seed of ["u:0", "u:1", "u:2", "u:3"]) {
      const slots = planIds(plan(name, { rotationSeed: seed })).map((id) => getCoreEntry(id)?.slot);
      assert.ok(!slots.includes("conditioning"), `${name}/${seed}: kondisyon hareketi seçildi`);
    }
  }
});

test("tohumsuz plan tekrarlanabilir, aynı tohum aynı planı verir", () => {
  assert.deepEqual(planIds(plan("salon")), planIds(plan("salon")));
  assert.deepEqual(planIds(plan("salon", { rotationSeed: "u:7" })), planIds(plan("salon", { rotationSeed: "u:7" })));
});

test("farklı tohum yardımcı hareketleri değiştirir, ana hareketleri büyük ölçüde sabit tutar", () => {
  for (const name of ["salon", "evDambil"]) {
    const base = planIds(plan(name, { rotationSeed: "u:0" }));
    const other = planIds(plan(name, { rotationSeed: "u:1" }));
    assert.notDeepEqual(accessories(base), accessories(other), `${name}: yardımcılar değişmedi`);
    assert.ok(overlap(base.filter(isMain), other.filter(isMain)) >= 0.85, `${name}: ana hareketler çok değişti`);
  }
});

test("son haftalarda yapılan yardımcı hareketler bir sonraki blokta geri çekilir", () => {
  for (const [name, maxOverlap] of [["evDambil", 0.35], ["salon", 0.75]]) {
    const first = planIds(plan(name, { rotationSeed: "u:0" }));
    const second = planIds(plan(name, { rotationSeed: "u:1", recentExerciseIds: first }));
    assert.ok(overlap(accessories(second), accessories(first)) <= maxOverlap, `${name}: yardımcı örtüşmesi yüksek`);
  }
});

test("geçmiş cezası ana hareketlere uygulanmaz (ilerleme için sabit kalırlar)", () => {
  const first = planIds(plan("salon", { rotationSeed: "u:0" }));
  const second = planIds(plan("salon", { rotationSeed: "u:1", recentExerciseIds: first }));
  assert.ok(overlap(first.filter(isMain), second.filter(isMain)) >= 0.85);
});

test("bir oturumda aynı slottan tekrar eden hareket nadirdir (aynı-slot cezası)", () => {
  // 3 profil × 6 tohum × tüm oturumlar: ceza açıkken 6, kapalıyken 14 tekrar ölçüldü.
  let repeats = 0;
  for (const name of ["salon", "evDambil", "evEkipmansiz"]) {
    for (const seed of ["u:0", "u:1", "u:2", "u:3", "u:4", "u:5"]) {
      for (const session of plan(name, { rotationSeed: seed }).sessions) {
        const counts = new Map();
        for (const exercise of session.exercises) {
          const slot = getCoreEntry(exercise.id)?.slot;
          if (slot) counts.set(slot, (counts.get(slot) || 0) + 1);
        }
        for (const [slot, count] of counts) {
          assert.ok(count <= 2, `${name}/${seed}/${session.dayName}: ${slot} ×${count}`);
          repeats += count - 1;
        }
      }
    }
  }
  assert.ok(repeats <= 8, `aynı slottan tekrar sayısı ${repeats}`);
});

test("hareket değiştirme önce aynı slottaki alternatifleri önerir", () => {
  const profile = normalizeTrainingProfile({ ...profiles.salon });
  const original = corePool.slots.find((slot) => slot.key === "vertical_pull").exercises[0].id;
  const result = replaceExercise(original, "disliked", profile);
  assert.ok(result, "alternatif bulunmalı");
  assert.ok(coreSlotMates(original).includes(result.replacementExercise.id), `${result.replacementExercise.id} aynı slottan değil`);
});

// -----------------------------------------------------------------------------
// Rotasyon yardımcıları
// -----------------------------------------------------------------------------
const day = (iso) => new Date(`${iso}T00:00:00.000Z`);

test("dönem doğrulanır: yalnız 'weekly' haftalık, geri kalan her şey aylık", () => {
  assert.equal(normalizeRotationPeriod("weekly"), "weekly");
  assert.equal(normalizeRotationPeriod("monthly"), "monthly");
  for (const value of [undefined, null, "", "daily", 7, {}]) assert.equal(normalizeRotationPeriod(value), DEFAULT_ROTATION_PERIOD);
  assert.equal(DEFAULT_ROTATION_PERIOD, "monthly");
});

test("haftalık blok Pazartesi, aylık blok ayın 1'i başlar", () => {
  // 2026-10-04 Pazar, 2026-10-05 Pazartesi
  assert.equal(rotationBlockId("weekly", day("2026-10-04")), rotationBlockId("weekly", day("2026-09-28")));
  assert.equal(rotationBlockId("weekly", day("2026-10-05")), rotationBlockId("weekly", day("2026-10-04")) + 1);
  assert.equal(rotationBlockId("weekly", day("2026-10-11")), rotationBlockId("weekly", day("2026-10-05")));
  assert.equal(rotationBlockId("monthly", day("2026-10-01")), rotationBlockId("monthly", day("2026-10-31")));
  assert.equal(rotationBlockId("monthly", day("2026-11-01")), rotationBlockId("monthly", day("2026-10-31")) + 1);
  assert.equal(rotationBlockId("monthly", day("2027-01-01")), rotationBlockId("monthly", day("2026-12-31")) + 1, "yıl geçişi");
});

test("blok numaraları Android ile aynıdır (android PlanRotationTest ile aynı referans değerler)", () => {
  for (const [iso, weekly, monthly] of [
    ["1970-01-05", 0, 23640], ["1970-01-11", 0, 23640], ["2026-10-04", 2960, 24321], ["2026-10-05", 2961, 24321],
    ["2026-10-11", 2961, 24321], ["2026-10-12", 2962, 24321], ["2026-12-31", 2973, 24323], ["2027-01-01", 2973, 24324],
  ]) {
    assert.equal(rotationBlockId("weekly", day(iso)), weekly, `${iso} haftalık`);
    assert.equal(rotationBlockId("monthly", day(iso)), monthly, `${iso} aylık`);
  }
});

test("rotationInfo bloğun başlangıcını ve bir sonraki bloğu verir", () => {
  assert.deepEqual(rotationInfo("weekly", day("2026-10-07")), { period: "weekly", blockId: rotationBlockId("weekly", day("2026-10-07")), startsAt: "2026-10-05", nextBlockAt: "2026-10-12" });
  assert.deepEqual(rotationInfo("monthly", day("2026-10-07")), { period: "monthly", blockId: rotationBlockId("monthly", day("2026-10-07")), startsAt: "2026-10-01", nextBlockAt: "2026-11-01" });
  assert.equal(rotationInfo("monthly", day("2026-12-15")).nextBlockAt, "2027-01-01");
});

test("yeni blok tetikleyicisi: üretim bloğu geçildiyse true, aynı blokta ya da üretim yoksa false", () => {
  assert.equal(isNewBlockDue("weekly", day("2026-10-05"), day("2026-10-11")), false);
  assert.equal(isNewBlockDue("weekly", day("2026-10-05"), day("2026-10-12")), true);
  assert.equal(isNewBlockDue("monthly", day("2026-10-05"), day("2026-10-31")), false);
  assert.equal(isNewBlockDue("monthly", day("2026-10-05"), day("2026-11-01")), true);
  // Aynı program haftalıkta 'yeni blok' sayılırken aylıkta sayılmayabilir: dönem ayarı belirleyici.
  assert.equal(isNewBlockDue("weekly", day("2026-10-05"), day("2026-10-20")), true);
  assert.equal(isNewBlockDue("monthly", day("2026-10-05"), day("2026-10-20")), false);
  assert.equal(isNewBlockDue("monthly", null, day("2026-10-20")), false);
});

test("yerel tarih doğrulanır: geçerli ve yakın tarih kullanılır, bozuk ya da uzak tarih yok sayılır", () => {
  const now = new Date("2026-10-05T01:00:00.000Z");
  assert.equal(resolveRotationDate("2026-10-05", now).toISOString(), "2026-10-05T00:00:00.000Z");
  assert.equal(resolveRotationDate("2026-10-06", now).toISOString(), "2026-10-06T00:00:00.000Z", "UTC+14'e kadar yerel gün ileride olabilir");
  for (const bad of ["2026-10-30", "2026-02-31", "5 Ekim", "", 20261005, null, undefined]) {
    assert.equal(resolveRotationDate(bad, now).toISOString(), "2026-10-05T00:00:00.000Z", String(bad));
  }
});

test("tohum aynı blokta sabit, bir sonraki blokta farklıdır; dönem ve override tohumu değiştirir", () => {
  assert.equal(rotationSeed("u1", "weekly", day("2026-10-05")), rotationSeed("u1", "weekly", day("2026-10-11")));
  assert.notEqual(rotationSeed("u1", "weekly", day("2026-10-05")), rotationSeed("u1", "weekly", day("2026-10-12")));
  assert.equal(rotationSeed("u1", "monthly", day("2026-10-01")), rotationSeed("u1", "monthly", day("2026-10-31")));
  assert.notEqual(rotationSeed("u1", "monthly", day("2026-10-31")), rotationSeed("u1", "monthly", day("2026-11-01")));
  assert.notEqual(rotationSeed("u1", "weekly", day("2026-10-05")), rotationSeed("u2", "weekly", day("2026-10-05")));
  assert.notEqual(rotationSeed("u1", "weekly", day("2026-10-05")), rotationSeed("u1", "monthly", day("2026-10-05")));
  assert.notEqual(rotationSeed("u1", "monthly", day("2026-10-05"), "yenile-2"), rotationSeed("u1", "monthly", day("2026-10-05")));
});

test("seededUnit belirleyici ve [0,1) aralığındadır", () => {
  assert.equal(seededUnit("a", "x"), seededUnit("a", "x"));
  assert.notEqual(seededUnit("a", "x"), seededUnit("b", "x"));
  for (const id of coreExerciseIds()) {
    const value = seededUnit("seed", id);
    assert.ok(value >= 0 && value < 1);
  }
});

test("geçmiş okuma jeton yokken ya da ağ hatasında boş döner, asla fırlatmaz", { concurrency: false }, async () => {
  assert.deepEqual(await loadRecentExerciseIds(new Request("http://localhost/api/generate-plan")), []);
  const previousFetch = globalThis.fetch;
  const restoreAuthEnv = withSupabaseAuthEnv();
  globalThis.fetch = async () => { throw new TypeError("ağ yok"); };
  try {
    assert.deepEqual(await loadRecentExerciseIds(authorizedRequest("http://localhost/api/generate-plan")), []);
  } finally {
    globalThis.fetch = previousFetch;
    restoreAuthEnv();
  }
});

// -----------------------------------------------------------------------------
// Rota entegrasyonu
// -----------------------------------------------------------------------------
async function postPlan(userId, logRows, extra = {}) {
  const previousKey = process.env.OPENAI_API_KEY;
  const previousAiKey = process.env.AI_API_KEY;
  const previousFetch = globalThis.fetch;
  const restoreAuthEnv = withSupabaseAuthEnv();
  const requested = [];
  delete process.env.OPENAI_API_KEY;
  delete process.env.AI_API_KEY;
  globalThis.fetch = withAuthenticatedFetch(async (url) => {
    requested.push(String(url));
    if (String(url).includes("/rest/v1/workout_exercise_logs")) return Response.json(logRows.map((id) => ({ exercise_id: id })));
    throw new TypeError("beklenmeyen ağ isteği");
  }, userId);
  try {
    const response = await POST(authorizedRequest("http://localhost/api/generate-plan", { method: "POST", body: JSON.stringify({ ...profiles.evDambil, ...extra }) }));
    return { status: response.status, body: await response.json(), requested };
  } finally {
    globalThis.fetch = previousFetch;
    restoreAuthEnv();
    if (previousKey === undefined) delete process.env.OPENAI_API_KEY; else process.env.OPENAI_API_KEY = previousKey;
    if (previousAiKey === undefined) delete process.env.AI_API_KEY; else process.env.AI_API_KEY = previousAiKey;
  }
}

test("generate-plan rotası: aynı blokta aynı plan, kullanıcıya göre ve geçmişe göre farklı yardımcılar", { concurrency: false }, async () => {
  const first = await postPlan("11111111-1111-4111-8111-111111111111", []);
  const again = await postPlan("11111111-1111-4111-8111-111111111111", []);
  assert.equal(first.status, 200);
  assert.ok(first.requested.some((url) => url.includes("/rest/v1/workout_exercise_logs")), "son haftaların kayıtları okunmalı");
  const ids = (result) => result.body.sessions.flatMap((session) => session.exercises.map((exercise) => exercise.id));
  assert.deepEqual(ids(first), ids(again), "aynı kullanıcı + aynı blok = aynı plan");

  const withHistory = await postPlan("11111111-1111-4111-8111-111111111111", accessories(ids(first)));
  assert.notDeepEqual(accessories(ids(withHistory)), accessories(ids(first)), "yapılan yardımcılar bir sonraki planda değişmeli");
  assert.ok(overlap(ids(first).filter(isMain), ids(withHistory).filter(isMain)) >= 0.85, "ana hareketler sabit kalmalı");
});

// -----------------------------------------------------------------------------
// Ek hareketler (data/exercises-supplement.json) ve ev ekipmanı kapsamı
// -----------------------------------------------------------------------------
const HOME_MUSCLES = ["chest", "back", "shoulders", "quadriceps", "hamstrings", "glutes", "biceps", "triceps", "calves", "core"];

for (const [label, equipment, allowedGroups] of [["yalnız dambıl", "Ayarlanabilir dambıl", ["bodyweight", "dumbbell"]], ["yalnız direnç bandı", "Direnç bandı", ["bodyweight", "bands"]]]) {
  for (const [days, minutes, expectedMuscles] of [
    ["3–4 gün", "60+ dakika", HOME_MUSCLES.filter((muscle) => muscle !== "calves")],
    ["5+ gün", "60+ dakika", HOME_MUSCLES],
  ]) {
    test(`evde ${label}: ${days} / ${minutes} planı tüm vücudu çalıştırır, yalnız eldeki ekipmanı ve havuzu kullanır`, () => {
      const payload = { age: 30, gender: "Kadın", height: 168, weight: 65, environment: "Evde", equipment, goal: "Kas geliştirmek", history: history({ goal: "Kas geliştirmek", experience: "Düzenli", level: "Orta seviye", recentFrequency: "3–4 gün", availableDays: days, sessionMinutes: minutes, location: "Evde", equipment }) };
      const catalog = getExercisesForProfile(false, equipment, "evde", "", Number.POSITIVE_INFINITY);
      for (const seed of ["u:0", "u:1", "u:2"]) {
        const result = buildLocalPlan(profileSignals(payload), catalog, "tr", { rotationSeed: seed });
        const covered = new Set();
        for (const exercise of planIds(result).map((id) => getStandardizedExerciseById(id))) {
          assert.ok(exercise.equipmentOptions.some((option) => option.every((item) => allowedGroups.includes(item))), `${label}/${seed}: ${exercise.name} eldeki ekipmanla yapılamaz`);
          assert.ok(getCoreEntry(exercise.id), `${label}/${seed}: ${exercise.name} havuz dışı`);
          exercise.primaryMuscles.forEach((muscle) => covered.add(muscle));
        }
        for (const muscle of expectedMuscles) assert.ok(covered.has(muscle), `${label}/${seed}: ${muscle} çalışılmıyor`);
        const backMoves = planIds(result).filter((id) => getStandardizedExerciseById(id).primaryMuscles.includes("back")).length;
        assert.ok(backMoves >= 2, `${label}/${seed}: haftada ${backMoves} sırt hareketi`);
      }
    });
  }
}

test("generate-plan rotası: dönem ve yerel tarih bloğu belirler, yanıt blok bilgisini döndürür", { concurrency: false }, async () => {
  const user = "22222222-2222-4222-8222-222222222222";
  const ids = (result) => result.body.sessions.flatMap((session) => session.exercises.map((exercise) => exercise.id));
  const weeklyA = await postPlan(user, [], { rotationPeriod: "weekly", localDate: new Date().toISOString().slice(0, 10) });
  assert.equal(weeklyA.body.rotation.period, "weekly");
  assert.match(weeklyA.body.rotation.startsAt, /^\d{4}-\d{2}-\d{2}$/);
  assert.ok(weeklyA.body.rotation.nextBlockAt > weeklyA.body.rotation.startsAt);
  // Varsayılan dönem aylıktır; geçersiz değer de aylığa düşer.
  const fallback = await postPlan(user, [], { rotationPeriod: "daily" });
  assert.equal(fallback.body.rotation.period, "monthly");
  // Aynı dönem + aynı gün = aynı plan; farklı dönem farklı tohum = farklı yardımcılar.
  const weeklyAgain = await postPlan(user, [], { rotationPeriod: "weekly", localDate: new Date().toISOString().slice(0, 10) });
  assert.deepEqual(ids(weeklyA), ids(weeklyAgain));
  assert.notDeepEqual(accessories(ids(weeklyA)), accessories(ids(fallback)), "haftalık ve aylık tohum farklı yardımcılar vermeli");
});
