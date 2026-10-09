import SwiftUI

enum LegalDocument: String, Identifiable, CaseIterable {
    case kvkk, privacy, terms, healthConsent, crossBorderConsent
    var id: String { rawValue }

    @MainActor var title: String {
        switch self {
        case .kvkk: return tr("KVKK Aydınlatma Metni", "KVKK Notice")
        case .privacy: return tr("Gizlilik Politikası", "Privacy Policy")
        case .terms: return tr("Kullanım Koşulları", "Terms of Use")
        case .healthConsent: return tr("Sağlık Verisi Açık Rızası", "Health Data Explicit Consent")
        case .crossBorderConsent: return tr("Yurt Dışı Aktarım Açık Rızası", "Cross-Border Transfer Consent")
        }
    }

    @MainActor var sections: [LegalSection] {
        let en = AppLang.shared.en
        switch self {
        case .kvkk: return en ? kvkkNoticeEn : kvkkNotice
        case .privacy: return en ? privacyPolicyEn : privacyPolicy
        case .terms: return en ? Self.termsEn : Self.termsTr
        case .healthConsent:
            return [LegalSection(title: tr("Açık rıza", "Explicit consent"), body: en
                ? "I consent to Hedefit processing my health data (height, weight, body measurements, workouts, sets, fatigue and pain reports, meals, water, sleep, steps and data I allow from Apple Health) to create my programs and goals, track progress and provide coach suggestions. I can withdraw this consent at any time in Settings → Privacy and consents."
                : "Hedefit'in program ve hedeflerimi oluşturmak, ilerlememi izlemek ve koç önerileri sunmak amacıyla sağlık verilerimi (boy, kilo, vücut ölçüleri, antrenman, set, yorgunluk ve ağrı bildirimleri, öğün, su, uyku, adım ve izin verdiğim Apple Sağlık verileri) işlemesine açık rıza veriyorum. Bu rızayı Ayarlar → Gizlilik ve rızalar bölümünden istediğim zaman geri alabilirim.")]
        case .crossBorderConsent:
            return [LegalSection(title: tr("Açık rıza", "Explicit consent"), body: en
                ? "I consent to my data being transferred to service providers located outside Türkiye (database, hosting and AI provider) as described in the KVKK notice, to deliver the service. I can withdraw this consent at any time."
                : "Verilerimin, KVKK Aydınlatma Metni'nde açıklandığı şekilde hizmetin sunulması için Türkiye dışında bulunan hizmet sağlayıcılara (veritabanı, barındırma ve yapay zekâ sağlayıcısı) aktarılmasına açık rıza veriyorum. Bu rızayı istediğim zaman geri alabilirim.")]
        }
    }

    private static let termsTr = [
        LegalSection(title: "1. Hizmet", body: "Hedefit; antrenman, beslenme ve hedef takibi sunan bir fitness uygulamasıdır. Uygulamadaki öneriler genel bilgilendirme amaçlıdır ve tıbbi tavsiye yerine geçmez. Egzersize başlamadan önce gerekirse bir sağlık uzmanına danış."),
        LegalSection(title: "2. Hesap", body: "Hesabından ve girdiğin bilgilerin doğruluğundan sen sorumlusun. Hesabını dilediğin zaman Ayarlar'dan dondurabilir veya kalıcı olarak silebilirsin."),
        LegalSection(title: "3. Abonelikler", body: "Plus ve Premium abonelikleri App Store üzerinden satın alınır ve Apple Kimliğinin iTunes hesabından tahsil edilir. Abonelik, dönem bitiminden en az 24 saat önce iptal edilmedikçe otomatik yenilenir. Aboneliği iPhone'unda Ayarlar → Apple Kimliği → Abonelikler bölümünden yönetebilir ve iptal edebilirsin. Ücretsiz deneme süresinin kullanılmayan kısmı, abonelik satın alındığında sona erer. Hesabını silmek aboneliği iptal etmez; önce App Store'dan iptal etmelisin. Apple'ın standart Lisanslı Uygulama Sözleşmesi (EULA) geçerlidir."),
        LegalSection(title: "4. Kabul edilebilir kullanım", body: "Uygulamayı yasalara ve başkalarının haklarına uygun kullanmayı, hizmeti kötüye kullanmamayı, uygunsuz kullanıcı adları veya içerik oluşturmamayı kabul edersin."),
        LegalSection(title: "5. Sorumluluk", body: "Hedefit, kişisel sağlık durumuna özel sonuç garantisi vermez. Yapay zekâ çıktıları hatalı olabilir; kalori ve besin değerleri tahminidir."),
        LegalSection(title: "6. İletişim", body: "Sorular için \(legalContact) adresine yazabilirsin."),
    ]
    private static let termsEn = [
        LegalSection(title: "1. Service", body: "Hedefit is a fitness app offering workout, nutrition and goal tracking. Suggestions in the app are for general information and are not medical advice. If needed, consult a health professional before starting to exercise."),
        LegalSection(title: "2. Account", body: "You are responsible for your account and the accuracy of what you enter. You can freeze or permanently delete your account in Settings at any time."),
        LegalSection(title: "3. Subscriptions", body: "Plus and Premium subscriptions are purchased through the App Store and charged to your Apple ID account. Subscriptions renew automatically unless cancelled at least 24 hours before the end of the current period. You can manage and cancel in iPhone Settings → Apple ID → Subscriptions. Any unused portion of a free trial is forfeited when you purchase a subscription. Deleting your account does not cancel your subscription; cancel in the App Store first. Apple's standard Licensed Application End User License Agreement (EULA) applies."),
        LegalSection(title: "4. Acceptable use", body: "You agree to use the app lawfully and respectfully, not abuse the service, and not create inappropriate usernames or content."),
        LegalSection(title: "5. Liability", body: "Hedefit does not guarantee results for your personal health situation. AI output may be wrong; calories and nutrient values are estimates."),
        LegalSection(title: "6. Contact", body: "Write to \(legalContact) with questions."),
    ]
}

struct LegalSheet: View {
    let document: LegalDocument
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    ForEach(Array(document.sections.enumerated()), id: \.offset) { _, section in
                        VStack(alignment: .leading, spacing: 6) {
                            Text(section.title).font(.hfTitleM.weight(.heavy)).foregroundStyle(HC.text)
                            Text(section.body).font(.hfBody).foregroundStyle(HC.textSecondary).fixedSize(horizontal: false, vertical: true)
                        }
                    }
                    Text(tr("Sürüm: \(AppConfiguration.legalDocumentVersion)", "Version: \(AppConfiguration.legalDocumentVersion)")).font(.hfSmall).foregroundStyle(HC.muted)
                }.padding(20)
            }
            .background(HC.bg).navigationTitle(document.title).navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button(tr("Kapat", "Close")) { dismiss() } } }
        }
    }
}

// MARK: - Dondurulmuş hesap

struct FrozenAccountView: View {
    @Environment(AppModel.self) private var app
    @State private var busy = false
    var body: some View {
        VStack(spacing: 18) {
            Image(systemName: "snowflake").font(.system(size: 54)).foregroundStyle(HC.water)
            Text(tr("Hesabın dondurulmuş", "Your account is frozen")).font(.hfHeadline.weight(.heavy)).foregroundStyle(HC.text)
            Text(tr("Verilerin korunuyor. Devam etmek için hesabını yeniden etkinleştir.", "Your data is safe. Reactivate your account to continue.")).font(.hfBody).foregroundStyle(HC.textSecondary).multilineTextAlignment(.center)
            HfButton(title: tr("Hesabı yeniden etkinleştir", "Reactivate account"), loading: busy) {
                busy = true
                Task {
                    defer { busy = false }
                    do { try await app.repo.reactivateAccount(); app.accountFrozen = false; await app.refreshAll() } catch { app.fail(error) }
                }
            }
            Button(tr("Çıkış yap", "Sign out")) { Task { await app.signOut() } }.foregroundStyle(HC.textSecondary)
        }.padding(28)
    }
}

// MARK: - Açık rıza kapısı (eski hesaplar)

struct ConsentGateView: View {
    @Environment(AppModel.self) private var app
    @State private var health = false
    @State private var cross = false
    @State private var busy = false
    @State private var page: LegalDocument?
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text(tr("Açık rızaların gerekli", "Your explicit consent is needed")).font(.hfHeadline.weight(.heavy)).foregroundStyle(HC.text)
                Text(tr("Hedefit'i kullanmaya devam etmek için sağlık verilerinin işlenmesine ve yurt dışı aktarıma ayrı ayrı açık rıza vermelisin.", "To keep using Hedefit, you need to give separate explicit consents for health data processing and cross-border transfer.")).font(.hfBody).foregroundStyle(HC.textSecondary)
                consent($health, tr("Sağlık verilerimin işlenmesine açık rıza veriyorum.", "I consent to the processing of my health data."), .healthConsent)
                consent($cross, tr("Verilerimin yurt dışına aktarılmasına açık rıza veriyorum.", "I consent to the cross-border transfer of my data."), .crossBorderConsent)
                HfButton(title: tr("Devam et", "Continue"), loading: busy, enabled: health && cross) {
                    busy = true
                    Task {
                        defer { busy = false }
                        do { try await AuthService.shared.saveExplicitConsents(); app.consentRequired = false } catch { app.fail(error) }
                    }
                }
                Button(tr("Çıkış yap", "Sign out")) { Task { await app.signOut() } }.foregroundStyle(HC.textSecondary).frame(maxWidth: .infinity)
            }.padding(24)
        }.sheet(item: $page) { LegalSheet(document: $0) }
    }

    private func consent(_ binding: Binding<Bool>, _ text: String, _ doc: LegalDocument) -> some View {
        HfCard {
            HStack(alignment: .top, spacing: 12) {
                Button { binding.wrappedValue.toggle() } label: { Image(systemName: binding.wrappedValue ? "checkmark.square.fill" : "square").font(.system(size: 24)).foregroundStyle(binding.wrappedValue ? HC.lime : HC.muted) }.buttonStyle(.plain)
                VStack(alignment: .leading, spacing: 4) {
                    Text(text).font(.hfBody).foregroundStyle(HC.text)
                    Button(tr("Metni oku", "Read the text")) { page = doc }.font(.hfLabel.weight(.bold)).foregroundStyle(HC.lime)
                }
            }
        }
    }
}
