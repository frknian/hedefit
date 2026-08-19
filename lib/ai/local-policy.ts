// Yerel AI politikası: NE yerelde çalışır, hangi bütçeyle, hangi sürelerle.
//
// Tek dosyada toplanmasının sebebi, bu değerlerin ÖLÇÜMLE değişecek olması
// (bkz. docs/LOCAL_AI_BENCHMARK.md). Karşılaştırma sonuçları geldiğinde
// yalnız burası güncellenir; sağlayıcı, yönlendirici ve rotalar değişmez.

import type { AiTaskCategory } from "./types.ts";

/**
 * Yerelde çalışmasına izin verilen kategoriler.
 *
 * Kısa, tek dönüşlü, deterministik gerçeklere dayanan koçluk yanıtları
 * kapsamdadır. Karmaşık plan üretimi (complex_reasoning), görsel akışlar
 * (vision) ve şemaya bağlı üretim (structured_extraction) bilerek DIŞARIDA —
 * küçük bir modelin bunları yeterli kalitede yaptığı ÖLÇÜLMEDEN yerele vermek
 * ürünü bozardı. Yerel bir hata olursa zincir zaten uzak sağlayıcıya düşer.
 *
 * `LOCAL_AI_CATEGORIES` ortam değişkeniyle (virgülle ayrılmış) daraltılıp
 * genişletilebilir; böylece ölçüm sonucu yeni sürüm beklemeden uygulanabilir.
 */
const DEFAULT_LOCAL_CATEGORIES: AiTaskCategory[] = [
  // "conversation" LİSTEDE OLMAK ZORUNDA: koç sohbeti rotası (app/api/chat)
  // isteği bu kategoriyle gönderir. Listeye alınmazsa cihaz üstü model
  // uygulamanın ASIL özelliğinde hiç devreye girmez — yerel model kurulu olsa
  // bile her mesaj uzak sağlayıcıya giderdi.
  "conversation",
  "simple_coaching",
  "daily_summary",
  "activity_summary",
  "goal_progress",
  "motivation",
  "nutrition_explanation",
];

const ALL_CATEGORIES: AiTaskCategory[] = [
  "simple_coaching", "daily_summary", "nutrition_explanation", "activity_summary",
  "goal_progress", "motivation", "conversation", "complex_reasoning",
  "structured_extraction", "vision",
];

export function localCapableCategories(): AiTaskCategory[] {
  const configured = process.env.LOCAL_AI_CATEGORIES;
  if (!configured) return DEFAULT_LOCAL_CATEGORIES;
  const wanted = configured.split(",").map((item) => item.trim()).filter(Boolean);
  // Bilinmeyen bir kategori adı sessizce yok sayılır; yazım hatası yüzünden
  // her isteğin yerele gitmesi ya da hiç gitmemesi istenmez.
  return ALL_CATEGORIES.filter((category) => wanted.includes(category));
}

/**
 * Yerel istem karakter bütçesi.
 *
 * Neden karakter, token değil? Token sayısı modele göre değişir ve JS tarafında
 * doğru saymak için tokenizer taşımak gerekirdi. Türkçe metinde kabaca
 * 1 token ≈ 3 karakter; 6.000 karakter ≈ 2.000 token, seçilen modellerin
 * `maxNumTokens = 2048` penceresine giriş+çıkış olarak sığar.
 */
export const LOCAL_PROMPT_CHAR_BUDGET = 6_000;

/** Koçluk yanıtı kısadır; uzun üretim mobilde doğrudan bekleme süresidir. */
export const LOCAL_MAX_OUTPUT_TOKENS = 320;

export const LOCAL_TEMPERATURE = 0.3;

/**
 * Üretim zaman aşımı. Aşılırsa native taraf üretimi durdurur ve yönlendirici
 * uzak sağlayıcıya BİR KEZ düşer — yerel yeniden denenmez (döngü olmaz).
 */
export const LOCAL_GENERATION_TIMEOUT_MS = 45_000;

/** Model yükleme zaman aşımı; ilk yükleme büyük modellerde on saniyeleri bulur. */
export const LOCAL_LOAD_TIMEOUT_MS = 120_000;
