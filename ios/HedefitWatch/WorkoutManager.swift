import Foundation
import HealthKit
import WatchConnectivity
import Observation

/// Saatte HKWorkoutSession ile nabız, kalori ve mesafe ölçer; bitince Sağlık'a yazar.
@MainActor @Observable final class WorkoutManager: NSObject, HKWorkoutSessionDelegate, HKLiveWorkoutBuilderDelegate {
    struct Kind: Identifiable, Hashable { let id: String, title: String, icon: String, type: HKWorkoutActivityType, outdoor: Bool }
    static let kinds: [Kind] = [
        .init(id: "running", title: "Koşu", icon: "figure.run", type: .running, outdoor: true),
        .init(id: "walking", title: "Yürüyüş", icon: "figure.walk", type: .walking, outdoor: true),
        .init(id: "hiking", title: "Doğa yürüyüşü", icon: "figure.hiking", type: .hiking, outdoor: true),
        .init(id: "cycling", title: "Bisiklet", icon: "bicycle", type: .cycling, outdoor: true),
        .init(id: "strength", title: "Ağırlık", icon: "dumbbell.fill", type: .traditionalStrengthTraining, outdoor: false)
    ]

    var running = false
    var paused = false
    var heartRate = 0.0
    var calories = 0.0
    var distance = 0.0
    var elapsed = 0
    var kind = WorkoutManager.kinds[0]
    private let store = HKHealthStore()
    private var session: HKWorkoutSession?
    private var builder: HKLiveWorkoutBuilder?
    private var timer: Timer?

    var paceSecondsPerKm: Int { distance > 50 ? Int(Double(elapsed) / (distance / 1000)) : 0 }

    func requestAuthorization() async {
        let write: Set<HKSampleType> = [HKQuantityType.workoutType()]
        let read: Set<HKObjectType> = [HKQuantityType(.heartRate), HKQuantityType(.activeEnergyBurned), HKQuantityType(.distanceWalkingRunning), HKQuantityType(.distanceCycling)]
        try? await store.requestAuthorization(toShare: write, read: read)
    }

    func start(_ kind: Kind) async {
        guard HKHealthStore.isHealthDataAvailable() else { return }
        await requestAuthorization()
        self.kind = kind
        let config = HKWorkoutConfiguration()
        config.activityType = kind.type
        config.locationType = kind.outdoor ? .outdoor : .indoor
        do {
            let session = try HKWorkoutSession(healthStore: store, configuration: config)
            let builder = session.associatedWorkoutBuilder()
            builder.dataSource = HKLiveWorkoutDataSource(healthStore: store, workoutConfiguration: config)
            session.delegate = self; builder.delegate = self
            self.session = session; self.builder = builder
            heartRate = 0; calories = 0; distance = 0; elapsed = 0; paused = false
            let date = Date()
            session.startActivity(with: date)
            try await builder.beginCollection(at: date)
            running = true
            timer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] _ in Task { @MainActor in guard let self, self.running, !self.paused else { return }; self.elapsed += 1 } }
        } catch { running = false }
    }

    func togglePause() { paused ? session?.resume() : session?.pause() }

    func finish() async {
        let summary: [String: Any] = ["id": UUID().uuidString, "kind": kind.id, "durationSec": elapsed, "distanceM": distance, "calories": Int(calories)]
        session?.end()
        timer?.invalidate(); timer = nil
        if let builder { try? await builder.endCollection(at: Date()); _ = try? await builder.finishWorkout() }
        running = false; paused = false; session = nil; builder = nil
        // 1 dakikadan kısa antrenmanlar kaydedilmez; iPhone hesaba yazar (transferUserInfo kuyruğa alır).
        if elapsed >= 60, WCSession.default.activationState == .activated { WCSession.default.transferUserInfo(["workout": summary]) }
    }

    nonisolated func workoutSession(_ s: HKWorkoutSession, didChangeTo to: HKWorkoutSessionState, from: HKWorkoutSessionState, date: Date) {
        Task { @MainActor in self.paused = to == .paused }
    }
    nonisolated func workoutSession(_ s: HKWorkoutSession, didFailWithError error: Error) {}
    nonisolated func workoutBuilderDidCollectEvent(_ b: HKLiveWorkoutBuilder) {}
    nonisolated func workoutBuilder(_ b: HKLiveWorkoutBuilder, didCollectDataOf types: Set<HKSampleType>) {
        for case let type as HKQuantityType in types {
            guard let stats = b.statistics(for: type) else { continue }
            let id = type.identifier
            Task { @MainActor in
                switch id {
                case HKQuantityTypeIdentifier.heartRate.rawValue: self.heartRate = stats.mostRecentQuantity()?.doubleValue(for: .count().unitDivided(by: .minute())) ?? 0
                case HKQuantityTypeIdentifier.activeEnergyBurned.rawValue: self.calories = stats.sumQuantity()?.doubleValue(for: .kilocalorie()) ?? 0
                case HKQuantityTypeIdentifier.distanceWalkingRunning.rawValue, HKQuantityTypeIdentifier.distanceCycling.rawValue: self.distance = stats.sumQuantity()?.doubleValue(for: .meter()) ?? 0
                default: break
                }
            }
        }
    }
}
