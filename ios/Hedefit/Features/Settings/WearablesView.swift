import SwiftUI

private struct WatchBrand {
    let key: String, name: String, appName: String
    /// Apple Sağlık'ta kaynak adında geçen anahtar kelimeler
    let match: [String]
    let supported: Bool
    let stepsTr: [String], stepsEn: [String]
}

private let watchBrands: [WatchBrand] = [
    .init(key: "apple", name: "Apple Watch", appName: "Apple Sağlık", match: ["watch", "apple"], supported: true,
          stepsTr: ["Saatini iPhone'daki Watch uygulamasıyla eşleştir.", "Ayarlar → Gizlilik ve Güvenlik → Sağlık → Hedefit'ten adım, uyku, nabız ve kaloriye izin ver.", "Hedefit'te \"İzinleri ver\"e dokun."],
          stepsEn: ["Pair the watch with the Watch app on iPhone.", "Settings → Privacy & Security → Health → Hedefit: allow steps, sleep, heart rate and calories.", "Tap \"Grant permissions\" in Hedefit."]),
    .init(key: "garmin", name: "Garmin", appName: "Garmin Connect", match: ["garmin"], supported: true,
          stepsTr: ["Saatini Garmin Connect uygulamasına ekle.", "Garmin Connect: Diğer → Ayarlar → Bağlı Uygulamalar → Apple Sağlık.", "Paylaşılacak verileri aç.", "Hedefit'te \"İzinleri ver\"e dokun."],
          stepsEn: ["Add your watch in Garmin Connect.", "Garmin Connect: More → Settings → Connected Apps → Apple Health.", "Turn on the data to share.", "Tap \"Grant permissions\" in Hedefit."]),
    .init(key: "fitbit", name: "Fitbit / Pixel Watch", appName: "Fitbit", match: ["fitbit"], supported: true,
          stepsTr: ["Saatini Fitbit uygulamasına ekle.", "Fitbit: Profil → Ayarlar → Apple Sağlık ile paylaş.", "Hedefit'te \"İzinleri ver\"e dokun."],
          stepsEn: ["Add your watch in the Fitbit app.", "Fitbit: Profile → Settings → Share with Apple Health.", "Tap \"Grant permissions\" in Hedefit."]),
    .init(key: "oura", name: "Oura / WHOOP", appName: "Oura / WHOOP", match: ["oura", "whoop"], supported: true,
          stepsTr: ["Cihaz uygulamasında Apple Sağlık entegrasyonunu aç.", "Uyku, nabız ve adım verilerini paylaşmaya izin ver.", "Hedefit'te \"İzinleri ver\"e dokun."],
          stepsEn: ["Turn on the Apple Health integration in the device's app.", "Allow sharing sleep, heart rate and steps.", "Tap \"Grant permissions\" in Hedefit."]),
    .init(key: "xiaomi", name: "Xiaomi / Amazfit", appName: "Zepp / Mi Fitness", match: ["zepp", "amazfit", "mi fitness", "xiaomi"], supported: true,
          stepsTr: ["Zepp / Mi Fitness uygulamasında Apple Sağlık senkronizasyonunu aç.", "Hedefit'te \"İzinleri ver\"e dokun."],
          stepsEn: ["Turn on Apple Health sync in Zepp / Mi Fitness.", "Tap \"Grant permissions\" in Hedefit."]),
    .init(key: "samsung", name: "Samsung Galaxy Watch", appName: "Samsung Health", match: ["samsung"], supported: false,
          stepsTr: ["Galaxy Watch iPhone ile çalışmaz; Apple Sağlık'a veri göndermiyor."], stepsEn: ["Galaxy Watch doesn't work with iPhone and doesn't write to Apple Health."]),
    .init(key: "huawei", name: "Huawei", appName: "Huawei Health", match: ["huawei"], supported: false,
          stepsTr: ["Huawei Health şu an Apple Sağlık'a veri göndermiyor; bu yüzden Huawei saatler doğrudan bağlanamıyor."], stepsEn: ["Huawei Health doesn't share data with Apple Health yet, so Huawei watches can't connect directly."]),
]

private func relativeTime(_ date: Date) -> String {
    let minutes = max(Int(Date().timeIntervalSince(date) / 60), 0)
    if minutes < 1 { return tr("az önce", "just now") }
    if minutes < 60 { return tr("\(minutes) dk önce", "\(minutes)m ago") }
    if minutes < 1440 { return tr("\(minutes / 60) sa önce", "\(minutes / 60)h ago") }
    return tr("\(minutes / 1440) gün önce", "\(minutes / 1440)d ago")
}

/// Akıllı saatler: Apple Sağlık üzerinden gelen veriler ve bağlı cihazlar.
struct WearablesView: View {
    @Environment(AppModel.self) private var app
    @State private var snapshot: WearableSnapshot?
    @State private var busy = false
    @State private var error: String?
    @State private var expanded: String?
    @State private var needsAuth = false

    var body: some View {
        let d = app.dashboard
        ScreenScaffold(spacing: 12) {
            HfScreenHeader(title: tr("Akıllı saatler", "Smart watches"), onBack: { if !app.path.isEmpty { app.path.removeLast() } }) {
                if busy { ProgressView().tint(HC.lime) } else { HfCircleButton(system: "arrow.clockwise", label: tr("Yenile", "Refresh")) { Task { await refresh() } } }
            }
            HfCard {
                VStack(alignment: .leading, spacing: 12) {
                    HStack(spacing: 12) {
                        HfIconBadge(system: "heart.fill", tint: HC.lime, size: 40, radius: 20)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(tr("Apple Sağlık", "Apple Health")).font(.hfTitleM.weight(.heavy)).foregroundStyle(HC.text)
                            let connected = (snapshot?.sources.isEmpty == false)
                            Text(needsAuth ? tr("İzin gerekli", "Permission needed") : connected ? tr("\(snapshot!.sources.count) kaynak veri gönderiyor", "\(snapshot!.sources.count) sources sending data") : tr("Saat verisi bekleniyor", "Waiting for watch data")).font(.hfBody.weight(.bold)).foregroundStyle(connected && !needsAuth ? HC.lime : HC.warning)
                        }
                    }
                    if !(HealthAvailability.available) { Text(tr("Sağlık verileri bu cihazda kullanılamıyor.", "Health data isn't available on this device.")).foregroundStyle(HC.textSecondary) }
                    else if needsAuth || snapshot?.latestHeartRate == nil { HfButton(title: tr("İzinleri ver", "Grant permissions"), icon: "checkmark.circle.fill") { Task { await grant() } } }
                    if let error { Text(error).foregroundStyle(HC.coral) }
                    Text(tr("Apple, okuma iznini gizlilik gereği göstermez. Veri gelmiyorsa Ayarlar → Gizlilik ve Güvenlik → Sağlık → Hedefit'i kontrol et.", "Apple doesn't reveal read permission for privacy. If no data arrives, check Settings → Privacy & Security → Health → Hedefit.")).font(.hfLabel).foregroundStyle(HC.muted)
                }
            }
            HStack(spacing: 10) {
                HfStatTile(label: tr("Nabız", "Heart rate"), value: snapshot?.latestHeartRate.map { "\($0) bpm" } ?? "—", valueColor: HC.coral)
                HfStatTile(label: tr("Dinlenik", "Resting"), value: snapshot?.restingHeartRate.map { "\($0) bpm" } ?? "—")
            }
            HStack(spacing: 10) {
                HfStatTile(label: tr("Adım", "Steps"), value: (d?.steps ?? 0).formatted(), valueColor: HC.water)
                let sleep = d?.sleepMinutes ?? 0
                HfStatTile(label: tr("Uyku", "Sleep"), value: sleep > 0 ? "\(sleep / 60)s \(sleep % 60)dk" : "—", valueColor: HC.sleep)
            }
            if let devices = snapshot?.devices, !devices.isEmpty {
                HfSectionHeader(title: tr("Bağlı cihazlar", "Connected devices"))
                ForEach(devices) { dev in HfNavRow(icon: "applewatch", tint: HC.lime, title: dev.label, subtitle: nil, chevron: false, action: nil) { Text(relativeTime(dev.lastSeen)).font(.hfBody.weight(.bold)).foregroundStyle(HC.lime) } }
            }
            HfSectionHeader(title: tr("Markalar", "Brands"))
            ForEach(watchBrands, id: \.key) { brand in
                let lastSeen = lastSeen(brand)
                let open = expanded == brand.key
                HfCard(padding: 16, onTap: { expanded = open ? nil : brand.key }) {
                    VStack(alignment: .leading, spacing: 10) {
                        HStack(spacing: 12) {
                            HfIconBadge(system: "applewatch", tint: lastSeen != nil ? HC.lime : HC.textSecondary, size: 38, radius: 19)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(brand.name).font(.hfTitleM.weight(.heavy)).foregroundStyle(HC.text)
                                Text(lastSeen != nil ? tr("Bağlı • \(relativeTime(lastSeen!))", "Connected • \(relativeTime(lastSeen!))") : !brand.supported ? tr("Şu an desteklenmiyor", "Not supported yet") : tr("Bağlı değil", "Not connected")).font(.hfLabel.weight(.bold)).foregroundStyle(lastSeen != nil ? HC.lime : !brand.supported ? HC.textSecondary : HC.warning)
                            }
                            Spacer(); Image(systemName: open ? "chevron.up" : "chevron.down").foregroundStyle(HC.textSecondary)
                        }
                        if open {
                            ForEach(Array((LangStore.english ? brand.stepsEn : brand.stepsTr).enumerated()), id: \.offset) { i, s in
                                HStack(alignment: .top, spacing: 10) {
                                    if brand.supported { Text("\(i + 1)").font(.hfLabel.weight(.heavy)).foregroundStyle(HC.lime).frame(width: 24, height: 24).background(HC.lime.opacity(0.16), in: Circle()) }
                                    Text(s).font(.hfBody).foregroundStyle(HC.text)
                                }
                            }
                            if brand.supported { HfButton(title: tr("İzinler", "Permissions"), secondary: true) { Task { await grant() } } }
                        }
                    }
                }
            }
        }
        .task { await refresh() }
    }

    private func lastSeen(_ brand: WatchBrand) -> Date? {
        var dates: [Date] = []
        for (name, date) in snapshot?.sources ?? [:] where brand.match.contains(where: { name.lowercased().contains($0) }) && !(brand.key == "apple" && name.lowercased() == "hedefit") { dates.append(date) }
        for dev in snapshot?.devices ?? [] where brand.match.contains(where: { dev.label.lowercased().contains($0) }) { dates.append(dev.lastSeen) }
        return dates.max()
    }

    private func refresh() async {
        busy = true; defer { busy = false }
        needsAuth = await HealthService.shared.needsAuthorization()
        if !needsAuth { snapshot = await HealthService.shared.wearableSources() }
        await app.syncHealth(silent: true)
    }

    private func grant() async {
        busy = true; defer { busy = false }
        do { try await HealthService.shared.authorize(); error = nil } catch { self.error = error.friendly }
        needsAuth = await HealthService.shared.needsAuthorization()
        snapshot = await HealthService.shared.wearableSources()
        await app.syncHealth(silent: true)
    }
}

import HealthKit
private enum HealthAvailability { static var available: Bool { HKHealthStore.isHealthDataAvailable() } }
