// GPS'in ham verisi gürültülüdür: telefon hiç hareket etmese bile ardışık
// konum sinyalleri birkaç metre "kayar" ve donanımın anlık hız alanı bu
// kaymadan dolayı saniyeden saniyeye 0-8 km/s arası zıplayabilir — canlı
// ekranda "tempo/hız sallanıyor" hissi tam olarak buradan gelir.
//
// İki bağımsız önlem:
//   1) Doğruluğu (accuracy) kötü noktalar rotaya/mesafeye hiç eklenmez —
//      GPS henüz "ısınırken" ya da bina içinde gelen 50-100 metrelik hata
//      payına sahip sahte sıçramaları en baştan eler.
//   2) Kabul edilen noktalardan gelen hız, üstel hareketli ortalama (EMA)
//      ile yumuşatılır ve GPS-drift eşiğinin altındaki değerler sıfıra
//      kilitlenir — dururken "0.3 km/s" gibi anlamsız kırıntılar görünmez.

/** Bu doğruluktan (metre) daha kötü bir nokta rotaya hiç eklenmez. */
export const MAX_ACCEPTABLE_ACCURACY_M = 25;

/** Bu hızın (m/s) altı GPS driftinden sayılır, "duruyor" kabul edilir. */
export const MIN_MOVING_SPEED_MPS = 0.3;

/** EMA ağırlığı: düşük değer daha yumuşak ama daha yavaş tepki verir. */
const SPEED_SMOOTHING_ALPHA = 0.3;

export function isUsableGpsPoint(point: { accuracy: number }): boolean {
  return Number.isFinite(point.accuracy) && point.accuracy <= MAX_ACCEPTABLE_ACCURACY_M;
}

/**
 * Önceki yumuşatılmış hızla yeni ham hızı harmanlar. `null`/negatif ham hız
 * (bazı cihazlarda GPS henüz hız veremez) önceki değeri korur — sıfıra
 * sıçramaz.
 */
export function smoothSpeedMps(previousSmoothedMps: number, rawSpeedMps: number | null): number {
  if (rawSpeedMps === null || !Number.isFinite(rawSpeedMps)) return previousSmoothedMps;
  const clamped = Math.max(0, rawSpeedMps) < MIN_MOVING_SPEED_MPS ? 0 : Math.max(0, rawSpeedMps);
  return previousSmoothedMps + SPEED_SMOOTHING_ALPHA * (clamped - previousSmoothedMps);
}
