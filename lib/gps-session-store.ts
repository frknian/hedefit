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

/**
 * Bir kayıt ne kadar süre "yarım kalmış aktivite" sayılır?
 *
 * Süre sınırı OLMADAN, kaydedilmeden bırakılan bir oturum diskte SONSUZA DEK
 * kalıyordu: uygulama her açılışta onu geri yükleyip Hedefit Rota'yı günler
 * önceki rotayla açıyordu. 12 saat, yürüyüş/koşu/bisiklet için fazlasıyla
 * cömert bir üst sınır; bunu aşan bir kayıt gerçek bir aktivite değil,
 * unutulmuş bir kalıntıdır.
 */
const STALE_AFTER_MS = 12 * 60 * 60 * 1000;

export type PersistedGpsSession = {
  activityKey: string;
  startedAt: string;
  points: TrackedPoint[];
  hrSamples: number[];
};

/** Oturumdan en son ne zaman veri alındı? Başlangıç değil, SON hareket ölçülür. */
function lastActivityMs(session: PersistedGpsSession): number {
  const lastPoint = session.points[session.points.length - 1];
  if (lastPoint && Number.isFinite(lastPoint.timeMs)) return lastPoint.timeMs;
  const started = Date.parse(session.startedAt);
  return Number.isFinite(started) ? started : 0;
}

export function isStaleGpsSession(session: PersistedGpsSession, now: number = Date.now()): boolean {
  return now - lastActivityMs(session) > STALE_AFTER_MS;
}

export function readPersistedGpsSession(): PersistedGpsSession | null {
  if (typeof window === "undefined") return null;
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as PersistedGpsSession;
    if (typeof parsed?.activityKey !== "string" || typeof parsed?.startedAt !== "string" || !Array.isArray(parsed?.points)) return null;
    const session = { activityKey: parsed.activityKey, startedAt: parsed.startedAt, points: parsed.points, hrSamples: Array.isArray(parsed.hrSamples) ? parsed.hrSamples : [] };
    // Bayat kayıt geri yüklenmez ve diskten de silinir; aksi halde her
    // açılışta aynı eski rotayı göstermeye devam ederdi.
    if (isStaleGpsSession(session)) { clearPersistedGpsSession(); return null; }
    return session;
  } catch {
    return null;
  }
}

export function writePersistedGpsSession(session: PersistedGpsSession): void {
  if (typeof window === "undefined") return;
  try { window.localStorage.setItem(STORAGE_KEY, JSON.stringify(session)); } catch { /* depolama kapalı */ }
}

/**
 * Kaplama açılmadan (GpsActivityTracker hiç mount olmadan) yarım kalan bir
 * oturum olup olmadığını ucuzca kontrol eder — FitAiApp bunu, uygulama
 * yeniden açıldığında kaplamayı otomatik açık başlatmak için kullanır.
 */
export function hasPersistedGpsSession(): boolean {
  // Yalnız anahtarın VARLIĞINA bakmak yetmiyordu: bayat bir kayıt da "var"
  // sayılıp kaplamayı her açılışta zorla açıyordu. Aynı doğrulama ve bayatlık
  // kuralından geçirilir.
  return readPersistedGpsSession() !== null;
}

export function clearPersistedGpsSession(): void {
  if (typeof window === "undefined") return;
  try { window.localStorage.removeItem(STORAGE_KEY); } catch { /* depolama kapalı */ }
}
