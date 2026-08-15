import assert from "node:assert/strict";
import test from "node:test";
import { rateLimit, __testOnlyTrackedKeyCount } from "../lib/rate-limit.ts";

test("aynı anahtar sınır içinde izin verir, aşılınca reddeder", () => {
  const key = `unit-${Date.now()}-basic`;
  assert.equal(rateLimit(key, 2, 60_000).ok, true);
  assert.equal(rateLimit(key, 2, 60_000).ok, true);
  const third = rateLimit(key, 2, 60_000);
  assert.equal(third.ok, false);
  assert.ok(third.retryAfterSeconds > 0);
});

test("izleme haritası MAX_TRACKED_KEYS'i aşınca en eski kovalar atılır, sınırsız büyümez", () => {
  const prefix = `unit-${Date.now()}-flood`;
  const firstKey = `${prefix}-0`;
  // İlk anahtarı sınırına kadar tüket (limit 1): ikinci çağrı reddedilmeli.
  assert.equal(rateLimit(firstKey, 1, 3_600_000).ok, true);
  assert.equal(rateLimit(firstKey, 1, 3_600_000).ok, false);

  // MAX_TRACKED_KEYS (10.000) kadar BAŞKA anahtarla haritayı taşır.
  // firstKey haritada en eski girdi olduğu için taşma sonrası atılmalı.
  for (let index = 1; index <= 10_000; index += 1) {
    rateLimit(`${prefix}-${index}`, 1000, 3_600_000);
  }

  // Atıldıysa firstKey artık "yeni" bir anahtar gibi davranır: ilk çağrı
  // tekrar ok:true döner (eski aşılmış sayaç hafızada kalmamıştır).
  const afterEviction = rateLimit(firstKey, 1, 3_600_000);
  assert.equal(afterEviction.ok, true, "en eski kova taşma sonrası atılmalıydı (sınırsız bellek büyümesi riski)");
});

test("izleme haritası hiçbir zaman MAX_TRACKED_KEYS'in 1 fazlasını geçmez", () => {
  // Zorlama noktası (enforceMaxTrackedKeys) her çağrının BAŞINDA çalışır; bu
  // yüzden bir çağrı sonrası boyut en fazla (zorlama öncesi en yüksek geçerli
  // durum) + 1 olabilir. Bu, sınırsız büyümeye karşı asıl garanti.
  const prefix = `unit-${Date.now()}-ceiling`;
  let maxObservedSize = 0;
  for (let index = 0; index < 10_001; index += 1) {
    rateLimit(`${prefix}-${index}`, 1000, 3_600_000);
    maxObservedSize = Math.max(maxObservedSize, __testOnlyTrackedKeyCount());
  }
  assert.ok(maxObservedSize <= 10_001, `harita hiçbir anda 10.001'i geçmemeli, gözlenen tepe: ${maxObservedSize}`);
});

test("izleme haritası tam sınıra değil, bir alt işaret çizgisine (watermark) kadar kırpılır — boşluk bırakır", () => {
  // Regresyon testi: eviction TAM MAX_TRACKED_KEYS'e (10.000) kırparsa, hemen
  // sonraki TEK ekleme haritayı yeniden sınırın üstüne çıkarıp O(n) süpürmeyi
  // neredeyse HER çağrıda yeniden tetikler. %90 işaret çizgisine kırpmak
  // gerçek bir boşluk bırakmalı. Bunu doğrudan gözlemlemek için boyutun bir
  // ÖNCEKİ çağrıya göre AZALDIĞI anı (yani kırpmanın gerçekten tetiklendiği
  // anı) yakalayıp o andaki değeri kontrol ediyoruz.
  const prefix = `unit-${Date.now()}-watermark`;
  let previousSize = __testOnlyTrackedKeyCount();
  let observedEviction = false;
  for (let index = 0; index < 3_000; index += 1) {
    rateLimit(`${prefix}-${index}`, 1000, 3_600_000);
    const size = __testOnlyTrackedKeyCount();
    if (size < previousSize) {
      // Kırpma bu çağrıda tetiklendi: yeni boyut, tam MAX_TRACKED_KEYS
      // (10.000) DEĞİL, belirgin ölçüde daha düşük (işaret çizgisine yakın)
      // olmalı — gerçek bir boşluk bıraktığının kanıtı.
      assert.ok(size <= 9_100, `kırpma sonrası boyut işaret çizgisine yakın olmalı, gerçek: ${size}`);
      observedEviction = true;
      break;
    }
    previousSize = size;
  }
  assert.ok(observedEviction, "3.000 ekleme içinde en az bir kırpma olayı gözlenmeliydi");
});
