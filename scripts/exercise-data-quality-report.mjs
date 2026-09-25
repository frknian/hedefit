// Produces the migration's data-quality report: import counts, media
// coverage, facet distributions, duplicate/migration-map summaries, and a
// list of exercises missing metadata worth a human look.
//
// Usage: node scripts/exercise-data-quality-report.mjs

import { readFile, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { fileURLToPath } from "node:url";

const root = fileURLToPath(new URL("..", import.meta.url));
const readJson = (path) => readFile(join(root, ...path), "utf8").then(JSON.parse).catch(() => null);

const exercises = await readJson(["data", "exercises.json"]) || [];
const legacy = await readJson(["data", "legacy-exercises.json"]) || [];
const migrationMap = await readJson(["data", "exercise_migration_map.json"]) || [];
const duplicateReport = await readJson(["data", "exercise-duplicate-report.json"]);

function countBy(list, keyFn) {
  const counts = {};
  for (const item of list) {
    const key = keyFn(item);
    const keys = Array.isArray(key) ? key : [key];
    for (const k of keys) counts[k ?? "(none)"] = (counts[k ?? "(none)"] || 0) + 1;
  }
  return counts;
}

const missingMetadata = exercises.filter((exercise) =>
  !exercise.instructions?.length
  || !exercise.primaryMuscles?.length
  || exercise.mediaStatus !== "complete"
).map((exercise) => ({
  id: exercise.id,
  missingInstructions: !exercise.instructions?.length,
  missingPrimaryMuscles: !exercise.primaryMuscles?.length,
  mediaStatus: exercise.mediaStatus,
}));

const migrationSummary = migrationMap.reduce((acc, row) => {
  acc[row.match_type] = (acc[row.match_type] || 0) + 1;
  return acc;
}, {});

const report = {
  generatedAt: new Date().toISOString(),
  totalImported: exercises.length,
  activeExercises: exercises.filter((e) => e.isActive !== false).length,
  legacyFallbackPool: legacy.length,
  media: countBy(exercises, (e) => e.mediaStatus || "unknown"),
  byEquipment: countBy(exercises, (e) => e.equipment || "bodyweight"),
  byDifficulty: countBy(exercises, (e) => e.level),
  byBodyPart: countBy(exercises, (e) => e.bodyPart || "unknown"),
  byCategory: countBy(exercises, (e) => e.category),
  byGoalCompatibility: countBy(exercises, (e) => e.goalCompatibility || []),
  byEnvironment: countBy(exercises, (e) => e.environment || []),
  duplicates: duplicateReport ? {
    exactNameDuplicates: duplicateReport.exactNameDuplicates.length,
    sourceIdDuplicates: duplicateReport.sourceIdDuplicates.length,
    suspiciousComposite: duplicateReport.suspiciousComposite.length,
  } : "run scripts/detect-exercise-duplicates.mjs first",
  legacyMigration: {
    totalLegacyExercises: legacy.length,
    ...migrationSummary,
    mappedTotal: migrationMap.filter((row) => row.new_exercise_id).length,
    unmapped: migrationMap.filter((row) => !row.new_exercise_id).length,
  },
  missingMetadataCount: missingMetadata.length,
  missingMetadataSample: missingMetadata.slice(0, 25),
};

await writeFile(join(root, "data", "exercise-data-quality-report.json"), `${JSON.stringify(report, null, 2)}\n`);

const lines = [
  "# Hedefit Egzersiz Veritabanı — Data Quality Raporu",
  "",
  `Oluşturulma: ${report.generatedAt}`,
  "",
  "## Özet",
  `- Toplam içe aktarılan (RepDB): ${report.totalImported}`,
  `- Aktif egzersiz: ${report.activeExercises}`,
  `- Legacy fallback havuzu (silinmedi, yalnız eski loglar için çözülebilir): ${report.legacyFallbackPool}`,
  `- Eksik metadata'sı olan egzersiz: ${report.missingMetadataCount}`,
  "",
  "## Görsel Durumu",
  ...Object.entries(report.media).map(([k, v]) => `- ${k}: ${v}`),
  "",
  "## Zorluk Dağılımı",
  ...Object.entries(report.byDifficulty).map(([k, v]) => `- ${k}: ${v}`),
  "",
  "## Vücut Bölgesi Dağılımı",
  ...Object.entries(report.byBodyPart).map(([k, v]) => `- ${k}: ${v}`),
  "",
  "## Hedef Uyumluluğu Dağılımı",
  ...Object.entries(report.byGoalCompatibility).map(([k, v]) => `- ${k}: ${v}`),
  "",
  "## Ortam Dağılımı",
  ...Object.entries(report.byEnvironment).map(([k, v]) => `- ${k}: ${v}`),
  "",
  "## Duplicate Kontrolü",
  typeof report.duplicates === "string" ? report.duplicates : Object.entries(report.duplicates).map(([k, v]) => `- ${k}: ${v}`).join("\n"),
  "",
  "## Legacy Migrasyon Eşleşmesi (data/exercise_migration_map.json)",
  `- Toplam legacy egzersiz: ${report.legacyMigration.totalLegacyExercises}`,
  `- Eşleşen: ${report.legacyMigration.mappedTotal}`,
  `- Eşleşmeyen (no_match, legacy havuzda salt-okunur kalır): ${report.legacyMigration.unmapped}`,
  `- exact: ${report.legacyMigration.exact || 0}, normalized_name: ${report.legacyMigration.normalized_name || 0}, manual: ${report.legacyMigration.manual || 0}`,
].join("\n");

await writeFile(join(root, "data", "exercise-data-quality-report.md"), `${lines}\n`);
console.log(lines);
