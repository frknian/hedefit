import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const [bell, mobile, app, manifest, capacitorConfig] = await Promise.all([
  readFile(new URL("../components/NotificationBell.tsx", import.meta.url), "utf8"),
  readFile(new URL("../lib/mobile.ts", import.meta.url), "utf8"),
  readFile(new URL("../components/FitAiApp.tsx", import.meta.url), "utf8"),
  readFile(new URL("../android/app/src/main/AndroidManifest.xml", import.meta.url), "utf8"),
  readFile(new URL("../capacitor.config.ts", import.meta.url), "utf8"),
]);

test("izin yalnızca kullanıcı zile bastığında istenir", () => {
  // Açılışta kendiliğinden sorulan izin tarayıcıda kalıcı "engellendi" ile
  // sonuçlanır ve o karar uygulama içinden geri alınamaz.
  assert.match(bell, /onClick=\{\(\) => void handleClick\(\)\}/);
  const effect = bell.slice(bell.indexOf("useEffect(() => {"), bell.indexOf("function flash"));
  assert.doesNotMatch(effect, /requestMobileNotificationPermission/, "izin efekt içinde istenmemeli");
  // Efekt yalnız mevcut durumu OKUR (refresh → mobileNotificationPermission).
  assert.match(effect, /refresh\(\);/, "durum okuma efektte olmalı");
  const refresh = bell.slice(bell.indexOf("const refresh = useCallback"), bell.indexOf("useEffect(() => {"));
  assert.match(refresh, /mobileNotificationPermission\(\)/);
  assert.doesNotMatch(refresh, /requestMobileNotificationPermission/);
});

test("zil üç platformda da aynı yardımcıdan geçer", () => {
  assert.match(bell, /import \{ isNativeApp, mobileNotificationPermission, requestMobileNotificationPermission \} from "@\/lib\/mobile"/);
  // Web: Notification API, yerel: Capacitor LocalNotifications (Android'de
  // POST_NOTIFICATIONS, iOS'ta UNUserNotificationCenter yetkilendirmesi).
  assert.match(mobile, /Notification\.requestPermission\(\)/);
  assert.match(mobile, /LocalNotifications\.requestPermissions\(\)/);
  assert.match(mobile, /LocalNotifications\.checkPermissions\(\)/);
});

test("dört izin durumunun da bir karşılığı vardır", () => {
  for (const state of ["granted", "denied", "unsupported", "default"]) {
    assert.ok(bell.includes(`"${state}"`), `${state} durumu ele alınmamış`);
  }
  // Reddedildiğinde kullanıcı çıkmazda bırakılmaz: yerel ve web için ayrı yol tarifi.
  assert.match(bell, /t\.notifications\.deniedNative/);
  assert.match(bell, /t\.notifications\.deniedWeb/);
  assert.match(bell, /t\.notifications\.unsupported/);
});

test("izin işletim sistemi ayarlarından değişirse zil güncellenir", () => {
  // Kullanıcı izni ayarlardan açıp uygulamaya dönebilir; zil eski durumu
  // göstermeye devam ederse "çalışmıyor" sanılır.
  assert.match(bell, /addEventListener\("focus", refresh\)/);
  assert.match(bell, /addEventListener\("visibilitychange", refresh\)/);
  assert.match(bell, /removeEventListener\("focus", refresh\)/);
  assert.match(bell, /removeEventListener\("visibilitychange", refresh\)/);
});

test("zil başlık çubuğuna bağlı ve hatırlatma ayarlarına götürür", () => {
  assert.match(app, /<NotificationBell onOpenSettings=\{\(\) => setActiveView\("calendar"\)\} \/>/);
});

test("Android ve iOS bildirim yapılandırması eksiksiz", () => {
  // Android 13+ bildirim için çalışma zamanı izni ister.
  assert.match(manifest, /android\.permission\.POST_NOTIFICATIONS/);
  // Bildirim ikonu tanımlıysa dosyası da olmalı; yoksa Android boş gri kare çizer.
  const smallIcon = capacitorConfig.match(/smallIcon: "([^"]+)"/)?.[1];
  assert.ok(smallIcon, "LocalNotifications.smallIcon tanımlı değil");
  assert.match(capacitorConfig, /iconColor: "#[0-9a-fA-F]{6}"/);
});
