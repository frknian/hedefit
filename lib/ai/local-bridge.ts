// Cihaz üstü AI'nın JavaScript köprüsü.
//
// Native tarafla (Capacitor eklentisi `HedefitLocalAI`) konuşan TEK yer burası.
// Uygulamanın geri kalanı LiteRT-LM, Kotlin veya Capacitor eklenti adı gibi
// ayrıntıları görmez.
//
// TARAYICIDA: eklenti yoktur. Bütün fonksiyonlar "yok" durumunu döndürür ve
// yönlendirici uzak sağlayıcıyı seçer — web sürümü hiç etkilenmez.

export type LocalAiNativeState =
  | "UNSUPPORTED_PLATFORM"
  | "LOW_MEMORY"
  | "INSUFFICIENT_STORAGE"
  | "MODEL_NOT_INSTALLED"
  | "MODEL_READY"
  | "RUNTIME_ERROR";

export type NativeCapabilities = {
  runtimeAvailable: boolean;
  supported: boolean;
  state: LocalAiNativeState;
  reason?: string | null;
  abi?: string;
  sdkInt?: number;
  totalRamMb?: number;
  availableRamMb?: number;
  freeStorageMb?: number;
  lowRamDevice?: boolean;
  engineLoaded?: boolean;
  loadedModelId?: string | null;
};

export type NativeModelInfo = {
  id: string;
  displayName: string;
  sizeBytes: number;
  installed: boolean;
  minTotalRamMb: number;
};

export type NativeGenerateResult = {
  text: string;
  modelId: string;
  totalMs: number;
  loadMs: number;
  promptTokens: number;
  outputTokens: number;
  ttftMs: number;
  decodeTokensPerSecond: number;
  prefillTokensPerSecond: number;
};

export type LocalAiPlugin = {
  getCapabilities(options?: { modelId?: string }): Promise<NativeCapabilities>;
  listModels(): Promise<{ models: NativeModelInfo[]; defaultModelId: string }>;
  getModelStatus(options: { modelId?: string }): Promise<{ modelId: string; installed: boolean; sizeBytes: number; downloadedBytes: number; downloading: boolean; loaded: boolean }>;
  downloadModel(options: { modelId?: string }): Promise<{ installed: boolean; modelId: string }>;
  cancelDownload(): Promise<void>;
  deleteModel(options: { modelId?: string }): Promise<{ deleted: boolean }>;
  loadModel(options: { modelId?: string; timeoutMs?: number }): Promise<{ loadMs: number; modelId: string }>;
  unloadModel(): Promise<void>;
  generate(options: {
    modelId?: string; systemPrompt: string; userPrompt: string;
    maxOutputTokens?: number; temperature?: number; timeoutMs?: number;
    stream?: boolean; requestId?: string;
  }): Promise<NativeGenerateResult>;
  cancelGeneration(): Promise<void>;
  addListener(event: "localAiToken" | "localAiDownloadProgress", handler: (data: Record<string, unknown>) => void): Promise<{ remove: () => Promise<void> }>;
};

type CapacitorGlobal = {
  isNativePlatform?: () => boolean;
  getPlatform?: () => string;
  Plugins?: Record<string, unknown>;
};

function capacitor(): CapacitorGlobal | undefined {
  return (globalThis as { Capacitor?: CapacitorGlobal }).Capacitor;
}

/**
 * Eklentiyi döndürür; yoksa `undefined`.
 *
 * Testlerin ve tarayıcının aynı yoldan geçebilmesi için `globalThis` üzerinden
 * okunur. Eklenti Capacitor tarafından WebView'a enjekte edilir.
 */
export function localAiPlugin(): LocalAiPlugin | undefined {
  const injected = (globalThis as { HedefitLocalAI?: LocalAiPlugin }).HedefitLocalAI;
  if (injected) return injected;
  const plugins = capacitor()?.Plugins;
  const plugin = plugins?.HedefitLocalAI as LocalAiPlugin | undefined;
  return plugin;
}

export function isNativeAndroid(): boolean {
  const runtime = capacitor();
  return Boolean(runtime?.isNativePlatform?.() && runtime.getPlatform?.() === "android");
}

/** Eklenti yoksa "desteklenmiyor" — hata DEĞİL. Web'de normal durum budur. */
export async function readNativeCapabilities(modelId?: string): Promise<NativeCapabilities> {
  const plugin = localAiPlugin();
  if (!plugin) {
    return { runtimeAvailable: false, supported: false, state: "UNSUPPORTED_PLATFORM", reason: "native_bridge_unavailable" };
  }
  try {
    return await plugin.getCapabilities(modelId ? { modelId } : undefined);
  } catch {
    // Native taraf beklenmedik bir şey döndürdüyse uygulama ÇÖKMEZ.
    return { runtimeAvailable: false, supported: false, state: "RUNTIME_ERROR", reason: "capability_call_failed" };
  }
}
