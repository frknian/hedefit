import Foundation

/// Hesap katmanları: Misafir → Free → Plus / Premium (sunucudaki plan_tier: free | plus | pro).
/// Sunucu kotaları lib/usage-limits.ts ve lib/entitlements.ts içindedir; ikisini birlikte güncelle.
enum Tier: Int, Sendable, Comparable {
    case guest, free, plus, premium
    static func < (a: Tier, b: Tier) -> Bool { a.rawValue < b.rawValue }
    @MainActor var label: String { switch self { case .guest: return tr("Misafir", "Guest"); case .free: return "Free"; case .plus: return "Plus"; case .premium: return "Premium" } }
}

/// Kısıtlanabilen her özellik; kilit ekranı metni buna göre seçilir.
enum LockedFeature: String, Identifiable, Sendable {
    case mealLogs, photoMeal, exercise, customProgram, route, mealPlanner, regionalPlan, history, cardioPrograms, cardioGame, wellnessContent, barre, adaptiveAction, pilatesSwitch, cycleAdaptation, aiAdaptiveCoach
    var id: String { rawValue }

    var emoji: String {
        switch self {
        case .mealLogs: return "🍽️"; case .photoMeal: return "📸"; case .exercise: return "🔒"; case .customProgram: return "🗂️"; case .route: return "🗺️"; case .mealPlanner: return "📅"
        case .regionalPlan: return "🎯"; case .history: return "📈"; case .cardioPrograms: return "🏃"; case .cardioGame: return "🎮"; case .wellnessContent: return "🧘"; case .barre: return "🩰"
        case .adaptiveAction: return "🔄"; case .pilatesSwitch: return "✨"; case .cycleAdaptation: return "🌙"; case .aiAdaptiveCoach: return "🤖"
        }
    }

    @MainActor var title: String {
        switch self {
        case .mealLogs: return tr("Bugünkü öğün hakkın doldu", "Daily meal limit reached")
        case .photoMeal: return tr("Fotoğraftan kalori", "Photo calorie scan")
        case .exercise: return tr("Bu hareket kilitli", "This exercise is locked")
        case .customProgram: return tr("Kendi programını oluştur", "Create your own program")
        case .route: return tr("Rotanı kaydet", "Save your route")
        case .mealPlanner: return tr("Haftalık öğün planlayıcı", "Weekly meal planner")
        case .regionalPlan: return tr("Bölgesel program", "Regional program")
        case .history: return tr("Geçmiş ilerleme", "Past progress")
        case .cardioPrograms: return tr("Hazır kardiyo programları", "Ready cardio programs")
        case .cardioGame: return tr("Kardiyo oyun modu", "Cardio game mode")
        case .wellnessContent: return tr("Pilates ve mobilite içeriği", "Pilates & mobility content")
        case .barre: return "Barre"
        case .adaptiveAction: return tr("Gelişmiş adaptasyon", "Advanced adaptation")
        case .pilatesSwitch: return tr("Pilates oturumuna geç", "Switch to a Pilates session")
        case .cycleAdaptation: return tr("Döngüye göre uyarlama", "Cycle-aware adaptation")
        case .aiAdaptiveCoach: return tr("Koçtan kişisel açıklama", "Personal coach explanation")
        }
    }

    @MainActor var body: String {
        switch self {
        case .mealLogs: return tr("Günlük öğün kaydı sınırına ulaştın. Sınırsız takip için devam et.", "You reached your daily meal logging limit. Continue for unlimited tracking.")
        case .photoMeal: return tr("Fotoğraftan kalori hesaplama hakkın doldu.", "Your photo calorie scans for today are used up.")
        case .exercise: return tr("Orta ve ileri seviye hareketler daha geniş erişimle açılır.", "Intermediate and advanced exercises unlock with wider access.")
        case .customProgram: return tr("Kendi program sınırına ulaştın.", "You reached your custom program limit.")
        case .route: return tr("GPS rotalarını kaydedip geçmişini görmek için erişimini genişlet.", "Upgrade access to save GPS routes and see your history.")
        case .mealPlanner: return tr("Haftalık öğün planı oluşturmak için erişimini genişlet.", "Upgrade access to build a weekly meal plan.")
        case .regionalPlan: return tr("Bölgeye özel program üretmek için erişimini genişlet.", "Upgrade access to generate body-part programs.")
        case .history: return tr("Daha eski kayıtları görmek için erişimini genişlet.", "Upgrade access to see older records.")
        case .cardioPrograms: return tr("12-3-30, aralıklı koşu ve tepe tırmanışı gibi programlar Plus ve Premium'da.", "Programs like 12-3-30, interval runs and hill climbs are in Plus and Premium.")
        case .cardioGame: return tr("Sanal rota, rekorunla yarış ve hedef görevleri Plus ve Premium'da.", "Virtual route, racing your record and target quests are in Plus and Premium.")
        case .wellnessContent: return tr("Başlangıç dışındaki Pilates, mobilite, düşük etkili ve toparlanma içerikleri Plus ve Premium'da.", "Pilates, mobility, low-impact and recovery content beyond the starter set is in Plus and Premium.")
        case .barre: return tr("Barre antrenmanları Plus ve Premium'da.", "Barre workouts are in Plus and Premium.")
        case .adaptiveAction: return tr("Hareket değiştirme, mobilite ekleme ve toparlanmaya geçme Plus ve Premium'da.", "Swapping exercises, adding mobility and switching to recovery are in Plus and Premium.")
        case .pilatesSwitch: return tr("Bugünkü planı Pilates oturumuna çevirme Premium'da.", "Turning today's plan into a Pilates session is in Premium.")
        case .cycleAdaptation: return tr("Döngü bilgini antrenman uyarlamasına katmak Plus ve Premium'da. Döngü takibi ücretsizdir.", "Using your cycle information in workout adaptation is in Plus and Premium. Cycle tracking is free.")
        case .aiAdaptiveCoach: return tr("Uyarlamanı koçun sana özel açıklaması Premium'da.", "Your coach's personalised explanation of the adaptation is in Premium.")
        }
    }
}

struct TierLimits: Sendable {
    var dailyMealLogs: Int
    var dailyPhotoMeals: Int
    var exerciseLevels: Set<String>
    var customPrograms: Int
    var routeSaving: Bool
    var mealPlanner: Bool
    var regionalPlans: Bool
    var historyDays: Int
    var dailyCoachQuestions: Int
    var bannerAds: Bool
    var interstitialAds: Bool
    var rewardedAds: Bool
    var cardioPrograms: Bool
    var cardioGame: Bool
    var wellnessStarterOnly: Bool
    var barre: Bool
    var checkinHistoryDays: Int
    var checkinTrends: Bool
    var adaptiveActions: Set<String>
    var cycleAdaptation: Bool
    var aiAdaptiveCoach: Bool
    var coachCycleAware: Bool
    var nutritionPersonalization: String

    func canUseExercise(_ item: ExerciseCatalogItem) -> Bool { item.levelKey.isEmpty || exerciseLevels.contains(item.levelKey.lowercased()) }
    func canUseAdaptiveAction(_ action: String) -> Bool { adaptiveActions.contains(action) }

    func canUseModalityExercise(modalities: [String], subcategories: [String]) -> Bool {
        if modalities.isEmpty { return true }
        let onlyBarre = modalities.allSatisfy { $0 == "barre" }
        if !wellnessStarterOnly { return barre || !onlyBarre }
        if onlyBarre { return false }
        let starter: Set<String> = ["beginner", "short", "recovery", "morning", "evening"]
        return subcategories.contains { starter.contains($0) }
    }
}

let unlimited = Int.max

let tierLimits: [Tier: TierLimits] = [
    .guest: TierLimits(dailyMealLogs: 5, dailyPhotoMeals: 1, exerciseLevels: ["beginner"], customPrograms: 0, routeSaving: false, mealPlanner: false, regionalPlans: false, historyDays: 7, dailyCoachQuestions: 3,
                       bannerAds: true, interstitialAds: true, rewardedAds: true, cardioPrograms: false, cardioGame: false, wellnessStarterOnly: true, barre: false, checkinHistoryDays: 7, checkinTrends: false,
                       adaptiveActions: ["shorten", "reduce_intensity"], cycleAdaptation: false, aiAdaptiveCoach: false, coachCycleAware: false, nutritionPersonalization: "basic"),
    .free: TierLimits(dailyMealLogs: 15, dailyPhotoMeals: 1, exerciseLevels: ["beginner", "intermediate"], customPrograms: 2, routeSaving: true, mealPlanner: false, regionalPlans: false, historyDays: 30, dailyCoachQuestions: 5,
                      bannerAds: true, interstitialAds: true, rewardedAds: true, cardioPrograms: false, cardioGame: false, wellnessStarterOnly: true, barre: false, checkinHistoryDays: 14, checkinTrends: false,
                      adaptiveActions: ["shorten", "reduce_intensity"], cycleAdaptation: false, aiAdaptiveCoach: false, coachCycleAware: false, nutritionPersonalization: "basic"),
    .plus: TierLimits(dailyMealLogs: unlimited, dailyPhotoMeals: 3, exerciseLevels: ["beginner", "intermediate", "advanced"], customPrograms: 5, routeSaving: true, mealPlanner: true, regionalPlans: false, historyDays: 90, dailyCoachQuestions: 20,
                      bannerAds: false, interstitialAds: false, rewardedAds: false, cardioPrograms: true, cardioGame: true, wellnessStarterOnly: false, barre: true, checkinHistoryDays: 90, checkinTrends: false,
                      adaptiveActions: ["shorten", "reduce_intensity", "replace_exercises", "add_mobility", "switch_recovery"], cycleAdaptation: true, aiAdaptiveCoach: false, coachCycleAware: false, nutritionPersonalization: "training_load"),
    .premium: TierLimits(dailyMealLogs: unlimited, dailyPhotoMeals: 8, exerciseLevels: ["beginner", "intermediate", "advanced"], customPrograms: unlimited, routeSaving: true, mealPlanner: true, regionalPlans: true, historyDays: unlimited, dailyCoachQuestions: 40,
                         bannerAds: false, interstitialAds: false, rewardedAds: false, cardioPrograms: true, cardioGame: true, wellnessStarterOnly: false, barre: true, checkinHistoryDays: unlimited, checkinTrends: true,
                         adaptiveActions: ["shorten", "reduce_intensity", "replace_exercises", "add_mobility", "switch_recovery", "switch_pilates"], cycleAdaptation: true, aiAdaptiveCoach: true, coachCycleAware: true, nutritionPersonalization: "full"),
]

extension Dashboard {
    func todayMealLogCount(today: Date = Date()) -> Int { nutritionLogs.filter { $0.date.hasPrefix(Dates.day(today)) }.count }
}
