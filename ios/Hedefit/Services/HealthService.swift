import Foundation
import HealthKit

struct WearableDevice: Identifiable, Sendable { var label: String; var lastSeen: Date; var id: String { label } }
struct WearableSnapshot: Sendable {
    var sources: [String: Date]
    var devices: [WearableDevice]
    var latestHeartRate: Int?
    var restingHeartRate: Int?
}

/// HealthKit okuma/yazma (Android Health Connect karşılığı).
actor HealthService {
    static let shared = HealthService()
    private let store = HKHealthStore()

    var isAvailable: Bool { HKHealthStore.isHealthDataAvailable() }

    private var readTypes: Set<HKObjectType> {
        [HKQuantityType(.stepCount), HKQuantityType(.activeEnergyBurned), HKQuantityType(.bodyMass), HKCategoryType(.sleepAnalysis), HKQuantityType(.heartRate), HKQuantityType(.restingHeartRate), HKObjectType.workoutType()]
    }
    private var writeTypes: Set<HKSampleType> { [HKObjectType.workoutType(), HKQuantityType(.activeEnergyBurned), HKQuantityType(.bodyMass)] }

    func authorize() async throws {
        guard isAvailable else { throw AppError.message(trNow("Sağlık verileri bu cihazda kullanılamıyor.", "Health data isn't available on this device.")) }
        try await store.requestAuthorization(toShare: writeTypes, read: readTypes)
    }

    /// Apple, okuma iznini gizlilik gereği bildirmez; yazma durumu ve istek durumu üzerinden izlenir.
    func needsAuthorization() async -> Bool {
        guard isAvailable else { return false }
        let status = try? await store.statusForAuthorizationRequest(toShare: writeTypes, read: readTypes)
        return status == .shouldRequest
    }

    func today() async -> HealthSnapshot {
        async let steps = cumulative(.stepCount, .count(), since: Dates.startOfDay())
        async let energy = cumulative(.activeEnergyBurned, .kilocalorie(), since: Dates.startOfDay())
        async let sleep = sleepMinutes()
        async let weight = latestWeight()
        return await HealthSnapshot(steps: Int(steps), activeCalories: Int(energy), sleepMinutes: sleep, weightKg: weight, date: Date())
    }

    func todaySteps() async -> Int { Int(await cumulative(.stepCount, .count(), since: Dates.startOfDay())) }

    private func cumulative(_ id: HKQuantityTypeIdentifier, _ unit: HKUnit, since start: Date) async -> Double {
        let type = HKQuantityType(id)
        return await withCheckedContinuation { continuation in
            let query = HKStatisticsQuery(quantityType: type, quantitySamplePredicate: HKQuery.predicateForSamples(withStart: start, end: Date()), options: .cumulativeSum) { _, stats, _ in
                continuation.resume(returning: stats?.sumQuantity()?.doubleValue(for: unit) ?? 0)
            }
            store.execute(query)
        }
    }

    /// Dün 12:00'den bugüne uyku süreleri (yatakta olma hariç), 0…1440 dk.
    private func sleepMinutes() async -> Int {
        let start = Calendar.current.date(bySettingHour: 12, minute: 0, second: 0, of: Dates.add(-1)) ?? Dates.add(-1)
        return await withCheckedContinuation { continuation in
            let query = HKSampleQuery(sampleType: HKCategoryType(.sleepAnalysis), predicate: HKQuery.predicateForSamples(withStart: start, end: Date()), limit: HKObjectQueryNoLimit, sortDescriptors: nil) { _, samples, _ in
                let asleep: Set<Int> = [HKCategoryValueSleepAnalysis.asleepUnspecified.rawValue, HKCategoryValueSleepAnalysis.asleepCore.rawValue, HKCategoryValueSleepAnalysis.asleepDeep.rawValue, HKCategoryValueSleepAnalysis.asleepREM.rawValue]
                let intervals = (samples as? [HKCategorySample] ?? []).filter { asleep.contains($0.value) }.map { ($0.startDate, $0.endDate) }.sorted { $0.0 < $1.0 }
                // Birden çok kaynak çakışabilir: aralıkları birleştir.
                var total: TimeInterval = 0
                var cursor: Date?
                for (s, e) in intervals {
                    let from = max(s, cursor ?? s)
                    if e > from { total += e.timeIntervalSince(from) }
                    cursor = max(cursor ?? e, e)
                }
                continuation.resume(returning: min(max(Int(total / 60), 0), 1440))
            }
            store.execute(query)
        }
    }

    private func latestWeight() async -> Double? {
        await withCheckedContinuation { continuation in
            let query = HKSampleQuery(sampleType: HKQuantityType(.bodyMass), predicate: nil, limit: 1, sortDescriptors: [NSSortDescriptor(key: HKSampleSortIdentifierStartDate, ascending: false)]) { _, samples, _ in
                continuation.resume(returning: (samples?.first as? HKQuantitySample)?.quantity.doubleValue(for: .gramUnit(with: .kilo)))
            }
            store.execute(query)
        }
    }

    /// Son `days` gün içinde hangi kaynakların / cihazların veri yazdığı.
    func wearableSources(days: Int = 7) async -> WearableSnapshot {
        let start = Dates.add(-days)
        var sources: [String: Date] = [:], devices: [String: Date] = [:]
        for type in [HKQuantityType(.stepCount), HKQuantityType(.heartRate)] {
            let samples: [HKSample] = await withCheckedContinuation { continuation in
                let query = HKSampleQuery(sampleType: type, predicate: HKQuery.predicateForSamples(withStart: start, end: Date()), limit: 200, sortDescriptors: [NSSortDescriptor(key: HKSampleSortIdentifierEndDate, ascending: false)]) { _, samples, _ in
                    continuation.resume(returning: samples ?? [])
                }
                store.execute(query)
            }
            for sample in samples {
                let name = sample.sourceRevision.source.name
                if (sources[name] ?? .distantPast) < sample.endDate { sources[name] = sample.endDate }
                if let device = sample.device, let model = device.model, ["Watch", "Ring", "Band", "Fitbit", "Garmin"].contains(where: { model.contains($0) || (device.name ?? "").contains($0) }) {
                    let label = [device.manufacturer, device.name ?? model].compactMap { $0 }.joined(separator: " ")
                    if (devices[label] ?? .distantPast) < sample.endDate { devices[label] = sample.endDate }
                }
            }
        }
        let latest = await latestQuantity(.heartRate, unit: HKUnit.count().unitDivided(by: .minute()), since: start)
        let resting = await latestQuantity(.restingHeartRate, unit: HKUnit.count().unitDivided(by: .minute()), since: start)
        return WearableSnapshot(sources: sources, devices: devices.map { WearableDevice(label: $0.key, lastSeen: $0.value) }.sorted { $0.lastSeen > $1.lastSeen },
                                latestHeartRate: latest.map { Int($0) }, restingHeartRate: resting.map { Int($0) })
    }

    private func latestQuantity(_ id: HKQuantityTypeIdentifier, unit: HKUnit, since: Date) async -> Double? {
        await withCheckedContinuation { continuation in
            let query = HKSampleQuery(sampleType: HKQuantityType(id), predicate: HKQuery.predicateForSamples(withStart: since, end: Date()), limit: 1, sortDescriptors: [NSSortDescriptor(key: HKSampleSortIdentifierEndDate, ascending: false)]) { _, samples, _ in
                continuation.resume(returning: (samples?.first as? HKQuantitySample)?.quantity.doubleValue(for: unit))
            }
            store.execute(query)
        }
    }

    /// Biten antrenmanı Sağlık'a yazar; izin yoksa sessizce false döner.
    @discardableResult
    func writeWorkout(type: HKWorkoutActivityType, start: Date, end: Date, calories: Double?, distanceMeters: Double? = nil) async -> Bool {
        guard isAvailable, store.authorizationStatus(for: HKObjectType.workoutType()) == .sharingAuthorized else { return false }
        let configuration = HKWorkoutConfiguration(); configuration.activityType = type
        let builder = HKWorkoutBuilder(healthStore: store, configuration: configuration, device: .local())
        do {
            try await builder.beginCollection(at: start)
            var samples: [HKSample] = []
            if let calories, calories > 0, store.authorizationStatus(for: HKQuantityType(.activeEnergyBurned)) == .sharingAuthorized {
                samples.append(HKQuantitySample(type: HKQuantityType(.activeEnergyBurned), quantity: HKQuantity(unit: .kilocalorie(), doubleValue: calories), start: start, end: end))
            }
            if !samples.isEmpty { try await builder.addSamples(samples) }
            try await builder.endCollection(at: end)
            _ = try await builder.finishWorkout()
            return true
        } catch { return false }
    }

    @discardableResult
    func writeBodyMass(kg: Double, date: Date = Date()) async -> Bool {
        guard isAvailable, store.authorizationStatus(for: HKQuantityType(.bodyMass)) == .sharingAuthorized else { return false }
        let sample = HKQuantitySample(type: HKQuantityType(.bodyMass), quantity: HKQuantity(unit: .gramUnit(with: .kilo), doubleValue: kg), start: date, end: date)
        return (try? await store.save(sample)) != nil
    }
}
