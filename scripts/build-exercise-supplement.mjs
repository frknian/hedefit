// Builds data/exercises-supplement.json: moves added on top of the RepDB atlas
// (data/exercises.json, which scripts/import-repdb.mjs regenerates and must stay
// untouched) so a person who owns ONLY a resistance band, or ONLY dumbbells, can
// train every muscle group at home.
//
// Two kinds of entries:
//  - promoted: band moves from the legacy free-exercise-db (public domain, see
//    data/FREE_EXERCISE_DB_LICENSE.md). Their photos already ship in
//    public/exercise-images/<legacy id>/, so they keep `mediaStatus: "complete"`.
//  - authored: standard moves the atlas lacks (band rows, pulldowns, curls…).
//    They have no photos yet (`mediaStatus: "missing"`, `images: []`); drop
//    `start.webp`/`peak.webp` into public/exercise-images/<id>/ and re-run this
//    script to switch them to "complete".
//
// Usage: node scripts/build-exercise-supplement.mjs

import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

const root = fileURLToPath(new URL("..", import.meta.url));
const legacy = new Map(JSON.parse(readFileSync(`${root}data/legacy-exercises.json`, "utf8")).map((exercise) => [exercise.id, exercise]));

const BAND = { equipment: "resistance_band", required: [["band"]] };
const DUMBBELL = { equipment: "dumbbell", required: [["dumbbell"]] };
const COMPOUND_GOALS = ["general_fitness", "muscle_gain", "strength"];
const ISOLATION_GOALS = ["general_fitness", "muscle_gain"];

// id, English name, setup, force, mechanic, level, bodyPart, primary, secondary, MET, [legacy id | instructions]
const ENTRIES = [
  // ---- promoted from legacy (photos exist) ------------------------------------------------------
  { id: "band-shoulder-press", name: "Band Shoulder Press", gear: BAND, force: "push", mechanic: "compound", level: "beginner", bodyPart: "shoulders", primary: ["anterior_deltoid", "lateral_deltoid"], secondary: ["triceps_brachii"], met: 4.5, legacy: "Shoulder_Press_-_With_Bands" },
  { id: "band-lateral-raise", name: "Band Lateral Raise", gear: BAND, force: "push", mechanic: "isolation", level: "beginner", bodyPart: "shoulders", primary: ["lateral_deltoid"], secondary: [], met: 3.5, legacy: "Lateral_Raise_-_With_Bands" },
  { id: "band-rear-delt-fly", name: "Band Rear Delt Fly", gear: BAND, force: "pull", mechanic: "isolation", level: "beginner", bodyPart: "shoulders", primary: ["posterior_deltoid"], secondary: ["rhomboids", "trapezius"], met: 3.5, legacy: "Back_Flyes_-_With_Bands" },
  { id: "band-chest-fly", name: "Band Chest Fly", gear: BAND, force: "push", mechanic: "isolation", level: "beginner", bodyPart: "chest", primary: ["pectoralis_major"], secondary: ["anterior_deltoid"], met: 3.5, legacy: "Cross_Over_-_With_Bands" },
  { id: "band-overhead-triceps-extension", name: "Band Overhead Triceps Extension", gear: BAND, force: "push", mechanic: "isolation", level: "beginner", bodyPart: "upper_arms", primary: ["triceps_brachii"], secondary: [], met: 3.5, legacy: "Speed_Band_Overhead_Triceps" },
  { id: "band-calf-raise", name: "Band Calf Raise", gear: BAND, force: "push", mechanic: "isolation", level: "beginner", bodyPart: "lower_legs", primary: ["gastrocnemius"], secondary: ["soleus"], met: 3.5, legacy: "Calf_Raises_-_With_Bands" },
  { id: "band-pull-through", name: "Band Pull-Through", gear: BAND, force: "pull", mechanic: "compound", level: "beginner", bodyPart: "upper_legs", primary: ["gluteus_maximus", "hamstrings"], secondary: ["erector_spinae"], met: 4.5, legacy: "Band_Good_Morning_Pull_Through" },
  { id: "band-seated-hamstring-curl", name: "Band Seated Hamstring Curl", gear: BAND, force: "pull", mechanic: "isolation", level: "beginner", bodyPart: "upper_legs", primary: ["hamstrings"], secondary: [], met: 3.5, legacy: "Seated_Band_Hamstring_Curl" },

  // ---- authored (no photos yet) -----------------------------------------------------------------
  { id: "band-seated-row", name: "Band Seated Row", gear: BAND, force: "pull", mechanic: "compound", level: "beginner", bodyPart: "back", primary: ["latissimus_dorsi", "rhomboids"], secondary: ["biceps_brachii", "posterior_deltoid"], met: 4.5, instructions: ["Sit on the floor with your legs extended and loop the middle of the band around your feet; hold an end in each hand with your arms straight.", "Sit tall and brace your core so your back stays neutral.", "Pull your hands toward your lower ribs, driving the elbows back and squeezing the shoulder blades together.", "Pause for a moment, then extend the arms slowly under control without leaning back."], photoFrom: "Seated_Cable_Rows" },
  { id: "band-bent-over-row", name: "Band Bent-Over Row", gear: BAND, force: "pull", mechanic: "compound", level: "beginner", bodyPart: "back", primary: ["latissimus_dorsi", "rhomboids"], secondary: ["biceps_brachii", "posterior_deltoid", "erector_spinae"], met: 4.5, instructions: ["Stand on the middle of the band with feet hip-width apart and hold an end in each hand.", "Hinge at the hips until your torso is roughly 45 degrees, back flat and knees slightly bent.", "Row both hands toward your hips, leading with the elbows and squeezing the shoulder blades.", "Lower slowly until the arms are straight again, keeping the torso still."], photoFrom: "Bent_Over_Two-Dumbbell_Row" },
  { id: "band-single-arm-row", name: "Band Single-Arm Row", gear: BAND, force: "pull", mechanic: "compound", level: "beginner", bodyPart: "back", primary: ["latissimus_dorsi"], secondary: ["rhomboids", "biceps_brachii"], met: 4, laterality: "unilateral", instructions: ["Anchor the band at about waist height (a door anchor or a sturdy post) and face the anchor in a split stance.", "Hold the band with one hand, arm extended, so there is tension from the start.", "Pull the elbow back past your ribs while keeping the shoulders square to the anchor.", "Return slowly to full extension, then repeat and switch sides."], photoFrom: "One-Arm_Dumbbell_Row" },
  { id: "band-lat-pulldown", name: "Band Lat Pulldown", gear: BAND, force: "pull", mechanic: "compound", level: "beginner", bodyPart: "back", primary: ["latissimus_dorsi"], secondary: ["biceps_brachii", "rhomboids"], met: 4.5, instructions: ["Anchor the band high (over the top of a door or to a pull-up bar) and kneel facing it, holding the band with arms extended overhead.", "Sit tall with your ribs down and shoulders away from your ears.", "Pull the band down toward your upper chest, driving the elbows down and back.", "Pause at the bottom, then let the hands rise slowly until your arms are straight."], photoFrom: "Wide-Grip_Lat_Pulldown" },
  { id: "band-straight-arm-pulldown", name: "Band Straight-Arm Pulldown", gear: BAND, force: "pull", mechanic: "isolation", level: "beginner", bodyPart: "back", primary: ["latissimus_dorsi"], secondary: ["triceps_brachii"], met: 3.5, instructions: ["Anchor the band high and stand facing it, holding the band with straight arms at shoulder height.", "Hinge slightly at the hips and keep a soft bend in the elbows.", "Sweep your arms down to your thighs, leading with the lats and keeping the elbows fixed.", "Return slowly to shoulder height without letting the band snap the arms back."], photoFrom: "Straight-Arm_Pulldown" },
  { id: "band-biceps-curl", name: "Band Biceps Curl", gear: BAND, force: "pull", mechanic: "isolation", level: "beginner", bodyPart: "upper_arms", primary: ["biceps_brachii"], secondary: ["brachialis"], met: 3.5, instructions: ["Stand on the middle of the band with feet hip-width apart and hold an end in each hand, palms forward.", "Keep your elbows pinned to your sides and your chest tall.", "Curl your hands toward your shoulders until the biceps are fully contracted.", "Lower slowly, resisting the band all the way down."], photoFrom: "Dumbbell_Bicep_Curl" },
  { id: "band-hammer-curl", name: "Band Hammer Curl", gear: BAND, force: "pull", mechanic: "isolation", level: "beginner", bodyPart: "upper_arms", primary: ["brachialis", "biceps_brachii"], secondary: ["brachioradialis"], met: 3.5, instructions: ["Stand on the middle of the band and hold an end in each hand with palms facing each other.", "Keep the elbows tucked against your ribs.", "Curl the hands up toward your shoulders without rotating the wrists.", "Lower slowly to a straight arm."], photoFrom: "Hammer_Curls" },
  { id: "band-triceps-pushdown", name: "Band Triceps Pushdown", gear: BAND, force: "push", mechanic: "isolation", level: "beginner", bodyPart: "upper_arms", primary: ["triceps_brachii"], secondary: [], met: 3.5, instructions: ["Anchor the band high (over a door or to a pull-up bar) and stand facing it, holding an end in each hand with elbows bent at your sides.", "Keep your upper arms still and your torso upright.", "Press the hands down until your arms are fully straight, squeezing the triceps.", "Return slowly until the forearms are about parallel to the floor."], photoFrom: "Triceps_Pushdown" },
  { id: "band-face-pull", name: "Band Face Pull", gear: BAND, force: "pull", mechanic: "compound", level: "beginner", bodyPart: "shoulders", primary: ["posterior_deltoid"], secondary: ["rhomboids", "trapezius"], met: 3.5, instructions: ["Anchor the band at about chest height and hold an end in each hand, stepping back until there is tension.", "Start with arms extended and thumbs pointing back.", "Pull the hands toward your face, spreading them apart and driving the elbows high and wide.", "Pause with the shoulder blades squeezed, then return slowly."], photoFrom: "Face_Pull" },
  { id: "band-chest-press", name: "Band Chest Press", gear: BAND, force: "push", mechanic: "compound", level: "beginner", bodyPart: "chest", primary: ["pectoralis_major"], secondary: ["anterior_deltoid", "triceps_brachii"], met: 4.5, instructions: ["Anchor the band behind you at chest height and stand facing away in a split stance, holding an end in each hand at chest level.", "Brace your core and keep your elbows at roughly 45 degrees from the torso.", "Press the hands forward until your arms are fully extended.", "Return slowly until the hands are back at chest level."], photoFrom: "Bench_Press_-_With_Bands" },
  { id: "band-pallof-press", name: "Band Pallof Press", gear: BAND, force: "static", mechanic: "compound", level: "beginner", bodyPart: "core", primary: ["obliques", "transverse_abdominis"], secondary: ["rectus_abdominis"], met: 3.5, instructions: ["Anchor the band at chest height and stand side-on to it, holding the band with both hands at your chest.", "Step away until there is tension, feet shoulder-width apart, core braced.", "Press the hands straight out in front of you without letting the band rotate your torso.", "Hold for a second, then bring the hands back to the chest."], photoFrom: "Pallof_Press" },
  { id: "dumbbell-floor-fly", name: "Dumbbell Floor Fly", gear: DUMBBELL, force: "push", mechanic: "isolation", level: "beginner", bodyPart: "chest", primary: ["pectoralis_major"], secondary: ["anterior_deltoid"], met: 3.5, instructions: ["Lie on the floor with a dumbbell in each hand, arms extended above your chest and palms facing each other.", "Keep a soft bend in your elbows and lower the dumbbells out to the sides until your upper arms touch the floor.", "Pause briefly, then squeeze your chest to bring the dumbbells back together above you.", "Keep the elbow bend constant; do not turn the movement into a press."], photoFrom: "Dumbbell_Flyes" },
];

const photo = (dir, name) => (existsSync(`${root}public/exercise-images/${dir}/${name}`) ? `/exercise-images/${dir}/${name}` : null);

const problems = [];
const records = ENTRIES.map((entry) => {
  let instructions = entry.instructions;
  let images = [];
  let sourceExerciseId;
  if (entry.photoFrom) {
    // Authored instructions, borrowed photos: the closest public-domain legacy movement
    // (same pattern, other equipment) so no authored move is left without media.
    const source = legacy.get(entry.photoFrom);
    if (!source) { problems.push(`${entry.id}: photoFrom ${entry.photoFrom} yok`); return null; }
    images = ["0.jpg", "1.jpg"].map((file) => photo(source.id, file)).filter(Boolean);
    if (images.length < 2) problems.push(`${entry.id}: photoFrom görselleri eksik`);
  } else if (entry.legacy) {
    const source = legacy.get(entry.legacy);
    if (!source) { problems.push(`${entry.id}: legacy ${entry.legacy} yok`); return null; }
    instructions = source.instructions;
    sourceExerciseId = source.id;
    images = ["0.jpg", "1.jpg"].map((file) => photo(source.id, file)).filter(Boolean);
    if (images.length < 2) problems.push(`${entry.id}: legacy görselleri eksik`);
  }
  if (!entry.photoFrom && !entry.legacy) {
    // Authored moves switch to "complete" once start/peak photos are dropped in.
    const start = photo(entry.id, "start.webp");
    const peak = photo(entry.id, "peak.webp");
    images = [start, peak].filter(Boolean);
  }
  const isolation = entry.mechanic === "isolation";
  return {
    id: entry.id,
    name: entry.name,
    force: entry.force,
    level: entry.level,
    mechanic: entry.mechanic,
    equipment: entry.gear.equipment,
    primaryMuscles: entry.primary,
    secondaryMuscles: entry.secondary,
    instructions,
    category: "strength",
    images,
    source: "supplement",
    ...(sourceExerciseId ? { sourceExerciseId } : {}),
    bodyPart: entry.bodyPart,
    goalCompatibility: isolation ? ISOLATION_GOALS : COMPOUND_GOALS,
    environment: ["home", "gym"],
    laterality: entry.laterality ?? "bilateral",
    isBodyweight: false,
    metValue: entry.met,
    imageStart: images[0] ?? null,
    imageEnd: images[1] ?? images[0] ?? null,
    mediaStatus: images.length >= 2 ? "complete" : images.length === 1 ? "partial" : "missing",
    isActive: true,
    requiredEquipment: entry.gear.required,
  };
}).filter(Boolean);

writeFileSync(`${root}data/exercises-supplement.json`, `${JSON.stringify(records, null, 2)}\n`);
const missing = records.filter((record) => record.mediaStatus !== "complete").map((record) => record.id);
console.log(`${records.length} hareket yazıldı; görseli eksik: ${missing.length}${missing.length ? ` (${missing.join(", ")})` : ""}`);
if (problems.length) { console.error(problems.join("\n")); process.exitCode = 1; }
