import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import { CHALLENGE_TEMPLATES, TASK_LIMITS, fitFor, minutesRange, planFromTemplate, recommendTemplates, validatePlan } from "../lib/challenges/catalog.ts";
import { adaptTask, challengeState, nextTask, recoveryAllowance } from "../lib/challenges/engine.ts";
import { buildCoachChallenge, resolveCoachInputs } from "../lib/challenges/coach.ts";
import { DEFAULT_XP_RULES, challengeRewardXp, levelFor, mergeXpRules } from "../lib/challenges/xp-rules.ts";
import { getExerciseById } from "../lib/exercise-service.ts";
import { SESSION_KINDS, buildWellnessSession } from "../lib/training/wellness-session.ts";

const migration = readFileSync(new URL("../supabase/migrations/20261008120000_challenge_system.sql", import.meta.url), "utf8");

test("katalogdaki her plan geçerli, anahtarlar benzersiz ve iki dilde metin var", () => {
  const keys = new Set();
  for (const template of CHALLENGE_TEMPLATES) {
    assert.ok(!keys.has(template.key), template.key);
    keys.add(template.key);
    const plan = planFromTemplate(template.key);
    assert.ok(validatePlan(plan), template.key);
    assert.notEqual(plan.title.tr, plan.title.en, `${template.key}: başlık çevrilmemiş`);
    assert.notEqual(plan.description.tr, plan.description.en, `${template.key}: açıklama çevrilmemiş`);
    assert.ok(plan.days.length <= 30, "arkadaş challenge'ı için en fazla 30 gün");
  }
  assert.equal(planFromTemplate("yok"), null);
});

test("plan doğrulaması kolaylaştırılmış hedefleri reddeder", () => {
  const plan = planFromTemplate("steps_7");
  assert.equal(validatePlan({ ...plan, days: plan.days.map(() => ({ kind: "steps", target: TASK_LIMITS.steps.min - 1 })) }), false);
  assert.equal(validatePlan({ ...plan, days: plan.days.slice(0, 2) }), false, "en az 3 gün");
  assert.equal(validatePlan({ ...plan, days: [...plan.days, ...plan.days, ...plan.days, ...plan.days, ...plan.days] }), false, "en fazla 30 gün");
  assert.equal(validatePlan({ ...plan, days: plan.days.map(() => ({ kind: "session", session: "uydurma", minutes: 10 })) }), false);
  assert.equal(validatePlan({ ...plan, key: "DROP TABLE" }), false);
});

test("XP kuralları: TS varsayılanları migration'daki başlangıç değerleriyle aynı", () => {
  for (const [source, rule] of Object.entries(DEFAULT_XP_RULES)) {
    const match = new RegExp(`\\('${source}', (\\d+), (true|false)`).exec(migration);
    assert.ok(match, `${source} migration'da yok`);
    assert.equal(Number(match[1]), rule.amount, `${source} miktarı`);
    assert.equal(match[2] === "true", rule.dailyCapped, `${source} tavanı`);
    assert.ok(migration.includes(`'${source}'`), `${source} xp_events kaynak listesinde`);
  }
  assert.equal(mergeXpRules([{ source: "CHALLENGE_DAY_COMPLETED", amount: 40, daily_capped: false }, { source: "BILINMEYEN", amount: 5 }, { source: "CHECKIN_COMPLETED", amount: -1 }]).CHALLENGE_DAY_COMPLETED.amount, 40);
  assert.equal(mergeXpRules([{ source: "CHECKIN_COMPLETED", amount: -1 }]).CHECKIN_COMPLETED.amount, DEFAULT_XP_RULES.CHECKIN_COMPLETED.amount);
});

test("challenge ödülü: gün + seri + bitirme (+ arkadaş bonusu)", () => {
  assert.equal(challengeRewardXp(14), 14 * 25 + 30 + 2 * 100 + 250);
  assert.equal(challengeRewardXp(7, DEFAULT_XP_RULES, true), 7 * 25 + 30 + 100 + 250 + 100);
});

test("level eğrisi Android GamificationEngine.levelFor ile aynı", () => {
  assert.deepEqual(levelFor(0), { level: 1, currentXp: 0, nextLevelXp: 300 });
  assert.deepEqual(levelFor(299), { level: 1, currentXp: 299, nextLevelXp: 300 });
  assert.deepEqual(levelFor(300), { level: 2, currentXp: 0, nextLevelXp: 350 });
  assert.deepEqual(levelFor(650), { level: 3, currentXp: 0, nextLevelXp: 400 });
  assert.deepEqual(levelFor(-50), { level: 1, currentXp: 0, nextLevelXp: 300 });
});

test("durum: ilerleme tamamlanan güne bağlı, kaçırılan gün challenge'ı bozmaz yalnız seriyi sıfırlar", () => {
  const logs = [
    { dayIndex: 0, localDate: "2026-10-01", status: "completed" },
    { dayIndex: 1, localDate: "2026-10-02", status: "adapted" },
    { dayIndex: 2, localDate: "2026-10-03", status: "recovery" },
  ];
  const live = challengeState(14, logs, "2026-10-04");
  assert.equal(live.currentDay, 4);
  assert.equal(live.streak, 3, "toparlanma günü seriyi bozmaz");
  assert.equal(live.todayDone, false);
  assert.equal(live.recoveryUsed, 1);
  assert.equal(live.recoveryAllowance, 2);
  const missed = challengeState(14, logs, "2026-10-06");
  assert.equal(missed.streak, 0);
  assert.equal(missed.longestStreak, 3);
  assert.equal(missed.currentDay, 4, "gün kaçırmak ilerlemeyi geri almaz");
  const done = challengeState(3, logs, "2026-10-03");
  assert.equal(done.finished, true);
  assert.equal(done.todayDone, true);
  assert.equal(done.percent, 100);
  assert.equal(nextTask(planFromTemplate("core_14").days, done), null);
  assert.equal(recoveryAllowance(7), 1);
  assert.equal(recoveryAllowance(30), 4);
});

test("adaptasyon: düşük hazırlıkta süre kısalır, gün tam sayılır; ağrıda nazik oturuma geçilir", () => {
  const task = { kind: "session", session: "core_focus", minutes: 15 };
  const tired = adaptTask(task, { day: "2026-10-07", energy: 3, sleepQuality: 4, sleepHours: 5, soreness: 8, pain: 0, availableMinutes: null }, { locale: "tr" });
  assert.equal(tired.adapted, true);
  assert.equal(tired.task.minutes, 8);
  assert.equal(tired.completionStatus, "adapted");
  assert.match(tired.message, /8 dakikaya uyarladım/);
  const pain = adaptTask(task, { day: "2026-10-07", energy: 7, sleepQuality: 7, sleepHours: 7, soreness: 2, pain: 7, availableMinutes: null }, { locale: "en" });
  assert.equal(pain.task.session, "posture_mobility");
  assert.match(pain.message, /adapted today's workout/);
  const fine = adaptTask(task, { day: "2026-10-07", energy: 8, sleepQuality: 8, sleepHours: 8, soreness: 1, pain: 0, availableMinutes: null });
  assert.equal(fine.adapted, false);
  assert.equal(adaptTask(task, null).adapted, false);
  assert.equal(adaptTask(task, { day: "x", energy: 2, sleepQuality: 2, sleepHours: 3, soreness: 9, pain: 0, availableMinutes: null }, { enabled: false }).adapted, false, "kullanıcı uyarlamayı kapattıysa");
  const water = adaptTask({ kind: "water", target: 2000 }, { day: "x", energy: 1, sleepQuality: 1, sleepHours: 1, soreness: 9, pain: 9, availableMinutes: null });
  assert.equal(water.adapted, false, "su/öğün görevleri değişmez");
  const steps = adaptTask({ kind: "steps", target: 8000 }, { day: "x", energy: 3, sleepQuality: 5, sleepHours: 6, soreness: 2, pain: 0, availableMinutes: null });
  assert.equal(steps.task.target, 5000);
  const cycleOnly = adaptTask(task, null, { periodLikely: true });
  assert.equal(cycleOnly.completionStatus, "completed", "döngü uyarlaması sağlık verisiyle doğrulanmaz, normal gün sayılır");
  assert.equal(adaptTask({ kind: "steps", target: 8000 }, null, { periodLikely: true }).adapted, false);
});

test("Fit Koç: bilinen veriyle tamamlar, kullanıcının seçimi önceliklidir, plan geçerli ve deterministik", () => {
  const known = { goal: "weight_loss", fitnessLevel: "beginner", equipmentText: "Evde, ekipman yok", limitations: [], sessionDurationMinutes: 45 };
  const auto = resolveCoachInputs({}, known);
  assert.equal(auto.focus, "steps");
  assert.equal(auto.level, "beginner");
  const band = buildCoachChallenge({ focus: "core", equipment: "band", days: 14, minutes: 15 }, known);
  assert.ok(validatePlan(band.plan));
  assert.equal(band.plan.title.tr, "14 Gün Band & Core");
  assert.equal(band.plan.title.en, "14-Day Band & Core");
  assert.equal(band.plan.days.length, 14);
  assert.deepEqual(buildCoachChallenge({ focus: "core", equipment: "band", days: 14, minutes: 15 }, known).plan, band.plan);
  const knee = buildCoachChallenge({ focus: "full_body" }, { ...known, limitations: ["knee"] });
  assert.ok(knee.plan.days.every((task) => task.session !== "home_strength"), "diz kısıtında zıplamasız/düşük etkili");
  const tired = buildCoachChallenge({ focus: "core", minutes: 20 }, { ...known, averageEnergy: 4 });
  assert.ok(tired.plan.days[0].minutes < 15, "düşük enerjide yumuşak başlangıç");
  const range = minutesRange(band.plan);
  assert.ok(range[0] >= 8 && range[1] <= 15);
});

test("öneri ve uygunluk", () => {
  assert.equal(fitFor({ difficulty: "intermediate", equipment: "none" }, { fitnessLevel: "beginner" }), "stretch");
  assert.equal(fitFor({ difficulty: "beginner", equipment: "band" }, { fitnessLevel: "beginner", equipmentText: "yok" }), "needs_band");
  assert.equal(fitFor({ difficulty: "beginner", equipment: "band" }, { fitnessLevel: "beginner", equipmentText: "Direnç bandı" }), "fit");
  const picks = recommendTemplates({ goal: "weight_loss", fitnessLevel: "beginner" });
  assert.equal(picks.length, 4);
  assert.ok(picks.includes("steps_7"));
  assert.ok(!picks.includes("band_14"), "bandı yoksa bant challenge'ı önerilmez");
});

test("challenge oturumları mevcut üreticiyle, katalogdaki hareketlerle üretilir", () => {
  for (const kind of SESSION_KINDS) for (const tier of ["free", "pro"]) {
    const session = buildWellnessSession({ kind, minutes: 15, tier, seed: `u:${kind}` });
    assert.ok(session.exercises.length >= 4, `${kind}/${tier}: ${session.exercises.length}`);
    assert.equal(new Set(session.exercises.map((item) => item.id)).size, session.exercises.length);
    for (const item of session.exercises) assert.ok(getExerciseById(item.id), item.id);
  }
  const band = buildWellnessSession({ kind: "band_strength", minutes: 20, tier: "pro", seed: "b" });
  assert.ok(band.exercises.filter((item) => /band/i.test(getExerciseById(item.id).equipment ?? "")).length >= 3, "bant oturumu bant hareketleri içerir");
  const core = buildWellnessSession({ kind: "core_focus", minutes: 15, tier: "free", seed: "c" });
  assert.ok(core.exercises.filter((item) => getExerciseById(item.id).bodyPart === "core").length >= 2);
});

test("migration: güvenlik ve gizlilik değişmezleri", () => {
  assert.match(migration, /user_challenges_one_active on public\.user_challenges \(user_id, template_key\) where status = 'active'/);
  assert.match(migration, /unique \(user_challenge_id, local_date\)/);
  assert.match(migration, /on conflict \(user_id, source, source_id\) do nothing/);
  // Görev doğrulaması gerçek kayıtlardan: antrenman, adım, su, öğün, check-in.
  for (const table of ["workout_sessions", "daily_steps", "water_logs", "food_entries", "daily_checkins"]) assert.ok(migration.includes(`from public.${table}`), table);
  // Arkadaş profili sağlık verisi döndürmez.
  const friendProfile = migration.slice(migration.indexOf("create or replace function public.hedefit_friend_profile"), migration.indexOf("create or replace function public.hedefit_set_share_progress"));
  for (const forbidden of ["weight", "calorie", "cycle", "sleep", "food_entries", "daily_checkins", "body_measurements"]) assert.ok(!friendProfile.includes(forbidden), `arkadaş profili ${forbidden} içermemeli`);
  // XP silen bir ifade yok.
  assert.ok(!/delete from public\.xp_events/i.test(migration));
});
