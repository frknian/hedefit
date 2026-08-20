// Cihazın kendi adım sayacından günlük toplamı çıkaran saf mantık.
//
// NEDEN GEREKLİ: Android tarafında donanım sayacı (TYPE_STEP_COUNTER) cihaz
// açılışından beri toplam verir, kullandığımız eklenti ise yalnızca
// `startMeasurementUpdates()` çağrısından SONRAKİ adımları sayar. İkisi de
// "bugün kaç adım attım" sorusunu tek başına cevaplamaz: gün dönümünü ve
// uygulamanın yeniden açılmasını bizim yönetmemiz gerekir.
//
// Saf tutuldu ki gün dönümü ve yeniden başlatma senaryoları cihaz olmadan
// test edilebilsin.

export type StoredStepState = {
  /** Sayacın ait olduğu yerel gün (YYYY-MM-DD). */
  localDate: string;
  /** O gün için birikmiş toplam adım. */
  steps: number;
};

export type MergeInput = {
  /** Önceki oturumlardan kalan kayıt; ilk kullanımda null. */
  stored: StoredStepState | null;
  /** Şu anki yerel gün. */
  todayKey: string;
  /** Eklentinin bu oturumda saydığı adım (oturum başından beri artan). */
  sessionSteps: number;
  /** Bu oturumda en son işlenen değer; artışı bulmak için. */
  lastSessionSteps: number;
};

/**
 * Oturum sayacındaki artışı günlük toplama ekler.
 *
 * - Gün değiştiyse toplam sıfırdan başlar (dünün adımı bugüne taşınmaz).
 * - Oturum sayacı geri giderse (eklenti yeniden başladı, cihaz yeniden
 *   açıldı) artış negatif sayılmaz; yeni değer artışın kendisi kabul edilir.
 */
export function mergeSessionSteps({ stored, todayKey, sessionSteps, lastSessionSteps }: MergeInput): StoredStepState {
  const delta = sessionSteps >= lastSessionSteps ? sessionSteps - lastSessionSteps : sessionSteps;
  const sameDay = stored?.localDate === todayKey;
  const base = sameDay ? stored.steps : 0;
  return { localDate: todayKey, steps: Math.max(0, base + Math.max(0, delta)) };
}

/** Kayıt bugüne aitse toplamı, değilse sıfırı döner. */
export function stepsForToday(stored: StoredStepState | null, todayKey: string): number {
  return stored && stored.localDate === todayKey ? Math.max(0, stored.steps) : 0;
}

export const DEFAULT_STEP_GOAL = 8000;
export const STEP_GOAL_STORAGE_KEY = "hedefit:step-goal";
export const MIN_STEP_GOAL = 1000;
export const MAX_STEP_GOAL = 50000;

/** localStorage'daki kullanıcı hedefini okur; yoksa/aralık dışıysa varsayılana döner. */
export function readStoredStepGoal(fallback: number = DEFAULT_STEP_GOAL): number {
  if (typeof window === "undefined") return fallback;
  try {
    const raw = window.localStorage.getItem(STEP_GOAL_STORAGE_KEY);
    const parsed = Number(raw);
    return raw && Number.isFinite(parsed) && parsed >= MIN_STEP_GOAL && parsed <= MAX_STEP_GOAL ? parsed : fallback;
  } catch {
    return fallback;
  }
}

/**
 * Cihaz sayacı ile sağlık uygulamasından gelen değeri birleştirir.
 * Sağlık verisi genelde daha eksiksizdir (uygulama kapalıyken de sayar),
 * bu yüzden büyük olan kazanır — iki kaynak birbirine EKLENMEZ, yoksa aynı
 * adımlar iki kez sayılırdı.
 */
export function combineStepSources(deviceSteps: number, healthSteps: number | null): number {
  const device = Math.max(0, Math.round(deviceSteps) || 0);
  if (healthSteps === null || !Number.isFinite(healthSteps)) return device;
  return Math.max(device, Math.max(0, Math.round(healthSteps)));
}

export type StepHistoryPoint = { localDate: string; steps: number };

/**
 * İlerlemem sayfasındaki tavsiye kartının hangi mesajı göstereceğine karar
 * verir. Metinler burada değil çağıran tarafta (i18n) — bu fonksiyon yalnız
 * HANGİ durumda olduğumuzu ve hesaba giren sayıları döner, böylece hem TR
 * hem EN metinleri aynı karardan üretilir ve tarayıcısız test edilebilir.
 *
 * "Dün" temel alınır, "bugün" değil: gün henüz bitmediği için bugünün adımı
 * her zaman düşük görünür ve yanıltıcı bir "az attın" uyarısı üretirdi.
 */
export type StepAdvice =
  | { kind: "noData" }
  | { kind: "goalReached"; steps: number; goal: number }
  | { kind: "belowAverage"; yesterdaySteps: number; averageSteps: number }
  | { kind: "onTrack"; yesterdaySteps: number; averageSteps: number };

export function computeStepAdvice(history: StepHistoryPoint[], goal: number, todayKey: string): StepAdvice {
  const byDate = new Map(history.map((entry) => [entry.localDate, Math.max(0, entry.steps || 0)]));
  const yesterdayKey = shiftDateKey(todayKey, -1);
  const yesterdaySteps = byDate.get(yesterdayKey);
  if (yesterdaySteps === undefined) return { kind: "noData" };

  if (yesterdaySteps >= goal) return { kind: "goalReached", steps: yesterdaySteps, goal };

  // Dünden önceki 6 günün ortalaması: dünü kendisiyle karşılaştırmamak için hariç tutulur.
  const priorDays: number[] = [];
  for (let offset = 2; offset <= 7; offset++) {
    const value = byDate.get(shiftDateKey(todayKey, -offset));
    if (value !== undefined) priorDays.push(value);
  }
  if (priorDays.length === 0) return { kind: "onTrack", yesterdaySteps, averageSteps: yesterdaySteps };

  const averageSteps = Math.round(priorDays.reduce((sum, value) => sum + value, 0) / priorDays.length);
  if (averageSteps > 0 && yesterdaySteps < averageSteps * 0.7) {
    return { kind: "belowAverage", yesterdaySteps, averageSteps };
  }
  return { kind: "onTrack", yesterdaySteps, averageSteps };
}

/** Cihaz sayacının Hedefit Rota'daki (JS oturumu, iOS/web) doğrudan güncellediği anahtar. */
export const DEVICE_STEPS_STORAGE_KEY = "hedefit:device-steps";

/**
 * Yürüyüş/koşu mesafesinden yaklaşık adım sayısı türetir. Ortalama adım
 * uzunluğu sabittir (boy sorulmadan tahmin), fitness uygulamalarının
 * kullandığı yaygın değerler: yürüyüşte ~0.75 m, koşuda ~1.00 m.
 */
export function estimateStepsFromDistance(distanceKm: number, activityKey: "walking" | "running"): number {
  if (!(distanceKm > 0)) return 0;
  const strideMeters = activityKey === "running" ? 1.0 : 0.75;
  return Math.round((distanceKm * 1000) / strideMeters);
}

/**
 * Hedefit Rota'da yürüyüp/koşulan mesafeyi günün adım toplamına ekler.
 *
 * YALNIZCA iOS/web'de anlamlıdır: Android'de gerçek adımlar zaten donanım
 * sensörünü dinleyen arka plan servisinden (StepCounterService) geliyor —
 * o servis GPS takibinden bağımsız, sürekli çalışıyor. Bu fonksiyonu orada
 * da çağırmak (DEVICE_STEPS_STORAGE_KEY'i güncellemek) zararsızdır çünkü
 * Android dalı o anahtarı hiç okumaz (bkz. StepCounterCard.tsx), ama iki
 * kez sayma riski yine de yoktur: kredi yalnızca kayıt anındaki TEK seferlik
 * bir toplama işlemidir.
 */
export function applyStepCredit(stored: StoredStepState | null, todayKey: string, creditSteps: number): StoredStepState {
  const sameDay = stored?.localDate === todayKey;
  const base = sameDay ? stored.steps : 0;
  return { localDate: todayKey, steps: Math.max(0, base + Math.max(0, Math.round(creditSteps))) };
}

function shiftDateKey(dateKey: string, amountDays: number): string {
  const [year, month, day] = dateKey.split("-").map(Number);
  const date = new Date(Date.UTC(year, month - 1, day + amountDays, 12));
  return `${date.getUTCFullYear()}-${String(date.getUTCMonth() + 1).padStart(2, "0")}-${String(date.getUTCDate()).padStart(2, "0")}`;
}
