import assert from "node:assert/strict";
import test from "node:test";
import { shouldRefetchMealAdvice } from "../lib/nutrition-advice.ts";

// CalorieTracker'da bu istek her öğün eklendiğinde otomatik tetikleniyor ve
// nutrition_advice günlük kotasından düşüyordu (bkz. app/api/nutrition/advice/route.ts).
// Öncesinde totals her değiştiğinde (yani her yemek girişinde) yeniden
// isteniyordu; ücretsiz kullanıcının günlük 5 hakkı tek bir öğün ekleme
// oturumunda tükenebiliyordu.

const baseTotals = { calories: 1200, protein: 60, carbs: 120, fat: 30 };
const baseTargets = { calorieTarget: 2000, proteinTarget: 120, carbsTarget: 200, fatTarget: 60 };
const base = { date: "2026-08-12", totals: baseTotals, targets: baseTargets, locale: "tr", revision: 0 };

test("shouldRefetchMealAdvice: normal durum — eşiğin (1200 kcal'nin %5'i + 1 = 61) altında kalan küçük bir değişiklik yeniden istemez", () => {
  const next = { ...base, totals: { ...baseTotals, calories: baseTotals.calories + 30 } };
  assert.equal(shouldRefetchMealAdvice(base, next), false);
});

test("shouldRefetchMealAdvice: normal durum — anlamlı bir değişiklik (%5'ten fazla kalori) yeniden ister", () => {
  // 1200 kcal'nin %5'i 60 + 1 taban değeri = 61; 150 kcal'lik bir öğün bunu aşar.
  const next = { ...base, totals: { ...baseTotals, calories: baseTotals.calories + 150 } };
  assert.equal(shouldRefetchMealAdvice(base, next), true);
});

test("shouldRefetchMealAdvice: edge case — hiçbir şey değişmediyse yeniden istemez", () => {
  const next = { ...base, totals: { ...baseTotals }, targets: { ...baseTargets } };
  assert.equal(shouldRefetchMealAdvice(base, next), false);
});

test("shouldRefetchMealAdvice: edge case — gün değişimi, toplamlar aynı kalsa bile her zaman yeniden ister", () => {
  const next = { ...base, date: "2026-08-13" };
  assert.equal(shouldRefetchMealAdvice(base, next), true);
});

test("shouldRefetchMealAdvice: edge case — 'Yenile' düğmesi (revision artışı) değişiklik olmasa bile zorunlu kılar", () => {
  const next = { ...base, revision: 1 };
  assert.equal(shouldRefetchMealAdvice(base, next), true);
});

test("shouldRefetchMealAdvice: edge case — revision AYNI kalınca (tek tıklamadan sonraki normal değişiklikler) tekrar eşik atlamaz", () => {
  // Regresyon testi: eski kod `adviceRevision > 0` gibi sabit bir eşik
  // kullanıyordu; bu, İLK "Yenile" tıklamasından SONRA kalıcı olarak devreye
  // girip debounce/eşik kontrolünü sonsuza dek atlıyordu. Burada revision=1
  // olarak SABİT kalan iki ardışık çağrı arasında küçük bir değişiklik varsa
  // hâlâ atlanmalı.
  const afterManualRefresh = { ...base, revision: 1 };
  const smallChangeSameRevision = { ...afterManualRefresh, totals: { ...baseTotals, calories: baseTotals.calories + 10 } };
  assert.equal(shouldRefetchMealAdvice(afterManualRefresh, smallChangeSameRevision), false);
});

test("shouldRefetchMealAdvice: edge case — dil (locale) değişimi, toplamlar aynı kalsa bile her zaman yeniden ister", () => {
  // Regresyon testi: locale/targets önceden hiç izlenmiyordu; kullanıcı dili
  // değiştirince eski dildeki öneri metni ekranda takılı kalıyordu.
  const next = { ...base, locale: "en" };
  assert.equal(shouldRefetchMealAdvice(base, next), true);
});

test("shouldRefetchMealAdvice: edge case — besin hedefi (targets) değişimi, toplamlar aynı kalsa bile her zaman yeniden ister", () => {
  const next = { ...base, targets: { ...baseTargets, calorieTarget: 1800 } };
  assert.equal(shouldRefetchMealAdvice(base, next), true);
});

test("shouldRefetchMealAdvice: hatalı input — önceki durum yoksa (ilk çağrı) her zaman istek atılır", () => {
  assert.equal(shouldRefetchMealAdvice(null, base), true);
});

test("shouldRefetchMealAdvice: hatalı input — sıfıra yakın toplamlarda taban değer eşiği koruma altına alır", () => {
  // Taban formülü Math.max(current, before) * 0.05 + 1: toplamlar 0'a
  // yakınken salt yüzdesel eşik neredeyse sıfıra iner ve her ufak
  // yuvarlama/gecikme farkında bile yeniden istek atardı. +1 taban değeri
  // bunu önlüyor.
  const zeroBase = { ...base, totals: { calories: 0, protein: 0, carbs: 0, fat: 0 } };
  const tinyChange = { ...zeroBase, totals: { calories: 0.5, protein: 0, carbs: 0, fat: 0 } };
  assert.equal(shouldRefetchMealAdvice(zeroBase, tinyChange), false);
});
