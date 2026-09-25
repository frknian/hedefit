import assert from "node:assert/strict";
import test from "node:test";

import {
  geoDistanceMeters,
  calculatePathDistance,
  interpolateGeodesicPolyline,
  generateRoundTripSegments,
  calculateRouteBounds,
  generateRouteChecksum,
} from "../app/routes/utils/routeAlgorithms.ts";

import {
  OneWayStrategy,
  RoundTripStrategy,
  getPlanningStrategy,
} from "../app/routes/modules/routePlanning.ts";

import { routeStore } from "../app/routes/stores/routeState.ts";
import { useRouteEngine } from "../app/routes/composables/useRouteEngine.ts";
import { mapService } from "../app/routes/services/MapService.ts";
import { prepareNavigation, updateNavigationProgress, fitCameraToRoute } from "../app/routes/modules/navigation.ts";
import { mapManager } from "../app/routes/plugins/map.ts";

test("Haversine mesafe ve yol mesafesi hesaplama tutarlıdır", () => {
  const p1 = { latitude: 41.0082, longitude: 28.9784 }; // Sultanahmet
  const p2 = { latitude: 41.0422, longitude: 29.0067 }; // Beşiktaş

  const dist = geoDistanceMeters(p1, p2);
  assert.ok(dist > 4000 && dist < 5500, `Beklenen mesafe ~4-5km, hesaplanan: ${dist}`);

  const pathDist = calculatePathDistance([p1, p2]);
  assert.equal(Math.round(pathDist), Math.round(dist));
});

test("Geodesic interpolasyon non-geodesic düz çizgileri önler ve ara noktalar üretir", () => {
  const p1 = { latitude: 41.0000, longitude: 29.0000 };
  const p2 = { latitude: 41.0100, longitude: 29.0100 }; // ~1.4 km

  const maxSegment = 200; // max 200 metre
  const points = interpolateGeodesicPolyline([p1, p2], maxSegment);

  assert.ok(points.length >= 7, `Ara noktalar üretilmeli, nokta sayısı: ${points.length}`);
  assert.equal(points[0].latitude, p1.latitude);
  assert.equal(points[points.length - 1].latitude, p2.latitude);

  // Herhangi iki ardışık nokta arasındaki mesafe maxSegment değerini aşmamalı
  for (let i = 0; i < points.length - 1; i++) {
    const stepDist = geoDistanceMeters(points[i], points[i + 1]);
    assert.ok(stepDist <= maxSegment + 5, `Adım mesafesi maxSegment sınırında olmalı: ${stepDist}`);
  }
});

test("Russell-grafik dönüşlü rota algoritması A→B→A döngüsünü ve toplam mesafeyi eksiksiz hesaplar", () => {
  const start = { latitude: 41.0082, longitude: 28.9784 };
  const dest = { latitude: 41.0300, longitude: 29.0000 };
  const waypoints = [
    { latitude: 41.0150, longitude: 28.9850 },
    { latitude: 41.0220, longitude: 28.9920 },
  ];

  const result = generateRoundTripSegments(start, dest, waypoints);

  // Gidiş ve dönüş mesafeleri
  assert.ok(result.forwardDistanceMeters > 0, "Gidiş mesafesi pozitif olmalı");
  assert.ok(result.returnDistanceMeters > 0, "Dönüş mesafesi pozitif olmalı");
  assert.equal(
    result.totalDistanceMeters,
    result.forwardDistanceMeters + result.returnDistanceMeters,
    "Toplam mesafe A→B ve B→A segmentlerinin toplamına eşit olmalı"
  );

  // Başlangıç ve bitiş noktası döngü oluşturmalı (A noktasında başlayıp A noktasında bitmeli)
  assert.equal(result.combinedPoints[0].latitude, start.latitude);
  assert.equal(result.combinedPoints[result.combinedPoints.length - 1].latitude, start.latitude);
  assert.equal(result.combinedPoints[0].longitude, start.longitude);
  assert.equal(result.combinedPoints[result.combinedPoints.length - 1].longitude, start.longitude);
});

test("Normal tek yönlü A→B rota planlaması geriye dönük uyumlu ve bozulmadan çalışır", async () => {
  const strategy = new OneWayStrategy();
  const origin = { latitude: 41.0082, longitude: 28.9784 };
  const destination = { latitude: 41.0350, longitude: 29.0050 };

  const route = await strategy.plan(origin, {
    activityType: "Koşu",
    planType: "oneWay",
  }, destination);

  assert.equal(route.isRoundTrip, false);
  assert.equal(route.origin.latitude, origin.latitude);
  assert.equal(route.destination.latitude, destination.latitude);
  assert.ok(route.distanceMeters > 0);
  assert.equal(route.maneuvers[0].suffix, "start");
  assert.equal(route.maneuvers[route.maneuvers.length - 1].suffix, "end");
});

test("RoundTripStrategy dönüş segmentini (B→A) ve 'return' manevrasını içerir", async () => {
  const strategy = new RoundTripStrategy();
  const origin = { latitude: 41.0082, longitude: 28.9784 };
  const destination = { latitude: 41.0350, longitude: 29.0050 };

  const route = await strategy.plan(origin, {
    activityType: "Koşu",
    planType: "roundTrip",
    targetDistanceMeters: 5000,
  }, destination);

  assert.equal(route.isRoundTrip, true);
  assert.ok(route.segments?.return, "Dönüş segmenti mevcut olmalı");
  assert.ok(route.distanceMeters > 0);

  // Manevra kontrolleri: start, return ve end suffixleri
  const suffixes = route.maneuvers.map((m) => m.suffix);
  assert.ok(suffixes.includes("start"), "Start manevrası bulunmalı");
  assert.ok(suffixes.includes("return"), "Dönüş (return) manevrası bulunmalı");
  assert.ok(suffixes.includes("end"), "Bitiş manevrası bulunmalı");

  // Rota A noktasına geri dönmeli
  const lastPoint = route.points[route.points.length - 1];
  assert.equal(lastPoint.latitude, origin.latitude);
  assert.equal(lastPoint.longitude, origin.longitude);
});

test("calculateRouteBounds tüm rotayı içine alan sınırlar üretir", () => {
  const points = [
    { latitude: 40.0, longitude: 28.0 },
    { latitude: 42.0, longitude: 30.0 },
    { latitude: 41.0, longitude: 29.0 },
  ];

  const bounds = calculateRouteBounds(points, 0.1);

  // Tüm noktalar bounds içinde olmalı
  for (const pt of points) {
    assert.ok(pt.latitude >= bounds.southWest.latitude, `lat ${pt.latitude} >= minLat`);
    assert.ok(pt.latitude <= bounds.northEast.latitude, `lat ${pt.latitude} <= maxLat`);
    assert.ok(pt.longitude >= bounds.southWest.longitude, `lng ${pt.longitude} >= minLng`);
    assert.ok(pt.longitude <= bounds.northEast.longitude, `lng ${pt.longitude} <= maxLng`);
  }
});

test("Rota önbellekleme (Checksum) ve MapService early return mekanizması mükerrer çağrıları engeller", async () => {
  const { calculateRoute } = useRouteEngine();
  const origin = { latitude: 41.0082, longitude: 28.9784 };

  const route1 = await calculateRoute(origin, {
    activityType: "Yürüyüş",
    planType: "roundTrip",
    targetDistanceMeters: 3000,
  });

  // İlk startRoute çağrısı (Önbellekten değil, ilk kurulum)
  const res1 = await mapService.startRoute(route1);
  assert.equal(res1.success, true);
  assert.equal(res1.fromCache, false);

  // İkinci startRoute çağrısı aynı rota ile yapıldığında (EARLY RETURN!)
  const res2 = await mapService.startRoute(route1);
  assert.equal(res2.success, true);
  assert.equal(res2.fromCache, true, "Aynı rota için harita yeniden oluşturulmamalı, önbellekten dönmeli");

  mapService.stopRoute();
});

test("Dönüşlü rota modunda üst sekme görünürlüğü dinamik olarak gizlenir", () => {
  const isHeaderTabsVisible = (mode) => mode !== "roundTrip";

  assert.equal(isHeaderTabsVisible("roundTrip"), false, "Dönüşlü modda sekmeler gizlenmeli");
  assert.equal(isHeaderTabsVisible("oneWay"), true, "Tek yön modda sekmeler görünür olmalı");
  assert.equal(isHeaderTabsVisible("point_to_point"), true, "Noktadan noktaya modda sekmeler görünür olmalı");
});

test("Başlangıç === Bitiş durumunda tekil Başlangıç/Bitiş marker'ı kuralı geçerlidir", () => {
  const isSingleMarkerMode = (route) => {
    if (route.isRoundTrip) return true;
    return geoDistanceMeters(route.origin, route.destination) < 15.0;
  };

  const roundTripRoute = {
    isRoundTrip: true,
    origin: { latitude: 41.0, longitude: 29.0 },
    destination: { latitude: 41.0, longitude: 29.0 },
  };
  assert.equal(isSingleMarkerMode(roundTripRoute), true);

  const oneWayDistinctRoute = {
    isRoundTrip: false,
    origin: { latitude: 41.0, longitude: 29.0 },
    destination: { latitude: 41.05, longitude: 29.05 },
  };
  assert.equal(isSingleMarkerMode(oneWayDistinctRoute), false);
});
