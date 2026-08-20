import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";
import { formatDistanceKm, formatDuration, formatPace } from "../lib/activity-format.ts";

test("süre bir saatin altında dakika:saniye, üstünde saat hanesiyle yazılır", () => {
  assert.equal(formatDuration(0), "00:00");
  assert.equal(formatDuration(62_000), "01:02");
  assert.equal(formatDuration(38 * 60_000 + 22_000), "38:22");
  assert.equal(formatDuration(3_723_000), "1:02:03");
  // Negatif süre (saat kayması) sıfıra kırpılır, "-1:-5" gibi bir şey yazmaz.
  assert.equal(formatDuration(-5000), "00:00");
});

test("tempo kilometre başına dakika:saniye olarak hesaplanır", () => {
  // 2302 sn / 5,20 km = 442,7 sn/km → 7:23.
  assert.equal(formatPace(5.2, 38 * 60_000 + 22_000), "7:23 /km");
  assert.equal(formatPace(10, 50 * 60_000), "5:00 /km");
});

test("tempo hesaplanamayan durumlarda sonsuza gitmez", () => {
  assert.equal(formatPace(0, 600_000), "—", "mesafe yoksa tempo yok");
  assert.equal(formatPace(5, 0), "—", "süre yoksa tempo yok");
  // Birkaç metrelik GPS gürültüsü saatlerce süren bir "tempo" üretmemeli.
  assert.equal(formatPace(0.0001, 3_600_000), "—");
});

test("mesafe iki ondalıkla ve birimiyle yazılır", () => {
  assert.equal(formatDistanceKm(5.2), "5.20 km");
  assert.equal(formatDistanceKm(0), "0.00 km");
  assert.equal(formatDistanceKm(-3), "0.00 km");
});

test("paylaşım görseli rota ve sayıları aynı biçimlendiriciden alır", async () => {
  const card = await readFile(new URL("../lib/share-card.ts", import.meta.url), "utf8");
  assert.match(card, /formatPace/);
  assert.match(card, /formatDuration/);
  assert.match(card, /formatDistanceKm/);
  // Harita karesi alınamazsa (CORS, WebGL yok) rota yine de çizilir.
  assert.match(card, /drawRouteFallback/);
  assert.match(card, /decodePolyline/);
});

test("paylaşım katmanı native ve web yollarını ayrı ele alır", async () => {
  const share = await readFile(new URL("../lib/share-activity.ts", import.meta.url), "utf8");
  // Android WebView'da navigator.share yoktur; native yol eklentiden geçmeli.
  assert.match(share, /@capacitor\/share/);
  assert.match(share, /@capacitor\/filesystem/);
  assert.match(share, /navigator\.canShare/);
  assert.match(share, /downloadFallback/);
  // Kullanıcının paylaşımı iptal etmesi hata olarak raporlanmamalı.
  assert.match(share, /isCancellation/);
});

test("harita karesi dışa aktarılabilir olmalı, yoksa görsel boş çıkar", async () => {
  const map = await readFile(new URL("../components/GpsMapView.tsx", import.meta.url), "utf8");
  assert.match(map, /preserveDrawingBuffer/);
  assert.match(map, /toDataURL/);
  // Rota yeşil çizilir ve açık döşemelerde seçilsin diye beyaz hattı vardır.
  assert.match(map, /ROUTE_COLOR/);
  assert.match(map, /-halo/);
});
