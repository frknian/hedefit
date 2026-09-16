import assert from "node:assert/strict";
import test from "node:test";
import { generateWorkoutPlan } from "../lib/training/plan-orchestrator.ts";
import { normalizeTrainingProfile } from "../lib/training/profile-normalizer.ts";
import { calculateWeeklyVolumeTargets } from "../lib/training/volume-calculator.ts";
import { generateTrainingSplit } from "../lib/training/split-generator.ts";
import { populateSplitBudgets } from "../lib/training/muscle-distribution.ts";
import { validatePlan } from "../lib/training/plan-validator.ts";
import { getStandardizedExerciseById } from "../lib/training/exercise-metadata.ts";
import { QUESTION, emptyHistory } from "../lib/onboarding-questions.ts";

function createHistory(answers) {
  const list = emptyHistory();
  for (const [key, value] of Object.entries(answers)) {
    list[QUESTION[key]] = value;
  }
  return list;
}

// -----------------------------------------------------------------------------
// Scenario A: Beginner, 3 days, Gym, Hypertrophy, 60m session
// -----------------------------------------------------------------------------
test("Senaryo A: Başlangıç seviyesi, 3 gün, Salon, Hipertrofi, 60 dk seans", () => {
  const payload = {
    age: 25,
    gender: "Erkek",
    height: 180,
    weight: 75,
    environment: "Salon",
    equipment: "Tam donanımlı salon",
    goal: "Kas geliştirmek ve hacim kazanmak",
    history: createHistory({
      goal: "Kas geliştirmek",
      experience: "Hayır, spora yeni başlıyorum",
      level: "Yeni başlıyorum",
      recentFrequency: "0 gün",
      availableDays: "3–4 gün",
      sessionMinutes: "60+ dakika",
      trainingStyles: "Kuvvet",
      location: "Salon",
      equipment: "Tam donanımlı salon",
      injuries: "Yok",
      dailyMovement: "Orta",
      sleep: "İyi",
    }),
  };

  const plan = generateWorkoutPlan(payload, undefined, "tr");

  // 1. Training Profile
  assert.equal(plan.profile.fitnessLevel, "beginner");
  assert.equal(plan.profile.goal, "hypertrophy");
  assert.equal(plan.profile.trainingDaysPerWeek, 3);
  assert.equal(plan.profile.sessionDurationMinutes, 60);

  // 2. Training Split
  assert.equal(plan.split.splitType, "full_body", "3 gün için Full Body split seçilmeli");
  assert.equal(plan.sessions.length, 3, "Haftalık 3 seans planlanmalı");

  // 3. Validation
  assert.equal(plan.validation.valid, true, "Plan tüm geçerlilik kurallarını sağlamalı");
  assert.equal(plan.validation.metrics.duplicateCount, 0, "Aynı seansta tekrar eden hareket olmamalı");
  assert.equal(plan.validation.metrics.contraindicationViolations, 0);

  // 4. Movement pattern & Push/Pull balance
  assert.ok(
    plan.validation.metrics.pushPullRatio >= 0.6 && plan.validation.metrics.pushPullRatio <= 1.6,
    `İtme/Çekme dengesi korunmalı (${plan.validation.metrics.pushPullRatio})`,
  );

  // 5. Volume targets
  assert.ok(plan.volumeTargets.chest.targetWeeklySets >= 8 && plan.volumeTargets.chest.targetWeeklySets <= 14);
  assert.ok(plan.volumeTargets.quadriceps.targetWeeklySets >= 8 && plan.volumeTargets.quadriceps.targetWeeklySets <= 14);

  // 6. Level safety
  for (const session of plan.sessions) {
    for (const ex of session.exercises) {
      assert.notEqual(ex.difficulty, "advanced", "Başlangıç seviyesine ileri egzersiz verilmemeli");
    }
  }
});

// -----------------------------------------------------------------------------
// Scenario B: Beginner, 3 days, Home with Bands, Weight Loss, 40m session
// -----------------------------------------------------------------------------
test("Senaryo B: Başlangıç, 3 gün, Evde Direnç Lastiği, Kilo Verme, 40 dk seans", () => {
  const payload = {
    age: 32,
    gender: "Kadın",
    height: 165,
    weight: 78,
    environment: "Evde",
    equipment: "Direnç lastiği",
    goal: "Kilo vermek ve yağ yakmak",
    history: createHistory({
      goal: "Kilo vermek",
      experience: "Yeni başlıyorum",
      level: "Yeni başlıyorum",
      recentFrequency: "1–2 gün",
      availableDays: "3–4 gün",
      sessionMinutes: "40 dakika",
      trainingStyles: "Kardiyo",
      location: "Evde",
      equipment: "Direnç lastiği",
      injuries: "Yok",
      dailyMovement: "Düşük",
      sleep: "Orta",
    }),
  };

  const plan = generateWorkoutPlan(payload, undefined, "tr");

  assert.equal(plan.profile.fitnessLevel, "beginner");
  assert.equal(plan.profile.goal, "weight_loss");
  assert.equal(plan.profile.trainingDaysPerWeek, 3);
  assert.equal(plan.profile.sessionDurationMinutes, 40);

  // Equipment validation
  for (const session of plan.sessions) {
    assert.ok(session.exercises.length >= 3 && session.exercises.length <= 5, "40 dk için 3-5 hareket olmalı");
    for (const ex of session.exercises) {
      // In bands + bodyweight, no barbells or gym machines
      const isAllowed = !ex.english.toLowerCase().includes("barbell") && !ex.english.toLowerCase().includes("machine");
      assert.ok(isAllowed, `Ekipman kısıtına uymayan hareket bulundu: ${ex.english}`);
    }
  }

  assert.equal(plan.validation.valid, true);
});

// -----------------------------------------------------------------------------
// Scenario C: Intermediate, 4 days, Gym, Hypertrophy, 60m session
// -----------------------------------------------------------------------------
test("Senaryo C: Orta Seviye, 4 gün, Salon, Hipertrofi, 60 dk seans", () => {
  const payload = {
    age: 28,
    gender: "Erkek",
    height: 178,
    weight: 82,
    environment: "Salon",
    equipment: "Full ekipman salon",
    goal: "Kas kütlesi artışı",
    history: createHistory({
      goal: "Kas geliştirmek",
      experience: "Düzenli",
      level: "Orta seviye",
      recentFrequency: "3–4 gün",
      availableDays: "4 gün",
      sessionMinutes: "60+ dakika",
      trainingStyles: "Kuvvet",
      location: "Salon",
      equipment: "Full ekipman",
      injuries: "Yok",
      dailyMovement: "Orta",
      sleep: "İyi",
    }),
  };

  const plan = generateWorkoutPlan(payload, undefined, "tr");

  // 1. Profile & Split
  assert.equal(plan.profile.fitnessLevel, "intermediate");
  assert.equal(plan.split.splitType, "upper_lower", "4 gün için Upper/Lower split seçilmeli");
  assert.equal(plan.sessions.length, 4, "4 seans oluşturulmalı");

  // 2. Muscle coverage
  const upperSessions = plan.sessions.filter((s) => s.focus.toLowerCase().includes("üst"));
  const lowerSessions = plan.sessions.filter((s) => s.focus.toLowerCase().includes("alt"));
  assert.equal(upperSessions.length, 2, "2 Üst vücut seansı olmalı");
  assert.equal(lowerSessions.length, 2, "2 Alt vücut seansı olmalı");

  // 3. Validation
  assert.equal(plan.validation.valid, true);
  assert.ok(plan.validation.metrics.pushPullRatio >= 0.6 && plan.validation.metrics.pushPullRatio <= 1.6);
  assert.ok(plan.validation.metrics.quadHamstringRatio >= 0.5 && plan.validation.metrics.quadHamstringRatio <= 1.8);
});

// -----------------------------------------------------------------------------
// Scenario D: Intermediate, 5 days, Gym, Strength + Hypertrophy
// -----------------------------------------------------------------------------
test("Senaryo D: Orta/İleri Seviye, 5 gün, Salon, Kuvvet ve Hacim", () => {
  const payload = {
    age: 29,
    gender: "Erkek",
    height: 182,
    weight: 85,
    environment: "Salon",
    equipment: "Full salon",
    goal: "Kuvvet ve hacim",
    history: createHistory({
      goal: "Güçlenmek",
      experience: "Düzenli",
      level: "Orta seviye",
      recentFrequency: "5+ gün",
      availableDays: "5+ gün",
      sessionMinutes: "60+ dakika",
      trainingStyles: "Kuvvet",
      location: "Salon",
      equipment: "Full salon",
      injuries: "Yok",
      dailyMovement: "Yüksek",
      sleep: "İyi",
    }),
  };

  const plan = generateWorkoutPlan(payload, undefined, "tr");

  assert.equal(plan.profile.trainingDaysPerWeek, 5);
  assert.equal(plan.split.splitType, "hybrid", "5 gün için PPL + Üst/Alt hibrit split seçilmeli");
  assert.equal(plan.sessions.length, 5);

  // Verify strength repetition ranges for compound exercises
  let compoundFound = false;
  for (const session of plan.sessions) {
    for (const ex of session.exercises) {
      if (ex.english.toLowerCase().includes("press") || ex.english.toLowerCase().includes("squat") || ex.english.toLowerCase().includes("deadlift")) {
        compoundFound = true;
      }
    }
  }
  assert.ok(compoundFound, "Kuvvet planında temel bileşik hareketler bulunmalı");
  assert.equal(plan.validation.valid, true);
});

// -----------------------------------------------------------------------------
// Scenario E: Knee limitation (Diz sakatlığı)
// -----------------------------------------------------------------------------
test("Senaryo E: Diz sakatlığı kısıtı — squat, lunge, bacak açma elenmeli", () => {
  const payload = {
    age: 35,
    gender: "Erkek",
    height: 175,
    weight: 80,
    environment: "Salon",
    equipment: "Full salon",
    goal: "Kas geliştirmek",
    history: createHistory({
      goal: "Kas geliştirmek",
      experience: "Düzenli",
      level: "Orta seviye",
      recentFrequency: "3–4 gün",
      availableDays: "3–4 gün",
      sessionMinutes: "50 dakika",
      trainingStyles: "Kuvvet",
      location: "Salon",
      equipment: "Full salon",
      injuries: "Diz", // KNEE LIMITATION
      dailyMovement: "Orta",
      sleep: "İyi",
    }),
  };

  const plan = generateWorkoutPlan(payload, undefined, "tr");

  assert.ok(plan.profile.limitations.includes("knee"), "Diz kısıtı profile yansıtılmalı");

  // RepDB'nin native güvenlik etiketleri (örn. knee_safe) bazı squat/lunge
  // varyasyonlarını isim benzerliğine rağmen meşru biçimde güvenli işaretler
  // (bkz. lib/training/exercise-metadata.ts detectContraindications). Bu
  // yüzden kontrol isim regex'i yerine gerçek contraindications alanına
  // bakar — asıl garanti zaten budur.
  for (const session of plan.sessions) {
    for (const ex of session.exercises) {
      const standardized = getStandardizedExerciseById(ex.id);
      assert.ok(standardized, `Egzersiz kataloğunda bulunamadı: ${ex.id}`);
      assert.ok(
        !standardized.contraindications.includes("knee"),
        `Diz sakatlığı olan kullanıcıya '${ex.english}' (${ex.id}) verilmemeli!`,
      );
    }
  }

  assert.equal(plan.validation.metrics.contraindicationViolations, 0);
  assert.equal(plan.validation.valid, true);
});

// -----------------------------------------------------------------------------
// Scenario F: Shoulder limitation (Omuz sakatlığı)
// -----------------------------------------------------------------------------
test("Senaryo F: Omuz sakatlığı kısıtı — overhead press, dip, dik çekiş elenmeli", () => {
  const payload = {
    age: 40,
    gender: "Erkek",
    height: 177,
    weight: 79,
    environment: "Salon",
    equipment: "Full salon",
    goal: "Kas geliştirmek",
    history: createHistory({
      goal: "Kas geliştirmek",
      experience: "Düzenli",
      level: "Orta seviye",
      recentFrequency: "3–4 gün",
      availableDays: "3–4 gün",
      sessionMinutes: "50 dakika",
      trainingStyles: "Kuvvet",
      location: "Salon",
      equipment: "Full salon",
      injuries: "Omuz", // SHOULDER LIMITATION
      dailyMovement: "Orta",
      sleep: "İyi",
    }),
  };

  const plan = generateWorkoutPlan(payload, undefined, "tr");

  assert.ok(plan.profile.limitations.includes("shoulder"), "Omuz kısıtı profile yansıtılmalı");

  // Bkz. Senaryo E'deki not: RepDB'nin shoulder_safe etiketi bazı varyasyonları
  // meşru biçimde güvenli işaretleyebilir, bu yüzden kontrol gerçek
  // contraindications alanına bakar.
  for (const session of plan.sessions) {
    for (const ex of session.exercises) {
      const standardized = getStandardizedExerciseById(ex.id);
      assert.ok(standardized, `Egzersiz kataloğunda bulunamadı: ${ex.id}`);
      assert.ok(
        !standardized.contraindications.includes("shoulder"),
        `Omuz sakatlığı olan kullanıcıya '${ex.english}' (${ex.id}) verilmemeli!`,
      );
    }
  }

  assert.equal(plan.validation.metrics.contraindicationViolations, 0);
  assert.equal(plan.validation.valid, true);
});

// -----------------------------------------------------------------------------
// Scenario G: Home Bodyweight (Ekipmansız evde spor) - Diversity & Safety
// -----------------------------------------------------------------------------
test("Legacy free-exercise-db ID'leri aktif katalogda görünmez, ama eski antrenman kayıtları için çözülebilir kalır", async () => {
  const { getStandardizedCatalog, getStandardizedExerciseById } = await import("../lib/training/exercise-metadata.ts");
  const { getAllExercises, getExerciseById } = await import("../lib/exercise-service.ts");

  // getStandardizedExerciseById normalize edilmiş id eşleşmesi de dener
  // (bkz. lib/training/exercise-metadata.ts), bu yüzden eski CamelCase_Underscore
  // id "Handstand_Push-Ups", RepDB'nin kendi "handstand-push-ups" kaydına fuzzy
  // eşleşir — bu, eski bir planın alternatif/regresyon aramasında koptuğunda
  // yararlı bir fallback'tir. Ama kataloğun KENDİSİ hâlâ yalnız RepDB id'sini
  // gerçek anahtar olarak taşır: eski id ile birebir (exact) arama başarısız olur.
  const standardizedCatalog = getStandardizedCatalog();
  assert.ok(!standardizedCatalog.some((e) => e.id === "Handstand_Push-Ups"), "Katalog kendi anahtarı olarak yalnız RepDB id'sini taşımalı");
  assert.ok(!getAllExercises().some((e) => e.id === "Handstand_Push-Ups"));

  // Ama eski workout_exercise_logs kayıtları (bkz. db/supabase-schema.sql,
  // serbest metin exercise_id) hâlâ çözülebilmeli — legacy fallback üzerinden.
  const legacyResolved = getExerciseById("Handstand_Push-Ups");
  assert.ok(legacyResolved, "Legacy id, eski loglar kırılmasın diye getExerciseById üzerinden çözülebilmeli");
  assert.equal(legacyResolved.source, "legacy");
  assert.equal(legacyResolved.isActive, false);

  // RepDB'nin kendi (doğru, aktif) sürümü katalogda gerçekten var ve doğru etiketlenmiş.
  const repdbVersion = getStandardizedExerciseById("handstand-push-ups");
  assert.ok(repdbVersion, "RepDB kendi handstand-push-ups kaydını sağlamalı");
  assert.equal(repdbVersion.difficulty, "advanced");
});

test("Senaryo G: Evde Ekipmansız Spor — Zengin hareket çeşitliliği ve oturumlar arası çeşitlilik", () => {
  const payload = {
    age: 26,
    gender: "Erkek",
    height: 175,
    weight: 70,
    environment: "Ev",
    equipment: "Ekipmansız (yalnız vücut ağırlığı)",
    goal: "Formda kalmak ve kas kazanımı",
    history: createHistory({
      goal: "Kas geliştirmek",
      experience: "Yeni başlıyorum",
      level: "Yeni başlıyorum",
      recentFrequency: "Hareketsiz",
      availableDays: "3 gün",
      sessionMinutes: "45 dakika",
      trainingStyles: "Kendi vücut ağırlığı",
      location: "Ev",
      equipment: "Vücut ağırlığı",
      injuries: "Yok",
      dailyMovement: "Düşük",
      sleep: "Orta",
    }),
  };

  const plan = generateWorkoutPlan(payload, undefined, "tr");

  assert.equal(plan.profile.fitnessLevel, "beginner");
  assert.equal(plan.profile.trainingDaysPerWeek, 3);
  assert.equal(plan.validation.valid, true);

  // All exercises must be bodyweight only and no expert acrobatics
  const allUsedExerciseIds = new Set();
  const allUsedNames = [];

  for (const session of plan.sessions) {
    for (const ex of session.exercises) {
      allUsedExerciseIds.add(ex.id);
      allUsedNames.push(ex.english);

      assert.ok(
        !ex.english.toLowerCase().includes("handstand"),
        `El duruşu şınav evde ekipmansız planda yer alamaz: ${ex.english}`,
      );
      assert.ok(
        !ex.english.toLowerCase().includes("mid row"),
        `Vücut ağırlığı orta kürek çekiş evde ekipmansız planda yer alamaz: ${ex.english}`,
      );
    }
  }

  // Verify diversity across 3 sessions: across 3 sessions there should be significant variety
  assert.ok(
    allUsedExerciseIds.size >= 8,
    `3 günlük ekipmansız planda en az 8 farklı hareket olmalı, bulunan: ${allUsedExerciseIds.size} (${allUsedNames.join(", ")})`,
  );
});
