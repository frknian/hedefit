// Exercise Metadata Standardization and Enrichment
//
// Source: RepDB (data/exercises.json, see scripts/import-repdb.mjs) plus the
// legacy free-exercise-db catalog kept addressable for old workout logs (see
// data/legacy-exercises.json + lib/exercise-service.ts's migration fallback).

import exerciseData from "../../data/exercises.json" with { type: "json" };
import repdbTags from "../../data/exercises-repdb-tags.json" with { type: "json" };
import type {
  FitnessLevel,
  LimitationArea,
  MovementPattern,
  MuscleGroup,
  StandardizedExercise,
} from "./types.ts";

const tagsById = repdbTags as Record<string, string[]>;

// RepDB ships a small, fixed muscle-slug vocabulary (30 values) — map it
// directly. The substring heuristic below stays as a fallback for the legacy
// free-exercise-db catalog, whose muscle strings are freeform ("middle back",
// "lower back", ...).
const REPDB_MUSCLE_MAP: Record<string, MuscleGroup> = {
  pectoralis_major: "chest",
  serratus_anterior: "chest",
  latissimus_dorsi: "back",
  trapezius: "back",
  rhomboids: "back",
  erector_spinae: "back",
  quadratus_lumborum: "back",
  anterior_deltoid: "shoulders",
  lateral_deltoid: "shoulders",
  posterior_deltoid: "shoulders",
  supraspinatus: "shoulders",
  quadriceps: "quadriceps",
  adductors: "quadriceps",
  hip_flexors: "quadriceps",
  hamstrings: "hamstrings",
  gluteus_maximus: "glutes",
  gluteus_medius: "glutes",
  abductors: "glutes",
  biceps_brachii: "biceps",
  brachialis: "biceps",
  brachioradialis: "biceps",
  triceps_brachii: "triceps",
  forearms: "biceps",
  forearm_flexors: "biceps",
  forearm_extensors: "triceps",
  gastrocnemius: "calves",
  soleus: "calves",
  rectus_abdominis: "core",
  obliques: "core",
  transverse_abdominis: "core",
};

export function normalizeMuscleGroup(rawMuscle: string): MuscleGroup {
  const key = rawMuscle.toLowerCase().trim();
  const direct = REPDB_MUSCLE_MAP[key];
  if (direct) return direct;

  // Legacy free-exercise-db fallback (freeform strings, e.g. "middle back").
  // NOTE: checked in an order that avoids "lateral_deltoid" false-matching
  // the back branch's "lat" substring.
  if (key.includes("shoulder") || key.includes("delt") || key.includes("omuz")) return "shoulders";
  if (key.includes("chest") || key.includes("pector") || key.includes("göğ")) return "chest";
  if (key.includes("lats") || key === "lat" || key.includes("middle back") || key.includes("lower back") || key.includes("trapezius") || key.includes("traps") || key.includes("sırt") || key.includes("kanat")) return "back";
  if (key.includes("quad") || key.includes("adductor") || key.includes("ön bacak")) return "quadriceps";
  if (key.includes("hamstring") || key.includes("arka bacak")) return "hamstrings";
  if (key.includes("glute") || key.includes("abductor") || key.includes("kalça")) return "glutes";
  if (key.includes("bicep") || key.includes("kol") || key.includes("forearm") || key.includes("pazu")) return "biceps";
  if (key.includes("tricep") || key.includes("arka kol")) return "triceps";
  if (key.includes("calf") || key.includes("calves") || key.includes("baldır")) return "calves";
  if (key.includes("abdom") || key.includes("core") || key.includes("karın") || key.includes("oblique")) return "core";
  return "core";
}

export function detectMovementPattern(name: string, primaryMuscles: MuscleGroup[], category: string, force: string | null = null): MovementPattern {
  const lower = name.toLowerCase();

  // 0. Static holds / mobility poses. A handful of RepDB rows (Locust Pose,
  // Superman, Bow Pose, ...) are static-force yoga-style holds but mistagged
  // category:"strength" upstream, so the `category === "stretching"` guard in
  // exercise-selector.ts never catches them. Left unguarded here, the "back"
  // muscle fallback below (rule 6) labelled them "horizontal_pull" — a real
  // rowing pattern — which won them the +40 pattern-match score bonus over an
  // actual banded row for e.g. a home+bands "back" budget slot. They still
  // pass the muscle-eligibility filter (this only strips the false bonus).
  if (category === "stretching" || force === "static" || /\bpose\b/i.test(lower)) {
    return "core";
  }

  // 1. Core
  if (primaryMuscles.includes("core") || /plank|crunch|sit-up|leg raise|rollout|twist|pallof|hollow|ab |dead bug/i.test(lower)) {
    return "core";
  }

  // 2. Lunge
  if (/lunge|split squat|step-up|step up|bulgarian/i.test(lower)) {
    return "lunge";
  }

  // 3. Squat
  if (/squat|leg press|hack squat/i.test(lower)) {
    return "squat";
  }

  // 4. Hinge
  if (/deadlift|rdl|romanian|good morning|hip thrust|glute bridge|swing|back extension|hyperextension|pull-through/i.test(lower)) {
    return "hinge";
  }

  // 5. Vertical Pull
  if (/pull-up|pull up|chin-up|chin up|lat pull|pulldown/i.test(lower)) {
    return "vertical_pull";
  }

  // 6. Horizontal Pull
  if (/row|face pull|inverted row|rear delt|lat pushdown/i.test(lower) || (primaryMuscles.includes("back") && !/pull-up|pulldown/i.test(lower))) {
    return "horizontal_pull";
  }

  // 7. Vertical Push
  if (/overhead|military press|shoulder press|push press|pike push|handstand|arnold press|upright row/i.test(lower) || (primaryMuscles.includes("shoulders") && /press/i.test(lower))) {
    return "vertical_push";
  }

  // 8. Horizontal Push
  if (/bench press|chest press|push-up|push up|dip|fly|flye|pec deck/i.test(lower) || primaryMuscles.includes("chest")) {
    return "horizontal_push";
  }

  // 9. Carry
  if (/carry|farmer|walk/i.test(lower)) {
    return "carry";
  }

  // Fallback heuristics based on primary muscles
  if (primaryMuscles.includes("quadriceps") || primaryMuscles.includes("calves")) return "squat";
  if (primaryMuscles.includes("hamstrings") || primaryMuscles.includes("glutes")) return "hinge";
  if (primaryMuscles.includes("shoulders")) return "vertical_push";
  if (primaryMuscles.includes("biceps")) return "horizontal_pull";
  if (primaryMuscles.includes("triceps")) return "horizontal_push";

  return "horizontal_push";
}

export function detectCompoundOrIsolation(name: string, mechanic: string | null, pattern: MovementPattern): "compound" | "isolation" {
  if (mechanic === "compound") return "compound";
  if (mechanic === "isolation") return "isolation";

  const lower = name.toLowerCase();
  if (/curl|extension|raise|fly|flye|kickback|shrug|wrist|calf/i.test(lower)) {
    return "isolation";
  }

  if (["squat", "hinge", "lunge", "horizontal_push", "vertical_push", "horizontal_pull", "vertical_pull"].includes(pattern)) {
    if (!/curl|extension|raise|fly/i.test(lower)) return "compound";
  }

  return "isolation";
}

/**
 * RepDB's `tags` carry explicit positive safety signals (`knee_safe`,
 * `shoulder_safe`, `lower_back_safe`). Those are trusted over the regex
 * heuristic below: a native "safe for X" tag clears that limitation even if
 * the name/pattern would otherwise flag it.
 */
const SAFE_TAG_TO_LIMITATION: Record<string, LimitationArea> = {
  knee_safe: "knee",
  shoulder_safe: "shoulder",
  lower_back_safe: "lower_back",
};

export function detectContraindications(name: string, pattern: MovementPattern, muscles: MuscleGroup[], tags: string[] = []): LimitationArea[] {
  const lower = name.toLowerCase();
  const limits = new Set<LimitationArea>();

  // Knee issues
  if (pattern === "squat" || pattern === "lunge" || /jump|pistol|leg press|hack squat/i.test(lower)) {
    limits.add("knee");
  }

  // Shoulder issues
  if (pattern === "vertical_push" || /dip|behind the neck|upright row|overhead|flye|incline bench/i.test(lower)) {
    limits.add("shoulder");
  }

  // Lower back issues
  if (/deadlift|good morning|hyperextension|heavy bent|standing row|clean/i.test(lower) || (pattern === "hinge" && /barbell/i.test(lower))) {
    limits.add("lower_back");
  }

  // Wrist issues
  if (/straight bar|clean|front squat|wrist|push-up/i.test(lower)) {
    limits.add("wrist");
  }

  // Neck issues
  if (/neck|behind the neck|shrug/i.test(lower)) {
    limits.add("neck");
  }

  // Hip issues
  if (/deep squat|sumo|adductor|wide stance/i.test(lower)) {
    limits.add("hip");
  }

  for (const tag of tags) {
    const cleared = SAFE_TAG_TO_LIMITATION[tag];
    if (cleared) limits.delete(cleared);
  }

  return Array.from(limits);
}

// Canonical equipment vocabulary consumed by lib/training/exercise-selector.ts
// (`isEquipmentAvailable`) and lib/training/profile-normalizer.ts
// (`normalizeEquipment`) — both sides of the hard filter MUST agree on this
// small set, so RepDB's ~55 specific tags collapse into it here.
const REPDB_EQUIPMENT_MAP: Record<string, string[]> = {
  barbell: ["barbell"],
  ez_bar: ["barbell"],
  trap_bar: ["barbell"],
  plates: ["barbell"],
  smith_machine: ["machine"],
  dumbbell: ["dumbbell"],
  kettlebell: ["kettlebell"],
  cable: ["cable"],
  resistance_band: ["bands"],
  loop_band: ["bands"],
  flat_bench: ["bench"],
  glute_ham_developer: ["bench"],
  pull_up_bar: ["pull-up bar"],
  assisted_pullup_machine: ["pull-up bar"],
  dip_station: ["pull-up bar"],
  rings: ["pull-up bar"],
  suspension_trainer: ["pull-up bar"],
};

export function normalizeEquipmentList(rawEquipment: string | null): string[] {
  if (!rawEquipment) return ["bodyweight"];
  const key = rawEquipment.toLowerCase().trim();
  const mapped = REPDB_EQUIPMENT_MAP[key];
  if (mapped) return mapped;
  // Any other RepDB machine tag (leg_press, hack_squat, lat_pulldown_machine,
  // rower, treadmill, ...) is gym-only equipment — bucket it as "machine".
  if (key in REPDB_MACHINE_SET) return ["machine"];

  // Legacy free-exercise-db fallback: freeform equipment text.
  const list: string[] = [];
  if (key.includes("barbell") || key.includes("olympic")) list.push("barbell");
  if (key.includes("dumbbell") || key.includes("dambıl")) list.push("dumbbell");
  if (key.includes("cable") || key.includes("makara")) list.push("cable");
  if (key.includes("machine") || key.includes("makine")) list.push("machine");
  if (key.includes("body") || key.includes("none") || key.includes("vücut")) list.push("bodyweight");
  if (key.includes("band") || key.includes("lastik")) list.push("bands");
  if (key.includes("kettlebell")) list.push("kettlebell");
  if (key.includes("bench")) list.push("bench");
  return list.length > 0 ? list : ["machine"];
}

const REPDB_MACHINE_SET: Record<string, true> = Object.fromEntries([
  "ab_crunch_machine", "ab_wheel", "air_bike", "back_extension_machine", "battle_rope",
  "bicep_curl_machine", "chest_fly_machine", "chest_press_machine", "climbing_rope",
  "dip_machine", "donkey_calf_raise_machine", "elliptical", "hack_squat",
  "hip_abduction_machine", "hip_adduction_machine", "hip_thrust_machine", "jump_rope",
  "lat_pulldown_machine", "leg_curl", "leg_extension", "leg_press",
  "plate_loaded_lateral_raise_machine", "plyo_box", "preacher_curl_machine", "rower",
  "seated_calf_raise_machine", "shoulder_press_machine", "shrug_machine", "slam_ball",
  "sled", "stability_ball", "stair_climber", "standing_calf_raise_machine",
  "stationary_bike", "treadmill", "tricep_extension_machine", "wrist_roller",
].map((key) => [key, true as const]));

export function normalizeDifficulty(level: string): FitnessLevel {
  const lower = level.toLowerCase();
  if (lower.includes("expert") || lower.includes("advanced") || lower.includes("ileri")) return "advanced";
  if (lower.includes("inter") || lower.includes("orta")) return "intermediate";
  return "beginner";
}

// Build standardized exercise catalog once in memory
const standardizedExercises: StandardizedExercise[] = (exerciseData as Array<Record<string, unknown>>).map((item) => {
  const id = String(item.id || "");
  const name = String(item.name || "");
  const instructions = Array.isArray(item.instructions) ? item.instructions.map(String) : [];
  const tags = tagsById[id] || [];

  const primaryRaw = Array.isArray(item.primaryMuscles) ? item.primaryMuscles.map(String) : [];
  const secondaryRaw = Array.isArray(item.secondaryMuscles) ? item.secondaryMuscles.map(String) : [];
  // RepDB sometimes lists multiple raw slugs that collapse onto the same
  // canonical MuscleGroup (e.g. gluteus_maximus + gluteus_medius -> "glutes").
  // Dedupe after mapping so weekly-volume metrics (lib/training/plan-validator.ts)
  // don't double-count sets for a single exercise's target muscle.
  const primaryMuscles: MuscleGroup[] = [...new Set(primaryRaw.map(normalizeMuscleGroup))];
  const secondaryMuscles: MuscleGroup[] = [...new Set(secondaryRaw.map(normalizeMuscleGroup))].filter((muscle) => !primaryMuscles.includes(muscle));
  const category = String(item.category || "strength");
  const force = item.force ? String(item.force) : null;
  const mechanic = item.mechanic ? String(item.mechanic) : null;
  const equipment = normalizeEquipmentList(item.equipment ? String(item.equipment) : null);
  const difficulty = normalizeDifficulty(String(item.level || "beginner"));

  const movementPattern = detectMovementPattern(name, primaryMuscles, category, force);
  const compoundOrIsolation = detectCompoundOrIsolation(name, mechanic, movementPattern);
  const contraindications = detectContraindications(name, movementPattern, primaryMuscles, tags);
  const estimatedDurationSeconds = compoundOrIsolation === "compound" ? 120 : 90;

  return {
    id,
    name,
    primaryMuscles: primaryMuscles.length > 0 ? primaryMuscles : ["core"],
    secondaryMuscles,
    movementPattern,
    equipment,
    difficulty,
    compoundOrIsolation,
    contraindications,
    estimatedDurationSeconds,
    category,
    force,
    mechanic,
    instructions,
    goalCompatibility: Array.isArray(item.goalCompatibility) ? item.goalCompatibility.map(String) : undefined,
  };
});

const exerciseMap = new Map<string, StandardizedExercise>(standardizedExercises.map((ex) => [ex.id, ex]));
const normalizedExerciseMap = new Map<string, StandardizedExercise>();
standardizedExercises.forEach((ex) => {
  normalizedExerciseMap.set(ex.id.toLowerCase().replace(/[-_\s]/g, ""), ex);
});

export function getStandardizedCatalog(): StandardizedExercise[] {
  return standardizedExercises;
}

export function getStandardizedExerciseById(id: string): StandardizedExercise | undefined {
  if (!id) return undefined;
  const direct = exerciseMap.get(id);
  if (direct) return direct;
  return normalizedExerciseMap.get(id.toLowerCase().replace(/[-_\s]/g, ""));
}
