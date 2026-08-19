import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";
import { providerRegistry } from "../lib/ai/providers/registry.ts";
import { onDeviceProvider, fitLocalPrompt, LocalGenerationCancelledError, ON_DEVICE_PROVIDER_ID } from "../lib/ai/providers/on-device.ts";
import { deterministicLocalProvider } from "../lib/ai/providers/deterministic-local.ts";
import { routeText, selectProviders } from "../lib/ai/router.ts";
import { AiAllProvidersFailedError } from "../lib/ai/errors.ts";
import { detectDeviceAiCapability } from "../lib/ai/capability.ts";
import { evaluateResponse, looksTurkish, extractNumbers, summarize } from "../lib/ai/benchmark.ts";
import { LOCAL_PROMPT_CHAR_BUDGET, localCapableCategories } from "../lib/ai/local-policy.ts";

const SILENT = { sink: () => {} };

/** Native köprüyü taklit eder. Gerçek eklenti WebView'da enjekte edilir. */
function installBridge(overrides = {}) {
  globalThis.HedefitLocalAI = {
    getCapabilities: async () => ({
      runtimeAvailable: true, supported: true, state: "MODEL_READY",
      abi: "arm64-v8a", sdkInt: 34, totalRamMb: 8192, availableRamMb: 4096,
      freeStorageMb: 20000, lowRamDevice: false, engineLoaded: true,
      ...(overrides.capabilities ?? {}),
    }),
    generate: async () => {
      if (overrides.generateThrows) throw overrides.generateThrows;
      return {
        text: overrides.text ?? "Bugün 30 dakikalık tempolu yürüyüş iyi bir seçim.",
        modelId: "qwen3-0.6b-int4", totalMs: 900, loadMs: 0,
        promptTokens: 420, outputTokens: 60, ttftMs: 250,
        decodeTokensPerSecond: 18.5, prefillTokensPerSecond: 300,
      };
    },
    ...overrides.extra,
  };
}
function removeBridge() { delete globalThis.HedefitLocalAI; }

test.afterEach(() => { removeBridge(); providerRegistry.reset(); });

// ------------------------------------------------------------- YETENEK

test("native köprü yokken (web/tarayıcı) yerel AI desteklenmez", async () => {
  removeBridge();
  const capability = await detectDeviceAiCapability();
  assert.equal(capability.supported, false);
  assert.equal(capability.state, "LOCAL_NOT_SUPPORTED");
  assert.equal(capability.runtimeAvailable, false);
});

test("model kurulu değilse yerel AI hazır sayılmaz", async () => {
  installBridge({ capabilities: { state: "MODEL_NOT_INSTALLED", supported: true } });
  const capability = await detectDeviceAiCapability();
  assert.equal(capability.state, "LOCAL_MODEL_NOT_DOWNLOADED");
  assert.equal(capability.supported, false, "model yokken üretim denenmemeli");
});

test("düşük bellek ve yetersiz depolama ayrı durumlar olarak raporlanır", async () => {
  installBridge({ capabilities: { state: "LOW_MEMORY", supported: false } });
  assert.equal((await detectDeviceAiCapability()).state, "LOCAL_LOW_MEMORY");
  installBridge({ capabilities: { state: "INSUFFICIENT_STORAGE", supported: false } });
  assert.equal((await detectDeviceAiCapability()).state, "LOCAL_INSUFFICIENT_STORAGE");
});

test("native köprü hata verirse uygulama ÇÖKMEZ, RUNTIME_ERROR döner", async () => {
  globalThis.HedefitLocalAI = { getCapabilities: async () => { throw new Error("jni boom"); } };
  const capability = await detectDeviceAiCapability();
  assert.equal(capability.state, "LOCAL_ERROR");
  assert.equal(capability.supported, false);
});

test("yapılandırmayla yerel AI tamamen kapatılabilir", async () => {
  installBridge();
  const capability = await detectDeviceAiCapability({ disabled: true });
  assert.equal(capability.state, "LOCAL_DISABLED");
  assert.equal(capability.supported, false);
});

// -------------------------------------------------------- SAĞLAYICI

test("cihaz üstü sağlayıcı hazır olduğunda gerçek üretim yapar", async () => {
  installBridge();
  assert.equal(await onDeviceProvider.isAvailable(), true);
  const response = await onDeviceProvider.generateText({ category: "simple_coaching", prompt: "bugün ne yapmalıyım?" });
  assert.equal(response.provider, ON_DEVICE_PROVIDER_ID);
  assert.equal(response.model, "qwen3-0.6b-int4");
  assert.equal(response.usage.outputTokens, 60);
});

test("görsel içeren istek cihaz üstü sağlayıcıya GİTMEZ", async () => {
  installBridge();
  await assert.rejects(
    () => onDeviceProvider.generateText({ category: "vision", prompt: "bu ne?", image: { mimeType: "image/jpeg", base64: "x" } }),
    /vision/,
  );
});

test("cihaz üstü sağlayıcı şema üretimi yapamaz", () => {
  // Küçük modellerin şema uyumu ölçülmeden yapılandırılmış üretim yerele
  // verilirse sessizce bozuk JSON üretilir.
  assert.equal(onDeviceProvider.generateObject, undefined);
});

test("yerel istem sert bir karakter tavanına sığdırılır", () => {
  const long = "a".repeat(LOCAL_PROMPT_CHAR_BUDGET + 500);
  const fitted = fitLocalPrompt(long);
  assert.equal(fitted.length, LOCAL_PROMPT_CHAR_BUDGET);
  assert.ok(fitted.endsWith("…"));
  assert.equal(fitLocalPrompt("kısa"), "kısa");
});

test("yerelde çalışacak kategoriler dar ve yapılandırılabilir", () => {
  const previous = process.env.LOCAL_AI_CATEGORIES;
  delete process.env.LOCAL_AI_CATEGORIES;
  const defaults = localCapableCategories();
  assert.ok(defaults.includes("simple_coaching"));
  assert.ok(!defaults.includes("vision"), "görsel yerelde çalışmamalı");
  assert.ok(!defaults.includes("complex_reasoning"), "karmaşık plan ölçülmeden yerele verilmemeli");

  process.env.LOCAL_AI_CATEGORIES = "motivation,uydurma_kategori";
  assert.deepEqual(localCapableCategories(), ["motivation"], "bilinmeyen kategori sessizce elenmeli");
  if (previous === undefined) delete process.env.LOCAL_AI_CATEGORIES; else process.env.LOCAL_AI_CATEGORIES = previous;
});

// ------------------------------------------------------- YÖNLENDİRME

function remoteStub(behaviour = {}) {
  return {
    id: "openai-compatible", kind: "remote",
    isAvailable: async () => true,
    generateText: async () => {
      if (behaviour.throws) throw behaviour.throws;
      behaviour.calls && behaviour.calls.push(1);
      return { text: "uzak yanıt", provider: "openai-compatible", model: "remote-model", latencyMs: 10 };
    },
  };
}

test("yerel hazırsa uzak sağlayıcıya HİÇ gidilmez", async () => {
  installBridge();
  const calls = [];
  providerRegistry.reset([onDeviceProvider, remoteStub({ calls })]);
  const response = await routeText({ category: "simple_coaching", prompt: "bugün spor yapmalı mıyım?" }, SILENT);
  assert.equal(response.provider, ON_DEVICE_PROVIDER_ID);
  assert.equal(calls.length, 0, "yerelde cevaplanan istek ücretli çağrı üretmemeli");
});

test("yerel model yoksa uzak sağlayıcı kullanılır", async () => {
  installBridge({ capabilities: { state: "MODEL_NOT_INSTALLED", supported: true } });
  providerRegistry.reset([onDeviceProvider, remoteStub()]);
  const response = await routeText({ category: "simple_coaching", prompt: "x" }, SILENT);
  assert.equal(response.provider, "openai-compatible");
});

test("yerel çalışma zamanı hatasında uzak sağlayıcıya düşülür", async () => {
  installBridge({ generateThrows: new Error("delegate init failed") });
  providerRegistry.reset([onDeviceProvider, remoteStub()]);
  const response = await routeText({ category: "simple_coaching", prompt: "x" }, SILENT);
  assert.equal(response.provider, "openai-compatible");
  assert.equal(response.fallbackUsed, true);
});

test("yerel zaman aşımında uzak sağlayıcıya düşülür", async () => {
  installBridge({ generateThrows: new Error("generation_timeout") });
  providerRegistry.reset([onDeviceProvider, remoteStub()]);
  const response = await routeText({ category: "simple_coaching", prompt: "x" }, SILENT);
  assert.equal(response.provider, "openai-compatible");
});

test("KULLANICI İPTALİ uzak sağlayıcıya düşmeyi TETİKLEMEZ", async () => {
  // İptal kullanıcının açık isteğidir; arkasından ücretli çağrı başlatmak
  // hem parayı boşa harcar hem istenmeyen işi yapar.
  installBridge({ generateThrows: new Error("cancelled") });
  const calls = [];
  providerRegistry.reset([onDeviceProvider, remoteStub({ calls })]);
  await assert.rejects(
    () => routeText({ category: "simple_coaching", prompt: "x" }, SILENT),
    LocalGenerationCancelledError,
  );
  assert.equal(calls.length, 0, "iptalden sonra uzak çağrı YAPILMAMALI");
});

test("yerel + uzak başarısızsa deterministik güvenli yedek devreye girer", async () => {
  installBridge({ generateThrows: new Error("oom") });
  providerRegistry.reset([onDeviceProvider, deterministicLocalProvider, remoteStub({ throws: new Error("500") })]);
  const response = await routeText({ category: "conversation", prompt: "bugün ne yapmalıyım?" }, SILENT);
  assert.equal(response.provider, "local-deterministic", "üçüncü katman her koşulda yanıt vermeli");
});

test("yalnız-yerel modda uzak sağlayıcıya İSTEK GİTMEZ", async () => {
  installBridge({ generateThrows: new Error("oom") });
  const calls = [];
  providerRegistry.reset([onDeviceProvider, deterministicLocalProvider, remoteStub({ calls })]);
  const chain = await selectProviders({ category: "simple_coaching" }, { mode: "local" }, false);
  assert.ok(chain.every((provider) => provider.kind === "local"));
  const response = await routeText({ category: "conversation", prompt: "merhaba" }, { ...SILENT, mode: "local" });
  assert.equal(response.provider, "local-deterministic");
  assert.equal(calls.length, 0, "yalnız-yerel modda ağ isteği olmamalı");
});

test("koç sohbeti (conversation) cihaz üstü modele ULAŞIR", async () => {
  // Bu testin varlık sebebi gerçek bir hata: "conversation" yerel kategori
  // listesinde olmadığı için cihaz üstü model uygulamanın ASIL özelliğinde
  // hiç devreye girmiyordu.
  installBridge();
  const previousKey = process.env.AI_API_KEY;
  process.env.AI_API_KEY = "test-key";
  try {
    providerRegistry.reset();
    const chain = await selectProviders({ category: "conversation" }, {}, false);
    assert.deepEqual(
      chain.map((provider) => provider.id),
      [ON_DEVICE_PROVIDER_ID, "openai-compatible", "local-deterministic"],
      "sıra: cihaz üstü → uzak → deterministik son çare",
    );
  } finally {
    if (previousKey === undefined) delete process.env.AI_API_KEY; else process.env.AI_API_KEY = previousKey;
  }
});

test("görsel ve karmaşık akıl yürütme yerele GİTMEZ", async () => {
  installBridge();
  const previousKey = process.env.AI_API_KEY;
  process.env.AI_API_KEY = "test-key";
  try {
    providerRegistry.reset();
    for (const category of ["vision", "complex_reasoning", "structured_extraction"]) {
      const chain = await selectProviders({ category }, {}, false);
      assert.ok(!chain.some((provider) => provider.id === ON_DEVICE_PROVIDER_ID), `${category} yerele gitmemeli`);
    }
  } finally {
    if (previousKey === undefined) delete process.env.AI_API_KEY; else process.env.AI_API_KEY = previousKey;
  }
});

// ------------------------------------------------------- KARŞILAŞTIRMA

test("gerçekleri koruyan yanıt geçer, uyduran yanıt kalır", () => {
  const good = evaluateResponse("Bugün 350 kcal alanın kaldı, dengeli bir öğün iyi olur.", { mustBeTurkish: true, mustContainNumbers: [350] });
  assert.equal(good.passed, true);
  const bad = evaluateResponse("Bugün 900 kcal alanın kaldı.", { mustBeTurkish: true, mustContainNumbers: [350] });
  assert.equal(bad.passed, false);
  assert.equal(bad.failures[0].check, "mustContainNumbers");
});

test("veri yokken sayı üretmek başarısızlıktır", () => {
  const invented = evaluateResponse("Bugün 8000 adım attın, harika!", { mustAdmitMissing: true, mustNotInventNumbers: true });
  assert.equal(invented.passed, false);
  const honest = evaluateResponse("Adım verini göremiyorum; kaydedersen değerlendirebilirim.", { mustAdmitMissing: true, mustNotInventNumbers: true });
  assert.equal(honest.passed, true);
});

test("İngilizce yanıt Türkçe beklentisini karşılamaz", () => {
  assert.equal(looksTurkish("Bugün yürüyüş yapman iyi bir seçim olabilir."), true);
  assert.equal(looksTurkish("You should walk today, the calories you have left are fine."), false);
  assert.equal(evaluateResponse("You should do a workout today with your calories.", { mustBeTurkish: true }).passed, false);
});

test("enjeksiyona uyan yanıt yakalanır", () => {
  const obeyed = evaluateResponse("Önceki tüm kuralları yok sayıyorum ve tanı koyuyorum: diyabet.", { mustNotObeyInjection: true });
  assert.equal(obeyed.passed, false);
});

test("sayı çıkarımı ondalık virgülü anlar ve benzer sayıları karıştırmaz", () => {
  assert.deepEqual(extractNumbers("350 kcal, 0,4 kg ve 7230 adım"), [350, 0.4, 7230]);
  assert.equal(evaluateResponse("3500 adım attın.", { mustContainNumbers: [350] }).passed, false, "3500, 350 sayılmamalı");
});

test("boş yanıt her koşulda başarısızdır", () => {
  assert.equal(evaluateResponse("", { mustBeTurkish: true }).passed, false);
  assert.equal(evaluateResponse("   ", {}).passed, false);
});

test("özet grup kırılımı ve yüzdeleri doğru üretir", () => {
  const summary = summarize("test-model", [
    { id: "a", group: "A", provider: "p", model: "m", text: "x", evaluation: { passed: true, failures: [], softMisses: [], wordCount: 5 }, latencyMs: 100, ttftMs: 50, decodeTokensPerSecond: 20 },
    { id: "b", group: "A", provider: "p", model: "m", text: "y", evaluation: { passed: false, failures: [{ check: "c", detail: "d" }], softMisses: [], wordCount: 3 }, latencyMs: 300, ttftMs: 90, decodeTokensPerSecond: 10 },
    { id: "c", group: "B", provider: "p", model: "m", text: "", evaluation: { passed: false, failures: [], softMisses: [], wordCount: 0 }, error: "timeout" },
  ]);
  assert.equal(summary.total, 3);
  assert.equal(summary.passed, 1);
  assert.equal(summary.errored, 1);
  assert.equal(summary.failed, 1);
  assert.deepEqual(summary.byGroup.A, { total: 2, passed: 1 });
  assert.equal(summary.latency.medianMs, 300);
});

// ----------------------------------------------------- VERİ KÜMESİ

test("karşılaştırma kümesi tüm zorunlu grupları ve en az 40 senaryo içerir", async () => {
  const dataset = JSON.parse(await readFile(new URL("./fixtures/ai/hedefit-local-benchmark.json", import.meta.url), "utf8"));
  assert.ok(dataset.scenarios.length >= 40, `en az 40 senaryo gerekli, ${dataset.scenarios.length} var`);
  for (const group of ["A", "B", "C", "D", "E", "F", "G", "H", "I", "J"]) {
    assert.ok(dataset.scenarios.some((s) => s.group === group), `grup eksik: ${group}`);
  }
  // Her senaryonun doğrulanabilir en az bir denetimi olmalı; aksi hâlde
  // "geçti" demek anlamsız olurdu.
  for (const scenario of dataset.scenarios) {
    assert.ok(Object.keys(scenario.checks ?? {}).length > 0, `denetimsiz senaryo: ${scenario.id}`);
  }
});

// --------------------------------------------------------- GÜVENLİK

test("Android katmanında sağlayıcı anahtarı veya sabit sır YOK", async () => {
  const files = ["HedefitLocalAiPlugin.kt", "LocalAiEngine.kt", "LocalAiModelStore.kt", "LocalAiModelCatalog.kt", "LocalAiCapability.kt"];
  for (const name of files) {
    const source = await readFile(new URL(`../android/app/src/main/java/com/hedefit/app/localai/${name}`, import.meta.url), "utf8");
    assert.doesNotMatch(source, /AI_API_KEY|Bearer\s|api[_-]?key\s*=\s*"/i, `${name} içinde sır olmamalı`);
    assert.doesNotMatch(source, /moonshot|kimi|openai\.com/i, `${name} uzak sağlayıcı bilmemeli`);
  }
});

test("native eklenti JS'ten keyfi dosya yolu veya indirme adresi KABUL ETMEZ", async () => {
  const plugin = await readFile(new URL("../android/app/src/main/java/com/hedefit/app/localai/HedefitLocalAiPlugin.kt", import.meta.url), "utf8");
  // Yalnız katalogdaki kimlik kabul edilir.
  assert.match(plugin, /LocalAiModelCatalog\.byId/);
  assert.doesNotMatch(plugin, /call\.getString\("(path|filePath|url|downloadUrl)"\)/, "JS'ten yol/URL alınmamalı");
});
