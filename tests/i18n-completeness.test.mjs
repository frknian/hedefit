import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import { MODALITIES, MODALITY_LABELS, MODALITY_SUBCATEGORIES, SUBCATEGORY_LABELS } from "../lib/exercise-modality.ts";
import { buildNutritionWellness } from "../lib/nutrition-wellness.ts";

const wellness = JSON.parse(readFileSync(new URL("../data/exercises-wellness.json", import.meta.url), "utf8"));
const turkish = /[ğüşıöçĞÜŞİÖÇ]/;

test("kategori ve alt kategori etiketleri her iki dilde var ve boş değil", () => {
  for (const modality of MODALITIES) {
    assert.ok(MODALITY_LABELS[modality].tr && MODALITY_LABELS[modality].en, modality);
    for (const sub of MODALITY_SUBCATEGORIES[modality]) assert.ok(SUBCATEGORY_LABELS[sub]?.tr && SUBCATEGORY_LABELS[sub]?.en, `${modality}/${sub}`);
  }
});

test("wellness hareketleri: Türkçe ad, İngilizce talimat; İngilizce alanda Türkçe karakter yok", () => {
  for (const exercise of wellness) {
    assert.ok(exercise.nameTr, exercise.id);
    assert.ok(exercise.instructions?.length, exercise.id);
    assert.doesNotMatch(`${exercise.name} ${exercise.instructions.join(" ")}`, turkish, `${exercise.id}: EN alanında Türkçe karakter`);
  }
});

test("beslenme ipuçları: TR ve EN çıktıları dolu, farklı ve dile uygun", () => {
  const input = { tier: "full", diet: "standard", training: { workedOutToday: true, level: "low", minutes: 20 }, cycle: { periodLikely: true, preMenstrualWindow: false } };
  const tr = buildNutritionWellness({ ...input, locale: "tr" }).tips;
  const en = buildNutritionWellness({ ...input, locale: "en" }).tips;
  assert.equal(tr.length, en.length);
  tr.forEach((tip, index) => {
    assert.ok(tip.title && tip.body && en[index].title && en[index].body);
    assert.notEqual(tip.body, en[index].body);
    assert.doesNotMatch(`${en[index].title} ${en[index].body}`, turkish, tip.id);
  });
});
