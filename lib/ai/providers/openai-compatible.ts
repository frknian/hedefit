// OpenAI-uyumlu HTTP sağlayıcısı.
//
// Moonshot (Kimi), OpenRouter, Together, Fireworks, kendi vLLM/Ollama
// sunucunuz — hepsi aynı gövdeyi konuşur, bu yüzden tek uygulama yeter.
// Göç öncesinde bu dosyanın içeriği lib/ai-provider.ts'in tamamıydı; artık
// yalnızca ZİNCİRDEKİ BİR HALKA. Kimi'ye özgü hiçbir bilgi burada
// sabitlenmez, yalnızca ortam değişkeni varsayılanı olarak durur.

import { createOpenAICompatible } from "@ai-sdk/openai-compatible";
import { asSchema, generateObject, generateText } from "ai";
import { toModelMessages, type AIProvider, type AiObjectRequest, type AiObjectResponse, type AiRequest, type AiResponse, type ImageInput } from "../types.ts";

// ÖLÇÜM (2026-08): sağlayıcının "akıl yürüten" modelleri bu uygulama için çok
// yavaş. Aynı soru/plan için ölçülen süreler:
//
//   sohbet yanıtı   kimi-k3 42 sn · kimi-k2.6 104 sn · k2.7-highspeed  4,8 sn
//   tam plan        kimi-k3 >100 sn (zaman aşımı)   · k2.7-highspeed 19 sn
//
// kimi-k3 ile sohbet 20 sn'lik pencereye yetişmediği için neredeyse her zaman
// güvenli yerel yanıta düşüyor, plan ise hiç üretilemiyordu. Varsayılan bu
// yüzden hızlı modele alındı; AI_MODEL ortam değişkeniyle yine ezilebilir.
const DEFAULT_MODEL = "kimi-k2.7-code-highspeed";

// Bazı modeller (ör. Kimi K3 — "always thinks", tamamen kapatılamaz) asıl
// yanıttan önce ayrı bir "reasoning" bütçesi tüketir ve bu bütçe de
// maxOutputTokens'a dahildir. Route'lardaki değerler (180–900) yalnızca
// GÖRÜNEN yanıt için düşünülmüştü; reasoning modelinde bu, düşünme payını
// tüketip asıl içeriğe hiç sıra bırakmadan sessizce boş sonuç döndürür (hata
// fırlatmaz). Bu yüzden route'ların istediği değerden bağımsız bir taban
// zorluyoruz; reasoning yapmayan modellerde zararsızdır.
const MIN_OUTPUT_TOKENS = 4_000;

// Ortam değişkenleri modül yüklenirken DEĞİL, her çağrıda okunur — testlerde
// (ve bazı edge çalışma zamanlarında) modül bir kez yüklenip önbelleğe alınır;
// üst düzeyde okunsaydı `AI_API_KEY` sonradan tanımlansa bile hiç görülmezdi.
export function remoteApiKey() {
  return process.env.AI_API_KEY || "";
}

export function remoteModelId() {
  return process.env.AI_MODEL || DEFAULT_MODEL;
}

function languageModel(modelId: string) {
  const provider = createOpenAICompatible({
    name: process.env.AI_PROVIDER_NAME || "moonshot",
    baseURL: process.env.AI_BASE_URL || "https://api.moonshot.ai/v1",
    apiKey: remoteApiKey(),
    headers: {
      // OpenRouter'ın kontrol panelinde uygulamayı tanımlamak için önerdiği
      // isteğe bağlı başlıklar; Moonshot dahil diğer sağlayıcılarda zararsızca
      // yok sayılır.
      "HTTP-Referer": process.env.AI_SITE_URL || "https://hedefit.app",
      "X-Title": "Hedefit",
    },
  });
  return provider.chatModel(modelId);
}

// Bazı modeller sıcaklık parametresini hiç kabul etmez veya yalnızca tek bir
// sabit değeri (1) kabul eder; başka bir değer gönderildiğinde istek tamamen
// reddedilir. Model listesi elle tutulamayacak kadar geniş ve sürekli
// değiştiği için, "geçersiz sıcaklık" hatasını yakalayıp isteği sıcaklık
// olmadan (sağlayıcının kendi varsayılanıyla) bir kez daha deneriz.
function isUnsupportedTemperatureError(error: unknown) {
  const message = error instanceof Error ? error.message : String(error);
  return /temperature/i.test(message);
}

async function withTemperatureFallback<T>(attempt: (useTemperature: boolean) => Promise<T>) {
  try {
    return await attempt(true);
  } catch (error) {
    if (!isUnsupportedTemperatureError(error)) throw error;
    return attempt(false);
  }
}

function userContent(text: string, image?: ImageInput) {
  if (!image) return text;
  return [
    { type: "text" as const, text },
    { type: "file" as const, data: image.base64, mediaType: image.mimeType },
  ];
}

// createOpenAICompatible() sağlayıcısı `supportsStructuredOutputs`'u
// belirtmediğimiz için varsayılan `false` kalır; bu da AI SDK'nın modele
// yalnızca `response_format: {type: "json_object"}` göndermesi anlamına
// gelir — bu, geçerli JSON SÖZDİZİMİni garanti eder ama alan adlarını
// (schema'yı) modele hiç iletmez. Sonuç: model kendi uydurduğu bir JSON
// şekli döndürebilir (gerçek bir Kimi K3 testinde doğrulandı). Çözüm:
// şemanın ham JSON Schema halini sistem promptuna açıkça ekleyip modele
// "bu alanları kullan" demek — her route'un promptunu elle yazmasına gerek
// kalmadan, tek yerden.
async function withSchemaInSystemPrompt(system: string | undefined, schema: Parameters<typeof asSchema>[0]) {
  const jsonSchema = await asSchema(schema).jsonSchema;
  const instruction = `Yanıtını AŞAĞIDAKİ JSON şemasına harfiyen uyacak şekilde, tam olarak bu alan adlarıyla ver (başka alan uydurma, eksik bırakma):\n${JSON.stringify(jsonSchema)}`;
  return system ? `${system}\n\n${instruction}` : instruction;
}

function outputTokens(request: AiRequest) {
  return Math.max(request.maxOutputTokens ?? 0, request.minimumOutputTokens ?? MIN_OUTPUT_TOKENS);
}

/**
 * Sağlayıcıya özgü tuhaflıklar SADECE burada.
 *
 * Moonshot'ın Kimi K2 ailesi, kapatılabilir bir "thinking" bütçesi tüketir;
 * kapatılmazsa kısa çıktı isteyen çağrılarda (ör. tek bir besin tahmini) asıl
 * içeriğe sıra kalmadan boş sonuç döner. Bu ayar önceden alan modülünde
 * (lib/ai-nutrition-estimator.ts) duruyordu — yani bir BESLENME modülü
 * sağlayıcının adını ve model ailesini bilmek zorundaydı. Sağlayıcı
 * değiştiğinde o dosyanın da düzenlenmesi gerekirdi; tam olarak göçün
 * kaldırmayı hedeflediği bağımlılık. Artık alan modülleri yalnızca "kısa ve
 * yapılandırılmış çıktı istiyorum" der, nasıl elde edileceği buranın işidir.
 */
function providerQuirks(modelId: string, request: AiRequest) {
  const isMoonshotK2 = (process.env.AI_PROVIDER_NAME || "moonshot") === "moonshot" && /^kimi-k2(?:\.|$)/.test(modelId);
  if (!isMoonshotK2) return { providerOptions: request.providerOptions, minimumOutputTokens: request.minimumOutputTokens };
  return {
    providerOptions: request.providerOptions ?? { moonshot: { thinking: { type: "disabled" } } },
    // Düşünme kapalıyken 4.000 token'lık taban gereksiz; küçük bir taban yeter.
    minimumOutputTokens: request.minimumOutputTokens ?? 350,
  };
}

export const openAiCompatibleProvider: AIProvider = {
  id: "openai-compatible",
  kind: "remote",

  // Anahtar yoksa sağlayıcı yok sayılır; router bir sonrakine geçer. Ağ
  // yoklaması YAPMIYORUZ: her istekte fazladan bir round trip, edge
  // çalışma zamanında gecikmeyi ikiye katlardı. Gerçek erişilebilirlik
  // isteğin kendisinde ölçülür, hata router'da yedeklemeyi tetikler.
  async isAvailable() {
    return Boolean(remoteApiKey());
  },

  async generateText(request: AiRequest): Promise<AiResponse> {
    const model = request.model || remoteModelId();
    const quirks = providerQuirks(model, request);
    const startedAt = Date.now();
    const result = await withTemperatureFallback((useTemperature) => generateText({
      model: languageModel(model),
      system: request.system,
      ...(request.messages?.length
        ? { messages: toModelMessages(request.messages) }
        : { prompt: [{ role: "user" as const, content: userContent(request.prompt ?? "", request.image) }] }),
      maxOutputTokens: outputTokens({ ...request, minimumOutputTokens: quirks.minimumOutputTokens }),
      temperature: useTemperature ? request.temperature : undefined,
      providerOptions: quirks.providerOptions,
      abortSignal: request.abortSignal,
    }));
    return {
      text: result.text,
      provider: openAiCompatibleProvider.id,
      model,
      latencyMs: Date.now() - startedAt,
      usage: { inputTokens: result.usage?.inputTokens, outputTokens: result.usage?.outputTokens },
    };
  },

  async generateObject<T>(request: AiObjectRequest<T>): Promise<AiObjectResponse<T>> {
    const model = request.model || remoteModelId();
    const quirks = providerQuirks(model, request);
    const system = await withSchemaInSystemPrompt(request.system, request.schema);
    const startedAt = Date.now();
    const result = await withTemperatureFallback((useTemperature) => generateObject({
      model: languageModel(model),
      // AI SDK varsayılanı 2 yeniden deneme, yani 3 tam üretim. Bu sağlayıcının
      // akıl yürüten modellerinde tek üretim ~90 sn sürüyor; üç katı her zaman
      // zaman aşımına düşüyordu. Tek deneme, verilen bütçenin tamamını kullanır.
      maxRetries: 0,
      system,
      prompt: [{ role: "user", content: userContent(request.prompt, request.image) }],
      schema: request.schema,
      maxOutputTokens: outputTokens({ ...request, minimumOutputTokens: quirks.minimumOutputTokens }),
      temperature: useTemperature ? request.temperature : undefined,
      providerOptions: quirks.providerOptions,
      abortSignal: request.abortSignal,
    }));
    return {
      object: result.object,
      provider: openAiCompatibleProvider.id,
      model,
      latencyMs: Date.now() - startedAt,
      usage: { inputTokens: result.usage?.inputTokens, outputTokens: result.usage?.outputTokens },
    };
  },
};
