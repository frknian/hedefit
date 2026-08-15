import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import { consumedAtForSelectedDate, localClock, localDateKey, nextStreakValue, shouldFreezeStreak } from "../lib/streak.ts";

test("uses a supplied local timezone for day boundaries", () => {
  const instant = new Date("2026-07-22T21:30:00Z");
  assert.equal(localDateKey(instant, "Europe/Istanbul"), "2026-07-23");
  assert.deepEqual(localClock(instant, "Europe/Istanbul"), { hour: 0, minute: 30 });
});

test("does not increase a streak more than once on the same day", () => {
  assert.equal(nextStreakValue(5, true, []), 5);
});

test("keeps a streak alive only across explicitly planned rest days", () => {
  assert.equal(shouldFreezeStreak(["rest", "rest"]), true);
  assert.equal(nextStreakValue(5, false, ["rest"]), 6);
  assert.equal(nextStreakValue(5, false, ["rest", "planned"]), 1);
});

// consumedAtForSelectedDate: CalorieTracker'da dateOffset ile geçmişe gidip
// yemek eklendiğinde consumedAt her zaman ŞİMDİ kullanılıyordu; kayıt anında
// bugüne kayıp seçili günden kayboluyordu (bkz. components/CalorieTracker.tsx
// addEntry). Bu üç test normal durumu, ay/yıl sınırı geçen bir edge case'i ve
// çağıranın hatası olan geçersiz bir tarih girdisini kapsar.

test("consumedAtForSelectedDate: normal durum — bugüne eklenen kayıt bugünün saatini taşır", () => {
  const now = new Date("2026-08-12T18:45:30.250Z");
  // "now" UTC; yerel gün anahtarı test ortamının saat dilimine göre değişebileceğinden
  // seçili günü doğrudan now'dan türetilen yerel tarihle eşleştiriyoruz.
  const selectedDate = localDateKey(now);
  const result = consumedAtForSelectedDate(selectedDate, now);
  assert.equal(localDateKey(result), selectedDate, "üretilen tarih seçili günün içinde kalmalı");
  assert.equal(result.getHours(), now.getHours());
  assert.equal(result.getMinutes(), now.getMinutes());
  assert.equal(result.getSeconds(), now.getSeconds());
  assert.equal(result.getMilliseconds(), now.getMilliseconds());
});

test("consumedAtForSelectedDate: edge case — ay ve yıl sınırını geçen geçmiş bir gün", () => {
  // Kullanıcı "önceki gün" okuyla 2025 sona dönüp 31 Aralık'a bir öğün ekliyor;
  // takvim günü 2025-12-31'de kalmalı, saat ise şu anki (2026'daki) saat olmalı.
  const now = new Date("2026-01-01T09:15:00");
  const selectedDate = "2025-12-31";
  const result = consumedAtForSelectedDate(selectedDate, now);
  assert.equal(result.getFullYear(), 2025);
  assert.equal(result.getMonth(), 11); // Aralık (0 tabanlı)
  assert.equal(result.getDate(), 31);
  assert.equal(result.getHours(), 9);
  assert.equal(result.getMinutes(), 15);
});

test("consumedAtForSelectedDate: edge case — artık yıl 29 Şubat günü doğru işlenir", () => {
  const now = new Date("2028-02-29T21:00:00");
  const result = consumedAtForSelectedDate("2028-02-29", now);
  assert.equal(result.getMonth(), 1); // Şubat
  assert.equal(result.getDate(), 29);
});

test("consumedAtForSelectedDate: hatalı input — geçersiz tarih biçimi Invalid Date döner, sessizce bugüne düşmez", () => {
  const now = new Date("2026-08-12T12:00:00");
  const result = consumedAtForSelectedDate("bugün-değil", now);
  assert.equal(Number.isNaN(result.getTime()), true, "geçersiz girdi sessizce geçerli bir tarihe (ör. bugün) dönüşmemeli");
  // .toISOString() çağıranın (addEntry) kullandığı adımdır; Invalid Date'te
  // fırlaması, hatanın localStorage'a veya sunucuya sessizce yanlış bir
  // tarihle yazılmak yerine görünür kalmasını sağlar.
  assert.throws(() => result.toISOString(), RangeError);
});

test("keeps streak updates inside the database layer", async () => {
  const migration = await readFile(new URL("../db/migrations/20260722_activity_streaks.sql", import.meta.url), "utf8");
  assert.match(migration, /create table if not exists public\.user_streaks/);
  assert.match(migration, /create table if not exists public\.activity_logs/);
  assert.match(migration, /record_streak_activity/);
  assert.match(migration, /workout_sessions_record_streak/);
  assert.match(migration, /not v_had_activity and not v_new/);
});
