#!/usr/bin/env node
// Hedefit'e özel Pilates / Mobility / Barre / Low Impact / Recovery hareketlerini üretir:
//   data/exercises-wellness.json  +  public/exercise-images/<id>/{start,peak}.webp
// Görseller scripts/wellness/figure.mjs ile ÜRETİLİR (üçüncü taraf içerik yok); lisans: Hedefit'e ait.
// Kullanım: node scripts/build-wellness-exercises.mjs   (kontak sayfası: node scripts/wellness/preview.mjs <modalite>)
import { mkdirSync, writeFileSync } from "node:fs";
import { createRequire } from "node:module";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { HEDEFIT_ORIGINAL_ASSET, MODALITIES, MODALITY_SUBCATEGORIES, validateAsset } from "../lib/exercise-modality.ts";
import { renderFrame } from "./wellness/figure.mjs";
import { pilates } from "./wellness/pilates.mjs";
import { mobility } from "./wellness/mobility.mjs";
import { barre } from "./wellness/barre.mjs";
import { lowImpact } from "./wellness/low-impact.mjs";
import { recovery } from "./wellness/recovery.mjs";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const sharp = createRequire(join(root, "package.json"))("sharp");
const ENTRIES = [...pilates, ...mobility, ...barre, ...lowImpact, ...recovery];

// RepDB kas etiketleri (data/exercises.json içindeki 27 değer).
const MUSCLES = new Set(["gluteus_maximus", "quadriceps", "pectoralis_major", "latissimus_dorsi", "anterior_deltoid", "hamstrings", "rectus_abdominis", "triceps_brachii", "erector_spinae", "lateral_deltoid", "trapezius", "biceps_brachii", "obliques", "rhomboids", "hip_flexors", "gluteus_medius", "gastrocnemius", "posterior_deltoid", "adductors", "forearm_flexors", "transverse_abdominis", "brachialis", "soleus", "forearm_extensors", "abductors", "brachioradialis", "quadratus_lumborum"]);
const GOALS = new Set(["general_fitness", "muscle_gain", "strength", "mobility", "fat_loss", "endurance"]);
const problems = [];
const ids = new Set();

const records = ENTRIES.map((entry) => {
  const fail = (message) => problems.push(`${entry.id}: ${message}`);
  if (ids.has(entry.id)) fail("tekrarlanan id"); ids.add(entry.id);
  if (!/^wl-[a-z0-9-]+$/.test(entry.id)) fail("id biçimi");
  for (const modality of entry.modality) {
    if (!MODALITIES.includes(modality)) fail(`bilinmeyen modalite ${modality}`);
    else if (!entry.sub.some((sub) => MODALITY_SUBCATEGORIES[modality].includes(sub))) fail(`${modality} için geçerli alt kategori yok`);
  }
  for (const sub of entry.sub) if (!entry.modality.some((modality) => MODALITY_SUBCATEGORIES[modality].includes(sub))) fail(`alt kategori ${sub} hiçbir modaliteye ait değil`);
  for (const muscle of [...entry.primary, ...entry.secondary]) if (!MUSCLES.has(muscle)) fail(`bilinmeyen kas ${muscle}`);
  for (const goal of entry.goals) if (!GOALS.has(goal)) fail(`bilinmeyen hedef ${goal}`);
  if (!["beginner", "intermediate", "advanced"].includes(entry.level)) fail("seviye");
  if (entry.instructions.length < 3 || entry.instructions.length !== entry.instructionsTr.length) fail("EN/TR talimat sayısı eşit ve ≥3 olmalı");
  if (entry.tips.length !== entry.tipsTr.length) fail("EN/TR ipucu sayısı eşit olmalı");
  if (!entry.nameTr || !entry.descTr || !entry.desc) fail("ad/açıklama eksik");
  if (entry.frames.length !== 2) fail("tam 2 kare gerekir");
  const assetError = validateAsset(HEDEFIT_ORIGINAL_ASSET);
  if (assetError) fail(assetError);
  return {
    id: entry.id,
    name: entry.name,
    nameTr: entry.nameTr,
    force: entry.force,
    level: entry.level,
    mechanic: entry.mechanic,
    equipment: entry.equipment,
    requiredEquipment: [[]],
    primaryMuscles: entry.primary,
    secondaryMuscles: entry.secondary,
    instructions: entry.instructions,
    instructionsTr: entry.instructionsTr,
    category: entry.category ?? "strength",
    images: [`/exercise-images/${entry.id}/start.webp`, `/exercise-images/${entry.id}/peak.webp`],
    source: "wellness",
    sourceExerciseId: entry.id,
    descriptionEn: entry.desc,
    descriptionTr: entry.descTr,
    tipsEn: entry.tips,
    tipsTr: entry.tipsTr,
    bodyPart: entry.bodyPart,
    goalCompatibility: entry.goals,
    environment: ["home", "outdoor", "gym"],
    laterality: "bilateral",
    isBodyweight: true,
    metValue: entry.met,
    imageStart: `/exercise-images/${entry.id}/start.webp`,
    imageEnd: `/exercise-images/${entry.id}/peak.webp`,
    mediaStatus: "complete",
    isActive: true,
    modalities: entry.modality,
    subcategories: entry.sub,
    impact: entry.impact,
    asset: HEDEFIT_ORIGINAL_ASSET,
  };
});

if (problems.length) { console.error(problems.join("\n")); process.exit(1); }

for (const entry of ENTRIES) {
  const dir = join(root, "public/exercise-images", entry.id);
  mkdirSync(dir, { recursive: true });
  for (const [index, name] of ["start", "peak"].entries()) {
    await sharp(Buffer.from(renderFrame(entry, index, 512))).webp({ quality: 84 }).toFile(join(dir, `${name}.webp`));
  }
}
writeFileSync(join(root, "data/exercises-wellness.json"), `${JSON.stringify(records, null, 2)}\n`);
const counts = Object.fromEntries(MODALITIES.map((modality) => [modality, records.filter((record) => record.modalities.includes(modality)).length]));
console.log(`${records.length} hareket yazıldı`, counts);
