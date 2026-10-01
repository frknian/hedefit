import Foundation
import WatchConnectivity

/// iPhone ↔ Apple Watch köprüsü: özeti saate iter; saatten gelen su, antrenman ve sesli istekleri uygular.
final class WatchBridge: NSObject, WCSessionDelegate {
    static let shared = WatchBridge()
    /// Saatten gelen su miktarını (ml) uygulamak için AppStore tarafından ayarlanır.
    @MainActor var onWater: ((Int) -> Void)?
    /// Saatte biten antrenman (doğrulanmış ve tekilleştirilmiş).
    @MainActor var onWorkout: ((WatchWorkoutPayload) -> Void)?
    /// Sesli istek: (mode "chat" | "food", metin) -> (başarılı mı, yanıt metni).
    @MainActor var onAsk: ((String, String) async -> (Bool, String))?
    /// Sıralama sayfası için telefonun yüklediği sosyal veri.
    @MainActor var social = WatchSocial()

    func activate() {
        guard WCSession.isSupported() else { return }
        WCSession.default.delegate = self
        WCSession.default.activate()
    }

    func push(_ dashboard: Dashboard, settings: AppSettings, social: WatchSocial = WatchSocial()) {
        guard WCSession.isSupported(), WCSession.default.activationState == .activated, WCSession.default.isWatchAppInstalled else { return }
        var snapshot = WatchSnapshot()
        snapshot.steps = dashboard.steps; snapshot.stepGoal = settings.stepGoal
        snapshot.waterMl = dashboard.waterMl; snapshot.waterGoal = settings.waterGoal
        snapshot.calories = dashboard.activeCalories; snapshot.streakDays = dashboard.streakDays
        snapshot.proteinG = Int(dashboard.nutritionLogs.reduce(0) { $0 + $1.protein }); snapshot.proteinGoal = dashboard.nutritionGoal.protein
        snapshot.workoutName = dashboard.workouts.first?.name ?? "Antrenman"
        snapshot.lang = settings.language
        snapshot.exercises = dashboard.workouts.prefix(12).map { WatchExercise(id: $0.id, name: $0.name, sets: min(max($0.sets, 1), 20), reps: Int($0.reps.split(whereSeparator: { !$0.isNumber }).first ?? "10") ?? 10, rest: min(max($0.restSeconds, 10), 600), kg: nil) }
        snapshot.social = social
        guard let data = try? JSONEncoder().encode(snapshot) else { return }
        try? WCSession.default.updateApplicationContext(["snapshot": data])
    }

    func session(_ session: WCSession, activationDidCompleteWith state: WCSessionActivationState, error: Error?) {}
    func sessionDidBecomeInactive(_ session: WCSession) {}
    func sessionDidDeactivate(_ session: WCSession) { session.activate() }

    func session(_ session: WCSession, didReceiveUserInfo userInfo: [String: Any] = [:]) {
        if let ml = userInfo["water"] as? Int, ml > 0, ml <= 20_000 { Task { @MainActor in self.onWater?(ml) }; return }
        guard let data = userInfo["workout"] as? Data, let workout = try? JSONDecoder().decode(WatchWorkoutPayload.self, from: data), workout.isValid else { return }
        // Saat aynı antrenmanı tekrar gönderebilir; kimliği görülmüşse yok say.
        let defaults = UserDefaults.standard
        var seen = defaults.stringArray(forKey: "watchWorkoutIDs") ?? []
        guard !seen.contains(workout.id) else { return }
        seen.append(workout.id); defaults.set(Array(seen.suffix(50)), forKey: "watchWorkoutIDs")
        Task { @MainActor in self.onWorkout?(workout) }
    }

    /// Sesli sohbet / yemek kaydı. Yanıt saatin replyHandler'ına gider.
    func session(_ session: WCSession, didReceiveMessage message: [String: Any], replyHandler: @escaping ([String: Any]) -> Void) {
        guard let mode = message["mode"] as? String, ["chat", "food"].contains(mode), let text = (message["text"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines), !text.isEmpty else { replyHandler(["ok": false, "text": "?"]); return }
        Task { @MainActor in
            let result = await self.onAsk?(mode, String(text.prefix(300))) ?? (false, "Telefonda Hedefit'i aç.")
            replyHandler(["ok": result.0, "text": String(result.1.prefix(600))])
        }
    }
}
