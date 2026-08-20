import type { TrackedPoint } from "./gps-tracking.ts";

// Canlı takip oturumunun DİSKE yazılan hâli.
//
// NEDEN GEREKLİ: rota önceden yalnızca GpsActivityTracker'ın React state'inde
// (JS belleğinde) tutuluyordu. Ekran kapanıp uzun süre kapalı kalınca ya da
// işletim sistemi WebView sürecini bellek geri kazanımı için öldürünce,
// uygulama yeniden açıldığında React tamamen sıfırdan kurulur — o ana kadar
// kaydedilen tüm rota, hiç var olmamış gibi kaybolur. Native arka plan GPS
// servisi konum almaya devam etse bile, onu dinleyen JS tarafı ölmüşse
// gelen noktalar hiçbir yere yazılmaz.
//
// Çözüm: her yeni nokta geldiğinde oturumun TAMAMI burada localStorage'a
// yazılır (rota onlarca-yüzlerce nokta boyutunda, tek bir JSON yazımı
// ucuzdur). Uygulama yeniden açıldığında GpsActivityTracker bu kaydı okur ve
// "duraklatıldı" durumunda geri yükler — kullanıcı ya devam eder ya bitirip
// kaydeder, veri sıfırlanmaz.

const STORAGE_KEY = "hedefit:gps-live-session";

export type PersistedGpsSession = {
  activityKey: string;
  startedAt: string;
  points: TrackedPoint[];
  hrSamples: number[];
};

export function readPersistedGpsSession(): PersistedGpsSession | null {
  if (typeof window === "undefined") return null;
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as PersistedGpsSession;
    if (typeof parsed?.activityKey !== "string" || typeof parsed?.startedAt !== "string" || !Array.isArray(parsed?.points)) return null;
    return { activityKey: parsed.activityKey, startedAt: parsed.startedAt, points: parsed.points, hrSamples: Array.isArray(parsed.hrSamples) ? parsed.hrSamples : [] };
  } catch {
    return null;
  }
}

export function writePersistedGpsSession(session: PersistedGpsSession): void {
  if (typeof window === "undefined") return;
  try { window.localStorage.setItem(STORAGE_KEY, JSON.stringify(session)); } catch { /* depolama kapalı */ }
}

export function clearPersistedGpsSession(): void {
  if (typeof window === "undefined") return;
  try { window.localStorage.removeItem(STORAGE_KEY); } catch { /* depolama kapalı */ }
}
