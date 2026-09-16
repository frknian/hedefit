/**
 * Doğal Dille Yazılı Öğün Ayrıştırıcısı (Natural Meal Parser).
 *
 * Kullanıcının günlük konuşma diliyle yazdığı metinleri ("1 tabak kuru fasulye pilav cacık",
 * "2 yumurta 2 dilim ekmek peynir", "yarım ekmek tavuk döner ayran") analiz eder.
 *
 * 1. Kademe: Hızlı kural ve sözlük tabanlı yerel ayrıştırıcı (0 maliyet, hızlı).
 * 2. Kademe: Karmaşık cümleler için yapılandırılmış AI ayrıştırıcısı.
 * Her iki kademeden çıkan öğeler ortak FoodResolver ile doğrulanmış besinlere bağlanır.
 */

import { jsonSchema } from "ai";
import { routeObject } from "./ai/router.ts";
import { resolveFood, type ResolvedFood } from "./food-resolver.ts";
import {
  TURKISH_FOOD_DATABASE,
  normalizeTurkishText,
} from "./turkish-food-database.ts";
import { containsPromptInjection } from "./nutrition-parser.ts";

export interface ParsedMealResult {
  items: ResolvedFood[];
  totals: {
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
  };
  parsedText: string;
  warnings: string[];
}

const TURKISH_NUMBERS: Record<string, number> = {
  bir: 1,
  bi: 1,
  iki: 2,
  uc: 3,
  üç: 3,
  dort: 4,
  dört: 4,
  bes: 5,
  beş: 5,
  alti: 6,
  altı: 6,
  yedi: 7,
  sekiz: 8,
  dokuz: 9,
  on: 10,
  yarim: 0.5,
  yarım: 0.5,
  ceyrek: 0.25,
  çeyrek: 0.25,
  "bir bucuk": 1.5,
  "bir buçuk": 1.5,
  "1 5": 1.5,
  "1.5": 1.5,
  "1,5": 1.5,
  az: 0.7,
  biraz: 0.7,
  bol: 1.3,
  bolca: 1.3,
};

const COMMON_UNITS = [
  "gram",
  "gr",
  "g",
  "ml",
  "tabak",
  "porsiyon",
  "portion",
  "kase",
  "kepce",
  "kepçe",
  "dilim",
  "adet",
  "tane",
  "avuc",
  "avuç",
  "bardak",
  "kasik",
  "kaşık",
  "yemek kasigi",
  "yemek kaşığı",
  "tatli kasigi",
  "tatlı kaşığı",
  "cay kasigi",
  "çay kaşığı",
  "durum",
  "dürüm",
  "ekmek",
  "kutu",
  "sise",
  "şişe",
  "fincan",
  "sahan",
  "tava",
  "sikim",
  "sıkım",
];

// Konuşma dili dolgu kelimeleri
const FILLER_WORDS = [
  "yedim",
  "yedik",
  "ictim",
  "içtim",
  "vardi",
  "vardı",
  "aldim",
  "aldım",
  "tuketim",
  "tükettim",
  "sabah",
  "oglen",
  "öğlen",
  "aksam",
  "akşam",
  "ogunde",
  "öğünde",
  "kahvaltida",
  "kahvaltıda",
  "annemin",
  "yaptigi",
  "yaptığı",
  "canim",
  "canım",
  "cekti",
  "çekti",
];

/**
 * 1. Kademe: Kural tabanlı yerel ayrıştırma.
 */
export function parseMealTextLocally(text: string): ResolvedFood[] | null {
  if (!text || text.trim().length < 2) return null;

  // Prompt injection kontrolü
  if (containsPromptInjection(text)) {
    return null;
  }

  // Korunması gereken birleşik kalıplar (bölünmemesi gerekenler)
  let workingText = text.trim();

  // Temizle: gereksiz dolgu kelimeleri
  for (const filler of FILLER_WORDS) {
    const reg = new RegExp(`\\b${filler}\\b`, "gi");
    workingText = workingText.replace(reg, " ");
  }

  // Bağlaçlar üzerinden ana parçalama: " ve ", " ile ", " yanında ", " beraber ", ",", "\n", "+"
  const delimiterRegex = /\s+(?:ve|ile|yanında|yaninda|beraber|\+)\s+|[,\n\r;]+/gi;
  let segments = workingText
    .split(delimiterRegex)
    .map((s) => s.trim())
    .filter((s) => s.length >= 2);

  // Eğer tek parça kaldıysa ve içinde birden fazla bilinen yemek geçiyorsa (ör: "kuru fasulye pilav cacık", "2 yumurta 2 dilim ekmek peynir")
  if (segments.length === 1) {
    const splitByKnownFoods = trySegmentByDatabase(segments[0]);
    if (splitByKnownFoods.length > 1) {
      segments = splitByKnownFoods;
    }
  }

  if (segments.length === 0) return null;

  const resolvedItems: ResolvedFood[] = [];

  for (const segment of segments) {
    const extracted = extractQuantityAndFood(segment);
    if (!extracted || extracted.foodName.length < 2) continue;

    const resolved = resolveFood({
      text: extracted.foodName,
      quantity: extracted.quantity,
      unit: extracted.unit,
      grams: extracted.grams,
    });

    resolvedItems.push(resolved);
  }

  // En az 1 geçerli yemek çözümlendiyse dön
  return resolvedItems.length > 0 ? resolvedItems : null;
}

/**
 * Metin parçasından miktar, birim ve yemek adını çıkarır.
 * Ör: "2 dilim ekmek", "1 tabak kuru fasulye", "200 gram tavuk", "bir kase mercimek çorbası"
 */
function extractQuantityAndFood(text: string): {
  quantity: number;
  unit?: string;
  grams?: number;
  foodName: string;
} {
  let clean = text.trim();
  let quantity = 1;
  let unit: string | undefined;
  let grams: number | undefined;

  // 1. Doğrudan gramaj tespiti (ör: "200 gram", "200gr", "150g", "100 ml")
  const gramsMatch = clean.match(/^(\d+(?:[.,]\d+)?)\s*(?:gram|gr|g|ml)\b/i) ||
    clean.match(/\b(\d+(?:[.,]\d+)?)\s*(?:gram|gr|g|ml)\b/i);

  if (gramsMatch) {
    grams = Number(gramsMatch[1].replace(",", "."));
    clean = clean.replace(gramsMatch[0], " ").trim();
  }

  // 2. Sayısal veya Türkçe miktar tespiti (ör: "1.5 porsiyon", "2 adet", "yarım", "bir kase")
  // Önce birleşik kalıplar: "bir buçuk", "yarım", "çeyrek"
  const fractionMatch = clean.match(/^(bir\s+bucuk|bir\s+buçuk|1[.,]5|yarim|yarım|ceyrek|çeyrek)\b/i);
  if (fractionMatch) {
    const key = fractionMatch[1].toLowerCase();
    quantity = TURKISH_NUMBERS[key] ?? 1;
    clean = clean.slice(fractionMatch[0].length).trim();
  } else {
    // Sayı kontrolü
    const numMatch = clean.match(/^(\d+(?:[.,]\d+)?)\b/);
    if (numMatch) {
      quantity = Number(numMatch[1].replace(",", "."));
      clean = clean.slice(numMatch[0].length).trim();
    } else {
      // Kelime olarak sayı ("iki", "üç", "bir")
      const wordMatch = clean.match(/^(bir|bi|iki|uc|üç|dort|dört|bes|beş|alti|altı|yedi|sekiz|dokuz|on|az|biraz|bol|bolca)\b/i);
      if (wordMatch) {
        const key = wordMatch[1].toLowerCase();
        quantity = TURKISH_NUMBERS[key] ?? 1;
        clean = clean.slice(wordMatch[0].length).trim();
      }
    }
  }

  // 3. Birim tespiti (ör: "dilim", "tabak", "kase", "porsiyon", "avuç", "kepçe", "adet", "tane", "bardak")
  for (const u of COMMON_UNITS) {
    const unitReg = new RegExp(`^${u}\\b`, "i");
    if (unitReg.test(clean)) {
      unit = u;
      clean = clean.replace(unitReg, " ").trim();
      break;
    }
  }

  return {
    quantity: Number.isFinite(quantity) && quantity > 0 ? quantity : 1,
    unit,
    grams,
    foodName: clean.trim(),
  };
}

/**
 * Bağlaçsız peş peşe yazılmış metinleri (ör: "kuru fasulye pilav cacık", "2 yumurta 2 dilim ekmek peynir")
 * veritabanındaki bilinen yemeklerle parçalar.
 */
function trySegmentByDatabase(text: string): string[] {
  const norm = normalizeTurkishText(text);
  const words = norm.split(/\s+/);
  if (words.length < 2) return [text];

  const segments: string[] = [];
  let currentWords: string[] = [];

  for (let i = 0; i < words.length; i++) {
    const w = words[i];
    // Sayı veya porsiyon belirteci mi?
    const isNumberOrUnit =
      /^\d+$/.test(w) ||
      TURKISH_NUMBERS[w] !== undefined ||
      COMMON_UNITS.includes(w) ||
      /^\d+(?:g|gr|gram|ml)$/i.test(w);

    if (isNumberOrUnit && currentWords.length > 0) {
      // Önceki kısmı segment olarak ekle
      segments.push(currentWords.join(" "));
      currentWords = [w];
      continue;
    }

    currentWords.push(w);

    // Mevcut kelime dizisi veritabanında bilinen bir yemek veya alias'a tam uyuyor mu?
    const phrase = currentWords.join(" ");
    const exactMatch = TURKISH_FOOD_DATABASE.some((food) => {
      const allNames = [food.name, ...food.aliases].map(normalizeTurkishText);
      return allNames.includes(phrase);
    });

    // Eğer tam uyuyorsa ve bir sonraki kelime de bilinen başka bir yemeğin başlangıcıysa
    if (exactMatch && i + 1 < words.length) {
      const nextWord = words[i + 1];
      const nextMatchesAnyFood = TURKISH_FOOD_DATABASE.some((f) => {
        const allNames = [f.name, ...f.aliases].map(normalizeTurkishText);
        return allNames.some((name) => name.startsWith(nextWord));
      });

      if (nextMatchesAnyFood) {
        segments.push(currentWords.join(" "));
        currentWords = [];
      }
    }
  }

  if (currentWords.length > 0) {
    segments.push(currentWords.join(" "));
  }

  return segments.length > 1 ? segments : [text];
}

/**
 * 2. Kademe: Yapay zeka ile doğal dil yapılandırması (karmaşık/serbest anlatımlı cümleler için).
 */
const MEAL_AI_SCHEMA = jsonSchema<{
  foods: Array<{
    name: string;
    quantity: number;
    unit: string;
    estimatedGrams?: number;
  }>;
}>({
  type: "object",
  properties: {
    foods: {
      type: "array",
      minItems: 1,
      maxItems: 10,
      items: {
        type: "object",
        properties: {
          name: { type: "string", minLength: 1, maxLength: 80 },
          quantity: { type: "number", minimum: 0.1, maximum: 50 },
          unit: { type: "string", minLength: 1, maxLength: 30 },
          estimatedGrams: { type: "number", minimum: 1, maximum: 5000 },
        },
        required: ["name", "quantity", "unit"],
        additionalProperties: false,
      },
    },
  },
  required: ["foods"],
  additionalProperties: false,
});

const MEAL_PARSER_SYSTEM_PROMPT = `Sen uzman bir Türk mutfağı ve beslenme metin ayrıştırıcısısın.
Kullanıcının yazdığı doğal Türkçe metinden yediği her yiyeceği ayrı bir öğe olarak çıkar.
Her yiyecek için:
1. "name": Yemeğin standart Türkçe adını yaz (ör. "kuru fasulye", "pirinç pilavı", "kıymalı biber dolması", "cacık").
2. "quantity": Sayısal miktarı (1, 2, 0.5, 1.5).
3. "unit": Porsiyon birimini (porsiyon, tabak, kase, dilim, adet, kepçe, bardak, avuç).
4. "estimatedGrams": Varsa belirtilen veya yaklaşık gramajı.

Kurallar:
- Kalori hesabı YAPMA; sadece yiyecekleri ve miktarları doğru sınıflandır.
- Dolgu kelimelerini ("yedim", "annem yapmıştı", "yanında vardı") yemek adına dahil etme.
- Türkçe yazım hatalarını standart ada dönüştür ("mercimek corbasi" -> "mercimek çorbası").`;

export async function parseMealTextWithAi(text: string): Promise<ResolvedFood[] | null> {
  try {
    const { object } = await routeObject({
      system: MEAL_PARSER_SYSTEM_PROMPT,
      prompt: `Öğün metni: <meal>${text}</meal>`,
      category: "structured_extraction",
      schema: MEAL_AI_SCHEMA,
      maxOutputTokens: 500,
      abortSignal: AbortSignal.timeout(30_000),
    });

    if (!object || !Array.isArray(object.foods) || object.foods.length === 0) {
      return null;
    }

    return object.foods.map((food) => {
      return resolveFood({
        text: food.name,
        quantity: food.quantity,
        unit: food.unit,
        grams: food.estimatedGrams,
      });
    });
  } catch (error) {
    console.error("[natural-meal-parser] AI extraction failed", error);
    return null;
  }
}

/**
 * Ana giriş noktası: Kullanıcı metnini alır, önce yerel motorla ayrıştırır;
 * yerel motor yetersiz kalırsa AI motoruna düşer.
 */
export async function parseNaturalMealText(
  text: string,
  allowAiFallback = true
): Promise<ParsedMealResult> {
  const clean = text.trim();

  // 1. Yerel kural tabanlı ayrıştırıcıyı dene
  let resolvedItems = parseMealTextLocally(clean);

  // 2. Yetersiz ise ve AI izni varsa AI motoruna başvur
  if ((!resolvedItems || resolvedItems.length === 0) && allowAiFallback) {
    resolvedItems = await parseMealTextWithAi(clean);
  }

  // 3. Hâlâ sonuç yoksa en azından metnin tamamını tek bir öğe olarak çözümle
  if (!resolvedItems || resolvedItems.length === 0) {
    resolvedItems = [resolveFood({ text: clean })];
  }

  // Toplamları hesapla
  const totals = resolvedItems.reduce(
    (acc, item) => ({
      calories: acc.calories + item.calories,
      protein: Math.round((acc.protein + item.protein) * 10) / 10,
      carbohydrates: Math.round((acc.carbohydrates + item.carbohydrates) * 10) / 10,
      fat: Math.round((acc.fat + item.fat) * 10) / 10,
      fiber: Math.round((acc.fiber + item.fiber) * 10) / 10,
      sugar: Math.round((acc.sugar + item.sugar) * 10) / 10,
      sodiumMg: Math.round(acc.sodiumMg + item.sodiumMg),
      potassiumMg: Math.round(acc.potassiumMg + item.potassiumMg),
      calciumMg: Math.round(acc.calciumMg + item.calciumMg),
      ironMg: Math.round((acc.ironMg + item.ironMg) * 10) / 10,
      vitaminCMg: Math.round(acc.vitaminCMg + item.vitaminCMg),
    }),
    {
      calories: 0,
      protein: 0,
      carbohydrates: 0,
      fat: 0,
      fiber: 0,
      sugar: 0,
      sodiumMg: 0,
      potassiumMg: 0,
      calciumMg: 0,
      ironMg: 0,
      vitaminCMg: 0,
    }
  );

  const warnings: string[] = [];
  if (resolvedItems.some((i) => i.needsConfirmation)) {
    warnings.push("Bazı porsiyonlar yaklaşık olarak tahmin edilmiştir; kaydetmeden önce kontrol edebilirsin.");
  }

  return {
    items: resolvedItems,
    totals,
    parsedText: clean,
    warnings,
  };
}
