import assert from "node:assert/strict";
import test from "node:test";
import { combineStepSources, mergeSessionSteps, stepsForToday } from "../lib/step-counter.ts";

test("oturumdaki artış günlük toplama eklenir", () => {
  const first = mergeSessionSteps({ stored: null, todayKey: "2026-08-20", sessionSteps: 120, lastSessionSteps: 0 });
  assert.deepEqual(first, { localDate: "2026-08-20", steps: 120 });

  const second = mergeSessionSteps({ stored: first, todayKey: "2026-08-20", sessionSteps: 200, lastSessionSteps: 120 });
  assert.deepEqual(second, { localDate: "2026-08-20", steps: 200 }, "yalnız 80'lik artış eklenmeli");
});

test("gün değişince sayaç sıfırdan başlar", () => {
  const yesterday = { localDate: "2026-08-19", steps: 8400 };
  const today = mergeSessionSteps({ stored: yesterday, todayKey: "2026-08-20", sessionSteps: 50, lastSessionSteps: 0 });
  assert.deepEqual(today, { localDate: "2026-08-20", steps: 50 }, "dünün adımı bugüne taşınmamalı");
});

test("uygulama yeniden başlayınca birikmiş toplam korunur", () => {
  // Yeni oturumda eklentinin sayacı 0'dan başlar; kayıtlı toplam kaybolmamalı.
  const stored = { localDate: "2026-08-20", steps: 3000 };
  const next = mergeSessionSteps({ stored, todayKey: "2026-08-20", sessionSteps: 40, lastSessionSteps: 0 });
  assert.equal(next.steps, 3040);
});

test("oturum sayacı geri giderse adım eksilmez", () => {
  // Eklenti yeniden başlatıldığında sessionSteps küçülebilir; toplam düşmemeli.
  const stored = { localDate: "2026-08-20", steps: 5000 };
  const next = mergeSessionSteps({ stored, todayKey: "2026-08-20", sessionSteps: 10, lastSessionSteps: 900 });
  assert.equal(next.steps, 5010);
  assert.ok(next.steps >= stored.steps, "toplam asla azalmamalı");
});

test("bugünün adımı yalnız bugüne ait kayıttan okunur", () => {
  assert.equal(stepsForToday({ localDate: "2026-08-20", steps: 700 }, "2026-08-20"), 700);
  assert.equal(stepsForToday({ localDate: "2026-08-19", steps: 700 }, "2026-08-20"), 0);
  assert.equal(stepsForToday(null, "2026-08-20"), 0);
});

test("iki kaynak toplanmaz, büyük olan alınır", () => {
  // Aynı adımlar hem cihazda hem sağlıkta görünür; toplamak sayıyı ikiye katlardı.
  assert.equal(combineStepSources(3000, 7200), 7200, "sağlık daha eksiksizse o kazanır");
  assert.equal(combineStepSources(9000, 4000), 9000, "cihaz daha ilerideyse o kazanır");
  assert.equal(combineStepSources(2500, null), 2500, "sağlık bağlı değilse cihaz yeterli");
  assert.equal(combineStepSources(0, 0), 0);
});
