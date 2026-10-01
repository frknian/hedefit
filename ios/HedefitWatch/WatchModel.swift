import Foundation
import WatchConnectivity
import Observation
import WidgetKit
import UserNotifications

@MainActor @Observable final class WatchModel: NSObject, WCSessionDelegate {
    var snapshot = WatchSnapshot()
    var reachable = false
    /// Tüm metinler telefonun dil ayarına göre seçilir.
    func t(_ tr: String, _ en: String) -> String { snapshot.isEnglish ? en : tr }

    override init() {
        super.init()
        if let data = UserDefaults.standard.data(forKey: "snapshot"), let saved = try? JSONDecoder().decode(WatchSnapshot.self, from: data) { snapshot = saved }
        guard WCSession.isSupported() else { return }
        WCSession.default.delegate = self
        WCSession.default.activate()
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound]) { _, _ in }
    }

    /// Su ekler; iyimser güncelleme yapar, telefon erişilemezse sistem kuyruğa alır.
    func addWater(_ ml: Int) {
        snapshot.waterMl = max(0, min(snapshot.waterMl + ml, 20_000))
        persist()
        guard WCSession.default.activationState == .activated else { return }
        WCSession.default.transferUserInfo(["water": ml])
    }

    /// Sesle soru veya yemek kaydı; yalnızca iPhone ulaşılabilirken çalışır.
    func ask(mode: String, text: String, completion: @escaping (Bool, String) -> Void) {
        guard WCSession.default.activationState == .activated, WCSession.default.isReachable else { completion(false, t("iPhone'a ulaşılamıyor. Telefonda Hedefit açık olmalı.", "Can't reach iPhone. Hedefit must be open on your phone.")); return }
        WCSession.default.sendMessage(["mode": mode, "text": text], replyHandler: { reply in
            let ok = reply["ok"] as? Bool ?? false, message = reply["text"] as? String ?? "?"
            Task { @MainActor in completion(ok, message) }
        }, errorHandler: { error in Task { @MainActor in completion(false, error.localizedDescription) } })
    }

    /// Biten antrenmanı telefona gönderir (transferUserInfo kuyruğa alır ve sonra iletir).
    func send(_ workout: WatchWorkoutPayload) {
        guard workout.isValid, WCSession.default.activationState == .activated, let data = try? JSONEncoder().encode(workout) else { return }
        WCSession.default.transferUserInfo(["workout": data])
    }

    private func persist() {
        if let data = try? JSONEncoder().encode(snapshot) { UserDefaults.standard.set(data, forKey: "snapshot") }
        // Kadran complication'ları bu paylaşılan alandan okur.
        if let d = UserDefaults(suiteName: "group.com.hedefit.app") {
            d.set(snapshot.steps, forKey: "watch.steps"); d.set(snapshot.stepGoal, forKey: "watch.stepGoal")
            d.set(snapshot.waterMl, forKey: "watch.water"); d.set(snapshot.waterGoal, forKey: "watch.waterGoal")
            d.set(snapshot.streakDays, forKey: "watch.streak"); d.set(snapshot.lang, forKey: "watch.lang")
        }
        WidgetCenter.shared.reloadAllTimelines()
    }

    private func apply(_ context: [String: Any]) {
        guard let data = context["snapshot"] as? Data, let value = try? JSONDecoder().decode(WatchSnapshot.self, from: data) else { return }
        notifyRankUp(old: snapshot.social.rank, new: value.social.rank, english: value.isEnglish)
        snapshot = value
        persist()
    }

    /// Haftalık sıralamada yükselince kısa yerel bildirim gösterir.
    private func notifyRankUp(old: Int, new: Int, english: Bool) {
        guard old > 0, new > 0, new < old else { return }
        let content = UNMutableNotificationContent()
        content.title = "Hedefit"
        content.body = english ? "You moved up to #\(new) this week" : "Bu hafta \(new). sıraya yükseldin"
        content.sound = .default
        UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: "rank-up", content: content, trigger: nil))
    }

    nonisolated func session(_ session: WCSession, activationDidCompleteWith state: WCSessionActivationState, error: Error?) {
        let context = session.receivedApplicationContext
        Task { @MainActor in self.reachable = session.isReachable; self.apply(context) }
    }
    nonisolated func sessionReachabilityDidChange(_ session: WCSession) { Task { @MainActor in self.reachable = session.isReachable } }
    nonisolated func session(_ session: WCSession, didReceiveApplicationContext context: [String: Any]) { Task { @MainActor in self.apply(context) } }
}
