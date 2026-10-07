/**
 * Gizlilik dostu ürün analitiği için İZİN LİSTESİ. Yalnızca bu olay adları sayılır; her biri bir özelliğin
 * kullanıldığını söyler, kullanıcının sağlık durumunu ya da döngü gününü ASLA ifade etmez. Listede olmayan ad reddedilir.
 * Yasak örnekler: period_day_1, cramps_8, pain_high, pregnant — sağlık değeri taşıyan hiçbir ad eklenmez.
 */
export const ANALYTICS_EVENTS = [
  "menstrual_tracking_enabled",
  "menstrual_tracking_disabled",
  "cycle_data_deleted",
  "health_data_deleted",
  "daily_checkin_completed",
  "adaptive_workout_generated",
  "adaptive_workout_started",
  "pilates_workout_started",
  "mobility_workout_started",
  "low_impact_workout_started",
  "recovery_workout_started",
  "nutrition_notes_viewed",
  "plan_upgrade_prompt_viewed",
] as const;

export type AnalyticsEvent = (typeof ANALYTICS_EVENTS)[number];

export function isAnalyticsEvent(value: unknown): value is AnalyticsEvent {
  return typeof value === "string" && (ANALYTICS_EVENTS as readonly string[]).includes(value);
}

/** Güvenlik ağı: izin listesine yanlışlıkla sağlık değeri taşıyan bir ad eklenirse test patlar. */
export const FORBIDDEN_EVENT_PATTERN = /(period|cramp|pain|pregnan|ovulat|fertil|symptom|mood|bleed|day_\d|_\d+$)/i;
