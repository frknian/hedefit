import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";
import { isUsableGpsPoint, MAX_ACCEPTABLE_ACCURACY_M, MIN_MOVING_SPEED_MPS, smoothSpeedMps } from "../lib/gps-smoothing.ts";

test("kötü doğrulukta gelen noktalar reddedilir", () => {
  assert.equal(isUsableGpsPoint({ accuracy: 5 }), true);
  assert.equal(isUsableGpsPoint({ accuracy: MAX_ACCEPTABLE_ACCURACY_M }), true, "eşiğin kendisi kabul edilmeli");
  assert.equal(isUsableGpsPoint({ accuracy: MAX_ACCEPTABLE_ACCURACY_M + 0.1 }), false);
  assert.equal(isUsableGpsPoint({ accuracy: 120 }), false, "bina içi gibi çok kötü doğruluk reddedilmeli");
  assert.equal(isUsableGpsPoint({ accuracy: NaN }), false);
});

test("hız EMA ile yumuşatılır, ani sıçrama yapmaz", () => {
  // Duran sensörden birden 5 m/s (18 km/s) gelirse, gösterilen değer o ana
  // kadar sıçramamalı — bu "sallanma" hissinin tam kaynağı.
  let speed = 0;
  speed = smoothSpeedMps(speed, 5);
  assert.ok(speed > 0 && speed < 2, `ilk adımda tam sıçramamalı, aldı: ${speed}`);
  speed = smoothSpeedMps(speed, 5);
  speed = smoothSpeedMps(speed, 5);
  speed = smoothSpeedMps(speed, 5);
  assert.ok(speed > 3, "birkaç ardışık aynı okumadan sonra gerçek hıza yaklaşmalı");
});

test("GPS-drift eşiğinin altındaki hız sıfıra kilitlenir", () => {
  // Telefon masada dururken GPS zaman zaman 0.1-0.2 m/s gibi sahte bir hız
  // bildirir; bu "yürüyormuş gibi" göstermemeli.
  const speed = smoothSpeedMps(0, MIN_MOVING_SPEED_MPS - 0.05);
  assert.equal(speed, 0);
});

test("ham hız yoksa (null) önceki değer korunur, sıfıra düşmez", () => {
  const speed = smoothSpeedMps(2.4, null);
  assert.equal(speed, 2.4);
});

test("negatif ham hız (bazı cihaz tuhaflıkları) sıfır kabul edilir", () => {
  const speed = smoothSpeedMps(1, -3);
  assert.ok(speed < 1 && speed >= 0);
});

test("canlı takip ekranı ham hız yerine yumuşatılmış hızı gösterir", async () => {
  const tracker = await readFile(new URL("../components/GpsActivityTracker.tsx", import.meta.url), "utf8");
  assert.match(tracker, /const currentSpeedKmh = displaySpeedMps \* 3\.6;/, "ham lastPoint.speedMps kullanılmamalı — GPS driftinden dolayı sallanır");
  assert.match(tracker, /isUsableGpsPoint\(point\)/, "kötü doğruluktaki noktalar rotaya eklenmeden reddedilmeli");
  assert.match(tracker, /smoothSpeedMps\(smoothedSpeedRef\.current, point\.speedMps\)/);
});
