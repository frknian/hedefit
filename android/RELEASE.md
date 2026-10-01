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
- [ ] Tile ve üç complication (Adım, Su, Seri) kadrana eklenebiliyor
- [ ] İzin reddinde uygulama çökmüyor
- [ ] `./gradlew :wear:lintRelease` temiz

## Bilinen sınırlar

- Saatte biten antrenman henüz telefona/Health Connect'e yazılmıyor (yalnızca saatte ölçülür ve gösterilir).
- Ağırlık antrenmanında kg/tekrar saatten girilmiyor; yalnızca set sayacı ve dinlenme var.
- Telefon tarafı `:app` için `lintVitalRelease` bu çalışmada çalıştırılmadı; yayından önce çalıştır.
