import assert from "node:assert/strict";
import test from "node:test";
import { applyStepCredit, combineStepSources, computeStepAdvice, estimateStepsFromDistance, mergeSessionSteps, stepsForToday } from "../lib/step-counter.ts";

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

test("hiç geçmiş yoksa tavsiye verilmez", () => {
  assert.deepEqual(computeStepAdvice([], 8000, "2026-08-20"), { kind: "noData" });
  // Dün için kayıt yoksa (ör. cihaz yeni bağlandı) da veri yok sayılır.
  assert.deepEqual(computeStepAdvice([{ localDate: "2026-08-15", steps: 5000 }], 8000, "2026-08-20"), { kind: "noData" });
});

test("dün hedefi aştıysa tebrik edilir", () => {
  const advice = computeStepAdvice([{ localDate: "2026-08-19", steps: 9000 }], 8000, "2026-08-20");
  assert.deepEqual(advice, { kind: "goalReached", steps: 9000, goal: 8000 });
});

test("dün ortalamanın belirgin altındaysa uyarılır (bugüne göre değil)", () => {
  const history = [
    { localDate: "2026-08-19", steps: 1500 }, // dün — düşük
    { localDate: "2026-08-18", steps: 8000 },
    { localDate: "2026-08-17", steps: 7000 },
    { localDate: "2026-08-16", steps: 9000 },
    { localDate: "2026-08-20", steps: 20 }, // bugün — henüz sürüyor, hesaba katılmamalı
  ];
  const advice = computeStepAdvice(history, 8000, "2026-08-20");
  assert.equal(advice.kind, "belowAverage");
  assert.equal(advice.yesterdaySteps, 1500);
  assert.equal(advice.averageSteps, 8000);
});

test("dün ortalamaya yakınsa yolunda sayılır", () => {
  const history = [
    { localDate: "2026-08-19", steps: 7500 },
    { localDate: "2026-08-18", steps: 8000 },
    { localDate: "2026-08-17", steps: 7000 },
  ];
  const advice = computeStepAdvice(history, 8000, "2026-08-20");
  assert.deepEqual(advice, { kind: "onTrack", yesterdaySteps: 7500, averageSteps: 7500 });
});

test("önceki günler yoksa dün tek başına ortalama kabul edilir", () => {
  const advice = computeStepAdvice([{ localDate: "2026-08-19", steps: 3000 }], 8000, "2026-08-20");
  assert.deepEqual(advice, { kind: "onTrack", yesterdaySteps: 3000, averageSteps: 3000 });
});

test("ay/yıl sınırında gün kaydırma doğru çalışır", () => {
  // 1 Eylül'ün dünü 31 Ağustos olmalı; basit string aritmetiği bunu bozardı.
  const advice = computeStepAdvice([{ localDate: "2026-08-31", steps: 9500 }], 8000, "2026-09-01");
  assert.equal(advice.kind, "goalReached");
});

test("mesafeden adım tahmini yürüyüş ve koşuda farklı adım uzunluğu kullanır", () => {
  // 3 km yürüyüş: 3000 / 0.75 = 4000 adım.
  assert.equal(estimateStepsFromDistance(3, "walking"), 4000);
  // 5 km koşu: 5000 / 1.00 = 5000 adım.
  assert.equal(estimateStepsFromDistance(5, "running"), 5000);
  assert.equal(estimateStepsFromDistance(0, "walking"), 0);
  assert.equal(estimateStepsFromDistance(-2, "walking"), 0, "negatif mesafe adım üretmemeli");
});

test("aktivite kredisi günün toplamına eklenir, gün değişmişse sıfırdan başlar", () => {
  const today = applyStepCredit({ localDate: "2026-08-20", steps: 2000 }, "2026-08-20", 4000);
  assert.deepEqual(today, { localDate: "2026-08-20", steps: 6000 });

  const newDay = applyStepCredit({ localDate: "2026-08-19", steps: 9000 }, "2026-08-20", 4000);
  assert.deepEqual(newDay, { localDate: "2026-08-20", steps: 4000 }, "dünün adımı bugüne taşınmamalı");

  const fresh = applyStepCredit(null, "2026-08-20", 1200);
  assert.deepEqual(fresh, { localDate: "2026-08-20", steps: 1200 });
});
