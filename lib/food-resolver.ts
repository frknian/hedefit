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
import { matchDefaultFood } from "./default-food-catalog.ts";

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
  source: "turkish_database" | "default_catalog" | "ai_estimate";
  needsConfirmation: boolean;
  warning?: string;
}

function rounded(value: number, digits = 1): number {
  const factor = 10 ** digits;
  return Math.round(value * factor) / factor;
}

/**
 * Genel Türk mutfağı porsiyon standartları (yiyeceğe özel tanım yoksa fallback).
 */
const DEFAULT_PORTION_GRAMS: Record<string, number> = {
  gram: 1,
  gr: 1,
  g: 1,
  ml: 1,
  porsiyon: 200,
  tabak: 250,
  kase: 250,
  kepce: 100,
  kepçe: 100,
  dilim: 30,
  adet: 50,
  tane: 50,
  avuc: 30,
  avuç: 30,
  bardak: 200,
  kasik: 25,
  kaşık: 25,
  yemek_kasigi: 25,
  tatli_kasigi: 10,
  cay_kasigi: 5,
  durum: 220,
  dürüm: 220,
  yarim: 100,
  yarım: 100,
  ceyrek: 50,
  çeyrek: 50,
  kutu: 250,
  sise: 250,
  şişe: 250,
  fincan: 70,
};

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
  const isAmbiguous = /\b(biraz|az|bir avuc|bir tabak|bolca|goz karari)\b/i.test(rawText);
  if (isAmbiguous) {
    needsConfirmation = true;
    confidence = Math.min(confidence, 0.7);
    warning = "Miktar yaklaşık olarak tahmin edilmiştir; kaydetmeden önce kontrol edebilirsin.";
  }

  // 4. Gramaj ve Porsiyon Çözümlemesi
  const qty = input.quantity && input.quantity > 0 ? input.quantity : 1;
  const unitRaw = input.unit ? normalizeTurkishText(input.unit) : "";
  let calculatedGrams = 0;

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
    } else {
      // Varsayılan porsiyonu veya genel porsiyon kuralını kullan
      const defaultPortion = matchedFood.portions.find((p) => p.isDefault) || matchedFood.portions[0];
      const baseGrams = defaultPortion ? defaultPortion.grams : (DEFAULT_PORTION_GRAMS[unitRaw] || 150);
      calculatedGrams = baseGrams * qty;
    }

    if (normText.includes("yarim") || normText.includes("yarım")) {
      calculatedGrams = calculatedGrams * 0.5;
    } else if (normText.includes("ceyrek") || normText.includes("çeyrek")) {
      calculatedGrams = calculatedGrams * 0.25;
    } else if (normText.includes("bir bucuk") || normText.includes("bir buçuk") || normText.includes("1.5")) {
      calculatedGrams = calculatedGrams * 1.5;
    } else if (normText.includes("biraz") || normText.includes("az ")) {
      calculatedGrams = Math.round(calculatedGrams * 0.65);
    } else if (normText.includes("bol") || normText.includes("bolca")) {
      calculatedGrams = Math.round(calculatedGrams * 1.35);
    }
  } else {
    // Genel porsiyon kuralı
    const fallbackUnitGrams = DEFAULT_PORTION_GRAMS[unitRaw] || 100;
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
    };
  }

  // 6. Eski Temel Katalog Kontrolü (Default Food Catalog Fallback)
  const defaultFood = matchDefaultFood(rawText);
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
      confidence: 0.85,
      verified: true,
      source: "default_catalog",
      needsConfirmation: false,
      warning,
    };
  }

  // 7. Eşleşmeyen Bilinmeyen Yiyecek
  // Ortalama ev yemeği referansı üzerinden tahmin
  const ratio = calculatedGrams / 100.0;
  return {
    foodId: `unknown-${Date.now()}`,
    name: rawText,
    quantity: qty,
    unit: input.unit || "porsiyon",
    grams: calculatedGrams,
    calories: Math.round(135 * ratio),
    protein: rounded(6.0 * ratio),
    carbohydrates: rounded(14.0 * ratio),
    fat: rounded(6.0 * ratio),
    fiber: rounded(2.0 * ratio),
    sugar: 0,
    sodiumMg: 0,
    potassiumMg: 0,
    calciumMg: 0,
    ironMg: 0,
    vitaminCMg: 0,
    confidence: 0.5,
    verified: false,
    source: "ai_estimate",
    needsConfirmation: true,
    warning: "Bu yiyecek veritabanında bulunamadı; tahmini değerler gösteriliyor.",
  };
}
