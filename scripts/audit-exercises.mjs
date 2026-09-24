// Per-exercise audit of data/exercises.json (run: node scripts/audit-exercises.mjs).
// Writes, for every exercise:
//   requiredEquipment: string[][]  alternatives; each inner list must be fully owned ([] = no equipment)
//   environment:       where it is realistically done (home = doable with home-ownable gear)
//   level / category:  corrected by manual review
// Household props (chair, sofa, step, wall, books) are treated as always available at home.
import fs from "node:fs";

const FILE = new URL("../data/exercises.json", import.meta.url);
const exercises = JSON.parse(fs.readFileSync(FILE, "utf8"));

/** RepDB equipment slug -> canonical group the user can own/select. */
const GROUP = {
  dumbbell: "dumbbell", barbell: "barbell", ez_bar: "barbell", trap_bar: "barbell", kettlebell: "kettlebell",
  loop_band: "band", resistance_band: "band", pull_up_bar: "pull_up_bar", flat_bench: "bench",
  cable: "cable", suspension_trainer: "suspension", rings: "suspension", stability_ball: "stability_ball",
  jump_rope: "jump_rope", ab_wheel: "ab_wheel", plates: "plates", dip_station: "dip_station",
  plyo_box: "plyo_box", battle_rope: "gym_gear", slam_ball: "gym_gear", sled: "gym_gear", climbing_rope: "gym_gear",
  treadmill: "cardio_machine", air_bike: "cardio_machine", elliptical: "cardio_machine", rower: "cardio_machine",
  stair_climber: "cardio_machine", stationary_bike: "cardio_machine", wrist_roller: "gym_gear",
};
const groupOf = (slug) => (slug == null ? null : GROUP[slug] ?? "machine"); // every *_machine, leg_press, hack_squat, pec_deck...

/** Groups commonly owned at home; barbell setups are ownable but not typical, so they browse as "gym". */
const TYPICAL_HOME = new Set(["dumbbell", "kettlebell", "band", "pull_up_bar", "bench", "suspension", "stability_ball", "jump_rope", "ab_wheel"]);

// ---- Manual review: extra equipment the single slug does not capture -----------------------------
const NEEDS = {
  // Bench needed on top of the free weight
  "bench-press": ["barbell", "bench"], "close-grip-bench-press": ["barbell", "bench"], "wide-grip-bench-press": ["barbell", "bench"],
  "paused-bench-press": ["barbell", "bench"], "spoto-press": ["barbell", "bench"], "incline-bench-press": ["barbell", "bench"],
  "paused-incline-bench-press": ["barbell", "bench"], "close-grip-incline-bench": ["barbell", "bench"], "decline-bench-press-barbell": ["barbell", "bench"],
  "ez-bar-bench-press": ["barbell", "bench"], "incline-bench-ez-bar-press": ["barbell", "bench"], "decline-bench-press-ez-bar": ["barbell", "bench"],
  "close-grip-ez-bar-bench-press": ["barbell", "bench"], "barbell-pullover": ["barbell", "bench"], "bent-arm-barbell-pullover": ["barbell", "bench"],
  "bent-arm-ez-bar-pullover": ["barbell", "bench"], "ez-bar-pullover": ["barbell", "bench"], "skull-crusher": ["barbell", "bench"],
  "ez-bar-lying-tricep-extension": ["barbell", "bench"], "bench-pull": ["barbell", "bench"], "seated-barbell-overhead-press": ["barbell", "bench"],
  "barbell-preacher-curl": ["barbell", "machine"], "preacher-curl": ["barbell", "machine"], "ez-bar-spider-curl": ["barbell", "bench"],
  "hip-thrust": ["barbell", "bench"], "barbell-glute-bridge": ["barbell"],
  "db-bench-press": ["dumbbell", "bench"], "close-grip-db-bench-press": ["dumbbell", "bench"], "incline-db-press": ["dumbbell", "bench"],
  "decline-bench-press": ["dumbbell", "bench"], "db-fly": ["dumbbell", "bench"], "incline-dumbbell-fly": ["dumbbell", "bench"],
  "decline-db-fly": ["dumbbell", "bench"], "db-pullover": ["dumbbell", "bench"], "db-skull-crusher": ["dumbbell", "bench"],
  "lying-tricep-extension": ["dumbbell", "bench"], "chest-supported-db-row": ["dumbbell", "bench"], "chest-supported-dumbbell-shrug": ["dumbbell", "bench"],
  "single-arm-chest-supported-dumbbell-row": ["dumbbell", "bench"], "chest-supported-kettlebell-row": ["kettlebell", "bench"],
  "dumbbell-bench-pull": ["dumbbell", "bench"], "incline-db-curl": ["dumbbell", "bench"], "incline-hammer-curl": ["dumbbell", "bench"],
  "spider-curl": ["dumbbell", "bench"], "preacher-hammer-curl": ["dumbbell", "machine"], "seated-db-press": ["dumbbell", "bench"],
  // Split squats, one-arm rows and hip thrusts work off a chair or sofa, so they need no bench.
  // Racks, stations and gym apparatus hidden behind "no equipment"
  "captains-chair-knee-raise": ["dip_station"], "captains-chair-leg-raise": ["dip_station"],
  "back-extension": ["machine"], "decline-crunch": ["bench"], "dragon-flag": ["bench"], "bench-leg-pull-in": ["bench"],
  "inverted-row": ["barbell", "machine"], "straight-bar-dips": ["dip_station"], "l-sit": [],
  "weighted-wall-crunch": ["plates"], "landmine-press": ["barbell", "machine"], "one-arm-landmine-press": ["barbell", "machine"],
  "t-bar-row": ["barbell", "machine"], "rack-pull": ["barbell", "machine"],
  "squat": ["barbell", "machine"], "pause-squat": ["barbell", "machine"], "front-squat": ["barbell", "machine"], "heel-elevated-squat": ["barbell", "machine"],
  "overhead-squat": ["barbell", "machine"], "sumo-squat": ["barbell", "machine"], "barbell-lunge": ["barbell", "machine"], "barbell-reverse-lunge": ["barbell", "machine"],
  "good-morning": ["barbell", "machine"], "box-jump": ["plyo_box"],
};

/** Moves that work with either tool; each entry replaces requiredEquipment with alternatives. */
const EITHER = {
  "goblet-squat": [["kettlebell"], ["dumbbell"]],
  "suitcase-carry": [["kettlebell"], ["dumbbell"]],
  "weighted-wall-crunch": [["plates"], ["dumbbell"], ["kettlebell"]],
  "lying-tricep-extension": [["dumbbell", "bench"], ["barbell", "bench"]],
  "reverse-lunge": [["dumbbell"], ["kettlebell"]],
  "l-sit": [[], ["dip_station"]],
  "band-assisted-pull-ups": [["band", "pull_up_bar"]],
  "dead-hang": [["pull_up_bar"], ["suspension"]],
  "jefferson-curl": [["dumbbell"], ["kettlebell"], ["barbell"]],
};

// ---- Manual review: level corrections ---------------------------------------------------------------
const LEVEL = {
  // Technical/high-risk lifts
  snatch: "advanced", "clean-and-jerk": "advanced", "split-jerk": "advanced", "muscle-snatch": "advanced", "overhead-squat": "advanced",
  "behind-the-neck-press": "advanced", "jefferson-curl": "advanced", "toes-to-bar": "advanced", "spoto-press": "advanced",
  "kettlebell-turkish-get-ups": "advanced", "double-kettlebell-dead-split-snatch": "advanced", "double-kettlebell-swing-snatch": "advanced",
  "clap-push-ups": "advanced", "plyo-push-up": "advanced",
  // Basic movements overstated as intermediate
  lunge: "beginner", "split-squat": "beginner", "step-ups": "beginner", "box-squat": "beginner", "dead-hang": "beginner",
  clamshells: "beginner", "clamshells-hold": "beginner", "side-lying-hip-adduction": "beginner", "side-lying-hip-adduction-hold": "beginner",
  "reverse-crunches": "beginner", "wide-grip-push-ups": "beginner", "scapular-pull-ups": "beginner", "walking-lunge": "beginner",
  "side-lunge": "beginner", "bird-dog-hold": "beginner", "glute-bridge": "beginner", "flutter-kicks": "beginner",
  "reverse-plank": "beginner", "reverse-tabletop-hip-pulses": "beginner", "dumbbell-deadlift": "beginner", "dumbbell-romanian-deadlift": "beginner",
  "bent-over-db-row": "beginner", "single-arm-db-row": "beginner", "db-bench-press": "beginner", "dumbbell-shoulder-press": "beginner",
  "seated-db-press": "beginner", "dumbbell-floor-press": "beginner", "kettlebell-deadlift": "beginner", "lat-pulldown": "beginner",
  "seated-cable-row": "beginner", "leg-press": "beginner", "hex-bar-deadlift": "intermediate",
  // Overstated as advanced
  "dancer-pose": "intermediate", "half-moon-pose": "intermediate",
};

// ---- Manual review: category corrections ------------------------------------------------------------
// Static holds and yoga/mobility work mislabelled "strength"; they must never fill a strength slot.
const MOBILITY = new Set([
  "boat-pose", "bow-pose", "chair-pose", "chin-tuck-hold", "crow-pose", "dancer-pose", "dolphin-pose", "eagle-pose",
  "extended-side-angle", "half-moon-pose", "heel-to-toe-walk", "isometric-neck-side", "locust-pose", "revolved-chair-pose",
  "supported-shoulderstand", "three-legged-dog", "tree-pose", "warrior-one", "warrior-three", "warrior-two",
  "downward-dog-knee-tuck", "downward-dog-to-knee-drive", "downward-dog-to-plank", "downward-dog-to-upward-dog",
  "revolved-crescent-lunge", "crescent-lunge", "cocoons", "supine-windshield-wipers", "thoracic-bridge",
]);

const labels = [];
for (const e of exercises) {
  const primary = groupOf(e.equipment);
  let required = EITHER[e.id] ?? [NEEDS[e.id] ?? (primary ? [primary] : [])];
  required = required.map((option) => [...new Set(option)].sort());
  e.requiredEquipment = required;

  const typicalHome = required.some((option) => option.every((g) => TYPICAL_HOME.has(g)));
  e.isBodyweight = required.some((option) => option.length === 0);
  e.environment = e.isBodyweight ? ["home", "outdoor", "gym"] : typicalHome ? ["home", "gym"] : ["gym"];
  // Barbell setups are ownable but uncommon: they stay "gym" for browsing, yet a home user who owns
  // the listed equipment still gets them because selection checks ownership, not this tag.

  if (LEVEL[e.id]) e.level = LEVEL[e.id];
  if (MOBILITY.has(e.id)) e.category = "stretching";
  if (e.id === "russian-twist" || e.id === "kettlebell-russian-twist" || e.id === "superman" || e.id === "bird-dog" || e.id === "bird-dog-hold") e.category = "strength";
  labels.push(`${e.id}: ${required.map((o) => o.join("+") || "none").join(" | ")} · ${e.environment.join("/")} · ${e.level} · ${e.category}`);
}

fs.writeFileSync(FILE, `${JSON.stringify(exercises, null, 2)}\n`);
if (process.argv.includes("--print")) console.log(labels.join("\n"));
console.log(`Audited ${exercises.length} exercises.`);
