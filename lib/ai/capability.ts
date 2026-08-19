// Cihaz üstü (on-device) AI yeteneği algılama.
//
// DÜRÜST DURUM TESPİTİ: Hedefit bir Next.js/RSC uygulamasıdır; sunucu tarafı
// Cloudflare Worker'da, istemci tarafı tarayıcıda ve Capacitor WebView'ında
// çalışır. Bugün bu üç ortamın hiçbirinde KURULU bir cihaz-üstü LLM çalışma
// zamanı yok:
//
//   · Cloudflare Worker  → kullanıcının cihazı değil; "yerel" tanımına girmez
//   · Tarayıcı/WebView   → WebGPU çıkarımı teknik olarak mümkün, ama modeli
//                          indirmek için native bir köprü ve depolama yönetimi
//                          gerekir (aşağıdaki BLOKAJ)
//   · Android/iOS native → gerçek çözüm; Capacitor eklentisi yazılmalı
//
// Bu modül o yüzden bir YER TUTUCU DEĞİL, bir SÖZLEŞMEdir: native köprü
// eklendiğinde `detectDeviceAiCapability` gerçek değerleri döndürmeye başlar
// ve router hiç değişmeden yerel modeli kullanmaya başlar. Bugün ise dürüst
// biçimde `LOCAL_NOT_SUPPORTED` döner ve uygulama uzak sağlayıcıyla çalışır.
//
// Ayrıntılı gerekçe ve model seçimi: docs/AI_MODEL_DECISION.md

export type LocalAiState =
  | "LOCAL_READY"
  | "LOCAL_MODEL_NOT_DOWNLOADED"
  | "LOCAL_NOT_SUPPORTED"
  | "LOCAL_ERROR"
  | "REMOTE_ONLY";

export type DeviceAICapability = {
  supported: boolean;
  state: LocalAiState;
  reason?: string;
  availableMemoryMb?: number;
  modelInstalled: boolean;
  runtimeAvailable: boolean;
};

/**
 * Native köprünün uygulaması gereken arayüz. Bir Capacitor eklentisi bu şekli
 * `window.HedefitLocalAi` üzerinden sağladığında algılama otomatik olarak
 * gerçek değerlere geçer — bu dosyada değişiklik gerekmez.
 */
export type LocalAiBridge = {
  isRuntimeAvailable(): Promise<boolean>;
  isModelInstalled(): Promise<boolean>;
  availableMemoryMb(): Promise<number>;
};

// Küçük bir quantize modelin (≈1–2 GB) yüklenip üretim yapabilmesi için
// gereken serbest bellek tabanı. Altındaki cihazda model yüklemek uygulamayı
// çökertir; bu yüzden yetenek algılama seviyesinde eleriz.
export const MIN_LOCAL_MEMORY_MB = 2_048;

function bridge(): LocalAiBridge | undefined {
  if (typeof globalThis === "undefined") return undefined;
  return (globalThis as { HedefitLocalAi?: LocalAiBridge }).HedefitLocalAi;
}

export async function detectDeviceAiCapability(): Promise<DeviceAICapability> {
  const native = bridge();
  if (!native) {
    return {
      supported: false,
      state: "LOCAL_NOT_SUPPORTED",
      reason: "no on-device inference runtime is installed on this platform",
      modelInstalled: false,
      runtimeAvailable: false,
    };
  }
  try {
    const runtimeAvailable = await native.isRuntimeAvailable();
    if (!runtimeAvailable) {
      return { supported: false, state: "LOCAL_NOT_SUPPORTED", reason: "runtime reported unavailable", modelInstalled: false, runtimeAvailable: false };
    }
    const availableMemoryMb = await native.availableMemoryMb();
    if (availableMemoryMb < MIN_LOCAL_MEMORY_MB) {
      return { supported: false, state: "LOCAL_NOT_SUPPORTED", reason: "insufficient memory", availableMemoryMb, modelInstalled: false, runtimeAvailable: true };
    }
    const modelInstalled = await native.isModelInstalled();
    return {
      supported: modelInstalled,
      state: modelInstalled ? "LOCAL_READY" : "LOCAL_MODEL_NOT_DOWNLOADED",
      availableMemoryMb,
      modelInstalled,
      runtimeAvailable: true,
    };
  } catch (error) {
    // Köprü hata verirse uygulama ÇÖKMEZ; uzak sağlayıcıyla devam eder.
    return {
      supported: false,
      state: "LOCAL_ERROR",
      reason: error instanceof Error ? error.name : "unknown bridge error",
      modelInstalled: false,
      runtimeAvailable: false,
    };
  }
}
