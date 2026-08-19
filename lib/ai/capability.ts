// Cihaz üstü (on-device) AI yeteneği algılama.
//
// PHASE 2: bu modül artık bir "sözleşme" değil, GERÇEK bir algılama katmanı.
// Android'de Capacitor eklentisi (HedefitLocalAI → LiteRT-LM) yüklüyse gerçek
// cihaz değerleri okunur; tarayıcıda ve iOS'ta eklenti yoktur ve dürüst
// biçimde "desteklenmiyor" döner.
//
// "Android = destekleniyor" VARSAYILMAZ. Native taraf ABI, RAM, düşük bellek
// bayrağı ve depolama alanını ayrı ayrı değerlendirir (LocalAiCapability.kt);
// burada o karar taşınır ve yönlendirmeye çevrilir.

import { readNativeCapabilities, type LocalAiNativeState, type NativeCapabilities } from "./local-bridge.ts";

export type LocalAiState =
  | "LOCAL_READY"
  | "LOCAL_MODEL_NOT_DOWNLOADED"
  | "LOCAL_NOT_SUPPORTED"
  | "LOCAL_LOW_MEMORY"
  | "LOCAL_INSUFFICIENT_STORAGE"
  | "LOCAL_ERROR"
  | "LOCAL_DISABLED"
  | "REMOTE_ONLY";

export type DeviceAICapability = {
  supported: boolean;
  state: LocalAiState;
  reason?: string;
  availableMemoryMb?: number;
  totalMemoryMb?: number;
  freeStorageMb?: number;
  abi?: string;
  modelInstalled: boolean;
  runtimeAvailable: boolean;
  engineLoaded?: boolean;
};

// Native durumların uygulama durumlarına eşlemesi. Tek yerde tutulur ki
// yeni bir native durum eklendiğinde burada derleme hatası alınsın.
const STATE_MAP: Record<LocalAiNativeState, LocalAiState> = {
  UNSUPPORTED_PLATFORM: "LOCAL_NOT_SUPPORTED",
  LOW_MEMORY: "LOCAL_LOW_MEMORY",
  INSUFFICIENT_STORAGE: "LOCAL_INSUFFICIENT_STORAGE",
  MODEL_NOT_INSTALLED: "LOCAL_MODEL_NOT_DOWNLOADED",
  MODEL_READY: "LOCAL_READY",
  RUNTIME_ERROR: "LOCAL_ERROR",
};

/**
 * Yerel AI'yı tamamen kapatan anahtar.
 *
 * Bir sorun çıktığında yeni sürüm yayınlamadan yerel yolu kapatabilmek için;
 * sunucu tarafında okunur ve istemciye taşınır (bkz. lib/ai/local-policy.ts).
 */
export function localAiDisabledByConfig(): boolean {
  return process.env.LOCAL_AI_ENABLED === "0" || process.env.LOCAL_AI_ENABLED === "false";
}

function fromNative(native: NativeCapabilities): DeviceAICapability {
  const state = STATE_MAP[native.state] ?? "LOCAL_ERROR";
  return {
    supported: Boolean(native.supported) && state === "LOCAL_READY",
    state,
    ...(native.reason ? { reason: native.reason } : {}),
    ...(typeof native.availableRamMb === "number" ? { availableMemoryMb: native.availableRamMb } : {}),
    ...(typeof native.totalRamMb === "number" ? { totalMemoryMb: native.totalRamMb } : {}),
    ...(typeof native.freeStorageMb === "number" ? { freeStorageMb: native.freeStorageMb } : {}),
    ...(native.abi ? { abi: native.abi } : {}),
    modelInstalled: native.state === "MODEL_READY",
    runtimeAvailable: Boolean(native.runtimeAvailable),
    ...(typeof native.engineLoaded === "boolean" ? { engineLoaded: native.engineLoaded } : {}),
  };
}

export async function detectDeviceAiCapability(options: { modelId?: string; disabled?: boolean } = {}): Promise<DeviceAICapability> {
  if (options.disabled ?? localAiDisabledByConfig()) {
    return { supported: false, state: "LOCAL_DISABLED", reason: "disabled_by_config", modelInstalled: false, runtimeAvailable: false };
  }
  const native = await readNativeCapabilities(options.modelId);
  return fromNative(native);
}
