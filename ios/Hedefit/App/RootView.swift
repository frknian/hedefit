import SwiftUI

struct RootView: View {
    @Environment(AppModel.self) private var app
    @Environment(Theme.self) private var theme
    @Environment(\.colorScheme) private var systemScheme
    @Environment(AppLang.self) private var lang

    var body: some View {
        ZStack {
            HC.bg.ignoresSafeArea()
            switch app.phase {
            case .loading: SplashView()
            case .configurationError(let message): ConfigurationErrorView(message: message)
            case .signedOut: AuthView()
            case .signedIn:
                if app.accountFrozen { FrozenAccountView() }
                else if app.consentRequired { ConsentGateView() }
                else if app.dashboard == nil && app.dataError == nil { SplashView() }
                else if app.dashboard == nil { LoadErrorView() }
                else { MainShell() }
            }
        }
        .onAppear { theme.dark = (theme.preferredScheme ?? systemScheme) == .dark }
        .onChange(of: systemScheme) { _, value in theme.dark = (theme.preferredScheme ?? value) == .dark }
        .onChange(of: theme.appearance) { _, _ in theme.dark = (theme.preferredScheme ?? systemScheme) == .dark }
        .id("\(lang.code)-\(theme.dark)-\(Int(theme.hue))")
        .overlay(alignment: .top) { ToastHost() }
    }
}

struct SplashView: View {
    var body: some View {
        VStack(spacing: 16) {
            Image("Logo").resizable().scaledToFit().frame(width: 96)
            ProgressView().tint(HC.lime)
        }
    }
}

struct ConfigurationErrorView: View {
    let message: String
    var body: some View {
        HfCard {
            VStack(alignment: .leading, spacing: 12) {
                Text(tr("iOS yapılandırması eksik", "iOS configuration missing")).font(.hfHeadline).foregroundStyle(HC.coral)
                Text(message).foregroundStyle(HC.text)
                Text(tr("ios/Config.xcconfig dosyasında NEXT_PUBLIC_SUPABASE_URL ve NEXT_PUBLIC_SUPABASE_ANON_KEY değerlerini tanımlayıp uygulamayı yeniden derle.", "Define NEXT_PUBLIC_SUPABASE_URL and NEXT_PUBLIC_SUPABASE_ANON_KEY in ios/Config.xcconfig and rebuild.")).font(.hfBody).foregroundStyle(HC.textSecondary)
            }
        }.padding(24)
    }
}

struct LoadErrorView: View {
    @Environment(AppModel.self) private var app
    var body: some View {
        VStack(spacing: 14) {
            HfEmptyState(icon: "wifi.exclamationmark", title: tr("Veriler yüklenemedi", "Couldn't load your data"), message: app.dataError)
            HfButton(title: tr("Tekrar dene", "Try again"), icon: "arrow.clockwise") { Task { await app.refreshAll() } }.padding(.horizontal, 40)
            Button(tr("Çıkış yap", "Sign out")) { Task { await app.signOut() } }.foregroundStyle(HC.textSecondary)
        }
    }
}

/// Üstten kayan geçici mesaj / hata.
struct ToastHost: View {
    @Environment(AppModel.self) private var app
    var body: some View {
        VStack(spacing: 8) {
            if let text = app.errorToast { bubble(text, HC.coral, "exclamationmark.triangle.fill") { app.errorToast = nil } }
            if let text = app.toast { bubble(text, HC.lime, "checkmark.circle.fill") { app.toast = nil } }
        }
        .padding(.horizontal, 16).padding(.top, 6).animation(.spring(duration: 0.3), value: app.toast).animation(.spring(duration: 0.3), value: app.errorToast)
        .allowsHitTesting(app.toast != nil || app.errorToast != nil)
    }

    private func bubble(_ text: String, _ color: Color, _ icon: String, dismiss: @escaping () -> Void) -> some View {
        HStack(spacing: 10) {
            Image(systemName: icon).foregroundStyle(color)
            Text(text).font(.system(size: 14, weight: .semibold)).foregroundStyle(HC.text).multilineTextAlignment(.leading)
            Spacer(minLength: 0)
        }
        .padding(14).background(HC.surfaceHigh, in: RoundedRectangle(cornerRadius: 16, style: .continuous)).overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(color.opacity(0.4)))
        .shadow(color: .black.opacity(0.25), radius: 12, y: 4).transition(.move(edge: .top).combined(with: .opacity)).onTapGesture(perform: dismiss)
        .task(id: text) { try? await Task.sleep(for: .seconds(3.6)); dismiss() }
    }
}
