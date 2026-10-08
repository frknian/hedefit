import type { NutritionPersonalization } from "./entitlements.ts";

/**
 * Beslenme kişiselleştirmesi: antrenman yüküne, toparlanmaya ve beslenme tercihine göre GENEL ipuçları.
 * Tamamen deterministik; model çağırmaz. Kurallar:
 *  - Kalori kısıtlaması, tıbbi diyet, hormonal iddia, takviye dozu YOK; hiçbir ipucu "daha az ye" demez.
 *  - Döngü bilgisi yalnızca çağıran izin ve katmanı doğrulamışsa gelir; ipucu "bazı kişiler" dilinde, kesin değildir.
 *  - Katmana göre: basic → genel; training_load → + antrenman günü/toparlanma; full → + tercihe özel kaynaklar ve (izinliyse) döngü.
 */
export type DietPreference = "standard" | "vegetarian" | "vegan" | "pescatarian";
export type NutritionTip = { id: string; tier: NutritionPersonalization; title: string; body: string };

export type NutritionWellnessInput = {
  tier: NutritionPersonalization;
  locale: "tr" | "en";
  diet: DietPreference;
  training: { workedOutToday: boolean; level?: "good" | "moderate" | "low" | "recovery"; sessionKind?: "pilates_today" | "low_impact_recovery" | "posture_mobility"; minutes?: number };
  /** Yalnızca izin + katman doğrulandıysa verilir. */
  cycle?: { periodLikely: boolean; preMenstrualWindow: boolean } | null;
};

export type NutritionWellnessResult = {
  tier: NutritionPersonalization;
  tips: NutritionTip[];
  /** Hedefte KÜÇÜK ve yalnızca artı yönlü öneri (g). Kalori hedefi hiçbir zaman düşürülmez. */
  proteinBonusGrams: number;
  /** Üst katmanda açılacak ipucu sayısı (kilit göstergesi için); içerik sızdırılmaz. */
  lockedTipCount: number;
};

type Text = { tr: string; en: string };
type Rule = { id: string; tier: NutritionPersonalization; when: (input: NutritionWellnessInput) => boolean; title: Text; body: Text };

const PROTEIN_SOURCES: Record<DietPreference, Text> = {
  standard: { tr: "yoğurt, yumurta, tavuk, balık ya da baklagil", en: "yogurt, eggs, chicken, fish or legumes" },
  vegetarian: { tr: "yoğurt, yumurta, peynir, mercimek ya da nohut", en: "yogurt, eggs, cheese, lentils or chickpeas" },
  vegan: { tr: "mercimek, nohut, tofu, tempeh ya da soya sütü", en: "lentils, chickpeas, tofu, tempeh or soy milk" },
  pescatarian: { tr: "balık, yoğurt, yumurta ya da baklagil", en: "fish, yogurt, eggs or legumes" },
};

const IRON_SOURCES: Record<DietPreference, Text> = {
  standard: { tr: "kırmızı et, mercimek, ıspanak ya da kuru baklagil", en: "red meat, lentils, spinach or dried legumes" },
  vegetarian: { tr: "mercimek, ıspanak, kuru baklagil ya da tam tahıl", en: "lentils, spinach, dried legumes or whole grains" },
  vegan: { tr: "mercimek, nohut, ıspanak, kuru baklagil ya da tam tahıl", en: "lentils, chickpeas, spinach, dried legumes or whole grains" },
  pescatarian: { tr: "balık, mercimek, ıspanak ya da kuru baklagil", en: "fish, lentils, spinach or dried legumes" },
};

const RULES: Rule[] = [
  { id: "basic-hydration", tier: "basic", when: () => true,
    title: { tr: "Gün içine yay", en: "Spread it through the day" },
    body: { tr: "Suyu ve proteini gün içine yaymak çoğu kişi için iyi gelir. Susuzluk ve açlık hissini dinle.", en: "Spreading water and protein across the day works well for most people. Listen to your thirst and hunger." } },
  { id: "basic-balanced-plate", tier: "basic", when: (i) => !i.training.workedOutToday,
    title: { tr: "Dengeli tabak", en: "A balanced plate" },
    body: { tr: "Her öğünde bir protein kaynağı, bir sebze ya da meyve ve ihtiyacına uygun bir karbonhidrat bulundurmak iyi bir başlangıçtır.", en: "A protein source, a vegetable or fruit and a carb that suits you at each meal is a good starting point." } },
  { id: "load-post-workout", tier: "training_load", when: (i) => i.training.workedOutToday && i.training.sessionKind !== "posture_mobility",
    title: { tr: "Antrenman sonrası", en: "After your workout" },
    body: { tr: "Antrenmandan sonraki öğününde protein ve biraz karbonhidrat bulundurmak toparlanmaya destek olur. Acıkma düzeyine göre porsiyonu ayarla.", en: "Including protein and some carbs in the meal after your workout supports recovery. Adjust the portion to your hunger." } },
  { id: "load-recovery-day", tier: "training_load", when: (i) => i.training.level === "low" || i.training.level === "recovery" || i.training.sessionKind === "low_impact_recovery",
    title: { tr: "Toparlanma günü", en: "Recovery day" },
    body: { tr: "Bugün hafif bir gün; yemeği kısmana gerek yok. Düzenli öğünler ve yeterli sıvı toparlanmana yardımcı olur.", en: "Today is a lighter day — no need to eat less. Regular meals and enough fluids help your recovery." } },
  { id: "load-short-session", tier: "training_load", when: (i) => i.training.workedOutToday && (i.training.minutes ?? 99) <= 25,
    title: { tr: "Kısa seans", en: "Short session" },
    body: { tr: "Kısa bir seansın ardından büyük bir ek öğüne gerek yoktur; normal öğününü planına göre ye.", en: "A short session doesn't call for a big extra meal; eat your normal meal as planned." } },
  { id: "full-protein-sources", tier: "full", when: (i) => i.training.workedOutToday,
    title: { tr: "Tercihine uygun protein", en: "Protein that fits you" },
    body: { tr: "Beslenme tercihine uygun protein kaynakları: {sources}.", en: "Protein sources that fit your eating style: {sources}." } },
  { id: "full-cycle-iron", tier: "full", when: (i) => i.cycle?.periodLikely === true,
    title: { tr: "Dönemde besin seçimi", en: "Food choices around your period" },
    body: { tr: "Bazı kişiler adet günlerinde demir içeren besinleri öne çıkarmayı sever: {iron}. C vitamini kaynaklarıyla birlikte almak emilime yardımcı olabilir. Bu tıbbi bir tavsiye değildir; endişen varsa bir hekime danış.", en: "Some people like to include iron-rich foods around their period: {iron}. Pairing them with vitamin C sources may help absorption. This is not medical advice; if you have concerns, talk to a doctor." } },
  { id: "full-cycle-premenstrual", tier: "full", when: (i) => i.cycle?.preMenstrualWindow === true && i.cycle?.periodLikely !== true,
    title: { tr: "Dönem öncesi", en: "Before your period" },
    body: { tr: "Bazı kişiler dönem öncesinde iştah ve enerji dalgalanması hisseder. Düzenli öğünler ve lifli besinler (tam tahıl, sebze, baklagil) iyi gelebilir; kendini suçlama.", en: "Some people notice appetite and energy swings before their period. Regular meals and fibre-rich foods (whole grains, vegetables, legumes) may help — and there's nothing to feel guilty about." } },
];

const TIER_RANK: Record<NutritionPersonalization, number> = { basic: 0, training_load: 1, full: 2 };

function render(text: Text, locale: "tr" | "en", input: NutritionWellnessInput) {
  return text[locale].replace("{sources}", PROTEIN_SOURCES[input.diet][locale]).replace("{iron}", IRON_SOURCES[input.diet][locale]);
}

export function buildNutritionWellness(input: NutritionWellnessInput): NutritionWellnessResult {
  const matching = RULES.filter((rule) => rule.when(input));
  // Döngü ipuçları (yalnızca izinli Premium'da eşleşir) listenin başına alınır; genel ipuçları tavan nedeniyle onu dışarıda bırakmasın.
  const priority = (rule: Rule) => (rule.id.startsWith("full-cycle") ? 0 : 1);
  const visible = matching.filter((rule) => TIER_RANK[rule.tier] <= TIER_RANK[input.tier]).sort((a, b) => priority(a) - priority(b));
  const tips = visible.slice(0, 5).map((rule) => ({ id: rule.id, tier: rule.tier, title: rule.title[input.locale], body: render(rule.body, input.locale, input) }));
  // Küçük, yalnızca artı yönlü protein önerisi: antrenman günü (training_load+) ve zorlu bir toparlanma günü değilse.
  const bonus = TIER_RANK[input.tier] >= 1 && input.training.workedOutToday && input.training.level !== "recovery" ? 10 : 0;
  return { tier: input.tier, tips, proteinBonusGrams: bonus, lockedTipCount: matching.length - visible.length };
}

export function normalizeDiet(value: unknown): DietPreference {
  const text = typeof value === "string" ? value.trim().toLowerCase() : "";
  if (["vejetaryen", "vegetarian"].includes(text)) return "vegetarian";
  if (text === "vegan") return "vegan";
  if (["pesketaryen", "pescatarian", "pescetarian"].includes(text)) return "pescatarian";
  return "standard";
}
