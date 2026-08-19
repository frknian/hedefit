// GERÇEK cihaz üstü LLM sağlayıcısı (Android · LiteRT-LM).
//
// Bu, şablon üreten `local-deterministic` sağlayıcısının YERİNE GEÇMEZ; onun
// ÖNÜNE geçer. Nihai zincir:
//
//   on-device LLM  →  uzak sağlayıcı  →  deterministik güvenli yedek
//
// Native tarafa yalnızca uygulamanın kendi boru hattının ürettiği NİHAİ istem
// gider (Intelligence Engine + Memory + Context Builder + Safety). Native kod
// kendi başına bağlam üretmez, veritabanına erişmez.

import { AiUnsupportedRequestError } from "../errors.ts";
import { localAiPlugin } from "../local-bridge.ts";
import { detectDeviceAiCapability } from "../capability.ts";
import { LOCAL_MAX_OUTPUT_TOKENS, LOCAL_PROMPT_CHAR_BUDGET, LOCAL_GENERATION_TIMEOUT_MS, LOCAL_TEMPERATURE, localCapableCategories } from "../local-policy.ts";
import type { AIProvider, AiRequest, AiResponse } from "../types.ts";

export const ON_DEVICE_PROVIDER_ID = "on-device-litertlm";

/** Kullanıcı üretimi kendisi durdurdu — bu bir ARIZA DEĞİLDİR. */
export class LocalGenerationCancelledError extends Error {
  constructor() {
    super("local generation cancelled by user");
    this.name = "LocalGenerationCancelledError";
  }
}

function isCancellation(error: unknown): boolean {
  const message = error instanceof Error ? error.message : String(error);
  return /(^|\b)cancelled(\b|$)/i.test(message);
}

/**
 * İstemi yerel bütçeye sığdırır.
 *
 * Mobil çıkarımda uzun istem doğrudan gecikmedir: prefill süresi token sayısıyla
 * doğrusal artar. Modelin ilan ettiği bağlam penceresini doldurmak, kullanıcıyı
 * saniyelerce bekletmekten başka bir şey yapmaz. Kırpma SONDAN değil, bilgi
 * önceliğine göre yapılır — bu yüzden bağlam kurucusu zaten önceliklendirilmiş
 * bir metin verir ve burada yalnız sert bir tavan uygulanır.
 */
export function fitLocalPrompt(text: string, budget = LOCAL_PROMPT_CHAR_BUDGET): string {
  if (text.length <= budget) return text;
  return `${text.slice(0, budget - 1)}…`;
}

export const onDeviceProvider: AIProvider = {
  id: ON_DEVICE_PROVIDER_ID,
  kind: "local",
  // Hangi kategorilerin yerelde çalışacağı SEZGİYLE değil ölçümle belirlenir;
  // liste tek yerden yönetilir (bkz. lib/ai/local-policy.ts).
  get categories() { return localCapableCategories(); },

  async isAvailable() {
    if (!localAiPlugin()) return false;
    const capability = await detectDeviceAiCapability();
    return capability.supported && capability.state === "LOCAL_READY";
  },

  async generateText(request: AiRequest): Promise<AiResponse> {
    const plugin = localAiPlugin();
    if (!plugin) throw new AiUnsupportedRequestError("native bridge unavailable");
    if (request.image) throw new AiUnsupportedRequestError("on-device provider has no vision support");

    const userPrompt = request.messages?.at(-1)?.text || request.prompt || "";
    if (!userPrompt.trim()) throw new AiUnsupportedRequestError("empty prompt");

    const startedAt = Date.now();
    try {
      const result = await plugin.generate({
        systemPrompt: fitLocalPrompt(request.system ?? ""),
        userPrompt: fitLocalPrompt(userPrompt, 2_000),
        maxOutputTokens: Math.min(request.maxOutputTokens ?? LOCAL_MAX_OUTPUT_TOKENS, LOCAL_MAX_OUTPUT_TOKENS),
        temperature: request.temperature ?? LOCAL_TEMPERATURE,
        timeoutMs: LOCAL_GENERATION_TIMEOUT_MS,
        stream: false,
      });
      return {
        text: result.text,
        provider: ON_DEVICE_PROVIDER_ID,
        model: result.modelId,
        latencyMs: result.totalMs ?? Date.now() - startedAt,
        usage: { inputTokens: result.promptTokens, outputTokens: result.outputTokens },
      };
    } catch (error) {
      // İPTAL, yedeklemeyi TETİKLEMEZ: kullanıcı "durdur"a bastıysa arkasından
      // ücretli bir uzak çağrı başlatmak hem yanlış hem masraflıdır. Router bu
      // hatayı ayrı ele alır (bkz. lib/ai/router.ts).
      if (isCancellation(error)) throw new LocalGenerationCancelledError();
      throw error;
    }
  },
  // generateObject BİLEREK yok: küçük yerel modellerin şema uyumu ölçülmeden
  // yapılandırılmış üretimi yerele vermek, sessizce bozuk JSON üretir.
};
