# Hedefit mobil uygulaması

Paket kimliği: `com.hedefit.app`

## Geliştirme

1. Web uygulamasını erişilebilir bir HTTPS adresinde çalıştır.
2. Gerekirse `CAPACITOR_SERVER_URL` ile mobil kabuğun açacağı adresi değiştir.
3. Yerel projeleri güncelle: `npm run mobile:sync`
4. Android Studio: `npm run mobile:android`
5. Xcode: `npm run mobile:ios`

Android derlemesi için Android Studio/JDK, iOS derlemesi için macOS üzerinde Xcode ve geçerli imzalama hesabı gerekir.

## Supabase Auth

Supabase Auth → URL Configuration → Redirect URLs listesine aşağıdaki adresi ekle:

`com.hedefit.app://auth/callback`

Google sağlayıcısı Supabase içinde etkin olmalı. Mobil OAuth, sistem tarayıcısında açılır ve doğrulamadan sonra deep link ile uygulamaya döner. Gemini anahtarı mobil projeye eklenmez; AI çağrıları yayınlanan sunucu API rotalarında kalır.

## Yerel özellikler

- Antrenman yerel bildirimleri
- Android geri tuşu
- Çevrimdışı bağlantı uyarısı
- Hafif dokunsal geri bildirim
- iOS ve Android güvenli alan uyumu

## Mağaza öncesi

- [x] Gizlilik politikası `/gizlilik` route'unda yayında (`app/gizlilik/page.tsx`),
      destek e-postası belirlendi: `furkaninanjob@gmail.com`.
- [x] Android upload keystore oluşturuldu (`~/hedefit-keystore-yedek/`, repo
      dışında). `android/keystore.properties` dolduruldu, imzalı AAB build
      doğrulandı (`./gradlew bundleRelease`).
- [ ] **Kalıcı prod domain satın al** ve Cloudflare'e bağla (şu an
      `hedefit.frknian.workers.dev` geçici adresi kullanılıyor). Domain
      belirlenince `CAPACITOR_SERVER_URL=https://<domain>` ile
      `npx cap sync` çalıştırılıp Play Console'a `https://<domain>/gizlilik`
      verilmeli.
- [ ] Supabase bağlantı değişkenlerini yayın ortamına ekle ve SQL şemasını uygula.
- [ ] Uygulama ikonlarını ve açılış görsellerini nihai Hedefit marka dosyalarıyla doğrula.
- [ ] Google Play Console hesabı aç (~$25 tek seferlik), `store-assets/PLAY-CONSOLE-NOTLARI.md`
      kontrol listesini uygula, telefon ekran görüntülerini al, `app-release.aab` yükle.
- [ ] Apple Developer hesabı aç (~$99/yıl), Xcode'da iOS Simulator platform
      bileşenini kur (`Xcode > Settings > Components`), imzalama takımını
      ayarla, TestFlight ve App Store Connect kaydını tamamla.
- [ ] Gerçek cihazlarda e-posta doğrulama, Google girişi, kamera, bildirim ve antrenman kaydını test et.
