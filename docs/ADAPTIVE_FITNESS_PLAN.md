# Hedefit — Adaptive Fitness planı (Faz 0: analiz ve teknik plan)

Durum: Faz 0 tamamlandı, kod değişikliği yok. Faz 1'e geçmeden önce "Karar bekleyenler" bölümü onaylanmalı.

## 1. Mevcut mimari (ne var, nereye oturuyor)

| Alan | Bugün | Yeni özellik için anlamı |
|---|---|---|
| Stack | Cloudflare Worker + vinext/Next (API rotaları `app/api`, iş kuralları `lib`), Supabase (Postgres + Auth + Storage), Vercel AI SDK + OpenAI. Android: Kotlin/Compose. iOS: SwiftUI (ayrı). Web: statik `site/` (ayrı `hedefit-site` Worker). | Yeni bağımlılık gerekmez. |
| Mobil (Android) | Tek `MainActivity`, `AppDestination` sekmeleri + `UtilityPage` ekranları, `MainViewModel`, `HedefitRepository` (HTTP + Supabase). | Yeni ekranlar `UtilityPage`/sheet olarak eklenir. |
| Web | `site/*.html` + vanilla JS; EN sayfalar `scripts/i18n/build-en.py` ile TR'den üretilir (elle düzenlenmez). | Önce TR, sonra `en.json` + üretim. |
| Veritabanı | `supabase/migrations` (güncel) + `db/` (eski). `profiles` (gender serbest metin, `history_answers` jsonb), `workout_*`, `nutrition_goals`, `food_entries`, `ai_memories`, `ai_usage_events`… RLS açık, `on delete cascade` ile `auth.users`'a bağlı. | Yeni tablolar aynı kalıpla (RLS + cascade). |
| Kimlik | Supabase Auth, misafir modu, `lib/api-auth.ts`; hesap silme `app/api/account/delete` (auth kullanıcısını siler, FK cascade ile veri gider). | Döngü verisi cascade'e bağlanırsa silme otomatik çalışır. |
| Antrenman motoru | `lib/training/*` (3164 satır): profil normalizasyonu, split, seçici, rotasyon, doğrulayıcı, onarım. **Zaten var:** `readiness-adapter.ts` (enerji/uyku/yorgunluk/ağrı → normal / azaltılmış / aktif toparlanma) ve `plan-adapter.ts` (süre kısaltma, ekipman, hastalık, yorgunluk), `POST /api/workout/adapt`. | Adaptive Engine sıfırdan değil, bu iki modül genişletilir. |
| Check-in | Android `ReadinessCheckinSheet` → `/api/workout/adapt` (action `readiness_checkin`). **Kalıcı değil:** veri tabloya yazılmıyor, geçmiş yok. | Faz 5'te kalıcı `daily_checkins` gerekir. |
| Egzersiz | 601 RepDB (`exercises.json`) + 20 tamamlayıcı (`exercises-supplement.json`) + 873 eski (salt-okunur). `category`: strength 462, stretching 105, cardio 16, olympic 14, plyometrics 4. Pilates / barre / mobility etiketi yok; "stretching" 105 hareket mobility için ham madde. `goalCompatibility` içinde `mobility` (100) var. Görseller `public/exercise-images`. | Yeni boyut: `modality` (pilates, mobility, barre, low_impact, recovery). `category`'ye dokunulmaz. |
| Beslenme | `lib/nutrition-*.ts` (hedefler formülle), `app/api/nutrition/*` (metin, foto, barkod, öneri), Android `NutritionScreen`. | Antrenman yükü ve toparlanma girdi olarak bağlanır. |
| AI Koç | `lib/ai/*`: router, prompts, `safety.ts` (deterministik tıbbi sınır katmanı), `memory.ts`, `context-builder.ts`, `signals.ts`. | `safety.ts` döngü/tıbbi iddia kuralları için hazır zemin. |
| Onboarding | Android `ProfileQuestionnaireScreen`; cevaplar `history_answers` içinde **23 sabit slot** (`lib/onboarding-questions.ts`). Slot 15–22 geçen oturumda eklendi (odak, sağlık, alerji, beslenme, alışkanlık, stres, memnuniyet, performans). | Cinsiyet `profiles.gender` serbest metin; döngü sorusu buna bağlanacak. |
| Yerelleştirme | Android: `tr("…","…")` ve `if (en)`; sunucu: `lib/i18n/dictionaries/{tr,en}.ts`; egzersiz adı/talimat: `lib/exercise-names-tr.ts`, `exercise-translations.ts`. | Her faz iki dili birlikte teslim eder. |
| Analitik | **Yok.** Android'de analytics SDK'sı yok; yalnızca sunucu tarafı `ai_usage_events`. | Faz 14 sıfırdan; bkz. karar 3. |
| Gizlilik | Gizlilik metni `LegalTexts.kt`; `/api/ai/memory` silme. | Bkz. "Bulgu 1". |
| Test | Node: `tests/*.test.mjs` (45 dosya). Android: `src/test` birim testleri (`androidTest` yok). CI yok. Lint: eslint (`npm run lint`), ayrı typecheck betiği yok. | Faz sonunda: `node --test`, `lint`, `tsc --noEmit`, Gradle build + birim. |

## 2. Gerekli değişiklikler

```
Mevcut mimari
  ↓
Gerekli değişiklikler
  ├─ egzersiz boyutu: modality + impact + asset lisansı
  ├─ döngü verisi (opsiyonel) + kişiselleştirme ayarları
  ├─ kalıcı günlük check-in
  └─ Adaptive Engine: readiness-adapter + plan-adapter genişlemesi
  ↓
Yeni bileşenler
  lib/exercise-modality.ts · lib/cycle.ts (faz/tahmin, saf fonksiyonlar)
  lib/training/adaptive-engine.ts (girdileri birleştirir, mevcut adaptörleri çağırır)
  app/api/checkin, app/api/cycle (CRUD + silme)
  ↓
Yeni veri modelleri (supabase/migrations)
  cycle_profiles(user_id pk → auth.users cascade, tracking_enabled default false,
                 last_period_start, cycle_length_days, period_length_days, regularity)
     • faz ve tahmini sonraki adet SAKLANMAZ, tarihten hesaplanır
  daily_checkins(user_id, day, energy, sleep_quality|hours, soreness, pain,
                 available_minutes, unique(user_id, day))
     • döngü alanı yok; döngü yalnızca cycle_profiles'tan okunur
  personalization_settings(user_id pk, adaptive_enabled, cycle_personalization_enabled,
                           ai_health_context_enabled default false)
  Egzersiz JSON: modality[], impact, asset{source, license, attribution, commercial_use}
  Beslenme tercihleri: history slot 16–18'den türetilir (ikinci kaynak açılmaz)
  ↓
Yeni ekranlar (Android)
  Döngü takibi (onboarding adımı + Ayarlar'da aç/kapat/düzenle/sil)
  Günlük check-in (≤5 dokunuş) · "Bugünün planı" kartı + adaptasyon özeti
  Pilates / Mobility / Barre / Low Impact / Recovery keşif satırları
  ↓
Yeni servisler
  /api/checkin · /api/cycle · /api/workout/adapt (girdi genişler) · koç bağlamı
  ↓
Migration gereksinimi
  Yalnızca ekleme (yeni tablolar + yeni JSON alanları). Mevcut kullanıcı verisine dokunmaz.
  ↓
Test stratejisi
  Saf fonksiyon birim testleri (faz/tahmin, adaptasyon karar tablosu),
  RLS ve silme testi, onboarding TR/EN × kadın/erkek akışı, mevcut kullanıcı regresyonu.
```

## 3. Faz sırası (öncelik değişmez)

P0: Faz 0 → 1 (veri/mimari) → 2 (onboarding)
P1: Faz 3 (egzersiz) → 4 (Pilates/kadın odaklı deneyim) → 5 (check-in) → 6 (Adaptive Engine)
P2: Faz 7 (AI Koç) → 8 (beslenme) → 9 (TR/EN)
P3: Faz 10–14 (web, demo, web TR/EN, gizlilik, analitik)
P4: Faz 15–17 (QA, UI cilası, üretim kontrolü)

## 4. Bulgular (kod yazmadan önce bilinmesi gerekenler)

1. **Sağlık cevapları AI'ya gidiyor.** Geçen oturumda eklediğim sağlık durumu, alerji, alışkanlık ve stres cevapları `history_answers` içinde. `lib/ai/signals.ts` bu diziyi (`assessmentAnswers`) ve Android `limitations` alanı tüm diziyi AI isteklerine ekliyor. Plan şu: sağlık/alerji/alışkanlık/stres/döngü slotları varsayılan olarak AI payload'ından çıkar, yalnızca `ai_health_context_enabled` açıkken gönderilir. Bu düzeltme Faz 1'in ilk işi olmalı.
2. **Check-in kalıcı değil.** Mevcut adaptör çalışıyor ama geçmiş, trend ve "kullanıcı her gün kısa form" akışı yok.
3. **Kategori çakışması.** Mevcut doğrulayıcılar ve seçici `category` değerine bağlı. Pilates/Barre'yi `category` yapmak onları bozar; ayrı `modality` alanı güvenli yol.
4. **Barre, Pilates ve gerçek Mobility görseli katalogda yok.** RepDB ve Free Exercise DB'de Pilates/Barre içeriği neredeyse yok. "Rastgele internetten içerik alma" kuralı nedeniyle bu içerik üretilmeli (bkz. karar 2).
5. **Cinsiyet serbest metin.** `profiles.gender` `"Erkek"` gibi serbest dize. Döngü sorusunu "cinsiyet == kadın" ile kilitlemek hem kırılgan hem ikili varsayım. Uygulanan karar: onboarding'de cinsiyet "Kadın" ise soru gösterilir; "Erkek", "Diğer" ve belirtmeyenlere sorulmaz. Ayarlar → "Döngü takibi (isteğe bağlı)" girişi yalnızca "Erkek" olarak belirtenlere gösterilmez (kadın, diğer ve belirtmeyen herkes erişebilir).
6. **iOS ayrı kod tabanı.** 23 slotluk şema ve yeni özellikler iOS'ta yok.

## 5. Karar bekleyenler

1. **iOS kapsamı:** Android + sunucu + web mi, iOS de mi? (Öneri: iOS bu plana dahil değil.)
2. **Pilates / Mobility / Barre görselleri:** Lisansı net, Hedefit'e özel içerik kim üretecek? Seçenekler: (a) ticari kullanımı açıkça veren bir AI görsel aracıyla üretim, (b) illüstratör, (c) kendi çekimin. Karar gelene kadar bu hareketler görselsiz **yayınlanmaz**.
3. **Analitik:** Şu an hiç yok. Seçenekler: (a) yalnızca sunucuda izin listeli, yüklemesiz olay sayacı (yeni tablo, yeni SDK yok), (b) hiç eklememek. (Öneri: a.)
4. **Production migration:** Migration dosyalarını yazıp inceleme yaparım; production Supabase'e uygulamak için onayın gerekir.
5. **Faz 1'in ilk işi:** Bulgu 1'deki AI payload düzeltmesini ilk iş olarak yapmamı onaylıyor musun?


## 6. Plan ayrımı (Free / Plus / Premium)

Kaynak: sunucuda `lib/entitlements.ts`, Android'de `TIER_LIMITS` (`ui/state/Entitlements.kt`). İkisi aynı matrisi taşır, ikisi de testlidir
(`tests/entitlements-adaptive.test.mjs`, `AdaptiveTierTest.kt`).

| Özellik | Free | Plus | Premium |
|---|---|---|---|
| Günlük check-in | ✓ | ✓ | ✓ |
| Check-in geçmişi | 14 gün (Misafir 7) | 90 gün | Sınırsız + trend |
| Adaptasyon eylemleri | Kısalt, yoğunluğu azalt | + hareket değiştir, mobilite ekle, toparlanmaya geç | + Pilates oturumuna geç |
| Pilates / Mobility / Low Impact / Recovery | Başlangıç seti (beginner, short, recovery, morning, evening) | Tümü | Tümü |
| Barre | – | ✓ | ✓ |
| Döngü takibi (kayıt, düzenleme, silme) | ✓ | ✓ | ✓ |
| Döngü bilgisinin adaptasyona katılması | – | ✓ | ✓ |
| Koç: adaptasyon açıklaması | Şablon | Şablon | Kişiye özel (AI) |
| Koç: döngü farkındalığı | – | – | ✓ |
| Beslenme kişiselleştirme | Temel | Antrenman yüküne göre | + toparlanma + (isteğe bağlı) döngü wellness |

İlke: sağlık verisini yönetme hakkı (takip, düzenleme, silme) hiçbir katmanda paywall'ın arkasında değildir. Ücretli katman yalnızca bu
bilginin adaptasyona katılmasını ve açıklamanın derinliğini ayırır. Modalitesiz klasik hareketler bu kurala tabi değildir.

Uygulama durumu: matris ve testler Faz 2'de kuruldu. Uygulama noktaları: egzersiz kilidi (Faz 3), check-in geçmişi (Faz 5), eylemler (Faz 6),
koç (Faz 7), beslenme (Faz 8), plan sayfaları ve mağaza metni (Faz 10, 12), uygulama içi plan karşılaştırması (Faz 16).
