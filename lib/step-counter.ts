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
