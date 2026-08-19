// GERİYE DÖNÜK UYUMLULUK KATMANI.
//
// Bu dosya eskiden sağlayıcının KENDİSİydi: createOpenAICompatible() burada
// kurulur, Moonshot/Kimi uç noktası burada sabitlenirdi. AI göçünden sonra o
// iş lib/ai/providers/openai-compatible.ts'e taşındı ve araya bir yönlendirici
// girdi (lib/ai/router.ts): istek önce yerel sağlayıcıya, olmazsa uzak
// sağlayıcıya gider.
//
// Dosyanın kalma sebebi, beş rotanın (chat, generate-plan, weekly-review,
// goal-plan, nutrition/*) imzalarını değiştirmeden çalışmaya devam etmesi.
// Böylece göç, her rotayı aynı anda yeniden yazmayı gerektirmedi.
//
// YENİ KOD BUNU KULLANMAMALI. Koçluk akışları için lib/ai/coach.ts, doğrudan
// model erişimi için lib/ai/router.ts kullanılır.

import type { FlexibleSchema, JSONValue, ModelMessage } from "ai";
import { routeObject, routeText } from "./ai/router.ts";
import { remoteApiKey, remoteModelId } from "./ai/providers/openai-compatible.ts";
import type { AiMessage, AiTaskCategory, ImageInput } from "./ai/types.ts";

/**
 * "UZAK sağlayıcı yapılandırıldı mı?" Yerel sağlayıcı her zaman hazırdır, bu
 * yüzden bu fonksiyon yerel katmanı KASTETMEZ. Rotalar bunu, ücretli çağrı
 * yapmadan önce kendi güvenli yerel yedeklerine düşmek için kullanır;
 * anlamının göç öncesiyle aynı kalması davranışı değiştirmemek için şart.
 */
export function hasAiProvider() {
  return Boolean(remoteApiKey());
}

export function aiModelId() {
  return remoteModelId();
}

export type AiTextRequest = {
  system?: string;
  image?: ImageInput;
  model?: string;
  category?: AiTaskCategory;
  minimumOutputTokens?: number;
  providerOptions?: Record<string, Record<string, JSONValue>>;
  maxOutputTokens?: number;
  temperature?: number;
  abortSignal?: AbortSignal;
} & ({ prompt: string; messages?: undefined } | { messages: ModelMessage[]; prompt?: undefined });

// AI SDK'nın ModelMessage'ı, router'ın sade AiMessage'ına. Bu katmandan geçen
// çağrılar yalnızca düz metin mesajı kullanıyor; içerik dizi biçimindeyse
// (araç çağrısı vb.) metin parçaları birleştirilir.
function toAiMessages(messages: ModelMessage[]): AiMessage[] {
  return messages.flatMap((message) => {
    if (message.role !== "user" && message.role !== "assistant") return [];
    const text = typeof message.content === "string"
      ? message.content
      : message.content.map((part) => ("text" in part && typeof part.text === "string" ? part.text : "")).join("");
    return text ? [{ role: message.role, text }] : [];
  });
}

export async function generateAiText(request: AiTextRequest): Promise<string> {
  const response = await routeText({
    // Görsel içeren istek yalnızca uzak sağlayıcıda çalışır; kategori bunu
    // yönlendiriciye bildirir ki yerel sağlayıcı boşuna denenmesin.
    category: request.category ?? (request.image ? "vision" : "conversation"),
    system: request.system,
    ...(request.messages ? { messages: toAiMessages(request.messages) } : { prompt: request.prompt }),
    image: request.image,
    model: request.model,
    minimumOutputTokens: request.minimumOutputTokens,
    providerOptions: request.providerOptions,
    maxOutputTokens: request.maxOutputTokens,
    temperature: request.temperature,
    abortSignal: request.abortSignal,
  });
  return response.text;
}

export type AiObjectRequest<T> = {
  system?: string;
  prompt: string;
  image?: ImageInput;
  model?: string;
  category?: AiTaskCategory;
  minimumOutputTokens?: number;
  providerOptions?: Record<string, Record<string, JSONValue>>;
  schema: FlexibleSchema<T>;
  maxOutputTokens?: number;
  temperature?: number;
  abortSignal?: AbortSignal;
};

export async function generateAiObject<T>(request: AiObjectRequest<T>): Promise<T> {
  const response = await routeObject<T>({
    category: request.category ?? (request.image ? "vision" : "structured_extraction"),
    schema: request.schema,
    system: request.system,
    prompt: request.prompt,
    image: request.image,
    model: request.model,
    minimumOutputTokens: request.minimumOutputTokens,
    providerOptions: request.providerOptions,
    maxOutputTokens: request.maxOutputTokens,
    temperature: request.temperature,
    abortSignal: request.abortSignal,
  });
  return response.object;
}

// Bir data URL'sini ("data:image/jpeg;base64,...") ImageInput'a çevirir.
// Tüm route'lar aynı doğrulamayı tekrarlamasın diye burada.
export function parseImageDataUrl(dataUrl: string): ImageInput | null {
  const match = dataUrl.match(/^data:(image\/[\w.+-]+);base64,(.+)$/);
  if (!match || match[2].length > 7_000_000) return null;
  return { mimeType: match[1], base64: match[2] };
}
