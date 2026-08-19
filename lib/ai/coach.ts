// AI Coach Service — Hedefit'in AI'ya açılan TEK kapısı.
//
// Rotalar ve bileşenler buradaki fonksiyonları çağırır; hangi sağlayıcının
// (yerel / Kimi / gelecekteki bir sağlayıcı) cevapladığını bilmezler.
//
// Akış:
//   deterministik gerçekler → hafıza → bilgi getirimi → bağlam bütçesi
//   → güvenlik → router → çıktı güvenliği → hafıza çıkarımı
//
// Güvenlik katmanı router'dan ÖNCE gelir: acil bir durumda modele hiç
// gidilmez (hem doğru yanıt garanti edilir hem ücretli çağrı yapılmaz).

import { jsonSchema } from "ai";
import { buildCoachContext, contextToSystemPrompt } from "./context-builder.ts";
import { AiAllProvidersFailedError } from "./errors.ts";
import { analyze, type CoachFacts, type IntelligenceInput } from "./intelligence.ts";
import { MEMORY_EXTRACTION_PROMPT, AI_COACH_PROMPT_VERSION } from "./prompts.ts";
import { MEMORY_TYPES, sanitizeMemory, type UserMemory } from "./memory.ts";
import { routeObject, routeText, type RoutingPolicy } from "./router.ts";
import { enforceOutputSafety, evaluateSafety } from "./safety.ts";
import type { AiMessage, AiTaskCategory } from "./types.ts";

export type CoachRequest = {
  messages: AiMessage[];
  locale?: "tr" | "en";
  /** Ham kullanıcı verisi; deterministik motor bunu gerçeklere çevirir. */
  signals?: IntelligenceInput;
  memories?: UserMemory[];
  category?: AiTaskCategory;
  policy?: RoutingPolicy;
  maxOutputTokens?: number;
  temperature?: number;
  abortSignal?: AbortSignal;
};

export type CoachResult = {
  text: string;
  /** "ai" = bir model üretti, "safety" = güvenlik katmanı yanıtladı. */
  source: "ai" | "safety";
  provider: string;
  model: string;
  promptVersion: string;
  fallbackUsed: boolean;
  latencyMs: number;
  facts: CoachFacts;
  blockedReason?: string;
};

/**
 * Kullanıcıya gösterilecek yanıt. HER ZAMAN bir sonuç döner ya da
 * `AiAllProvidersFailedError` fırlatır — çağıran rota bunu nazik bir mesaja
 * çevirir; ham sağlayıcı hatası hiçbir zaman kullanıcıya ulaşmaz.
 */
export async function generateCoachResponse(request: CoachRequest): Promise<CoachResult> {
  const locale = request.locale === "en" ? "en" : "tr";
  const facts = analyze(request.signals ?? {});
  const question = request.messages.at(-1)?.text || "";

  const safety = evaluateSafety(question, locale);
  if (safety.blocked) {
    return {
      text: safety.response,
      source: "safety",
      provider: "safety-layer",
      model: "rule-based",
      promptVersion: AI_COACH_PROMPT_VERSION,
      fallbackUsed: false,
      latencyMs: 0,
      facts,
      blockedReason: safety.reason,
    };
  }

  const context = await buildCoachContext({
    facts,
    memories: request.memories,
    messages: request.messages,
    locale,
  });

  const response = await routeText({
    category: request.category ?? "conversation",
    system: contextToSystemPrompt(context, { locale, safetyInstruction: safety.extraInstruction }),
    messages: context.recentMessages,
    locale,
    facts,
    temperature: request.temperature ?? 0.35,
    maxOutputTokens: request.maxOutputTokens ?? 500,
    abortSignal: request.abortSignal,
  }, request.policy);

  return {
    text: enforceOutputSafety(response.text.trim(), locale),
    source: "ai",
    provider: response.provider,
    model: response.model,
    promptVersion: AI_COACH_PROMPT_VERSION,
    fallbackUsed: Boolean(response.fallbackUsed),
    latencyMs: response.latencyMs,
    facts,
  };
}

const memoryExtractionSchema = jsonSchema<{ memories: Array<{ type: string; key: string; value: string; confidence?: number }> }>({
  type: "object",
  additionalProperties: false,
  properties: {
    memories: {
      type: "array",
      maxItems: 4,
      items: {
        type: "object",
        additionalProperties: false,
        properties: {
          type: { type: "string", enum: [...MEMORY_TYPES] },
          key: { type: "string", minLength: 1, maxLength: 60 },
          value: { type: "string", minLength: 1, maxLength: 120 },
          confidence: { type: "number", minimum: 0, maximum: 1 },
        },
        required: ["type", "key", "value"],
      },
    },
  },
  required: ["memories"],
});

/**
 * Kullanıcı mesajından kalıcı tercih çıkarır.
 *
 * ASLA ÇAĞRIYI BLOKLAMAZ: hata durumunda boş liste döner. Hafıza bir
 * iyileştirmedir; çıkarım başarısız diye kullanıcı yanıtsız kalmamalı.
 * Model çıktısı `sanitizeMemory` ile TEK TEK doğrulanır — şemaya uyduğunu
 * iddia eden ama uymayan JSON (bkz. openai-compatible.ts) uygulamayı bozamaz.
 */
export async function extractMemories(input: { message: string; locale?: "tr" | "en"; abortSignal?: AbortSignal }): Promise<UserMemory[]> {
  const locale = input.locale === "en" ? "en" : "tr";
  const message = input.message.trim().slice(0, 600);
  if (!message) return [];
  try {
    const response = await routeObject({
      category: "structured_extraction",
      schema: memoryExtractionSchema,
      system: MEMORY_EXTRACTION_PROMPT[locale],
      prompt: `<message>\n${message}\n</message>`,
      temperature: 0,
      maxOutputTokens: 400,
      abortSignal: input.abortSignal,
    });
    const raw = Array.isArray(response.object?.memories) ? response.object.memories : [];
    return raw
      // Çıkarım kullanıcının açık ifadesinden geliyor ama modelin yorumundan
      // geçiyor; bu yüzden kaynak "inferred" olarak işaretlenir.
      .map((item) => sanitizeMemory({ ...item, source: "inferred" }))
      .filter((memory): memory is UserMemory => memory !== null);
  } catch (error) {
    if (!(error instanceof AiAllProvidersFailedError)) console.error("[ai-coach] memory extraction failed", error instanceof Error ? error.name : "unknown");
    return [];
  }
}
