package com.hedefit.app.ui.screens

// TODO(legal): fill in the data controller's legal name, address and contact e-mail before release.
internal const val LEGAL_CONTROLLER = "Furkan İNAN (furkaninanjob@gmail.com)"
internal const val LEGAL_CONTACT = "furkaninanjob@gmail.com"

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

internal val KVKK_NOTICE_EN = listOf(
    LegalSection(
        "1. Data controller",
        "This privacy notice is issued under Article 10 of Turkish Personal Data Protection Law No. 6698 (\"KVKK\") by $LEGAL_CONTROLLER, the data controller of the Hedefit mobile app. You can send questions and requests to $LEGAL_CONTACT.",
    ),
    LegalSection(
        "2. Personal data we process",
        "• Identity and contact: email address, username, display name; with Google sign-in, your Google account name and email.\n" +
            "• Account and security: an irreversible hash of your password (the password itself is never stored), session and sign-in records, dates and versions of legal consents.\n" +
            "• Profile: age, gender, goal, target weight and timeline, training setting and equipment, onboarding answers, profile photo.\n" +
            "• Health data (special category): height, weight and body measurements; workout, set, rep, weight and fatigue logs; pain/injury areas you report; meal and food logs; water, sleep and step data; if you allow it, steps, sleep, weight and active calories read from Health Connect.\n" +
            "• Location: only while you record with Hedefit Route, GPS route points and distance/pace for the duration of the recording.\n" +
            "• Images: photos you take for meal analysis and equipment recognition (processed only for analysis, not stored on our servers).\n" +
            "• Fit Coach: in cloud mode, the messages you send and the profile/workout/nutrition summary needed to answer. Chat history is kept only on your device.\n" +
            "• Device and usage: app version, error logs, daily AI usage counter, notification preferences; advertising ID in the ad-supported version.",
    ),
    LegalSection(
        "3. Purposes of processing",
        "Creating and securing your account; building your personal training program and calorie and macro targets; tracking your workout, nutrition, step, sleep and weight progress; providing Fit Coach suggestions and meal/photo analysis; recording routes; sending reminder notifications; running the rewards and level system; checking that usernames are unique and appropriate; fixing errors and improving the service; meeting legal obligations and protecting our rights in potential disputes.",
    ),
    LegalSection(
        "4. Legal grounds",
        "General personal data is processed on the grounds of entering into and performing a contract (KVKK Art. 5/2-c), legal obligation (Art. 5/2-ç), establishing and protecting a right (Art. 5/2-e) and legitimate interest (Art. 5/2-f). Special-category health data and processing that requires transfer abroad additionally rely on your explicit consent (Art. 6/2 and Art. 9). Use of the advertising ID for personalized ads depends on the choice you make on the in-app consent screen.",
    ),
    LegalSection(
        "5. Recipients and transfers abroad",
        "Your data is never sold. It is shared with the following service providers only as far as needed to run the service:\n" +
            "• Supabase: authentication, database and profile photo storage.\n" +
            "• Cloudflare: application server and secure connection infrastructure.\n" +
            "• Moonshot AI: cloud Fit Coach replies and meal/photo analysis. In local Fit Coach mode, chats never leave your device.\n" +
            "• Google: Google sign-in, ad serving (AdMob) and consent management, in-app reviews; Health Connect data is read on your device through Google infrastructure.\n" +
            "These providers' servers may be located outside Türkiye. Transfers abroad are made under KVKK Art. 9 on the basis of appropriate safeguards (standard contracts) or your explicit consent. Data may also be shared in response to lawful requests from competent public authorities.",
    ),
    LegalSection(
        "6. Collection method",
        "Data is collected electronically, by automatic and partly automatic means, through the information you enter in the app, device sensors (step counter, GPS, camera), the Health Connect connection you allow and Google sign-in.",
    ),
    LegalSection(
        "7. Retention and deletion",
        "Data is kept while your account is open. \"Delete account permanently\" deletes your account and related data; records subject to legal retention are kept only for that period. \"Reset progress\" deletes your logs, and \"Freeze account\" stops access while keeping your data. Photo analyses are not stored; Fit Coach chats live only on your device and can be cleared in the app.",
    ),
    LegalSection(
        "8. Your rights (KVKK Art. 11)",
        "You have the right to learn whether your data is processed, request information, learn the purpose of processing and whether data is used accordingly, know the third parties it is transferred to, ask for incomplete or incorrect data to be corrected, deleted or destroyed, ask for these actions to be notified to recipients, object to an outcome against you arising from analysis by automated systems, and claim compensation for damage caused by unlawful processing. You can withdraw your explicit consent at any time.",
    ),
    LegalSection(
        "9. How to apply",
        "Send your requests to $LEGAL_CONTACT from the email address registered to your account. Requests are resolved free of charge within 30 days at the latest. If you find the reply insufficient, you can file a complaint with the Turkish Personal Data Protection Board.",
    ),
)

internal val PRIVACY_POLICY_EN = listOf(
    LegalSection(
        "In short",
        "Hedefit processes the data it needs to help you reach your training and nutrition goals. We never sell your data, we don't use health data for ads, and you can delete your account at any time. Details are in the KVKK Privacy Notice.",
    ),
    LegalSection(
        "What data we use",
        "Your account details (email, username), profile (age, height, weight, goal), logs (workouts, meals, water, sleep, steps, measurements), your location when you allow it (only while recording a route), your camera (meal and equipment analysis) and Health Connect data.",
    ),
    LegalSection(
        "Usernames",
        "Each username is unique and can't be taken by anyone else. It must be at least 3 characters; obscene, sexual, profane or insulting names, or names impersonating Hedefit, are not accepted.",
    ),
    LegalSection(
        "Artificial intelligence",
        "Fit Coach's cloud mode and meal/photo analysis send the information needed to answer your request to the AI provider (Moonshot AI). The provider processes this data only to generate the reply. In local mode, the chat runs entirely on your device. AI suggestions are not medical advice; if you have a health concern, consult a professional.",
    ),
    LegalSection(
        "Ads",
        "The free version may show Google AdMob ads. You can manage personalized ad preferences on the consent screen at first launch. Your health and nutrition data is never shared for advertising.",
    ),
    LegalSection(
        "Permissions",
        "Location, camera, notification, physical activity and Health Connect permissions are requested only when you use the related feature and can always be revoked in device settings. Declining a permission doesn't block other features.",
    ),
    LegalSection(
        "Security",
        "All connections are encrypted, each user can access only their own records in the database, and passwords are stored irreversibly. No system is completely risk-free, but we take reasonable technical and organizational measures to protect your data.",
    ),
    LegalSection(
        "You're in control",
        "You can edit your profile and goals in settings, reset your progress, freeze your account or delete it permanently. For data requests, write to $LEGAL_CONTACT.",
    ),
    LegalSection(
        "Children",
        "Hedefit is not designed for children under 16. Users under 18 should use the app with the consent of a parent or guardian.",
    ),
    LegalSection(
        "Changes",
        "When this policy is updated, the new version date is shown; significant changes are announced in the app and, where required, your consent is requested again.",
    ),
)

/** Uygulama diline göre yasal metin. */
internal fun kvkkNotice() = if (com.hedefit.app.ui.i18n.AppLang.en) KVKK_NOTICE_EN else KVKK_NOTICE
internal fun privacyPolicy() = if (com.hedefit.app.ui.i18n.AppLang.en) PRIVACY_POLICY_EN else PRIVACY_POLICY
