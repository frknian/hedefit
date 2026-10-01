import Foundation
import WatchConnectivity

/// iPhone ↔ Apple Watch köprüsü: özet verisini saate iter, saatten gelen su eklemelerini uygular.
final class WatchBridge: NSObject, WCSessionDelegate {
    static let shared = WatchBridge()
    /// Saatten gelen su miktarını (ml) uygulamak için AppStore tarafından ayarlanır.
    @MainActor var onWater: ((Int) -> Void)?

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
        guard let ml = userInfo["water"] as? Int else { return }
        Task { @MainActor in self.onWater?(ml) }
    }
}
