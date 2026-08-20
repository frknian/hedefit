// Aktivite sayılarının okunur hâli. Saf fonksiyonlar: hem ekranda hem de
// paylaşım görselinde aynı biçim kullanılsın diye tek yerde durur ve
// tarayıcı olmadan test edilebilir.

/** 1:23:45 / 12:34 biçiminde süre. Bir saatin altında saat hanesi yazılmaz. */
export function formatDuration(ms: number): string {
  const totalSeconds = Math.max(0, Math.floor(ms / 1000));
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  const pad = (value: number) => String(value).padStart(2, "0");
  return hours > 0 ? `${hours}:${pad(minutes)}:${pad(seconds)}` : `${pad(minutes)}:${pad(seconds)}`;
}

/**
 * Kilometre başına tempo ("5:30 /km"). Koşu ve yürüyüşte hız değil tempo
 * konuşulur. Mesafe yoksa hesap sonsuza gider; o durumda "—" döner.
 */
export function formatPace(distanceKm: number, durationMs: number): string {
  if (!(distanceKm > 0) || !(durationMs > 0)) return "—";
  const secondsPerKm = Math.round(durationMs / 1000 / distanceKm);
  // 99'dan uzun tempolar (birkaç metre kayıp GPS gürültüsü) anlamsız.
  if (!Number.isFinite(secondsPerKm) || secondsPerKm > 99 * 60) return "—";
  const minutes = Math.floor(secondsPerKm / 60);
  const seconds = secondsPerKm % 60;
  return `${minutes}:${String(seconds).padStart(2, "0")} /km`;
}

export function formatDistanceKm(distanceKm: number): string {
  return `${(Math.max(0, distanceKm) || 0).toFixed(2)} km`;
}
