// Merkezi XP kuralları. Sunucuda TEK yetkili kaynak `public.xp_rules` tablosudur (bkz.
// supabase/migrations/20261008120000_challenge_system.sql); buradaki değerler o tablonun
// başlangıç değerleriyle aynıdır ve yalnızca tablo okunamadığında gösterim için kullanılır.
// tests/challenges.test.mjs iki kaynağın birbirinden ayrışmadığını doğrular.

export type XpSource =
  | "CHECKIN_COMPLETED"
  | "WORKOUT_COMPLETED"
  | "STEP_GOAL_COMPLETED"
  | "ROUTE_DISTANCE"
  | "WATER_GOAL_COMPLETED"
  | "NUTRITION_TARGET_COMPLETED"
  | "SLEEP_GOAL_COMPLETED"
  | "WEEKLY_GOAL_COMPLETED"
  | "WEEKLY_CHALLENGE_COMPLETED"
  | "ACHIEVEMENT_UNLOCKED"
  | "CHALLENGE_DAY_COMPLETED"
  | "CHALLENGE_RECOVERY_DAY"
  | "CHALLENGE_STREAK_3"
  | "CHALLENGE_STREAK_7"
  | "CHALLENGE_COMPLETED"
  | "FRIEND_CHALLENGE_BONUS";

export interface XpRule {
  amount: number;
  /** Günlük görev tavanına (100 XP) dahil mi? Challenge ve haftalık ödüller dahil değildir. */
  dailyCapped: boolean;
}

export const DAILY_XP_CAP = 100;

export const DEFAULT_XP_RULES: Record<XpSource, XpRule> = {
  CHECKIN_COMPLETED: { amount: 5, dailyCapped: true },
  WORKOUT_COMPLETED: { amount: 50, dailyCapped: true },
  STEP_GOAL_COMPLETED: { amount: 20, dailyCapped: true },
  // Kilometre başına.
  ROUTE_DISTANCE: { amount: 10, dailyCapped: true },
  WATER_GOAL_COMPLETED: { amount: 10, dailyCapped: true },
  NUTRITION_TARGET_COMPLETED: { amount: 20, dailyCapped: true },
  SLEEP_GOAL_COMPLETED: { amount: 10, dailyCapped: true },
  WEEKLY_GOAL_COMPLETED: { amount: 100, dailyCapped: false },
  WEEKLY_CHALLENGE_COMPLETED: { amount: 250, dailyCapped: false },
  ACHIEVEMENT_UNLOCKED: { amount: 100, dailyCapped: false },
  CHALLENGE_DAY_COMPLETED: { amount: 25, dailyCapped: false },
  CHALLENGE_RECOVERY_DAY: { amount: 5, dailyCapped: false },
  CHALLENGE_STREAK_3: { amount: 30, dailyCapped: false },
  CHALLENGE_STREAK_7: { amount: 100, dailyCapped: false },
  CHALLENGE_COMPLETED: { amount: 250, dailyCapped: false },
  FRIEND_CHALLENGE_BONUS: { amount: 100, dailyCapped: false },
};

export type XpRules = Record<XpSource, XpRule>;

/** Veritabanı satırlarını varsayılanların üzerine uygular; bilinmeyen kaynak ya da geçersiz değer yok sayılır. */
export function mergeXpRules(rows: { source?: unknown; amount?: unknown; daily_capped?: unknown }[] | null | undefined): XpRules {
  const rules: XpRules = { ...DEFAULT_XP_RULES };
  for (const row of rows ?? []) {
    const source = row.source as XpSource;
    if (!(source in rules)) continue;
    const amount = Number(row.amount);
    if (!Number.isInteger(amount) || amount < 0 || amount > 10_000) continue;
    rules[source] = { amount, dailyCapped: row.daily_capped === true };
  }
  return rules;
}

/**
 * Bir challenge'ı eksiksiz tamamlayan kullanıcının kazanacağı en yüksek XP:
 * gün ödülleri + 3 günlük seri + her 7 günlük seri + bitirme ödülü (+ arkadaş bonusu).
 * Gösterim içindir; gerçek ödül her adımda sunucuda verilir.
 */
export function challengeRewardXp(days: number, rules: XpRules = DEFAULT_XP_RULES, withFriend = false): number {
  const safeDays = Math.max(0, Math.floor(days));
  return safeDays * rules.CHALLENGE_DAY_COMPLETED.amount
    + (safeDays >= 3 ? rules.CHALLENGE_STREAK_3.amount : 0)
    + Math.floor(safeDays / 7) * rules.CHALLENGE_STREAK_7.amount
    + rules.CHALLENGE_COMPLETED.amount
    + (withFriend ? rules.FRIEND_CHALLENGE_BONUS.amount : 0);
}

/** Level eğrisi: Android `GamificationEngine.levelFor` ile birebir aynı (300 + (level-1)·50 XP). */
export function levelFor(totalXp: number): { level: number; currentXp: number; nextLevelXp: number } {
  let level = 1;
  let remaining = Math.max(0, Math.floor(totalXp));
  let required = 300;
  while (remaining >= required) { remaining -= required; level += 1; required = 300 + (level - 1) * 50; }
  return { level, currentXp: remaining, nextLevelXp: required };
}
