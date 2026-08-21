import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

// Kalori takibi sayfasında bir süre iki ayrı kalori göstergesi vardı:
// sayfanın en üstünde DailyEnergyRing (ana ekrandaki çemberin tam hâli) ve
// hemen altında sayfanın kendi orijinal "calorie-hero" özeti — aynı sayı
// iki farklı görselle tekrarlanıyordu. Üstteki kaldırıldı; antrenman/
// Hedefit Rota kalorisi artık tek göstergenin kendi hesabına dahil.
const tracker = await readFile(new URL("../components/CalorieTracker.tsx", import.meta.url), "utf8");

test("kalori takibinde tek gösterge var, DailyEnergyRing kaldırıldı", () => {
  assert.doesNotMatch(tracker, /<DailyEnergyRing/, "üstteki tekrar eden çember kaldırılmalı");
  assert.doesNotMatch(tracker, /import \{ DailyEnergyRing \}/, "kullanılmayan import da kalkmalı");
  assert.match(tracker, /className="calorie-hero"/, "sayfanın kendi orijinal özeti kalmalı");
});

test("antrenman/aktivite kalorisi tek göstergenin hesabına dahil, sadece bugün için", () => {
  assert.match(tracker, /const budget = nutritionGoal\.calorieTarget \+ \(dateOffset === 0 \? Math\.max\(0, burnedKcal\) : 0\);/, "geçmiş bir güne bugünün yakılan kalorisi uygulanmamalı");
  assert.match(tracker, /const remaining = Math\.max\(0, budget - totals\.calories\);/);
  assert.match(tracker, /const overBy = Math\.max\(0, totals\.calories - budget\);/);
});

test("yakılan kalori varsa kullanıcıya not olarak gösterilir", () => {
  assert.match(tracker, /dateOffset === 0 && burnedKcal > 0 && <p className="calorie-hero-burned">/);
});
