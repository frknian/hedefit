import SwiftUI
import AuthenticationServices

struct AuthView: View {
    @Environment(AppModel.self) private var app
    @State private var mode = 0 // 0 giriş, 1 kayıt
    @State private var email = ""
    @State private var password = ""
    @State private var passwordAgain = ""
    @State private var username = ""
    @State private var usernameStatus: String?
    @State private var kvkk = false
    @State private var privacy = false
    @State private var health = false
    @State private var crossBorder = false
    @State private var legalPage: LegalDocument?
    @State private var forgotSent = false

    private var legal: LegalAcceptance { LegalAcceptance(kvkkNotice: kvkk, privacyPolicy: privacy, healthData: health, crossBorder: crossBorder) }
    private var allLegal: Bool { kvkk && privacy && health && crossBorder }

    var body: some View {
        ScrollView {
            VStack(spacing: 18) {
                Image("Logo").resizable().scaledToFit().frame(width: 84).padding(.top, 28)
                Text(tr("Hedefine bir adım daha yakın", "One step closer to your goal")).font(.hfHeadline.weight(.heavy)).foregroundStyle(HC.text).multilineTextAlignment(.center)
                HfSegmented(options: [tr("Giriş yap", "Sign in"), tr("Kayıt ol", "Sign up")], selection: $mode)

                VStack(spacing: 12) {
                    HfField(title: tr("E-posta", "Email"), text: $email, keyboard: .emailAddress)
                    if mode == 1 { usernameField }
                    HfField(title: tr("Parola", "Password"), text: $password, secure: true)
                    if mode == 1 { HfField(title: tr("Parola (tekrar)", "Password (again)"), text: $passwordAgain, secure: true) }
                    if let hint = formHint { Text(hint).font(.hfSmall).foregroundStyle(HC.coral).frame(maxWidth: .infinity, alignment: .leading) }
                }

                if mode == 1 { legalBlock }

                if let message = app.authMessage { HfBanner(text: message) }

                HfButton(title: mode == 0 ? tr("Giriş yap", "Sign in") : tr("Hesap oluştur", "Create account"), loading: app.authBusy, enabled: canSubmit) { Task { await submit() } }
                if mode == 0 { Button(tr("Parolamı unuttum", "Forgot password")) { Task { await forgot() } }.font(.hfLabel.weight(.bold)).foregroundStyle(HC.textSecondary) }
                if forgotSent { Text(tr("Eğer bu e-posta kayıtlıysa sıfırlama bağlantısı gönderildi.", "If this email is registered, a reset link was sent.")).font(.hfSmall).foregroundStyle(HC.lime) }

                HStack { HfDivider(); Text(tr("veya", "or")).font(.hfSmall).foregroundStyle(HC.muted); HfDivider() }

                Button { Task { await social(apple: true) } } label: {
                    HStack(spacing: 10) { Image(systemName: "apple.logo"); Text(tr("Apple ile devam et", "Continue with Apple")).font(.system(size: 16, weight: .semibold)) }
                        .foregroundStyle(Theme.shared.dark ? Color.black : Color.white).frame(maxWidth: .infinity, minHeight: 50).background(Theme.shared.dark ? Color.white : Color.black, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                }.buttonStyle(PressableStyle())
                Button { Task { await social(apple: false) } } label: {
                    HStack(spacing: 10) { Image(systemName: "g.circle.fill"); Text(tr("Google ile devam et", "Continue with Google")).font(.system(size: 16, weight: .semibold)) }
                        .foregroundStyle(HC.text).frame(maxWidth: .infinity, minHeight: 50).background(HC.surfaceHigh, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                }.buttonStyle(PressableStyle())

                Button { Task { await guest() } } label: {
                    VStack(spacing: 2) {
                        Text(tr("Üye olmadan dene", "Try without an account")).font(.system(size: 15, weight: .bold)).foregroundStyle(HC.lime)
                        Text(tr("Misafir olarak başla; verilerin kaybolmadan sonra hesap oluşturabilirsin.", "Start as a guest; create an account later without losing your data.")).font(.hfSmall).foregroundStyle(HC.muted).multilineTextAlignment(.center)
                    }.padding(.vertical, 6)
                }
                if mode == 0 { legalFooter }
            }.padding(.horizontal, 22).padding(.bottom, 30)
        }
        .scrollDismissesKeyboard(.interactively).scrollIndicators(.hidden)
        .sheet(item: $legalPage) { doc in LegalSheet(document: doc) }
        .onChange(of: username) { _, value in Task { await checkUsername(value) } }
        .onChange(of: mode) { _, _ in app.authMessage = nil }
    }

    // MARK: Parçalar

    private var usernameField: some View {
        VStack(alignment: .leading, spacing: 6) {
            HfField(title: tr("Kullanıcı adı", "Username"), text: Binding(get: { username }, set: { username = String($0.lowercased().filter { $0.isLetter || $0.isNumber || $0 == "." || $0 == "_" }.prefix(20)) }), keyboard: .asciiCapable)
            if let status = usernameStatusText(usernameStatus) { Text(status.0).font(.hfSmall).foregroundStyle(status.1 ? HC.lime : HC.coral) }
        }
    }

    private var legalBlock: some View {
        VStack(alignment: .leading, spacing: 10) {
            check($kvkk, tr("KVKK Aydınlatma Metni'ni okudum.", "I've read the KVKK notice."), .kvkk)
            check($privacy, tr("Gizlilik Politikası'nı kabul ediyorum.", "I accept the Privacy Policy."), .privacy)
            check($health, tr("Sağlık verilerimin işlenmesine açık rıza veriyorum.", "I explicitly consent to the processing of my health data."), .healthConsent)
            check($crossBorder, tr("Verilerimin yurt dışına aktarılmasına açık rıza veriyorum.", "I explicitly consent to cross-border transfer of my data."), .crossBorderConsent)
            Button(allLegal ? tr("Tümünü kaldır", "Clear all") : tr("Tümünü onayla", "Accept all")) { let v = !allLegal; kvkk = v; privacy = v; health = v; crossBorder = v }
                .font(.hfLabel.weight(.bold)).foregroundStyle(HC.lime)
        }.padding(14).background(HC.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    private func check(_ binding: Binding<Bool>, _ text: String, _ doc: LegalDocument) -> some View {
        HStack(alignment: .top, spacing: 10) {
            Button { binding.wrappedValue.toggle() } label: {
                Image(systemName: binding.wrappedValue ? "checkmark.square.fill" : "square").font(.system(size: 22)).foregroundStyle(binding.wrappedValue ? HC.lime : HC.muted)
            }.buttonStyle(.plain)
            Button { legalPage = doc } label: { Text(text).font(.hfSmall).foregroundStyle(HC.textSecondary).multilineTextAlignment(.leading).underline() }.buttonStyle(.plain)
            Spacer(minLength: 0)
        }
    }

    private var legalFooter: some View {
        HStack(spacing: 14) {
            Button(tr("KVKK", "KVKK")) { legalPage = .kvkk }
            Button(tr("Gizlilik", "Privacy")) { legalPage = .privacy }
            Button(tr("Koşullar", "Terms")) { legalPage = .terms }
        }.font(.hfSmall.weight(.semibold)).foregroundStyle(HC.muted)
    }

    // MARK: Mantık

    private var formHint: String? {
        if !email.isEmpty && !Validation.isEmail(email) { return tr("Geçerli bir e-posta adresi yaz.", "Enter a valid email address.") }
        if !password.isEmpty && password.count < 8 { return tr("Parola en az 8 karakter olmalı.", "Password must be at least 8 characters.") }
        if mode == 1 && !passwordAgain.isEmpty && password != passwordAgain { return tr("Parolalar eşleşmiyor.", "Passwords don't match.") }
        return nil
    }

    private var canSubmit: Bool {
        if !Validation.isEmail(email) || password.count < 8 { return false }
        if mode == 0 { return true }
        return password == passwordAgain && usernameStatus == "ok" && allLegal
    }

    private func submit() async {
        if mode == 0 { await app.signIn(email: email, password: password) }
        else { await app.signUp(email: email, password: password, username: username, legal: legal) }
    }

    private func guest() async {
        guard allLegal else {
            mode = 1; app.fail(AppError.message(tr("Misafir olarak devam etmek için yasal onayları işaretle.", "Accept the legal consents below to continue as a guest."))); return
        }
        await app.continueAsGuest(legal: legal)
    }

    private func social(apple: Bool) async {
        if mode == 1 && !allLegal { app.fail(AppError.message(tr("Kayıt için yasal onayları işaretle.", "Accept the legal consents to sign up."))); return }
        do {
            let credential = apple ? try await AppleAuthService.signIn() : try await GoogleAuthService.signIn()
            await app.signInWithProvider(apple ? "apple" : "google", idToken: credential.idToken, nonce: credential.nonce, legal: mode == 1 ? legal : nil)
        } catch { app.fail(error) }
    }

    private func forgot() async {
        guard Validation.isEmail(email) else { app.fail(AppError.message(tr("Önce e-posta adresini yaz.", "Enter your email first."))); return }
        _ = try? await HTTP.request("\(AppConfiguration.supabaseURL)/auth/v1/recover", method: "POST", headers: ["apikey": AppConfiguration.anonKey], body: ["email": JSON(email.trimmingCharacters(in: .whitespaces))].data())
        forgotSent = true
    }

    private func checkUsername(_ value: String) async {
        usernameStatus = nil
        if value.isEmpty { return }
        if let local = localUsernameStatus(value) { usernameStatus = local; return }
        try? await Task.sleep(for: .milliseconds(450))
        guard !Task.isCancelled, value == username else { return }
        usernameStatus = "checking"
        let result = (try? await AuthService.shared.checkUsername(value)) ?? "error"
        if value == username { usernameStatus = result }
    }
}

func localUsernameStatus(_ username: String) -> String? {
    if username.isEmpty { return "empty" }
    if username.count < 3 { return "too_short" }
    if username.range(of: "^[a-z0-9._]{3,20}$", options: .regularExpression) == nil { return "invalid" }
    return nil
}

@MainActor func usernameStatusText(_ status: String?) -> (String, Bool)? {
    switch status {
    case nil: return nil
    case "ok": return (tr("Bu kullanıcı adı kullanılabilir.", "This username is available."), true)
    case "checking": return (tr("Kontrol ediliyor…", "Checking…"), true)
    case "empty": return (tr("Bir kullanıcı adı seç.", "Pick a username."), false)
    case "too_short": return (tr("Kullanıcı adı en az 3 karakter olmalı.", "Username must be at least 3 characters."), false)
    case "invalid": return (tr("Yalnızca küçük harf, rakam, nokta ve alt çizgi kullanabilirsin.", "Use only lowercase letters, digits, dots and underscores."), false)
    case "taken": return (tr("Bu kullanıcı adı alınmış.", "This username is taken."), false)
    case "blocked": return (tr("Bu kullanıcı adı uygun değil.", "This username isn't allowed."), false)
    default: return (tr("Kullanıcı adı şu an kontrol edilemedi.", "Couldn't check the username right now."), false)
    }
}
