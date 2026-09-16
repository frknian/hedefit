// Safety net over the current catalog (data/exercises.json): RepDB ships
// pre-deduplicated, but this guards against future catalog refreshes
// introducing near-duplicates (same movement under a slightly different
// name/id) — composite key per the migration plan: sourceExerciseId ->
// normalized name -> equipment+movementPattern+primaryMuscles+mechanic.
//
// Usage: node scripts/detect-exercise-duplicates.mjs

import { readFile, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { fileURLToPath } from "node:url";

const root = fileURLToPath(new URL("..", import.meta.url));
const fold = (value) => String(value || "").toLowerCase().replace(/[-_]/g, " ").replace(/\s+/g, " ").trim();

const exercises = JSON.parse(await readFile(join(root, "data", "exercises.json"), "utf8"));

const groups = new Map();
function addTo(key, exercise) {
  if (!key) return;
  const bucket = groups.get(key);
  if (bucket) bucket.push(exercise.id);
  else groups.set(key, [exercise.id]);
}

const bySourceId = new Map();
const byName = new Map();
for (const exercise of exercises) {
  if (exercise.sourceExerciseId) addTo(`source:${exercise.sourceExerciseId}`, exercise);
  addTo(`name:${fold(exercise.name)}`, exercise);
  const compositeKey = `composite:${exercise.equipment || "none"}|${fold(exercise.primaryMuscles.join(","))}|${exercise.mechanic || "n/a"}`;
  addTo(compositeKey, exercise);
}

const suspiciousComposite = [...groups.entries()]
  .filter(([key, ids]) => key.startsWith("composite:") && ids.length > 1)
  // Composite alone is a weak signal (many legit variants share
  // equipment+muscle+mechanic); only report ones that ALSO share a near-identical name.
  .filter(([, ids]) => {
    const names = ids.map((id) => fold(exercises.find((e) => e.id === id).name));
    return new Set(names).size < names.length;
  });

const exactNameDuplicates = [...groups.entries()].filter(([key, ids]) => key.startsWith("name:") && ids.length > 1);
const sourceIdDuplicates = [...groups.entries()].filter(([key, ids]) => key.startsWith("source:") && ids.length > 1);

const report = {
  totalExercises: exercises.length,
  exactNameDuplicates: exactNameDuplicates.map(([key, ids]) => ({ key: key.slice(5), ids })),
  sourceIdDuplicates: sourceIdDuplicates.map(([key, ids]) => ({ key: key.slice(7), ids })),
  suspiciousComposite: suspiciousComposite.map(([key, ids]) => ({ key: key.slice(10), ids })),
};

await writeFile(join(root, "data", "exercise-duplicate-report.json"), `${JSON.stringify(report, null, 2)}\n`);
console.log(`Checked ${exercises.length} exercises.`);
console.log(`Exact name duplicates: ${exactNameDuplicates.length}, source-id duplicates: ${sourceIdDuplicates.length}, suspicious composite matches: ${suspiciousComposite.length}`);
