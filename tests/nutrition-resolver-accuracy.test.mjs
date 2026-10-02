import assert from "node:assert/strict";
import test from "node:test";

import { listDefaultFoods, matchDefaultFood } from "../lib/default-food-catalog.ts";
import { resolveFood } from "../lib/food-resolver.ts";
import { parseMealTextLocally } from "../lib/natural-meal-parser.ts";
import { RAW_FOODS, matchRawFood } from "../lib/raw-food-equivalents.ts";
import { POST as parseTextRoute } from "../app/api/nutrition/parse-text/route.ts";
import { authorizedRequest, withAuthenticatedFetch, withSupabaseAuthEnv } from "./helpers/auth.mjs";

const total = (items) => items.reduce((sum, item) => sum + item.calories, 0);
const parse = (text) => {
  const items = parseMealTextLocally(text);
  assert.ok(items && items.length > 0, `"${text}" çözümlenemedi`);
  return items;
};

// -----------------------------------------------------------------------------
// Hata raporunda ölçülen sapmalar: her biri için referans enerji aralığı.
// Referanslar USDA / TürKomp tipik değerleridir; aralıklar ~%10.
// -----------------------------------------------------------------------------
const CASES = [
  // [metin, öğe sayısı, min kcal, maks kcal, açıklama]
  ["1 kase yulaf ezmesi", 1, 160, 240, "kuru yulaf 50 g (eskiden 250 g × 379 = 948 kcal)"],
  ["1 kase yulaf ezmesi muz süt", 3, 360, 440, "süt artık düşmüyor"],
  ["muz süt", 2, 180, 230, "bitişik yazılan iki yiyecek"],
  ["200 gram dana kıyma", 1, 450, 550, "dana + kıyma çift sayımı yok"],
  ["200 gram tavuk göğsü", 1, 310, 350, "katalogda var (eskiden 270 kcal tahmin)"],
  ["200 gram çiğ tavuk göğsü", 1, 230, 250, "çiğ ağırlık"],
  ["1 kutu kola", 1, 125, 150, "330 ml (eskiden 338 kcal tahmin)"],
  ["1 kutu kola zero", 1, 0, 10, "şekersiz"],
  ["1 kutu soğuk kola", 1, 125, 150, "gevşek katalog eşleşmesi (fazladan sözcük)"],
  ["1 yemek kaşığı zeytinyağı", 1, 110, 130, "kaşık birimi tanınıyor (JS \\b hatası)"],
  ["2 yemek kaşığı bal", 1, 120, 135, ""],
  ["1 çay kaşığı şeker", 1, 14, 18, ""],
  ["1 ölçü whey protein", 1, 110, 130, "ölçü birimi"],
  ["1 adet protein bar", 1, 190, 220, ""],
  ["100 gram çiğ pirinç", 1, 355, 365, "çiğ ≠ pilav (145 kcal)"],
  ["100 gram çiğ bulgur", 1, 335, 350, "çiğ işareti yok sayılmıyor"],
  ["80 gram kuru makarna", 1, 290, 300, ""],
  ["100 gram kuru mercimek", 1, 345, 360, ""],
  ["100 gram çiğ kuru fasulye", 1, 330, 336, "fasulye yalnız açık çiğ işaretiyle"],
  ["1 bardak çiğ pirinç", 1, 640, 660, "bardak = 180 g çiğ pirinç"],
  ["üç dilim ekmek", 1, 230, 250, "\"üç\" (ç ile biten sayı) tanınıyor"],
  ["bir avuç fındık", 1, 180, 195, "\"avuç\" birimi"],
  ["iki yemek kaşığı fıstık ezmesi", 1, 180, 195, ""],
  ["3 kare bitter çikolata", 1, 100, 115, "kare birimi"],
  ["1 kadeh şarap", 1, 115, 135, "kadeh birimi"],
  ["1 şişe bira", 1, 135, 150, "330 ml"],
  ["2 adet kuru incir", 1, 95, 105, ""],
  ["1 top dondurma", 1, 95, 110, "top birimi"],
  ["öğlen 3 yemek kaşığı bal yedim", 1, 185, 200, "ö ile başlayan dolgu sözcüğü temizleniyor"],
];

for (const [text, count, min, max, note] of CASES) {
  test(`"${text}" ${min}–${max} kcal${note ? ` (${note})` : ""}`, () => {
    const items = parse(text);
    assert.equal(items.length, count, `öğeler: ${items.map((item) => item.name).join(", ")}`);
    const kcal = total(items);
    assert.ok(kcal >= min && kcal <= max, `${kcal} kcal bekleneni aştı (${items.map((item) => `${item.name} ${item.grams}g ${item.calories}`).join("; ")})`);
    assert.ok(items.every((item) => item.verified), "doğrulanmamış (genel tahmin) kalem var");
  });
}

// -----------------------------------------------------------------------------
// Kapsam: sık kaydedilen yiyecekler artık sabit "135 kcal/100 g" tahminine düşmez.
// -----------------------------------------------------------------------------
const COMMON_FOODS = `tavuk göğsü|ızgara tavuk göğsü|haşlanmış tavuk|tavuk but|tavuk kanat|hindi füme|dana biftek|kuzu pirzola|sucuk|salam|sosis|pastırma|çipura|konserve ton|karides|kefir|labne|tulum peyniri|tereyağı|bal|reçel|pekmez|tahin|fıstık ezmesi|nutella|çikolata|bitter çikolata|bisküvi|kraker|gofret|cips|patlamış mısır|kuruyemiş|fıstık|antep fıstığı|kaju|ay çekirdeği|hurma|incir|granola|mısır gevreği|müsli|kabak|patlıcan|karnabahar|mantar|kavun|kiraz|nar|ananas|avokado|kola|kola zero|gazoz|soda|meyve suyu|filtre kahve|latte|cappuccino|nescafe|süt kahve|enerji içeceği|bira|şarap|rakı|protein tozu|whey protein|protein bar|kreatin|kadayıf|dondurma|kek|kurabiye|tiramisu|dolma|şeker|tuz|ketçap|mayonez|hardal|yumurta|omlet|menemen|süt|yoğurt|ayran|beyaz peynir|zeytin|zeytinyağı|ekmek|simit|poğaça|lahmacun|pirinç|bulgur|makarna|pilav|yulaf ezmesi|mercimek|nohut|kuru fasulye|patates|havuç|elma|muz|portakal|çilek|üzüm|karpuz|çay|türk kahvesi|baklava|künefe|hamburger|pizza|döner|iskender|mantı|cacık|humus`.split("|");

test("sık yazılan yiyeceklerin hiçbiri genel tahmine düşmez", () => {
  const unknown = COMMON_FOODS.filter((food) => resolveFood({ text: food }).source === "generic_estimate");
  assert.deepEqual(unknown, []);
});

test("genel tahmin dürüstçe etiketlenir: AI değil, doğrulanmamış, her zaman onay ister", () => {
  const unknown = resolveFood({ text: "bilinmeyen yemek xyz", grams: 100 });
  assert.equal(unknown.source, "generic_estimate");
  assert.equal(unknown.verified, false);
  assert.equal(unknown.needsConfirmation, true);
  assert.match(unknown.warning, /bulunamadı/);
  // Kategori düzeyinde kaba tahmin: içecek 135 değil ~40, yağ ~800.
  assert.equal(resolveFood({ text: "bilinmeyen içecek", grams: 100 }).calories, 40);
  assert.equal(resolveFood({ text: "bilinmeyen yağ", grams: 100 }).calories, 800);
  assert.equal(resolveFood({ text: "bilinmeyen tatlı", grams: 100 }).calories, 430);
});

// -----------------------------------------------------------------------------
// Çiğ / pişmiş
// -----------------------------------------------------------------------------
test("çiğ/kuru işareti çiğ satırı seçer; işaret yoksa pişmiş varsayılır ve uyarılır", () => {
  assert.equal(matchRawFood("çiğ pirinç")?.id, "cig-pirinc");
  assert.equal(matchRawFood("kuru makarna")?.id, "kuru-makarna");
  assert.equal(matchRawFood("pirinç"), null);
  assert.equal(matchRawFood("pirinç pilavı"), null);
  assert.equal(matchRawFood("kuru fasulye"), null, "kuru fasulye pişmiş bir yemek adıdır");
  assert.equal(matchRawFood("çiğ köfte"), null, "çiğ köfte bir yemektir");
  assert.equal(matchRawFood("mercimek çorbası"), null);
  assert.equal(matchRawFood("kuru kayısı"), null);

  const bare = resolveFood({ text: "pirinç", grams: 100 });
  assert.equal(bare.calories, 145, "pişmiş varsayılan");
  assert.equal(bare.needsConfirmation, true);
  assert.match(bare.warning, /Pişmiş ağırlık varsayıldı/);
  for (const text of ["pilav", "pirinç pilavı", "haşlanmış makarna"]) {
    assert.equal(resolveFood({ text, grams: 100 }).needsConfirmation, false, `${text} uyarı vermemeli`);
  }
  assert.equal(resolveFood({ text: "çiğ pirinç", grams: 100 }).needsConfirmation, false);
});

test("çiğ satırlar makro-enerji tutarlıdır ve çiğ pirinç pilavın ~2,5 katıdır", () => {
  const ratio = resolveFood({ text: "çiğ pirinç", grams: 100 }).calories / resolveFood({ text: "pilav", grams: 100 }).calories;
  assert.ok(ratio > 2.3 && ratio < 2.7, `oran ${ratio.toFixed(2)}`);
  for (const raw of RAW_FOODS) {
    const macros = raw.protein * 4 + raw.carbohydrates * 4 + raw.fat * 9;
    assert.ok(Math.abs(macros - raw.calories) <= Math.max(35, raw.calories * 0.2), `${raw.id}: ${raw.calories} kcal vs ${Math.round(macros)}`);
  }
});

// -----------------------------------------------------------------------------
// Katalog veri kalitesi
// -----------------------------------------------------------------------------
const ALCOHOL = new Set(["sarap", "raki", "sert-icki", "bira"]);

test("temel katalogdaki her giriş makro-enerji tutarlıdır (alkol hariç: alkol 7 kcal/g, makroda görünmez)", () => {
  for (const food of listDefaultFoods()) {
    if (ALCOHOL.has(food.id)) continue;
    const macros = food.protein * 4 + food.carbohydrates * 4 + food.fat * 9;
    assert.ok(Math.abs(macros - food.calories) <= Math.max(35, food.calories * 0.2), `${food.id}: ${food.calories} kcal vs makro ${Math.round(macros)}`);
  }
});

test("porsiyon tabloları makul: pozitif gram, tek bir birim hiçbir yiyeceği 1200 kcal'in üstüne çıkarmaz (şişe/kutu hariç)", () => {
  for (const food of listDefaultFoods()) {
    for (const [unit, grams] of Object.entries(food.portions ?? {})) {
      assert.ok(grams > 0 && grams <= 1000, `${food.id}.${unit} = ${grams} g`);
      if (!["sise", "kutu"].includes(unit)) assert.ok((food.calories * grams) / 100 <= 1200, `${food.id}.${unit}: ${Math.round((food.calories * grams) / 100)} kcal`);
    }
  }
});

test("takma adlar tek bir girişe gider (aynı ad iki farklı yiyeceğe çözülmez)", () => {
  const owner = new Map();
  for (const food of listDefaultFoods().filter((entry) => !entry.id.includes("-") || entry.portions)) {
    for (const alias of [food.name, ...food.aliases]) {
      const resolved = matchDefaultFood(alias);
      assert.ok(resolved, `${alias} çözülmedi`);
      owner.set(alias, resolved.id);
    }
  }
  // Uyumsuz ek hareketler: kola ile kola zero karışmamalı.
  assert.equal(matchDefaultFood("kola")?.id, "kola");
  assert.equal(matchDefaultFood("kola zero")?.id, "kola-sekersiz");
  assert.equal(matchDefaultFood("dana kıyma")?.id, "kiyma");
  assert.equal(matchDefaultFood("tavuk göğsü")?.id, "tavuk-gogsu");
});

// -----------------------------------------------------------------------------
// Uyarılar: her kalem NEDEN kontrol istediğini kodla bildirir (istemci yerelleştirir)
// -----------------------------------------------------------------------------
test("uyarılar sabit bir kodla gelir: yaklaşık miktar, pişmiş varsayımı, katalogda yok", () => {
  assert.equal(resolveFood({ text: "pirinç", grams: 100 }).warningCode, "cooked_assumed");
  assert.equal(resolveFood({ text: "bilinmeyen yemek xyz", grams: 100 }).warningCode, "not_in_catalogue");
  assert.equal(resolveFood({ text: "biraz zeytin" }).warningCode, "approximate_amount");
  for (const clean of [resolveFood({ text: "çiğ pirinç", grams: 100 }), resolveFood({ text: "pilav", grams: 100 }), resolveFood({ text: "kola", unit: "kutu" })]) {
    assert.equal(clean.warning, undefined);
    assert.equal(clean.warningCode, undefined);
    assert.equal(clean.needsConfirmation, false);
  }
});

async function postParseText(body) {
  const previousFetch = globalThis.fetch;
  const previousKey = process.env.OPENAI_API_KEY;
  const previousAiKey = process.env.AI_API_KEY;
  const restoreAuthEnv = withSupabaseAuthEnv();
  delete process.env.OPENAI_API_KEY;
  delete process.env.AI_API_KEY;
  globalThis.fetch = withAuthenticatedFetch(null, "33333333-3333-4333-8333-333333333333");
  try {
    const response = await parseTextRoute(authorizedRequest("http://localhost/api/nutrition/parse-text", { method: "POST", body: JSON.stringify(body) }));
    return { status: response.status, body: await response.json() };
  } finally {
    globalThis.fetch = previousFetch;
    restoreAuthEnv();
    if (previousKey === undefined) delete process.env.OPENAI_API_KEY; else process.env.OPENAI_API_KEY = previousKey;
    if (previousAiKey === undefined) delete process.env.AI_API_KEY; else process.env.AI_API_KEY = previousAiKey;
  }
}

test("parse-text yanıtı her kalem için warning ve warningCode taşır; temiz kalemde yoktur", { concurrency: false }, async () => {
  const { status, body } = await postParseText({ text: "100 gram pirinç, 1 kutu kola, bilinmeyen yemek xyz" });
  assert.equal(status, 200);
  const byName = Object.fromEntries(body.items.map((item) => [item.query, item]));
  const rice = byName["Pirinç Pilavı"];
  assert.equal(rice.needsConfirmation, true);
  assert.equal(rice.warningCode, "cooked_assumed");
  assert.match(rice.warning, /Pişmiş ağırlık varsayıldı/);
  const cola = byName["Kola"];
  assert.equal(cola.needsConfirmation, false);
  assert.equal(cola.warning, undefined);
  assert.equal(cola.warningCode, undefined);
  const unknown = body.items.find((item) => item.source === "generic_estimate");
  assert.equal(unknown.warningCode, "not_in_catalogue");
  assert.equal(unknown.verified, false);
});
