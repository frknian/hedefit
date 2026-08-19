import assert from "node:assert/strict";
import test from "node:test";
import { encodePolyline, decodePolyline, haversineDistanceMeters, routeDistanceKm } from "../lib/polyline.ts";

test("encodePolyline bilinen Google örneğini üretir", () => {
  // Google Encoded Polyline Algorithm Format dokümantasyonundaki örnek.
  const points = [
    { lat: 38.5, lng: -120.2 },
    { lat: 40.7, lng: -120.95 },
    { lat: 43.252, lng: -126.453 },
  ];
  assert.equal(encodePolyline(points), "_p~iF~ps|U_ulLnnqC_mqNvxq`@");
});

test("decodePolyline encodePolyline'ın tersini alır (round-trip)", () => {
  const points = [
    { lat: 41.123456, lng: 29.123456 },
    { lat: 41.123556, lng: 29.123956 },
    { lat: 41.124556, lng: 29.124456 },
  ];
  const decoded = decodePolyline(encodePolyline(points));
  assert.equal(decoded.length, points.length);
  for (let i = 0; i < points.length; i++) {
    assert.ok(Math.abs(decoded[i].lat - points[i].lat) < 1e-5);
    assert.ok(Math.abs(decoded[i].lng - points[i].lng) < 1e-5);
  }
});

test("haversineDistanceMeters iki nokta arası mesafeyi doğru hesaplar", () => {
  // İstanbul (Kadıköy) - Ankara (Kızılay) yaklaşık 350 km.
  const kadikoy = { lat: 40.9909, lng: 29.0304 };
  const kizilay = { lat: 39.9208, lng: 32.8541 };
  const meters = haversineDistanceMeters(kadikoy, kizilay);
  assert.ok(meters > 330000 && meters < 360000, `beklenmedik mesafe: ${meters}`);
});

test("routeDistanceKm ardışık noktalar arası mesafeleri toplar", () => {
  const points = [
    { lat: 41.0, lng: 29.0 },
    { lat: 41.001, lng: 29.0 },
    { lat: 41.002, lng: 29.0 },
  ];
  const km = routeDistanceKm(points);
  const expected = (haversineDistanceMeters(points[0], points[1]) + haversineDistanceMeters(points[1], points[2])) / 1000;
  assert.ok(Math.abs(km - expected) < 1e-9);
});

test("routeDistanceKm tek nokta için sıfır döner", () => {
  assert.equal(routeDistanceKm([{ lat: 41, lng: 29 }]), 0);
  assert.equal(routeDistanceKm([]), 0);
});
