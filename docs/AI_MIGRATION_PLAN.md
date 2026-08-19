# Hedefit AI Migration Plan

Bu belge, Hedefit'in yapay zekâ katmanının tek sağlayıcıya (Moonshot/Kimi)
bağımlı halden; yerel-öncelikli, sağlayıcıdan bağımsız, kişiselleştirilmiş bir
koçluk platformuna taşınmasını anlatır.

## 1. Mevcut mimari (göç öncesi)

```
Kullanıcı
   ↓
AiCoachChat.tsx  (istemci `context` dizesini kendisi üretir)
   ↓
app/api/chat/route.ts        (auth + rate limit + kullanım hakkı)
   ↓
lib/ai-provider.ts           (createOpenAICompatible → tek uç nokta)
   ↓
https://api.moonshot.ai/v1   (Kimi)
   ↓
yanıt  ·  hata halinde lib/ai-coach.ts localCoachReply() (anahtar kelime tabanlı)
```

Aynı zincir `generate-plan`, `weekly-review`, `goal-plan`, `nutrition/advice`
ve `nutrition/estimate` rotalarında da tekrarlanır.

Göç öncesi durumun doğru tespiti önemli: `lib/ai-provider.ts` **zaten** bir
soyutlama katmanıydı. Sağlayıcı/model, `AI_BASE_URL` + `AI_MODEL` ortam
değişkenleriyle değiştirilebiliyordu ve API anahtarı hiçbir zaman istemciye
sızmıyordu. Bu göç sıfırdan bir soyutlama kurmuyor; var olanı **çok
sağlayıcılı, yönlendirmeli ve yedeklemeli** hale getiriyor.

## 2. Problemler

| # | Problem | Etki |
|---|---|---|
| P1 | Tek uç nokta: `AI_API_KEY` yoksa **her** AI özelliği anahtar kelime yedeğine düşer; ikinci bir sağlayıcıya geçiş yok | Erişilebilirlik |
| P2 | Sağlayıcı hatasında (429/500/timeout) alternatif sağlayıcı denenmez | Dayanıklılık |
| P3 | Koç bağlamı **istemcide** üretilip düz metin olarak gönderilir; sunucu doğrulayamaz | Doğruluk / güven |
| P4 | Deterministik değerler (BMI, TDEE, kalori açığı, kilo eğilimi) LLM'in metin bağlamından okumasına bırakılmış | Halüsinasyon |
| P5 | Kalıcı kullanıcı hafızası yok; her sohbet sıfırdan başlar | Kişiselleştirme |
| P6 | Bağlam bütçesi yok: son 12 mesaj + 8.000 karakter bağlam her istekte gider | Maliyet |
| P7 | Güvenlik kuralları yalnızca sistem promptunun içinde — model uyarsa çalışır | Güvenlik |
| P8 | Sağlayıcı/model/gecikme ölçülmüyor, kullanıcı geri bildirimi toplanmıyor | Gözlemlenebilirlik |
| P9 | Cihaz üstü (yerel) çıkarım yok; her istek ücretli uzak sağlayıcıya gider | Maliyet / gizlilik |

## 3. Hedef mimari

```
                     HEDEFIT ROTALARI
                            │
                            ▼
                    lib/ai/coach.ts  (AI Coach Service)
                            │
            ┌───────────────┴───────────────┐
            ▼                               ▼
   lib/ai/intelligence.ts            lib/ai/memory.ts
   (deterministik motor)             (yapılandırılmış hafıza)
            │                               │
            └───────────────┬───────────────┘
                            ▼
                  lib/ai/context-builder.ts
                  (UserCoachContext + bütçe)
                            │
                            ▼
                    lib/ai/safety.ts
                            │
                            ▼
                    lib/ai/router.ts
                            │
        ┌───────────────────┼───────────────────┐
        ▼                   ▼                   ▼
  deterministic-local   openai-compatible   (gelecek sağlayıcılar)
  (cihazda, ağsız)      (Kimi / OpenRouter    registry.register() ile
                         / vLLM / Ollama)     tek satırda eklenir
```

**Temel ilke:** LLM fitness hesaplarının kaynağı DEĞİLDİR. Deterministik motor
sayıyı üretir, LLM yalnızca anlatır.

## 4. Göç aşamaları

| Aşama | İçerik | Durum |
|---|---|---|
| 1 | `AIProvider` arayüzü, registry, OpenAI-uyumlu sağlayıcı; `lib/ai-provider.ts` uyumluluk katmanına dönüşür | ✅ |
| 2 | Hedefit Intelligence Engine — deterministik hesaplar | ✅ |
| 3 | `UserCoachContext` + bağlam bütçesi | ✅ |
| 4 | Yapılandırılmış hafıza + çıkarım + tekilleştirme + RLS | ✅ |
| 5 | Yerel sağlayıcı + cihaz yeteneği algılama | ✅ |
| 6 | Model Router + yedekleme zinciri | ✅ |
| 7 | Geri bildirim + telemetri | ✅ |
| 8 | Güvenlik denetimi (anahtar, RLS, izolasyon, prompt injection) | ✅ |
| 9 | Regresyon | ✅ |

Her aşama kendi testleriyle birlikte gelir; `tests/ai-*.test.mjs`.

## 5. Geriye dönük uyumluluk (tamamlandı, katman kaldırıldı)

İlk dalgada `lib/ai-provider.ts` bir uyumluluk katmanına dönüştürüldü:
`generateAiText` / `generateAiObject` imzaları korundu, altlarında router
çalıştı. Böylece göç, her rotayı aynı anda yeniden yazmayı gerektirmedi.

İkinci dalgada dört rota da Coach Service boru hattına taşındı ve katman
**tamamen silindi**. İçindeki iki genel yardımcı
(`hasRemoteProvider`, `parseImageDataUrl`) sağlayıcı katmanına taşındı.

Bugün AI'ya erişimin üç meşru yolu var:

| Kullanım | Giriş noktası |
|---|---|
| Koç sohbeti | `lib/ai/coach.ts` → `generateCoachResponse` |
| Şemaya bağlı görev (değerlendirme, analiz, plan) | `lib/ai/coach.ts` → `generateCoachObject` |
| Kişiselleştirme gerektirmeyen üretim (besin tahmini) | `lib/ai/router.ts` → `routeObject` / `routeText` |
