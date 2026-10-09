import SwiftUI

/// Manuel spor aktivitesi kaydı: tür seç → süre/mesafe/eğim → tahmini yakım → kaydet.
struct ManualActivityView: View {
    @Environment(AppModel.self) private var app
    @State private var selected: ManualActivityType?

    var body: some View {
        Group {
            if let selected {
                ActivityForm(activity: selected) { self.selected = nil }.id(selected.key)
            } else {
                ScreenScaffold(spacing: 14) {
                    HfScreenHeader(title: tr("Aktivite ekle", "Log activity")) { if !app.path.isEmpty { app.path.removeLast() } }
                    Text(tr("AKTİVİTE TÜRÜNÜ SEÇ", "SELECT ACTIVITY TYPE")).font(.hfLabel.weight(.bold)).foregroundStyle(HC.lime)
                    Text(tr("Bugün hangi sporu kaydediyorsun?", "What are you tracking today?")).font(.system(size: 26, weight: .bold)).foregroundStyle(HC.text)
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 8), count: 3), spacing: 8) {
                        ForEach(manualActivityTypes) { a in
                            HfCard(padding: 8, onTap: { selected = a }) {
                                VStack(spacing: 6) { Text(a.emoji).font(.system(size: 30)); Text(a.title).font(.hfLabel.weight(.semibold)).foregroundStyle(HC.text).multilineTextAlignment(.center).lineLimit(2) }
                                    .frame(maxWidth: .infinity, minHeight: 92)
                            }
                        }
                    }
                }
            }
        }
    }
}

private struct ActivityForm: View {
    @Environment(AppModel.self) private var app
    let activity: ManualActivityType
    var onBack: () -> Void
    @State private var duration = "60"
    @State private var distance = ""
    @State private var incline = "0"
    @State private var variantKey: String?
    @State private var notes = ""
    @State private var saving = false

    var body: some View {
        let minutes = min(max(Int(duration) ?? 0, 0), 600)
        let usesDistance = ["walking", "running", "cycling", "swimming", "hiking", "rowing"].contains(activity.key)
        let usesIncline = ["walking", "running", "hiking"].contains(activity.key)
        let entered = Double(distance.replacingOccurrences(of: ",", with: ".")).flatMap { $0 > 0 ? $0 : nil }.map { activity.key == "swimming" ? $0 / 1000 : $0 }
        let input = ManualActivityInput(activityKey: activity.key, durationMinutes: max(minutes, 1), distanceKm: entered, inclinePercent: min(max(Double(incline.replacingOccurrences(of: ",", with: ".")) ?? 0, 0), 40), variantKey: variantKey, notes: notes.trimmingCharacters(in: .whitespaces))
        let estimate = minutes > 0 ? estimateManualActivityEnergy(activity, input: input, weightKg: app.dashboard?.profile.weightKg) : nil
        ScreenScaffold(spacing: 14) {
            HfScreenHeader(title: tr("Aktivite ekle", "Log activity"), onBack: onBack) {}
            VStack(spacing: 8) {
                Text(activity.emoji).font(.system(size: 40)).frame(width: 78, height: 78).background(HC.surfaceHigh, in: Circle())
                Text(activity.title).font(.system(size: 26, weight: .bold)).foregroundStyle(HC.text)
            }.frame(maxWidth: .infinity)
            HfCard { HfField(title: tr("SÜRE (DK)", "DURATION (MIN)"), text: Binding(get: { duration }, set: { duration = String($0.filter(\.isNumber).prefix(3)) }), keyboard: .numberPad) }
            if usesDistance { HfCard { HfField(title: activity.key == "swimming" ? tr("MESAFE (METRE)", "DISTANCE (METERS)") : tr("MESAFE (KM)", "DISTANCE (KM)"), text: Binding(get: { distance }, set: { distance = String($0.filter { $0.isNumber || $0 == "," || $0 == "." }.prefix(7)) }), keyboard: .decimalPad); Text(tr("İsteğe bağlı, doğruluğu artırır", "Optional, improves accuracy")).font(.hfSmall).foregroundStyle(HC.muted) } }
            if usesIncline { HfCard { HfField(title: tr("ORTALAMA EĞİM (%)", "AVERAGE INCLINE (%)"), text: Binding(get: { incline }, set: { incline = String($0.filter { $0.isNumber || $0 == "," || $0 == "." }.prefix(4)) }), keyboard: .decimalPad) } }
            if !activity.variants.isEmpty {
                HfCard {
                    VStack(alignment: .leading, spacing: 8) {
                        Text(tr("AKTİVİTE DETAYI", "ACTIVITY TYPE")).font(.hfLabel.weight(.bold)).foregroundStyle(HC.textSecondary)
                        HfChipRow { ForEach(activity.variants, id: \.key) { v in HfChip(text: tr(v.titleTr, v.titleEn), selected: variantKey == v.key) { variantKey = v.key } } }
                    }
                }
            }
            HfCard {
                HStack(spacing: 12) {
                    Image(systemName: "flame.fill").foregroundStyle(HC.warning).frame(width: 48, height: 48).background(HC.warning.opacity(0.15), in: Circle())
                    VStack(alignment: .leading, spacing: 2) {
                        Text(tr("TAHMİNİ AKTİF YAKIM", "ESTIMATED ACTIVE BURN")).font(.hfLabel.weight(.bold)).foregroundStyle(HC.textSecondary)
                        Text("\(estimate?.activeCalories ?? 0) kcal").font(.hfHeadline.weight(.bold)).foregroundStyle(HC.text)
                        Text("MET \(String(format: "%.1f", estimate?.met ?? 0)) • " + (estimate?.confidence == "high" ? tr("Yüksek güven", "High confidence") : estimate?.confidence == "medium" ? tr("Orta güven", "Medium confidence") : tr("Düşük güven", "Low confidence"))).font(.hfSmall).foregroundStyle(HC.textSecondary)
                    }
                    Spacer()
                }
            }
            VStack(alignment: .leading, spacing: 6) {
                Text(tr("Seans notu (isteğe bağlı)", "Session notes (optional)")).font(.hfLabel.weight(.bold)).foregroundStyle(HC.textSecondary)
                TextEditor(text: Binding(get: { notes }, set: { notes = String($0.prefix(500)) })).scrollContentBackground(.hidden).frame(height: 90).padding(10).background(HC.surfaceHigh, in: RoundedRectangle(cornerRadius: 14)).foregroundStyle(HC.text)
                Text("\(notes.count)/500").font(.hfSmall).foregroundStyle(HC.muted).frame(maxWidth: .infinity, alignment: .trailing)
            }
            HfButton(title: saving ? tr("Kaydediliyor…", "Saving…") : tr("Aktiviteyi kaydet", "Save activity"), enabled: !saving && minutes > 0) {
                saving = true
                Task {
                    let ok = await app.recordManualActivity(input)
                    saving = false
                    if ok, !app.path.isEmpty { app.path.removeLast() }
                }
            }
        }
        .onAppear { variantKey = variantKey ?? activity.variants.first?.key }
    }
}
