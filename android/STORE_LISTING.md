# Mağaza kaydı hazırlığı (Google Play / App Store)

Bu belge, alan adı alındıktan sonra mağaza formlarına girilecek bağlantıları ve metinleri toplar.
`ALANADI` yerine gerçek alan adını yaz. Metinler uygulamada gerçekten var olan özellikleri anlatır;
sağlık/tıbbi vaat içermez (AI önerileri tıbbi tavsiye değildir).

## 1. Alan adı kurulumu (sıra)

1. Alan adını Cloudflare'e ekle (DNS Cloudflare'de olmalı; Worker'a özel alan adı bağlamak için bu şart).
2. **Site:** `SITE_URL=https://ALANADI npm run site:deploy` — Worker `ALANADI`'a bağlanır; canonical,
   sitemap ve paylaşım görselleri bu adresle yeniden üretilir. (İsteğe bağlı: `www` için ikinci bir
   yönlendirme.)
3. **API:** `HEDEFIT_API_BASE_URL=https://api.ALANADI npm run deploy` — Worker `api.ALANADI`'a da bağlanır
   ve sağlık kontrolü bu adresten yapılır. `workers.dev` adresi çalışmaya devam eder (eski sürümler).
4. **Uygulama:** release derlemesine `HEDEFIT_API_BASE_URL=https://api.ALANADI` ver (Android: Gradle
   property / ortam değişkeni / `.env`; iOS: `Config.xcconfig`). Eski sürümler `workers.dev`'i kullanmaya
   devam edeceği için eski adresi hemen kapatma.
5. **Bildirim/callback adresleri:** Pub/Sub push (RTDN) ve AdMob SSV callback URL'lerini yeni API
   adresiyle güncelle (README: "Google Play abonelikleri", "Ödüllü reklam"). RTDN audience değeri de
   yeni adrese uymalı (`GOOGLE_PLAY_RTDN_AUDIENCE`).
6. **E-posta:** `destek@ALANADI` (Cloudflare Email Routing ile kişisel kutuya yönlendirilebilir) aç;
   `LegalTexts.kt` içindeki `LEGAL_CONTACT`'ı bununla değiştir ve `python3 scripts/build-legal-pages.py`
   çalıştır. Şu an kişisel Gmail adresi yazıyor.
7. Google Auth Platform'da yetkili alan adlarına `ALANADI`'ı ekle; gerekiyorsa Supabase Auth
   "Site URL / Redirect URLs" ayarını güncelle.

## 2. Mağaza formlarına girilecek URL'ler

| Alan | URL |
|---|---|
| Gizlilik politikası (Play + App Store) | `https://ALANADI/gizlilik` (EN: `https://ALANADI/en/privacy`) |
| Destek / pazarlama URL'si | `https://ALANADI/destek` · `https://ALANADI/` |
| Hesap silme (Play Data safety "Veri silme talebi URL'si") | `https://ALANADI/hesap-silme` (EN: `/en/delete-account`) |
| Abonelik şartları | Play'in standart şartları + `https://ALANADI/planlar` |

## 3. Yayından sonra: site butonlarını aç

```bash
GOOGLE_PLAY_URL="https://play.google.com/store/apps/details?id=com.hedefit.app" \
APP_STORE_URL="https://apps.apple.com/app/hedefit/id…" \
SITE_URL=https://ALANADI npm run site:deploy
```

Boş bırakılan mağazanın butonu pasif ("Yakında") kalır. Sitedeki "Yakında" / "Çok yakında" metinleri ve
"yayınlanınca haber ver" formu elle güncellenmeli (`site/index.html`, `site/planlar.html`; EN sayfalar
`python3 scripts/i18n/build-en.py` ile üretilir). Uygulamadaki paylaş/puanla bağlantıları Play adresini
paket adından kendisi üretir (`AppGrowth.kt`), ayrı ayar gerekmez.

## 4. Metinler

**Ad** (≤30): `Hedefit: Antrenman & Beslenme` · EN: `Hedefit: Workout & Nutrition`

**Kısa açıklama** (≤80)
- TR: `Antrenman, beslenme, GPS rota ve yapay zekâ koçu tek uygulamada.`
- EN: `Workouts, nutrition, GPS routes and an AI coach in one app.`

**Uzun açıklama (TR)**

> Hedefit; antrenman, kardiyo, beslenme ve hedef takibini tek yerde toplar.
>
> • Sana göre program: hedefini, ekipmanını ve seviyeni söyle; Hedefit doğrulanmış hareket atlasından
>   program kurar. Hızlı (dokun-bitir) ve detaylı set modu, takvim, kas haritası.
> • Fit Koç: yapay zekâ koçuna soru sor, program ve öğün önerileri al, hareket değişikliği iste.
> • Beslenme: yazarak ya da fotoğrafla öğün kaydı, kalori ve makro hedefleri, su ve protein takibi.
> • Kardiyo ve rota: koşu, yürüyüş, doğa yürüyüşü, bisiklet ve kayak için GPS rota kaydı, rota planlama
>   ve paylaşılabilir bitiş kartı.
> • Hedef yolculuğu: kanıta dayalı tempo seçenekleriyle tarihli yol haritası.
> • Görevler, seri ve XP; arkadaşlarla haftalık sıralama ve ortak meydan okumalar.
> • Health Connect ile adım, uyku, kilo ve kalori verisi; Wear OS saat uygulaması.
>
> Plus ve Premium abonelikleri daha yüksek günlük yapay zekâ haklarını ve reklamsız deneyimi açar.
> Abonelik Google Play üzerinden yönetilir ve istediğin an iptal edilir.
>
> Önemli: Hedefit tıbbi tavsiye vermez; yapay zekâ önerileri bilgilendirme amaçlıdır. Sağlık sorunun
> varsa bir uzmana danış.

**Long description (EN)**

> Hedefit brings workouts, cardio, nutrition and goal tracking into one app.
>
> • A plan built for you: tell it your goal, equipment and level and Hedefit builds a program from a
>   verified exercise atlas. Quick (tap-to-finish) and detailed set modes, calendar, muscle map.
> • Fit Coach: ask the AI coach questions, get program and meal suggestions, request exercise swaps.
> • Nutrition: log meals by typing or with a photo, calorie and macro targets, water and protein tracking.
> • Cardio and routes: GPS route recording for running, walking, hiking, cycling and skiing, route
>   planning and shareable finish cards.
> • Goal journey: a dated roadmap with evidence-based pace options.
> • Quests, streaks and XP; weekly leaderboard and shared challenges with friends.
> • Steps, sleep, weight and calories via Health Connect; Wear OS companion app.
>
> Plus and Premium subscriptions unlock higher daily AI allowances and an ad-free experience.
> Subscriptions are managed through Google Play and can be cancelled at any time.
>
> Important: Hedefit does not give medical advice; AI suggestions are informational. If you have a
> health concern, consult a professional.

## 5. Form notları

- **Kategori:** Sağlık ve Fitness. **İçerik derecelendirmesi:** kullanıcı etkileşimi (arkadaşlık) ve
  kullanıcı adları var; 16 yaş altına yönelik değil (gizlilik metni: 16 altı için tasarlanmadı).
- **Reklamlar:** "Reklam içerir" = evet (AdMob; ödüllü ve geçiş reklamları).
- **Uygulama içi satın alma:** abonelik (Plus ₺99/ay veya ₺849/yıl, 7 gün deneme; Premium ₺169/ay veya
  ₺1.449/yıl). Mağaza fiyatı Play Console'daki fiyattır.
- **Veri güvenliği / App Privacy:** `legal/gizlilik-inceleme-2026-10-01.md` bölüm C ve F. Konum (yalnız
  rota kaydı, ön plan servisi; arka plan konum izni YOK), sağlık ve fitness, fotoğraflar, kullanıcı
  kimlikleri, satın alma geçmişi, uygulama etkinliği (AI kullanım metadatası), reklam kimliği.
- **Önplan servisi türleri:** `health` ve `location` (bkz. `RELEASE.md`).
- **Ekran görüntüleri:** `brand/screens/` (TR), `brand/screens-en/` (EN), `brand/screens-premium/`;
  uygulama ikonu `brand/hedefit-app-icon.png`; Wear OS için en az 1:1, 384×384.
- **Sağlık uygulaması beyanı / Health Connect formu:** okunan veri türleri + `WRITE_EXERCISE` gerekçesi.
