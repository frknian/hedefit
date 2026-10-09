import SwiftUI
import Observation

/// Uygulama teması: koyu/açık + ayarlanabilir vurgu tonu (Android `HedefitColors.applyTheme`).
@MainActor @Observable
final class Theme {
    static let shared = Theme()

    var dark = true
    var hue: Double = UserDefaults.standard.object(forKey: "accent_hue") as? Double ?? 106
    /// system | light | dark
    var appearance: String = UserDefaults.standard.string(forKey: "appearance") ?? "dark"

    func setHue(_ value: Double) { hue = min(max(value, 0), 360); UserDefaults.standard.set(hue, forKey: "accent_hue") }
    func setAppearance(_ value: String) { appearance = value; UserDefaults.standard.set(value, forKey: "appearance") }
    var preferredScheme: ColorScheme? { appearance == "system" ? nil : (appearance == "light" ? .light : .dark) }

    static let accentSaturation = 0.46

    private func hsl(_ h: Double, _ s: Double, _ l: Double) -> Color { Self.hsl(h, s, l) }
    static func hsl(_ h: Double, _ s: Double, _ l: Double) -> Color {
        // HSL → HSB
        let v = l + s * min(l, 1 - l)
        let sb = v == 0 ? 0 : 2 * (1 - l / v)
        return Color(hue: ((h.truncatingRemainder(dividingBy: 360)) + 360).truncatingRemainder(dividingBy: 360) / 360, saturation: sb, brightness: v)
    }

    var accent: Color { hsl(hue, Self.accentSaturation, dark ? 0.58 : 0.46) }
    var accentDark: Color { hsl(hue, Self.accentSaturation + 0.04, dark ? 0.42 : 0.36) }
    var onAccent: Color { (hue > 40 && hue < 200 || dark) ? Color(red: 0.02, green: 0.11, blue: 0.04) : .white }
    var background: Color { dark ? Color(hex: 0x090A0C) : Color(hex: 0xF4F6F8) }
    var surface: Color { dark ? Color(hex: 0x12151B) : .white }
    var surfaceHigh: Color { dark ? Color(hex: 0x1A1F27) : Color(hex: 0xEEF2F6) }
    var surfaceSoft: Color { dark ? Color(hex: 0x222934) : Color(hex: 0xE2E8F0) }
    var textPrimary: Color { dark ? Color(hex: 0xF8FAFC) : Color(hex: 0x0F172A) }
    var textSecondary: Color { dark ? Color(hex: 0x94A3B8) : Color(hex: 0x64748B) }
    var textMuted: Color { dark ? Color(hex: 0x79899F) : Color(hex: 0x607490) }
    var divider: Color { dark ? Color.white.opacity(0.09) : Color(hex: 0x0F172A).opacity(0.10) }
    var coral: Color { dark ? Color(hex: 0xFF6B6B) : Color(hex: 0xDC2626) }
    var water: Color { dark ? Color(hex: 0x38BDF8) : Color(hex: 0x0369A1) }
    var sleep: Color { dark ? Color(hex: 0xA78BFA) : Color(hex: 0x6D28D9) }
    var warning: Color { dark ? Color(hex: 0xFBBF24) : Color(hex: 0xB45309) }
}

extension Color {
    init(hex: UInt32, opacity: Double = 1) {
        self.init(.sRGB, red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255, blue: Double(hex & 0xFF) / 255, opacity: opacity)
    }
}

/// Kısa renk erişimi: `HC.lime`, `HC.surface` …
@MainActor
enum HC {
    private static var t: Theme { Theme.shared }
    static var lime: Color { t.accent }
    static var limeDark: Color { t.accentDark }
    static var onLime: Color { t.onAccent }
    static var bg: Color { t.background }
    static var surface: Color { t.surface }
    static var surfaceHigh: Color { t.surfaceHigh }
    static var surfaceSoft: Color { t.surfaceSoft }
    static var text: Color { t.textPrimary }
    static var textSecondary: Color { t.textSecondary }
    static var muted: Color { t.textMuted }
    static var divider: Color { t.divider }
    static var coral: Color { t.coral }
    static var water: Color { t.water }
    static var sleep: Color { t.sleep }
    static var warning: Color { t.warning }
}

enum Spacing { static let xs: CGFloat = 4, sm: CGFloat = 8, md: CGFloat = 12, lg: CGFloat = 16, xl: CGFloat = 20, xxl: CGFloat = 24, section: CGFloat = 32 }

extension Font {
    static func hf(_ size: CGFloat, _ weight: Font.Weight = .regular) -> Font { .system(size: size, weight: weight, design: .default) }
    static let hfTitle = Font.system(size: 26, weight: .heavy)
    static let hfHeadline = Font.system(size: 20, weight: .semibold)
    static let hfTitleL = Font.system(size: 17, weight: .bold)
    static let hfTitleM = Font.system(size: 16, weight: .semibold)
    static let hfBody = Font.system(size: 15)
    static let hfSmall = Font.system(size: 12)
    static let hfLabel = Font.system(size: 12, weight: .medium)
}
