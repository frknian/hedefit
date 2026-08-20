import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

// Bu testler kullanıcının cihazda gerçekten yaşadığı iki hatayı bir daha
// üretmemek için var:
//   1) Ekran kapanıp WebView süreci öldürülünce canlı rota tamamen kayboldu
//      ve uygulama "yeniden başlamış" gibi göründü.
//   2) Yeterli GPS noktası toplanmadığında (ör. cihaz neredeyse hiç
//      hareket etmedi) harita boş kaldı ve paylaşım hiçbir şey üretmeden
//      sessizce başarısız oldu.
const tracker = await readFile(new URL("../components/GpsActivityTracker.tsx", import.meta.url), "utf8");

test("canlı rota her yeni noktada diske yazılır", () => {
  assert.match(tracker, /writePersistedGpsSession\(\{ activityKey, startedAt, points, hrSamples \}\)/);
  assert.match(tracker, /useEffect\(\(\) => \{\s*if \(!startedAt \|\| phase === "idle" \|\| phase === "unavailable"\) return;\s*writePersistedGpsSession/);
});

test("uygulama yeniden açılınca yarım kalan oturum geri yüklenir", () => {
  assert.match(tracker, /readPersistedGpsSession\(\)/);
  assert.match(tracker, /useState<Phase>\(\(\) => \(recovered \? "paused" : isGpsTrackingAvailable/, "geri yüklenen oturum doğrudan duraklatıldı durumunda açılmalı, sıfırdan başlamamalı");
  assert.match(tracker, /sessionRecovered/, "kullanıcı geri yüklemenin farkında olmalı");
});

test("kayıt başarıyla tamamlanınca diskteki oturum temizlenir", () => {
  const saveStart = tracker.indexOf("async function handleSave");
  const saveBody = tracker.slice(saveStart, tracker.indexOf("\n  }\n", saveStart));
  assert.match(saveBody, /clearPersistedGpsSession\(\)/);
});

test("çok az GPS noktası varsa paylaşım sessizce başarısız olmaz", () => {
  const shareStart = tracker.indexOf("async function handleShare");
  const shareBody = tracker.slice(shareStart, tracker.indexOf("\n  }\n", shareStart));
  assert.match(shareBody, /route\.length < 2/, "yetersiz nokta erken ve açıkça reddedilmeli");
  assert.match(shareBody, /errorRouteTooShort/);
});

test("özet haritası boşsa kullanıcıya sebebi söylenir", () => {
  assert.match(tracker, /route\.length < 2 && <p className="gps-tracker-route-warning">/);
});
