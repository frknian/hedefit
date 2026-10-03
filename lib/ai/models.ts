import type { AiTaskCategory } from "./types.ts";

export type AiModelTier = "light" | "cheap" | "standard" | "advanced";

/**
 * Hedefit'in tek model kaynağı. Değerler yalnız sunucu ortamından okunur;
 * istemciye model anahtarı veya sağlayıcı anahtarı gönderilmez.
 */
export const AI_MODELS: Record<AiModelTier, () => string> = {
  // Serbest sohbet ve kısa açıklamalar: hacmin büyük kısmı burada, 4o'ya göre
  // ~15 kat ucuz. Doğruluğun kritik olduğu çıkarım ve görsel okuma bu katmanda DEĞİL.
  light: () => process.env.OPENAI_MODEL_LIGHT || "gpt-4o-mini",
  // Basit sohbet, besin çıkarımı ve görsel okuma hızlı 4o katmanındadır.
  cheap: () => process.env.OPENAI_MODEL_CHEAP || "gpt-4o",
  standard: () => process.env.OPENAI_MODEL_STANDARD || "gpt-4o",
  // 15 soruluk kişisel plan ve haftalık muhakeme güçlü reasoning modeline gider.
  advanced: () => process.env.OPENAI_MODEL_ADVANCED || "gpt-5.1",
};

const LIGHT_CATEGORIES: ReadonlySet<AiTaskCategory> = new Set([
  "conversation", "simple_coaching", "daily_summary", "nutrition_explanation",
  "activity_summary", "goal_progress", "motivation",
]);

export function tierForTask(category: AiTaskCategory): AiModelTier {
  if (category === "complex_reasoning" || category === "plan_generation") return "advanced";
  if (category === "structured_extraction") return "cheap";
  if (LIGHT_CATEGORIES.has(category)) return "light";
  // vision: fotoğraftan besin/ekipman okuma tam model ister.
  return "standard";
}

export function modelForTask(category: AiTaskCategory): string {
  return AI_MODELS[tierForTask(category)]();
}
