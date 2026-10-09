import SwiftUI
import MapKit
import CoreLocation

let routeActivities: [(String, String, String, String)] = [
    ("Koşu", "figure.run", "Koşu", "Run"), ("Yürüyüş", "figure.walk", "Yürüyüş", "Walk"), ("Trail Koşusu", "mountain.2.fill", "Trail koşusu", "Trail run"),
    ("Doğa Yürüyüşü", "figure.hiking", "Doğa yürüyüşü", "Hike"), ("Bisiklet", "figure.outdoor.cycle", "Bisiklet", "Ride"), ("Kayak", "figure.skiing.downhill", "Kayak", "Ski"),
]

func routeActivityLabel(_ type: String) -> String {
    switch type.lowercased() {
    case "run", "running", "koşu": return tr("Koşu", "Run")
    case "trail run", "trail_running", "trail koşusu": return tr("Trail Koşusu", "Trail run")
    case "hike", "hiking", "doğa yürüyüşü": return tr("Doğa Yürüyüşü", "Hike")
    case "ride", "cycling", "bisiklet": return tr("Bisiklet", "Ride")
    case "ski", "skiing", "kayak": return tr("Kayak", "Ski")
    default: return tr("Yürüyüş", "Walk")
    }
}
func routeActivityEmoji(_ type: String) -> String {
    switch type.lowercased() {
    case "run", "running", "koşu": return "🏃"; case "trail run", "trail_running", "trail koşusu": return "⛰️"; case "hike", "hiking", "doğa yürüyüşü": return "🥾"
    case "ride", "cycling", "bisiklet": return "🚴"; case "ski", "skiing", "kayak": return "⛷️"; default: return "🚶"
    }
}
private func defaultActivityTitle(_ type: String) -> String {
    let h = Calendar.current.component(.hour, from: Date())
    let period = h < 12 ? tr("Sabah", "Morning") : h < 18 ? tr("Öğleden Sonra", "Afternoon") : tr("Akşam", "Evening")
    let t: String
    switch type { case "Koşu": t = tr("Koşusu", "Run"); case "Trail Koşusu": t = tr("Trail Koşusu", "Trail Run"); case "Bisiklet": t = tr("Bisikleti", "Ride"); case "Kayak": t = tr("Kayağı", "Ski"); case "Doğa Yürüyüşü": t = tr("Doğa Yürüyüşü", "Hike"); default: t = tr("Yürüyüşü", "Walk") }
    return "\(period) \(t)"
}
private func estimatedCalories(_ s: RouteSnapshot) -> Int {
    Int(s.distanceMeters / 1000 * ([ "Bisiklet": 28.0, "Kayak": 35.0, "Koşu": 62.0, "Trail Koşusu": 62.0 ][s.activityType] ?? 45.0))
}
private func coord(_ p: RoutePoint) -> CLLocationCoordinate2D { CLLocationCoordinate2D(latitude: p.latitude, longitude: p.longitude) }

/// Rota çizgisi + başlangıç/bitiş + konum noktalarıyla harita.
struct RouteMapView: View {
    var points: [RoutePoint]
    var current: RoutePoint? = nil
    var follow = false
    @State private var camera: MapCameraPosition = .automatic
    @State private var following = true

    var body: some View {
        Map(position: $camera) {
            if points.count >= 2 {
                MapPolyline(coordinates: points.map(coord)).stroke(HC.lime, style: StrokeStyle(lineWidth: 6, lineCap: .round, lineJoin: .round))
            }
            if let first = points.first { Annotation("", coordinate: coord(first)) { Circle().fill(.white).frame(width: 14, height: 14).overlay(Circle().stroke(HC.lime, lineWidth: 3)) } }
            if let last = points.last, points.count > 1 { Annotation("", coordinate: coord(last)) { Circle().fill(HC.lime).frame(width: 16, height: 16).overlay(Circle().stroke(.white, lineWidth: 3)) } }
            if let current { Annotation("", coordinate: coord(current)) { Circle().fill(Color(hex: 0x4C9AFF)).frame(width: 18, height: 18).overlay(Circle().stroke(.white, lineWidth: 3)) } }
            UserAnnotation()
        }
        .mapStyle(.standard(elevation: .flat, pointsOfInterest: .excludingAll))
        .mapControls { MapCompass() }
        .overlay(alignment: .trailing) {
            if follow && !following { Button(tr("Konuma dön", "Recenter")) { following = true; recenter() }.font(.hfBody.weight(.semibold)).foregroundStyle(HC.lime).padding(.horizontal, 14).padding(.vertical, 10).background(HC.surface.opacity(0.93), in: Capsule()).padding(.trailing, 12) }
        }
        .onAppear { fit() }
        .onChange(of: points.count) { _, _ in if follow { if following { recenter() } } else { fit() } }
        .onMapCameraChange(frequency: .onEnd) { _ in }
        .simultaneousGesture(DragGesture(minimumDistance: 8).onChanged { _ in if follow { following = false } })
    }

    private func fit() {
        guard points.count >= 2 else { if let c = current ?? points.first { camera = .region(MKCoordinateRegion(center: coord(c), latitudinalMeters: 600, longitudinalMeters: 600)) }; return }
        let lats = points.map(\.latitude), lons = points.map(\.longitude)
        let center = CLLocationCoordinate2D(latitude: (lats.min()! + lats.max()!) / 2, longitude: (lons.min()! + lons.max()!) / 2)
        let span = MKCoordinateSpan(latitudeDelta: max((lats.max()! - lats.min()!) * 1.5, 0.003), longitudeDelta: max((lons.max()! - lons.min()!) * 1.5, 0.003))
        camera = .region(MKCoordinateRegion(center: center, span: span))
    }
    private func recenter() {
        if let c = current { camera = .camera(MapCamera(centerCoordinate: coord(c), distance: 700)) } else { camera = .userLocation(fallback: .automatic) }
    }
}

/// Hedefit Rota: yeni aktivite (GPS kaydı + rota planlama) ve yapılanlar.
struct RouteView: View {
    @Environment(AppModel.self) private var app
    private var tracker = RouteTracker.shared
    @State private var section = 0
    @State private var activityType = "Koşu"
    @State private var finished: RouteSnapshot?
    @State private var activityTitle = ""
    @State private var countdown: Int?
    @State private var plannedRoute: PlannedRoute?
    @State private var planBusy = false
    @State private var routeMessage: String?
    @State private var showPlanner = false
    @State private var showExit = false
    @State private var showFinish = false
    @State private var plannerOrigin: RoutePoint?
    @State private var plannerDestination: RoutePoint?
    @State private var picker: PickerTarget?
    @State private var lastReroute = Date.distantPast
    @State private var spoken: Set<String> = []
    @State private var sharing = false
    @State private var tick = 0
    private let voice = NavigationVoice()

    enum PickerTarget: Identifiable { case start, destination; var id: Int { self == .start ? 0 : 1 } }

    private var snapshot: RouteSnapshot { tracker.snapshot }
    private var inProgress: Bool { snapshot.tracking }
    private var shown: RouteSnapshot { inProgress ? snapshot : (finished ?? snapshot) }
    private var current: RoutePoint? { snapshot.points.last }

    var body: some View {
        let _ = tick
        Group {
            if !inProgress && finished == nil && section == 1 { historyPage } else { activityPage }
        }
        .background(HC.bg.ignoresSafeArea())
        .onAppear {
            if let completed = Optional(tracker.completed), completed.canSave, finished == nil, !inProgress { finished = completed; activityTitle = defaultActivityTitle(completed.activityType) }
            if inProgress { activityType = snapshot.activityType }
        }
        .task { while !Task.isCancelled { try? await Task.sleep(for: .seconds(1)); tick &+= 1 } }
        .onChange(of: tracker.snapshot.points.last?.recordedAt) { _, _ in navigationTick() }
        .task(id: countdown) {
            guard let v = countdown else { return }
            try? await Task.sleep(for: .seconds(1))
            if v > 1 { countdown = v - 1 } else { tracker.start(activityType: activityType); finished = nil; activityTitle = ""; countdown = nil }
        }
        .sheet(isPresented: $showPlanner) { RoutePlannerSheet(activityType: activityType, busy: planBusy, start: plannerOrigin, destination: plannerDestination,
            onUseCurrent: { plannerOrigin = nil }, onPick: { t in showPlanner = false; DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { picker = t } }, onPlan: { request in showPlanner = false; Task { await generate(request) } }) }
        .fullScreenCover(item: $picker) { target in
            LocationPickerView(initial: (target == .start ? (plannerOrigin ?? plannerDestination) : (plannerDestination ?? plannerOrigin)) ?? current, title: target == .start ? tr("Başlangıç noktasını seç", "Choose starting point") : tr("Varış noktasını seç", "Choose destination")) { point in
                if target == .start { plannerOrigin = point } else { plannerDestination = point }
                picker = nil; DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { showPlanner = true }
            } onCancel: { picker = nil; DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { showPlanner = true } }
        }
        .alert(tr("Rota kaydından çıkılsın mı?", "Leave route recording?"), isPresented: $showExit) {
            Button(tr("Kayda devam et", "Keep recording"), role: .cancel) {}
            if snapshot.canSave { Button(tr("Sil", "Discard"), role: .destructive) { tracker.discard(); back() } }
            Button(snapshot.canSave ? tr("Bitir ve kaydet", "Finish and save") : tr("Rotayı sil", "Discard route")) {
                if snapshot.canSave { let final = tracker.stop(); finished = final; activityTitle = defaultActivityTitle(final.activityType) } else { tracker.discard(); back() }
            }
        } message: { Text(snapshot.canSave ? tr("Bu rotayı kaydet, sil veya kayda devam et.", "Save this route, discard it, or keep recording.") : tr("Bu rota kaydetmek için çok kısa ve silinecek.", "This route is too short to save and will be discarded.")) }
        .alert(tr("Aktiviteyi bitirmek istiyor musun?", "Finish activity?"), isPresented: $showFinish) {
            Button(tr("Devam Et", "Continue"), role: .cancel) {}
            Button(tr("Aktiviteyi Bitir", "Finish activity"), role: .destructive) { let final = tracker.stop(); finished = final; activityTitle = defaultActivityTitle(final.activityType); voice.stop() }
        } message: { Text(tr("Kaydedilen rota adlandırmaya, kaydetmeye ve paylaşmaya hazır olacak.", "The recorded route will be ready to name, save and share.")) }
    }

    private func back() { if !app.path.isEmpty { app.path.removeLast() } }

    // MARK: Ana sayfa

    private var activityPage: some View {
        ZStack(alignment: .bottom) {
            mapLayer.ignoresSafeArea()
            VStack(spacing: 10) {
                HStack(spacing: 12) {
                    HfCircleButton(system: "chevron.left", label: tr("Geri", "Back")) { if inProgress { showExit = true } else { back() } }
                    VStack(alignment: .leading, spacing: 0) {
                        Text(tr("Hedefit Rota", "Hedefit Route")).font(.hfTitleL.weight(.heavy)).foregroundStyle(HC.text)
                        Text(statusText).font(.hfSmall).foregroundStyle(HC.lime)
                    }
                    Spacer()
                }.padding(6).background(HC.surface, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
                if !inProgress && finished == nil && plannedRoute == nil { sectionPicker }
                if let m = tracker.error { notice(m, warning: true) }
                if let routeMessage { notice(routeMessage, warning: plannedRoute == nil) }
                if !inProgress && finished == nil && plannedRoute == nil { Spacer(); choosePanel; Spacer().frame(height: 330) }
                else { Spacer() }
            }.padding(16)
            if planBusy { Color.black.opacity(0.72).ignoresSafeArea().overlay { VStack(spacing: 12) { ProgressView().tint(HC.lime); Text(tr("Uygun parkur bulunuyor…", "Finding a suitable loop…")).foregroundStyle(.white) } } }
            bottomPanel
            if let v = countdown { Color.black.opacity(0.92).ignoresSafeArea().overlay { Text(v > 0 ? "\(v)" : tr("BAŞLA", "GO")).font(.system(size: 120, weight: .black)).foregroundStyle(HC.lime) } }
        }
    }

    @ViewBuilder private var mapLayer: some View {
        if inProgress { RouteMapView(points: plannedRoute?.points ?? snapshot.points, current: current, follow: true) }
        else if let finished { RouteMapView(points: finished.points) }
        else if let plannedRoute { RouteMapView(points: plannedRoute.points, current: current) }
        else { HC.bg }
    }

    private var statusText: String {
        if inProgress && snapshot.points.isEmpty { return tr("Hassas GPS sinyali aranıyor…", "Acquiring precise GPS signal…") }
        if snapshot.status == .paused { return tr("Duraklatıldı", "Paused") }
        if inProgress { return tr("Arka planda kaydediliyor", "Recording in background") }
        if finished != nil { return tr("Kaydetmeye hazır", "Ready to save") }
        return tr("GPS aktivitesi", "GPS activity")
    }

    private var sectionPicker: some View {
        HStack(spacing: 4) {
            ForEach([(0, tr("Yeni aktivite", "New activity")), (1, tr("Yapılanlar", "Completed"))], id: \.0) { i, l in
                Button { section = i } label: { Text(l).font(.hfBody.weight(.bold)).foregroundStyle(section == i ? HC.onLime : HC.textSecondary).frame(maxWidth: .infinity, minHeight: 40).background(section == i ? HC.lime : .clear, in: Capsule()) }.buttonStyle(.plain)
            }
        }.padding(4).background(HC.surface, in: Capsule())
    }

    private var choosePanel: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(tr("Aktiviteni seç", "Choose an activity")).font(.hfTitleL.weight(.heavy)).foregroundStyle(HC.text).padding(.leading, 4)
            LazyVGrid(columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)], spacing: 10) {
                ForEach(routeActivities, id: \.0) { key, icon, trL, enL in
                    let on = activityType == key
                    Button { activityType = key } label: {
                        HStack(spacing: 10) {
                            Image(systemName: icon).font(.system(size: 18)).foregroundStyle(on ? HC.onLime : HC.lime).frame(width: 36, height: 36).background(on ? HC.onLime.opacity(0.12) : HC.lime.opacity(0.14), in: Circle())
                            Text(tr(trL, enL)).font(.hfBody.weight(.bold)).foregroundStyle(on ? HC.onLime : HC.text).lineLimit(1)
                            Spacer(minLength: 0)
                        }.padding(.horizontal, 14).padding(.vertical, 14).background(on ? HC.lime : HC.surface, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                    }.buttonStyle(.plain)
                }
            }
            Button { showPlanner = true } label: {
                HStack(spacing: 12) {
                    HfIconBadge(system: "point.topleft.down.to.point.bottomright.curvepath.fill", tint: HC.lime, size: 44, radius: 22)
                    VStack(alignment: .leading, spacing: 2) { Text(tr("Rota planla", "Plan a route")).font(.hfBody.weight(.black)).foregroundStyle(HC.text); Text(tr("Süreye/mesafeye göre dönüşlü ya da A → B", "Loop by time or distance, or A → B")).font(.hfSmall).foregroundStyle(HC.textSecondary).multilineTextAlignment(.leading) }
                    Spacer(); Image(systemName: "chevron.right").foregroundStyle(HC.lime)
                }.padding(16).background(HC.surface, in: RoundedRectangle(cornerRadius: 20, style: .continuous)).overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).stroke(HC.lime.opacity(0.45)))
            }.buttonStyle(.plain)
        }
    }

    private func notice(_ text: String, warning: Bool) -> some View {
        HStack(spacing: 10) { Image(systemName: warning ? "info.circle" : "point.topleft.down.to.point.bottomright.curvepath").foregroundStyle(warning ? HC.warning : HC.lime); Text(text).font(.hfSmall).foregroundStyle(HC.text); Spacer(minLength: 0) }
            .padding(12).background(HC.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    // MARK: Alt panel

    @ViewBuilder private var bottomPanel: some View {
        if section == 0 || inProgress || finished != nil {
            let plan = finished == nil ? plannedRoute : nil
            let bike = activityType == "Bisiklet" || activityType == "Kayak"
            VStack(spacing: 12) {
                Capsule().fill(HC.divider).frame(width: 42, height: 4)
                if inProgress {
                    if let plannedRoute { navCard(plannedRoute) }
                    HStack(spacing: 0) {
                        metric(tr("MESAFE", "DISTANCE"), Units.formatDistance(shown.distanceMeters, app.units))
                        metric(tr("SÜRE", "TIME"), formatDuration(shown.durationSeconds))
                        metric(bike ? tr("HIZ", "SPEED") : tr("TEMPO", "PACE"), bike ? String(format: "%.1f km/sa", shown.currentSpeedKmh) : Units.formatPace(shown.displayPaceSecondsPerKm, app.units))
                    }.padding(.vertical, 12).background(HC.surfaceHigh, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                } else if finished == nil && plan == nil {
                    Label(tr("Kayıt arka planda sürer; ekranı kapatabilirsin.", "Recording continues in the background; screen can be off."), systemImage: "location.fill").font(.hfSmall).foregroundStyle(HC.textSecondary)
                } else {
                    HStack(spacing: 0) {
                        metric(tr("MESAFE", "DISTANCE"), Units.formatDistance(plan?.distanceMeters ?? shown.distanceMeters, app.units))
                        metric(tr("SÜRE", "TIME"), formatDuration(plan?.estimatedDurationSeconds ?? shown.durationSeconds))
                        metric(plan != nil ? tr("HEDEF", "TARGET") : (bike ? tr("HIZ", "SPEED") : tr("TEMPO", "PACE")),
                               plan != nil ? Units.formatDistance(plan!.requestedDistanceMeters, app.units) : (bike ? String(format: "%.1f km/sa", shown.averageSpeedKmh) : Units.formatPace(shown.displayPaceSecondsPerKm, app.units)))
                    }.padding(.vertical, 12).background(HC.surfaceHigh, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                }
                if inProgress && !snapshot.paused { HfButton(title: tr("Duraklat", "Pause"), icon: "pause.fill") { tracker.pause() } }
                else if inProgress { HStack(spacing: 10) { HfButton(title: tr("Bitir", "Finish"), secondary: true) { showFinish = true }; HfButton(title: tr("Devam Et", "Continue"), icon: "play.fill") { tracker.resume() } } }
                else if finished == nil { HfButton(title: plannedRoute != nil ? tr("Planlı Rotayı Başlat", "Start planned route") : tr("GPS Kaydını Başlat", "Start GPS Recording"), icon: "figure.run") { begin() } }
                if !inProgress && finished == nil && plannedRoute != nil {
                    HStack(spacing: 8) {
                        Button(tr("Planı temizle", "Clear plan")) { plannedRoute = nil; routeMessage = nil }.font(.hfBody.weight(.bold)).foregroundStyle(HC.coral).frame(maxWidth: .infinity, minHeight: 46).background(HC.surfaceHigh, in: RoundedRectangle(cornerRadius: 14))
                        Button(tr("Hedefi değiştir", "Change target")) { showPlanner = true }.font(.hfBody.weight(.bold)).foregroundStyle(HC.lime).frame(maxWidth: .infinity, minHeight: 46).background(HC.surfaceHigh, in: RoundedRectangle(cornerRadius: 14))
                    }
                }
                if let completed = finished { finishedBlock(completed) }
            }.padding(16).background(HC.surface.clipShape(UnevenRoundedRectangle(topLeadingRadius: 26, topTrailingRadius: 26)).ignoresSafeArea(edges: .bottom))
        }
    }

    private func finishedBlock(_ c: RouteSnapshot) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(tr("Rotayı tamamladın", "Route completed")).font(.hfTitleL).foregroundStyle(HC.lime)
            Text("\(Units.formatDistance(c.distanceMeters, app.units)) • \(formatDuration(c.durationSeconds)) • \(Units.formatPace(c.paceSecondsPerKm, app.units)) • \(estimatedCalories(c)) kcal").font(.hfSmall).foregroundStyle(HC.textSecondary)
            HfField(title: tr("Aktivite adı", "Activity name"), text: Binding(get: { activityTitle }, set: { activityTitle = String($0.prefix(80)) }))
            HfButton(title: tr("Aktiviteyi Kaydet", "Save activity")) {
                let title = activityTitle.isEmpty ? defaultActivityTitle(c.activityType) : activityTitle
                Task { await app.saveRoute(c, activityType: c.activityType, title: title) }
                tracker.clearCompleted(); finished = nil; plannedRoute = nil; routeMessage = nil; section = 1
            }
            HfButton(title: tr("Paylaşım kartı", "Share card"), icon: "square.and.arrow.up", secondary: true) { sharing = true }
        }
        .sheet(isPresented: $sharing) { RouteShareSheet(snapshot: c, title: activityTitle.isEmpty ? defaultActivityTitle(c.activityType) : activityTitle, calories: estimatedCalories(c)) }
    }

    private func metric(_ l: String, _ v: String) -> some View {
        VStack(spacing: 2) { Text(v).font(.system(size: 20, weight: .heavy)).foregroundStyle(HC.text).minimumScaleFactor(0.6).lineLimit(1); Text(l).font(.system(size: 10, weight: .bold)).foregroundStyle(HC.textSecondary) }.frame(maxWidth: .infinity)
    }

    private func navCard(_ route: PlannedRoute) -> some View {
        let startDistance = current.map { geoDistanceMeters($0, route.points[0]) }
        let joining = (startDistance ?? 0) > 80
        let p = RoutePlanner.progress(route, current)
        let remaining = current == nil ? route.distanceMeters : joining ? (startDistance ?? 0) : p.remainingMeters
        let en = LangStore.english
        let instruction = current == nil ? tr("Hassas GPS konumu alınıyor…", "Getting your precise GPS location…") : joining ? tr("Planlanan rota başlangıcına ilerle", "Head to the planned route start") : p.offRoute ? tr("Rotadan çıktın • yeniden hesaplanıyor", "Off route • recalculating") : (p.nextManeuver.map { RoutePlanner.instruction($0.type, english: en) } ?? tr("Rotada devam et", "Continue on the route"))
        return VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 12) {
                Image(systemName: "location.north.fill").foregroundStyle(HC.onLime).frame(width: 44, height: 44).background(HC.lime, in: Circle())
                VStack(alignment: .leading, spacing: 0) {
                    Text(instruction).font(.hfTitleM.weight(.black)).foregroundStyle(HC.text).lineLimit(2)
                    Text(p.nextManeuver?.streetName.isEmpty == false ? p.nextManeuver!.streetName : tr("Planlanan rota", "Planned route")).font(.hfSmall).foregroundStyle(HC.textSecondary).lineLimit(1)
                }
                Spacer()
                Text(Units.formatDistance(joining ? remaining : p.distanceToManeuverMeters, app.units)).font(.hfTitleL.weight(.black)).foregroundStyle(HC.lime)
            }
            if !joining {
                ProgressView(value: Double(p.completionPercent) / 100).tint(HC.lime)
                HStack { Text(tr("\(Units.formatDistance(p.traveledMeters, app.units)) tamamlandı", "\(Units.formatDistance(p.traveledMeters, app.units)) done")); Spacer(); Text(tr("\(Units.formatDistance(remaining, app.units)) kaldı • %\(p.completionPercent)", "\(Units.formatDistance(remaining, app.units)) left • \(p.completionPercent)%")) }.font(.hfLabel).foregroundStyle(HC.textSecondary)
            }
        }.padding(14).background(HC.lime.opacity(0.12), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }

    // MARK: Mantık

    private func begin() {
        guard CLLocationManager.locationServicesEnabled() else { tracker.error = tr("Konum servisleri kapalı.", "Location services are turned off."); return }
        switch tracker.authorization {
        case .notDetermined: tracker.requestPermission(); Task { for _ in 0..<60 { try? await Task.sleep(for: .milliseconds(500)); if tracker.authorization != .notDetermined { break } }; if tracker.hasLocationPermission { countdown = 3 } }
        case .denied, .restricted: tracker.error = tr("Konum izni verilmedi. Ayarlar'dan izin ver.", "Location permission denied. Enable it in Settings.")
        default: tracker.error = nil; routeMessage = nil; countdown = 3
        }
    }

    private func generate(_ request: RoutePlanRequest) async {
        planBusy = true; routeMessage = nil; defer { planBusy = false }
        do {
            var origin = plannerOrigin
            if origin == nil {
                if !tracker.hasLocationPermission { tracker.requestPermission(); try? await Task.sleep(for: .seconds(1.5)) }
                guard let loc = tracker.lastLocation ?? CLLocationManager().location else { throw AppError.message(tr("Konum alınamadı. Haritadan bir başlangıç seç ya da konum iznini kontrol et.", "Couldn't get your location. Pick a start on the map or check location permission.")) }
                origin = RoutePoint(latitude: loc.coordinate.latitude, longitude: loc.coordinate.longitude, recordedAt: nowMs(), accuracyMeters: loc.horizontalAccuracy)
            }
            let plan = try await RoutePlanner.plan(origin: origin!, request: request, destination: plannerDestination)
            plannedRoute = plan; spoken = []; activityType = request.activityType
            routeMessage = tr("\(Units.formatDistance(plan.distanceMeters, app.units)) \(plan.isLoop ? "dönüşlü rota" : "rota") hazır. Başlamadan önce geçişleri ve zemin koşullarını kontrol et.", "\(Units.formatDistance(plan.distanceMeters, app.units)) \(plan.isLoop ? "loop route" : "route") ready. Check crossings and surface before you start.")
        } catch { routeMessage = error.friendly }
    }

    private func navigationTick() {
        guard let position = current, let route = plannedRoute, inProgress, !snapshot.paused else { return }
        let p = RoutePlanner.progress(route, position)
        if let threshold = RoutePlanner.announcementThreshold(p.distanceToManeuverMeters), let m = p.nextManeuver {
            let cue = "\(m.pointIndex):\(threshold)"
            if !spoken.contains(cue) {
                spoken.insert(cue)
                let street = m.streetName.isEmpty ? "" : " \(m.streetName)"
                voice.speak(LangStore.english ? "In \(threshold) meters, \(RoutePlanner.instruction(m.type, english: true))\(street)" : "\(threshold) metre sonra \(RoutePlanner.instruction(m.type))\(street)")
            }
        }
        if p.remainingMeters <= 25, geoDistanceMeters(position, route.destination ?? route.points.last!) <= 30 {
            let final = tracker.stop(); finished = final; activityTitle = defaultActivityTitle(final.activityType); routeMessage = tr("Rotayı tamamladın", "Route completed")
        } else if p.offRoute, Date().timeIntervalSince(lastReroute) >= 30 {
            lastReroute = Date(); routeMessage = tr("Rotadan çıktın. Yeni rota hesaplanıyor…", "You are off route. Recalculating…")
            let dest = route.destination ?? route.points.last!
            Task {
                do { plannedRoute = try await RoutePlanner.plan(origin: position, request: RoutePlanRequest(activityType: activityType, goalType: "distance", goalValue: p.remainingMeters / 1000, planType: "point_to_point"), destination: dest); spoken = []; routeMessage = nil }
                catch { routeMessage = tr("Yeni rota hesaplanamadı. Güvenliyse rotaya doğru ilerle.", "Rerouting failed. Continue toward the route when safe.") }
            }
        }
    }

    // MARK: Yapılanlar

    private var historyPage: some View {
        ScreenScaffold(spacing: 12) {
            HfScreenHeader(title: tr("Yapılanlar", "Completed activities")) { back() }
            sectionPicker
            let routes = (app.dashboard?.routeActivities ?? []).sorted { $0.startedAt > $1.startedAt }
            if routes.isEmpty { HfEmptyState(icon: "figure.run", title: tr("Henüz tamamlanan rota yok", "No completed routes yet"), message: "") }
            ForEach(routes) { r in
                HfCard(padding: 14, onTap: { app.push(.routeDetail(r.id)) }) {
                    HStack(spacing: 12) {
                        Text(routeActivityEmoji(r.activityType)).font(.system(size: 22)).frame(width: 44, height: 44).background(HC.lime.opacity(0.14), in: Circle())
                        VStack(alignment: .leading, spacing: 3) {
                            Text(r.title.isEmpty ? routeActivityLabel(r.activityType) : r.title).font(.hfTitleM).foregroundStyle(HC.text).lineLimit(1)
                            Text("\(routeActivityLabel(r.activityType)) • \(ISO.date(r.startedAt)?.formatted(.dateTime.day().month(.abbreviated).year().hour().minute().locale(AppLang.shared.locale)) ?? r.startedAt)").font(.hfSmall).foregroundStyle(HC.textSecondary)
                        }
                        Spacer()
                        VStack(alignment: .trailing, spacing: 2) { Text(Units.formatDistance(r.distanceMeters, app.units)).font(.hfLabel.weight(.bold)).foregroundStyle(HC.lime); Text(formatDuration(r.movingDurationSeconds)).font(.hfSmall).foregroundStyle(HC.textSecondary) }
                    }
                }
            }
        }
    }
}

// MARK: - Detay

struct RouteDetailView: View {
    @Environment(AppModel.self) private var app
    let id: String
    @State private var confirmDelete = false

    var body: some View {
        if let r = app.dashboard?.routeActivities.first(where: { $0.id == id }) {
            ScreenScaffold(spacing: 14) {
                HfScreenHeader(title: r.title.isEmpty ? routeActivityLabel(r.activityType) : r.title) { if !app.path.isEmpty { app.path.removeLast() } }
                RouteMapView(points: r.routePoints).frame(height: 260).clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                HfCard {
                    HStack {
                        stat(tr("Mesafe", "Distance"), Units.formatDistance(r.distanceMeters, app.units)); Spacer()
                        stat(tr("Süre", "Time"), formatDuration(r.movingDurationSeconds)); Spacer()
                        stat(tr("Tempo", "Pace"), Units.formatPace(r.averagePaceSecondsPerKm, app.units))
                    }
                }
                Text("\(ISO.date(r.startedAt)?.formatted(.dateTime.day().month(.wide).year().hour().minute().locale(AppLang.shared.locale)) ?? r.startedAt) • \(r.calories) kcal").font(.hfSmall).foregroundStyle(HC.textSecondary)
                HfButton(title: tr("Sil", "Delete"), secondary: true) { confirmDelete = true }
            }
            .alert(tr("Bu rota silinsin mi?", "Delete this route?"), isPresented: $confirmDelete) {
                Button(tr("Vazgeç", "Cancel"), role: .cancel) {}
                Button(tr("Sil", "Delete"), role: .destructive) { Task { await app.deleteRoute(r); if !app.path.isEmpty { app.path.removeLast() } } }
            } message: { Text(tr("Rota ve aktivite ayrıntıları kalıcı olarak silinecek.", "The route and its activity details will be permanently deleted.")) }
        } else { Placeholder(title: tr("Rota", "Route")) }
    }
    private func stat(_ l: String, _ v: String) -> some View { VStack(spacing: 2) { Text(v).font(.hfTitleM.weight(.bold)).foregroundStyle(HC.lime); Text(l).font(.hfSmall).foregroundStyle(HC.textSecondary) } }
}

// MARK: - Planlayıcı

struct RoutePlannerSheet: View {
    @Environment(\.dismiss) private var dismiss
    let activityType: String
    let busy: Bool
    let start: RoutePoint?
    let destination: RoutePoint?
    var onUseCurrent: () -> Void
    var onPick: (RouteView.PickerTarget) -> Void
    var onPlan: (RoutePlanRequest) -> Void
    @State private var planType = "loop"
    @State private var goalType = "time"
    @State private var value = 30.0

    private var range: ClosedRange<Double> { goalType == "time" ? 10...240 : 0.5...50 }
    private var step: Double { goalType == "time" ? 5 : 0.5 }
    private func label(_ p: RoutePoint) -> String { String(format: "%.5f, %.5f", p.latitude, p.longitude) }

    var body: some View {
        let valid = planType == "point_to_point" ? destination != nil : range.contains(value)
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    HfSegmented(options: [tr("Dönüşlü", "Loop"), "A → B"], selection: Binding(get: { planType == "loop" ? 0 : 1 }, set: { planType = $0 == 0 ? "loop" : "point_to_point" }))
                    Text(planType == "loop" ? tr("Başladığın yerde biter", "Ends where you start") : tr("Başka bir noktada biter", "Ends at another point")).font(.hfSmall).foregroundStyle(HC.textSecondary)
                    VStack(alignment: .leading, spacing: 14) {
                        stop("location.fill", tr("Başlangıç", "Start"), start.map(label) ?? tr("Mevcut konumum", "My current location")) {
                            chip(tr("Konumum", "My location"), "location.fill") { onUseCurrent() }
                            chip(tr("Haritadan", "On map"), "map") { onPick(.start) }
                        }
                        if planType == "point_to_point" {
                            stop("flag.fill", tr("Varış", "Destination"), destination.map(label) ?? tr("Henüz seçilmedi", "Not selected yet")) { chip(tr("Haritadan seç", "Choose on map"), "map") { onPick(.destination) } }
                        }
                    }.padding(16).frame(maxWidth: .infinity, alignment: .leading).background(HC.surface, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
                    if planType == "loop" {
                        Text(tr("HEDEF", "GOAL")).font(.hfLabel.weight(.black)).foregroundStyle(HC.muted)
                        HfSegmented(options: [tr("Süre", "Duration"), tr("Mesafe", "Distance")], selection: Binding(get: { goalType == "time" ? 0 : 1 }, set: { goalType = $0 == 0 ? "time" : "distance"; value = $0 == 0 ? 30 : 5 }))
                        VStack(spacing: 14) {
                            HStack {
                                stepper("minus") { value = min(max(value - step, range.lowerBound), range.upperBound) }
                                HStack(alignment: .lastTextBaseline, spacing: 4) {
                                    Text(goalType == "time" || value.truncatingRemainder(dividingBy: 1) == 0 ? "\(Int(value))" : String(format: "%.1f", value)).font(.system(size: 52, weight: .black)).foregroundStyle(HC.text)
                                    Text(goalType == "time" ? tr("dk", "min") : "km").font(.system(size: 20, weight: .bold)).foregroundStyle(HC.textSecondary)
                                }.frame(maxWidth: .infinity)
                                stepper("plus") { value = min(max(value + step, range.lowerBound), range.upperBound) }
                            }
                            HStack(spacing: 6) {
                                ForEach(goalType == "time" ? [20.0, 30, 45, 60] : [3.0, 5, 10, 21], id: \.self) { p in
                                    Button { value = p } label: { Text("\(Int(p)) \(goalType == "time" ? tr("dk", "min") : "km")").font(.hfLabel.weight(.bold)).foregroundStyle(value == p ? HC.onLime : HC.text).frame(maxWidth: .infinity, minHeight: 38).background(value == p ? HC.lime : HC.surfaceHigh, in: Capsule()) }.buttonStyle(.plain)
                                }
                            }
                            Text(goalType == "time" ? tr("10–240 dk arası", "10–240 min") : tr("0,5–50 km arası", "0.5–50 km")).font(.system(size: 11)).foregroundStyle(HC.muted)
                        }.padding(16).background(HC.surface, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
                    }
                    VStack(alignment: .leading, spacing: 4) {
                        Text(planType == "loop" ? tr("Başlangıcına dönen parkur; mesafe biraz değişebilir.", "Loops back to your start; distance may vary.") : tr("İki nokta arasında rota oluşturulur.", "A route between your two points.")).font(.hfSmall).foregroundStyle(HC.textSecondary)
                        Text(tr("Noktalar açık BRouter servisine gönderilir.", "Points go to the open BRouter service.")).font(.system(size: 11)).foregroundStyle(HC.muted)
                    }
                }.padding(20)
            }.background(HC.bg)
            .safeAreaInset(edge: .bottom) {
                HfButton(title: busy ? tr("Planlanıyor…", "Planning…") : (planType == "point_to_point" && destination == nil ? tr("Önce varış noktası seç", "Choose a destination") : tr("Rota oluştur", "Create route")), enabled: valid && !busy) {
                    onPlan(RoutePlanRequest(activityType: activityType, goalType: goalType, goalValue: planType == "loop" ? value : 0, planType: planType))
                }.padding(20).background(HC.bg)
            }
            .navigationTitle(tr("Rotanı planla", "Plan your route")).navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button { dismiss() } label: { Image(systemName: "xmark") } } }
        }.presentationDetents([.large])
    }

    private func stop<C: View>(_ icon: String, _ label: String, _ value: String, @ViewBuilder actions: () -> C) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: icon).foregroundStyle(HC.lime).frame(width: 30, height: 30).background(HC.lime.opacity(0.14), in: Circle())
            VStack(alignment: .leading, spacing: 6) { Text(label).font(.hfLabel.weight(.bold)).foregroundStyle(HC.textSecondary); Text(value).font(.hfBody.weight(.semibold)).foregroundStyle(HC.text); HStack(spacing: 8) { actions() } }
        }
    }
    private func chip(_ t: String, _ icon: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) { Label(t, systemImage: icon).font(.hfLabel.weight(.bold)).foregroundStyle(HC.lime).padding(.horizontal, 12).padding(.vertical, 7).background(HC.lime.opacity(0.14), in: Capsule()) }.buttonStyle(.plain)
    }
    private func stepper(_ icon: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) { Image(systemName: icon).font(.system(size: 20, weight: .bold)).foregroundStyle(HC.lime).frame(width: 52, height: 52).background(HC.lime.opacity(0.16), in: Circle()) }.buttonStyle(.plain)
    }
}

/// Haritadan nokta seçimi: harita kaydırılır, ortadaki iğne seçilir.
struct LocationPickerView: View {
    let initial: RoutePoint?
    let title: String
    var onConfirm: (RoutePoint) -> Void
    var onCancel: () -> Void
    @State private var camera: MapCameraPosition = .automatic
    @State private var center: CLLocationCoordinate2D?

    var body: some View {
        ZStack {
            Map(position: $camera) { UserAnnotation() }
                .onMapCameraChange(frequency: .continuous) { ctx in center = ctx.region.center }
                .mapStyle(.standard(elevation: .flat, pointsOfInterest: .excludingAll))
            Image(systemName: "mappin").font(.system(size: 36, weight: .bold)).foregroundStyle(HC.lime).shadow(radius: 4).offset(y: -18)
            VStack {
                HStack { Button { onCancel() } label: { Image(systemName: "xmark").foregroundStyle(HC.text).frame(width: 44, height: 44).background(HC.surface, in: Circle()) }; Text(title).font(.hfTitleM.weight(.bold)).foregroundStyle(HC.text).padding(.horizontal, 14).padding(.vertical, 10).background(HC.surface, in: Capsule()); Spacer() }.padding(16)
                Spacer()
                HfButton(title: tr("Bu noktayı seç", "Use this point")) { if let c = center { onConfirm(RoutePoint(latitude: c.latitude, longitude: c.longitude, recordedAt: nowMs())) } }.padding(20)
            }
        }
        .onAppear {
            if let i = initial { camera = .region(MKCoordinateRegion(center: CLLocationCoordinate2D(latitude: i.latitude, longitude: i.longitude), latitudinalMeters: 1500, longitudinalMeters: 1500)) }
            else { camera = .userLocation(fallback: .automatic) }
        }
    }
}

// MARK: - Paylaşım kartı

struct RouteShareSheet: View {
    @Environment(\.dismiss) private var dismiss
    let snapshot: RouteSnapshot
    let title: String
    let calories: Int
    @State private var image: UIImage?

    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                if let image { Image(uiImage: image).resizable().scaledToFit().clipShape(RoundedRectangle(cornerRadius: 14)) }
                if let image { ShareLink(item: Image(uiImage: image), preview: SharePreview(title, image: Image(uiImage: image))) { Label(tr("Paylaş", "Share"), systemImage: "square.and.arrow.up").font(.hfBody.weight(.bold)).foregroundStyle(HC.onLime).frame(maxWidth: .infinity, minHeight: 54).background(HC.lime, in: RoundedRectangle(cornerRadius: 18)) } }
            }.padding(20).background(HC.bg)
            .navigationTitle(tr("Paylaşım kartı", "Share card")).navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button(tr("Kapat", "Close")) { dismiss() } } }
        }.presentationDetents([.large]).task { render() }
    }

    @MainActor private func render() {
        let card = VStack(alignment: .leading, spacing: 24) {
            Text("HEDEFİT").font(.system(size: 36, weight: .heavy)).foregroundStyle(Color(red: 0.71, green: 1, blue: 0.17))
            Text(title.uppercased()).font(.system(size: 64, weight: .heavy)).foregroundStyle(.white).minimumScaleFactor(0.5)
            RoutePolyline(points: snapshot.points, lineWidth: 10).frame(height: 760)
            Text(Units.formatDistance(snapshot.distanceMeters, AppModel.shared.units)).font(.system(size: 96, weight: .heavy)).foregroundStyle(Color(red: 0.71, green: 1, blue: 0.17))
            Text("\(formatDuration(snapshot.durationSeconds))  •  \(Units.formatPace(snapshot.paceSecondsPerKm, AppModel.shared.units))  •  \(calories) kcal").font(.system(size: 40, weight: .bold)).foregroundStyle(.white.opacity(0.75))
        }.padding(80).frame(width: 1080, height: 1920, alignment: .topLeading).background(Color(red: 0.03, green: 0.04, blue: 0.035))
        let renderer = ImageRenderer(content: card); renderer.scale = 1
        image = renderer.uiImage
    }
}
