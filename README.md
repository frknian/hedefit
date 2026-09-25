# Hedefit

Hedefit; antrenman, kardiyo/rota, beslenme, hedef ve görev takibini tek yerde
toplayan bir fitness uygulamasıdır. Bu depo backend servislerini, native Android
(Kotlin + Jetpack Compose) ve iOS (SwiftUI) istemcilerini içerir.

## Öne çıkanlar

- **Fit Koç**: bulut modelleri ve cihaz üstü (on-device) yerel AI ile sohbet,
  program üretimi ve besin çıkarımı
- **Antrenman**: hazır programlar, detaylı ve hızlı (dokun-bitir) set modu,
  takvim, spor salonu analitiği ve kas haritası
- **Kardiyo ve Rota**: tam ekran kardiyo, rota planlama, arka planda takip ve
  paylaşım kartları; koşu, yürüyüş, doğa yürüyüşü, trail, bisiklet ve kayak
- **Hedef yolculuğu**: kanıta dayalı tempo seçenekleri, su/protein/adım
  hedefleri ve kaynaklar
- **Görevler ve başarımlar**: XP, seri (streak) ve başlangıç rehberi görevleri
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

## Canlı ortam

Backend Cloudflare Workers üzerinde yayınlanır:

- Production API: `https://hedefit.frknian.workers.dev`
- Fit Koç, plan türüne göre günlük kullanım kotası uygular. Görevlerden kazanılan
  XP, ücretsiz hesapların günlük soru hakkını artırır: 300 XP'de +1, 500 XP'de
  +2 ve sonrasında her 250 XP'de bir ek hak (en çok +5). Kötüye kullanım
  koruması olarak kullanıcı başına kısa süreli istek sınırı korunur.
- Fit Koç model yönlendirmesi basit sohbet, besin çıkarımı ve görsel okumada
  `gpt-4o`; kişisel program üretimi ve karmaşık haftalık değerlendirmede
  `gpt-5.1` kullanır. Model adları Worker ortamındaki `OPENAI_MODEL_*`
  değişkenleriyle değiştirilebilir.

Canlı dağıtım:

```bash
npm run deploy
```

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
