import Foundation
import CoreLocation
import Observation
import WidgetKit

/// Canlı GPS aktivite takibi: kalıcı anlık görüntü, duraklatma, arka plan konumu.
/// Android `RouteTrackingStore` + `RouteTrackingService` birleşimi.
@MainActor @Observable
final class RouteTracker: NSObject, CLLocationManagerDelegate {
    static let shared = RouteTracker()

    private(set) var snapshot = RouteSnapshot()
    private(set) var completed = RouteSnapshot()
    private(set) var authorization: CLAuthorizationStatus = .notDetermined
    private(set) var lastLocation: CLLocation?
    private(set) var gpsAccuracy: Double?
    var error: String?

    @ObservationIgnored private let manager = CLLocationManager()
    @ObservationIgnored private let defaults = UserDefaults.standard
    @ObservationIgnored private var pendingStart: String?

    override private init() {
        super.init()
        manager.delegate = self
        manager.activityType = .fitness
        manager.desiredAccuracy = kCLLocationAccuracyBest
        manager.distanceFilter = 2
        manager.pausesLocationUpdatesAutomatically = false
        authorization = manager.authorizationStatus
        snapshot = Self.read("route.active")
        completed = Self.read("route.completed")
        if snapshot.tracking && !snapshot.paused { resumeUpdates() }
    }

    var isTracking: Bool { snapshot.tracking }
    var hasLocationPermission: Bool { authorization == .authorizedWhenInUse || authorization == .authorizedAlways }

    // MARK: Yaşam döngüsü

    func requestPermission() {
        if authorization == .notDetermined { manager.requestWhenInUseAuthorization() }
        else if authorization == .authorizedWhenInUse { manager.requestAlwaysAuthorization() }
    }

    /// İzin yoksa istenir; verildiğinde otomatik başlar.
    func start(activityType: String = "Koşu") {
        error = nil
        guard CLLocationManager.locationServicesEnabled() else { error = trNow("Konum servisleri kapalı.", "Location services are turned off."); return }
        switch authorization {
        case .notDetermined: pendingStart = activityType; manager.requestWhenInUseAuthorization(); return
        case .denied, .restricted: error = trNow("Konum izni verilmedi. Ayarlar'dan izin ver.", "Location permission denied. Enable it in Settings."); return
        default: break
        }
        if snapshot.tracking == false, snapshot.canSave { completed = snapshot; write(completed, key: "route.completed") }
        snapshot = RouteSnapshot(id: UUID().uuidString.lowercased(), tracking: true, startedAtMs: nowMs(), activityType: activityType, startedUptime: uptimeMs())
        persist()
        resumeUpdates()
        if authorization == .authorizedWhenInUse { manager.requestAlwaysAuthorization() }
    }

    func pause() {
        guard snapshot.tracking, !snapshot.paused else { return }
        snapshot.paused = true; snapshot.pausedAtMs = nowMs(); snapshot.pausedUptime = uptimeMs()
        manager.stopUpdatingLocation(); manager.allowsBackgroundLocationUpdates = false
        persist()
    }

    func resume() {
        guard snapshot.tracking, snapshot.paused else { return }
        let delta = snapshot.pausedUptime > 0 && uptimeMs() >= snapshot.pausedUptime ? uptimeMs() - snapshot.pausedUptime : max(nowMs() - snapshot.pausedAtMs, 0)
        snapshot.paused = false; snapshot.pausedAtMs = 0; snapshot.pausedDurationMs += delta; snapshot.pausedUptime = 0
        persist(); resumeUpdates()
    }

    @discardableResult
    func stop() -> RouteSnapshot {
        var stopped = snapshot
        if stopped.tracking {
            let delta = stopped.paused ? (stopped.pausedUptime > 0 && uptimeMs() >= stopped.pausedUptime ? uptimeMs() - stopped.pausedUptime : max(nowMs() - stopped.pausedAtMs, 0)) : 0
            stopped.tracking = false; stopped.paused = false; stopped.stoppedAtMs = nowMs(); stopped.pausedDurationMs += delta; stopped.pausedUptime = 0; stopped.stoppedUptime = uptimeMs()
        }
        stopUpdates()
        if stopped.canSave { completed = stopped; write(stopped, key: "route.completed") }
        snapshot = RouteSnapshot(); persist()
        return stopped
    }

    func discard() { stopUpdates(); snapshot = RouteSnapshot(); persist() }

    func clearCompleted() { completed = RouteSnapshot(); defaults.removeObject(forKey: "route.completed") }

    private func resumeUpdates() {
        manager.allowsBackgroundLocationUpdates = true
        manager.showsBackgroundLocationIndicator = true
        manager.startUpdatingLocation()
    }

    private func stopUpdates() {
        manager.stopUpdatingLocation()
        manager.allowsBackgroundLocationUpdates = false
        manager.showsBackgroundLocationIndicator = false
    }

    // MARK: CLLocationManagerDelegate

    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let status = manager.authorizationStatus
        Task { @MainActor in
            self.authorization = status
            if let pending = self.pendingStart, status == .authorizedWhenInUse || status == .authorizedAlways { self.pendingStart = nil; self.start(activityType: pending) }
            else if status == .denied || status == .restricted { self.pendingStart = nil; self.error = trNow("Konum izni verilmedi. Ayarlar'dan izin ver.", "Location permission denied. Enable it in Settings.") }
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        Task { @MainActor in locations.forEach(self.handle) }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        let message = error.localizedDescription
        Task { @MainActor in self.error = message }
    }

    private func handle(_ location: CLLocation) {
        lastLocation = location
        gpsAccuracy = location.horizontalAccuracy >= 0 ? location.horizontalAccuracy : nil
        guard snapshot.tracking, !snapshot.paused else { return }
        let time = Int64(location.timestamp.timeIntervalSince1970 * 1000)
        guard isFreshRouteLocation(time), location.horizontalAccuracy >= 0, isPreciseRouteLocation(location.horizontalAccuracy) else { return }
        let point = RoutePoint(latitude: location.coordinate.latitude, longitude: location.coordinate.longitude, recordedAt: time, accuracyMeters: location.horizontalAccuracy,
                               altitudeMeters: location.verticalAccuracy >= 0 ? location.altitude : nil, speedMetersPerSecond: location.speed >= 0 ? location.speed : nil, bearingDegrees: location.course >= 0 ? location.course : nil)
        guard isValidRoutePoint(point) else { return }
        var extra = 0.0
        if let last = snapshot.points.last {
            guard let accepted = acceptedRouteSegmentMeters(last: last, next: point, activityType: snapshot.activityType) else { return }
            if accepted == 0 { return }
            extra = accepted
        }
        snapshot.distanceMeters = safeDistance(snapshot.distanceMeters + extra)
        snapshot.points.append(point)
        if snapshot.points.count > 12_000 { snapshot.points.removeFirst(snapshot.points.count - 12_000) }
        persist()
    }

    private func safeDistance(_ value: Double) -> Double { value.isFinite && value >= 0 ? value : 0 }

    // MARK: Kalıcılık

    private func persist() {
        write(snapshot, key: "route.active")
        let group = UserDefaults(suiteName: AppConfiguration.appGroup)
        group?.set(snapshot.tracking, forKey: "route_tracking")
        group?.set(snapshot.distanceMeters, forKey: "route_distance")
        group?.set(snapshot.activityType, forKey: "route_activity")
    }

    private func write(_ value: RouteSnapshot, key: String) {
        if let data = try? JSONEncoder().encode(value) { defaults.set(data, forKey: key) }
    }

    private static func read(_ key: String) -> RouteSnapshot {
        guard let data = UserDefaults.standard.data(forKey: key), let value = try? JSONDecoder().decode(RouteSnapshot.self, from: data) else { return RouteSnapshot() }
        return value
    }
}
