# Gizlilik / KVKK metni — ön inceleme (2026-10-01)

> **Bu belge hukuki görüş değildir.** Kodun gerçek davranışı ile uygulamadaki/sitedeki
> gizlilik metni (`LegalTexts.kt` → `site/gizlilik.html`) karşılaştırılarak çıkarılmış
> bir kontrol listesidir; bir KVKK avukatına götürmek için hazırlandı.
> Kaynak dosya: `android/app/src/main/java/com/hedefit/app/ui/screens/LegalTexts.kt`.

## A. Yayından önce düzeltilmesi gerekenler (yüksek öncelik)

1. **Açık rıza ayrı alınmıyor.** Kayıtta tek bir onay kutusu var: "KVKK Aydınlatma Metni'ni okudum,
   Gizlilik Politikası'nı kabul ediyorum" (`AuthScreen.kt`). Metin ise sağlık verisi ve yurt dışı aktarım
   için "açık rızana dayanır" diyor. Aydınlatmayı okumak ile açık rıza aynı şey değildir; özel nitelikli
   (sağlık) veri için rıza ayrı, belirli bir konuya ilişkin ve özgür iradeyle verilmeli, diğer onaylarla
   bağlanmamalı (KVKK m.3, m.6/2).
   *Öneri:* Ayrı, işaretsiz kutular: (a) sağlık verilerimin işlenmesine, (b) yurt dışı aktarıma açık rıza;
   reddedilebilmeli ve reddedince ne olacağı net olmalı. Rıza kaydı (tarih, sürüm) zaten tutuluyor.
   **Durum (Android):** Yapıldı. Kayıt ekranında (e-posta, Google, misafir başlangıcı) aydınlatma/politika
   onayından ayrı iki işaretsiz kutu var: sağlık verisi ve yurt dışı aktarım. İkisi de hesap açmak için
   zorunlu; metin bunu ve rızanın hesap silinerek/yazılarak geri çekilebileceğini söylüyor. Rıza zaman
   damgaları kullanıcı meta verisinde ayrı alanlarda tutuluyor (`health_data_consent_at`,
   `cross_border_consent_at`, `consent_text_version`). **Avukata sorulacak:** rızaların hizmet için
   zorunlu tutulması "özgür irade" ölçütüne uyuyor mu; mevcut (eski) kullanıcılardan yeniden rıza
   alınması gerekir mi. **Yapılmadı:** iOS kayıt akışı; mevcut kullanıcılar için yeniden rıza ekranı;
   uygulama içinden rızayı geri çekme/ayarlardan görme ekranı.
2. **Sosyal özellikler metinde yok.** Arkadaş isteği, kullanıcı adıyla arama, haftalık XP sıralaması,
   etkinlik akışı ve ortak meydan okumalar var; arkadaşlar görünen adı, kullanıcı adını, avatarı, XP'yi,
   etkinlik türünü ve (meydan okumada) mesafeyi görüyor. Kullanıcı adı en az 2 karakterle herkese
   aranabilir. Gizlilik politikasında "Sosyal özellikler" bölümü ve (mümkünse) aranabilirliği kapatma
   seçeneği gerekir.
3. **AI hafızası sunucuda saklanıyor, metin "yalnızca cihazında" diyor.** KVKK metni: "Sohbet geçmişi
   yalnızca cihazında tutulur." Ancak sunucuda kullanıcıya bağlı, yapılandırılmış "AI hafıza" kayıtları
   var (egzersiz/yemek tercihi, hedef, kısıt, alışkanlık, ekipman, motivasyon; `lib/ai/memory.ts`,
   `db/migrations/20260819_ai_memory.sql`). Kullanıcı görüp silebiliyor. Bu veri kategorisi (özellikle
   "kısıt" — sakatlık/sağlık içerebilir) metne eklenmeli.
4. **Veri sorumlusu bilgisi eksik.** Metinde yalnızca ad ve kişisel Gmail adresi var. KVKK m.10 ve
   Aydınlatma Tebliği: sorumlunun kimliği ve (varsa) temsilcisi; ticari faaliyet/abonelik için ticari
   unvan, adres, ayrı bir destek e-postası düşünülmeli. VERBİS kayıt muafiyeti (çalışan/bilanço eşikleri)
   avukatla teyit edilmeli.
5. **Yurt dışı aktarım dayanağı güncel değil.** Metin "standart sözleşmeler veya açık rıza" diyor.
   7499 sayılı Kanun (2024) sonrası: yeterlilik kararı, uygun güvenceler (standart sözleşme imzalanıp
   5 iş günü içinde Kurul'a bildirilmeli) veya arızi durumlarda açık rıza. Alıcılar: OpenAI (ABD),
   Supabase, Cloudflare, Google. Supabase projesinin bölgesi (AB/ABD) metinde belirtilmeli; sözleşme/bildirim
   durumu doğrulanmalı.
6. **Yaş.** Metin: 16 yaş altı için tasarlanmadı, 18 yaş altı veli onayıyla. Kayıtta yaş doğrulaması/beyanı
   yok (misafir girişi hiçbir şey sormuyor; yaş sonradan profil sorusunda isteniyor). Sağlık verisi işleyen
   uygulamada reşit olmayan için veli rızası mekanizması ya da net bir yaş beyanı gerekir; mağaza yaş
   derecelendirmesi de buna göre seçilmeli.

## B. Doğruluk/ifade düzeltmeleri (orta öncelik)

7. **OpenAI saklaması.** Metin "fotoğraf isteklerinde sağlayıcı tarafında kayıt (store) kapatılır" diyor
   (kodda `store:false`, doğru). Ancak API sağlayıcılar kötüye kullanım izleme için verileri sınırlı süre
   tutabilir; "eğitimde kullanılmaz" ifadesini OpenAI hesabındaki veri kontrolleriyle ve sözleşmeyle
   doğrulayın (sıfır veri saklama açık değilse "sınırlı süre saklanabilir" eklenmeli).
8. **Saklama süreleri.** "Hesap açık olduğu sürece" dışında: silinen hesabın yedekleri/günlükleri, sayaçlar,
   **30 gün giriş yapmayan kaydedilmemiş misafirlerin otomatik silinmesi** (`guest_cleanup` migration)
   metinde yok.
9. **Konum.** Arka plan konumu (foreground service) kullanılıyor; metin "yalnızca kayıt süresince" diyor
   (kodla tutarlı). Mağaza formlarındaki arka plan konum gerekçesi aynı olmalı.
10. **Sağlık platformları.** Android: Health Connect (adım, uyku, kilo, aktif kalori, nabız) ve metinde
    var. iOS (HealthKit okuma/yazma, saat uygulaması) için ayrı madde ve Apple "App Privacy" etiketleri yok.
11. **Reklam.** Android'de AdMob + UMP rıza ekranı var, metinde var. iOS'ta reklam/ATT izni projede
    görünmüyor; iOS sürümünde reklam varsa App Tracking Transparency ve etiketler gerekir.
12. **Site SSS'si** (`site/index.html`, "Verilerim nerede"): "yalnızca o an yazdığın metin ya da fotoğraf
    işlenir" doğru değildi (profil/antrenman/beslenme özeti ve AI hafızası da işleniyor). Bu düzeltildi.

## C. Mağaza formları ile tutarlılık

- **Google Play Data safety:** konum (yaklaşık/kesin, arka plan), sağlık ve fitness, fotoğraflar, kullanıcı
  kimlikleri, reklam kimliği, kişisel bilgiler (e-posta, ad); paylaşılan üçüncü taraflar: OpenAI, Google (AdMob).
  Veri silme talebi URL'si: `/hesap-silme`.
- **App Store App Privacy:** Health & Fitness, Location, Photos, Identifiers, Usage Data; izleme (ATT) varsa
  ayrıca beyan.
- Gizlilik politikası ve destek URL'si kalıcı alan adında olmalı (şu an `workers.dev`).

## D. Metin zaten uyumlu görünenler
Hesap silme akışı (uygulamadan, avatar dahil, zincirleme silme), şifrenin saklanmaması, konumun yalnızca
rota kaydında kullanılması, fotoğrafların sunucuda saklanmaması, sağlık verisinin reklam için kullanılmaması,
ilgili kişi hakları ve 30 gün başvuru süresi.

## E. Avukata sorulacaklar
Açık rıza metinleri ve kayıt akışı; yurt dışı aktarım yöntemi (standart sözleşme + Kurul bildirimi);
veri sorumlusu/temsilci/VERBİS durumu; yaş ve veli rızası; sosyal özelliklerde aydınlatma; AI sağlayıcısıyla
veri işleme sözleşmesi (DPA).
