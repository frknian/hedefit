import assert from "node:assert/strict";
import test from "node:test";

import {
  parseNaturalMealText,
  parseMealTextLocally,
} from "../lib/natural-meal-parser.ts";

import {
  resolveFood,
} from "../lib/food-resolver.ts";

import {
  TURKISH_FOOD_DATABASE,
  findTurkishFoodByAliasOrName,
  normalizeTurkishText,
} from "../lib/turkish-food-database.ts";

import { searchDefaultFoods } from "../lib/default-food-catalog.ts";

test("Türkçe metin normalizasyonu diyakritik harfleri düzgün katlar", () => {
  assert.equal(normalizeTurkishText("Mercimek Çorbası"), "mercimek corbasi");
  assert.equal(normalizeTurkishText("Çiğ Köfte"), "cig kofte");
  assert.equal(normalizeTurkishText("Kuru Fasülye"), "kuru fasulye");
  assert.equal(normalizeTurkishText("Şalgam"), "salgam");
});

test("Türk yemekleri veritabanı temel kategorileri ve popüler yemekleri içerir", () => {
  const categories = new Set(TURKISH_FOOD_DATABASE.map((f) => f.category));
  assert.ok(categories.has("Çorbalar"));
  assert.ok(categories.has("Kahvaltılıklar"));
  assert.ok(categories.has("Yumurtalı yemekler"));
  assert.ok(categories.has("Kebaplar"));
  assert.ok(categories.has("Köfteler"));
  assert.ok(categories.has("Döner çeşitleri"));
  assert.ok(categories.has("Bakliyat yemekleri"));
  assert.ok(categories.has("Dolma ve sarmalar"));
  assert.ok(categories.has("Pilavlar"));
  assert.ok(categories.has("Börekler"));
  assert.ok(categories.has("Hamur işleri"));
  assert.ok(categories.has("Pideler"));
  assert.ok(categories.has("Mantı"));
  assert.ok(categories.has("Tatlılar"));
  assert.ok(categories.has("İçecekler"));
});

test("Yemek varyantları farklı kalori ve makro değerleri üretir", () => {
  const sadeMenemen = resolveFood({ text: "sade menemen" });
  const sucukluMenemen = resolveFood({ text: "sucuklu menemen" });
  assert.ok(sucukluMenemen.calories > sadeMenemen.calories, "Sucuklu menemen daha kalorili olmalı");
  assert.ok(sucukluMenemen.fat > sadeMenemen.fat, "Sucuklu menemen daha yağlı olmalı");

  const etsizFasulye = resolveFood({ text: "etsiz kuru fasulye" });
  const etliFasulye = resolveFood({ text: "etli kuru fasulye" });
  assert.ok(etliFasulye.calories > etsizFasulye.calories, "Etli kuru fasulye daha kalorili olmalı");
  assert.ok(etliFasulye.protein > etsizFasulye.protein, "Etli fasulyenin proteini daha yüksek olmalı");

  const porsiyonDoner = resolveFood({ text: "et döner", unit: "porsiyon" });
  const yarimEkmekDoner = resolveFood({ text: "yarım ekmek et döner" });
  assert.ok(yarimEkmekDoner.carbohydrates > porsiyonDoner.carbohydrates, "Yarım ekmek döner daha fazla karbonhidrat içermeli");
});

test("Alias sistemi alternatif isimleri ve yazım hatalarını doğru yemeğe bağlar", () => {
  const sarma1 = resolveFood({ text: "sarma" });
  const sarma2 = resolveFood({ text: "yaprak dolması" });
  const sarma3 = resolveFood({ text: "zeytinyağlı sarma" });
  assert.equal(sarma1.foodId, "yaprak-sarma");
  assert.equal(sarma2.foodId, "yaprak-sarma");
  assert.equal(sarma3.foodId, "yaprak-sarma");

  const cigKofte = resolveFood({ text: "cig kofte" });
  assert.equal(cigKofte.foodId, "cig-kofte");

  const mercimek = resolveFood({ text: "mercimek corbasi" });
  assert.equal(mercimek.foodId, "mercimek-corbasi");

  const fasulye = resolveFood({ text: "kuru fasülye" });
  assert.equal(fasulye.foodId, "kuru-fasulye");

  const iskender = resolveFood({ text: "iskender kebap" });
  assert.equal(iskender.foodId, "iskender-kebap");
});

test("Porsiyon sistemi Türk mutfağı birimlerini doğru gramaja dönüştürür", () => {
  const corba = resolveFood({ text: "mercimek çorbası", unit: "kase" });
  assert.equal(corba.grams, 250, "1 kase mercimek çorbası 250g olmalı");

  const kepceCorba = resolveFood({ text: "mercimek çorbası", unit: "kepçe" });
  assert.equal(kepceCorba.grams, 100, "1 kepçe çorba 100g olmalı");

  const ekmek = resolveFood({ text: "ekmek", quantity: 2, unit: "dilim" });
  assert.equal(ekmek.grams, 60, "2 dilim ekmek 60g olmalı");

  const simit = resolveFood({ text: "simit", quantity: 1, unit: "adet" });
  assert.equal(simit.grams, 100, "1 adet simit 100g olmalı");

  const iskender = resolveFood({ text: "iskender", quantity: 1.5, unit: "porsiyon" });
  assert.equal(iskender.grams, 480, "1.5 porsiyon iskender 480g olmalı");
});

test("Doğal dil ayrıştırıcı: '2 yumurta 2 dilim ekmek peynir'", async () => {
  const result = await parseNaturalMealText("2 yumurta 2 dilim ekmek peynir", false);
  assert.ok(result.items.length >= 3, `En az 3 yiyecek ayrışmalı, bulunan: ${result.items.length}`);
  const names = result.items.map((i) => i.foodId);
  assert.ok(names.includes("haslanmis-yumurta"));
  assert.ok(names.includes("beyaz-ekmek"));
  assert.ok(names.includes("beyaz-peynir"));

  const yumurta = result.items.find((i) => i.foodId === "haslanmis-yumurta");
  assert.equal(yumurta?.quantity, 2);
  assert.equal(yumurta?.grams, 100);

  const ekmek = result.items.find((i) => i.foodId === "beyaz-ekmek");
  assert.equal(ekmek?.quantity, 2);
  assert.equal(ekmek?.grams, 60);

  assert.ok(result.totals.calories > 300);
});

test("Doğal dil ayrıştırıcı: '1 tabak kuru fasulye pilav cacık'", async () => {
  const result = await parseNaturalMealText("1 tabak kuru fasulye pilav cacık", false);
  assert.ok(result.items.length >= 3, `3 yiyecek ayrışmalı: ${result.items.map((i) => i.name).join(", ")}`);
  const names = result.items.map((i) => i.foodId);
  assert.ok(names.includes("kuru-fasulye"));
  assert.ok(names.includes("pirinc-pilavi"));
  assert.ok(names.includes("cacik"));

  const fasulye = result.items.find((i) => i.foodId === "kuru-fasulye");
  assert.equal(fasulye?.grams, 300, "1 tabak kuru fasulye 300g olmalı");
});

test("Doğal dil ayrıştırıcı: 'yarım ekmek tavuk döner ayran'", async () => {
  const result = await parseNaturalMealText("yarım ekmek tavuk döner ayran", false);
  assert.ok(result.items.length >= 2);
  const names = result.items.map((i) => i.foodId);
  assert.ok(names.includes("tavuk-doner"));
  assert.ok(names.includes("ayran"));

  const doner = result.items.find((i) => i.foodId === "tavuk-doner");
  assert.ok(doner?.grams && doner.grams >= 200, "Yarım ekmek tavuk döner ~250g olmalı");
});

test("Doğal dil ayrıştırıcı: '1.5 porsiyon iskender'", async () => {
  const result = await parseNaturalMealText("1.5 porsiyon iskender", false);
  assert.equal(result.items.length, 1);
  assert.equal(result.items[0].foodId, "iskender-kebap");
  assert.equal(result.items[0].quantity, 1.5);
  assert.equal(result.items[0].grams, 480);
});

test("Doğal dil ayrıştırıcı: 'bir kase mercimek çorbası'", async () => {
  const result = await parseNaturalMealText("bir kase mercimek çorbası", false);
  assert.equal(result.items.length, 1);
  assert.equal(result.items[0].foodId, "mercimek-corbasi");
  assert.equal(result.items[0].grams, 250);
});

test("Doğal dil ayrıştırıcı: '200gr tavuk 150gr bulgur'", async () => {
  const result = await parseNaturalMealText("200gr tavuk 150gr bulgur", false);
  assert.ok(result.items.length >= 2);

  const tavuk = result.items.find((i) => i.name.toLowerCase().includes("tavuk"));
  assert.ok(tavuk);
  assert.equal(tavuk?.grams, 200);

  const bulgur = result.items.find((i) => i.foodId === "bulgur-pilavi");
  assert.ok(bulgur);
  assert.equal(bulgur?.grams, 150);
});

test("Doğal dil ayrıştırıcı: 'sucuklu yumurta ve 3 dilim ekmek'", async () => {
  const result = await parseNaturalMealText("sucuklu yumurta ve 3 dilim ekmek", false);
  assert.ok(result.items.length >= 2);
  const names = result.items.map((i) => i.foodId);
  assert.ok(names.includes("sucuklu-yumurta"));
  assert.ok(names.includes("beyaz-ekmek"));

  const ekmek = result.items.find((i) => i.foodId === "beyaz-ekmek");
  assert.equal(ekmek?.quantity, 3);
  assert.equal(ekmek?.grams, 90);
});

test("Doğal dil ayrıştırıcı: '2 tane kıymalı biber dolması yoğurt'", async () => {
  const result = await parseNaturalMealText("2 tane kıymalı biber dolması yoğurt", false);
  assert.ok(result.items.length >= 2);
  const names = result.items.map((i) => i.foodId);
  assert.ok(names.includes("biber-dolmasi"));
  assert.ok(names.includes("yogurt"));

  const dolma = result.items.find((i) => i.foodId === "biber-dolmasi");
  assert.equal(dolma?.quantity, 2);
  assert.equal(dolma?.grams, 240);
});

test("Doğal dil ayrıştırıcı: 'menemen simit çay'", async () => {
  const result = await parseNaturalMealText("menemen simit çay", false);
  assert.ok(result.items.length >= 3);
  const names = result.items.map((i) => i.foodId);
  assert.ok(names.includes("menemen"));
  assert.ok(names.includes("simit"));
  assert.ok(names.includes("cay"));
});

test("Doğal dil ayrıştırıcı: 'adana bulgur közlenmiş biber ayran'", async () => {
  const result = await parseNaturalMealText("adana bulgur közlenmiş biber ayran", false);
  assert.ok(result.items.length >= 3);
  const names = result.items.map((i) => i.foodId);
  assert.ok(names.includes("adana-kebap"));
  assert.ok(names.includes("bulgur-pilavi"));
  assert.ok(names.includes("ayran"));
});

test("Arama sistemi Türk yemeklerini ve alternatif yazımları anında bulur", () => {
  const cigKofteResults = searchDefaultFoods("cig kofte");
  assert.ok(cigKofteResults.some((f) => f.id === "cig-kofte"));

  const iskenderResults = searchDefaultFoods("iskender");
  assert.ok(iskenderResults.some((f) => f.id === "iskender-kebap"));

  const mantiResults = searchDefaultFoods("manti");
  assert.ok(mantiResults.some((f) => f.id === "manti"));

  const menemenResults = searchDefaultFoods("menemen");
  assert.ok(menemenResults.some((f) => f.id === "menemen"));
});
