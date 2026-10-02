// Builds the approved "core exercise pool" that plan generation will draw from.
//
// The 601-move atlas (data/exercises.json) stays the reference for search,
// manual logging and history. The core pool is a curated subset organised as
// SLOTS (e.g. "vertical pull") with several interchangeable alternatives each,
// so a plan can rotate moves inside a slot without ever leaving vetted ones.
//
// Curation lives in SLOTS below: names are listed in preference order (the
// first two of every slot are its "staples"). The script resolves each name to
// an id, checks it, and writes a review file plus coverage warnings.
//
// Usage: node scripts/build-core-exercises.mjs

import { readFile, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import { translateExerciseName } from "../lib/exercise-translations.ts";
import { catalogExerciseNamesTr } from "../lib/exercise-names-tr.ts";

const root = fileURLToPath(new URL("..", import.meta.url));
const atlas = [
  ...JSON.parse(await readFile(join(root, "data", "exercises.json"), "utf8")),
  ...JSON.parse(await readFile(join(root, "data", "exercises-supplement.json"), "utf8")),
];
const byName = new Map(atlas.map((exercise) => [exercise.name, exercise]));

// role: main = progressive-overload lifts that stay stable across blocks;
//       accessory = rotates every block; core / conditioning rotate freely.
const SLOTS = [
  { key: "chest_press", label: "Göğüs · yatay itiş", role: "main", names: ["Barbell Bench Press", "Dumbbell Bench Press", "Machine Chest Press", "Smith Machine Bench Press", "Cable Chest Press", "Push-Up", "Knee Push Ups", "Dumbbell Floor Press", "Band Chest Press", "Wide Grip Push Ups"] },
  { key: "chest_incline", label: "Üst göğüs · eğimli itiş", role: "accessory", names: ["Incline Dumbbell Press", "Incline Barbell Bench Press", "Smith Machine Incline Bench Press", "Incline Push-Up", "Decline Push-Up"] },
  { key: "chest_fly", label: "Göğüs · açış (izolasyon)", role: "accessory", names: ["Dumbbell Fly", "Cable Fly", "Machine Chest Fly", "Pec Deck", "Band Chest Fly", "Dumbbell Floor Fly", "Single Dumbbell Svend Press"] },
  { key: "vertical_press", label: "Omuz · dikey itiş", role: "main", names: ["Dumbbell Shoulder Press", "Barbell Overhead Press", "Seated Dumbbell Shoulder Press", "Machine Shoulder Press", "Smith Machine Shoulder Press", "Arnold Press", "Pike Push Ups", "Band Shoulder Press", "Dumbbell Push Press"] },
  { key: "lateral_raise", label: "Omuz · yan kaldırış", role: "accessory", names: ["Dumbbell Lateral Raise", "Cable Lateral Raise", "Seated Dumbbell Lateral Raise", "Plate-Loaded Lateral Raise", "Band Lateral Raise"] },
  { key: "rear_delt", label: "Arka omuz / üst sırt", role: "accessory", names: ["Cable Face Pull", "Rear Delt Fly", "Dumbbell Reverse Fly", "Band Pull Apart", "TRX Face Pull", "Band Rear Delt Fly", "Band Face Pull", "Dumbbell Face Pull"] },
  { key: "traps", label: "Trapez", role: "accessory", names: ["Dumbbell Shrug", "Barbell Shrug", "Kettlebell Shrug", "Smith Machine Shrug"] },
  { key: "triceps_compound", label: "Arka kol · bileşik", role: "accessory", names: ["Close-Grip Dumbbell Bench Press", "Close-Grip Bench Press", "Bench Dips", "Diamond Push Ups", "Machine Assisted Dips", "Chest Dips", "Close Grip Push Ups"] },
  { key: "triceps_isolation", label: "Arka kol · izolasyon", role: "accessory", names: ["Cable Tricep Pushdown", "Overhead Tricep Extension", "V-Bar Tricep Pushdown", "Dumbbell Skull Crusher", "Dumbbell Tricep Kickback", "Machine Triceps Extension", "Single Arm Tricep Pushdown", "Band Triceps Pushdown", "Band Overhead Triceps Extension", "Dumbbell Tricep Extension", "Single-Arm Dumbbell Overhead Tricep Extension"] },
  { key: "vertical_pull", label: "Sırt · dikey çekiş", role: "main", names: ["Lat Pulldown", "Pull-Up", "V-Bar Lat Pulldown", "Close Grip Lat Pulldown", "Chin-Ups", "Neutral Grip Pull Ups", "Assisted Pull Ups", "Band Assisted Pull Ups", "Straight-Arm Pulldown", "Band Lat Pulldown", "Band Straight-Arm Pulldown"] },
  { key: "horizontal_pull", label: "Sırt · yatay çekiş", role: "main", names: ["Seated Cable Row", "Bent-Over Dumbbell Row", "Single-Arm Dumbbell Row", "Bent-Over Barbell Row", "Chest-Supported Dumbbell Row", "T-Bar Row", "Chest-Supported Smith Machine Row", "Kneeling Cable Row", "TRX Row", "Inverted Row", "One Arm Kettlebell Row", "Band Seated Row", "Band Bent-Over Row", "Band Single-Arm Row"] },
  { key: "squat", label: "Bacak · squat", role: "main", names: ["Barbell Back Squat", "Goblet Squat", "Dumbbell Squat", "Front Squat", "Leg Press", "Hack Squat", "Bodyweight Squat", "TRX Squat", "Dumbbell Front Squat", "Dumbbell Sumo Squat", "Banded Squat"] },
  { key: "quad_isolation", label: "Ön bacak · izolasyon", role: "accessory", names: ["Leg Extension", "Single Leg Extension", "Banded Terminal Knee Extension", "Reverse Nordic Curl", "Wall Sit"] },
  { key: "hinge", label: "Arka bacak · kalça menteşesi", role: "main", names: ["Romanian Deadlift", "Dumbbell Romanian Deadlift", "Barbell Deadlift", "Dumbbell Deadlift", "Kettlebell Deadlift", "Smith Machine Romanian Deadlift", "Single Leg Romanian Deadlift", "Banded Romanian Deadlift", "Band Pull-Through", "Dumbbell Kickstand Deadlift"] },
  { key: "hamstring_curl", label: "Arka bacak · curl", role: "accessory", names: ["Lying Leg Curl", "Seated Leg Curl", "Stability Ball Leg Curl", "TRX Hamstring Curl", "Banded Standing Leg Curl", "Band Seated Hamstring Curl"] },
  { key: "glute_bridge", label: "Kalça · köprü / thrust", role: "main", names: ["Barbell Hip Thrust", "Glute Bridge", "Dumbbell Hip Thrust", "Single Leg Glute Bridge", "Smith Machine Hip Thrust", "Banded Hip Thrust"] },
  { key: "glute_abduction", label: "Kalça · dış/iç bacak", role: "accessory", names: ["Cable Glute Kickback", "Machine Hip Abduction", "Banded Lateral Walk", "Side-Lying Hip Abduction", "Hip Adduction", "Glute Kickback", "Clamshells"] },
  { key: "lunge", label: "Bacak · tek bacak / lunge", role: "accessory", names: ["Dumbbell Lunge", "Walking Lunge", "Reverse Lunge", "Bulgarian Split Squat", "Bodyweight Reverse Lunge", "Step Ups", "Dumbbell Split Squat"] },
  { key: "calves", label: "Baldır", role: "accessory", names: ["Standing Calf Raise", "Seated Calf Raise", "Bodyweight Calf Raise", "Single Leg Calf Raise", "Dumbbell Calf Raise", "Band Calf Raise"] },
  { key: "biceps_curl", label: "Ön kol · biceps curl", role: "accessory", names: ["Dumbbell Bicep Curl", "Barbell Curl", "EZ-Bar Curl", "Cable Curl", "Machine Bicep Curl", "Preacher Curl", "Incline Dumbbell Curl", "Concentration Curl", "Band Biceps Curl", "Seated Dumbbell Curl", "Zottman Curl"] },
  { key: "hammer_brachialis", label: "Ön kol · hammer / brachialis", role: "accessory", names: ["Dumbbell Hammer Curl", "Cable Hammer Curl", "Cross Body Hammer Curl", "EZ-Bar Reverse Curl", "Band Hammer Curl", "Single-Arm Hammer Curl"] },
  { key: "forearm_grip", label: "Önkol / kavrama", role: "accessory", names: ["Dumbbell Wrist Curl", "Dead Hang", "Dumbbell Reverse Wrist Curl"] },
  { key: "core_stability", label: "Core · stabilite", role: "core", names: ["Plank", "Dead Bug", "Bird-Dog", "High Plank", "Hollow Body Hold", "Ab Wheel Rollout", "TRX Plank"] },
  { key: "core_flexion", label: "Core · karın bükme", role: "core", names: ["Crunches", "Cable Crunch", "Reverse Crunches", "Machine Seated Crunch", "Bicycle Crunch"] },
  { key: "leg_raise", label: "Core · bacak kaldırma", role: "core", names: ["Hanging Knee Raise", "Lying Leg Raise", "Captain's Chair Knee Raise", "Hanging Leg Raise", "Flutter Kicks"] },
  { key: "obliques", label: "Core · yan / rotasyon", role: "core", names: ["Side Plank", "Cable Pallof Press", "Russian Twist", "Dumbbell Side Bend", "Cross-Body Crunch", "Band Pallof Press"] },
  { key: "lower_back", label: "Alt sırt", role: "core", names: ["Back Extension", "Superman", "Reverse Plank", "Machine Back Extension"] },
  { key: "carry", label: "Taşıma / kavrama", role: "conditioning", names: ["Dumbbell Farmer's Walk", "Kettlebell Farmer's Walk", "Suitcase Carry"] },
  { key: "conditioning", label: "Kondisyon / tam vücut", role: "conditioning", names: ["Kettlebell Swing", "Burpees", "Mountain Climbers", "Jump Rope", "Medicine Ball Slam", "Battle Ropes", "Jumping Jacks"] },
];

// Warm-up (dynamic) and cool-down (static) moves, kept out of strength slots.
const WARMUP = ["Cat-Cow", "Bird-Dog", "Jumping Jacks", "High Knees", "Downward Dog to Plank", "Thread the Needle", "Half-Kneeling Hip Flexor Rock"];
const COOLDOWN = ["Child's Pose", "Downward-Facing Dog", "Kneeling Hip Flexor Stretch", "Pigeon Stretch", "Standing Calf Stretch", "Standing Quad Stretch", "Doorway Chest Stretch", "Cross-Body Shoulder Stretch", "Overhead Triceps Stretch", "Bench Hamstring Stretch", "Supine Spinal Twist", "Knee-to-Chest Stretch", "Neck Side Stretch"];

// Gym loanwords (Goblet Squat, Lat Pulldown…) are deliberately kept as-is in the
// name table; only names with NO table entry and no rule-based change are missing.
const hasTurkishName = (exercise) => exercise.name in catalogExerciseNamesTr || translateExerciseName(exercise.name, "tr") !== exercise.name;

const problems = [];
const warnings = [];
const seen = new Map();

function entry(name, slotKey, index) {
  const exercise = byName.get(name);
  if (!exercise) { problems.push(`${slotKey}: "${name}" atlasta yok`); return null; }
  if (exercise.isActive === false) problems.push(`${slotKey}: "${name}" aktif değil`);
  if (seen.has(exercise.id)) problems.push(`${slotKey}: "${name}" zaten ${seen.get(exercise.id)} slotunda`);
  seen.set(exercise.id, slotKey);
  const nameTr = translateExerciseName(exercise.name, "tr");
  if (!hasTurkishName(exercise)) warnings.push(`Türkçe adı yok: ${exercise.name}`);
  if (exercise.mediaStatus !== "complete") warnings.push(`Görsel eksik: ${exercise.name}`);
  return {
    id: exercise.id,
    name: exercise.name,
    nameTr,
    level: exercise.level,
    equipment: exercise.equipment || "bodyweight",
    environment: exercise.environment ?? [],
    category: exercise.category,
    tier: index < 2 ? "staple" : "variation",
  };
}

const slots = SLOTS.map((slot) => ({
  key: slot.key,
  label: slot.label,
  role: slot.role,
  exercises: slot.names.map((name, index) => entry(name, slot.key, index)).filter(Boolean),
}));

for (const slot of slots) {
  const count = (predicate) => slot.exercises.filter(predicate).length;
  const home = count((e) => e.environment.includes("home"));
  const beginner = count((e) => e.level === "beginner");
  if (slot.exercises.length < 3) warnings.push(`${slot.key}: yalnız ${slot.exercises.length} alternatif, rotasyon için az`);
  if (home < 2 && slot.role !== "conditioning") warnings.push(`${slot.key}: evde yapılabilen ${home} hareket (ev kullanıcısı için az)`);
  if (beginner < 2) warnings.push(`${slot.key}: başlangıç seviyesinde ${beginner} hareket`);
  const staples = slot.exercises.slice(0, 2);
  if (staples.some((e) => e.level === "advanced")) warnings.push(`${slot.key}: staple hareketlerden biri ileri seviye`);
  slot.stats = { total: slot.exercises.length, home, gymOnly: slot.exercises.length - home, beginner, intermediate: count((e) => e.level === "intermediate") };
}

const mobility = (names, kind) => names.map((name) => {
  const exercise = byName.get(name);
  if (!exercise) { problems.push(`${kind}: "${name}" atlasta yok`); return null; }
  const nameTr = translateExerciseName(exercise.name, "tr");
  if (!hasTurkishName(exercise)) warnings.push(`Türkçe adı yok: ${exercise.name}`);
  return { id: exercise.id, name: exercise.name, nameTr, level: exercise.level, equipment: exercise.equipment || "bodyweight" };
}).filter(Boolean);

const warmup = mobility(WARMUP, "warmup");
const cooldown = mobility(COOLDOWN, "cooldown");
const strengthTotal = slots.reduce((sum, slot) => sum + slot.exercises.length, 0);

const draft = {
  status: "approved",
  note: "Onaylı çekirdek havuz. Atlas (data/exercises.json) değişmez; plan üretimi yalnız bu havuzdan seçer. Her slotun ilk iki hareketi 'staple'dır.",
  summary: { slots: slots.length, strength: strengthTotal, warmup: warmup.length, cooldown: cooldown.length },
  slots,
  warmup,
  cooldown,
};
await writeFile(join(root, "data", "core-exercises.json"), `${JSON.stringify(draft, null, 2)}\n`);

const roleTr = { main: "ana", accessory: "yardımcı", core: "core", conditioning: "kondisyon" };
const lines = [
  "# Çekirdek hareket havuzu",
  "",
  `Toplam **${strengthTotal}** hareket, **${slots.length}** slot. Ayrıca ${warmup.length} ısınma, ${cooldown.length} soğuma hareketi.`,
  "",
  "Atlas (601 hareket) değişmez. Plan üretimi ve rotasyon yalnız bu havuzdan seçer. Her slotun ilk iki hareketi *staple*, gerisi *varyasyon*.",
  "**Rol:** ana = ilerleme için sabit kalan temel hareket · yardımcı = her blokta döner · core / kondisyon = serbest döner.",
  "",
  "Değişiklik için `scripts/build-core-exercises.mjs` içindeki SLOTS düzenlenir ve script yeniden çalıştırılır; bu dosya ve `core-exercises.json` ondan üretilir.",
  "",
];
for (const slot of slots) {
  lines.push(`## ${slot.label} · ${roleTr[slot.role]} (${slot.stats.total}: ev ${slot.stats.home}, yalnız salon ${slot.stats.gymOnly})`, "", "| | Hareket | Türkçe | Seviye | Ekipman | Ortam |", "|---|---|---|---|---|---|");
  for (const e of slot.exercises) lines.push(`| ${e.tier === "staple" ? "★" : ""} | ${e.name} | ${e.nameTr} | ${e.level} | ${e.equipment} | ${e.environment.join("/")} |`);
  lines.push("");
}
lines.push("## Isınma (dinamik)", "", warmup.map((e) => `${e.name} (${e.nameTr})`).join(" · "), "", "## Soğuma (statik)", "", cooldown.map((e) => `${e.name} (${e.nameTr})`).join(" · "), "");
if (warnings.length) lines.push("## Kapsam uyarıları", "", ...warnings.map((w) => `- ${w}`), "");
await writeFile(join(root, "data", "core-exercises.md"), lines.join("\n"));

console.log(`slot=${slots.length} strength=${strengthTotal} warmup=${warmup.length} cooldown=${cooldown.length}`);
if (problems.length) { console.error("HATALAR:\n" + problems.map((p) => `  ${p}`).join("\n")); process.exitCode = 1; }
if (warnings.length) console.log("UYARILAR:\n" + warnings.map((w) => `  ${w}`).join("\n"));
