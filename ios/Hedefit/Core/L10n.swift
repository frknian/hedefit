import SwiftUI
import Observation

/// Eşzamanlılıktan bağımsız dil bayrağı (arka plan görevleri de `tr()` çağırabilsin).
enum LangStore {
    nonisolated(unsafe) static var english: Bool = {
        if let saved = UserDefaults.standard.string(forKey: "language") { return saved == "en" }
        return !(Locale.preferredLanguages.first?.hasPrefix("tr") ?? false)
    }()
}

/// Uygulama dili. Yeni metinlerde `tr("…", "…")` kullanılır; Android'deki `tr()` ile birebir.
/// Dil değişince kök görünüm `.id(...)` ile yeniden kurulur.
@MainActor @Observable
final class AppLang {
    static let shared = AppLang()
    private(set) var en: Bool

    private init() {
        if let saved = UserDefaults.standard.string(forKey: "language") { en = saved == "en" }
        else { en = !(Locale.preferredLanguages.first?.hasPrefix("tr") ?? false) }
    }

    var code: String { en ? "en" : "tr" }
    var locale: Locale { Locale(identifier: en ? "en_US" : "tr_TR") }

    func set(_ code: String) {
        en = code == "en"
        LangStore.english = en
        UserDefaults.standard.set(code, forKey: "language")
    }
}

/// Türkçe / İngilizce metin seçer. SwiftUI gövdesinde çağrıldığında dil değişince görünüm yenilenir.
func tr(_ turkish: String, _ english: String) -> String { LangStore.english ? english : turkish }

/// Eşzamanlı olmayan bağlamlar (actor/arka plan) için dil kodu.
var currentLanguageCode: String { LangStore.english ? "en" : "tr" }
var currentLanguageIsEnglish: Bool { LangStore.english }
func trNow(_ turkish: String, _ english: String) -> String { tr(turkish, english) }

extension String {
    /// Türkçede i → İ olacak şekilde büyük harf.
    @MainActor var upperLocalized: String { uppercased(with: AppLang.shared.locale) }
    func upper(_ locale: Locale) -> String { uppercased(with: locale) }
    var firstUppercased: String { prefix(1).uppercased() + dropFirst() }
}

/// Varsayılan misafir adı dile göre gösterilir; kullanıcının kendi adı olduğu gibi kalır.
@MainActor func localizedDisplayName(_ name: String?) -> String {
    guard let name, !name.isEmpty, name != "Sporcu", name != "Athlete" else { return tr("Sporcu", "Athlete") }
    return name
}

private let quickTR = "Hızlı Antrenman: ", quickEN = "Quick Workout: "

@MainActor func isQuickWorkoutName(_ name: String?) -> Bool { name.map { $0.hasPrefix(quickTR) || $0.hasPrefix(quickEN) } ?? false }

@MainActor func localizedQuickWorkoutName(_ name: String) -> String {
    let rest = name.replacingOccurrences(of: quickTR, with: "").replacingOccurrences(of: quickEN, with: "")
    let areas = rest.components(separatedBy: " & ").map { localizedArea($0.trimmingCharacters(in: .whitespaces)) }.joined(separator: " & ")
    return tr(quickTR, quickEN) + areas
}

/// Uygulamanın kendi ürettiği program adları veritabanına Türkçe yazılır; ekranda dile göre gösterilir.
@MainActor func localizedProgramName(_ name: String?, source: String? = nil) -> String {
    if source == "assessment" || name == "Kişisel Atlas Programım" || name == "Personal Atlas Program" { return tr("Kişisel Atlas Programım", "Personal Atlas Program") }
    if name == "Kendi Programım" || name == "My Program" { return tr("Kendi Programım", "My Program") }
    if name == "Fit Koç Programı" || name == "Fit Coach Program" { return tr("Fit Koç Programı", "Fit Coach Program") }
    if name == "Antrenman" || name == "Workout" { return tr("Antrenman", "Workout") }
    if isQuickWorkoutName(name) { return localizedQuickWorkoutName(name!) }
    guard let name, !name.isEmpty else { return tr("Antrenman", "Workout") }
    return name
}

private let areaPairs: [(String, String)] = [
    ("Göğüs", "Chest"), ("Sırt", "Back"), ("Kanat", "Lats"), ("Bacak", "Legs"), ("Kalça", "Glutes"), ("Omuz", "Shoulders"),
    ("Kol", "Arms"), ("Ön kol", "Biceps"), ("Arka kol", "Triceps"), ("Karın", "Core"), ("Tüm Vücut", "Full body"), ("Kardiyo", "Cardio"),
    ("Ön bacak", "Quads"), ("Arka bacak", "Hamstrings"), ("Baldır", "Calves"), ("Trapez", "Traps"), ("Bel", "Lower back"),
]

/// Program/hareket bölge etiketlerini dile göre gösterir (Türkçe ya da İngilizce kaynak).
@MainActor func localizedArea(_ area: String) -> String {
    let lower = area.lowercased()
    if let pair = areaPairs.first(where: { $0.0.lowercased() == lower || $0.1.lowercased() == lower }) { return tr(pair.0, pair.1) }
    switch lower {
    case "quadriceps": return tr("Ön bacak", "Quads")
    case "biceps": return tr("Ön kol", "Biceps")
    case "triceps": return tr("Arka kol", "Triceps")
    case "abdominals", "abs": return tr("Karın", "Core")
    case "lower back": return tr("Bel", "Lower back")
    case "middle back": return tr("Sırt", "Back")
    default: return area.firstUppercased
    }
}
