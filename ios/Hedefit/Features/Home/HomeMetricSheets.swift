import SwiftUI
import Charts

enum HomeMetric: String, Identifiable { case steps, calories, weight, water, sleep; var id: String { rawValue } }

struct HomeMetricSheet: View {
    let metric: HomeMetric
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                Group {
                    switch metric {
                    case .steps: StepsDetail()
                    case .calories: CaloriesDetail()
                    case .weight: QuickWeightForm(onDone: { dismiss() })
                    case .water: WaterDetail(onDone: { dismiss() })
                    case .sleep: SleepForm(onDone: { dismiss() })
                    }
                }.padding(20)
            }
            .background(HC.bg).navigationBarTitleDisplayMode(.inline)
            .navigationTitle(title)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button(tr("Kapat", "Close")) { dismiss() } } }
        }.presentationDetents([.medium, .large])
    }

    private var title: String {
        switch metric {
        case .steps: return tr("Adımlar", "Steps"); case .calories: return tr("Kalori özeti", "Calories")
        case .weight: return tr("Kilo ekle", "Log weight"); case .water: return tr("Su", "Water"); case .sleep: return tr("Uyku", "Sleep")
        }
    }
}

// MARK: Adım

private struct StepsDetail: View {
    @Environment(AppModel.self) private var app
    @State private var goal = 8000
    var body: some View {
        let d = app.dashboard
        let steps = d?.steps ?? 0
        VStack(alignment: .leading, spacing: 18) {
            HStack(spacing: 18) {
                ZStack { HfRing(progress: Double(steps) / Double(max(goal, 1)), color: HC.water, lineWidth: 12).frame(width: 110, height: 110); Text("\(steps)").font(.system(size: 22, weight: .heavy)).foregroundStyle(HC.text) }
                VStack(alignment: .leading, spacing: 4) {
                    Text(tr("Bugün", "Today")).font(.hfSmall).foregroundStyle(HC.muted)
                    Text(tr("\(steps) / \(app.prefs.stepGoal) adım", "\(steps) / \(app.prefs.stepGoal) steps")).font(.hfTitleM.weight(.bold)).foregroundStyle(HC.text)
                    Text("≈ \(Int(Double(steps) * 0.0008 * 10) / 10) km").font(.hfSmall).foregroundStyle(HC.textSecondary)
                    Text(tr("Kaynak: Apple Sağlık / iPhone", "Source: Apple Health / iPhone")).font(.hfSmall).foregroundStyle(HC.muted)
                }
            }
            let history = Array((d?.stepHistory ?? []).suffix(14))
            if !history.isEmpty {
                Text(tr("Son 14 gün", "Last 14 days")).font(.hfTitleM.weight(.bold)).foregroundStyle(HC.text)
                Chart(history, id: \.localDate) { day in
                    BarMark(x: .value("d", String(day.localDate.suffix(2))), y: .value("steps", day.steps)).foregroundStyle(day.steps >= app.prefs.stepGoal ? HC.lime : HC.water)
                    RuleMark(y: .value("goal", app.prefs.stepGoal)).lineStyle(StrokeStyle(lineWidth: 1, dash: [4])).foregroundStyle(HC.muted)
                }.frame(height: 150)
            }
            HfCard {
                VStack(alignment: .leading, spacing: 10) {
                    Text(tr("Günlük adım hedefi", "Daily step goal")).font(.hfTitleM.weight(.bold)).foregroundStyle(HC.text)
                    HStack { Text("\(goal)").font(.system(size: 22, weight: .heavy)).foregroundStyle(HC.lime); Spacer(); HfStepper(value: $goal, range: 1000...50000, step: 500) }
                    Text(tr("Önerilen: günde yaklaşık \(GoalScience.stepTarget) adım anlamlı sağlık faydası sağlar.", "Suggested: about \(GoalScience.stepTarget) steps a day gives meaningful health benefits.")).font(.hfSmall).foregroundStyle(HC.muted)
                    HfButton(title: tr("Hedefi kaydet", "Save goal"), enabled: goal != app.prefs.stepGoal) { app.prefs.stepGoal = goal; app.notify(tr("Adım hedefi güncellendi.", "Step goal updated.")); WidgetBridge.update(app) }
                }
            }
            HfButton(title: tr("Sağlık'tan eşitle", "Sync from Health"), icon: "arrow.triangle.2.circlepath", secondary: true) { Task { await app.syncHealth() } }
        }.onAppear { goal = app.prefs.stepGoal }
    }
}

// MARK: Kalori

private struct CaloriesDetail: View {
    @Environment(AppModel.self) private var app
    var body: some View {
        let d = app.dashboard
        let eaten = d?.nutritionLogs.reduce(0) { $0 + $1.calories } ?? 0
        let manual = (d?.sessions ?? []).filter { $0.manualActivityKey != nil && ($0.date.map { Dates.isSameDay($0, Date()) } ?? false) }.reduce(0) { $0 + $1.calories }
        let burned = (d?.activeCalories ?? 0) + manual
        let goal = d?.nutritionGoal.calories ?? 2250
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 10) {
                HfStatTile(label: tr("Alınan", "Eaten"), value: "\(eaten)", sub: "kcal")
                HfStatTile(label: tr("Yakılan", "Burned"), value: "\(burned)", sub: "kcal")
                HfStatTile(label: tr("Kalan", "Left"), value: "\(goal - eaten + burned)", sub: "kcal", valueColor: goal - eaten + burned >= 0 ? HC.lime : HC.coral)
            }
            HfCard {
                VStack(alignment: .leading, spacing: 8) {
                    row(tr("Günlük hedef", "Daily goal"), "\(goal) kcal")
                    row(tr("Aktif enerji (adım / Sağlık)", "Active energy (steps / Health)"), "\(d?.activeCalories ?? 0) kcal")
                    row(tr("Kayıtlı aktiviteler", "Logged activities"), "\(manual) kcal")
                    row(tr("Protein", "Protein"), "\(Int(d?.nutritionLogs.reduce(0) { $0 + $1.protein } ?? 0)) / \(d?.nutritionGoal.protein ?? 0) g")
                    row(tr("Karbonhidrat", "Carbs"), "\(Int(d?.nutritionLogs.reduce(0) { $0 + $1.carbs } ?? 0)) / \(d?.nutritionGoal.carbs ?? 0) g")
                    row(tr("Yağ", "Fat"), "\(Int(d?.nutritionLogs.reduce(0) { $0 + $1.fat } ?? 0)) / \(d?.nutritionGoal.fat ?? 0) g")
                }
            }
            Text(tr("Yakım değerleri tahmindir.", "Burn values are estimates.")).font(.hfSmall).foregroundStyle(HC.muted)
        }
    }
    private func row(_ a: String, _ b: String) -> some View { HStack { Text(a).foregroundStyle(HC.textSecondary); Spacer(); Text(b).foregroundStyle(HC.text).fontWeight(.semibold) }.font(.hfBody) }
}

// MARK: Kilo

struct QuickWeightForm: View {
    @Environment(AppModel.self) private var app
    var onDone: () -> Void
    @State private var text = ""
    var body: some View {
        let unit = Units.weightUnit(app.units)
        VStack(alignment: .leading, spacing: 14) {
            HfField(title: tr("Kilo (\(unit))", "Weight (\(unit))"), text: $text, keyboard: .decimalPad)
            HfButton(title: tr("Kaydet", "Save"), loading: app.measurementSaving, enabled: value != nil) {
                guard let v = value else { return }
                Task { await app.saveQuickWeight(Units.weightToKg(v, app.units)); onDone() }
            }
        }.onAppear {
            let current = app.dashboard?.measurements.last(where: { $0.weightKg != nil })?.weightKg ?? app.dashboard?.profile.weightKg
            if let current { text = String(format: "%.1f", Units.weightValue(current, app.units)) }
        }
    }
    private var value: Double? {
        guard let v = Double(text.replacingOccurrences(of: ",", with: ".")) else { return nil }
        let kg = Units.weightToKg(v, app.units)
        return (20...400).contains(kg) ? v : nil
    }
}

// MARK: Su

private struct WaterDetail: View {
    @Environment(AppModel.self) private var app
    var onDone: () -> Void
    @State private var custom = ""
    @State private var goal = 2500
    var body: some View {
        let d = app.dashboard
        VStack(alignment: .leading, spacing: 16) {
            HStack(spacing: 18) {
                ZStack { HfRing(progress: Double(d?.waterMl ?? 0) / Double(max(goal, 1)), color: HC.water, lineWidth: 12).frame(width: 110, height: 110); Text(Units.formatWater(d?.waterMl ?? 0, app.units)).font(.system(size: 18, weight: .heavy)).foregroundStyle(HC.text) }
                VStack(alignment: .leading, spacing: 4) {
                    Text(tr("Hedef", "Goal")).font(.hfSmall).foregroundStyle(HC.muted)
                    Text(Units.formatWater(app.prefs.waterGoalMl, app.units)).font(.hfTitleM.weight(.bold)).foregroundStyle(HC.text)
                }
            }
            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                ForEach([150, 250, 330, 500, 750, 1000], id: \.self) { ml in
                    Button { Task { await app.addWater(ml); onDone() } } label: {
                        VStack(spacing: 2) { Image(systemName: "drop.fill").foregroundStyle(HC.water); Text(app.units == "imperial" ? String(format: "%.0f oz", Double(ml) / 29.57) : "\(ml) ml").font(.system(size: 15, weight: .bold)).foregroundStyle(HC.text) }
                            .frame(maxWidth: .infinity, minHeight: 64).background(HC.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                    }.buttonStyle(PressableStyle())
                }
            }
            HStack {
                HfField(title: tr("Özel miktar (ml)", "Custom amount (ml)"), text: $custom, keyboard: .numberPad)
                Button { if let v = Int(custom), v > 0 { Task { await app.addWater(v); onDone() } } } label: { Image(systemName: "plus").font(.system(size: 18, weight: .bold)).foregroundStyle(HC.onLime).frame(width: 50, height: 50).background(HC.lime, in: RoundedRectangle(cornerRadius: 14)) }.padding(.top, 20)
            }
            HStack {
                Button { Task { await app.addWater(-250) } } label: { Label(tr("250 ml geri al", "Undo 250 ml"), systemImage: "arrow.uturn.backward") }.foregroundStyle(HC.textSecondary).font(.hfBody)
                Spacer()
            }
            HfCard {
                VStack(alignment: .leading, spacing: 8) {
                    Text(tr("Günlük su hedefi", "Daily water goal")).font(.hfTitleM.weight(.bold)).foregroundStyle(HC.text)
                    HStack { Text(Units.formatWater(goal, app.units)).font(.system(size: 20, weight: .heavy)).foregroundStyle(HC.lime); Spacer(); HfStepper(value: $goal, range: 500...10000, step: 250) }
                    let rec = GoalScience.recommendedWaterMl(weightKg: d?.profile.weightKg, gender: d?.profile.gender)
                    Button(tr("Önerilen: \(Units.formatWater(rec, app.units))", "Suggested: \(Units.formatWater(rec, app.units))")) { goal = rec }.font(.hfSmall.weight(.bold)).foregroundStyle(HC.lime)
                    HfButton(title: tr("Hedefi kaydet", "Save goal"), enabled: goal != app.prefs.waterGoalMl) { app.prefs.waterGoalMl = goal; WidgetBridge.update(app); app.notify(tr("Su hedefi güncellendi.", "Water goal updated.")) }
                }
            }
        }.onAppear { goal = app.prefs.waterGoalMl }
    }
}

// MARK: Uyku

private struct SleepForm: View {
    @Environment(AppModel.self) private var app
    var onDone: () -> Void
    @State private var hours = 7
    @State private var minutes = 30
    @State private var quality = "iyi"
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text(tr("Dün gece ne kadar uyudun?", "How long did you sleep last night?")).font(.hfTitleM.weight(.bold)).foregroundStyle(HC.text)
            HStack(spacing: 20) {
                VStack { Text(tr("Saat", "Hours")).font(.hfSmall).foregroundStyle(HC.muted); HfStepper(value: $hours, range: 0...16) }
                VStack { Text(tr("Dakika", "Minutes")).font(.hfSmall).foregroundStyle(HC.muted); HfStepper(value: $minutes, range: 0...55, step: 5) }
            }
            Text(tr("Kalite", "Quality")).font(.hfSmall).foregroundStyle(HC.muted)
            HfSegmented(options: [tr("Kötü", "Poor"), tr("Orta", "Fair"), tr("İyi", "Good")], selection: Binding(get: { ["kötü", "orta", "iyi"].firstIndex(of: quality) ?? 2 }, set: { quality = ["kötü", "orta", "iyi"][$0] }))
            HfButton(title: tr("Kaydet", "Save"), enabled: hours * 60 + minutes > 0) {
                Task { await app.saveSleep(minutes: hours * 60 + minutes, quality: quality); onDone() }
            }
        }.onAppear {
            let m = app.dashboard?.sleepMinutes ?? 0
            if m > 0 { hours = m / 60; minutes = (m % 60) / 5 * 5 }
        }
    }
}
