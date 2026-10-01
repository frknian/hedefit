import Foundation
import WatchConnectivity
import Observation
import WidgetKit

/// Telefondan gelen günlük özet. Telefon `updateApplicationContext` ile gönderir.
struct WatchSnapshot: Codable, Equatable {
    var steps = 0, stepGoal = 10_000, waterMl = 0, waterGoal = 2_500
    var calories = 0, streakDays = 0, proteinG = 0, proteinGoal = 0
    var workoutName = "Antrenman"
}

@MainActor @Observable final class WatchModel: NSObject, WCSessionDelegate {
    var snapshot = WatchSnapshot()
    var reachable = false

    override init() {
        super.init()
        if let data = UserDefaults.standard.data(forKey: "snapshot"), let saved = try? JSONDecoder().decode(WatchSnapshot.self, from: data) { snapshot = saved }
        guard WCSession.isSupported() else { return }
        WCSession.default.delegate = self
        WCSession.default.activate()
    }

    /// Su ekler; iyimser güncelleme yapar, telefon erişilemezse kuyruğa alır.
    func addWater(_ ml: Int) {
        snapshot.waterMl = max(0, min(snapshot.waterMl + ml, 20_000))
        persist()
        guard WCSession.default.activationState == .activated else { return }
        WCSession.default.transferUserInfo(["water": ml])
    }

    private func persist() {
        if let data = try? JSONEncoder().encode(snapshot) { UserDefaults.standard.set(data, forKey: "snapshot") }
        // Kadran complication'ları bu paylaşılan alandan okur.
        if let d = UserDefaults(suiteName: "group.com.hedefit.app") {
            d.set(snapshot.steps, forKey: "watch.steps"); d.set(snapshot.stepGoal, forKey: "watch.stepGoal")
            d.set(snapshot.waterMl, forKey: "watch.water"); d.set(snapshot.waterGoal, forKey: "watch.waterGoal")
            d.set(snapshot.streakDays, forKey: "watch.streak")
        }
        WidgetCenter.shared.reloadAllTimelines()
    }

    private func apply(_ context: [String: Any]) {
        guard let data = context["snapshot"] as? Data, let value = try? JSONDecoder().decode(WatchSnapshot.self, from: data) else { return }
        snapshot = value
        persist()
    }

    nonisolated func session(_ session: WCSession, activationDidCompleteWith state: WCSessionActivationState, error: Error?) {
        let context = session.receivedApplicationContext
        Task { @MainActor in self.reachable = session.isReachable; self.apply(context) }
    }
    nonisolated func sessionReachabilityDidChange(_ session: WCSession) { Task { @MainActor in self.reachable = session.isReachable } }
    nonisolated func session(_ session: WCSession, didReceiveApplicationContext context: [String: Any]) { Task { @MainActor in self.apply(context) } }
}
