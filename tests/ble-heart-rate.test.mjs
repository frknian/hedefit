import assert from "node:assert/strict";
import test from "node:test";
import { parseHeartRateMeasurement } from "../lib/ble-heart-rate.ts";

test("uint8 formatındaki nabız değerini okur (flags bit0 = 0)", () => {
  const bytes = new Uint8Array([0x00, 72]);
  assert.equal(parseHeartRateMeasurement(bytes), 72);
});

test("uint16 formatındaki nabız değerini okur (flags bit0 = 1)", () => {
  // 300 bpm = 0x012C, little-endian: [0x2C, 0x01]
  const bytes = new Uint8Array([0x01, 0x2c, 0x01]);
  assert.equal(parseHeartRateMeasurement(bytes), 300);
});

test("sensör teması / enerji harcaması bitleri sonucu etkilemez", () => {
  const bytes = new Uint8Array([0b00001110, 150]);
  assert.equal(parseHeartRateMeasurement(bytes), 150);
});

test("DataView girdisini de kabul eder", () => {
  const buffer = new ArrayBuffer(2);
  const view = new DataView(buffer);
  view.setUint8(0, 0x00);
  view.setUint8(1, 88);
  assert.equal(parseHeartRateMeasurement(view), 88);
});

test("eksik veri hata fırlatır", () => {
  assert.throws(() => parseHeartRateMeasurement(new Uint8Array([0x00])));
});
