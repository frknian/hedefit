import SwiftUI
import PhotosUI

/// Profil ve ayarlar (Android ProfileSettingsScreen).
struct SettingsView: View {
    @Environment(AppModel.self) private var app
    private var theme = Theme.shared
    @State private var name = ""
    @State private var age = ""
    @State private var height = ""
    @State private var weight = ""
    @State private var gender = ""
    @State private var error: String?
    @State private var avatarItem: PhotosPickerItem?
    @State private var avatarError: String?
    @State private var showReset = false
    @State private var showFreeze = false
    @State private var showDelete = false
    @State private var showUnits = false
    @State private var showRotation = false
    @State private var showShare = false
    @State private var showWidgets = false
    @State private var healthConnected = false

    var body: some View {
        let u = app.prefs.unitSystem
        let profile = app.dashboard?.profile
        let tier = app.tier
        ScreenScaffold(spacing: 14) {
            VStack(alignment: .leading, spacing: 2) {
                HfScreenHeader(title: tr("Profil ve ayarlar", "Profile and settings")) { if !app.path.isEmpty { app.path.removeLast() } }
            }
            HfCard(padding: 18) {
                HStack(spacing: 14) {
                    ZStack(alignment: .bottomTrailing) {
                        AvatarView(url: profile?.avatarURL, name: name, size: 64)
                        PhotosPicker(selection: $avatarItem, matching: .images) {
                            Image(systemName: "camera.fill").font(.system(size: 13)).foregroundStyle(HC.onLime).frame(width: 28, height: 28).background(HC.lime, in: Circle()).overlay(Circle().stroke(HC.surface, lineWidth: 2))
                        }.disabled(app.avatarUploading).offset(x: 6, y: 6).accessibilityLabel(tr("Profil fotoğrafını değiştir", "Change profile photo"))
                    }
                    VStack(alignment: .leading, spacing: 2) {
                        Text(localizedDisplayName(name)).font(.hfTitleL.weight(.heavy)).foregroundStyle(HC.text)
                        Text(app.session?.user.email ?? "").font(.hfSmall).foregroundStyle(HC.muted)
                        if app.avatarUploading { Text(tr("Fotoğraf yükleniyor…", "Uploading photo…")).font(.hfLabel).foregroundStyle(HC.lime) }
                        if let avatarError { Text(avatarError).font(.hfSmall).foregroundStyle(HC.coral) }
                    }
                    Spacer()
                }
            }
            if app.isGuest {
                HfCard(padding: 16, onTap: { app.showSaveAccount = true }) {
                    HStack(spacing: 12) {
                        Text("🛡️").font(.system(size: 26))
                        VStack(alignment: .leading, spacing: 2) { Text(tr("Hesabını kaydet", "Save your account")).font(.hfBody.weight(.bold)).foregroundStyle(HC.text); Text(tr("Misafir olarak kullanıyorsun. İlerlemen kaybolmasın diye hesabını kaydet.", "You're a guest. Save to keep your progress on any device.")).font(.hfSmall).foregroundStyle(HC.textSecondary).multilineTextAlignment(.leading) }
                        Spacer(); Image(systemName: "chevron.right").foregroundStyle(HC.lime)
                    }
                }
            } else {
                HfCard(padding: 16, onTap: { app.showPlans = true }) {
                    HStack(spacing: 12) {
                        Text("⭐").font(.system(size: 26))
                        VStack(alignment: .leading, spacing: 2) {
                            Text(tr("Paketin: ", "Your plan: ") + tier.label).font(.hfBody.weight(.bold)).foregroundStyle(HC.text)
                            Text(tier == .free ? tr("Plus ile sınırsız öğün ve günde 20 FitKoç sorusu.", "Plus: unlimited meals and 20 Fit Coach questions a day.") : tr("Paketini yönet veya yükselt.", "Manage or upgrade your plan.")).font(.hfSmall).foregroundStyle(HC.textSecondary).multilineTextAlignment(.leading)
                        }
                        Spacer(); Text(tier == .free ? tr("Plus'a geç", "Get Plus") : "›").font(.hfBody.weight(.black)).foregroundStyle(HC.lime)
                    }
                }.overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).stroke(HC.lime.opacity(0.6), lineWidth: 1.5))
            }
            HfSectionHeader(title: tr("Vücut ve profil", "Body and profile"))
            HfCard {
                VStack(spacing: 12) {
                    HfField(title: tr("Adın", "Name"), text: $name)
                    HStack(spacing: 10) {
                        HfField(title: tr("Boy", "Height") + " (\(Units.heightUnit(u)))", text: $height, keyboard: .decimalPad)
                        HfField(title: tr("Kilo", "Weight") + " (\(Units.weightUnit(u)))", text: $weight, keyboard: .decimalPad)
                    }
                    HStack(spacing: 10) {
                        HfField(title: tr("Yaş", "Age"), text: $age, keyboard: .numberPad)
                        HfField(title: tr("Cinsiyet", "Gender"), text: $gender)
                    }
                    if let error { Text(error).font(.hfSmall).foregroundStyle(HC.coral) }
                    HfButton(title: app.profileSaving ? tr("Kaydediliyor…", "Saving…") : tr("Profil değişikliklerini kaydet", "Save profile changes"), enabled: !app.profileSaving, action: save)
                }
            }
            HfNavRow(icon: "slider.horizontal.3", tint: HC.lime, title: tr("Hedef ve programını yenile", "Refresh your goal and program"), subtitle: tr("7 soruyu yeniden cevapla", "Answer the 7 questions again")) { app.push(.questionnaire) }
            HfSectionHeader(title: tr("Uygulama", "Application"))
            HfCard(padding: 14) {
                VStack(spacing: 0) {
                    HStack(spacing: 10) {
                        toggleChip(icon: theme.appearance == "light" ? "sun.max.fill" : theme.appearance == "system" ? "circle.lefthalf.filled" : "moon.fill",
                                   label: theme.appearance == "light" ? tr("Açık tema", "Light theme") : theme.appearance == "system" ? tr("Sistem", "System") : tr("Koyu tema", "Dark theme")) {
                            theme.setAppearance(theme.appearance == "dark" ? "light" : theme.appearance == "light" ? "system" : "dark")
                        }
                        toggleChip(icon: "globe", label: LangStore.english ? "English" : "Türkçe") { AppLang.shared.set(LangStore.english ? "tr" : "en") }
                    }.padding(.vertical, 10)
                    HfDivider()
                    accentPicker
                    HfDivider()
                    HfListRow(icon: "ruler", tint: HC.lime, title: tr("Ölçü birimleri", "Measurement units"), subtitle: u == "imperial" ? "Imperial • lb, in, mi, fl oz" : tr("Metrik", "Metric") + " • kg, cm, km, ml") { showUnits = true }
                    HfDivider()
                    HfListRow(icon: "arrow.triangle.2.circlepath", tint: HC.lime, title: tr("Program yenileme", "Program renewal"),
                              subtitle: app.prefs.planRotation == "weekly" ? tr("Her hafta • her Pazartesi yeni hareketler", "Every week • new exercises each Monday") : tr("Her ay • ayın 1'inde yeni hareketler", "Every month • new exercises on the 1st")) { showRotation = true }
                    HfDivider()
                    HfListRow(icon: "bell.fill", tint: HC.lime, title: tr("Bildirim takvimi", "Notification calendar")) { app.push(.notifications) }
                    HfDivider()
                    HfListRow(icon: "graduationcap.fill", tint: HC.lime, title: tr("Başlangıç rehberi", "Getting started guide")) { app.showWelcomeGuide = true }
                    HfDivider()
                    HfListRow(icon: "square.grid.2x2.fill", tint: HC.lime, title: tr("Ana ekran widget'ları", "Home screen widgets")) { showWidgets = true }
                    HfDivider()
                    HfListRow(icon: "applewatch", tint: HC.lime, title: tr("Akıllı saatler", "Smart watches")) { app.push(.wearables) }
                    HfDivider()
                    HfListRow(icon: "heart.fill", tint: healthConnected ? HC.lime : HC.textSecondary, title: tr("Apple Sağlık", "Apple Health"),
                              subtitle: healthConnected ? tr("Bağlı • adım, uyku, kilo ve kalori", "Connected • steps, sleep, weight and calories") : tr("Apple Sağlık verilerini bağla", "Connect Apple Health data")) {
                        Task { await app.syncHealth(); healthConnected = !(await HealthService.shared.needsAuthorization()) }
                    }
                }
            }
            HfSectionHeader(title: tr("Hedefit'i destekle", "Support Hedefit"))
            HfCard(padding: 14) {
                VStack(spacing: 0) {
                    HfListRow(icon: "star.fill", tint: HC.lime, title: tr("Uygulamayı puanla", "Rate the app")) { rateApp() }
                    HfDivider()
                    HfListRow(icon: "square.and.arrow.up", tint: HC.lime, title: tr("Uygulamayı paylaş", "Share the app")) { showShare = true }
                }
            }
            HfSectionHeader(title: tr("Hesap ve veri", "Account and data"))
            HfCard(padding: 14) {
                VStack(spacing: 0) {
                    HfListRow(icon: "checkmark.shield.fill", tint: HC.lime, title: tr("Gizlilik ve rızalar", "Privacy and consents"), subtitle: tr("Rıza durumu, yasal metinler, rızayı geri çek", "Consent status, legal texts, withdraw consent")) { app.push(.consents) }
                    HfDivider()
                    HfListRow(icon: "checkmark.shield.fill", tint: HC.lime, title: tr("Sağlık verisi ve kişiselleştirme", "Health data and personalization"), subtitle: tr("Uyarlama, isteğe bağlı döngü takibi, sağlık verisini sil", "Adaptation, optional cycle tracking, delete health data")) { app.push(.healthPrivacy) }
                    HfDivider()
                    HfListRow(icon: "brain.head.profile", tint: HC.lime, title: tr("Koç hafızası", "Coach memory"), subtitle: tr("FitKoç'un hatırladıklarını gör ve sil", "See and delete what Fit Coach remembers")) { app.push(.aiMemory) }
                    HfDivider()
                    HfListRow(icon: "rectangle.portrait.and.arrow.right", tint: HC.textSecondary, title: tr("Çıkış yap", "Sign out")) { Task { await app.signOut() } }
                    HfDivider()
                    HfListRow(icon: "pause.circle.fill", tint: HC.lime, title: tr("Hesabı dondur", "Freeze account")) { showFreeze = true }
                    HfDivider()
                    HfListRow(icon: "arrow.counterclockwise", tint: HC.lime, title: tr("İlerlemeyi sıfırla", "Reset progress"), titleColor: HC.coral) { showReset = true }
                    HfDivider()
                    HfListRow(icon: "trash.fill", tint: HC.coral, title: tr("Hesabı kalıcı sil", "Delete account permanently"), titleColor: HC.coral) { showDelete = true }
                }
            }
            Button(tr("Gizlilik Politikası", "Privacy Policy")) { app.push(.legal("privacy")) }.font(.hfSmall).foregroundStyle(HC.muted).frame(maxWidth: .infinity)
        }
        .onAppear(perform: fill)
        .onChange(of: u) { _, _ in fill() }
        .onChange(of: avatarItem) { _, item in Task { await uploadAvatar(item) } }
        .task { let needs = await HealthService.shared.needsAuthorization(); healthConnected = await HealthService.shared.isAvailable && !needs }
        .sheet(isPresented: $showUnits) { OptionSheet(title: tr("Ölçü birimleri", "Measurement units"), options: [("metric", tr("Metrik", "Metric"), "kg • cm • km • ml"), ("imperial", "Imperial", "lb • in • mi • fl oz")], selected: app.prefs.unitSystem) { app.prefs.unitSystem = $0 } }
        .sheet(isPresented: $showRotation) { OptionSheet(title: tr("Program yenileme", "Program renewal"), message: tr("Hedefit ne sıklıkla yeni bir hareket bloğu önersin? Ana hareketlerin ilerlemen için aynı kalır; yardımcı hareketler döner.", "How often should Hedefit offer a fresh block of exercises? Your main lifts stay the same so you keep progressing; accessories rotate."), options: [("weekly", tr("Haftalık", "Weekly"), tr("Her Pazartesi yeni blok", "A new block every Monday")), ("monthly", tr("Aylık", "Monthly"), tr("Her ayın 1'inde yeni blok", "A new block on the 1st of each month"))], selected: app.prefs.planRotation) { app.prefs.planRotation = $0 } }
        .sheet(isPresented: $showShare) { ShareAppSheet() }
        .sheet(isPresented: $showWidgets) { HomeWidgetsSheet() }
        .sheet(isPresented: $showReset) { ConfirmSheet(title: tr("İlerleme verilerini sil", "Delete progress data"), message: tr("Antrenman, ölçüm, kalori ve seri kayıtların kalıcı olarak silinecek.", "Your workouts, measurements, calories and streak records will be permanently deleted."), action: tr("Sıfırla", "Reset"), busy: app.accountBusy) { await app.resetProgress() } }
        .sheet(isPresented: $showFreeze) { ConfirmSheet(title: tr("Hesabı dondur", "Freeze account"), message: tr("Verilerin korunacak. Yeniden etkinleştirene kadar uygulama erişimin duracak.", "Your data will remain. App access will pause until you reactivate."), action: tr("Dondur", "Freeze"), busy: app.accountBusy) { await app.freezeAccount() } }
        .sheet(isPresented: $showDelete) { DeleteAccountSheet(email: app.session?.user.email ?? "", hasSubscription: tier == .plus || tier == .premium) }
    }

    // MARK: Parçalar

    private func toggleChip(icon: String, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 8) { Image(systemName: icon).foregroundStyle(HC.lime); Text(label).font(.hfBody.weight(.bold)).foregroundStyle(HC.text) }
                .frame(maxWidth: .infinity, minHeight: 48).background(HC.surfaceHigh, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        }.buttonStyle(.plain)
    }

    private var accentPicker: some View {
        let presets: [Double] = [0, 22, 42, 72, 106, 145, 172, 195, 215, 240, 275, 325]
        return VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 12) {
                Image(systemName: "paintpalette.fill").font(.system(size: 18)).foregroundStyle(HC.onLime).frame(width: 38, height: 38).background(HC.lime, in: Circle())
                Text(tr("Uygulama rengi", "Application colour")).font(.hfTitleM).foregroundStyle(HC.text); Spacer()
                Button(tr("Sıfırla", "Reset")) { theme.setHue(106) }.foregroundStyle(HC.lime)
            }
            ScrollView(.horizontal) {
                HStack(spacing: 10) {
                    ForEach(presets, id: \.self) { h in
                        let selected = abs(theme.hue - h) < 3
                        Button { theme.setHue(h) } label: {
                            Circle().fill(Theme.hsl(h, Theme.accentSaturation, theme.dark ? 0.58 : 0.46)).frame(width: 36, height: 36)
                                .overlay { if selected { Circle().stroke(HC.text, lineWidth: 3); Image(systemName: "checkmark").font(.system(size: 14, weight: .bold)).foregroundStyle(.white) } }
                        }.buttonStyle(.plain)
                    }
                }.padding(.horizontal, 2).padding(.vertical, 4)
            }.scrollIndicators(.hidden)
        }.padding(.vertical, 10)
    }

    // MARK: İşlemler

    private func fill() {
        guard let p = app.dashboard?.profile else { return }
        let u = app.prefs.unitSystem
        name = p.displayName; age = p.age.map(String.init) ?? ""; gender = p.gender
        height = p.heightCm.map { String(format: "%.1f", Units.heightValue($0, u)) } ?? ""
        weight = p.weightKg.map { String(format: "%.1f", Units.weightValue($0, u)) } ?? ""
    }

    private func save() {
        let u = app.prefs.unitSystem
        let dec = { (s: String) in Double(s.replacingOccurrences(of: ",", with: ".")) }
        let heightCm = dec(height).map { Units.heightToCm($0, u) }, weightKg = dec(weight).map { Units.weightToKg($0, u) }
        let n = name.trimmingCharacters(in: .whitespaces)
        if n.count < 2 || n.count > 60 { error = tr("Ad 2 ile 60 karakter arasında olmalı.", "Name must be between 2 and 60 characters."); return }
        if !age.isEmpty, !(Int(age).map { (13...100).contains($0) } ?? false) { error = tr("Yaş 13 ile 100 arasında olmalı.", "Age must be between 13 and 100."); return }
        if !height.isEmpty, !(heightCm.map { (100...250).contains($0) } ?? false) { error = tr("Boy 100 ile 250 cm arasında olmalı.", "Height must be between 100 and 250 cm."); return }
        if !weight.isEmpty, !(weightKg.map { (20...400).contains($0) } ?? false) { error = tr("Kilo 20 ile 400 kg arasında olmalı.", "Weight must be between 20 and 400 kg."); return }
        if gender.trimmingCharacters(in: .whitespaces).count > 40 { error = tr("Cinsiyet en fazla 40 karakter olabilir.", "Gender can be at most 40 characters."); return }
        error = nil
        guard let p = app.dashboard?.profile else { return }
        let update = ProfileUpdate(displayName: n, age: Int(age), gender: gender.trimmingCharacters(in: .whitespaces), heightCm: heightCm, weightKg: weightKg, goalType: p.goal, targetWeightKg: p.targetWeightKg, targetWeeks: p.targetWeeks, environment: p.environment, equipment: p.equipment, historyAnswers: p.historyAnswers)
        Task { _ = await app.saveProfile(update) }
    }

    private func uploadAvatar(_ item: PhotosPickerItem?) async {
        guard let item else { return }
        defer { avatarItem = nil }
        guard let image = await PhotoLoader.image(from: item), let data = image.squareCropped(1024).jpegData(compressionQuality: 0.9), data.count <= 5 * 1024 * 1024 else {
            avatarError = tr("Geçerli bir JPG, PNG veya WebP görsel seç.", "Choose a valid JPG, PNG, or WebP image."); return
        }
        avatarError = nil
        await app.uploadAvatar(data, mime: "image/jpeg")
    }

    private func rateApp() {
        guard let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene else { return }
        SKStoreReviewController.requestReview(in: scene)
    }
}

import StoreKit

extension UIImage {
    /// Ortadan kare kırpar ve `side`'dan büyükse küçültür.
    func squareCropped(_ side: CGFloat) -> UIImage {
        let s = min(size.width, size.height)
        let rect = CGRect(x: (size.width - s) / 2, y: (size.height - s) / 2, width: s, height: s)
        let target = min(s, side)
        let format = UIGraphicsImageRendererFormat(); format.scale = 1
        return UIGraphicsImageRenderer(size: CGSize(width: target, height: target), format: format).image { _ in
            draw(in: CGRect(x: -rect.minX * target / s, y: -rect.minY * target / s, width: size.width * target / s, height: size.height * target / s))
        }
    }
}

// MARK: - Diyaloglar

struct OptionSheet: View {
    @Environment(\.dismiss) private var dismiss
    let title: String
    var message: String? = nil
    let options: [(String, String, String)]
    let selected: String
    var onSelect: (String) -> Void
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title).font(.hfHeadline.weight(.heavy)).foregroundStyle(HC.text)
            if let message { Text(message).font(.hfBody).foregroundStyle(HC.textSecondary) }
            ForEach(options, id: \.0) { key, t, sub in
                HfCard(padding: 14, onTap: { onSelect(key); dismiss() }) {
                    HStack(spacing: 12) {
                        Image(systemName: selected == key ? "largecircle.fill.circle" : "circle").foregroundStyle(selected == key ? HC.lime : HC.textSecondary)
                        VStack(alignment: .leading) { Text(t).font(.hfTitleM).foregroundStyle(HC.text); Text(sub).font(.hfSmall).foregroundStyle(HC.textSecondary) }
                        Spacer()
                    }
                }
            }
            Spacer()
        }.padding(24).frame(maxWidth: .infinity, alignment: .leading).background(HC.bg).presentationDetents([.medium])
    }
}

struct ConfirmSheet: View {
    @Environment(\.dismiss) private var dismiss
    let title: String, message: String, action: String
    var busy = false
    var onConfirm: () async -> Void
    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(title).font(.hfHeadline.weight(.heavy)).foregroundStyle(HC.text)
            Text(message).foregroundStyle(HC.textSecondary)
            Spacer()
            HfButton(title: busy ? tr("İşleniyor…", "Processing…") : action, enabled: !busy) { Task { await onConfirm(); dismiss() } }
            HfButton(title: tr("Vazgeç", "Cancel"), secondary: true) { dismiss() }
        }.padding(24).background(HC.bg).presentationDetents([.height(300)])
    }
}

struct DeleteAccountSheet: View {
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss
    let email: String
    let hasSubscription: Bool
    @State private var typedEmail = ""
    @State private var phrase = ""
    @State private var ack = false
    var body: some View {
        let expected = tr("HESABIMI SİL", "DELETE MY ACCOUNT")
        let ready = typedEmail.trimmingCharacters(in: .whitespaces).caseInsensitiveCompare(email) == .orderedSame && phrase == expected && (!hasSubscription || ack)
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text(tr("Hesabı kalıcı olarak sil", "Permanently delete account")).font(.hfHeadline.weight(.heavy)).foregroundStyle(HC.text)
                Text(tr("Profilin, antrenmanların ve tüm kayıtların geri alınamaz biçimde silinir.", "Your profile, workouts and all records will be deleted irreversibly.")).foregroundStyle(HC.textSecondary)
                if hasSubscription {
                    Text(tr("Hesabı silmek App Store aboneliğini İPTAL ETMEZ. Önce Ayarlar > Apple Kimliği > Abonelikler'den iptal et, yoksa ücretlendirme sürer.", "Deleting your account does NOT cancel your App Store subscription. Cancel it first in Settings > Apple ID > Subscriptions or you will keep being charged.")).foregroundStyle(HC.coral)
                    Toggle(tr("Anladım, App Store'dan iptal edeceğim", "I understand and will cancel it in the App Store"), isOn: $ack).tint(HC.lime).foregroundStyle(HC.text)
                }
                HfField(title: tr("E-posta adresin", "Your email address"), text: $typedEmail, keyboard: .emailAddress)
                HfField(title: tr("HESABIMI SİL yaz", "Type DELETE MY ACCOUNT"), text: $phrase)
                HfButton(title: app.accountBusy ? tr("Siliniyor…", "Deleting…") : tr("Kalıcı olarak sil", "Delete permanently"), enabled: ready && !app.accountBusy) { Task { await app.deleteAccount(email: typedEmail); dismiss() } }
                HfButton(title: tr("Vazgeç", "Cancel"), secondary: true) { dismiss() }
            }.padding(24)
        }.background(HC.bg).presentationDetents([.large])
    }
}

struct ShareAppSheet: View {
    @Environment(\.dismiss) private var dismiss
    @State private var message = tr("Hedefit ile kişisel antrenman programı, beslenme takibi ve AI koçu ücretsiz deniyorum. Sen de dene: https://hedefit.app", "I'm trying a personal training plan, meal tracking and an AI coach for free with Hedefit. Try it too: https://hedefit.app")
    @State private var sharing = false
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(tr("Hedefit'i paylaş", "Share Hedefit")).font(.hfHeadline.weight(.heavy)).foregroundStyle(HC.text)
            Text(tr("Arkadaşların kişisel antrenman programı, beslenme takibi ve AI koçu ücretsiz deneyebilir. Mesajı dilediğin gibi düzenle.", "Your friends get a personal training plan, meal tracking and an AI coach for free. Edit the message as you like.")).font(.hfSmall).foregroundStyle(HC.textSecondary)
            TextEditor(text: Binding(get: { message }, set: { message = String($0.prefix(500)) })).scrollContentBackground(.hidden).foregroundStyle(HC.text).frame(height: 130).padding(10).background(HC.surfaceHigh, in: RoundedRectangle(cornerRadius: 14))
            HfButton(title: tr("Paylaş", "Share"), enabled: !message.trimmingCharacters(in: .whitespaces).isEmpty) { sharing = true }
            Spacer()
        }.padding(24).background(HC.bg).presentationDetents([.medium])
        .sheet(isPresented: $sharing, onDismiss: { dismiss() }) { ActivityView(items: [message]) }
    }
}

struct ActivityView: UIViewControllerRepresentable {
    let items: [Any]
    func makeUIViewController(context: Context) -> UIActivityViewController { UIActivityViewController(activityItems: items, applicationActivities: nil) }
    func updateUIViewController(_ vc: UIActivityViewController, context: Context) {}
}

struct HomeWidgetsSheet: View {
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Text(tr("Ana ekranına nasıl eklenir?", "How to add to your Home Screen")).font(.hfTitleL.weight(.bold)).foregroundStyle(HC.text)
                    ForEach(Array([tr("Ana ekranda boş bir yere uzun bas.", "Touch and hold an empty area of the Home Screen."), tr("Sol üstteki + düğmesine dokun.", "Tap the + button at the top left."), tr("Hedefit'i ara ve bir widget seç.", "Search for Hedefit and pick a widget."), tr("Widget'ı ekle; veriler uygulamayı her açtığında güncellenir.", "Add it; data refreshes whenever you use the app.")].enumerated()), id: \.offset) { i, s in
                        HStack(alignment: .top, spacing: 12) { Text("\(i + 1)").font(.hfBody.weight(.heavy)).foregroundStyle(HC.onLime).frame(width: 26, height: 26).background(HC.lime, in: Circle()); Text(s).foregroundStyle(HC.text) }
                    }
                    HfSectionHeader(title: tr("Mevcut widget'lar", "Available widgets"))
                    ForEach([(tr("Bugün", "Today"), "house.fill"), (tr("Aktivite", "Activity"), "figure.walk"), (tr("Antrenman", "Workout"), "dumbbell.fill"), (tr("Kalori", "Calories"), "flame.fill"), (tr("Kilit ekranı", "Lock Screen"), "lock.fill")], id: \.0) { n, i in
                        HfListRow(icon: i, tint: HC.lime, title: n, chevron: false, action: nil)
                    }
                }.padding(20)
            }.background(HC.bg).navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button(tr("Kapat", "Close")) { dismiss() } } }
        }
    }
}
