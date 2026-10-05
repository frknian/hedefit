import assert from "node:assert/strict";
import test from "node:test";
import { HEDEFIT_ORIGINAL_ASSET, MODALITIES, MODALITY_LABELS, MODALITY_SUBCATEGORIES, SUBCATEGORY_LABELS, readAsset, readModalities, readSubcategories, validateAsset } from "../lib/exercise-modality.ts";
import { normalizeExercise } from "../lib/exercise-service.ts";

test("beş modalite ve belirtilen alt kategoriler tanımlı; her etiketin TR ve EN karşılığı var", () => {
  assert.deepEqual([...MODALITIES], ["pilates", "mobility", "barre", "low_impact", "recovery"]);
  assert.deepEqual([...MODALITY_SUBCATEGORIES.pilates], ["beginner", "full_body", "core", "lower_body", "upper_body", "posture", "short", "recovery"]);
  assert.deepEqual([...MODALITY_SUBCATEGORIES.mobility], ["full_body", "hip", "back", "shoulder", "morning", "evening"]);
  assert.deepEqual([...MODALITY_SUBCATEGORIES.barre], ["beginner", "lower_body", "core", "full_body", "balance_posture"]);
  assert.deepEqual([...MODALITY_SUBCATEGORIES.low_impact], ["full_body", "cardio", "beginner", "recovery"]);
  for (const modality of MODALITIES) {
    assert.ok(MODALITY_LABELS[modality].tr && MODALITY_LABELS[modality].en, modality);
    for (const sub of MODALITY_SUBCATEGORIES[modality]) assert.ok(SUBCATEGORY_LABELS[sub]?.tr && SUBCATEGORY_LABELS[sub]?.en, `${modality}/${sub}`);
  }
});

test("bilinmeyen modalite ve alt kategoriler atılır", () => {
  assert.deepEqual(readModalities(["pilates", "yoga", "pilates", 3]), ["pilates"]);
  assert.deepEqual(readSubcategories(["pilates"], ["core", "hip", "posture"]), ["core", "posture"]);
  assert.deepEqual(readModalities("pilates"), []);
});

test("lisans kaydı: kaynak, lisans ve ticari izin yoksa kabul edilmez", () => {
  assert.equal(validateAsset(undefined), "asset_missing");
  assert.equal(validateAsset({ source: "", license: "MIT", attribution: "", commercialUse: true }), "asset_source_missing");
  assert.equal(validateAsset({ source: "x", license: "", attribution: "", commercialUse: true }), "asset_license_missing");
  assert.equal(validateAsset({ source: "x", license: "CC BY-NC", attribution: "x", commercialUse: false }), "asset_not_commercial");
  assert.equal(validateAsset(HEDEFIT_ORIGINAL_ASSET), null);
  assert.equal(readAsset({ source: "x" }), undefined);
});

test("katalog satırı: yeni alanlar geçer, eski satırlar aynen kalır (geriye uyumlu)", () => {
  const legacy = normalizeExercise({ id: "plain", name: "Plain Move" });
  assert.equal(legacy.modalities, undefined);
  assert.equal(legacy.asset?.source, "RepDB", "kaynağı belirtilmeyen satır RepDB varsayılır ve atıf kaydı alır");
  const pilates = normalizeExercise({ id: "hundred", name: "The Hundred", modalities: ["pilates", "nonsense"], subcategories: ["core", "hip"], impact: "low", asset: HEDEFIT_ORIGINAL_ASSET, category: "strength" });
  assert.deepEqual(pilates.modalities, ["pilates"]);
  assert.deepEqual(pilates.subcategories, ["core"]);
  assert.equal(pilates.impact, "low");
  assert.equal(pilates.category, "strength", "category değişmez");
  assert.equal(pilates.asset.commercialUse, true);
});
