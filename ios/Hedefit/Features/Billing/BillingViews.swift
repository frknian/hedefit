import SwiftUI
import StoreKit
import AuthenticationServices

// MARK: - Paketler

/// Plus varsayılan seçili ve "En çok tercih edilen"; fiyat ve deneme teklifi App Store'dan gelir.
struct PlansSheet: View {
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss
    private var billing = BillingManager.shared
    @State private var selected: Tier = .plus
    @State private var yearly = false

    private func product(_ t: Tier) -> Product? { billing.products[BillingProduct.id(t, yearly: yearly)] }
    private func price(_ t: Tier) -> String {
        let p = product(t)
        let amount = p?.displayPrice ?? ({ switch (t, yearly) { case (.premium, true): return "₺1.449"; case (.premium, false): return "₺169"; case (_, true): return "₺849"; default: return "₺99" } }())
        let trial = p.flatMap { billing.freeTrialDays($0) }.map { tr(" · ilk \($0) gün ücretsiz", " · first \($0) days free") } ?? ""
        return "\(amount) / \(yearly ? tr("yıl", "year") : tr("ay", "month"))\(trial)"
    }

    var body: some View {
        let plus = tierLimits[.plus]!, premium = tierLimits[.premium]!
        let owned = app.tier == selected
        let offer = product(selected)
        let trialDays = offer.flatMap { billing.freeTrialDays($0) }
        let label: String = {
            if app.isGuest { return tr("Satın almak için hesabını kaydet", "Save your account to subscribe") }
            if owned { return tr("Şu anki paketin", "Your current plan") }
            if billing.busy { return tr("İşleniyor…", "Processing…") }
            if billing.loading { return tr("Fiyatlar yükleniyor…", "Loading prices…") }
            if offer == nil { return tr("Fiyatlar yüklenemedi, yeniden dene", "Couldn't load prices, tap to retry") }
            if let d = trialDays { return tr("\(d) gün ücretsiz dene", "Try free for \(d) days") }
            if app.tier == .premium { return tr("\(selected.label) paketine geç", "Switch to \(selected.label)") }
            return tr("\(selected.label) paketine abone ol", "Subscribe to \(selected.label)")
        }()
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text(tr("Paketini seç", "Choose your plan")).font(.hfHeadline.weight(.black)).foregroundStyle(HC.text)
                Text(tr("Şu anki paketin: ", "Current plan: ") + app.tier.label).foregroundStyle(HC.textSecondary)
                HStack(spacing: 8) {
                    HfChip(text: tr("Aylık", "Monthly"), selected: !yearly) { yearly = false }
                    HfChip(text: tr("Yıllık · %28 indirim", "Yearly · 28% off"), selected: yearly) { yearly = true }
                }
                planCard(.plus, badge: tr("En çok tercih edilen", "Most popular"), perks: [
                    tr("Günde \(plus.dailyCoachQuestions) FitKoç sorusu", "\(plus.dailyCoachQuestions) Fit Coach questions a day"),
                    tr("Sınırsız öğün kaydı", "Unlimited meal logging"),
                    tr("Tüm hareketler ve \(plus.customPrograms) özel program", "All exercises and \(plus.customPrograms) custom programs"),
                    tr("Kardiyo programları ve oyun modu", "Cardio programs and game mode"),
                    tr("Haftalık öğün planlayıcı", "Weekly meal planner"),
                ])
                planCard(.premium, badge: nil, perks: [
                    tr("Plus'taki her şey", "Everything in Plus"),
                    tr("Günde \(premium.dailyCoachQuestions) FitKoç sorusu", "\(premium.dailyCoachQuestions) Fit Coach questions a day"),
                    tr("Günde \(premium.dailyPhotoMeals) fotoğraftan kalori", "\(premium.dailyPhotoMeals) photo calorie scans a day"),
                    tr("Sınırsız özel program ve bölgesel programlar", "Unlimited custom and body-part programs"),
                    tr("Sınırsız ilerleme geçmişi", "Unlimited progress history"),
                ])
                HfButton(title: label, enabled: !owned && !billing.busy && !billing.loading) {
                    if app.isGuest { dismiss(); DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { app.showSaveAccount = true } }
                    else if let offer { Task { await billing.purchase(offer, app: app) } }
                    else { Task { await billing.loadOffers() } }
                }
                if app.tier == .plus || app.tier == .premium { Button(tr("Aboneliği yönet veya iptal et", "Manage or cancel subscription")) { Task { await billing.manageSubscriptions() } }.foregroundStyle(HC.textSecondary).frame(maxWidth: .infinity) }
                if !app.isGuest { Button(tr("Satın alımları geri yükle", "Restore purchases")) { Task { await billing.restore(app: app, userInitiated: true) } }.font(.hfBody.weight(.semibold)).foregroundStyle(HC.lime).frame(maxWidth: .infinity) }
                if let m = billing.message { Text(m).font(.hfSmall).foregroundStyle(HC.textSecondary).multilineTextAlignment(.center).frame(maxWidth: .infinity) }
                Text(tr("Abonelik, onayda Apple Kimliği hesabından tahsil edilir ve dönem bitiminden en az 24 saat önce iptal edilmedikçe otomatik yenilenir. Ayarlar > Apple Kimliği > Abonelikler'den yönetebilir veya iptal edebilirsin.", "Payment is charged to your Apple ID at confirmation and the subscription renews automatically unless cancelled at least 24 hours before the period ends. Manage or cancel in Settings > Apple ID > Subscriptions.")).font(.system(size: 11)).foregroundStyle(HC.muted).multilineTextAlignment(.center)
                HStack(spacing: 18) {
                    Button(tr("Kullanım Koşulları", "Terms of Use")) { openLegal("terms") }
                    Button(tr("Gizlilik Politikası", "Privacy Policy")) { openLegal("privacy") }
                }.font(.system(size: 12, weight: .semibold)).foregroundStyle(HC.textSecondary).frame(maxWidth: .infinity)
            }.padding(20)
        }
        .background(HC.bg).presentationDetents([.large])
        .task { selected = app.tier == .premium ? .premium : .plus; await billing.loadOffers() }
    }

    private func openLegal(_ key: String) { dismiss(); DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { app.push(.legal(key)) } }

    private func planCard(_ tier: Tier, badge: String?, perks: [String]) -> some View {
        let isSelected = selected == tier
        return Button { selected = tier } label: {
            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    Text(tier.label).font(.system(size: 22, weight: .black)).foregroundStyle(HC.text); Spacer()
                    if let badge { Text(badge).font(.system(size: 11, weight: .black)).foregroundStyle(HC.onLime).padding(.horizontal, 10).padding(.vertical, 4).background(HC.lime, in: Capsule()) }
                }
                Text(price(tier)).font(.hfBody.weight(.bold)).foregroundStyle(HC.textSecondary)
                ForEach(perks, id: \.self) { Text("✓  \($0)").font(.system(size: 14)).foregroundStyle(HC.text).multilineTextAlignment(.leading) }
            }
            .padding(16).frame(maxWidth: .infinity, alignment: .leading)
            .background(isSelected ? HC.lime.opacity(0.08) : HC.surface, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 22, style: .continuous).stroke(isSelected ? HC.lime : HC.divider, lineWidth: isSelected ? 2 : 1))
        }.buttonStyle(.plain)
    }
}

// MARK: - Kilit

private func fmt(_ v: Int) -> String { v == unlimited ? "∞" : "\(v)" }
private func yes(_ v: Bool) -> String { v ? "✓" : "—" }
@MainActor private func levels(_ s: Set<String>) -> String { s.count == 1 ? tr("Başl.", "Beg.") : s.count == 2 ? tr("Başl.+Orta", "Beg.+Int.") : tr("Tümü", "All") }

struct PremiumLockSheet: View {
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss
    let feature: LockedFeature

    var body: some View {
        let tiers: [Tier] = [.free, .plus, .premium]
        let limits = tiers.map { tierLimits[$0]! }
        let rows: [(String, [String])] = [
            (tr("FitKoç soru / gün", "Fit Coach Qs / day"), limits.map { fmt($0.dailyCoachQuestions) }),
            (tr("Öğün kaydı / gün", "Meal logs / day"), limits.map { fmt($0.dailyMealLogs) }),
            (tr("Fotoğraftan kalori", "Photo calories"), limits.map { fmt($0.dailyPhotoMeals) }),
            (tr("Hareketler", "Exercises"), limits.map { levels($0.exerciseLevels) }),
            (tr("Kendi programın", "Own programs"), limits.map { fmt($0.customPrograms) }),
            (tr("Öğün planlayıcı", "Meal planner"), limits.map { yes($0.mealPlanner) }),
            (tr("Bölgesel program", "Regional program"), limits.map { yes($0.regionalPlans) }),
            (tr("Geçmiş (gün)", "History (days)"), limits.map { fmt($0.historyDays) }),
        ]
        ScrollView {
            VStack(spacing: 12) {
                Text(feature.emoji).font(.system(size: 48))
                Text(feature.title).font(.hfHeadline.weight(.black)).foregroundStyle(HC.text).multilineTextAlignment(.center)
                Text(feature.body).foregroundStyle(HC.textSecondary).multilineTextAlignment(.center)
                VStack(spacing: 8) {
                    HStack { Spacer().frame(maxWidth: .infinity).layoutPriority(0); ForEach(tiers, id: \.self) { Text($0.label).font(.system(size: 12, weight: .black)).foregroundStyle($0 == .free ? HC.muted : HC.warning).frame(width: 64) } }
                    ForEach(rows, id: \.0) { label, values in
                        HStack { Text(label).font(.system(size: 13)).foregroundStyle(HC.text).frame(maxWidth: .infinity, alignment: .leading)
                            ForEach(Array(values.enumerated()), id: \.offset) { i, v in Text(v).font(.system(size: 13, weight: i == 0 ? .regular : .bold)).foregroundStyle(i == 0 ? HC.textSecondary : HC.text).frame(width: 64) } }
                    }
                }.padding(14).background(HC.surfaceHigh, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                HfButton(title: app.isGuest ? tr("Hesabını kaydet", "Save your account") : tr("Paketleri gör", "See plans")) {
                    dismiss()
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { if app.isGuest { app.showSaveAccount = true } else { app.showPlans = true } }
                }
                Button(tr("Tamam", "OK")) { dismiss() }.foregroundStyle(HC.textSecondary)
            }.padding(24)
        }.background(HC.surface).presentationDetents([.large])
    }
}

// MARK: - Hesabı kaydet

enum SaveAccountTrigger { case manual, limit, workoutCompleted, coachLimit, sync }

struct SaveAccountSheet: View {
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss
    var trigger: SaveAccountTrigger = .manual
    @State private var useEmail = false
    @State private var email = ""
    @State private var username = ""
    @State private var password = ""
    @State private var submitted = false
    @State private var busy = false
    @State private var message: String?

    var body: some View {
        let locked = app.lockedFeature
        let (emoji, title, body): (String, String, String) = {
            switch trigger {
            case .limit: return (locked?.emoji ?? "🔒", locked?.title ?? tr("Daha fazlası için hesabını kaydet", "Save your account for more"), (locked?.body ?? "") + tr(" Ücretsiz hesap aç; sınırların genişlesin, ilerlemen güvende kalsın.", " Create a free account to raise your limits and keep your progress safe."))
            case .workoutCompleted: return ("💪", tr("İlk antrenmanın kayıtlı kalsın", "Keep your first workout"), tr("Serin, XP'in ve programın şu an sadece bu cihazda. Hesabını kaydet, hiçbirini kaybetme.", "Your streak, XP and program live only on this device. Save your account so you never lose them."))
            case .coachLimit: return ("🤖", tr("Koçunla konuşmaya devam et", "Keep talking to your coach"), tr("Misafir sohbet hakkın doldu. Ücretsiz hesap ile koçun seni tanımaya devam etsin.", "You've used your guest chats. A free account lets your coach keep learning about you."))
            case .sync: return ("⌚", tr("Cihazlarını eşitle", "Sync your devices"), tr("Saat ve sağlık verisi eşitlemesi için hesabını kaydetmelisin.", "Save your account to sync watch and health data."))
            case .manual: return ("🛡️", tr("Hesabını kaydet", "Save your account"), tr("İlerlemen güvende olsun, telefon değiştirsen bile kaldığın yerden devam et.", "Keep your progress safe and pick up where you left off, even on a new phone."))
            }
        }()
        let formError: String? = {
            guard submitted else { return nil }
            if !Validation.isEmail(email) { return tr("Geçerli bir e-posta adresi yaz.", "Enter a valid email address.") }
            if password.count < 8 { return tr("Parola en az 8 karakter olmalı.", "Password must be at least 8 characters.") }
            if username.trimmingCharacters(in: .whitespaces).count < 3 { return tr("Kullanıcı adı en az 3 karakter olmalı.", "Username must be at least 3 characters.") }
            return nil
        }()
        ScrollView {
            VStack(spacing: 12) {
                Text(emoji).font(.system(size: 52))
                Text(title).font(.hfHeadline.weight(.black)).foregroundStyle(HC.text).multilineTextAlignment(.center)
                Text(body).foregroundStyle(HC.textSecondary).multilineTextAlignment(.center)
                if let message { Text(message).font(.hfSmall).foregroundStyle(HC.text).padding(14).frame(maxWidth: .infinity, alignment: .leading).background(HC.surfaceHigh, in: RoundedRectangle(cornerRadius: 14)) }
                if !useEmail {
                    HfButton(title: tr("Apple ile kaydet", "Save with Apple"), secondary: true, enabled: !busy) { Task { await social(apple: true) } }
                    HfButton(title: tr("Google ile kaydet", "Save with Google"), secondary: true, enabled: !busy) { Task { await social(apple: false) } }
                    HfButton(title: tr("E-posta ile kaydet", "Save with email"), enabled: !busy) { useEmail = true }
                } else {
                    HfField(title: tr("Kullanıcı adı", "Username"), text: $username, keyboard: .asciiCapable)
                    HfField(title: tr("E-posta", "Email"), text: $email, keyboard: .emailAddress)
                    HfField(title: tr("Parola", "Password"), text: $password, secure: true)
                    if let formError { Text(formError).font(.hfSmall).foregroundStyle(HC.coral).frame(maxWidth: .infinity, alignment: .leading) }
                    HfButton(title: busy ? tr("Kaydediliyor…", "Saving…") : tr("Hesabımı kaydet", "Save my account"), enabled: !busy) {
                        submitted = true
                        guard Validation.isEmail(email), password.count >= 8, username.trimmingCharacters(in: .whitespaces).count >= 3 else { return }
                        Task { await linkEmail() }
                    }
                }
                Button(tr("Şimdi değil", "Not now")) { dismiss() }.foregroundStyle(HC.textSecondary)
            }.padding(24)
        }.background(HC.surface).presentationDetents([.large])
    }

    private func linkEmail() async {
        busy = true; defer { busy = false }
        do {
            try await AuthService.shared.linkEmail(email: email, password: password, username: username)
            message = tr("Doğrulama bağlantısı e-posta adresine gönderildi. Doğruladıktan sonra hesabın kayıtlı olur.", "A verification link was sent to your email. Your account is saved once you verify.")
            await app.refreshSession()
        } catch { message = error.friendly }
    }

    private func social(apple: Bool) async {
        busy = true; defer { busy = false }
        do {
            let credential = apple ? try await AppleAuthService.signIn() : try await GoogleAuthService.signIn()
            let session = try await AuthService.shared.linkIdentity(provider: apple ? "apple" : "google", idToken: credential.idToken, nonce: credential.nonce)
            app.session = session
            await app.refreshAll()
            app.notify(tr("Hesabın kaydedildi.", "Your account is saved."))
            dismiss()
        } catch { if (error as? ASAuthorizationError)?.code != .canceled { message = error.friendly } }
    }
}

extension AppModel {
    func refreshSession() async {
        if let s = try? await AuthService.shared.refreshSession() { session = s }
    }
}
