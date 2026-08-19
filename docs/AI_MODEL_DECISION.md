# AI Model ve Çalışma Zamanı Kararı

Bu belge, Hedefit'in "yerel-öncelikli" hedefine hangi teknik gerçeklikle
yaklaştığını ve neden bugün cihaz üstü bir LLM ÇALIŞTIRMADIĞIMIZI anlatır.

## 1. Platform gerçeği (karar buradan çıkıyor)

Hedefit tek bir kod tabanından üç ortamda çalışır:

| Ortam | Ne çalışır | Cihaz üstü LLM mümkün mü? |
|---|---|---|
| Cloudflare Worker | Next.js/RSC sunucu tarafı, API rotaları | Hayır — bu kullanıcının cihazı değil; "yerel" tanımına girmez. Worker'da 128 MB bellek ve CPU süresi sınırı var. |
| Tarayıcı (PWA) | İstemci bileşenleri | Teknik olarak WebGPU ile mümkün, ama 1–2 GB model indirmesi ve kalıcı depolama yönetimi gerekir |
| Capacitor WebView (Android/iOS) | Aynı istemci kodu | Ancak NATIVE bir köprü ile. Bugün projede böyle bir eklenti yok. |

`android/` ve `ios/` klasörleri Capacitor'ün ürettiği kabuklardır; içlerinde
çıkarım çalışma zamanı (llama.cpp, MLC, MediaPipe LLM, Core ML) yoktur.

## 2. Neden şimdi bir model indirmiyoruz

Küçük ama Türkçesi kullanılabilir bir model (Gemma 3 4B, Qwen 3 4B, Phi 4 mini
sınıfı) 4-bit quantize halde **≈2–3 GB**'dır. Bunun anlamı:

- Google Play'de uygulama boyutu değil ama **ilk açılışta 2–3 GB indirme**
- 3–4 GB RAM'li orta segment Android cihazlarda yükleme sırasında **OOM riski**
- Üretim hızı bu sınıf cihazlarda **~3–8 token/sn** → 140 kelimelik bir koç
  yanıtı **30–60 sn**. Ölçülen uzak model süresi (`kimi-k2.7-code-highspeed`)
  **~5 sn**.

Yani bugün cihaz üstü LLM'e geçmek, kullanıcı deneyimini **iyileştirmez,
belirgin biçimde kötüleştirir**. "Yerel-öncelikli" hedefi doğru; onu yanlış
zamanda yanlış araçla uygulamak ürünü bozar.

## 3. Bunun yerine ne yapıldı

Yerel katman **gerçekten** kuruldu, ama LLM ile değil:

**`lib/ai/providers/deterministic-local.ts`** — ağ gerektirmeyen, cihazda
çalışan, ücretsiz bir sağlayıcı. Deterministik motorun (`lib/ai/intelligence.ts`)
ürettiği gerçek sayıları, güvenli ve tıbben temkinli şablon cümlelerle
birleştirir. Bu bir yer tutucu değil: uzak sağlayıcı çöktüğünde kullanıcı boş
ekran yerine kendi verisine dayanan somut bir yanıt alır.

Kritik tasarım kararı: bu sağlayıcı serbest sohbette **son çare**dir
(`lastResortCategories`), tercih edilen yol değil. Aksi hâlde her kullanıcı her
soruda şablon cevap alırdı.

## 4. Cihaz üstü LLM eklendiğinde ne olacak

Mimari buna **hazır**. Gereken tek şey `LocalAiBridge` arayüzünü uygulayan bir
Capacitor eklentisi (`lib/ai/capability.ts`):

```ts
type LocalAiBridge = {
  isRuntimeAvailable(): Promise<boolean>;
  isModelInstalled(): Promise<boolean>;
  availableMemoryMb(): Promise<number>;
};
```

Köprü `globalThis.HedefitLocalAi` üzerinden göründüğünde:

1. `detectDeviceAiCapability()` otomatik olarak `LOCAL_READY` döner
2. Yeni sağlayıcı `providerRegistry.register(onDeviceLlmProvider)` ile eklenir
3. Router onu `kind: "local"` olduğu için **zincirin başına** alır

**Hedefit'in geri kalanında tek satır değişmez.** Rotalar, bileşenler ve
deterministik motor bu geçişten habersizdir.

### Köprü geldiğinde model seçim ölçütleri

Bugünden bir model adı sabitlemiyoruz — köprü yazıldığında piyasa değişmiş
olacak. Değerlendirme şu eksenlerde yapılmalı:

| Ölçüt | Neden |
|---|---|
| Türkçe kalitesi | Kullanıcı tabanının ana dili; İngilizce ağırlıklı küçük modeller Türkçede belirgin düşüş yaşar |
| Yapılandırılmış çıktı | Hafıza çıkarımı ve niyet sınıflandırma JSON şeması gerektirir |
| RAM (4-bit) | Hedef: ≤ 2 GB, `MIN_LOCAL_MEMORY_MB` eşiğiyle uyumlu |
| İndirme boyutu | Wi-Fi zorunlu, kullanıcı onaylı indirme |
| Gecikme | Orta segment Androidde ilk token < 2 sn, ≥ 15 token/sn |
| Çalışma zamanı uyumu | Seçilen köprünün (MediaPipe / llama.cpp / Core ML) desteklediği format |
| Lisans | Ticari dağıtıma açık olmalı (Hedefit Play Store'da ücretli plan sunar) |

Aday aileler: **Gemma**, **Qwen**, **Phi**. Karar, gerçek cihazlarda ölçüm
yapılmadan verilmemelidir.

## 5. Model indirme deneyimi (köprü sonrası)

`docs/AI_MIGRATION_REPORT.md` "Remaining Limitations" bölümünde de belirtildiği
gibi bu ekran **henüz yapılmadı**, çünkü indirilecek bir şey yok. Köprü
geldiğinde gereken davranış:

- Sessiz indirme YOK; açık kullanıcı onayı
- Wi-Fi zorunlu, boyut önceden gösterilir
- İlerleme, iptal, yeniden dene
- Depolama alanı kontrolü
- Modeli silme / yeniden indirme
- Model sürümü görünür

## 6. Uzak sağlayıcı seçimi

Uzak katman **sağlayıcıya bağlı değildir**: `lib/ai/providers/openai-compatible.ts`
OpenAI-uyumlu herhangi bir uç noktayla çalışır. Moonshot (Kimi) yalnızca
varsayılan; `AI_BASE_URL` + `AI_MODEL` ile OpenRouter, Together, Fireworks veya
kendi vLLM/Ollama sunucunuza tek ortam değişkeniyle geçilir.

Varsayılan `kimi-k2.7-code-highspeed` seçimi ölçüme dayanır (bkz. aynı dosyadaki
süre tablosu): akıl yürüten modeller bu uygulamanın zaman penceresine
yetişmiyordu.
