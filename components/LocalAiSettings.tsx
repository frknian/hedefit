"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useTranslations } from "@/lib/i18n/translate";
import { localAiPlugin, type NativeModelInfo } from "@/lib/ai/local-bridge";
import { detectDeviceAiCapability, type DeviceAICapability } from "@/lib/ai/capability";

/**
 * "Cihazda AI kullan" ayarı.
 *
 * TASARIM KURALI: Hedefit yeniden tasarlanmaz. Bu bileşen ProfileManager'daki
 * ayar satırlarının (`profile-export`) aynı yapısını ve sınıflarını kullanır.
 *
 * ÜRÜN KURALI: model SESSİZCE İNDİRİLMEZ. Kullanıcı boyutu görür ve açıkça
 * onaylar. Model kurulu değilken uygulama normal çalışmaya (uzak AI) devam
 * eder; bu ayar bir iyileştirmedir, ön koşul değil.
 *
 * Model adı ("Qwen3", "Gemma") sıradan kullanıcıya GÖSTERİLMEZ; katalogdaki
 * kullanıcı dostu ad kullanılır, teknik kimlik yalnız geliştirici panelinde.
 */
export function LocalAiSettings() {
  const t = useTranslations();
  const [capability, setCapability] = useState<DeviceAICapability | null>(null);
  const [model, setModel] = useState<NativeModelInfo | null>(null);
  const [downloading, setDownloading] = useState(false);
  const [progress, setProgress] = useState(0);
  const [error, setError] = useState("");
  const listenerRef = useRef<{ remove: () => Promise<void> } | null>(null);

  const refresh = useCallback(async () => {
    const plugin = localAiPlugin();
    if (!plugin) { setCapability(await detectDeviceAiCapability()); return; }
    const [next, list] = await Promise.all([detectDeviceAiCapability(), plugin.listModels()]);
    setCapability(next);
    setModel(list.models.find((item) => item.id === list.defaultModelId) ?? list.models[0] ?? null);
  }, []);

  // Tek efekt: durum okuma + ilerleme aboneliği.
  //
  // Durum okuma bilerek efekt gövdesinde SENKRON setState yapmaz — hepsi
  // await'lerin arkasında ve `active` bayrağıyla korunuyor; bileşen bu arada
  // sökülürse geç gelen yanıt state'e yazmaz.
  //
  // İlerleme olayları native taraftan yaklaşık %0,5'te bir gelir, her bayt için
  // değil (bkz. LocalAiModelStore); aksi hâlde React her karede render ederdi.
  useEffect(() => {
    let active = true;
    void (async () => {
      const plugin = localAiPlugin();
      if (!plugin) {
        const capabilityOnly = await detectDeviceAiCapability();
        if (active) setCapability(capabilityOnly);
        return;
      }
      const [next, list] = await Promise.all([detectDeviceAiCapability(), plugin.listModels()]);
      if (!active) return;
      setCapability(next);
      setModel(list.models.find((item) => item.id === list.defaultModelId) ?? list.models[0] ?? null);
      const handle = await plugin.addListener("localAiDownloadProgress", (data) => {
        if (!active) return;
        const downloaded = Number(data.downloadedBytes) || 0;
        const total = Number(data.totalBytes) || 1;
        setProgress(Math.min(100, Math.round((downloaded / total) * 100)));
      });
      if (active) listenerRef.current = handle;
      else void handle.remove();
    })();
    return () => { active = false; void listenerRef.current?.remove(); };
  }, []);

  // Native köprü yoksa (web tarayıcı, iOS) ayar hiç gösterilmez: var olmayan
  // bir yeteneği vaat etmemek gerekir.
  if (!capability?.runtimeAvailable) return null;

  const gigabytes = model ? (model.sizeBytes / 1_000_000_000).toFixed(1) : "?";
  const installed = capability.state === "LOCAL_READY";

  async function download() {
    const plugin = localAiPlugin();
    if (!plugin || !model) return;
    setError(""); setDownloading(true); setProgress(0);
    try {
      await plugin.downloadModel({ modelId: model.id });
      await refresh();
    } catch (failure) {
      // Ham native hata (JNI/OOM/stacktrace) ASLA gösterilmez; sınıflandırılmış
      // koda göre anlaşılır bir cümle seçilir.
      const code = failure instanceof Error ? failure.message : String(failure);
      setError(
        code.includes("integrity") ? t.localAi.errorIntegrity
        : code.includes("cancelled") ? ""
        : code.includes("storage") ? t.localAi.errorStorage
        : t.localAi.errorDownload,
      );
    } finally {
      setDownloading(false);
    }
  }

  async function cancel() {
    await localAiPlugin()?.cancelDownload();
    setDownloading(false);
  }

  async function remove() {
    const plugin = localAiPlugin();
    if (!plugin || !model) return;
    await plugin.deleteModel({ modelId: model.id });
    await refresh();
  }

  return <div className="profile-export local-ai-zone">
    <div>
      <span>{t.localAi.eyebrow}</span>
      <strong>{t.localAi.title}</strong>
      <p>{t.localAi.body}</p>
      {!installed && !downloading && capability.state === "LOCAL_MODEL_NOT_DOWNLOADED" && <small>{t.localAi.sizeHint(gigabytes)}</small>}
      {capability.state === "LOCAL_LOW_MEMORY" && <small>{t.localAi.lowMemory}</small>}
      {capability.state === "LOCAL_INSUFFICIENT_STORAGE" && <small>{t.localAi.noStorage}</small>}
      {capability.state === "LOCAL_NOT_SUPPORTED" && <small>{t.localAi.unsupported}</small>}
      {downloading && <div className="local-ai-progress" role="status" aria-live="polite">
        <div className="local-ai-progress-track"><i style={{ width: `${progress}%` }} /></div>
        <small>{t.localAi.downloading(progress)}</small>
      </div>}
      {installed && <small className="local-ai-ready">{t.localAi.ready}</small>}
      {error && <small className="local-ai-error" role="alert">{error}</small>}
    </div>
    <div>
      {installed
        ? <button type="button" onClick={() => void remove()}>{t.localAi.deleteModel}</button>
        : downloading
          ? <button type="button" onClick={() => void cancel()}>{t.localAi.cancel}</button>
          : capability.state === "LOCAL_MODEL_NOT_DOWNLOADED"
            ? <button type="button" onClick={() => void download()}>{t.localAi.download}</button>
            : null}
    </div>
  </div>;
}
