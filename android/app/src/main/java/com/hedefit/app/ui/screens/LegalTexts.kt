package com.hedefit.app.ui.screens

// TODO(legal): fill in the data controller's legal name, address and contact e-mail before release.
internal const val LEGAL_CONTROLLER = "[Veri sorumlusunun adı/unvanı ve adresi]"
internal const val LEGAL_CONTACT = "[iletişim e-posta adresi]"

internal data class LegalSection(val title: String, val body: String)

internal val KVKK_NOTICE = listOf(
    LegalSection(
        "1. Veri sorumlusu",
        "Bu aydınlatma metni, 6698 sayılı Kişisel Verilerin Korunması Kanunu (\"KVKK\") madde 10 uyarınca Hedefit mobil uygulamasının veri sorumlusu $LEGAL_CONTROLLER tarafından hazırlanmıştır. Sorularını ve taleplerini $LEGAL_CONTACT adresine iletebilirsin.",
    ),
    LegalSection(
        "2. İşlenen kişisel veriler",
        "• Kimlik ve iletişim: e-posta adresi, kullanıcı adı, görünen ad; Google ile girişte Google hesabının adı ve e-postası.\n" +
            "• Hesap ve güvenlik: şifrenin geri döndürülemez özeti (şifrenin kendisi saklanmaz), oturum ve giriş kayıtları, yasal onay tarih ve sürümleri.\n" +
            "• Profil: yaş, cinsiyet, hedef, hedef kilo ve süre, antrenman ortamı ve ekipman, başlangıç anketi cevapları, profil fotoğrafı.\n" +
            "• Sağlık verileri (özel nitelikli): boy, kilo ve vücut ölçüleri; antrenman, set, tekrar, ağırlık ve yorgunluk kayıtları; bildirdiğin ağrı/sakatlık bölgeleri; öğün ve besin kayıtları; su, uyku ve adım verileri; izin verirsen Health Connect'ten okunan adım, uyku, kilo ve aktif kalori verileri.\n" +
            "• Konum: yalnızca Hedefit Rota ile kayıt başlattığında, kayıt süresince GPS rota noktaları ve mesafe/tempo bilgileri.\n" +
            "• Görsel: öğün analizi ve ekipman tanıma için çektiğin fotoğraflar (yalnızca analiz için işlenir, sunucularımızda saklanmaz).\n" +
            "• FitKoç: bulut modunda koça yazdığın mesajlar ve sorunun yanıtlanması için gereken profil/antrenman/beslenme özeti. Sohbet geçmişi yalnızca cihazında tutulur.\n" +
            "• Cihaz ve kullanım: uygulama sürümü, hata kayıtları, günlük yapay zekâ kullanım sayacı, bildirim tercihleri; reklam gösterilen sürümde reklam kimliği.",
    ),
    LegalSection(
        "3. İşleme amaçları",
        "Hesabını oluşturmak ve güvenliğini sağlamak; kişisel antrenman programı, kalori ve makro hedefleri oluşturmak; antrenman, beslenme, adım, uyku ve kilo ilerlemeni takip etmek; FitKoç önerilerini ve öğün/fotoğraf analizlerini sunmak; rota kaydı yapmak; hatırlatma bildirimleri göndermek; ödül ve seviye sistemini işletmek; kullanıcı adının benzersizliğini ve uygunluğunu denetlemek; hataları gidermek ve hizmeti iyileştirmek; yasal yükümlülükleri yerine getirmek ve olası uyuşmazlıklarda haklarımızı korumak.",
    ),
    LegalSection(
        "4. Hukuki sebepler",
        "Genel nitelikli verilerin; sözleşmenin kurulması ve ifası (KVKK m.5/2-c), hukuki yükümlülük (m.5/2-ç), bir hakkın tesisi ve korunması (m.5/2-e) ve meşru menfaat (m.5/2-f) sebeplerine dayanarak işlenir. Özel nitelikli sağlık verilerin ve yurt dışına aktarım gerektiren işlemler ise ayrıca verdiğin açık rızaya (m.6/2 ve m.9) dayanır. Reklam kimliğinin kişiselleştirilmiş reklam amacıyla kullanımı, uygulamadaki izin ekranında verdiğin tercihe bağlıdır.",
    ),
    LegalSection(
        "5. Aktarılan taraflar ve yurt dışı aktarım",
        "Veriler satılmaz. Hizmetin çalışması için şu hizmet sağlayıcılarla, yalnızca gerekli olduğu kadar paylaşılır:\n" +
            "• Supabase: kimlik doğrulama, veritabanı ve profil fotoğrafı depolama.\n" +
            "• Cloudflare: uygulama sunucusu ve güvenli bağlantı altyapısı.\n" +
            "• Moonshot AI: bulut FitKoç yanıtları ile öğün/fotoğraf analizleri. Yerel FitKoç modu seçildiğinde sohbet cihaz dışına çıkmaz.\n" +
            "• Google: Google ile giriş, reklam gösterimi (AdMob) ve izin yönetimi, uygulama içi değerlendirme; Health Connect verileri cihazında Google altyapısı üzerinden okunur.\n" +
            "Bu sağlayıcıların sunucuları Türkiye dışında bulunabilir. Yurt dışı aktarımlar KVKK m.9 kapsamında uygun güvencelere (standart sözleşmeler) veya açık rızana dayanılarak yapılır. Ayrıca yetkili kamu kurumlarının hukuka uygun talepleri halinde veriler paylaşılabilir.",
    ),
    LegalSection(
        "6. Toplama yöntemi",
        "Veriler; uygulamaya girdiğin bilgiler, cihaz sensörleri (adım sayar, GPS, kamera), izin verdiğin Health Connect bağlantısı ve Google ile giriş aracılığıyla, elektronik ortamda otomatik ve kısmen otomatik yollarla toplanır.",
    ),
    LegalSection(
        "7. Saklama ve silme",
        "Veriler hesabın açık olduğu sürece saklanır. \"Hesabı kalıcı sil\" işlemiyle hesabın ve ilişkili verilerin silinir; yasal saklama yükümlülüğü olan kayıtlar yalnızca bu süre boyunca tutulur. \"İlerlemeyi sıfırla\" kayıtlarını siler, \"Hesabı dondur\" verilerini koruyarak erişimi durdurur. Fotoğraf analizleri saklanmaz; FitKoç sohbeti yalnızca cihazındadır ve uygulamadan temizlenebilir.",
    ),
    LegalSection(
        "8. Haklarınız (KVKK m.11)",
        "Verilerinin işlenip işlenmediğini öğrenme, bilgi talep etme, işleme amacını ve amaca uygun kullanılıp kullanılmadığını öğrenme, aktarıldığı üçüncü kişileri bilme, eksik veya yanlış verilerin düzeltilmesini, silinmesini ya da yok edilmesini isteme, bu işlemlerin aktarılan kişilere bildirilmesini isteme, otomatik sistemlerle analiz sonucu aleyhine bir sonuç çıkmasına itiraz etme ve kanuna aykırı işleme nedeniyle zararın giderilmesini talep etme haklarına sahipsin. Açık rızanı dilediğin zaman geri alabilirsin.",
    ),
    LegalSection(
        "9. Başvuru",
        "Taleplerini $LEGAL_CONTACT adresine, hesabına kayıtlı e-posta adresinden iletebilirsin. Başvurular en geç 30 gün içinde ücretsiz sonuçlandırılır. Yanıtı yeterli bulmazsan Kişisel Verileri Koruma Kurulu'na şikâyette bulunabilirsin.",
    ),
)

internal val PRIVACY_POLICY = listOf(
    LegalSection(
        "Kısaca",
        "Hedefit, antrenman ve beslenme hedeflerine ulaşman için ihtiyaç duyduğu verileri işler. Verilerini satmayız, sağlık verilerini reklam için kullanmayız ve istediğin an hesabını silebilirsin. Ayrıntılar KVKK Aydınlatma Metni'nde yer alır.",
    ),
    LegalSection(
        "Hangi verileri kullanıyoruz",
        "Hesap bilgilerin (e-posta, kullanıcı adı), profilin (yaş, boy, kilo, hedef), kayıtların (antrenman, öğün, su, uyku, adım, ölçüm), izin verdiğinde konumun (yalnızca rota kaydı sırasında), kameran (öğün ve ekipman analizi) ve Health Connect verilerin.",
    ),
    LegalSection(
        "Kullanıcı adları",
        "Kullanıcı adı her hesap için benzersizdir ve başka biri tarafından alınamaz. En az 3 karakter olmalı; müstehcen, cinsel içerikli, küfür ya da hakaret içeren veya Hedefit'i taklit eden adlar kabul edilmez.",
    ),
    LegalSection(
        "Yapay zekâ",
        "FitKoç'un bulut modu ve öğün/fotoğraf analizleri, isteğin yanıtlanması için gereken bilgileri yapay zekâ sağlayıcısına (Moonshot AI) gönderir. Sağlayıcı bu verileri yalnızca yanıt üretmek için işler. Yerel mod seçildiğinde sohbet tamamen cihazında çalışır. Yapay zekâ önerileri tıbbi tavsiye değildir; bir sağlık sorunun varsa uzmana danış.",
    ),
    LegalSection(
        "Reklamlar",
        "Ücretsiz sürümde Google AdMob reklamları gösterilebilir. Kişiselleştirilmiş reklam tercihini ilk açılıştaki izin ekranından yönetebilirsin. Sağlık ve beslenme verilerin reklam amacıyla paylaşılmaz.",
    ),
    LegalSection(
        "İzinler",
        "Konum, kamera, bildirim, fiziksel aktivite ve Health Connect izinleri yalnızca ilgili özelliği kullandığında istenir ve cihaz ayarlarından her zaman geri alınabilir. İzin vermemek diğer özellikleri engellemez.",
    ),
    LegalSection(
        "Güvenlik",
        "Tüm bağlantılar şifrelidir, veritabanında her kullanıcı yalnızca kendi kayıtlarına erişebilir ve şifreler geri döndürülemez biçimde saklanır. Hiçbir sistem tamamen risksiz olmasa da verilerini korumak için makul teknik ve idari önlemleri alırız.",
    ),
    LegalSection(
        "Kontrol sende",
        "Profil ve hedeflerini ayarlardan düzenleyebilir, ilerlemeni sıfırlayabilir, hesabını dondurabilir veya kalıcı olarak silebilirsin. Veri talepleri için $LEGAL_CONTACT adresine yazabilirsin.",
    ),
    LegalSection(
        "Çocuklar",
        "Hedefit 16 yaşından küçükler için tasarlanmamıştır. 18 yaşından küçükler uygulamayı veli veya vasi onayıyla kullanmalıdır.",
    ),
    LegalSection(
        "Değişiklikler",
        "Bu politika güncellendiğinde yeni sürüm tarihi belirtilir; önemli değişiklikler uygulama içinde bildirilir ve gerekiyorsa yeniden onayın istenir.",
    ),
)
