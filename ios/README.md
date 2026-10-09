# Hedefit iOS

Android uygulamasıyla birebir özellik eşliğinde SwiftUI (iOS 17+) sürümü. Eski prototip `_legacy/` altında, derlenmez.

## Yapı
- `Hedefit/Core` ağ, kimlik doğrulama (Supabase), JSON, yerelleştirme (`tr("tr","en")`)
- `Hedefit/Models`, `Domain` modeller, antrenman analitiği, gamification, yetki matrisi (Guest/Free/Plus/Premium)
- `Hedefit/Data` `HedefitRepository` + `AppModel` (merkezi `@Observable`)
- `Hedefit/Services` HealthKit, GPS rota, bildirim, StoreKit 2 (`BillingManager`), Watch/Widget köprüsü
- `Hedefit/Features` ekranlar; `Hedefit/Design` ortak bileşenler
- `HedefitWidgets`, `HedefitWatch`, `HedefitWatchWidgets` widget / Apple Watch hedefleri

## Derleme
```bash
cd ios && xcodegen generate
xcodebuild -project Hedefit.xcodeproj -scheme Hedefit -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
```
DEBUG'da `-HedefitPreview` örnek veriyle (giriş gerektirmeden) açar; `-HedefitOpen <calendar|cardio|route|goal|library|atlas|muscle|wearables|notifications|rewards|settings|plans|lock|…>` doğrudan ekran açar.

## Yayın öncesi yapılacaklar
- App Store Connect: abonelik ürünleri `hedefit_plus_monthly/yearly`, `hedefit_premium_monthly/yearly`
- Sunucu: `app/api/billing/apple/verify` yayına alınmalı (`APPLE_BUNDLE_ID` opsiyonel); App Store Server Notifications V2 uç noktası henüz yok
- Supabase'te Google ve Apple sağlayıcıları (iOS client id) etkinleştirilmeli; Sign in with Apple capability
- Reklamlar (AdMob) iOS'a taşınmadı
