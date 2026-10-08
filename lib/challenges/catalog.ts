// Challenge kataloğu. Her challenge, gün gün görev listesi olan bir PLANDIR; görevler mevcut sistemlere
// referans verir (oturum = lib/training/wellness-session.ts, antrenman = kullanıcının aktif programı,
// adım/su/öğün = mevcut kayıtlar). Antrenman verisi kopyalanmaz.
//
// Katılımda plan `user_challenges.plan` sütununa SNAPSHOT olarak yazılır: katalog güncellenirse devam eden
// challenge'lar etkilenmez (yeni katılımlar yeni sürümü alır).

import type { SessionKind } from "../training/wellness-session.ts";

export type L10n = { tr: string; en: string };
export type ChallengeCategory = "workout" | "nutrition" | "steps" | "pilates" | "flexibility" | "coach";
export type Difficulty = "beginner" | "intermediate" | "advanced";
export type Equipment = "none" | "mat" | "band" | "program";
export type TaskKind = "session" | "workout" | "steps" | "water" | "meals" | "checkin";

export interface ChallengeTask {
  kind: TaskKind;
  /** kind = "session": hangi oturum türü üretilecek. */
  session?: SessionKind;
  /** Oturum/antrenman için hedef dakika. */
  minutes?: number;
  /** steps: adım, water: ml, meals: öğün sayısı. */
  target?: number;
}

export interface ChallengePlan {
  key: string;
  version: number;
  source: "catalog" | "coach";
  category: ChallengeCategory;
  difficulty: Difficulty;
  equipment: Equipment;
  title: L10n;
  description: L10n;
  days: ChallengeTask[];
}

export const CATALOG_VERSION = 1;
export const MAX_ACTIVE_CHALLENGES = 3;
export const MIN_DAYS = 3;
export const MAX_DAYS = 30;

/** Görev hedeflerinin alt sınırları: sunucu (SQL) da aynı sınırları uygular, kolaylaştırılmış plan kabul edilmez. */
export const TASK_LIMITS = {
  minutes: { min: 5, max: 60 },
  steps: { min: 3_000, max: 30_000 },
  water: { min: 1_000, max: 5_000 },
  meals: { min: 2, max: 6 },
} as const;

const ramp = (days: number, from: number, to: number) =>
  Array.from({ length: days }, (_, index) => Math.round(from + ((to - from) * index) / Math.max(1, days - 1)));

const session = (kind: SessionKind, minutes: number): ChallengeTask => ({ kind: "session", session: kind, minutes });

type TemplateSpec = Omit<ChallengePlan, "version" | "source"> & { popular?: boolean };

function spec(input: TemplateSpec): TemplateSpec { return input; }

export const CHALLENGE_TEMPLATES: TemplateSpec[] = [
  spec({
    key: "core_14", category: "workout", difficulty: "beginner", equipment: "none", popular: true,
    title: { tr: "14 Gün Core Challenge", en: "14-Day Core Challenge" },
    description: { tr: "Her gün kısa, kontrollü core seansları. Duruşun ve gövde gücün iki haftada fark edilir.", en: "Short, controlled core sessions every day. Feel your posture and trunk strength change in two weeks." },
    days: ramp(14, 8, 15).map((minutes) => session("core_focus", minutes)),
  }),
  spec({
    key: "pilates_7", category: "pilates", difficulty: "beginner", equipment: "mat",
    title: { tr: "7 Gün Pilates Başlangıç", en: "7-Day Pilates Starter" },
    description: { tr: "Pilates'e nazik bir giriş: nefes, kontrol ve core aktivasyonu.", en: "A gentle introduction to Pilates: breath, control and core activation." },
    days: ramp(7, 10, 14).map((minutes) => session("pilates_today", minutes)),
  }),
  spec({
    key: "pilates_21", category: "pilates", difficulty: "intermediate", equipment: "mat", popular: true,
    title: { tr: "21 Gün Pilates", en: "21-Day Pilates" },
    description: { tr: "Üç haftalık düzenli Pilates: daha güçlü core, daha esnek kalça ve daha dik duruş.", en: "Three weeks of regular Pilates: a stronger core, looser hips and a taller posture." },
    days: ramp(21, 12, 22).map((minutes, index) => (index % 7 === 6 ? session("posture_mobility", 10) : session("pilates_today", minutes))),
  }),
  spec({
    key: "flex_10", category: "flexibility", difficulty: "beginner", equipment: "none",
    title: { tr: "10 Gün Esneklik", en: "10-Day Flexibility" },
    description: { tr: "Günde birkaç dakika kontrollü esneme. Masa başı tutukluğuna iyi gelir.", en: "A few minutes of controlled stretching a day. Great for desk-bound stiffness." },
    days: ramp(10, 8, 12).map((minutes) => session("flexibility", minutes)),
  }),
  spec({
    key: "mobility_14", category: "flexibility", difficulty: "beginner", equipment: "none",
    title: { tr: "14 Gün Duruş & Mobilite", en: "14-Day Posture & Mobility" },
    description: { tr: "Sırt, omuz ve kalçayı açan kısa mobilite akışları.", en: "Short mobility flows that open up your back, shoulders and hips." },
    days: ramp(14, 10, 15).map((minutes, index) => session(index % 2 === 0 ? "posture_mobility" : "flexibility", minutes)),
  }),
  spec({
    key: "band_14", category: "workout", difficulty: "intermediate", equipment: "band",
    title: { tr: "14 Gün Direnç Bandı", en: "14-Day Resistance Band" },
    description: { tr: "Sadece bir bantla tüm vücut güç ve core. Evde, seyahatte, her yerde.", en: "Full-body strength and core with just a band. At home, travelling, anywhere." },
    days: ramp(14, 15, 20).map((minutes, index) => (index % 3 === 2 ? session("core_focus", 10) : session("band_strength", minutes))),
  }),
  spec({
    key: "home_21", category: "workout", difficulty: "intermediate", equipment: "none", popular: true,
    title: { tr: "21 Gün Evde Güç", en: "21-Day Home Strength" },
    description: { tr: "Ekipmansız, kademeli artan tüm vücut antrenmanları. Her dördüncü gün aktif toparlanma.", en: "Progressive full-body workouts with no equipment. Active recovery every fourth day." },
    days: ramp(21, 15, 25).map((minutes, index) => (index % 4 === 3 ? session("posture_mobility", 10) : session("home_strength", minutes))),
  }),
  spec({
    key: "program_14", category: "workout", difficulty: "beginner", equipment: "program",
    title: { tr: "14 Gün Antrenman Ritmi", en: "14-Day Training Rhythm" },
    description: { tr: "Kendi programınla gün aşırı antrenman, aradaki günlerde kısa mobilite. Ritim oturtmak için ideal.", en: "Train with your own program every other day, short mobility in between. Ideal for building a rhythm." },
    days: Array.from({ length: 14 }, (_, index): ChallengeTask => (index % 2 === 0 ? { kind: "workout", minutes: 40 } : session("posture_mobility", 10))),
  }),
  spec({
    key: "steps_7", category: "steps", difficulty: "beginner", equipment: "none", popular: true,
    title: { tr: "7 Gün Adım Challenge", en: "7-Day Step Challenge" },
    description: { tr: "Bir hafta boyunca her gün 8.000 adım. Arkadaşınla yarışmak için harika.", en: "8,000 steps every day for a week. Great to race a friend." },
    days: Array.from({ length: 7 }, (): ChallengeTask => ({ kind: "steps", target: 8_000 })),
  }),
  spec({
    key: "steps_30", category: "steps", difficulty: "intermediate", equipment: "none",
    title: { tr: "30 Gün 10K Adım", en: "30-Day 10K Steps" },
    description: { tr: "Bir ay boyunca günde 10.000 adım: en sade ve en etkili alışkanlık.", en: "10,000 steps a day for a month: the simplest, most effective habit." },
    days: Array.from({ length: 30 }, (): ChallengeTask => ({ kind: "steps", target: 10_000 })),
  }),
  spec({
    key: "water_7", category: "nutrition", difficulty: "beginner", equipment: "none",
    title: { tr: "7 Gün Su Challenge", en: "7-Day Hydration Challenge" },
    description: { tr: "Her gün en az 2 litre su. Küçük alışkanlık, büyük fark.", en: "At least 2 litres of water every day. Small habit, big difference." },
    days: Array.from({ length: 7 }, (): ChallengeTask => ({ kind: "water", target: 2_000 })),
  }),
  spec({
    key: "meals_14", category: "nutrition", difficulty: "beginner", equipment: "none",
    title: { tr: "14 Gün Öğün Takibi", en: "14-Day Meal Tracking" },
    description: { tr: "İki hafta boyunca her gün en az 3 öğününü kaydet. Ne yediğini görmek, hedefin yarısıdır.", en: "Log at least 3 meals every day for two weeks. Seeing what you eat is half the goal." },
    days: Array.from({ length: 14 }, (): ChallengeTask => ({ kind: "meals", target: 3 })),
  }),
];

const BY_KEY = new Map(CHALLENGE_TEMPLATES.map((template) => [template.key, template]));

export function templateByKey(key: string): TemplateSpec | null {
  return BY_KEY.get(key) ?? null;
}

/** Katalog şablonundan katılım anında saklanacak plan snapshot'ını üretir. */
export function planFromTemplate(key: string): ChallengePlan | null {
  const template = templateByKey(key);
  if (!template) return null;
  return {
    key: template.key, category: template.category, difficulty: template.difficulty, equipment: template.equipment,
    title: template.title, description: template.description, days: template.days.map((task) => ({ ...task })),
    version: CATALOG_VERSION, source: "catalog",
  };
}

export function minutesRange(plan: Pick<ChallengePlan, "days">): [number, number] | null {
  const minutes = plan.days.map((task) => task.minutes).filter((value): value is number => typeof value === "number");
  if (!minutes.length) return null;
  return [Math.min(...minutes), Math.max(...minutes)];
}

const SESSION_SET = new Set<SessionKind>(["pilates_today", "low_impact_recovery", "posture_mobility", "core_focus", "band_strength", "flexibility", "home_strength"]);
const CATEGORIES = new Set<ChallengeCategory>(["workout", "nutrition", "steps", "pilates", "flexibility", "coach"]);
const DIFFICULTIES = new Set<Difficulty>(["beginner", "intermediate", "advanced"]);
const EQUIPMENT = new Set<Equipment>(["none", "mat", "band", "program"]);

const inRange = (value: unknown, range: { min: number; max: number }) => typeof value === "number" && Number.isInteger(value) && value >= range.min && value <= range.max;

/** Planın yapısal doğrulaması. Sunucudaki SQL doğrulamasıyla aynı kurallar; kolaylaştırılmış hedefler reddedilir. */
export function validatePlan(plan: unknown): plan is ChallengePlan {
  if (!plan || typeof plan !== "object") return false;
  const value = plan as Partial<ChallengePlan>;
  if (typeof value.key !== "string" || !/^[a-z0-9_:.-]{2,80}$/.test(value.key)) return false;
  if (!CATEGORIES.has(value.category as ChallengeCategory) || !DIFFICULTIES.has(value.difficulty as Difficulty) || !EQUIPMENT.has(value.equipment as Equipment)) return false;
  for (const text of [value.title, value.description]) {
    if (!text || typeof text.tr !== "string" || typeof text.en !== "string" || !text.tr.trim() || !text.en.trim() || text.tr.length > 240 || text.en.length > 240) return false;
  }
  if (!Array.isArray(value.days) || value.days.length < MIN_DAYS || value.days.length > MAX_DAYS) return false;
  return value.days.every((task) => {
    if (!task || typeof task !== "object") return false;
    switch (task.kind) {
      case "session": return SESSION_SET.has(task.session as SessionKind) && inRange(task.minutes, TASK_LIMITS.minutes);
      case "workout": return task.minutes === undefined || inRange(task.minutes, { min: 10, max: 120 });
      case "steps": return inRange(task.target, TASK_LIMITS.steps);
      case "water": return inRange(task.target, TASK_LIMITS.water);
      case "meals": return inRange(task.target, TASK_LIMITS.meals);
      case "checkin": return true;
      default: return false;
    }
  });
}

/** Mevcut profile göre uygunluk: "fit" (seviyene uygun), "stretch" (zorlayıcı) ya da eksik ekipman. */
export type Fit = "fit" | "stretch" | "needs_band";

const LEVEL_RANK: Record<Difficulty, number> = { beginner: 0, intermediate: 1, advanced: 2 };

export function fitFor(plan: Pick<ChallengePlan, "difficulty" | "equipment">, profile: { fitnessLevel?: string; equipmentText?: string }): Fit {
  const level = LEVEL_RANK[(profile.fitnessLevel as Difficulty) ?? "beginner"] ?? 0;
  if (plan.equipment === "band" && !/band|bant|lastik|salon|gym|full/i.test(profile.equipmentText ?? "")) return "needs_band";
  return LEVEL_RANK[plan.difficulty] > level ? "stretch" : "fit";
}

/** "Sana Özel" önerisi: hedef, seviye, ekipman ve hareketlilik profiline göre puanlanmış katalog anahtarları. */
export function recommendTemplates(profile: {
  goal?: string;
  fitnessLevel?: string;
  equipmentText?: string;
  priorityMuscles?: string[];
  mobilityLevel?: string;
  limitations?: string[];
  wellnessProminent?: boolean;
}, limit = 4): string[] {
  const scored = CHALLENGE_TEMPLATES.map((template) => {
    let score = template.popular ? 1 : 0;
    const fit = fitFor(template, profile);
    if (fit === "fit") score += 3;
    if (fit === "needs_band") score -= 4;
    if (profile.goal === "weight_loss" && (template.category === "steps" || template.key === "home_21")) score += 3;
    if ((profile.goal === "hypertrophy" || profile.goal === "strength") && template.key === "program_14") score += 3;
    if (profile.priorityMuscles?.includes("core") && template.key === "core_14") score += 3;
    if (profile.mobilityLevel === "low" && template.category === "flexibility") score += 2;
    if ((profile.limitations?.length ?? 0) > 0 && (template.category === "pilates" || template.category === "flexibility")) score += 2;
    if (profile.wellnessProminent && template.category === "pilates") score += 2;
    return { key: template.key, score };
  });
  return scored.sort((a, b) => b.score - a.score || a.key.localeCompare(b.key)).slice(0, limit).map((entry) => entry.key);
}
