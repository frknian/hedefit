import Foundation

struct AppPreferences: Codable, Equatable {
    var notificationsEnabled = false
    var notificationHour = 19
    var notificationMinute = 0
    /// Calendar weekday (1 = Pazar … 7 = Cumartesi)
    var notificationDays: Set<Int> = [2, 4, 6]
    var stepGoal = 8_000
    var waterGoalMl = 2_500
    var weeklyWorkoutGoal = 3
    var coachName = ""
    /// metric | imperial
    var unitSystem = "metric"
    var welcomeGuideSeen = false
    var homeQuickActions: [String] = AppPreferences.defaultQuickActions
    /// weekly | monthly
    var planRotation = "monthly"
    var weighInReminderEnabled = false
    var weighInReminderDay = 2
    var challengeRemindersEnabled = true
    var cardioAfterStrength = true

    static let defaultQuickActions = ["workout", "nutrition", "coach", "musclemap", "atlas", "cardio", "route", "sleep", "curlgame"]

    private static let key = "hedefit_preferences_v1"

    static func load() -> AppPreferences {
        guard let data = UserDefaults.standard.data(forKey: key), var value = try? JSONDecoder().decode(AppPreferences.self, from: data) else { return AppPreferences() }
        value.stepGoal = min(max(value.stepGoal, 1_000), 50_000)
        value.waterGoalMl = min(max(value.waterGoalMl, 500), 10_000)
        value.weeklyWorkoutGoal = min(max(value.weeklyWorkoutGoal, 1), 7)
        value.coachName = String(value.coachName.prefix(24))
        return value
    }

    func save() { if let data = try? JSONEncoder().encode(self) { UserDefaults.standard.set(data, forKey: Self.key) } }
}
