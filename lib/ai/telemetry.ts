// Gizliliğe duyarlı AI gözlemlenebilirliği.
//
// NE ÖLÇÜLÜR: hangi sağlayıcı/model seçildi, yedeğe düşüldü mü, ne kadar
// sürdü, kaç token, hangi prompt sürümü, hangi kategori.
//
// NE ÖLÇÜLMEZ: istek metni, yanıt metni, bağlam, hafıza içeriği, kalori/kilo
// değerleri. Bunlar kullanıcının sağlık verisidir; bir metrik tablosunda veya
// log satırında çoğaltılmaları gizlilik açısından savunulamaz (bkz.
// db/migrations/20260819_ai_memory.sql ai_provider_events).

import { AI_COACH_PROMPT_VERSION } from "./prompts.ts";
import type { AiTaskCategory } from "./types.ts";

export type AiEvent = {
  category: AiTaskCategory;
  provider: string;
  model?: string;
  outcome: "success" | "error" | "skipped";
  fallbackUsed: boolean;
  latencyMs?: number;
  inputTokens?: number;
  outputTokens?: number;
  promptVersion: string;
  errorKind?: string;
};

/** Sağlayıcı hatasını ham mesaj SIZDIRMADAN sınıflandırır. */
export function classifyError(error: unknown): string {
  if (error instanceof Error) {
    if (error.name === "AbortError" || error.name === "TimeoutError") return "timeout";
    if (error.name === "AiUnsupportedRequestError") return "unsupported";
  }
  const message = error instanceof Error ? error.message : String(error);
  if (/\b429\b|rate.?limit/i.test(message)) return "rate_limited";
  if (/\b401\b|\b403\b|unauthor|forbidden|api key/i.test(message)) return "auth";
  if (/\b5\d{2}\b|internal server/i.test(message)) return "provider_error";
  if (/timeout|timed out|aborted/i.test(message)) return "timeout";
  if (/quota|insufficient.?(credit|balance)/i.test(message)) return "quota";
  if (/json|parse|schema|validation/i.test(message)) return "invalid_output";
  if (/fetch failed|network|ENOTFOUND|ECONNREFUSED/i.test(message)) return "network";
  return "unknown";
}

export function createEvent(input: Omit<AiEvent, "promptVersion"> & { promptVersion?: string }): AiEvent {
  return { promptVersion: AI_COACH_PROMPT_VERSION, ...input };
}

// Toplanan olaylar. Kalıcı yazma (ai_provider_events tablosu) isteğe bağlıdır
// ve isteğin gecikmesine eklenmemesi için çağıran tarafın kararına bırakılır;
// yazılamazsa AI akışı etkilenmez.
export type AiEventSink = (event: AiEvent) => void;

/** Geliştirme kolaylığı: yönlendirmenin ne yaptığını sunucu log'unda görmek. */
export const consoleEventSink: AiEventSink = (event) => {
  if (process.env.NODE_ENV === "production" && !process.env.AI_DEBUG) return;
  console.info("[ai]", JSON.stringify(event));
};
