export type PlanTier = "free" | "plus" | "pro";
export type EntitlementFeature = "ads" | "ai_coach" | "vision_food" | "weekly_review";

const PLAN_FEATURES: Record<PlanTier, Record<EntitlementFeature, boolean>> = {
  free: { ads: true, ai_coach: true, vision_food: true, weekly_review: true },
  plus: { ads: false, ai_coach: true, vision_food: true, weekly_review: true },
  pro: { ads: false, ai_coach: true, vision_food: true, weekly_review: true },
};

export function normalizePlanTier(value: unknown): PlanTier {
  return value === "plus" || value === "pro" ? value : "free";
}

export function hasEntitlement(tier: PlanTier, feature: EntitlementFeature): boolean {
  return PLAN_FEATURES[tier][feature];
}

export function shouldShowAds(tier: PlanTier): boolean {
  return hasEntitlement(tier, "ads");
}

// --- Adaptive Fitness: Pilates/Mobility/Barre içeriği, check-in, adaptasyon, döngü, koç, beslenme -------------
//
// DÖNGÜ TAKİBİ (kayıt, düzenleme, silme) HER KATMANDA ÜCRETSİZDİR: sağlık verisini yönetme hakkı paywall'ın
// arkasında olmamalı. Ücretli katman yalnızca bu bilginin ADAPTASYONA katılmasını ve açıklama derinliğini ayırır.
// Android'deki `TIER_LIMITS` (ui/state/Entitlements.kt) ile birlikte güncelle; iki taraf da testlidir.

export type AdaptiveAction = "shorten" | "reduce_intensity" | "replace_exercises" | "add_mobility" | "switch_recovery" | "switch_pilates";
export type NutritionPersonalization = "basic" | "training_load" | "full";

export interface AdaptiveCapabilities {
  /** true: yalnız başlangıç seti (STARTER_SUBCATEGORIES) açık, Barre kapalı. */
  starterModalitiesOnly: boolean;
  barre: boolean;
  /** Check-in geçmişinde görülebilen gün; Infinity = sınırsız. */
  checkinHistoryDays: number;
  checkinTrends: boolean;
  adaptiveActions: readonly AdaptiveAction[];
  /** Döngü bilgisinin adaptasyon kararına katılması (takip kendisi ücretsiz). */
  cycleAdaptation: boolean;
  /** Koç adaptasyonu kişisel olarak AI ile açıklar; false = şablon açıklama. */
  aiAdaptiveCoach: boolean;
  coachCycleAware: boolean;
  nutritionPersonalization: NutritionPersonalization;
}

/** Ücretsiz katmanda açık içerik: yeni başlayanlara uygun, kısa ve toparlanma odaklı alt kategoriler. */
export const STARTER_SUBCATEGORIES: readonly string[] = ["beginner", "short", "recovery", "morning", "evening"];

const BASIC_ACTIONS: readonly AdaptiveAction[] = ["shorten", "reduce_intensity"];
const PLUS_ACTIONS: readonly AdaptiveAction[] = [...BASIC_ACTIONS, "replace_exercises", "add_mobility", "switch_recovery"];
const PRO_ACTIONS: readonly AdaptiveAction[] = [...PLUS_ACTIONS, "switch_pilates"];

const ADAPTIVE_CAPABILITIES: Record<PlanTier, AdaptiveCapabilities> = {
  free: { starterModalitiesOnly: true, barre: false, checkinHistoryDays: 14, checkinTrends: false, adaptiveActions: BASIC_ACTIONS, cycleAdaptation: false, aiAdaptiveCoach: false, coachCycleAware: false, nutritionPersonalization: "basic" },
  plus: { starterModalitiesOnly: false, barre: true, checkinHistoryDays: 90, checkinTrends: false, adaptiveActions: PLUS_ACTIONS, cycleAdaptation: true, aiAdaptiveCoach: false, coachCycleAware: false, nutritionPersonalization: "training_load" },
  pro: { starterModalitiesOnly: false, barre: true, checkinHistoryDays: Number.POSITIVE_INFINITY, checkinTrends: true, adaptiveActions: PRO_ACTIONS, cycleAdaptation: true, aiAdaptiveCoach: true, coachCycleAware: true, nutritionPersonalization: "full" },
};

export function adaptiveCapabilities(tier: PlanTier): AdaptiveCapabilities {
  return ADAPTIVE_CAPABILITIES[tier];
}

export function canUseAdaptiveAction(tier: PlanTier, action: AdaptiveAction): boolean {
  return ADAPTIVE_CAPABILITIES[tier].adaptiveActions.includes(action);
}

/**
 * Bir modalite egzersizi bu katmanda açık mı? Modalitesiz (klasik) hareketler bu kurala TABİ DEĞİL:
 * mevcut katman kuralları (seviye kilidi) aynen geçerli kalır. Programdaki hareketler her zaman açıktır
 * (çağıran taraf kontrol eder).
 */
export function canUseModalityExercise(tier: PlanTier, modalities: readonly string[] | undefined, subcategories: readonly string[] | undefined): boolean {
  if (!modalities || modalities.length === 0) return true;
  const caps = ADAPTIVE_CAPABILITIES[tier];
  if (!caps.starterModalitiesOnly) return caps.barre || !modalities.every((modality) => modality === "barre");
  if (modalities.every((modality) => modality === "barre")) return false;
  return (subcategories ?? []).some((subcategory) => STARTER_SUBCATEGORIES.includes(subcategory));
}
