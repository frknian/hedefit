export type ActivityType = "workout" | "walk" | "sport";

export function userTimeZone() {
  try { return Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC"; } catch { return "UTC"; }
}

export function localDateKey(date: Date | number = new Date(), timeZone = userTimeZone()) {
  const parts = new Intl.DateTimeFormat("en-CA", { timeZone, year: "numeric", month: "2-digit", day: "2-digit" }).formatToParts(date);
  const value = (part: string) => parts.find((item) => item.type === part)?.value || "";
  return `${value("year")}-${value("month")}-${value("day")}`;
}

export function localClock(date: Date | number = new Date(), timeZone = userTimeZone()) {
  const parts = new Intl.DateTimeFormat("en-GB", { timeZone, hour: "2-digit", minute: "2-digit", hourCycle: "h23" }).formatToParts(date);
  const value = (part: string) => Number(parts.find((item) => item.type === part)?.value || 0);
  return { hour: value("hour"), minute: value("minute") };
}

/**
 * Seçili takvim gününe (selectedDate, "YYYY-MM-DD") şu anki saat/dakika/
 * saniyeyi ekleyerek bir Date üretir. CalorieTracker'da dateOffset ile
 * geçmişe gidip yemek eklendiğinde consumedAt her zaman ŞİMDİ kullanılıyordu;
 * kayıt anında bugüne kayıp seçili günden kayboluyordu (localDateKey(consumedAt)
 * her zaman bugünü verirdi). Takvim günü selectedDate'ten, saat selectedDate'e
 * bakılmaksızın `now`'dan alınır — ikisi de tarayıcının yerel saat dilimidir.
 *
 * `selectedDate` geçersiz bir biçimdeyse (çağıranın hatası; normalde her zaman
 * localDateKey() çıktısıdır) `Invalid Date` döner — çağıran bunu ISO'ya
 * çevirmeye çalıştığında RangeError fırlar, sessizce yanlış bir tarihe
 * düşülmez.
 */
export function consumedAtForSelectedDate(selectedDate: string, now: Date | number = new Date()): Date {
  const reference = now instanceof Date ? now : new Date(now);
  const consumedAt = new Date(`${selectedDate}T00:00:00`);
  consumedAt.setHours(reference.getHours(), reference.getMinutes(), reference.getSeconds(), reference.getMilliseconds());
  return consumedAt;
}

export function shouldFreezeStreak(gapStatuses: Array<"rest" | "planned" | "completed" | "deferred" | undefined>) {
  return gapStatuses.every((status) => status === "rest");
}

export function nextStreakValue(current: number, sameDay: boolean, gapStatuses: Array<"rest" | "planned" | "completed" | "deferred" | undefined>) {
  if (sameDay) return Math.max(1, current);
  return shouldFreezeStreak(gapStatuses) ? Math.max(1, current) + 1 : 1;
}
