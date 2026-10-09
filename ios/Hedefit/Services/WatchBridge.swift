import Foundation
import WatchConnectivity

/// iPhone ↔ Apple Watch köprüsü: özet verisini saate iter, saatten gelen su ve antrenmanları uygular.
final class WatchBridge: NSObject, WCSessionDelegate {
    static let shared = WatchBridge()
    private let allowedKinds: Set<String> = ["running", "walking", "hiking", "cycling", "strength"]
    @MainActor var onWater: ((Int) -> Void)?
    /// (tür, dakika, mesafe metre)
    @MainActor var onWorkout: ((String, Int, Double) -> Void)?

    func activate() {
        guard WCSession.isSupported() else { return }
        WCSession.default.delegate = self
        WCSession.default.activate()
    }

    @MainActor func push(_ app: AppModel) {
        guard WCSession.isSupported(), WCSession.default.activationState == .activated, WCSession.default.isWatchAppInstalled, let d = app.dashboard else { return }
        let snapshot: [String: Any] = [
            "steps": d.steps, "stepGoal": app.prefs.stepGoal, "waterMl": d.waterMl, "waterGoal": app.prefs.waterGoalMl, "calories": d.activeCalories, "streakDays": d.streakDays,
            "proteinG": Int(d.nutritionLogs.reduce(0) { $0 + $1.protein }), "proteinGoal": d.nutritionGoal.protein,
            "workoutName": d.workoutPrograms.first(where: { $0.isActive }).map { localizedProgramName($0.name, source: $0.source) } ?? "Antrenman",
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
              let seconds = (workout["durationSec"] as? NSNumber)?.intValue, (60...86_400).contains(seconds) else { return }
        let defaults = UserDefaults.standard
        var seen = defaults.stringArray(forKey: "watchWorkoutIDs") ?? []
        guard !seen.contains(id) else { return }
        seen.append(id); defaults.set(Array(seen.suffix(50)), forKey: "watchWorkoutIDs")
        let distance = (workout["distanceM"] as? NSNumber)?.doubleValue ?? 0
        Task { @MainActor in self.onWorkout?(kind, seconds / 60, distance) }
    }
}
