import Foundation
import UserNotifications

/// Yerel bildirimler: rutin hatırlatmaları, yeni blok, haftalık tartı, challenge ve dinlenme sayacı.
@MainActor
final class NotificationService: NSObject, UNUserNotificationCenterDelegate {
    static let shared = NotificationService()
    private let center = UNUserNotificationCenter.current()

    private enum ID {
        static let routinePrefix = "routine-", newBlock = "new-block", weighIn = "weigh-in", challenge = "challenge", rest = "rest-timer"
    }

    func configure() { center.delegate = self }

    func requestAuthorization() async -> Bool {
        (try? await center.requestAuthorization(options: [.alert, .sound, .badge])) ?? false
    }

    func isAuthorized() async -> Bool {
        let settings = await center.notificationSettings()
        return settings.authorizationStatus == .authorized || settings.authorizationStatus == .provisional
    }

    func clearAll() async { center.removeAllPendingNotificationRequests() }

    /// Tercihlere göre tüm planlı bildirimleri baştan kurar.
    func reschedule(_ prefs: AppPreferences) async {
        let routineIds = (1...7).map { ID.routinePrefix + "\($0)" } + [ID.newBlock, ID.weighIn]
        center.removePendingNotificationRequests(withIdentifiers: routineIds)
        guard prefs.notificationsEnabled, await isAuthorized() else { return }

        for day in prefs.notificationDays {
            var components = DateComponents(); components.weekday = day; components.hour = prefs.notificationHour; components.minute = prefs.notificationMinute
            await add(ID.routinePrefix + "\(day)", title: tr("Bugünün hedefi hazır", "Today's goal is ready"),
                      body: tr("Kısa bir antrenman bile serini korur. Planına göz at.", "Even a short workout keeps your streak. Take a look at your plan."), trigger: UNCalendarNotificationTrigger(dateMatching: components, repeats: true))
        }

        let weekly = PlanRotationPeriod(key: prefs.planRotation) == .weekly
        let start = PlanRotation.nextBlockStart(PlanRotationPeriod(key: prefs.planRotation), Date())
        var blockComponents = Dates.calendar.dateComponents([.year, .month, .day], from: start); blockComponents.hour = 9
        await add(ID.newBlock, title: tr("Yeni antrenman bloğun hazır", "A new training block is ready"),
                  body: tr("Yeni \(weekly ? "hafta" : "ay") başladı. Yardımcı hareketlerini yenile.", "A new \(weekly ? "week" : "month") has started. Refresh your accessory exercises."), trigger: UNCalendarNotificationTrigger(dateMatching: blockComponents, repeats: false))

        if prefs.weighInReminderEnabled {
            var c = DateComponents(); c.weekday = prefs.weighInReminderDay; c.hour = 9
            await add(ID.weighIn, title: tr("Haftalık tartı zamanı", "Weekly weigh-in"),
                      body: tr("Kilonu kaydet; trendin ve hedef tahminin güncel kalsın.", "Log your weight to keep your trend and goal estimate up to date."), trigger: UNCalendarNotificationTrigger(dateMatching: c, repeats: true))
        }
    }

    /// Bugünkü challenge görevi yapılmadıysa 18:00'de tek bir nazik hatırlatma; yapıldıysa iptal.
    func scheduleChallengeReminder(prefs: AppPreferences, title: String?, done: Bool, streak: Int) async {
        center.removePendingNotificationRequests(withIdentifiers: [ID.challenge])
        guard prefs.notificationsEnabled, prefs.challengeRemindersEnabled, !done, let title, await isAuthorized() else { return }
        var c = Dates.calendar.dateComponents([.year, .month, .day], from: Date()); c.hour = 18; c.minute = 0
        guard let fire = Dates.calendar.date(from: c), fire > Date() else { return }
        let body = streak % 7 == 6 ? tr("\(streak + 1) günlük serine yalnızca 1 gün kaldı.", "Just 1 day left to your \(streak + 1)-day streak.") : tr("Bugünkü görevin hazır.", "Today's task is ready.")
        await add(ID.challenge, title: title, body: body, trigger: UNCalendarNotificationTrigger(dateMatching: c, repeats: false))
    }

    /// Dinlenme sayacı: uygulama arka plandayken süre bitince haber verir.
    func scheduleRestEnd(seconds: Int, exercise: String) async {
        cancelRestEnd()
        guard seconds > 0, await isAuthorized() else { return }
        await add(ID.rest, title: tr("Dinlenme bitti", "Rest is over"), body: tr("Sıradaki sete hazırsın: \(exercise)", "Ready for your next set: \(exercise)"), trigger: UNTimeIntervalNotificationTrigger(timeInterval: TimeInterval(seconds), repeats: false), sound: .defaultCritical)
    }

    func cancelRestEnd() { center.removePendingNotificationRequests(withIdentifiers: [ID.rest]) }

    private func add(_ id: String, title: String, body: String, trigger: UNNotificationTrigger, sound: UNNotificationSound = .default) async {
        let content = UNMutableNotificationContent()
        content.title = title; content.body = body; content.sound = sound
        try? await center.add(UNNotificationRequest(identifier: id, content: content, trigger: trigger))
    }

    // Uygulama açıkken de bildirimi göster.
    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification) async -> UNNotificationPresentationOptions { [.banner, .sound] }
}
