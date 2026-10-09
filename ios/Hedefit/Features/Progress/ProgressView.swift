import SwiftUI
import Charts
import MapKit

private let progressRanges = ["7G", "30G", "90G", "1Y", "all"]

struct ProgressTabView: View {
    @Environment(AppModel.self) private var app
    @State private var range = 1
    @State private var showGoal = false
    @State private var showMeasure = false
    @State private var showHistory = false
    @State private var editing: BodyMeasurement?
    @State private var showReview = false

    private var key: String { progressRanges[range] }
    private var d: Dashboard? { app.dashboard }

    private func cutoff() -> Date? {
        let days: Int? = ["7G": 7, "30G": 30, "90G": 90, "1Y": 365][key]
        return days.map { Dates.add(-($0 - 1), to: Dates.startOfDay()) }
    }
    private func sessionDate(_ s: WorkoutSession) -> Date { s.date.map(Dates.startOfDay) ?? .distantPast }
    private func routeDate(_ r: RouteActivity) -> Date { r.date.map(Dates.startOfDay) ?? .distantPast }

    var body: some View {
        let c = cutoff()
        let sessions = (d?.sessions ?? []).filter { c == nil || sessionDate($0) >= c! }
        let routes = (d?.routeActivities ?? []).filter { c == nil || routeDate($0) >= c! }
        let perf = (d?.exercisePerformance ?? []).filter { c == nil || (ISO.date($0.completedAt).map(Dates.startOfDay) ?? Dates.parse(String($0.completedAt.prefix(10))) ?? .distantPast) >= c! }
        let measures = (d?.measurements ?? []).filter { c == nil || (Dates.parse($0.date) ?? .distantPast) >= c! }
        ScreenScaffold(spacing: 15) {
            HfScreenHeader(title: tr("İlerleme", "Progress")) { HfCircleButton(system: "scalemass.fill", label: tr("Ölçüm ekle", "Add measurement")) { showMeasure = true } }
            HfSegmented(options: [tr("7G", "7D"), tr("30G", "30D"), tr("90G", "90D"), "1Y", tr("Tümü", "All")], selection: $range)
            hero(sessions)
            if let snap = app.gamification() { GamificationSection(snapshot: snap) }
            ActivityHeatmap(sessions: d?.sessions ?? [], routes: d?.routeActivities ?? [], range: key)
            WeeklyMinutesChart(sessions: d?.sessions ?? [], routes: d?.routeActivities ?? [])
            WeeklyCalorieBalance()
            WeightChartCard(measurements: measures, all: d, range: key) { showHistory = true }
            ExercisePerformanceHistory(performances: perf)
            WorkoutHistoryCard(sessions: sessions)
            RouteHistoryCard(routes: routes)
            BodyMeasurementsCard(measurements: measures) { showMeasure = true }
            HfCard(padding: 14, onTap: { showReview = true }) {
                HStack(spacing: 12) {
                    HfIconBadge(system: "sparkles", tint: HC.sleep, size: 48, radius: 24)
                    VStack(alignment: .leading, spacing: 2) { Text(tr("Haftalık Değerlendirme", "Weekly Review")).font(.hfTitleM.weight(.bold)).foregroundStyle(HC.text); Text(sessions.isEmpty ? tr("İlk antrenmanını tamamladıktan sonra kişisel değerlendirmen burada oluşacak.", "Your personal review appears after your first workout.") : tr("\(sessions.count) antrenmandan yeni haftanı değerlendir.", "Review your new week from \(sessions.count) workouts.")).font(.hfSmall).foregroundStyle(HC.textSecondary).multilineTextAlignment(.leading) }
                    Spacer(); Image(systemName: "arrow.right").foregroundStyle(HC.textSecondary)
                }
            }
        }
        .task { await app.nutrition.loadProgressHistory() }
        .sheet(isPresented: $showGoal) { WeeklyGoalSheet() }
        .sheet(isPresented: $showMeasure) { MeasurementSheet(entry: nil) }
        .sheet(item: $editing) { MeasurementSheet(entry: $0) }
        .sheet(isPresented: $showHistory) { WeightHistorySheet { editing = $0 } }
        .sheet(isPresented: $showReview) { WeeklyReviewSheet(sessions: sessions) }
    }

    private func hero(_ sessions: [WorkoutSession]) -> some View {
        let minutes = sessions.reduce(0) { $0 + $1.durationSeconds } / 60, calories = sessions.reduce(0) { $0 + $1.calories }
        let weekStart = Dates.weekStart()
        let weeklyDone = Set((d?.sessions ?? []).map(sessionDate).filter { $0 >= weekStart }.map(Dates.epochDay)).count
        let goal = app.prefs.weeklyWorkoutGoal
        return HfCard(padding: 18) {
            VStack(alignment: .leading, spacing: 16) {
                HStack {
                    VStack(alignment: .leading, spacing: 0) { Text("\(sessions.count)").font(.system(size: 44, weight: .black)).foregroundStyle(HC.lime); Text(tr("antrenman", "workouts")).font(.hfTitleM.weight(.bold)).foregroundStyle(HC.text) }
                    Spacer()
                    Button { showGoal = true } label: {
                        ZStack { HfRing(progress: Double(weeklyDone) / Double(max(goal, 1)), lineWidth: 9).frame(width: 86, height: 86)
                            VStack(spacing: 0) { Text("\(weeklyDone)/\(goal)").font(.system(size: 15, weight: .heavy)).foregroundStyle(HC.text); Text(tr("bu hafta", "this week")).font(.system(size: 10, weight: .bold)).foregroundStyle(HC.textSecondary) } }
                    }.buttonStyle(.plain)
                }
                HStack(spacing: 8) {
                    metric(tr("Süre", "Time"), "\(minutes / 60)\(tr("s", "h")) \(minutes % 60)\(tr("dk", "m"))", HC.water)
                    metric("kcal", "\(calories)", HC.warning)
                    metric(tr("Seri", "Streak"), "\(d?.streakDays ?? 0) \(tr("gün", "d"))", HC.coral)
                }
            }
        }
    }
    private func metric(_ l: String, _ v: String, _ c: Color) -> some View {
        VStack(alignment: .leading, spacing: 2) { Text(v).font(.system(size: 15, weight: .heavy)).foregroundStyle(c).lineLimit(1); Text(l).font(.hfLabel.weight(.bold)).foregroundStyle(HC.text) }
            .padding(10).frame(maxWidth: .infinity, alignment: .leading).background(c.opacity(0.12), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }
}

// MARK: - Isı haritası

struct ActivityHeatmap: View {
    let sessions: [WorkoutSession]
    let routes: [RouteActivity]
    let range: String
    var body: some View {
        let today = Dates.startOfDay()
        let all = sessions.compactMap { $0.date.map(Dates.startOfDay) } + routes.compactMap { $0.date.map(Dates.startOfDay) }
        let days: Int = ["7G": 7, "30G": 30, "90G": 90, "1Y": 365][range] ?? max((all.min().map { Dates.epochDay(today) - Dates.epochDay($0) + 1 } ?? 30), 7)
        let cutoff = Dates.add(-(days - 1), to: today)
        let start = Dates.weekStart(cutoff)
        let weeks = (Dates.epochDay(today) - Dates.epochDay(start)) / 7 + 1
        let counts = Dictionary(grouping: all.filter { $0 >= cutoff }, by: Dates.epochDay).mapValues(\.count)
        return VStack(alignment: .leading, spacing: 10) {
            HfSectionHeader(title: tr("Aktivite", "Activity"))
            HfCard(padding: 14) {
                ScrollView(.horizontal) {
                    HStack(spacing: 4) {
                        ForEach(0..<weeks, id: \.self) { w in
                            VStack(spacing: 4) {
                                ForEach(0..<7, id: \.self) { dd in
                                    let day = Dates.add(w * 7 + dd, to: start)
                                    let n = counts[Dates.epochDay(day)] ?? 0
                                    RoundedRectangle(cornerRadius: 4).fill(day > today || day < cutoff ? Color.clear : (n == 0 ? HC.surfaceSoft : HC.lime.opacity(min(0.35 + Double(n) * 0.25, 1)))).frame(width: 16, height: 16)
                                }
                            }
                        }
                    }
                }.scrollIndicators(.hidden).defaultScrollAnchor(.trailing)
            }
        }
    }
}

struct WeeklyMinutesChart: View {
    let sessions: [WorkoutSession]
    let routes: [RouteActivity]
    var body: some View {
        let weekStart = Dates.weekStart()
        let weeks = (0...7).reversed().map { Dates.add(-$0 * 7, to: weekStart) }
        let data = weeks.map { start -> (Date, Int) in
            let end = Dates.add(7, to: start)
            let s = sessions.filter { guard let d = $0.date.map(Dates.startOfDay) else { return false }; return d >= start && d < end }.reduce(0) { $0 + $1.durationSeconds } / 60
            let r = routes.filter { guard let d = $0.date.map(Dates.startOfDay) else { return false }; return d >= start && d < end }.reduce(0) { $0 + $1.durationSeconds } / 60
            return (start, s + r)
        }
        return VStack(alignment: .leading, spacing: 10) {
            HfSectionHeader(title: tr("Haftalık antrenman süresi", "Weekly training time"))
            HfCard(padding: 14) {
                Chart(data, id: \.0) { item in
                    BarMark(x: .value("w", item.0, unit: .weekOfYear), y: .value("min", item.1)).foregroundStyle(item.0 == weekStart ? HC.lime : HC.lime.opacity(0.35)).cornerRadius(5)
                        .annotation(position: .top) { if item.1 > 0 { Text("\(item.1)").font(.system(size: 10, weight: .bold)).foregroundStyle(item.0 == weekStart ? HC.lime : HC.text) } }
                }
                .chartXAxis { AxisMarks(values: .stride(by: .weekOfYear)) { _ in AxisValueLabel(format: .dateTime.day(), centered: true).font(.system(size: 10, weight: .bold)).foregroundStyle(HC.text) } }
                .chartYAxis(.hidden).frame(height: 150)
            }
        }
    }
}

/// Mifflin-St Jeor BMR × 1.2 + antrenman/rota + adım.
func estimatedBurn(_ d: Dashboard, on date: Date) -> Int {
    let p = d.profile
    let weight = d.measurements.last(where: { $0.weightKg != nil })?.weightKg ?? p.weightKg
    var base = d.nutritionGoal.calories
    if let weight, let h = p.heightCm, let age = p.age {
        let female = ["kad", "female"].contains { p.gender.lowercased().contains($0) }
        base = Int((10 * weight + 6.25 * h - 5 * Double(age) + (female ? -161 : 5)) * 1.2)
    }
    let w = d.sessions.filter { $0.date.map { Dates.isSameDay($0, date) } ?? false }.reduce(0) { $0 + $1.calories }
    let r = d.routeActivities.filter { $0.date.map { Dates.isSameDay($0, date) } ?? false }.reduce(0) { $0 + $1.calories }
    let steps = d.stepHistory.first { $0.localDate == Dates.day(date) }?.steps ?? 0
    return base + w + r + Int(Double(steps) * 0.04)
}

struct WeeklyCalorieBalance: View {
    @Environment(AppModel.self) private var app
    var body: some View {
        if let d = app.dashboard {
            let today = Date(), weekStart = Dates.weekStart()
            let logs = app.nutrition.history + d.nutritionLogs
            var seen = Set<String>()
            let unique = logs.filter { seen.insert($0.id).inserted }
            let intake = Dictionary(grouping: unique, by: { String($0.date.prefix(10)) }).mapValues { $0.reduce(0) { $0 + $1.calories } }
            let weeks = (0...7).reversed().map { Dates.add(-$0 * 7, to: weekStart) }.map { start -> (Date, Int, Int, Int) in
                let logged = (0..<7).map { Dates.add($0, to: start) }.filter { $0 <= today && (intake[Dates.day($0)] ?? 0) > 0 }
                return (start, logged.reduce(0) { $0 + (intake[Dates.day($1)] ?? 0) }, logged.reduce(0) { $0 + estimatedBurn(d, on: $1) }, logged.count)
            }
            let current = weeks.last!
            let balance = current.1 - current.2
            VStack(alignment: .leading, spacing: 10) {
                HfSectionHeader(title: tr("Haftalık kalori dengesi", "Weekly calorie balance"))
                HfCard(padding: 14) {
                    VStack(alignment: .leading, spacing: 12) {
                        Text(current.3 == 0 ? "—" : (balance <= 0 ? tr("Açık \(-balance) kcal", "Deficit \(-balance) kcal") : tr("Fazla \(balance) kcal", "Surplus \(balance) kcal"))).font(.system(size: 22, weight: .heavy)).foregroundStyle(balance <= 0 ? HC.lime : HC.warning)
                        Text(tr("Bu hafta • \(current.3) kayıtlı gün", "This week • \(current.3) logged days")).font(.hfBody.weight(.bold)).foregroundStyle(HC.text)
                        HStack(spacing: 14) { legend(HC.lime, tr("Alınan", "Eaten")); legend(HC.coral, tr("Yakılan (tahmini)", "Burned (est.)")) }
                        Chart {
                            ForEach(weeks, id: \.0) { w in
                                BarMark(x: .value("w", w.0, unit: .weekOfYear), y: .value("kcal", w.1)).foregroundStyle(HC.lime).position(by: .value("t", "in")).cornerRadius(3)
                                BarMark(x: .value("w", w.0, unit: .weekOfYear), y: .value("kcal", w.2)).foregroundStyle(HC.coral).position(by: .value("t", "out")).cornerRadius(3)
                            }
                        }
                        .chartXAxis { AxisMarks(values: .stride(by: .weekOfYear)) { _ in AxisValueLabel(format: .dateTime.day(), centered: true).font(.system(size: 10, weight: .bold)).foregroundStyle(HC.text) } }
                        .chartYAxis(.hidden).chartLegend(.hidden).frame(height: 150)
                    }
                }
            }
        }
    }
    private func legend(_ c: Color, _ t: String) -> some View { HStack(spacing: 6) { Circle().fill(c).frame(width: 10, height: 10); Text(t).font(.hfLabel).foregroundStyle(HC.text) } }
}

// MARK: - Kilo

struct WeightChartCard: View {
    @Environment(AppModel.self) private var app
    let measurements: [BodyMeasurement]
    let all: Dashboard?
    let range: String
    var onHistory: () -> Void
    var body: some View {
        let units = app.units
        let weights = measurements.compactMap(\.weightKg)
        let current = weights.last ?? all?.profile.weightKg
        let change = weights.count >= 2 ? (current ?? 0) - weights[0] : nil
        let points = WeightTrend.points(measurements)
        let trend = WeightTrend.smooth(points)
        return HfCard {
            VStack(alignment: .leading, spacing: 8) {
                HStack(alignment: .bottom) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(tr("VÜCUT AĞIRLIĞI", "BODY WEIGHT")).font(.hfLabel.weight(.bold)).foregroundStyle(HC.textSecondary)
                        HStack(alignment: .bottom, spacing: 6) { Text(current.map { String(format: "%.1f", Units.weightValue($0, units)) } ?? "—").font(.system(size: 28, weight: .heavy)).foregroundStyle(HC.text); Text(Units.weightUnit(units)).foregroundStyle(HC.muted).padding(.bottom, 5) }
                    }
                    Spacer()
                    if let change { HfPill(text: String(format: "%+.1f %@", Units.weightValue(change, units), Units.weightUnit(units)), color: change <= 0 ? HC.lime : HC.warning) }
                }
                if trend.count >= 2 {
                    Chart(Array(zip(points, trend)), id: \.0.date) { p, v in
                        LineMark(x: .value("d", p.date), y: .value("kg", Units.weightValue(v, units))).foregroundStyle(HC.lime).interpolationMethod(.catmullRom).lineStyle(StrokeStyle(lineWidth: 3, lineCap: .round))
                        AreaMark(x: .value("d", p.date), y: .value("kg", Units.weightValue(v, units))).foregroundStyle(LinearGradient(colors: [HC.lime.opacity(0.25), .clear], startPoint: .top, endPoint: .bottom)).interpolationMethod(.catmullRom)
                    }.chartYScale(domain: .automatic(includesZero: false)).chartYAxis { AxisMarks { _ in AxisGridLine().foregroundStyle(HC.divider); AxisValueLabel().foregroundStyle(HC.muted) } }.chartXAxis(.hidden).frame(height: range == "7G" ? 80 : 120)
                    Text(tr("Çizgi 7 günlük ortalamanı gösterir; günlük dalgalanmalar yanıltmasın.", "Line shows your 7-day average, so daily ups and downs don't mislead.")).font(.hfSmall).foregroundStyle(HC.muted)
                } else { Text(tr("Trend için en az iki kilo kaydı gerekir.", "At least two weight entries are needed for a trend.")).font(.hfSmall).foregroundStyle(HC.muted).padding(.vertical, 24) }
                WeightInsights(all: all, onHistory: onHistory)
            }
        }
    }
}

struct WeightInsights: View {
    @Environment(AppModel.self) private var app
    let all: Dashboard?
    var onHistory: () -> Void
    var body: some View {
        let profile = all?.profile
        let points = WeightTrend.points(all?.measurements ?? [])
        let current = points.last?.kg ?? profile?.weightKg
        let rate = WeightTrend.weeklyRateKg(points)
        let target = profile?.targetWeightKg
        let units = app.units
        return VStack(alignment: .leading, spacing: 10) {
            if rate != nil || (target != nil && current != nil) {
                HStack {
                    value(tr("Haftalık trend", "Weekly trend"), rate.map { String(format: "%+.1f %@/%@", Units.weightValue($0, units), Units.weightUnit(units), tr("hf", "wk")) } ?? "—")
                    if let target, let current { Spacer(); value(tr("Hedef", "Goal"), Units.formatWeight(target, units)); Spacer(); value(tr("Kalan", "To go"), Units.formatWeight(abs(target - current), units), accent: true) }
                }
            }
            if let target, let current {
                let start = points.first?.kg ?? current
                if let progress = WeightTrend.goalProgress(start: start, current: current, target: target) {
                    HfProgressBar(progress: progress, height: 8)
                    Text(tr("\(Units.formatWeight(start, units)) → hedef yolunun %\(Int(progress * 100))'i tamam", "\(Int(progress * 100))% of the way from \(Units.formatWeight(start, units)) to your goal")).font(.hfSmall).foregroundStyle(HC.textSecondary)
                }
                if let date = WeightTrend.projectedDate(current: current, target: target, weeklyRateKg: rate) {
                    let f = date.formatted(.dateTime.day().month(.wide).year().locale(AppLang.shared.locale))
                    Text(tr("Bu hızla hedefine yaklaşık \(f) civarı ulaşırsın.", "At this pace you reach your goal around \(f).")).font(.hfSmall).foregroundStyle(HC.textSecondary)
                }
            }
            if WeightTrend.bmiApplies(age: profile?.age), let bmi = WeightTrend.bmi(weightKg: current, heightCm: profile?.heightCm) {
                let band: String = { switch WeightTrend.bmiBand(bmi) { case .low: return tr("tipik aralığın altında", "below the typical range"); case .healthy: return tr("tipik aralıkta", "within the typical range"); case .high: return tr("tipik aralığın üzerinde", "above the typical range") } }()
                Text(tr("VKİ \(String(format: "%.1f", bmi)) · \(band). Kas kütlesini hesaba katmaz; yalnızca kaba bir gösterge olarak düşün.", "BMI \(String(format: "%.1f", bmi)) · \(band). It doesn't account for muscle mass, so treat it as a rough guide.")).font(.hfSmall).foregroundStyle(HC.muted)
            }
            if !points.isEmpty { Button(tr("Kilo geçmişi (\(points.count))", "Weight history (\(points.count))"), action: onHistory).font(.hfBody.weight(.semibold)).foregroundStyle(HC.lime) }
        }
    }
    private func value(_ label: String, _ text: String, accent: Bool = false) -> some View {
        VStack(alignment: .leading, spacing: 2) { Text(label).font(.hfSmall).foregroundStyle(HC.muted); Text(text).font(.system(size: 15, weight: .bold)).foregroundStyle(accent ? HC.lime : HC.text) }
    }
}

// MARK: - Egzersiz & antrenman & rota geçmişi

struct ExercisePerformanceHistory: View {
    @Environment(AppModel.self) private var app
    let performances: [ExercisePerformance]
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HfSectionHeader(title: tr("Hareket performansı", "Exercise performance"))
            if performances.isEmpty { HfCard { Text(tr("Detaylı antrenman bitirdikçe setlerin burada görünür.", "Your sets show up here as you finish detailed workouts.")).font(.hfBody).foregroundStyle(HC.textSecondary) } }
            else {
                let best = Dictionary(grouping: performances, by: { $0.exerciseId ?? $0.exerciseName }).values.compactMap { list -> (String, Double, Double)? in
                    guard let top = list.flatMap(\.sets).max(by: { estimatedOneRepMax(weightKg: $0.weightKg, reps: $0.reps) < estimatedOneRepMax(weightKg: $1.weightKg, reps: $1.reps) }) else { return nil }
                    let e = estimatedOneRepMax(weightKg: top.weightKg, reps: top.reps)
                    return e > 0 ? (list[0].exerciseName, e, top.weightKg ?? 0) : nil
                }.sorted { $0.1 > $1.1 }.prefix(5)
                HfCard(padding: 14) {
                    VStack(spacing: 8) {
                        ForEach(Array(best), id: \.0) { name, e1rm, _ in
                            HStack { Text(name).font(.hfBody.weight(.semibold)).foregroundStyle(HC.text).lineLimit(1); Spacer(); Text(tr("1TM ≈ ", "e1RM ≈ ") + Units.formatWeight(e1rm, app.units, decimals: 0)).font(.system(size: 14, weight: .heavy)).foregroundStyle(HC.lime) }
                        }
                        Text(tr("1TM, Epley formülüyle tahmin edilir.", "e1RM is estimated with the Epley formula.")).font(.hfSmall).foregroundStyle(HC.muted).frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
            }
        }
    }
}

struct WorkoutHistoryCard: View {
    @Environment(AppModel.self) private var app
    let sessions: [WorkoutSession]
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HfSectionHeader(title: tr("Antrenman geçmişi", "Workout history"))
            if sessions.isEmpty { HfCard { Text(tr("Bu aralıkta kayıtlı antrenman yok.", "No workouts in this range.")).foregroundStyle(HC.textSecondary).font(.hfBody) } }
            ForEach(sessions.prefix(12)) { s in
                let activity = activityType(for: s.manualActivityKey)
                HfCard(padding: 14) {
                    HStack(spacing: 12) {
                        Text(activity?.emoji ?? "🏋️").font(.system(size: 24)).frame(width: 44, height: 44).background(HC.surfaceHigh, in: RoundedRectangle(cornerRadius: 14))
                        VStack(alignment: .leading, spacing: 2) {
                            Text(activity?.title ?? (s.exerciseNames.first ?? tr("Antrenman", "Workout"))).font(.hfTitleM.weight(.bold)).foregroundStyle(HC.text).lineLimit(1)
                            Text((s.date?.formatted(.dateTime.day().month(.abbreviated).hour().minute().locale(AppLang.shared.locale)) ?? "") + " • \(s.durationSeconds / 60) \(tr("dk", "min"))").font(.hfSmall).foregroundStyle(HC.textSecondary)
                        }
                        Spacer(); Text("\(s.calories) kcal").font(.system(size: 14, weight: .bold)).foregroundStyle(HC.warning)
                    }
                }
            }
        }
    }
}

struct RouteHistoryCard: View {
    @Environment(AppModel.self) private var app
    let routes: [RouteActivity]
    var body: some View {
        if !routes.isEmpty {
            VStack(alignment: .leading, spacing: 10) {
                HfSectionHeader(title: tr("Rotalar", "Routes"))
                ForEach(routes.prefix(10)) { r in
                    HfCard(padding: 14, onTap: { app.push(.routeDetail(r.id)) }) {
                        HStack(spacing: 12) {
                            RoutePolyline(points: r.routePoints).frame(width: 64, height: 64).background(HC.surfaceHigh, in: RoundedRectangle(cornerRadius: 14))
                            VStack(alignment: .leading, spacing: 2) {
                                Text(r.title).font(.hfTitleM.weight(.bold)).foregroundStyle(HC.text).lineLimit(1)
                                Text("\(Units.formatDistance(r.distanceMeters, app.units)) • \(formatDuration(r.movingDurationSeconds)) • \(Units.formatPace(r.averagePaceSecondsPerKm, app.units))").font(.hfSmall).foregroundStyle(HC.textSecondary)
                                Text(r.date?.formatted(.dateTime.day().month(.abbreviated).year().locale(AppLang.shared.locale)) ?? "").font(.hfSmall).foregroundStyle(HC.muted)
                            }
                            Spacer(); Image(systemName: "chevron.right").foregroundStyle(HC.muted)
                        }
                    }
                }
            }
        }
    }
}

/// Rota noktalarını küçük bir çizgi olarak çizer.
struct RoutePolyline: View {
    let points: [RoutePoint]
    var lineWidth: CGFloat = 3
    var body: some View {
        GeometryReader { geo in
            Path { path in
                guard points.count >= 2 else { return }
                let lats = points.map(\.latitude), lons = points.map(\.longitude)
                let minLat = lats.min()!, maxLat = lats.max()!, minLon = lons.min()!, maxLon = lons.max()!
                let spanLat = max(maxLat - minLat, 1e-5), spanLon = max((maxLon - minLon) * cos(((minLat + maxLat) / 2) * .pi / 180), 1e-5)
                let scale = min((geo.size.width - 12) / spanLon, (geo.size.height - 12) / spanLat)
                let offsetX = (geo.size.width - spanLon * scale) / 2, offsetY = (geo.size.height - spanLat * scale) / 2
                func pt(_ p: RoutePoint) -> CGPoint { CGPoint(x: offsetX + (p.longitude - minLon) * cos(((minLat + maxLat) / 2) * .pi / 180) * scale, y: geo.size.height - offsetY - (p.latitude - minLat) * scale) }
                path.move(to: pt(points[0])); points.dropFirst().forEach { path.addLine(to: pt($0)) }
            }.stroke(HC.lime, style: StrokeStyle(lineWidth: lineWidth, lineCap: .round, lineJoin: .round))
        }
    }
}

// MARK: - Ölçüler

struct BodyMeasurementsCard: View {
    @Environment(AppModel.self) private var app
    let measurements: [BodyMeasurement]
    var onTap: () -> Void
    var body: some View {
        let first = measurements.first, latest = measurements.last
        HfCard(padding: 14, onTap: onTap) {
            VStack(alignment: .leading, spacing: 10) {
                HStack { Text(tr("Vücut ölçüleri", "Body measurements")).font(.hfTitleM.weight(.heavy)).foregroundStyle(HC.text); Spacer(); Image(systemName: "plus").foregroundStyle(HC.lime) }
                if latest == nil { Text(tr("Bel, kalça, göğüs, kol ve bacak ölçülerini kaydet.", "Log your waist, hips, chest, arm and thigh.")).font(.hfSmall).foregroundStyle(HC.textSecondary) }
                else {
                    LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible()), GridItem(.flexible())], spacing: 8) {
                        cell(tr("Bel", "Waist"), first?.waistCm, latest?.waistCm); cell(tr("Kalça", "Hips"), first?.hipsCm, latest?.hipsCm); cell(tr("Göğüs", "Chest"), first?.chestCm, latest?.chestCm)
                        cell(tr("Kol", "Arm"), first?.armCm, latest?.armCm); cell(tr("Bacak", "Thigh"), first?.thighCm, latest?.thighCm)
                    }
                }
            }
        }
    }
    private func cell(_ l: String, _ a: Double?, _ b: Double?) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(l).font(.hfSmall).foregroundStyle(HC.textSecondary)
            Text(b.map { Units.formatLength($0, app.units) } ?? "—").font(.system(size: 14, weight: .bold)).foregroundStyle(HC.text).lineLimit(1).minimumScaleFactor(0.7)
            if let a, let b, a != b { Text(Units.formatLength(b - a, app.units, signed: true)).font(.system(size: 11)).foregroundStyle(b < a ? HC.lime : HC.warning) }
        }.padding(10).frame(maxWidth: .infinity, alignment: .leading).background(HC.surfaceHigh, in: RoundedRectangle(cornerRadius: 12))
    }
}

struct MeasurementSheet: View {
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss
    let entry: BodyMeasurement?
    @State private var date = Date()
    @State private var weight = ""
    @State private var waist = ""
    @State private var hips = ""
    @State private var chest = ""
    @State private var arm = ""
    @State private var thigh = ""

    var body: some View {
        let u = app.units
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    DatePicker(tr("Tarih", "Date"), selection: $date, in: ...Date(), displayedComponents: .date).foregroundStyle(HC.text).disabled(entry != nil)
                    HfField(title: tr("Kilo (\(Units.weightUnit(u)))", "Weight (\(Units.weightUnit(u)))"), text: $weight, keyboard: .decimalPad)
                    HfField(title: tr("Bel (\(Units.heightUnit(u)))", "Waist (\(Units.heightUnit(u)))"), text: $waist, keyboard: .decimalPad)
                    HfField(title: tr("Kalça (\(Units.heightUnit(u)))", "Hips (\(Units.heightUnit(u)))"), text: $hips, keyboard: .decimalPad)
                    HfField(title: tr("Göğüs (\(Units.heightUnit(u)))", "Chest (\(Units.heightUnit(u)))"), text: $chest, keyboard: .decimalPad)
                    HfField(title: tr("Kol (\(Units.heightUnit(u)))", "Arm (\(Units.heightUnit(u)))"), text: $arm, keyboard: .decimalPad)
                    HfField(title: tr("Bacak (\(Units.heightUnit(u)))", "Thigh (\(Units.heightUnit(u)))"), text: $thigh, keyboard: .decimalPad)
                    HfButton(title: tr("Kaydet", "Save"), loading: app.measurementSaving, enabled: [weight, waist, hips, chest, arm, thigh].contains { !$0.isEmpty }) {
                        func num(_ s: String, _ conv: (Double) -> Double) -> Double? { Double(s.replacingOccurrences(of: ",", with: ".")).map(conv) }
                        let m = BodyMeasurement(date: Dates.day(date), weightKg: num(weight) { Units.weightToKg($0, u) }, waistCm: num(waist) { Units.heightToCm($0, u) }, hipsCm: num(hips) { Units.heightToCm($0, u) },
                                                chestCm: num(chest) { Units.heightToCm($0, u) }, armCm: num(arm) { Units.heightToCm($0, u) }, thighCm: num(thigh) { Units.heightToCm($0, u) })
                        Task { await app.saveMeasurement(m); dismiss() }
                    }
                }.padding(20)
            }.background(HC.bg).navigationTitle(tr("Vücut ölçüleri", "Body measurements")).navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button(tr("Vazgeç", "Cancel")) { dismiss() } } }
        }
        .onAppear {
            let src = entry ?? app.dashboard?.measurements.last
            if let entry, let d = Dates.parse(entry.date) { date = d }
            func f(_ v: Double?, _ conv: (Double) -> Double) -> String { v.map { String(format: "%.1f", conv($0)) } ?? "" }
            weight = f(src?.weightKg ?? (entry == nil ? app.dashboard?.profile.weightKg : nil)) { Units.weightValue($0, u) }
            waist = f(src?.waistCm) { Units.heightValue($0, u) }; hips = f(src?.hipsCm) { Units.heightValue($0, u) }; chest = f(src?.chestCm) { Units.heightValue($0, u) }
            arm = f(src?.armCm) { Units.heightValue($0, u) }; thigh = f(src?.thighCm) { Units.heightValue($0, u) }
        }.presentationDetents([.large])
    }
}

struct WeightHistorySheet: View {
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss
    var onEdit: (BodyMeasurement) -> Void
    @State private var deleting: BodyMeasurement?
    var body: some View {
        NavigationStack {
            List {
                if (app.dashboard?.measurements ?? []).isEmpty { Text(tr("Henüz kayıt yok.", "No records yet.")).foregroundStyle(HC.textSecondary).listRowBackground(HC.surface) }
                ForEach((app.dashboard?.measurements ?? []).sorted { $0.date > $1.date }) { m in
                    Button { dismiss(); DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { onEdit(m) } } label: {
                        HStack { VStack(alignment: .leading) { Text(m.weightKg.map { Units.formatWeight($0, app.units) } ?? tr("Kilo yok", "No weight")).font(.hfBody.weight(.bold)).foregroundStyle(HC.text); Text(Dates.parse(m.date)?.formatted(.dateTime.day().month(.wide).year().locale(AppLang.shared.locale)) ?? m.date).font(.hfSmall).foregroundStyle(HC.textSecondary) }; Spacer()
                            Button { deleting = m } label: { Image(systemName: "trash").foregroundStyle(HC.textSecondary) }.buttonStyle(.plain) }
                    }.listRowBackground(HC.surface)
                }
            }.scrollContentBackground(.hidden).background(HC.bg).navigationTitle(tr("Ölçüm geçmişi", "Measurement history")).navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button(tr("Kapat", "Close")) { dismiss() } } }
            .alert(tr("Bu kayıt silinsin mi?", "Delete this record?"), isPresented: Binding(get: { deleting != nil }, set: { if !$0 { deleting = nil } })) {
                Button(tr("Vazgeç", "Cancel"), role: .cancel) { deleting = nil }
                Button(tr("Sil", "Delete"), role: .destructive) { if let m = deleting { Task { await app.deleteMeasurement(m) } }; deleting = nil }
            }
        }.presentationDetents([.large])
    }
}

struct WeeklyGoalSheet: View {
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss
    @State private var value = 3.0
    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 16) {
                Text(tr("Haftada \(Int(value)) tamamlanmış antrenman", "\(Int(value)) completed workouts per week")).font(.hfHeadline.weight(.bold)).foregroundStyle(HC.lime)
                Text(tr("Bir hafta içinde gerçekçi olarak tamamlamak istediğin ayrı antrenman sayısını seç.", "Choose how many separate workouts you realistically want to complete in one week.")).font(.hfBody).foregroundStyle(HC.textSecondary)
                Slider(value: $value, in: 1...7, step: 1).tint(HC.lime)
                HfButton(title: tr("Kaydet", "Save")) { app.prefs.weeklyWorkoutGoal = Int(value); Task { await app.repo.syncGamificationPreferences(stepGoal: app.prefs.stepGoal, waterGoalMl: app.prefs.waterGoalMl, weeklyActivityGoal: Int(value), timezone: TimeZone.current.identifier) }; dismiss() }
                Spacer()
            }.padding(20).background(HC.bg).navigationTitle(tr("Haftalık hedef", "Weekly target")).navigationBarTitleDisplayMode(.inline)
        }.onAppear { value = Double(app.prefs.weeklyWorkoutGoal) }.presentationDetents([.medium])
    }
}

struct WeeklyReviewSheet: View {
    @Environment(\.dismiss) private var dismiss
    let sessions: [WorkoutSession]
    var body: some View {
        let minutes = sessions.reduce(0) { $0 + $1.durationSeconds } / 60, completed = sessions.reduce(0) { $0 + $1.completedExercises }, planned = sessions.reduce(0) { $0 + $1.totalExercises }
        NavigationStack {
            VStack(alignment: .leading, spacing: 12) {
                if sessions.isEmpty { Text(tr("Kişisel haftalık değerlendirmeni görmek için ilk antrenmanını tamamla.", "Complete your first workout to unlock a personal weekly review.")).foregroundStyle(HC.textSecondary) }
                else {
                    Text(tr("\(sessions.count) antrenman • \(minutes) dakika", "\(sessions.count) workouts • \(minutes) minutes")).font(.hfHeadline.weight(.bold)).foregroundStyle(HC.lime)
                    Text(tr("Planlanan \(planned) hareketin \(completed) tanesi tamamlandı.", "\(completed) of \(planned) planned movements were completed.")).font(.hfBody).foregroundStyle(HC.textSecondary)
                    Text(tr("Gelecek haftayı ilerleyici ama sürdürülebilir tutmak için yorgunluk ve ağrı geri bildirimlerini kullan.", "Use your fatigue and pain feedback to keep next week progressive but sustainable.")).font(.hfBody).foregroundStyle(HC.textSecondary)
                }
                Spacer()
            }.padding(20).background(HC.bg).navigationTitle(tr("Haftalık Değerlendirme", "Weekly Review")).navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button(tr("Kapat", "Close")) { dismiss() } } }
        }.presentationDetents([.medium])
    }
}

// MARK: - Seviye ve seri bölümü

struct GamificationSection: View {
    @Environment(AppModel.self) private var app
    let snapshot: GamificationSnapshot
    @State private var showAll = false
    var body: some View {
        let level = snapshot.level, hub = app.challenge.hub, today = Date()
        let challenges = hub?.challenges ?? []
        let current = challenges.filter { $0.status == "active" }.map { $0.state(today: today).streak }.max() ?? 0
        let longest = challenges.map { $0.state(today: today).longestStreak }.max() ?? 0
        let badges = snapshot.achievements.filter { $0.unlockedAt != nil }.sorted { ($0.unlockedAt ?? .distantPast) > ($1.unlockedAt ?? .distantPast) }
        let history = challenges.filter { $0.status != "active" }
        VStack(alignment: .leading, spacing: 12) {
            HfSectionHeader(title: tr("Seviye ve seri", "Level & streak"), trailing: tr("Ödüller", "Rewards")) { app.push(.rewards) }
            HfCard(padding: 18, onTap: { app.push(.rewards) }) {
                VStack(alignment: .leading, spacing: 10) {
                    HStack(spacing: 12) {
                        HfIconBadge(system: "trophy.fill", tint: HC.lime, size: 44, radius: 14)
                        VStack(alignment: .leading) { Text(tr("Seviye \(level.level)", "Level \(level.level)")).font(.hfHeadline.weight(.heavy)).foregroundStyle(HC.text); Text(tr("Toplam \(snapshot.totalXp) XP", "\(snapshot.totalXp) XP total")).font(.hfSmall).foregroundStyle(HC.textSecondary) }
                        Spacer(); Text("\(level.currentXp) / \(level.nextLevelXp) XP").font(.system(size: 14, weight: .heavy)).foregroundStyle(HC.lime)
                    }
                    HfProgressBar(progress: level.progress, height: 8)
                    Text(tr("Seviye \(level.level + 1) için \(level.nextLevelXp - level.currentXp) XP kaldı", "\(level.nextLevelXp - level.currentXp) XP to Level \(level.level + 1)")).font(.hfSmall).foregroundStyle(HC.muted)
                }
            }
            HStack(spacing: 10) {
                HfStatTile(label: tr("Seri", "Streak"), value: "🔥 \(current)", sub: tr("En uzun \(longest)", "Best \(longest)"))
                HfStatTile(label: "Challenge", value: "\(challenges.filter { $0.status == "completed" }.count)", sub: tr("tamamlandı", "completed"))
                HfStatTile(label: tr("Antrenman", "Workouts"), value: "\(app.dashboard?.sessions.filter { $0.manualActivityKey == nil }.count ?? 0)", sub: tr("toplam", "total"))
            }
            if !badges.isEmpty {
                HfCard(padding: 14) { VStack(alignment: .leading, spacing: 8) { Text(tr("Kazanılan rozetler · \(badges.count)", "Achievements earned · \(badges.count)")).font(.hfTitleM.weight(.bold)).foregroundStyle(HC.text); HfChipRow { ForEach(badges.prefix(8)) { HfTag(text: "🏅 \($0.title)") } } } }
            }
            if !history.isEmpty {
                HfSectionHeader(title: tr("Challenge geçmişi", "Challenge history"), trailing: history.count > 3 ? (showAll ? tr("Daha az", "Less") : tr("Tümü", "All")) : nil) { showAll.toggle() }
                ForEach(showAll ? history : Array(history.prefix(3))) { ch in ActiveChallengeRow(challenge: ch, today: today) { app.push(.challengeDetail(ch.id)) } }
            }
        }
    }
}
