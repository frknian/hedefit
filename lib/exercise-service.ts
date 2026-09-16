import exerciseData from "../data/exercises.json" with { type: "json" };
import legacyExerciseData from "../data/legacy-exercises.json" with { type: "json" };
import { translateExerciseLabel, translateExerciseName } from "./exercise-translations.ts";
import type { AIExerciseContext, Exercise, ExerciseFilters } from "@/types/exercise";

const safeText = (value: unknown, fallback = "", maxLength = 300) => typeof value === "string" ? value.trim().slice(0, maxLength) : fallback;
const safeList = (value: unknown, limit = 20, maxLength = 300) => Array.isArray(value) ? value.map((item) => safeText(item, "", maxLength)).filter(Boolean).slice(0, limit) : [];
const safeImage = (value: unknown) => typeof value === "string" && /^\/exercise-images\/[a-zA-Z0-9_-]+\/[a-zA-Z0-9_.-]+$/.test(value) ? value : null;
const fold = (value: string) => value.toLocaleLowerCase("en-US").normalize("NFD").replace(/[\u0300-\u036f]/g, "").trim();

export function normalizeExercise(value: unknown): Exercise | null {
  if (!value || typeof value !== "object") return null;
  const item = value as Record<string, unknown>;
  const id = safeText(item.id).replace(/[^a-zA-Z0-9_-]/g, "");
  const name = safeText(item.name);
  if (!id || !name) return null;
  return {
    id,
    name,
    force: safeText(item.force) || null,
    level: safeText(item.level, "beginner"),
    mechanic: safeText(item.mechanic) || null,
    equipment: safeText(item.equipment) || null,
    primaryMuscles: safeList(item.primaryMuscles),
    secondaryMuscles: safeList(item.secondaryMuscles),
    instructions: safeList(item.instructions, 12, 1200),
    category: safeText(item.category, "strength"),
    images: safeList(item.images, 4).map(safeImage).filter((image): image is string => Boolean(image)),
    source: item.source === "legacy" ? "legacy" : "repdb",
    sourceExerciseId: safeText(item.sourceExerciseId) || undefined,
    bodyPart: safeText(item.bodyPart) || undefined,
    goalCompatibility: safeList(item.goalCompatibility, 10, 40),
    environment: safeList(item.environment, 5, 20),
    laterality: item.laterality === "unilateral" ? "unilateral" : "bilateral",
    isBodyweight: Boolean(item.isBodyweight),
    metValue: typeof item.metValue === "number" ? item.metValue : undefined,
    imageStart: safeImage(item.imageStart),
    imageEnd: safeImage(item.imageEnd),
    mediaStatus: item.mediaStatus === "partial" || item.mediaStatus === "missing" ? item.mediaStatus : "complete",
    isActive: item.isActive !== false,
  };
}

const importedExercises = (exerciseData as unknown[]).map(normalizeExercise).filter((exercise): exercise is Exercise => Boolean(exercise));

/**
 * Legacy (free-exercise-db) catalog, replaced as the primary source by RepDB
 * (see scripts/import-repdb.mjs). Kept resolvable ONLY through `getExerciseById`
 * so historical `workout_exercise_logs` rows (which store the id as free text,
 * no FK \u2014 see db/supabase-schema.sql) never break; never surfaced in catalogs,
 * search, or plan generation.
 */
const legacyExercises: Exercise[] = (legacyExerciseData as unknown[])
  .map((raw) => {
    const normalized = normalizeExercise(raw);
    return normalized ? { ...normalized, source: "legacy" as const, isActive: false } : null;
  })
  .filter((exercise): exercise is NonNullable<typeof exercise> => exercise !== null);

const exercises = Object.freeze(importedExercises);
const exerciseById = new Map(importedExercises.map((exercise) => [exercise.id, exercise]));
const legacyExerciseById = new Map(legacyExercises.map((exercise) => [exercise.id, exercise]));

export const getAllExercises = () => [...exercises];
export function getExerciseById(id: string): Exercise | null {
  const key = id.replace(/[^a-zA-Z0-9_-]/g, "");
  return exerciseById.get(key) ?? legacyExerciseById.get(key) ?? null;
}

/**
 * Aranabilir metin ÖNCEDEN hesaplanır.
 *
 * Eskiden her tuş vuruşunda 873 hareketin her biri için ad, kas grupları,
 * ekipman ve bunların Türkçe karşılıkları yeniden birleştirilip
 * normalize ediliyordu: aramanın her adımında on binlerce geçici dize.
 * Katalog uygulama ömrü boyunca değişmediği için bu iş bir kez yapılır ve
 * filtreleme tek bir `includes` çağrısına iner.
 */
const searchHaystacks = exercises.map((exercise) => fold([
  exercise.name,
  translateExerciseName(exercise.name),
  ...exercise.primaryMuscles,
  ...exercise.secondaryMuscles,
  exercise.equipment || "",
  ...exercise.primaryMuscles.map((item) => translateExerciseLabel(item)),
  ...exercise.secondaryMuscles.map((item) => translateExerciseLabel(item)),
  translateExerciseLabel(exercise.equipment),
].join(" ")));

/** Eşleşen hareketlerin katalog sırasındaki indeksleri. */
export function searchExerciseIndexes(query: string): number[] {
  const normalizedQuery = fold(query).slice(0, 100);
  const matches: number[] = [];
  if (!normalizedQuery) return matches;
  for (let index = 0; index < searchHaystacks.length; index += 1) {
    if (searchHaystacks[index].includes(normalizedQuery)) matches.push(index);
  }
  return matches;
}

export const getExerciseByIndex = (index: number) => exercises[index] ?? null;

export function searchExercises(query: string) {
  const normalizedQuery = fold(query).slice(0, 100);
  if (!normalizedQuery) return getAllExercises();
  return exercises.filter((_, index) => searchHaystacks[index].includes(normalizedQuery));
}

export function filterExercises(filters: ExerciseFilters = {}) {
  const search = fold(filters.search || "").slice(0, 100);
  const muscleTargets = expandMuscleFilter(filters.muscle || "");
  const equipment = fold(filters.equipment || "");
  const level = fold(filters.level || "");
  const category = fold(filters.category || "");
  return exercises.filter((exercise, index) => {
    return (!search || searchHaystacks[index].includes(search))
      && (!muscleTargets.length || [...exercise.primaryMuscles, ...exercise.secondaryMuscles].some((item) => muscleTargets.includes(fold(item))))
      && (!equipment || fold(exercise.equipment || "none") === equipment)
      && (!level || fold(exercise.level) === level)
      && (!category || fold(exercise.category) === category);
  });
}

// RepDB uses a fixed 30-value muscle-slug vocabulary (see
// scripts/import-repdb.mjs / lib/training/exercise-metadata.ts's
// REPDB_MUSCLE_MAP). UI region + single-muscle labels expand into it here so
// existing callers (search filters, lib/ai/exercise-atlas.ts) keep working
// unchanged against the new catalog.
const muscleGroups: Record<string, string[]> = {
  arms: ["biceps_brachii", "brachialis", "brachioradialis", "triceps_brachii", "forearms", "forearm_flexors", "forearm_extensors"],
  back: ["latissimus_dorsi", "trapezius", "rhomboids", "erector_spinae", "quadratus_lumborum"],
  core: ["rectus_abdominis", "obliques", "transverse_abdominis"],
  hips: ["gluteus_maximus", "gluteus_medius", "adductors", "abductors", "hip_flexors"],
  legs: ["quadriceps", "hamstrings", "gastrocnemius", "soleus", "adductors"],
  chest: ["pectoralis_major", "serratus_anterior"],
  shoulders: ["anterior_deltoid", "lateral_deltoid", "posterior_deltoid", "supraspinatus"],
  biceps: ["biceps_brachii", "brachialis", "brachioradialis"],
  triceps: ["triceps_brachii"],
  forearms: ["forearms", "forearm_flexors", "forearm_extensors"],
  calves: ["gastrocnemius", "soleus"],
  glutes: ["gluteus_maximus", "gluteus_medius"],
  abdominals: ["rectus_abdominis", "obliques", "transverse_abdominis"],
  lats: ["latissimus_dorsi"],
  traps: ["trapezius"],
  neck: ["trapezius"],
};

/** Expands a UI region (for example `back`) into the source catalog muscles. */
export function expandMuscleFilter(muscle: string): string[] {
  const normalized = fold(muscle);
  if (!normalized) return [];
  return muscleGroups[normalized] || [normalized];
}

export const getExerciseCatalogStats = () => ({
  imported: importedExercises.length,
  visible: exercises.length,
  legacyFallback: legacyExercises.length,
});

export const getExercisesByMuscle = (muscle: string) => filterExercises({ muscle });
export const getExercisesByEquipment = (equipment: string) => filterExercises({ equipment });
export const getExercisesByLevel = (level: string) => filterExercises({ level });

/**
 * Plan üretimine gönderilen katalog.
 *
 * Yalnız modelin seçim yaparken gerçekten kullandığı alanlar gider.
 * secondaryMuscles ve category isteme ~%40 fazladan token ekliyordu; katalog
 * zaten istemin en büyük parçası ve model bir akıl yürütme modeli olduğu için
 * bu, üretimi zaman aşımına kadar yavaşlatıyordu.
 */
export function getExercisesForAI(filters: ExerciseFilters = {}): AIExerciseContext[] {
  return filterExercises(filters).map(({ id, name, level, equipment, primaryMuscles }) => ({ id, name, level, equipment: equipment || undefined, primaryMuscles }));
}

// Her seçenek listesi, kendi boyutu dışındaki aktif filtrelere göre daraltılır.
// Böylece bir kas grubu seçildiğinde o kasta hareketi bulunmayan ekipman, seviye
// ve kategori seçenekleri listede kalmaz; kullanıcı boş sonuç veren bir filtre
// kombinasyonunu seçemez.
export function getExerciseFilterOptions(filters: ExerciseFilters = {}) {
  const unique = (values: string[]) => [...new Set(values.filter(Boolean))].sort((a, b) => a.localeCompare(b));
  const excluding = (dimension: keyof ExerciseFilters) => filterExercises({ ...filters, [dimension]: undefined });
  return {
    muscles: unique(excluding("muscle").flatMap((exercise) => [...exercise.primaryMuscles, ...exercise.secondaryMuscles])),
    equipment: unique(excluding("equipment").map((exercise) => exercise.equipment || "none")),
    levels: unique(excluding("level").map((exercise) => exercise.level)),
    categories: unique(excluding("category").map((exercise) => exercise.category)),
  };
}

/**
 * Bir filtre boyutundaki her seçeneğin kaç harekete karşılık geldiği.
 *
 * Kütüphane 873 hareket içeriyor ve seçenekler eskiden isimsiz bir açılır
 * listede duruyordu: kullanıcı "sırt" seçmeden kaç hareket çıkacağını
 * bilmiyor, seçtikten sonra boş sonuç ekranıyla karşılaşabiliyordu. Sayı
 * seçeneğin yanında görünürse seçim körlemesine yapılmaz.
 *
 * Sayılar DİĞER filtreler uygulanmış hâlde hesaplanır: "dambıl" seçiliyken
 * "sırt" rozeti, dambılla yapılan sırt hareketi sayısını gösterir.
 */
export function countExercisesByFacet(
  filters: ExerciseFilters,
  dimension: "muscle" | "equipment" | "level" | "category",
): Record<string, number> {
  const options = getExerciseFilterOptions(filters);
  const values = dimension === "muscle" ? options.muscles
    : dimension === "equipment" ? options.equipment
    : dimension === "level" ? options.levels
    : options.categories;
  const counts: Record<string, number> = {};
  for (const value of values) counts[value] = filterExercises({ ...filters, [dimension]: value }).length;
  return counts;
}

// RepDB'nin ~55 ekipman etiketinin (bkz. scripts/import-repdb.mjs), kullanıcının
// serbest metinle yazabileceği ekipman karşılıkları. Salon dışındaki bir
// kullanıcıya barbell/cable/machine göndermenin anlamı yok: model onları
// seçemez, ama tokenini yer.
const EQUIPMENT_TAG_SYNONYMS: Record<string, string[]> = {
  dumbbell: ["dambıl", "dambil", "dumbbell", "dumbell"],
  kettlebell: ["kettlebell"],
  resistance_band: ["band", "bant", "lastik", "direnç"],
  loop_band: ["band", "bant", "lastik", "direnç"],
  stability_ball: ["egzersiz topu", "pilates topu", "swiss ball", "denge topu", "mat"],
  slam_ball: ["medicine ball", "sağlık topu", "slam ball"],
  ez_bar: ["ez bar", "e-z bar", "curl bar"],
  barbell: ["barbell", "halter", "olimpik bar", "salon"],
  trap_bar: ["barbell", "halter", "salon"],
  plates: ["barbell", "halter", "salon", "ağırlık diski"],
  cable: ["salon", "makine", "kablo"],
  smith_machine: ["salon", "makine"],
  pull_up_bar: ["barfiks", "pull-up bar", "pull up bar"],
  flat_bench: ["sehpa", "bench"],
  jump_rope: ["ip atlama", "jump rope", "atlama ipi"],
};

/** Ekipman gerektirmeyen etiketler; herkes yapabilir. */
const BODYWEIGHT_TAGS = new Set(["", "bodyweight"]);

// Bazı veri satırları ekipmansız etiketlense de hareket adı gerçek bir
// ekipman gerektirir. İsimdeki bu gizli gereksinimler ayrıca doğrulanır.
const HIDDEN_EQUIPMENT_RULES: Array<{ pattern: RegExp; owned: RegExp }> = [
  { pattern: /\b(?:resistance |exercise )?bands?\b/i, owned: /band|bant|lastik/i },
  { pattern: /\b(?:pull[ -]?ups?|chin[ -]?ups?)\b/i, owned: /barfiks|pull[ -]?up bar/i },
  { pattern: /\b(?:barbells?|olympic bar)\b/i, owned: /barbell|halter|olimpik bar|salon/i },
  { pattern: /\b(?:kettlebells?)\b/i, owned: /kettlebell/i },
  { pattern: /\bmedicine ball\b/i, owned: /medicine ball|sağlık topu/i },
  { pattern: /\b(?:exercise|swiss|stability) ball\b/i, owned: /egzersiz topu|pilates topu|swiss ball|stability ball/i },
  { pattern: /\b(?:cable|machine|smith)\b/i, owned: /kablo|makine|smith|salon/i },
];

function hiddenEquipmentAvailable(name: string, owned: string, isGym: boolean) {
  if (isGym) return true;
  return HIDDEN_EQUIPMENT_RULES.every((rule) => !rule.pattern.test(name) || rule.owned.test(owned));
}

/**
 * Plan istemine giden hareket sayısının üst sınırı.
 *
 * ÖLÇÜM: geniş bir kataloğun (RepDB: 601 hareket) salon profilinde istemin
 * yalnız katalog kısmı on binlerce token oluyor. Sağlayıcının modeli akıl
 * yürüten bir model ve plan üretimi zaten 60 sn'lik pencerede zar zor
 * tamamlanıyor; kataloğun tamamını göndermek üretimi o pencerenin dışına
 * taşırdı. Sınır kütüphaneyi DEĞİL yalnız istemi bağlar: kullanıcı kataloğun
 * tamamını uygulamada görmeye devam eder.
 */
export const PROMPT_CATALOG_LIMIT = 240;

function groupBy<T>(items: T[], key: (item: T) => string): T[][] {
  const buckets = new Map<string, T[]>();
  for (const item of items) {
    const bucket = buckets.get(key(item));
    if (bucket) bucket.push(item);
    else buckets.set(key(item), [item]);
  }
  return [...buckets.values()];
}

/** Grupları sırayla dolaşarak tek listeye örer; baştaki grup listeyi kaplamaz. */
function interleave<T>(groups: T[][]): T[] {
  const woven: T[] = [];
  const deepest = groups.reduce((longest, group) => Math.max(longest, group.length), 0);
  for (let index = 0; index < deepest; index += 1) {
    for (const group of groups) {
      if (index < group.length) woven.push(group[index]);
    }
  }
  return woven;
}

/**
 * Kataloğu sınıra indirirken çeşitliliği korur.
 *
 * Düz `slice` alfabetik sıraya güvenir ve listeyi ilk kas grubuna boğardı; bu
 * yüzden önce kas grubu, sonra her grubun içinde ekipman bazında sırayla seçim
 * yapılır. Böylece sınır dolduğunda her kas grubu ve her ekipman türü listede
 * temsil edilmiş olur.
 */
function balanceForPrompt(exercises: AIExerciseContext[], limit = PROMPT_CATALOG_LIMIT) {
  if (exercises.length <= limit) return exercises;
  const byMuscle = groupBy(exercises, (exercise) => exercise.primaryMuscles[0] || "other");
  return interleave(byMuscle.map((group) => interleave(groupBy(group, (exercise) => exercise.equipment || "none")))).slice(0, limit);
}

/**
 * Plan istemine giden kataloğu kullanıcının GERÇEKTEN yapabileceklerine indirir.
 *
 * Ölçüldü: tam katalog istemi o kadar büyütüyordu ki üretim zaman aşımına
 * düşüyordu. Filtreleme hem istemi küçültür hem de plan kalitesini artırır —
 * model evdeki kullanıcıya lat pulldown öneremez. Ekipman elemesinden sonra
 * kalan liste ayrıca PROMPT_CATALOG_LIMIT ile sınırlanır.
 */
export function getExercisesForProfile(
  isGym: boolean,
  equipmentText: string,
  environmentText = "",
  trainingStyleText = "",
): AIExerciseContext[] {
  const owned = equipmentText.toLocaleLowerCase("tr-TR");
  // "Spor salonunda" ortamı, kullanıcının salondaki her ekipmana eriştiği
  // anlamına gelmez. Açıkça yalnız dambıl dediğinde ortam seçimi bu kısıtı
  // ezmemeli; tam salon erişimi ancak ekipman cevabı bunu söylüyorsa açılır.
  const restrictiveEquipment = /(?:sadece|yalnız|yalniz|only)\b/.test(owned);
  const fullGymAccess = isGym && !restrictiveEquipment && (owned.trim() === "" || /tam salon|salon ekipmanı|salon ekipmani|full (?:gym|equipment)|tüm ekipman|tum ekipman/.test(owned));
  const outdoorRunning = /açık hava|outdoor/.test(environmentText.toLocaleLowerCase("tr-TR"))
    && /koşu|run|jog/.test(trainingStyleText.toLocaleLowerCase("tr-TR"));
  const outdoorMovement = /\b(?:run(?:ning)?|jog(?:ging)?|sprint(?:s)?|walk(?:ing)?)\b/i;
  return balanceForPrompt(getExercisesForAI().filter((exercise) => {
    const tag = (exercise.equipment || "").toLocaleLowerCase("en-US");
    if (!hiddenEquipmentAvailable(exercise.name, owned, fullGymAccess)) return false;
    const equipmentAvailable = BODYWEIGHT_TAGS.has(tag)
      || fullGymAccess
      || (EQUIPMENT_TAG_SYNONYMS[tag]?.some((word) => owned.includes(word)) ?? false);
    if (!equipmentAvailable) return false;
    // AI kataloğu token tasarrufu için `category` taşımaz. Burada kategoriye
    // bakmak açık hava koşu profilini sessizce BOŞ kataloğa düşürüyordu.
    // Kelime sınırları da zorunlu: çıplak /run/ ifadesi "crunch"ı koşu sanır.
    if (outdoorRunning) return outdoorMovement.test(exercise.name);
    if (BODYWEIGHT_TAGS.has(tag)) return true;
    if (fullGymAccess) return true;
    const synonyms = EQUIPMENT_TAG_SYNONYMS[tag];
    return synonyms ? synonyms.some((word) => owned.includes(word)) : false;
  }));
}
