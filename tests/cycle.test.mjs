import assert from "node:assert/strict";
import test from "node:test";
import { describeCycle, emptyCycleProfile, validateCycleProfile } from "../lib/cycle.ts";

const base = { trackingEnabled: true, lastPeriodStart: "2026-10-01", cycleLengthDays: 28, periodLengthDays: 5, regularity: "regular" };

test("takip kapalıysa ya da veri yoksa sonuç null (özellik zorunlu değil)", () => {
  assert.equal(describeCycle(emptyCycleProfile(), "2026-10-05"), null);
  assert.equal(describeCycle(null, "2026-10-05"), null);
  assert.equal(describeCycle({ ...base, trackingEnabled: false }, "2026-10-05"), null);
  assert.equal(describeCycle({ ...base, lastPeriodStart: null }, "2026-10-05"), null);
});

test("döngü günü ve faz: adet, foliküler, ovülasyon, luteal", () => {
  const at = (day) => describeCycle(base, `2026-10-${String(day).padStart(2, "0")}`);
  assert.equal(at(1).cycleDay, 1); assert.equal(at(1).phase, "menstrual"); assert.equal(at(1).periodLikely, true);
  assert.equal(at(5).phase, "menstrual");
  assert.equal(at(6).phase, "follicular");
  assert.equal(at(15).phase, "ovulatory");           // 28 günlük döngüde gün 14 ± 1
  assert.equal(describeCycle(base, "2026-10-20").phase, "luteal");
});

test("sonraki adet tahmini döngüler boyunca ilerler", () => {
  assert.equal(describeCycle(base, "2026-10-05").nextPeriodStart, "2026-10-29");
  assert.equal(describeCycle(base, "2026-10-05").daysToNextPeriod, 24);
  assert.equal(describeCycle(base, "2026-10-30").cycleDay, 2);
  assert.equal(describeCycle(base, "2026-10-30").nextPeriodStart, "2026-11-26");
});

test("adet öncesi pencere son 3 günde, kesin tahmin gerektirir", () => {
  assert.equal(describeCycle(base, "2026-10-27").preMenstrualWindow, true);
  assert.equal(describeCycle(base, "2026-10-15").preMenstrualWindow, false);
});

test("düzensiz döngüde faz TAHMİN EDİLMEZ", () => {
  const state = describeCycle({ ...base, regularity: "irregular" }, "2026-10-03");
  assert.equal(state.phase, null);
  assert.equal(state.periodLikely, false);
  assert.equal(state.preMenstrualWindow, false);
  assert.equal(state.confidence, "low");
});

test("az düzensiz döngü düşük güvenle tahmin eder; bayat veri tahmin etmez", () => {
  assert.equal(describeCycle({ ...base, regularity: "somewhat_irregular" }, "2026-10-03").confidence, "low");
  const stale = describeCycle(base, "2026-12-30");
  assert.equal(stale.stale, true);
  assert.equal(stale.phase, null);
});

test("gelecekteki ya da bozuk tarih null döner; uzunluk eksikse varsayılanlar kullanılır", () => {
  assert.equal(describeCycle({ ...base, lastPeriodStart: "2026-10-20" }, "2026-10-05"), null);
  assert.equal(describeCycle({ ...base, lastPeriodStart: "2026-02-31" }, "2026-10-05"), null);
  const defaults = describeCycle({ ...base, cycleLengthDays: null, periodLengthDays: null }, "2026-10-05");
  assert.equal(defaults.nextPeriodStart, "2026-10-29");
});

test("doğrulama: geçerli girdi, sınırlar ve opsiyonel alanlar", () => {
  const ok = validateCycleProfile({ trackingEnabled: true, lastPeriodStart: "2026-10-01", cycleLengthDays: 30, periodLengthDays: 6, regularity: "regular" }, "2026-10-05");
  assert.equal(ok.ok, true);
  assert.equal(ok.value.cycleLengthDays, 30);
  // takip kapalıyken hiçbir alan zorunlu değil
  assert.deepEqual(validateCycleProfile({ trackingEnabled: false }, "2026-10-05"), { ok: true, value: { trackingEnabled: false, lastPeriodStart: null, cycleLengthDays: null, periodLengthDays: null, regularity: "unknown" } });
  for (const [input, error] of [
    [{ trackingEnabled: true, cycleLengthDays: 10 }, "invalid_cycle_length"],
    [{ trackingEnabled: true, cycleLengthDays: 28.5 }, "invalid_cycle_length"],
    [{ trackingEnabled: true, periodLengthDays: 0 }, "invalid_period_length"],
    [{ trackingEnabled: true, cycleLengthDays: 22, periodLengthDays: 22 }, "invalid_period_length"],
    [{ trackingEnabled: true, lastPeriodStart: "2026-12-01" }, "invalid_last_period_start"],
    [{ trackingEnabled: true, lastPeriodStart: "2024-01-01" }, "invalid_last_period_start"],
    [{ trackingEnabled: true, lastPeriodStart: "yarın" }, "invalid_last_period_start"],
  ]) assert.deepEqual(validateCycleProfile(input, "2026-10-05"), { ok: false, error });
  assert.deepEqual(validateCycleProfile(null, "2026-10-05"), { ok: false, error: "invalid_body" });
});
