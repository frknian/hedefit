import { registerPlugin } from "@capacitor/core";
import type { BackgroundGeolocationPlugin, Location } from "@capacitor-community/background-geolocation";
import { Geolocation } from "@capacitor/geolocation";
import { isNativeApp, requestMobileNotificationPermission } from "./mobile.ts";

// Bu eklenti JS tarafında hiçbir modül dışa aktarmaz (yalnızca native
// kaynak + tip tanımları); resmi kullanım şekli budur, bkz. paketin README'i.
const BackgroundGeolocation = registerPlugin<BackgroundGeolocationPlugin>("BackgroundGeolocation");

export type TrackedPoint = {
  lat: number;
  lng: number;
  accuracy: number;
  altitude: number | null;
  speedMps: number | null;
  timeMs: number;
};

export type GpsPermissionStatus = "idle" | "checking" | "prompt" | "granted" | "denied" | "unavailable";

const STORAGE_KEY = "hedefit:gps-permission-prompted";

function toTrackedPoint(location: Location): TrackedPoint {
  return {
    lat: location.latitude,
    lng: location.longitude,
    accuracy: location.accuracy,
    altitude: location.altitude,
    speedMps: location.speed,
    timeMs: location.time ?? Date.now(),
  };
}

/** Daha önce izin istendiyse (kabul ya da red fark etmez) true döner; tekrar tekrar sormamak için kullanılır. */
export function hasPromptedGpsPermission(): boolean {
  if (typeof window === "undefined") return false;
  return window.localStorage.getItem(STORAGE_KEY) === "1";
}

function markGpsPermissionPrompted() {
  if (typeof window !== "undefined") window.localStorage.setItem(STORAGE_KEY, "1");
}

/** GPS takibi bu platformda mümkün mü? Web'de yalnızca ön planda navigator.geolocation ile çalışır. */
export function isGpsTrackingAvailable(): boolean {
  if (isNativeApp()) return true;
  return typeof navigator !== "undefined" && "geolocation" in navigator;
}

/**
 * Arka plan konum iznini ister. Android 10+ bunu ön plan izniyle AYNI anda
 * vermez: önce "uygulamayı kullanırken" alınmalı, ardından ikinci bir istek
 * "her zaman"a yükseltir. Tek adımda istenirse sistem sessizce reddeder ve
 * ekran kapanınca takip durur — gerçek cihazda bu iznin verilmemiş olduğu
 * görüldü. Reddedilirse takip yine de ön planda çalışmaya devam eder.
 */
export async function ensureBackgroundLocationPermission(): Promise<boolean> {
  if (!isNativeApp()) return false;
  try {
    const current = await Geolocation.checkPermissions();
    if (current.location !== "granted") {
      const asked = await Geolocation.requestPermissions({ permissions: ["location"] });
      if (asked.location !== "granted") return false;
    }
    const withBackground = await Geolocation.requestPermissions({ permissions: ["coarseLocation", "location"] });
    return withBackground.location === "granted";
  } catch {
    return false;
  }
}

/** Haritayı ilk konuma ortalamak gibi tek seferlik ihtiyaçlar için anlık konum. */
export async function getCurrentPosition(): Promise<TrackedPoint | null> {
  try {
    if (isNativeApp()) {
      const position = await Geolocation.getCurrentPosition({ enableHighAccuracy: true });
      return {
        lat: position.coords.latitude,
        lng: position.coords.longitude,
        accuracy: position.coords.accuracy,
        altitude: position.coords.altitude,
        speedMps: position.coords.speed,
        timeMs: position.timestamp,
      };
    }
    if (typeof navigator === "undefined" || !navigator.geolocation) return null;
    return await new Promise((resolve) => {
      navigator.geolocation.getCurrentPosition(
        (position) => resolve({
          lat: position.coords.latitude,
          lng: position.coords.longitude,
          accuracy: position.coords.accuracy,
          altitude: position.coords.altitude,
          speedMps: position.coords.speed,
          timeMs: position.timestamp,
        }),
        () => resolve(null),
        { enableHighAccuracy: true, timeout: 10000 },
      );
    });
  } catch {
    return null;
  }
}

/**
 * Bir "Hedefit Rota" takip oturumu başlatır. `backgroundMessage` verilmesi,
 * eklentinin Android'de zorunlu kalıcı bildirimi (foreground service) devreye
 * almasını, iOS'ta ise arka plan konum güncellemelerini sağlar — ekran kapalı
 * veya uygulama arka planda olsa bile takip sürer.
 */
export async function startGpsTracking(
  onLocation: (point: TrackedPoint) => void,
  onError: (message: string) => void,
): Promise<string> {
  markGpsPermissionPrompted();

  if (isNativeApp()) {
    // Android, konum ön plan servisi için KALICI BİR BİLDİRİM zorunlu tutar.
    // Bildirim izni yokken o bildirim gösterilemez ve sistem servisi erkenden
    // öldürebilir; gerçek cihazda bu iznin verilmediği görüldü.
    await requestMobileNotificationPermission().catch(() => undefined);
    // Ekran kapalıyken takip için ayrı bir "her zaman izin ver" gerekir
    // (Android 10+). Bu istenmezse takip yalnız uygulama önplandayken sürer.
    await ensureBackgroundLocationPermission().catch(() => undefined);

    return BackgroundGeolocation.addWatcher(
      {
        backgroundTitle: "Hedefit Rota",
        backgroundMessage: "Aktivite takip ediliyor",
        requestPermissions: true,
        stale: false,
        distanceFilter: 5,
      },
      (location?: Location, error?: { message: string }) => {
        if (error) { onError(error.message); return; }
        if (location) onLocation(toTrackedPoint(location));
      },
    );
  }

  if (typeof navigator === "undefined" || !navigator.geolocation) {
    onError("Bu tarayıcı konum takibini desteklemiyor");
    return "";
  }
  const watchId = navigator.geolocation.watchPosition(
    (position) => onLocation({
      lat: position.coords.latitude,
      lng: position.coords.longitude,
      accuracy: position.coords.accuracy,
      altitude: position.coords.altitude,
      speedMps: position.coords.speed,
      timeMs: position.timestamp,
    }),
    (error) => onError(error.message),
    { enableHighAccuracy: true },
  );
  return String(watchId);
}

export async function stopGpsTracking(watcherId: string): Promise<void> {
  if (!watcherId) return;
  if (isNativeApp()) {
    await BackgroundGeolocation.removeWatcher({ id: watcherId }).catch(() => undefined);
    return;
  }
  if (typeof navigator !== "undefined" && navigator.geolocation) {
    navigator.geolocation.clearWatch(Number(watcherId));
  }
}
