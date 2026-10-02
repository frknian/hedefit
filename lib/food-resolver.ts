/**
 * Ortak Besin Çözümleyici (Common Food Resolver).
 *
 * Kamera tespiti, serbest metin girişi ve arama ekranından gelen
 * tüm yiyecekleri standart Türk yemekleri veritabanına, porsiyon
 * ve varyant motoruna bağlar.
 */

import {
  TURKISH_FOOD_DATABASE,
  type TurkishFood,
  type FoodVariant,
  normalizeTurkishText,
} from "./turkish-food-database.ts";
import { matchDefaultFood, matchDefaultFoodLoose, type DefaultFood } from "./default-food-catalog.ts";
import { matchRawFood } from "./raw-food-equivalents.ts";

export interface ResolveFoodInput {
  text: string;
  quantity?: number;
  unit?: string;
  grams?: number;
}

export interface ResolvedFood {
  foodId: string;
  name: string;
  variantName?: string;
  quantity: number;
  unit: string;
  grams: number;
  calories: number;
  protein: number;
  carbohydrates: number;
  fat: number;
  fiber: number;
  sugar: number;
  sodiumMg: number;
  potassiumMg: number;
  calciumMg: number;
  ironMg: number;
  vitaminCMg: number;
  confidence: number;
  verified: boolean;
  /** generic_estimate = nothing matched; a category-level guess, NOT an AI result. */
  source: "turkish_database" | "default_catalog" | "generic_estimate";
  needsConfirmation: boolean;
  warning?: string;
  /** Stable machine-readable reason for `warning`, so clients can localise the message. */
  warningCode?: ResolvedFoodWarningCode;
}

export type ResolvedFoodWarningCode = "approximate_amount" | "cooked_assumed" | "not_in_catalogue";

function rounded(value: number, digits = 1): number {
  const factor = 10 ** digits;
  return Math.round(value * factor) / factor;
}

/**
 * Genel Türk mutfağı porsiyon standartları (yiyeceğe özel tanım yoksa fallback).
 */
const GENERIC_UNIT_GRAMS: Record<string, number> = Object.fromEntries(Object.entries({
  gram: 1, gr: 1, g: 1, ml: 1,
  porsiyon: 200, tabak: 250, kase: 250, kepçe: 100, dilim: 30, adet: 50, tane: 50, avuç: 30, bardak: 200,
  kaşık: 25, "yemek kaşığı": 25, "tatlı kaşığı": 10, "çay kaşığı": 5, "yemek kaşığı ": 25,
  dürüm: 220, yarım: 100, çeyrek: 50, kutu: 250, şişe: 250, fincan: 70,
  ölçü: 30, kadeh: 150, kare: 6, top: 50, paket: 50, küp: 4,
}).map(([unit, grams]) => [normalizeTurkishText(unit), grams]));

/**
 * Yiyecekten bağımsız ölçü birimleri: bir yemek kaşığı hangi yiyecek olursa olsun ~25 g'dır.
 * "adet", "dilim", "tabak", "kutu" gibi yiyeceğe bağlı birimler burada YOKTUR; onlar için yiyeceğin
 * kendi porsiyon tablosu ya da varsayılan porsiyonu kullanılır.
 */
const MEASURE_UNITS = new Set(["yemek kasigi", "tatli kasigi", "cay kasigi", "kasik", "olcu", "kadeh", "kare", "kup", "avuc", "fincan", "bardak"]);

/**
 * Bir yiyecek metnini ve miktarını analiz edip doğrulanmış besin değerlerine çözümler.
 */
export function resolveFood(input: ResolveFoodInput): ResolvedFood {
  const rawText = input.text.trim();
  const normText = normalizeTurkishText(rawText);

  let matchedFood: TurkishFood | null = null;
  let matchedVariant: FoodVariant | null = null;
  let confidence = 0.9;
  let needsConfirmation = false;
  let warning: string | undefined;
  let warningCode: ResolvedFoodWarningCode | undefined;

  // 0. ÇİĞ / KURU AĞIRLIK: "100 gram çiğ pirinç", "80 gram kuru makarna".
  // Pişirme ağırlığı değiştirir, enerjiyi değil; açık işaret yoksa pişmiş değer kullanılır.
  const rawFood = matchRawFood(rawText);
  if (rawFood) {
    const rawQty = input.quantity && input.quantity > 0 ? input.quantity : 1;
    const rawUnit = input.unit ? normalizeTurkishText(input.unit) : "";
    const rawGrams = input.grams && input.grams > 0
      ? input.grams
      : (rawUnit && (rawFood.portions[rawUnit] ?? GENERIC_UNIT_GRAMS[rawUnit]) ? (rawFood.portions[rawUnit] ?? GENERIC_UNIT_GRAMS[rawUnit]) * rawQty : rawFood.defaultGrams * rawQty);
    const grams = Math.max(1, Math.round(rawGrams));
    const ratio = grams / 100;
    return {
      foodId: rawFood.id,
      name: rawFood.name,
      quantity: rawQty,
      unit: input.unit || "gram",
      grams,
      calories: Math.round(rawFood.calories * ratio),
      protein: rounded(rawFood.protein * ratio),
      carbohydrates: rounded(rawFood.carbohydrates * ratio),
      fat: rounded(rawFood.fat * ratio),
      fiber: rounded(rawFood.fiber * ratio),
      sugar: 0, sodiumMg: 0, potassiumMg: 0, calciumMg: 0, ironMg: 0, vitaminCMg: 0,
      confidence: 0.85,
      verified: true,
      source: "default_catalog",
      needsConfirmation: false,
    };
  }

  // 1. PASS: Tam Ad veya Tam Alias Eşleşmesi (En yüksek öncelik)
  for (const food of TURKISH_FOOD_DATABASE) {
    if (food.variants) {
      for (const variant of food.variants) {
        const vNorm = normalizeTurkishText(variant.name);
        const vAliases = (variant.aliases || []).map(normalizeTurkishText);
        if (normText === vNorm || vAliases.includes(normText)) {
          matchedFood = food;
          matchedVariant = variant;
          break;
        }
      }
    }
    if (matchedFood) break;

    const fNorm = normalizeTurkishText(food.name);
    const fAliases = food.aliases.map(normalizeTurkishText);
    if (normText === fNorm || fAliases.includes(normText)) {
      matchedFood = food;
      break;
    }
  }

  // 2. PASS: Kelime Sınırlarıyla En Uzun Eşleşme (Word boundary search, longest first)
  if (!matchedFood) {
    // Tüm adayları uzunluğuna göre azalan sırada topla
    const candidates: Array<{ food: TurkishFood; variant?: FoodVariant; phrase: string }> = [];
    for (const food of TURKISH_FOOD_DATABASE) {
      candidates.push({ food, phrase: normalizeTurkishText(food.name) });
      for (const alias of food.aliases) {
        candidates.push({ food, phrase: normalizeTurkishText(alias) });
      }
      if (food.variants) {
        for (const v of food.variants) {
          candidates.push({ food, variant: v, phrase: normalizeTurkishText(v.name) });
          for (const va of v.aliases || []) {
            candidates.push({ food, variant: v, phrase: normalizeTurkishText(va) });
          }
        }
      }
    }
    candidates.sort((a, b) => b.phrase.length - a.phrase.length);

    for (const cand of candidates) {
      if (cand.phrase.length < 3) continue;
      // Kelime sınırları içinde tam kelime/öbek geçiyor mu?
      const escaped = cand.phrase.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
      const wordRegex = new RegExp(`(^|\\s)${escaped}(\\s|$)`, "i");
      if (wordRegex.test(normText)) {
        matchedFood = cand.food;
        if (cand.variant) matchedVariant = cand.variant;
        break;
      }
    }
  }

  // 3. PASS: Bulunan Yemekten Varyant Çıkarımı
  if (matchedFood && !matchedVariant && matchedFood.variants) {
    for (const variant of matchedFood.variants) {
      const keywords = [variant.name, variant.id, ...(variant.aliases || [])].map(normalizeTurkishText);
      if (keywords.some((kw) => {
        const escaped = kw.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
        return new RegExp(`(^|\\s)${escaped}(\\s|$)`, "i").test(normText);
      })) {
        matchedVariant = variant;
        break;
      }
    }
  }

  // Belirsizlik kuralları: "biraz", "az", "avuç"
  // Katlanmış (aksansız) metinde aranır: "avuç" gibi sözcüklerde JS'in \b'si Türkçe harflerde çalışmaz.
  const isAmbiguous = /(^|\s)(biraz|az|bir avuc|bir tabak|bolca|goz karari)(\s|$)/.test(normText);
  if (isAmbiguous) {
    needsConfirmation = true;
    confidence = Math.min(confidence, 0.7);
    warning = "Miktar yaklaşık olarak tahmin edilmiştir; kaydetmeden önce kontrol edebilirsin.";
    warningCode = "approximate_amount";
  }

  // 4. Gramaj ve Porsiyon Çözümlemesi
  const qty = input.quantity && input.quantity > 0 ? input.quantity : 1;
  const unitRaw = input.unit ? normalizeTurkishText(input.unit) : "";
  let calculatedGrams = 0;

  // Veritabanında yoksa temel katalog: kendi porsiyon tablosu (yağ kaşığı, ölçek, kutu…) vardır.
  const defaultFood = matchedFood ? null : (matchDefaultFood(rawText) ?? matchDefaultFoodLoose(rawText));

  /** "yarım", "çeyrek", "biraz"… gibi miktar sözcüklerinin çarpanı (açık gramaj verilmediyse). */
  const amountWordFactor = (grams: number): number => {
    if (normText.includes("yarim")) return grams * 0.5;
    if (normText.includes("ceyrek")) return grams * 0.25;
    if (normText.includes("bir bucuk") || normText.includes("1.5")) return grams * 1.5;
    if (/(^|\s)(biraz|az)(\s|$)/.test(normText)) return Math.round(grams * 0.65);
    if (/(^|\s)(bol|bolca)(\s|$)/.test(normText)) return Math.round(grams * 1.35);
    return grams;
  };

  // Adında miktar geçen varyantlar ("Yarım Ekmek Tavuk Döner") kendi gramajını kullanır;
  // "yarım" kelimesi tekrar yarıya indirmemeli.
  const namedVariant = matchedVariant && matchedVariant.defaultGrams && [matchedVariant.name, ...(matchedVariant.aliases || [])].map(normalizeTurkishText).includes(normText)
    ? matchedVariant : null;

  if (input.grams && input.grams > 0) {
    // Kullanıcı doğrudan gramaj belirtmişse (ör. "200 gram tavuk")
    calculatedGrams = input.grams;
  } else if (namedVariant) {
    calculatedGrams = (namedVariant.defaultGrams ?? 150) * qty;
  } else if (matchedFood) {
    // Yiyeceğe özel porsiyon tablosunda birim ara
    const specificPortion = matchedFood.portions.find((p) => {
      const pNorm = normalizeTurkishText(p.unit);
      const pNameNorm = normalizeTurkishText(p.name);
      return (
        pNorm === unitRaw ||
        pNameNorm.includes(unitRaw) ||
        (unitRaw && pNorm.includes(unitRaw))
      );
    });

    if (specificPortion) {
      calculatedGrams = specificPortion.grams * qty;
    } else if (matchedVariant && matchedVariant.defaultGrams) {
      calculatedGrams = matchedVariant.defaultGrams * qty;
    } else if (unitRaw && MEASURE_UNITS.has(unitRaw) && GENERIC_UNIT_GRAMS[unitRaw]) {
      // Kullanıcı bir ölçü birimi söyledi ("3 yemek kaşığı") ama bu yiyeceğin tablosunda yok:
      // ölçü birimi yiyecekten bağımsızdır, varsayılan porsiyondan daha doğrudur.
      calculatedGrams = GENERIC_UNIT_GRAMS[unitRaw] * qty;
    } else {
      // Varsayılan porsiyonu veya genel porsiyon kuralını kullan
      const defaultPortion = matchedFood.portions.find((p) => p.isDefault) || matchedFood.portions[0];
      const baseGrams = defaultPortion ? defaultPortion.grams : 150;
      calculatedGrams = baseGrams * qty;
    }
    calculatedGrams = amountWordFactor(calculatedGrams);
  } else if (defaultFood) {
    calculatedGrams = amountWordFactor(defaultFoodGrams(defaultFood, unitRaw) * qty);
  } else {
    // Genel porsiyon kuralı
    const fallbackUnitGrams = GENERIC_UNIT_GRAMS[unitRaw] || 100;
    calculatedGrams = fallbackUnitGrams * qty;
  }

  calculatedGrams = Math.max(1, Math.round(calculatedGrams));

  // 5. Besin Değerlerini Hesapla
  if (matchedFood) {
    const ratio = calculatedGrams / 100.0;
    const baseNutrients = matchedVariant || matchedFood;

    const cal = Math.round(baseNutrients.calories * ratio);
    const prot = rounded(baseNutrients.protein * ratio);
    const carbs = rounded(baseNutrients.carbohydrates * ratio);
    const fat = rounded(baseNutrients.fat * ratio);
    const fiber = rounded(baseNutrients.fiber * ratio);

    // Düz tahıl + gramaj ("100 gram pirinç"): çiğ mi pişmiş mi tartıldığı bilinmiyor.
    // Pişmiş varsayılır (tabaktaki ağırlık); çiğ tartıldıysa değer ~2,5 kat yüksektir.
    if (input.grams && input.grams > 0 && COOKED_BY_DEFAULT.has(matchedFood.id) && !COOKING_WORDS.test(normText)) {
      needsConfirmation = true;
      confidence = Math.min(confidence, 0.7);
      warning = "Pişmiş ağırlık varsayıldı. Çiğ/kuru tarttıysan 'çiğ pirinç' gibi yaz; kalori yaklaşık 2,5 kat yüksek çıkar.";
      warningCode = "cooked_assumed";
    }

    return {
      foodId: matchedFood.id,
      name: matchedVariant ? matchedVariant.name : matchedFood.name,
      variantName: matchedVariant?.name,
      quantity: qty,
      unit: input.unit || (matchedFood.portions[0]?.unit ?? "porsiyon"),
      grams: calculatedGrams,
      calories: cal,
      protein: prot,
      carbohydrates: carbs,
      fat: fat,
      fiber: fiber,
      sugar: rounded((matchedFood.sugar || 0) * ratio),
      sodiumMg: rounded((matchedFood.sodiumMg || 0) * ratio),
      potassiumMg: rounded((matchedFood.potassiumMg || 0) * ratio),
      calciumMg: rounded((matchedFood.calciumMg || 0) * ratio),
      ironMg: rounded((matchedFood.ironMg || 0) * ratio),
      vitaminCMg: rounded((matchedFood.vitaminCMg || 0) * ratio),
      confidence,
      verified: true,
      source: "turkish_database",
      needsConfirmation,
      warning,
      warningCode,
    };
  }

  // 6. Temel Katalog (Default Food Catalog)
  if (defaultFood) {
    const ratio = calculatedGrams / 100.0;
    return {
      foodId: `default-${defaultFood.id}`,
      name: defaultFood.name,
      quantity: qty,
      unit: input.unit || "porsiyon",
      grams: calculatedGrams,
      calories: Math.round(defaultFood.calories * ratio),
      protein: rounded(defaultFood.protein * ratio),
      carbohydrates: rounded(defaultFood.carbohydrates * ratio),
      fat: rounded(defaultFood.fat * ratio),
      fiber: rounded(defaultFood.fiber * ratio),
      sugar: 0,
      sodiumMg: 0,
      potassiumMg: 0,
      calciumMg: 0,
      ironMg: 0,
      vitaminCMg: 0,
      confidence: needsConfirmation ? 0.7 : 0.85,
      verified: true,
      source: "default_catalog",
      needsConfirmation,
      warning,
      warningCode,
    };
  }

  // 7. Eşleşmeyen Bilinmeyen Yiyecek
  // Kategori düzeyinde kaba bir tahmin (içecek, yağ, tatlı…); yapay zekâ DEĞİL, her zaman onay ister.
  const guess = genericGuess(normText);
  const ratio = calculatedGrams / 100.0;
  return {
    foodId: `unknown-${Date.now()}`,
    name: rawText,
    quantity: qty,
    unit: input.unit || "porsiyon",
    grams: calculatedGrams,
    calories: Math.round(guess.calories * ratio),
    protein: rounded(guess.protein * ratio),
    carbohydrates: rounded(guess.carbohydrates * ratio),
    fat: rounded(guess.fat * ratio),
    fiber: rounded(guess.fiber * ratio),
    sugar: 0,
    sodiumMg: 0,
    potassiumMg: 0,
    calciumMg: 0,
    ironMg: 0,
    vitaminCMg: 0,
    confidence: 0.5,
    verified: false,
    source: "generic_estimate",
    needsConfirmation: true,
    warning: `Bu yiyecek veritabanında bulunamadı; ${guess.label} değerleri gösteriliyor. Kaydetmeden önce kontrol et.`,
    warningCode: "not_in_catalogue",
  };
}

// ---------------------------------------------------------------------------------------------
// Yardımcılar
// ---------------------------------------------------------------------------------------------

/** Pişmiş değeri tutulan düz tahıl/makarna girişleri. */
const COOKED_BY_DEFAULT = new Set(["pirinc-pilavi", "bulgur-pilavi", "makarna"]);
const COOKING_WORDS = /(pilav|haslanmis|pismis|cig|kuru|pismemis|tabak|porsiyon|kase)/;

/** Temel katalog girişinin birim ağırlığı: kendi tablosu → genel birim → porsiyon. */
function defaultFoodGrams(food: DefaultFood, unit: string): number {
  const table = food.portions;
  if (table) {
    if (unit && table[unit]) return table[unit];
    if (!unit) return table.adet ?? table.porsiyon ?? Object.values(table)[0] ?? 100;
    if (GENERIC_UNIT_GRAMS[unit]) return GENERIC_UNIT_GRAMS[unit];
    return table.porsiyon ?? Object.values(table)[0] ?? 100;
  }
  return (unit && GENERIC_UNIT_GRAMS[unit]) || 100;
}

type GenericGuess = { label: string; calories: number; protein: number; carbohydrates: number; fat: number; fiber: number };

/** Category-level guesses for foods no catalogue knows. Order matters: first match wins. */
const GENERIC_GUESSES: Array<[RegExp, GenericGuess]> = [
  [/(icecek|suyu|kola|gazoz|soda|limonata|smoothie|shake|boza|salgam)/, { label: "içecek için ortalama", calories: 40, protein: 0.5, carbohydrates: 9, fat: 0.2, fiber: 0 }],
  [/(^|\s)(yag|margarin)(\s|$)/, { label: "yağ için ortalama", calories: 800, protein: 0, carbohydrates: 0, fat: 90, fiber: 0 }],
  [/(tatli|cikolata|biskuvi|kek|kurabiye|gofret|lokum|helva|sekerleme|draje|pasta)/, { label: "tatlı/atıştırmalık için ortalama", calories: 430, protein: 5, carbohydrates: 65, fat: 17, fiber: 1.5 }],
];
const GENERIC_DEFAULT: GenericGuess = { label: "ortalama ev yemeği", calories: 135, protein: 6, carbohydrates: 14, fat: 6, fiber: 2 };

function genericGuess(normText: string): GenericGuess {
  return GENERIC_GUESSES.find(([pattern]) => pattern.test(normText))?.[1] ?? GENERIC_DEFAULT;
}
