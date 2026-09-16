// Imports the RepDB Free Exercise Dataset (https://github.com/RepDB/exercise-dataset)
// as Hedefit's primary exercise catalog, replacing the legacy free-exercise-db import.
//
// Output shape matches `types/exercise.ts` `Exercise` (legacy fields kept stable so
// `lib/exercise-service.ts` / `lib/training/exercise-metadata.ts` need no reshaping,
// plus the additive RepDB canonical fields).
//
// Usage: node scripts/import-repdb.mjs [--source=<local exercises.json>] [--skip-images]

import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = fileURLToPath(new URL("..", import.meta.url));
const dataUrl = "https://raw.githubusercontent.com/RepDB/exercise-dataset/main/exercises.json";
const imageBaseUrl = "https://raw.githubusercontent.com/RepDB/exercise-dataset/main";
const attributionUrl = "https://raw.githubusercontent.com/RepDB/exercise-dataset/main/ATTRIBUTION.md";
const licenseUrl = "https://raw.githubusercontent.com/RepDB/exercise-dataset/main/LICENSE-DATA.md";

const sourceArgument = process.argv.find((argument) => argument.startsWith("--source="))?.slice(9);
const skipImages = process.argv.includes("--skip-images");

const safeId = (value) => String(value || "").replace(/[^a-zA-Z0-9_-]/g, "");

async function sourceData() {
  if (sourceArgument) return JSON.parse(await readFile(sourceArgument, "utf8"));
  const response = await fetch(dataUrl);
  if (!response.ok) throw new Error(`RepDB dataset download failed: ${response.status}`);
  return response.json();
}

// RepDB muscle slug -> the equipment/goal mapping tables live in
// lib/training/exercise-metadata.ts and lib/exercise-service.ts (the actual
// runtime consumers). The import script stays a thin, mostly-lossless transform
// so those tables are the single source of truth for domain normalization.

const GOAL_MAP = {
  hypertrophy: ["muscle_gain"],
  strength: ["strength"],
  power: ["strength"],
  endurance: ["endurance", "fat_loss"],
  mobility: ["mobility"],
  rehabilitation: ["mobility"],
  core: ["general_fitness"],
};

function deriveGoalCompatibility(exercise) {
  const goals = new Set(["general_fitness"]);
  for (const goal of exercise.goals || []) {
    for (const mapped of GOAL_MAP[goal] || []) goals.add(mapped);
  }
  if (exercise.category === "cardio") {
    goals.add("fat_loss");
    goals.add("endurance");
  }
  return [...goals];
}

function deriveEnvironment(exercise) {
  const env = new Set();
  const equipment = exercise.equipment || "";
  const tags = exercise.tags || [];
  if (exercise.is_bodyweight || equipment === "" || tags.includes("bodyweight")) {
    env.add("home");
    env.add("outdoor");
  }
  if (["dumbbell", "kettlebell", "resistance_band", "loop_band", "jump_rope"].includes(equipment)) {
    env.add("home");
  }
  // Anything requiring a rack, machine, or plates is gym-only.
  env.add("gym");
  return [...env];
}

function mediaStatus(images) {
  const flat = images?.flat || {};
  if (flat.start && flat.peak) return "complete";
  if (flat.main) return "complete";
  if (flat.start || flat.peak) return "partial";
  return "missing";
}

function transform(exercise) {
  const id = safeId(exercise.id);
  const flat = exercise.images?.flat || {};
  const imageStart = flat.start ? `/exercise-images/${id}/start.webp` : flat.main ? `/exercise-images/${id}/main.webp` : null;
  const imageEnd = flat.peak ? `/exercise-images/${id}/peak.webp` : flat.main ? `/exercise-images/${id}/main.webp` : null;
  const images = [...new Set([imageStart, imageEnd].filter(Boolean))];

  return {
    id,
    name: String(exercise.name_en || id.replaceAll("-", " ")),
    force: typeof exercise.force_type === "string" ? exercise.force_type : null,
    level: typeof exercise.difficulty === "string" ? exercise.difficulty : "beginner",
    mechanic: typeof exercise.mechanic === "string" ? exercise.mechanic : null,
    equipment: exercise.equipment ? String(exercise.equipment) : null,
    primaryMuscles: Array.isArray(exercise.primary_muscles) ? exercise.primary_muscles.map(String) : [],
    secondaryMuscles: Array.isArray(exercise.secondary_muscles) ? exercise.secondary_muscles.map(String) : [],
    instructions: Array.isArray(exercise.instructions_en) ? exercise.instructions_en.map(String) : [],
    category: typeof exercise.category === "string" ? exercise.category : "strength",
    images,

    source: "repdb",
    sourceExerciseId: exercise.id,
    descriptionEn: typeof exercise.description_en === "string" ? exercise.description_en : undefined,
    tipsEn: Array.isArray(exercise.tips_en) ? exercise.tips_en.map(String) : [],
    bodyPart: typeof exercise.body_part === "string" ? exercise.body_part : undefined,
    goalCompatibility: deriveGoalCompatibility(exercise),
    environment: deriveEnvironment(exercise),
    laterality: exercise.is_unilateral ? "unilateral" : "bilateral",
    isBodyweight: Boolean(exercise.is_bodyweight),
    metValue: typeof exercise.met === "number" ? exercise.met : undefined,
    imageStart,
    imageEnd,
    mediaStatus: mediaStatus(exercise.images),
    // RepDB tags carry positive safety signals (knee_safe, shoulder_safe, ...)
    // consumed by lib/training/exercise-metadata.ts to refine contraindications.
    _tags: Array.isArray(exercise.tags) ? exercise.tags.map(String) : [],
    isActive: true,
  };
}

async function downloadImage(sourcePath, localPath, attempts = 3) {
  for (let attempt = 1; attempt <= attempts; attempt += 1) {
    try {
      const response = await fetch(`${imageBaseUrl}/${sourcePath.split("/").map(encodeURIComponent).join("/")}`, {
        signal: AbortSignal.timeout(20_000),
      });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      await mkdir(dirname(localPath), { recursive: true });
      await writeFile(localPath, Buffer.from(await response.arrayBuffer()));
      return true;
    } catch (error) {
      if (attempt === attempts) {
        console.warn(`RepDB image download failed after ${attempts} attempts: ${sourcePath} (${error.message})`);
        return false;
      }
      await new Promise((resolve) => setTimeout(resolve, 500 * attempt));
    }
  }
  return false;
}

const source = await sourceData();
const list = Array.isArray(source.exercises) ? source.exercises : [];
if (!list.length) throw new Error("RepDB dataset payload has no `exercises` array.");

const normalized = list.map(transform);

await mkdir(join(root, "data"), { recursive: true });
// Strip the internal `_tags` field from the persisted catalog; it's only used
// transiently by the metadata-enrichment step at build time (see below).
const persisted = normalized.map(({ _tags, ...rest }) => rest);
await writeFile(join(root, "data", "exercises.json"), `${JSON.stringify(persisted, null, 2)}\n`);

if (!skipImages) {
  const jobs = list.flatMap((exercise) => {
    const id = safeId(exercise.id);
    const flat = exercise.images?.flat || {};
    const entries = flat.main
      ? [["main.webp", flat.main]]
      : [flat.start && ["start.webp", flat.start], flat.peak && ["peak.webp", flat.peak]].filter(Boolean);
    return entries.map(([basename, sourcePath]) => () => downloadImage(sourcePath, join(root, "public", "exercise-images", id, basename)));
  });
  // Bounded concurrency, with retries per image: keep this polite to
  // raw.githubusercontent.com and resilient to a flaky sandbox network.
  const batchSize = 12;
  let failures = 0;
  for (let index = 0; index < jobs.length; index += batchSize) {
    const results = await Promise.all(jobs.slice(index, index + batchSize).map((job) => job()));
    failures += results.filter((ok) => !ok).length;
  }
  if (failures) console.warn(`${failures}/${jobs.length} RepDB images failed to download; re-run \`npm run data:import-repdb -- --skip-images=false\` to retry, media status stays accurate either way.`);
}
await writeFile(join(root, "data", "exercises-repdb-tags.json"), `${JSON.stringify(Object.fromEntries(normalized.map((e) => [e.id, e._tags])), null, 2)}\n`);

const [attributionResponse, licenseResponse] = await Promise.all([fetch(attributionUrl), fetch(licenseUrl)]);
if (!attributionResponse.ok) throw new Error(`Attribution download failed: ${attributionResponse.status}`);
if (!licenseResponse.ok) throw new Error(`License download failed: ${licenseResponse.status}`);
await writeFile(join(root, "data", "RepDB_ATTRIBUTION.md"), await attributionResponse.text());
await writeFile(join(root, "data", "RepDB_LICENSE-DATA.md"), await licenseResponse.text());

const mediaCounts = persisted.reduce((acc, ex) => {
  acc[ex.mediaStatus] = (acc[ex.mediaStatus] || 0) + 1;
  return acc;
}, {});
console.log(`Imported ${persisted.length} RepDB exercises.`);
console.log(`Media status: ${JSON.stringify(mediaCounts)}`);
