# Android yayın rehberi (telefon + Wear OS)

İki modül **aynı paket adıyla** (`com.hedefit.app`) tek Play listelemesine yüklenir:

| Modül | Hedef | Sürüm kodu | Çıktı |
|---|---|---|---|
| `:app` | Telefon | `22` | `app/build/outputs/bundle/release/app-release.aab` |
| `:wear` | Wear OS 3+ (API 30+) | `1_000_022` | `wear/build/outputs/bundle/release/wear-release.aab` |

Her sürümde iki modülün `versionName`'ini birlikte güncelle. Saat modülünün kodu telefonunkinin
1.000.000 üstünde kalır (Play, aynı paketin form faktörleri için ayrı kod ister); telefon kodunu
artırınca saatinkini de `1_000_000 + telefon kodu` yap.

## 1. İmza

Upload key'i ortam değişkeni veya `~/.gradle/gradle.properties` ile ver (git'e girmez):

```bash
export HEDEFIT_UPLOAD_STORE_FILE=/guvenli/yol/hedefit-upload.jks
export HEDEFIT_UPLOAD_STORE_PASSWORD=...
export HEDEFIT_UPLOAD_KEY_ALIAS=...
export HEDEFIT_UPLOAD_KEY_PASSWORD=...
```

Değişkenler yoksa AAB **imzasız** üretilir; Play'e yüklenemez. Data Layer'ın çalışması için
telefon ve saat aynı anahtarla imzalanmalıdır (Play App Signing bunu sağlar).

## 2. Derleme

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
cd android
./gradlew :app:bundleRelease :wear:bundleRelease
```

## 3. Play Console

1. Tek uygulama (`com.hedefit.app`) altında **Üretim/Dahili test** sürümüne iki AAB'yi birlikte yükle.
2. *Form faktörleri → Wear OS*'u aç ve saat için en az bir ekran görüntüsü (1:1, en az 384×384) ve ikon ekle.
3. **Uygulama içeriği** formları:
   - *Önplan hizmeti türleri*: `health` ve `location` (antrenman kaydı; kullanıcıya görünür ongoing bildirimi var).
   - *Sağlık uygulamaları*: nabız, kalori ve mesafe saatte yalnızca ekranda gösterilir, cihaz dışına gönderilmez.
   - *Konum*: yalnızca dış mekân antrenmanı sırasında kullanılır.
4. Gizlilik politikası ve Veri güvenliği formu telefon sürümüyle aynıdır; saat ek veri toplamaz.

## 4. Yayın öncesi kontrol listesi

- [ ] Gerçek Wear OS 4+ saat (ve eşlenmiş telefon) üzerinde dahili test
- [ ] Telefondan saate özet geliyor (adım, su, seri, protein)
- [ ] Saatten su ekleme telefona ve sunucuya yazılıyor; uçak modunda ekleme sonradan senkronlanıyor
- [ ] Koşu/yürüyüş/bisiklet: nabız, mesafe ve süre doğru; ekran kapalıyken ölçüm sürüyor
- [ ] Ağırlık antrenmanı: set sayacı ve dinlenme titreşimi
- [ ] Biten antrenman telefonda aktivite olarak görünüyor (uygulama kapalıyken bitirip sonra açarak da dene); aynı antrenman iki kez kaydolmuyor
- [ ] Tile ve üç complication (Adım, Su, Seri) kadrana eklenebiliyor
- [ ] İzin reddinde uygulama çökmüyor (kod 2026-10-03'te düzeltildi: izin yoksa önplan servisi hiç başlatılmaz ve toast gösterilir; Wear OS 6'da nabız `health.READ_HEART_RATE` ister — gerçek saatte hem Wear OS 4/5 hem 6 ile dene)
- [ ] `./gradlew :wear:lintRelease` temiz

## Bilinen sınırlar

- Saatte biten antrenman telefona gönderilir ve elle eklenen aktivite olarak hesaba yazılır; kalori telefonda kilodan tahmin edilir (saatin ölçtüğü değer kullanılmaz). Health Connect'e yazılmaz.
- Ağırlık antrenmanında kg/tekrar saatten girilmiyor; yalnızca set sayacı ve dinlenme var.
- `:app:lintVitalRelease`, `:app:lintRelease` (0 hata, 98 uyarı) ve `:wear:lintRelease` 2026-10-03'te temiz çalıştı; yayın öncesi yeniden çalıştır.

## 5. Faturalandırma ve mağaza öncesi kontrol listesi (telefon)

Kod tarafı hazır (Play Billing, sunucu doğrulama, RTDN, uzlaştırma); aşağıdakiler Play Console ve cihaz gerektirir:

- [ ] Upload key üret ve sakla (yoksa AAB imzasız): `keytool -genkeypair -v -keystore hedefit-upload.jks -alias hedefit -keyalg RSA -keysize 2048 -validity 10000` — dosyayı ve parolaları git dışında, parola yöneticisinde tut; Play App Signing'i aç.
- [ ] `versionCode`/`versionName`'i artır (`app` 22 ve `wear` 1_000_022 şu an son yüklenenle aynı olabilir); iki modülün `versionName`'i aynı kalsın.
- [ ] Play Console'da `hedefit_plus` / `hedefit_premium` abonelikleri (monthly + yearly base plan; Plus'a 7 gün deneme) — README'deki "Google Play abonelikleri" bölümüne bak.
- [ ] Dahili test kanalına imzalı AAB yükle; lisans test hesabıyla: satın alma, deneme, iptal, iade, Plus→Premium geçişi; hesap silerken abonelik uyarısı.
- [ ] Release derlemesinde `SUPABASE_URL`/`SUPABASE_ANON_KEY`/`GOOGLE_WEB_CLIENT_ID` dolu mu (kökteki `.env` veya CI ortamı; `BuildConfig` 2026-10-03'te dolu doğrulandı).
- [ ] Google Auth Platform'a release SHA-1/SHA-256 (Play App Signing sertifikası dahil) ekle.
- [ ] Data safety / App privacy formları: satın alma geçmişi, AI kullanım metadatası, konum, sağlık, AD_ID (bkz. `legal/gizlilik-inceleme-2026-10-01.md` bölüm F).
- [ ] Health Connect bildirim formu: manifest `READ_*` izinlerinin yanında `WRITE_EXERCISE` de istiyor; kullanımı gerekçelendir ya da kullanılmıyorsa kaldır.
- [ ] R8/küçültme kapalı (`isMinifyEnabled = false`); açılırsa Billing, Health Connect ve Wear Data Layer ile tam regresyon testi şart.
- [ ] Alan adı + gizlilik/destek/hesap-silme URL'leri kalıcı alan adında (şu an `workers.dev`) — adım adım kurulum ve form metinleri: `STORE_LISTING.md`.
