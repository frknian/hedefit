# Hedefit

Hedefit; antrenman, kardiyo/rota, beslenme, hedef ve görev takibini tek yerde
toplayan bir fitness uygulamasıdır. Bu depo backend servislerini, native Android
(Kotlin + Jetpack Compose) ve iOS (SwiftUI) istemcilerini içerir.

## Öne çıkanlar

- **Fit Koç**: bulut tabanlı AI ile sohbet, program üretimi ve besin çıkarımı
- **Antrenman**: hazır programlar, detaylı ve hızlı (dokun-bitir) set modu,
  takvim, spor salonu analitiği ve kas haritası
- **Kardiyo ve Rota**: tam ekran kardiyo, rota planlama, arka planda takip ve
  paylaşım kartları; koşu, yürüyüş, doğa yürüyüşü, trail, bisiklet ve kayak
- **Hedef yolculuğu**: kanıta dayalı tempo seçenekleri, su/protein/adım
  hedefleri ve kaynaklar
- **Görevler ve başarımlar**: XP, seri (streak) ve başlangıç rehberi görevleri
- **Sosyal**: karşılıklı arkadaşlık, haftalık XP lider tablosu, aktivite
  akışı ve arkadaşlarla ortak meydan okumalar
- **Diğer**: misafir modu, plan katmanları, ana ekran widget'ları, akıllı
  saat eşleştirme, Türkçe/İngilizce arayüz

## İçerik

- `app/api`: mobil istemcinin kullandığı HTTP API rotaları
- `worker`: Cloudflare Worker girişi ve Supabase proxy
- `lib`: API rotalarının kullandığı iş kuralları ve servisler
- `db`: Supabase şeması ve migrasyonlar
- `data`: egzersiz kataloğu (RepDB tabanlı, bkz. `data/RepDB_ATTRIBUTION.md`)
- `public/exercise-images`: mobil istemciye sunulan egzersiz görselleri
- `supabase/migrations`: güncel Supabase migrasyonları
- `scripts`, `tests`: içe aktarma/ML araçları ve testler
- `android`: Kotlin ve Jetpack Compose ile geliştirilen native Android uygulaması
- `ios`: SwiftUI iOS uygulaması ve widget'ları (bkz. `ios/README.md`)

## Egzersiz veritabanı

Egzersiz kataloğu [RepDB Free Exercise Dataset](https://github.com/RepDB/exercise-dataset)
kaynağını kullanır (`scripts/import-repdb.mjs`). RepDB'nin ücretsiz katmanı,
görünür bir atıf koşuluyla ticari kullanıma açıktır:

> Exercise data by [RepDB](https://repdb.co)

Tam lisans metni: `data/RepDB_LICENSE-DATA.md`. Eski free-exercise-db kataloğu
(`data/legacy-exercises.json`) yalnızca geçmiş antrenman kayıtlarının eski
egzersiz ID'lerini çözebilmesi için `lib/exercise-service.ts` içinde salt-okunur
bir yedek olarak tutulur; aktif katalogda veya aramada görünmez.

### Çekirdek hareket havuzu

Atlas (601 hareket) arama, elle kayıt ve geçmiş için olduğu gibi kalır. Program
üretimi ise `data/core-exercises.json` içindeki küratörlü havuzdan seçer: slot
başına (ör. "dikey çekiş") birbirinin yerine geçebilen 3–11 hareket, ilk ikisi
*staple*. Ana kaldırışlar (squat, press, çekiş…) ilerleme için bloklar arasında
sabit kalır; yardımcı, core ve kondisyon hareketleri kullanıcının seçtiği
dönemde (haftalık: her Pazartesi, aylık: her ayın 1'i; istek alanları
`rotationPeriod` ve `localDate`) kullanıcıya özel tohumla ve son 8 haftada
yapılan hareketlere göre döner (`lib/training/rotation.ts`,
`lib/training/exercise-selector.ts`). Hiçbiri AI çağrısı gerektirmez. Yanıttaki
`rotation` alanı bloğun başlangıç ve bitiş gününü verir; Android bu dönemi
ayarlardan seçtirir, blok geçince "Yeni blok hazır" kartı ve bildirim gösterir.

RepDB dosyası (`data/exercises.json`) import script'iyle yeniden üretildiği için
ek hareketler ayrı tutulur: `data/exercises-supplement.json`
(`scripts/build-exercise-supplement.mjs`), `lib/exercise-records.ts` ikisini
birleştirir. Yalnız direnç bandı ya da yalnız dambılı olan ev kullanıcısının tüm
vücudu çalışabilmesi için band sırt/biceps/triceps/omuz hareketleri ve birkaç
dambıl hareketi buradadır. Görseli olmayanlar `mediaStatus: "missing"` işaretlidir;
`public/exercise-images/<id>/start.webp` ve `peak.webp` eklenip script yeniden
çalıştırılınca "complete" olur.

Havuzu değiştirmek için `scripts/build-core-exercises.mjs` içindeki `SLOTS`
düzenlenir ve `node scripts/build-core-exercises.mjs` çalıştırılır; script
`data/core-exercises.json` ve okunabilir `data/core-exercises.md` dosyalarını
üretir, kapsam boşluklarını (az alternatif, evde yapılamayan slot…) raporlar.

## Canlı ortam

Backend Cloudflare Workers üzerinde yayınlanır:

- Production API: `https://hedefit.frknian.workers.dev`
- Fit Koç, plan türüne göre günlük kullanım kotası uygular. Görevlerden kazanılan
  XP, ücretsiz hesapların günlük soru hakkını artırır: 300 XP'de +1, 500 XP'de
  +2 ve sonrasında her 250 XP'de bir ek hak (en çok +5). Kötüye kullanım
  koruması olarak kullanıcı başına kısa süreli istek sınırı korunur.
- Fit Koç model yönlendirmesi: serbest sohbet, besin açıklaması ve kısa özetler
  `gpt-4o-mini` (`OPENAI_MODEL_LIGHT`); besin çıkarımı ve görsel okuma `gpt-4o`
  (`OPENAI_MODEL_CHEAP` / `OPENAI_MODEL_STANDARD`); kişisel program üretimi ve
  karmaşık haftalık değerlendirme `gpt-5.1` (`OPENAI_MODEL_ADVANCED`).
- Maliyet telemetrisi: her model çağrısı `ai_usage_events` tablosuna (kullanıcı,
  özellik, plan, model, token, tahmini maliyet; metin yok) yazılır. Özet görünümler:
  `ai_usage_daily_by_feature`, `ai_usage_monthly_by_user`. Fiyatlar
  `lib/ai/pricing.ts`'te; ham kayıtlar `purge_ai_usage_events(180)` ile temizlenir.
  Migration: `20261003130000_ai_usage_events.sql` (aylık tavan için
  `usage_month_total` da burada).
- Pahalı özelliklerde aylık tavan vardır (`lib/usage-limits.ts` `MONTHLY_LIMITS`):
  program üretimi Free 4 / Plus 20 / Premium 30, Premium fotoğraf 120.

Canlı dağıtım:

```bash
npm run deploy
```

## Google Play abonelikleri (Billing)

Plan yalnızca sunucuda, Google Play Developer API'den doğrulanarak yazılır
(`app/api/billing/verify`, `lib/billing/`); istemci plan yazamaz. Abonelik
değişiklikleri (yenileme, iptal, iade, ödeme sorunu) RTDN ile gelir:

1. Play Console → Monetization setup → *Real-time developer notifications*: bir Pub/Sub
   topic'i bağla ve `google-play-developer-notifications@system.gserviceaccount.com`
   hesabına topic üzerinde *Pub/Sub Publisher* yetkisi ver.
2. Topic'e **push** abonelik ekle: endpoint `https://<worker>/api/billing/rtdn`,
   *Enable authentication* açık, servis hesabı + audience = endpoint URL'si.
3. Worker secret'ları: `GOOGLE_PLAY_SERVICE_ACCOUNT` (Play API servis hesabı JSON'u),
   `GOOGLE_PLAY_RTDN_AUDIENCE` (adım 2'deki audience), `GOOGLE_PLAY_RTDN_SERVICE_ACCOUNT_EMAIL`
   (push aboneliğinin servis hesabı e-postası); isteğe bağlı `GOOGLE_PLAY_PACKAGE_NAME`.
4. Play Console'dan *Send test notification* ile doğrula (log: `rtdn test notification received`).

Günlük cron (`vite.config.ts` → `triggers.crons`, 03:17 UTC) RTDN kaçsa bile planları
Google ile uzlaştırır (`lib/billing/reconcile.ts`). Migration'lar: `20261003120000_play_billing.sql`,
`20261003140000_play_rtdn.sql`. Hesap silme, aktif abonelik varken kullanıcı onaylamadan 409 döner
(silmek Play aboneliğini iptal etmez).

## Ödüllü reklam (sunucu doğrulamalı)

Ücretsiz/misafir kullanıcı günlük koç sorusu hakkı bitince kısa bir ödüllü reklam izleyip +1 soru
kazanabilir (günde en fazla 3). Hak **yalnızca** AdMob'un imzalı SSV callback'iyle verilir; istemci
"izledim" diyerek hak alamaz (`app/api/ads/reward` yalnızca okur).

1. AdMob → Apps → Ad units → bir **Rewarded** birimi oluştur; birimin **Server-side verification**
   callback URL'sini `https://<worker>/api/ads/ssv` yap (AdMob'daki "Verify URL" ile dene).
2. Worker secret/değişken: `ADMOB_REWARDED_AD_UNIT_ID` (sunucu başka birimlerin callback'ini yok sayar).
3. Android derlemesi: `ADMOB_REWARDED_AD_UNIT_ID` (Gradle property, ortam değişkeni ya da `.env`); boşsa
   release'te "reklam izle" seçeneği gösterilmez. Debug derlemesi Google test birimini kullanır.
4. Migration: `20261003150000_ad_rewards.sql` (`ad_reward_events`, `grant_ad_reward`, `ad_bonus_today`).

Doğrulama: ECDSA P-256 imzası (`lib/ads/ssv.ts`), 24 saatten eski zaman damgası reddedilir, her
`transaction_id` bir kez işlenir, yalnız ücretsiz plan ve etkin hesap hak alır.

## Web sitesi (tanıtım)

`site/` klasörü, statik varlıklı bir Cloudflare Worker (`hedefit-site`, `scripts/wrangler.site.jsonc`) olarak yayınlanan tanıtım sitesidir; API Worker'ından ayrıdır.
Sayfalar: `index.html`, `planlar.html`, `gizlilik.html`, `destek.html`, `hesap-silme.html`.

```bash
npm run site:preview        # http://localhost:8788 (yerel önizleme)
npm run site:deploy         # https://hedefit-site.frknian.workers.dev
SITE_URL=https://alanadi.com npm run site:deploy   # özel alan adı bağlanınca
npm run site:build          # yalnız .site-dist/ üretir (SITE_URL gerekir)
```

- `site:build` çıktısı `.site-dist/` içindedir: `__SITE_URL__` yer tutucularını doldurur, `sitemap.xml` ve `robots.txt` üretir.
- Özel alan adı: alan adı Cloudflare'de olmalı; `SITE_URL=https://alanadi.com npm run site:deploy` Worker'ı o alan adına da bağlar ve canonical, sitemap ve paylaşım görselini günceller. API için `HEDEFIT_API_BASE_URL=https://api.alanadi.com npm run deploy`.
- İngilizce sürüm (`site/en/`) Türkçe sayfalardan **üretilir**: `python3 scripts/i18n/build-en.py` (çeviriler `scripts/i18n/en.json`; yeni/değişen Türkçe metin için `python3 scripts/i18n/extract.py` eksikleri listeler). Çıktı dosyalarını elle düzenleme.
- "Yayınlanınca haber ver" formu: site Worker'ı (`scripts/site-worker/index.js`, Workers KV `SUBSCRIBERS`). Liste: `node scripts/export-subscribers.mjs > aboneler.csv`.
- Mağaza bağlantıları: `GOOGLE_PLAY_URL` / `APP_STORE_URL` ortam değişkenleriyle (yalnız `https://`) `npm run site:deploy` çalıştır; butonlar otomatik aktifleşir. Alan adı ve mağaza formları için `android/STORE_LISTING.md`.
- `gizlilik.html` ve `destek.html` üretilir: `LegalTexts.kt` değişince `python3 scripts/build-legal-pages.py`.
- Plan limitleri (`planlar.html`) `lib/usage-limits.ts` ve Android `Entitlements.kt` ile birlikte güncellenmelidir.
- Wrangler komutları depo dışından çalıştırılır (kökteki `.wrangler/deploy` yönlendirmesi API Worker'ına aittir).

## Geliştirme

```bash
npm install
npm run dev
```

Doğrulama için `npm run build`, `npm run lint` ve `npm test` kullanılabilir.

## Android

Android Studio ile `android` klasörünü aç veya terminalden:

```bash
cd android
./gradlew :app:assembleDebug
```

Android istemcisinin ayrıntılı çalıştırma ve mimari notları için
`android/README.md` dosyasına bak.

## iOS

`ios/Config.xcconfig.example` dosyasını `ios/Config.xcconfig` olarak kopyalayıp
değerleri doldur, ardından `ios/Hedefit.xcodeproj` projesini Xcode ile aç.
Ayrıntılar için `ios/README.md`.

## Veritabanı migrasyonları

Yeni özellikler `supabase/migrations` altındaki migrasyonlara bağlıdır; örneğin
rota kayak aktivitesi için `20260925140000_route_activity_ski.sql`. Dağıtımdan
önce bekleyen migrasyonları uygula.

## Sosyal katman

Karşılıklı arkadaşlık (istek/onay, `profiles.username` üzerinden arama),
haftalık XP lider tablosu, arkadaşların antrenman/rota/başarım aktivitelerini
gösteren bir akış ve arkadaşlarla ortak haftalık meydan okumalar (XP,
antrenman sayısı veya mesafe hedefi). Tümü mevcut `xp_events` ve
`route_activities` üzerinde okuma-zamanlı `security definer` RPC'lerle
çalışır, yeni bir yazım/fan-out katmanı eklemez — meydan okumalar hariç, onlar
kendi `challenges`/`challenge_participants` tablolarını kullanır. Android'de
Profil → Arkadaşlar'dan erişilir. Gerekli migration'lar:
`20260929000000_friendships.sql`, `20260930000000_social_feed_challenges.sql`.

## Görevler, başarımlar ve beslenme hedefleri

Android uygulamasındaki Görevler ekranı; uyku, adım, antrenman, rota ve
beslenme kayıtlarından gün bazlı değişen görevler üretir. Günlük görev toplamı
100 XP, 24 başarımın her biri ise 100 XP'dir. Kalıcı XP ve Fit Koç ödülleri için
`supabase/migrations/20260828180000_tasks_rewards.sql` migration'ını uygula.

Beslenmede günlük kalori ve makro hedefleri, profil verisiyle deterministik
olarak hesaplanır; OpenAI yalnız girilen bir porsiyonun besin değerini tahmin
eder. Bu ayrım, genelleştirilmiş ve gereğinden yüksek protein hedeflerini
önler.

## Manuel spor kaydı

Android uygulamasında Antrenman sekmesindeki “Antrenman Ekle” ve ana ekrandaki
kısayol; yürüyüş öncelikli 17 spor türünü spora özel süre, mesafe, tempo, eğim
veya alt türle kaydeder. Aktif kalori 2024 Compendium MET değerleri ve profil
kilosundan, 1 MET dinlenme enerjisi çıkarılarak deterministik hesaplanır; sonuç mevcut RLS korumalı antrenman geçmişine yazılır ve İlerleme
ekranında kalıcı olarak gösterilir. Yeni kayıt sınırları için
`20260829020000_secure_manual_activity_limits.sql` migration'ını uygula.
