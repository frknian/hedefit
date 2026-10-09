import SwiftUI
import Charts

/// `muscle_anatomy.png` (kare) üzerindeki normalize elips merkezleri/yarıçapları (Android `AnatomyRegions.kt`).
struct AnatomicalRegion { let muscle: String; let x: Double, y: Double, rx: Double, ry: Double }

let muscleRegionsFront: [AnatomicalRegion] = [
    .init(muscle: "neck", x: 0.312, y: 0.152, rx: 0.028, ry: 0.020),
    .init(muscle: "front_delts", x: 0.258, y: 0.192, rx: 0.030, ry: 0.023), .init(muscle: "front_delts", x: 0.360, y: 0.192, rx: 0.032, ry: 0.023),
    .init(muscle: "side_delts", x: 0.207, y: 0.208, rx: 0.026, ry: 0.024), .init(muscle: "side_delts", x: 0.421, y: 0.208, rx: 0.026, ry: 0.024),
    .init(muscle: "chest", x: 0.312, y: 0.245, rx: 0.072, ry: 0.032),
    .init(muscle: "biceps", x: 0.183, y: 0.285, rx: 0.024, ry: 0.038), .init(muscle: "biceps", x: 0.438, y: 0.285, rx: 0.024, ry: 0.038),
    .init(muscle: "abs", x: 0.315, y: 0.345, rx: 0.048, ry: 0.054),
    .init(muscle: "forearms", x: 0.150, y: 0.400, rx: 0.026, ry: 0.042), .init(muscle: "forearms", x: 0.462, y: 0.400, rx: 0.026, ry: 0.042),
    .init(muscle: "abductors", x: 0.228, y: 0.512, rx: 0.022, ry: 0.030), .init(muscle: "abductors", x: 0.396, y: 0.512, rx: 0.022, ry: 0.030),
    .init(muscle: "adductors", x: 0.285, y: 0.548, rx: 0.022, ry: 0.038), .init(muscle: "adductors", x: 0.340, y: 0.548, rx: 0.022, ry: 0.038),
    .init(muscle: "quads", x: 0.252, y: 0.578, rx: 0.042, ry: 0.052), .init(muscle: "quads", x: 0.373, y: 0.578, rx: 0.042, ry: 0.052),
    .init(muscle: "calves", x: 0.245, y: 0.748, rx: 0.031, ry: 0.040), .init(muscle: "calves", x: 0.377, y: 0.748, rx: 0.031, ry: 0.040),
]
let muscleRegionsBack: [AnatomicalRegion] = [
    .init(muscle: "neck", x: 0.687, y: 0.152, rx: 0.026, ry: 0.020),
    .init(muscle: "traps", x: 0.688, y: 0.196, rx: 0.052, ry: 0.026),
    .init(muscle: "rear_delts", x: 0.584, y: 0.212, rx: 0.030, ry: 0.024), .init(muscle: "rear_delts", x: 0.790, y: 0.212, rx: 0.030, ry: 0.024),
    .init(muscle: "upper_back", x: 0.689, y: 0.272, rx: 0.068, ry: 0.030),
    .init(muscle: "triceps", x: 0.568, y: 0.292, rx: 0.024, ry: 0.038), .init(muscle: "triceps", x: 0.813, y: 0.292, rx: 0.026, ry: 0.038),
    .init(muscle: "lats", x: 0.688, y: 0.345, rx: 0.076, ry: 0.042),
    .init(muscle: "forearms", x: 0.542, y: 0.400, rx: 0.026, ry: 0.042), .init(muscle: "forearms", x: 0.844, y: 0.400, rx: 0.026, ry: 0.042),
    .init(muscle: "lower_back", x: 0.687, y: 0.437, rx: 0.048, ry: 0.030),
    .init(muscle: "glutes", x: 0.687, y: 0.503, rx: 0.078, ry: 0.038),
    .init(muscle: "abductors", x: 0.604, y: 0.532, rx: 0.022, ry: 0.030), .init(muscle: "abductors", x: 0.772, y: 0.532, rx: 0.022, ry: 0.030),
    .init(muscle: "adductors", x: 0.652, y: 0.578, rx: 0.021, ry: 0.036), .init(muscle: "adductors", x: 0.722, y: 0.578, rx: 0.021, ry: 0.036),
    .init(muscle: "hamstrings", x: 0.628, y: 0.605, rx: 0.040, ry: 0.048), .init(muscle: "hamstrings", x: 0.746, y: 0.605, rx: 0.040, ry: 0.048),
    .init(muscle: "calves", x: 0.621, y: 0.735, rx: 0.031, ry: 0.040), .init(muscle: "calves", x: 0.754, y: 0.735, rx: 0.031, ry: 0.040),
]
let muscleRegions = muscleRegionsFront + muscleRegionsBack

@MainActor func loadColor(_ level: LoadLevel) -> Color {
    switch level {
    case .none: return Color(hex: 0x303532); case .low: return Color(hex: 0x9ACE83); case .balanced: return HC.lime
    case .high: return Color(hex: 0x36C95A); case .overload: return HC.coral
    }
}
@MainActor func loadStatus(_ level: LoadLevel) -> String {
    switch level {
    case .none: return tr("Veri yok", "No data"); case .low: return tr("Düşük", "Low"); case .balanced: return tr("Dengeli", "Balanced")
    case .high: return tr("Yüksek", "High"); case .overload: return tr("Aşırı yük", "Overload")
    }
}

/// Kas Atlası: tamamlanan setlerden tahmini kas yükü + haftalık hacim.
struct MuscleAtlasView: View {
    @Environment(AppModel.self) private var app
    var body: some View {
        ScreenScaffold(spacing: 16) {
            VStack(alignment: .leading, spacing: 2) {
                HfScreenHeader(title: tr("Kas Atlası", "Muscle Atlas")) { if !app.path.isEmpty { app.path.removeLast() } }
                Text(tr("Kas gelişim haritan", "Your muscle development map")).font(.hfSmall).foregroundStyle(HC.muted).padding(.leading, 56)
            }
            TrainingAnalysisSection(performances: app.dashboard?.exercisePerformance ?? [], catalog: app.dashboard?.exerciseCatalog ?? [])
        }
    }
}

struct TrainingAnalysisSection: View {
    let performances: [ExercisePerformance]
    var catalog: [ExerciseCatalogItem] = []
    @State private var range = 0
    @State private var selected: MuscleLoad?

    var body: some View {
        let days = [7, 30, 90][range]
        let analysis = analyzeTraining(performances, catalog: catalog, rangeDays: days)
        VStack(spacing: 14) {
            HfCard {
                VStack(alignment: .leading, spacing: 14) {
                    Text(tr("Tamamlanan setlerden tahmini antrenman yükünü gösterir; fiziksel kas büyüklüğü ölçümü değildir.", "Estimated training stimulus from completed sets; this is not a physical muscle-size measurement.")).font(.hfSmall).foregroundStyle(HC.textSecondary)
                    HfSegmented(options: [tr("Bu hafta", "This week"), tr("Son 30 gün", "30 days"), tr("Son 3 ay", "3 months")], selection: $range)
                    if analysis.muscleLoads.allSatisfy({ $0.level == .none }) { Text(tr("Henüz yeterli antrenman verin yok. Kas haritasını açmak için ilk setini tamamla.", "There is not enough workout data yet. Complete your first set to unlock the map.")).foregroundStyle(HC.textSecondary) }
                    figure(analysis)
                    ForEach(analysis.muscleLoads.filter { $0.level != .none }.prefix(5)) { load in
                        Button { selected = load } label: {
                            HStack { Circle().fill(loadColor(load.level)).frame(width: 9, height: 9); Text(muscleName(load.muscle)).foregroundStyle(HC.text); Spacer()
                                Text(String(format: "%.1f set • %@", load.setEquivalent, compactKg(load.totalVolumeKg))).foregroundStyle(HC.textSecondary) }.padding(.vertical, 3)
                        }.buttonStyle(.plain)
                    }
                    Text(LangStore.english ? analysis.balanceInsightEn : analysis.balanceInsightTr).foregroundStyle(HC.textSecondary)
                }
            }
            weeklyVolume(analysis)
        }.sheet(item: $selected) { MuscleDetailSheet(load: $0, rangeDays: days) }
    }

    private func figure(_ analysis: TrainingAnalysis) -> some View {
        let score = Dictionary(analysis.muscleLoads.map { ($0.muscle, $0) }, uniquingKeysWith: { a, _ in a })
        return VStack(spacing: 10) {
            ZStack(alignment: .top) {
                Image("MuscleAnatomy").resizable().aspectRatio(1, contentMode: .fit).saturation(0.12).opacity(0.62)
                GeometryReader { geo in
                    Canvas { ctx, size in
                        for region in muscleRegions {
                            let level = score[region.muscle]?.level ?? .none
                            let center = CGPoint(x: region.x * size.width, y: region.y * size.height)
                            let rx = region.rx * size.width, ry = region.ry * size.height
                            if level == .none {
                                ctx.fill(Path(ellipseIn: CGRect(x: center.x - 1.6, y: center.y - 1.6, width: 3.2, height: 3.2)), with: .color(HC.textSecondary.opacity(0.3)))
                                continue
                            }
                            let color = loadColor(level)
                            var c = ctx
                            c.translateBy(x: center.x, y: center.y); c.scaleBy(x: 1, y: ry / rx); c.translateBy(x: -center.x, y: -center.y)
                            let r = rx * 1.35
                            c.fill(Path(ellipseIn: CGRect(x: center.x - r, y: center.y - r, width: r * 2, height: r * 2)),
                                   with: .radialGradient(Gradient(stops: [.init(color: color.opacity(0.72), location: 0), .init(color: color.opacity(0.46), location: 0.45), .init(color: color.opacity(0.18), location: 0.78), .init(color: .clear, location: 1)]), center: center, startRadius: 0, endRadius: r))
                            if region.muscle == selected?.muscle {
                                ctx.stroke(Path(ellipseIn: CGRect(x: center.x - rx * 1.2, y: center.y - ry * 1.2, width: rx * 2.4, height: ry * 2.4)), with: .color(.white.opacity(0.85)), lineWidth: 1.6)
                            }
                        }
                    }
                    .contentShape(Rectangle())
                    .onTapGesture { p in
                        let hits = muscleRegions.compactMap { r -> (AnatomicalRegion, Double)? in
                            let dx = (p.x / geo.size.width - r.x) / r.rx, dy = (p.y / geo.size.height - r.y) / r.ry
                            let d = dx * dx + dy * dy
                            return d <= 1 ? (r, d) : nil
                        }
                        if let hit = hits.min(by: { $0.1 < $1.1 }) { selected = score[hit.0.muscle] ?? MuscleLoad(muscle: hit.0.muscle, score: 0, setEquivalent: 0, level: .none) }
                    }
                }
                HStack { Text(tr("ÖN", "FRONT")); Spacer(); Text(tr("ARKA", "BACK")) }.font(.system(size: 10, weight: .semibold)).foregroundStyle(HC.textSecondary).padding(.horizontal, 22).padding(.top, 4)
            }.aspectRatio(1, contentMode: .fit)
            HStack {
                ForEach([(LoadLevel.none, tr("Çalışılmadı", "None")), (.low, tr("Az", "Low")), (.balanced, tr("Dengeli", "Balanced")), (.high, tr("Yoğun", "High")), (.overload, tr("Aşırı", "Overload"))], id: \.1) { l, t in
                    HStack(spacing: 4) { Circle().fill(loadColor(l)).frame(width: 8, height: 8); Text(t).font(.system(size: 10)).foregroundStyle(HC.textSecondary) }
                    if t != tr("Aşırı", "Overload") { Spacer() }
                }
            }
        }
    }

    private func weeklyVolume(_ a: TrainingAnalysis) -> some View {
        let labels = LangStore.english ? ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"] : ["Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz"]
        return HfCard {
            VStack(alignment: .leading, spacing: 12) {
                Text(tr("Haftalık Antrenman Hacmi", "Weekly training volume")).font(.hfTitleL).foregroundStyle(HC.text)
                Chart(1...7, id: \.self) { d in
                    BarMark(x: .value("d", labels[d - 1]), y: .value("v", max(a.dailyVolume[d] ?? 0, 0.0001))).foregroundStyle((a.dailyVolume[d] ?? 0) > 0 ? HC.lime : HC.surfaceSoft).cornerRadius(6)
                }.chartYAxis(.hidden).frame(height: 150)
                Text(tr("Toplam: ", "Total: ") + compactKg(a.totalVolumeKg)).font(.hfLabel.weight(.bold)).foregroundStyle(HC.lime)
            }
        }
    }
}

struct MuscleDetailSheet: View {
    @Environment(\.dismiss) private var dismiss
    let load: MuscleLoad
    let rangeDays: Int
    var body: some View {
        let name = muscleName(load.muscle)
        let coach: String = {
            switch load.level {
            case .none: return tr("\(name) için bu dönemde kayıtlı çalışma yok.", "\(name) has no recorded work in this period.")
            case .low: return tr("\(name) hacmin bu dönem düşük kaldı. Toparlanman uygunsa birkaç kaliteli set ekleyebilirsin.", "\(name) volume remained low. Add a few quality sets if recovery allows.")
            case .balanced: return tr("\(name) hacmin dengeli. Mevcut ilerlemeyi koruyabilirsin.", "\(name) volume is balanced. Keep the current progression.")
            case .high: return tr("\(name) hacmin yüksek; artırmadan önce toparlanmaya öncelik ver.", "\(name) volume is high; prioritize recovery before adding more.")
            case .overload: return tr("\(name) aşırı yük altında. Set azaltmayı düşün; ağrı ve yorgunluğu izle.", "\(name) is overloaded. Consider reducing sets and watch pain or fatigue.")
            }
        }()
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Text(name).font(.hfHeadline.weight(.bold)).foregroundStyle(HC.text)
                HStack {
                    block(tr("SET", "SETS"), String(format: "%.1f", load.setEquivalent)); Spacer()
                    block(tr("HACİM", "VOLUME"), compactKg(load.totalVolumeKg)); Spacer()
                    block(tr("DURUM", "STATUS"), loadStatus(load.level))
                }
                Text(tr("Son çalıştırılma: ", "Last trained: ") + (load.lastTrainedAt.map { String($0.prefix(10)) } ?? "—")).foregroundStyle(HC.textSecondary)
                Text(tr("Son 30 günlük trend", "30-day trend")).font(.hfTitleM).foregroundStyle(HC.text)
                let trend = load.trendVolumes.isEmpty ? [0, 0] : load.trendVolumes
                Chart(Array(trend.enumerated()), id: \.offset) { i, v in LineMark(x: .value("i", i), y: .value("v", v)).foregroundStyle(HC.lime).interpolationMethod(.catmullRom) }
                    .chartXAxis(.hidden).chartYAxis(.hidden).frame(height: 90)
                Text(tr("Hacmin geldiği hareketler", "Volume sources")).font(.hfTitleM).foregroundStyle(HC.text)
                if load.exercises.isEmpty { Text("—").foregroundStyle(HC.textSecondary) }
                ForEach(load.exercises.prefix(6), id: \.exerciseName) { c in
                    HStack { Text(c.exerciseName).foregroundStyle(HC.text); Spacer(); Text(String(format: "%.1f set • %@", c.setEquivalent, compactKg(c.volumeKg))).foregroundStyle(HC.textSecondary) }.font(.hfBody)
                }
                HfCard { VStack(alignment: .leading, spacing: 6) { Text("AI COACH").font(.hfLabel.weight(.bold)).foregroundStyle(HC.lime); Text(coach).foregroundStyle(HC.text) }.frame(maxWidth: .infinity, alignment: .leading) }
            }.padding(20)
        }.background(HC.surface).presentationDetents([.large])
    }
    private func block(_ l: String, _ v: String) -> some View { VStack(alignment: .leading, spacing: 2) { Text(l).font(.hfLabel.weight(.bold)).foregroundStyle(HC.textSecondary); Text(v).font(.hfTitleM.weight(.bold)).foregroundStyle(HC.text) } }
}
