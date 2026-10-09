import Foundation
import AVFoundation

struct RoutePlanRequest: Sendable {
    var activityType: String
    var goalType: String
    var goalValue: Double
    var planType = "loop"
}

enum ManeuverType: String, Sendable { case start, straight, slightLeft, left, sharpLeft, slightRight, right, sharpRight, arrive }

struct RouteManeuver: Sendable {
    var pointIndex: Int
    var type: ManeuverType
    var instruction: String
    var streetName = ""
}

struct PlannedRoute: Sendable {
    var points: [RoutePoint]
    var distanceMeters: Double
    var estimatedDurationSeconds: Int
    var requestedDistanceMeters: Double
    var isLoop = true
    var destination: RoutePoint?
    var maneuvers: [RouteManeuver] = []
}

struct RouteProgress: Sendable {
    var nearestPointIndex: Int
    var traveledMeters: Double
    var remainingMeters: Double
    var completionPercent: Int
    var distanceFromRouteMeters: Double
    var offRoute: Bool
    var nextManeuver: RouteManeuver?
    var distanceToManeuverMeters: Double
}

/// BRouter tabanlı rota planlama + ilerleme/manevra hesabı (Android `RoutePlanner`).
enum RoutePlanner {
    private static let baseURL = "https://brouter.de/brouter"
    private static let earthRadius = 6_371_000.0

    static func speedKmh(_ type: String) -> Double {
        switch type { case "Koşu": return 9; case "Trail Koşusu": return 7.5; case "Doğa Yürüyüşü": return 4.5; case "Bisiklet": return 18; case "Kayak": return 20; default: return 5 }
    }

    static func targetDistanceMeters(_ r: RoutePlanRequest) -> Double {
        let d = r.goalType == "time" ? speedKmh(r.activityType) * r.goalValue / 60 * 1000 : r.goalValue * 1000
        return min(max(d, 500), 50_000)
    }

    static func plan(origin: RoutePoint, request: RoutePlanRequest, destination: RoutePoint? = nil) async throws -> PlannedRoute {
        if request.planType == "point_to_point" {
            guard let end = destination else { throw AppError.message(trNow("Varış noktası seçilmedi.", "No destination selected.")) }
            return try await fetchRoute([origin, end], request.activityType, 0, false, end)
        }
        let target = targetDistanceMeters(request)
        let orientation = Double(Int(origin.latitude * 1000 + origin.longitude * 1000) % 360 + 360).truncatingRemainder(dividingBy: 360)
        let initialRadius = target / 3
        let first = try await fetchLoop(origin, initialRadius, orientation, request.activityType, target)
        if abs(first.distanceMeters - target) / target <= 0.12 { return first }
        let corrected = initialRadius * min(max(target / max(first.distanceMeters, 100), 0.45), 1.8)
        let second = try await fetchLoop(origin, corrected, orientation, request.activityType, target)
        return abs(second.distanceMeters - target) < abs(first.distanceMeters - target) ? second : first
    }

    private static func fetchLoop(_ origin: RoutePoint, _ radius: Double, _ bearing: Double, _ type: String, _ target: Double) async throws -> PlannedRoute {
        let a = destinationPoint(origin, radius, bearing), b = destinationPoint(origin, radius, bearing + 60)
        return try await fetchRoute([origin, a, b, origin], type, target, true, origin)
    }

    private static func fetchRoute(_ waypoints: [RoutePoint], _ type: String, _ target: Double, _ isLoop: Bool, _ destination: RoutePoint) async throws -> PlannedRoute {
        let coords = waypoints.map { "\($0.longitude),\($0.latitude)" }.joined(separator: "|")
        let profile = type == "Bisiklet" ? "fastbike" : "hiking-mountain"
        var comps = URLComponents(string: baseURL)!
        comps.queryItems = [.init(name: "lonlats", value: coords), .init(name: "profile", value: profile), .init(name: "alternativeidx", value: "0"), .init(name: "format", value: "geojson"), .init(name: "timode", value: "1")]
        var request = URLRequest(url: comps.url!, timeoutInterval: 18)
        request.setValue("application/geo+json, application/json", forHTTPHeaderField: "Accept")
        request.setValue("Hedefit/0.2 iOS route planner", forHTTPHeaderField: "User-Agent")
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else { throw AppError.message(trNow("Rota servisi şu anda yanıt vermiyor.", "The route service isn't responding right now.")) }
        let root = JSON.parse(data)
        let feature = root["features"].items.first ?? .null
        guard feature.isObject else { throw AppError.message(trNow("Bu konum için uygun rota bulunamadı.", "No suitable route found for this location.")) }
        let raw = feature["geometry"]["coordinates"].items
        let now = nowMs()
        let all: [RoutePoint] = raw.enumerated().compactMap { i, c in
            let a = c.items
            guard a.count >= 2, let lon = a[0].doubleValue, let lat = a[1].doubleValue else { return nil }
            return RoutePoint(latitude: lat, longitude: lon, recordedAt: now + Int64(i), altitudeMeters: a.count > 2 ? a[2].doubleValue : nil)
        }
        guard all.count >= 2 else { throw AppError.message(trNow("Rota çizgisi alınamadı.", "Couldn't read the route line.")) }
        let serverDistance = Double(feature["properties"].string("track-length"))
        let distance = serverDistance ?? zip(all, all.dropFirst()).reduce(0) { $0 + geoDistanceMeters($1.0, $1.1) }
        let points: [RoutePoint] = all.count <= 2000 ? all : { let step = all.count / 2000 + 1; return all.enumerated().filter { $0.offset % step == 0 }.map(\.element) + [all.last!] }()
        return PlannedRoute(points: points, distanceMeters: distance, estimatedDurationSeconds: max(Int(distance / (speedKmh(type) * 1000 / 3600)), 1), requestedDistanceMeters: target > 0 ? target : distance,
                            isLoop: isLoop, destination: destination, maneuvers: parseManeuvers(feature, points))
    }

    static func destinationPoint(_ origin: RoutePoint, _ distance: Double, _ bearingDeg: Double) -> RoutePoint {
        let ang = distance / earthRadius, b = bearingDeg * .pi / 180, lat = origin.latitude * .pi / 180, lon = origin.longitude * .pi / 180
        let dLat = asin(sin(lat) * cos(ang) + cos(lat) * sin(ang) * cos(b))
        let dLon = lon + atan2(sin(b) * sin(ang) * cos(lat), cos(ang) - sin(lat) * sin(dLat))
        return RoutePoint(latitude: dLat * 180 / .pi, longitude: dLon * 180 / .pi, recordedAt: nowMs(), altitudeMeters: origin.altitudeMeters)
    }

    static func progress(_ route: PlannedRoute, _ position: RoutePoint?, offRouteThreshold: Double = 45) -> RouteProgress {
        guard let position, !route.points.isEmpty else { return RouteProgress(nearestPointIndex: 0, traveledMeters: 0, remainingMeters: route.distanceMeters, completionPercent: 0, distanceFromRouteMeters: .infinity, offRoute: false, nextManeuver: route.maneuvers.first, distanceToManeuverMeters: route.distanceMeters) }
        if route.points.count == 1 {
            let d = geoDistanceMeters(position, route.points[0])
            return RouteProgress(nearestPointIndex: 0, traveledMeters: 0, remainingMeters: route.distanceMeters, completionPercent: 0, distanceFromRouteMeters: d, offRoute: d > offRouteThreshold, nextManeuver: route.maneuvers.first, distanceToManeuverMeters: route.distanceMeters)
        }
        let lengths = zip(route.points, route.points.dropFirst()).map { geoDistanceMeters($0, $1) }
        var cumulative = [Double](repeating: 0, count: route.points.count)
        for (i, l) in lengths.enumerated() { cumulative[i + 1] = cumulative[i] + l }
        let geometry = max(cumulative.last ?? 1, 1)
        var best = 0, bestDist = Double.infinity
        for i in lengths.indices { let d = project(position, route.points[i], route.points[i + 1]).distance; if d < bestDist { bestDist = d; best = i } }
        let proj = project(position, route.points[best], route.points[best + 1])
        let traveledGeo = cumulative[best] + lengths[best] * proj.fraction
        let scale = route.distanceMeters / geometry
        let traveled = min(max(traveledGeo * scale, 0), route.distanceMeters)
        let remaining = max(route.distanceMeters - traveled, 0)
        let nearest = best + (proj.fraction >= 0.5 ? 1 : 0)
        let next = route.maneuvers.first { $0.pointIndex > best } ?? route.maneuvers.last
        let toTurn = next.map { max(cumulative[min(max($0.pointIndex, 0), cumulative.count - 1)] - traveledGeo, 0) * scale } ?? remaining
        return RouteProgress(nearestPointIndex: nearest, traveledMeters: traveled, remainingMeters: remaining, completionPercent: route.distanceMeters > 0 ? min(max(Int((traveled / route.distanceMeters * 100).rounded()), 0), 100) : 0,
                             distanceFromRouteMeters: proj.distance, offRoute: proj.distance > offRouteThreshold, nextManeuver: next, distanceToManeuverMeters: toTurn)
    }

    private static func project(_ p: RoutePoint, _ s: RoutePoint, _ e: RoutePoint) -> (fraction: Double, distance: Double) {
        let refLat = ((s.latitude + e.latitude + p.latitude) / 3) * .pi / 180
        func x(_ lon: Double) -> Double { (lon - s.longitude) * .pi / 180 * earthRadius * cos(refLat) }
        func y(_ lat: Double) -> Double { (lat - s.latitude) * .pi / 180 * earthRadius }
        let ex = x(e.longitude), ey = y(e.latitude), px = x(p.longitude), py = y(p.latitude)
        let sq = ex * ex + ey * ey
        let f = sq <= 0 ? 0 : min(max((px * ex + py * ey) / sq, 0), 1)
        return (f, hypot(px - ex * f, py - ey * f))
    }

    static func parseManeuvers(_ feature: JSON, _ points: [RoutePoint]) -> [RouteManeuver] {
        let props = feature["properties"]
        let hints = props["voicehints"].items.isEmpty ? props["voiceHints"].items : props["voicehints"].items
        let fromService: [RouteManeuver] = hints.compactMap { hint in
            if hint.isObject {
                let idx = hint.intOrNil("i") ?? hint.intOrNil("index") ?? -1
                guard points.indices.contains(idx) else { return nil }
                let type = maneuverType(hint.string("cmd", hint.string("command")))
                let street = hint.string("streetname", hint.string("street", hint.string("name")))
                let msg = hint.string("message")
                return RouteManeuver(pointIndex: idx, type: type, instruction: msg.isEmpty ? instruction(type) : msg, streetName: street)
            }
            let a = hint.items
            guard a.count >= 2, let idx = a[0].intValue, points.indices.contains(idx) else { return nil }
            let type = maneuverType(a[1].intValue ?? 1)
            return RouteManeuver(pointIndex: idx, type: type, instruction: instruction(type))
        }
        if !fromService.isEmpty {
            var seen = Set<Int>()
            let mid = fromService.sorted { $0.pointIndex < $1.pointIndex }.filter { seen.insert($0.pointIndex).inserted && $0.pointIndex != 0 && $0.pointIndex != points.count - 1 }
            return [RouteManeuver(pointIndex: 0, type: .start, instruction: instruction(.start))] + mid + [RouteManeuver(pointIndex: points.count - 1, type: .arrive, instruction: instruction(.arrive))]
        }
        return geometryManeuvers(points)
    }

    static func geometryManeuvers(_ points: [RoutePoint]) -> [RouteManeuver] {
        guard points.count >= 2 else { return [] }
        var result = [RouteManeuver(pointIndex: 0, type: .start, instruction: instruction(.start))]
        var lastTurn = 0
        if points.count > 3 {
            for i in 2..<(points.count - 1) {
                if i - lastTurn < 3 { continue }
                let incoming = bearing(points[i - 2], points[i]), outgoing = bearing(points[i], points[min(i + 2, points.count - 1)])
                let delta = (outgoing - incoming + 540).truncatingRemainder(dividingBy: 360) - 180
                let type: ManeuverType?
                if delta <= -100 { type = .sharpLeft } else if delta <= -45 { type = .left } else if delta <= -22 { type = .slightLeft }
                else if delta >= 100 { type = .sharpRight } else if delta >= 45 { type = .right } else if delta >= 22 { type = .slightRight } else { type = nil }
                if let type { result.append(RouteManeuver(pointIndex: i, type: type, instruction: instruction(type))); lastTurn = i }
            }
        }
        result.append(RouteManeuver(pointIndex: points.count - 1, type: .arrive, instruction: instruction(.arrive)))
        return result
    }

    private static func maneuverType(_ c: String) -> ManeuverType {
        switch c.lowercased() {
        case "tl", "left": return .left; case "tsll", "slight_left": return .slightLeft; case "tshl", "sharp_left": return .sharpLeft
        case "tr", "right": return .right; case "tslr", "slight_right": return .slightRight; case "tshr", "sharp_right": return .sharpRight
        case "finish", "arrive": return .arrive; default: return .straight
        }
    }
    private static func maneuverType(_ c: Int) -> ManeuverType {
        switch c { case 2: return .left; case 3: return .slightLeft; case 4: return .sharpLeft; case 5: return .right; case 6: return .slightRight; case 7: return .sharpRight; default: return .straight }
    }

    static func announcementThreshold(_ d: Double) -> Int? { d <= 25 ? 25 : d <= 80 ? 80 : d <= 200 ? 200 : nil }

    static func instruction(_ t: ManeuverType, english: Bool = false) -> String {
        if english {
            switch t { case .start: return "Start on the route"; case .straight: return "Continue straight"; case .slightLeft: return "Bear left"; case .left: return "Turn left"; case .sharpLeft: return "Make a sharp left"
            case .slightRight: return "Bear right"; case .right: return "Turn right"; case .sharpRight: return "Make a sharp right"; case .arrive: return "You have reached your destination" }
        }
        switch t { case .start: return "Rotada ilerle"; case .straight: return "Düz devam et"; case .slightLeft: return "Hafif sola yönel"; case .left: return "Sola dön"; case .sharpLeft: return "Keskin sola dön"
        case .slightRight: return "Hafif sağa yönel"; case .right: return "Sağa dön"; case .sharpRight: return "Keskin sağa dön"; case .arrive: return "Hedefine ulaştın" }
    }

    private static func bearing(_ a: RoutePoint, _ b: RoutePoint) -> Double {
        let l1 = a.latitude * .pi / 180, l2 = b.latitude * .pi / 180, dl = (b.longitude - a.longitude) * .pi / 180
        let y = sin(dl) * cos(l2), x = cos(l1) * sin(l2) - sin(l1) * cos(l2) * cos(dl)
        return (atan2(y, x) * 180 / .pi + 360).truncatingRemainder(dividingBy: 360)
    }
}

/// Sesli yönlendirme (AVSpeechSynthesizer).
@MainActor final class NavigationVoice {
    private let synth = AVSpeechSynthesizer()
    func speak(_ text: String) {
        let u = AVSpeechUtterance(string: text); u.voice = AVSpeechSynthesisVoice(language: LangStore.english ? "en-US" : "tr-TR")
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .voicePrompt, options: [.duckOthers, .interruptSpokenAudioAndMixWithOthers])
        try? AVAudioSession.sharedInstance().setActive(true)
        synth.speak(u)
    }
    func stop() { synth.stopSpeaking(at: .immediate); try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation) }
}
