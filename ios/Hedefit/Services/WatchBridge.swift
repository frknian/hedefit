import Foundation
import WatchConnectivity

/// iPhone ↔ Apple Watch köprüsü: özet verisini saate iter, saatten gelen su eklemelerini uygular.
final class WatchBridge: NSObject, WCSessionDelegate {
    static let shared = WatchBridge()
    /// Saatten gelen su miktarını (ml) uygulamak için AppStore tarafından ayarlanır.
    @MainActor var onWater: ((Int) -> Void)?
    /// Saatte biten antrenman: (tür kimliği, dakika, mesafe metre).
    @MainActor var onWorkout: ((String, Int, Double) -> Void)?
    private let allowedKinds: Set<String> = ["running", "walking", "hiking", "cycling", "strength"]

    func activate() {
        guard WCSession.isSupported() else { return }
        WCSession.default.delegate = self
        WCSession.default.activate()
    }

    func push(_ dashboard: Dashboard, settings: AppSettings) {
        guard WCSession.isSupported(), WCSession.default.activationState == .activated, WCSession.default.isWatchAppInstalled else { return }
        let snapshot: [String: Any] = [
            "steps": dashboard.steps, "stepGoal": settings.stepGoal,
            "waterMl": dashboard.waterMl, "waterGoal": settings.waterGoal,
            "calories": dashboard.activeCalories, "streakDays": dashboard.streakDays,
            "proteinG": Int(dashboard.nutritionLogs.reduce(0) { $0 + $1.protein }), "proteinGoal": dashboard.nutritionGoal.protein,
            "workoutName": dashboard.workouts.first?.name ?? "Antrenman"
        ]
        guard let data = try? JSONSerialization.data(withJSONObject: snapshot) else { return }
        try? WCSession.default.updateApplicationContext(["snapshot": data])
    }

    func session(_ session: WCSession, activationDidCompleteWith state: WCSessionActivationState, error: Error?) {}
    func sessionDidBecomeInactive(_ session: WCSession) {}
    func sessionDidDeactivate(_ session: WCSession) { session.activate() }
    func session(_ session: WCSession, didReceiveUserInfo userInfo: [String: Any] = [:]) {
        if let ml = userInfo["water"] as? Int { Task { @MainActor in self.onWater?(ml) }; return }
        guard let workout = userInfo["workout"] as? [String: Any], let id = workout["id"] as? String, let kind = workout["kind"] as? String, allowedKinds.contains(kind),
              let seconds = (workout["durationSec"] as? NSNumber)?.intValue, seconds >= 60, seconds <= 86_400 else { return }
        // Saat aynı antrenmanı tekrar gönderebilir; kimliği görülmüşse yok say.
        let defaults = UserDefaults.standard
        var seen = defaults.stringArray(forKey: "watchWorkoutIDs") ?? []
        guard !seen.contains(id) else { return }
        seen.append(id); defaults.set(Array(seen.suffix(50)), forKey: "watchWorkoutIDs")
        let distance = (workout["distanceM"] as? NSNumber)?.doubleValue ?? 0
        Task { @MainActor in self.onWorkout?(kind, seconds / 60, distance) }
    }
}
