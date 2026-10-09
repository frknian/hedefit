import Foundation
import CoreLocation

/// Monoton saat (ms): duvar saati değişse de süre doğru kalsın.
func uptimeMs() -> Int64 { Int64(ProcessInfo.processInfo.systemUptime * 1000) }
func nowMs() -> Int64 { Int64(Date().timeIntervalSince1970 * 1000) }

private func safeDistance(_ value: Double) -> Double { value.isFinite && value >= 0 ? value : 0 }

enum ActivitySessionStatus: Sendable { case idle, preparingGPS, countdown, active, paused, finishing, completed }

struct RouteSnapshot: Codable, Equatable, Sendable {
    var id = ""
    var tracking = false
    var startedAtMs: Int64 = 0
    var stoppedAtMs: Int64 = 0
    var distanceMeters = 0.0
    var points: [RoutePoint] = []
    var activityType = "Koşu"
    var paused = false
    var pausedAtMs: Int64 = 0
    var pausedDurationMs: Int64 = 0
    var startedUptime: Int64 = 0
    var pausedUptime: Int64 = 0
    var stoppedUptime: Int64 = 0

    var startedAt: Date { Date(timeIntervalSince1970: Double(startedAtMs) / 1000) }
    var stoppedAt: Date { Date(timeIntervalSince1970: Double(stoppedAtMs) / 1000) }

    private var endWall: Int64 { tracking && !paused ? nowMs() : (paused ? pausedAtMs : stoppedAtMs) }
    private var endUptime: Int64 { tracking && !paused ? uptimeMs() : (paused ? pausedUptime : stoppedUptime) }

    var status: ActivitySessionStatus {
        if tracking && paused { return .paused }
        if tracking { return .active }
        if stoppedAtMs > 0 && !points.isEmpty { return .completed }
        return .idle
    }

    var durationSeconds: Int {
        let monotonicValid = startedUptime > 0 && endUptime >= startedUptime
        let millis = monotonicValid ? endUptime - startedUptime - pausedDurationMs : endWall - startedAtMs - pausedDurationMs
        return max(Int(millis / 1000), 0)
    }

    var elapsedDurationSeconds: Int { startedAtMs <= 0 ? 0 : max(Int(((tracking ? nowMs() : stoppedAtMs) - startedAtMs) / 1000), 0) }
    var paceSecondsPerKm: Int? { !distanceMeters.isFinite || distanceMeters < 50 ? nil : Int(Double(durationSeconds) / (distanceMeters / 1000)) }
    var averageSpeedKmh: Double { durationSeconds < 1 || !distanceMeters.isFinite ? 0 : distanceMeters / Double(durationSeconds) * 3.6 }

    var currentSpeedKmh: Double {
        let recent = Array(points.suffix(7))
        let speeds = zip(recent, recent.dropFirst()).compactMap { first, second -> Double? in
            let seconds = Double(second.recordedAt - first.recordedAt) / 1000
            guard (0.5...20).contains(seconds) else { return nil }
            let speed = geoDistanceMeters(first, second) / seconds * 3.6
            return (0...120).contains(speed) ? speed : nil
        }.sorted()
        return speeds.isEmpty ? 0 : speeds[speeds.count / 2]
    }

    var currentPaceSecondsPerKm: Int? { currentSpeedKmh >= 1 ? Int(3600 / currentSpeedKmh) : nil }
    var displayPaceSecondsPerKm: Int? { tracking ? (currentPaceSecondsPerKm ?? paceSecondsPerKm) : paceSecondsPerKm }
    var canSave: Bool { points.count >= 2 && distanceMeters >= 10 }
}

func geoDistanceMeters(_ a: RoutePoint, _ b: RoutePoint) -> Double {
    let r = 6_371_000.0
    let dLat = (b.latitude - a.latitude) * .pi / 180, dLon = (b.longitude - a.longitude) * .pi / 180
    let h = sin(dLat / 2) * sin(dLat / 2) + cos(a.latitude * .pi / 180) * cos(b.latitude * .pi / 180) * sin(dLon / 2) * sin(dLon / 2)
    return r * 2 * atan2(sqrt(min(max(h, 0), 1)), sqrt(max(1 - h, 0)))
}

func normalizeAccuracy(_ value: Double) -> Double { value.isFinite && (0...1000).contains(value) ? value : 0 }

func isValidRoutePoint(_ p: RoutePoint) -> Bool {
    p.latitude.isFinite && (-85.05112878...85.05112878).contains(p.latitude) && p.longitude.isFinite && (-180...180).contains(p.longitude) && p.recordedAt > 0
}

let maxRouteAccuracyMeters = 20.0
let maxRouteLocationAgeMs: Int64 = 15_000

/// Önbellek/ağ konumlarının GPS rotasını yan sokaklara çekmesini engeller.
func isPreciseRouteLocation(_ accuracy: Double) -> Bool { accuracy.isFinite && (0...maxRouteAccuracyMeters).contains(accuracy) }
func isFreshRouteLocation(_ timestampMs: Int64, now: Int64 = nowMs()) -> Bool { timestampMs > 0 && timestampMs <= now + 2000 && now - timestampMs <= maxRouteLocationAgeMs }

/// Bildirilen GPS belirsizliğinden küçük yer değiştirme hareket değil gürültüdür; hız tavanını aşan sıçramalar atılır.
func acceptedRouteSegmentMeters(last: RoutePoint, next: RoutePoint, activityType: String) -> Double? {
    guard isValidRoutePoint(last), isValidRoutePoint(next) else { return nil }
    let elapsed = Double(next.recordedAt - last.recordedAt) / 1000
    guard elapsed > 0 else { return nil }
    let distance = geoDistanceMeters(last, next)
    if distance <= max(2, last.accuracyMeters, next.accuracyMeters) { return 0 }
    let maxKmh: Double
    switch activityType { case "Yürüyüş": maxKmh = 15; case "Bisiklet": maxKmh = 100; case "Kayak": maxKmh = 130; default: maxKmh = 30 }
    return distance / elapsed * 3.6 <= maxKmh ? distance : nil
}

func formatDuration(_ seconds: Int) -> String { String(format: "%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60) }
func formatPace(_ seconds: Int?) -> String { seconds.map { String(format: "%d:%02d /km", $0 / 60, $0 % 60) } ?? "— /km" }

// MARK: - Sağlık anlık görüntüsü

struct HealthSnapshot: Sendable {
    var steps: Int
    var activeCalories: Int
    var sleepMinutes: Int
    var weightKg: Double?
    var date: Date
}
