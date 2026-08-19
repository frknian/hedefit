// Sağlayıcı kaydı.
//
// Yeni bir sağlayıcı eklemek TEK satırdır:
//
//   providerRegistry.register(openAiProvider);
//
// Hedefit'in geri kalanında hiçbir dosya değişmez. Sıra önemlidir: router
// zinciri bu sırayla dener, `local` olanlar `remote` olanlardan önce gelir.

import { deterministicLocalProvider } from "./deterministic-local.ts";
import { onDeviceProvider } from "./on-device.ts";
import { openAiCompatibleProvider } from "./openai-compatible.ts";
import type { AIProvider } from "../types.ts";

class ProviderRegistry {
  #providers: AIProvider[] = [];

  register(provider: AIProvider): this {
    // Aynı id iki kez kaydedilirse sonuncusu kazanır; testlerin sağlayıcıyı
    // sahtesiyle değiştirebilmesi için gerekli.
    this.#providers = [...this.#providers.filter((existing) => existing.id !== provider.id), provider];
    return this;
  }

  unregister(id: string): this {
    this.#providers = this.#providers.filter((provider) => provider.id !== id);
    return this;
  }

  get(id: string): AIProvider | undefined {
    return this.#providers.find((provider) => provider.id === id);
  }

  /**
   * Yerel sağlayıcılar önce. Aynı türde olanlar KAYIT SIRASINI korur —
   * bu yüzden gerçek cihaz üstü LLM, deterministik şablon sağlayıcısından
   * önce kaydedilir (bkz. DEFAULT_PROVIDERS).
   */
  list(): AIProvider[] {
    return [
      ...this.#providers.filter((provider) => provider.kind === "local"),
      ...this.#providers.filter((provider) => provider.kind === "remote"),
    ];
  }

  reset(providers: AIProvider[] = DEFAULT_PROVIDERS): this {
    this.#providers = [...providers];
    return this;
  }
}

// SIRA ÖNEMLİ:
//   1. onDeviceProvider          — gerçek cihaz üstü LLM (varsa)
//   2. openAiCompatibleProvider  — uzak sağlayıcı
//   3. deterministicLocalProvider — her koşulda çalışan güvenli son çare
//
// Deterministik sağlayıcı `local` türünde olduğu için listede uzak
// sağlayıcıdan önce görünür; ama serbest sohbette yalnız son çare
// kategorilerine sahiptir, bu yüzden router onu ZİNCİRİN SONUNA koyar
// (bkz. lib/ai/router.ts selectProviders).
const DEFAULT_PROVIDERS: AIProvider[] = [onDeviceProvider, deterministicLocalProvider, openAiCompatibleProvider];

export const providerRegistry = new ProviderRegistry().reset();
