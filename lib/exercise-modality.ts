// Egzersiz "modalite" taksonomisi: Pilates, Mobility, Barre, Low Impact, Recovery.
//
// BİLEREK `category` DEĞİL: mevcut seçici, doğrulayıcı ve adaptörler `category` değerine (strength,
// stretching, cardio…) bağlıdır; yeni değerler onları sessizce bozar. Modalite bağımsız, ek bir boyuttur
// ve hiçbir cinsiyete ya da kullanıcı grubuna kilitli değildir.

export const MODALITIES = ["pilates", "mobility", "barre", "low_impact", "recovery"] as const;
export type Modality = (typeof MODALITIES)[number];

export const MODALITY_SUBCATEGORIES: Record<Modality, readonly string[]> = {
  pilates: ["beginner", "full_body", "core", "lower_body", "upper_body", "posture", "short", "recovery"],
  mobility: ["full_body", "hip", "back", "shoulder", "morning", "evening"],
  barre: ["beginner", "lower_body", "core", "full_body", "balance_posture"],
  low_impact: ["full_body", "cardio", "beginner", "recovery"],
  recovery: ["full_body", "lower_body", "upper_body", "breathing"],
};

export const IMPACT_LEVELS = ["low", "moderate", "high"] as const;
export type ImpactLevel = (typeof IMPACT_LEVELS)[number];

/**
 * Üçüncü taraf ya da Hedefit'e özel her görsel/animasyon için lisans kaydı. `commercialUse` açıkça
 * `true` olmayan hiçbir varlık yayınlanamaz (bkz. validateAsset).
 */
export interface ExerciseAsset {
  source: string;
  license: string;
  attribution: string;
  commercialUse: boolean;
}

export const HEDEFIT_ORIGINAL_ASSET: ExerciseAsset = {
  source: "Hedefit original illustration",
  license: "Proprietary (Hedefit)",
  attribution: "",
  commercialUse: true,
};

export function isModality(value: unknown): value is Modality {
  return typeof value === "string" && (MODALITIES as readonly string[]).includes(value);
}

export function isSubcategoryOf(modality: Modality, value: unknown): boolean {
  return typeof value === "string" && MODALITY_SUBCATEGORIES[modality].includes(value);
}

/** Katalog satırındaki modalite alanlarını güvenli biçimde okur; bilinmeyen değerleri atar. */
export function readModalities(value: unknown): Modality[] {
  return Array.isArray(value) ? [...new Set(value.filter(isModality))] : [];
}

export function readSubcategories(modalities: Modality[], value: unknown): string[] {
  if (!Array.isArray(value)) return [];
  const allowed = new Set(modalities.flatMap((modality) => MODALITY_SUBCATEGORIES[modality]));
  return [...new Set(value.filter((item): item is string => typeof item === "string" && allowed.has(item)))];
}

export function readImpact(value: unknown): ImpactLevel | undefined {
  return (IMPACT_LEVELS as readonly string[]).includes(value as string) ? (value as ImpactLevel) : undefined;
}

export function readAsset(value: unknown): ExerciseAsset | undefined {
  if (!value || typeof value !== "object") return undefined;
  const item = value as Record<string, unknown>;
  const text = (input: unknown) => (typeof input === "string" ? input.trim().slice(0, 300) : "");
  const asset: ExerciseAsset = { source: text(item.source), license: text(item.license), attribution: text(item.attribution), commercialUse: item.commercialUse === true };
  return asset.source && asset.license ? asset : undefined;
}

/** Yayın kuralı: kaynak, lisans ve ticari kullanım izni net olmayan varlık kabul edilmez. */
export function validateAsset(asset: ExerciseAsset | undefined): string | null {
  if (!asset) return "asset_missing";
  if (!asset.source) return "asset_source_missing";
  if (!asset.license) return "asset_license_missing";
  if (!asset.commercialUse) return "asset_not_commercial";
  return null;
}

export const MODALITY_LABELS: Record<Modality, { tr: string; en: string }> = {
  pilates: { tr: "Pilates", en: "Pilates" },
  mobility: { tr: "Mobilite", en: "Mobility" },
  barre: { tr: "Barre", en: "Barre" },
  low_impact: { tr: "Düşük Etkili", en: "Low Impact" },
  recovery: { tr: "Toparlanma", en: "Recovery" },
};

export const SUBCATEGORY_LABELS: Record<string, { tr: string; en: string }> = {
  beginner: { tr: "Başlangıç", en: "Beginner" },
  full_body: { tr: "Tüm vücut", en: "Full body" },
  core: { tr: "Core", en: "Core" },
  lower_body: { tr: "Alt vücut", en: "Lower body" },
  upper_body: { tr: "Üst vücut", en: "Upper body" },
  posture: { tr: "Duruş", en: "Posture" },
  short: { tr: "Kısa", en: "Short" },
  recovery: { tr: "Toparlanma", en: "Recovery" },
  hip: { tr: "Kalça", en: "Hip" },
  back: { tr: "Sırt", en: "Back" },
  shoulder: { tr: "Omuz", en: "Shoulder" },
  morning: { tr: "Sabah", en: "Morning" },
  evening: { tr: "Akşam", en: "Evening" },
  cardio: { tr: "Kardiyo", en: "Cardio" },
  balance_posture: { tr: "Denge ve duruş", en: "Balance & posture" },
  breathing: { tr: "Nefes", en: "Breathing" },
};
