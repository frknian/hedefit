import SwiftUI
import Charts

/// Hedef yolculuğum: ilerleme halkası, tempo, ara hedefler, süreç ve günlük hedefler.
struct GoalJourneyView: View {
    @Environment(AppModel.self) private var app
    @State private var editCurrent = false
    @State private var editTarget = false
    @State private var pace: GoalPace = {
        switch UserDefaults.standard.string(forKey: "goal.pace") { case "Slow": return .slow; case "Fast": return .fast; default: return .steady }
    }()

    private func paceName(_ p: GoalPace) -> String { switch p { case .slow: return "Slow"; case .steady: return "Steady"; case .fast: return "Fast" } }
    private func paceLabel(_ p: GoalPace) -> String { switch p { case .slow: return tr("Yavaş", "Slow"); case .steady: return tr("Dengeli", "Steady"); case .fast: return tr("Hızlı", "Fast") } }

    var body: some View {
        if let d = app.dashboard {
            let u = app.units
            let weights = d.measurements.compactMap { m in m.weightKg.map { (m.date, $0) } }
            let current = weights.last?.1 ?? d.profile.weightKg
            let start = weights.first?.1 ?? current
            let target = d.profile.targetWeightKg
            let weeks = GoalScience.weeks(current: current, target: target, pace: pace)
            let losing = current != nil && target != nil && target! < current!
            let gaining = current != nil && target != nil && target! > current!
            let hm = d.profile.heightCm.map { $0 / 100 }
            let bmi = (current != nil && hm != nil && hm! > 0) ? current! / (hm! * hm!) : nil
            let targetBmi = (target != nil && hm != nil && hm! > 0) ? target! / (hm! * hm!) : nil
            let remaining = (current != nil && target != nil) ? abs(target! - current!) : nil
            let weeklyRate = (current != nil && target != nil && (remaining ?? 0) >= 0.1) ? GoalScience.weeklyRateKg(current: current!, target: target!, pace: pace) : nil
            let weekMinutes = d.sessions.filter { ($0.date ?? .distantPast) > Dates.add(-7) }.reduce(0) { $0 + $1.durationSeconds } / 60
            let recWater = GoalScience.recommendedWaterMl(weightKg: current, gender: d.profile.gender, weeklyTrainingMinutes: weekMinutes)
            let recProtein = GoalScience.recommendedProteinG(weightKg: current, losing: losing)
            let totalChange = (start != nil && target != nil) ? abs(target! - start!) : 0
            let progress = (totalChange > 0.05 && start != nil && current != nil) ? min(max(abs(current! - start!) / totalChange, 0), 1) : 0
            let finish = weeks.flatMap { Calendar.current.date(byAdding: .weekOfYear, value: $0, to: Date()) }
            let dateFmt = { (date: Date) in date.formatted(.dateTime.day().month(.wide).year().locale(AppLang.shared.locale)) }
            let kg = { (v: Double, dec: Int) in Units.formatWeight(v, u, decimals: dec) }
            ScreenScaffold(spacing: 14) {
                HfScreenHeader(title: tr("Hedef yolculuğum", "My goal journey")) { if !app.path.isEmpty { app.path.removeLast() } }
                HfCard(padding: 18) {
                    HStack(spacing: 16) {
                        ZStack { HfRing(progress: progress, lineWidth: 10).frame(width: 96, height: 96); Text("%\(Int(progress * 100))").font(.hfTitleM.weight(.black)).foregroundStyle(HC.text) }
                        VStack(alignment: .leading, spacing: 6) {
                            Text(losing ? tr("KİLO VERME", "WEIGHT LOSS") : gaining ? tr("KİLO ALMA", "WEIGHT GAIN") : target != nil ? tr("KİLOYU KORU", "MAINTAIN") : tr("HEDEF BELİRLE", "SET A GOAL")).font(.hfLabel.weight(.heavy)).foregroundStyle(HC.lime)
                            HStack {
                                weightButton(tr("Şimdi", "Now"), current.map { kg($0, 1) } ?? "—", end: false) { editCurrent = true }
                                Image(systemName: "arrow.right").foregroundStyle(HC.lime)
                                weightButton(tr("Hedef", "Target"), target.map { kg($0, 1) } ?? tr("Belirle", "Set"), end: true) { editTarget = true }
                            }
                            Text(target == nil ? tr("Hedef kilonu belirlemek için Hedef'e dokun", "Tap Target to set your goal") : tr("\(remaining.map { kg($0, 1) } ?? "—") kaldı • \(finish.map(dateFmt) ?? "—")", "\(remaining.map { kg($0, 1) } ?? "—") to go • \(finish.map(dateFmt) ?? "—")")).font(.hfSmall).foregroundStyle(HC.textSecondary)
                        }
                    }
                }
                if let current, let target, let remaining, remaining >= 0.1 {
                    HfCard(padding: 14) {
                        VStack(alignment: .leading, spacing: 10) {
                            Text(tr("Tempon", "Your pace")).font(.hfTitleM.weight(.heavy)).foregroundStyle(HC.text)
                            HStack(spacing: 8) {
                                ForEach(GoalPace.allCases, id: \.self) { option in
                                    let rate = GoalScience.weeklyRateKg(current: current, target: target, pace: option)
                                    let w = GoalScience.weeks(current: current, target: target, pace: option) ?? 0
                                    let sel = option == pace
                                    Button { pace = option; UserDefaults.standard.set(paceName(option), forKey: "goal.pace") } label: {
                                        VStack(spacing: 2) {
                                            Text(paceLabel(option)).font(.hfBody.weight(.bold)).foregroundStyle(HC.text)
                                            Text(kg(rate, 2) + tr("/hf", "/wk")).font(.hfSmall.weight(.black)).foregroundStyle(HC.lime)
                                            Text(tr("\(w) hafta", "\(w) weeks")).font(.system(size: 10)).foregroundStyle(HC.textSecondary)
                                            if option == .steady { Text(tr("Önerilen", "Recommended")).font(.system(size: 10, weight: .bold)).foregroundStyle(HC.lime) }
                                        }.frame(maxWidth: .infinity).padding(.vertical, 10).background(sel ? HC.lime.opacity(0.15) : HC.surfaceHigh, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                                            .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(sel ? HC.lime : .clear, lineWidth: 2))
                                    }.buttonStyle(.plain)
                                }
                            }
                            Text(losing ? tr("Haftada vücut ağırlığının %0,5–1'i kas kaybını en aza indirir. Daha hızlısı kas ve performans kaybını artırır.", "0.5–1% of body weight per week minimises muscle loss. Faster increases muscle and performance loss.") : tr("Haftada %0,25–0,5 kilo alımı kas kazanımını yağ artışına göre en iyi dengeler.", "Gaining 0.25–0.5% per week best balances muscle gain against fat gain.")).font(.hfSmall).foregroundStyle(HC.muted)
                            Text(tr("Günlük enerji farkı: ", "Daily energy difference: ") + "\(GoalScience.dailyEnergyDelta(current: current, target: target, pace: pace)) kcal").font(.hfBody.weight(.bold)).foregroundStyle(HC.text)
                        }
                    }
                }
                HStack(spacing: 10) {
                    HfStatTile(label: tr("Haftalık tempo", "Weekly pace"), value: weeklyRate.map { kg($0, 2) } ?? "—", valueColor: HC.lime)
                    HfStatTile(label: tr("Süre", "Duration"), value: weeks.map { tr("\($0) hafta", "\($0) weeks") } ?? "—")
                    HfStatTile(label: tr("VKİ", "BMI"), value: bmi.map { String(format: "%.1f", $0) } ?? "—", sub: targetBmi.map { tr("Hedef ", "Target ") + String(format: "%.1f", $0) })
                }
                HfCard {
                    VStack(alignment: .leading, spacing: 12) {
                        Text(tr("Kilo gidişatı", "Weight trend")).font(.hfTitleL).foregroundStyle(HC.text)
                        let pts = weights.suffix(12).map(\.1)
                        if pts.count >= 2 {
                            Text(tr("Ölçümlerin", "Your measurements")).font(.hfBody.weight(.bold)).foregroundStyle(HC.lime)
                            spark(pts, HC.lime).frame(height: 110)
                        }
                        if let current, let target {
                            Text(tr("Planlanan yol", "Planned path")).font(.hfBody.weight(.bold)).foregroundStyle(HC.water)
                            spark((0..<9).map { current + (target - current) * Double($0) / 8 }, HC.water).frame(height: 90)
                        }
                        Text(tr("\(d.measurements.count) ölçüm kayıtlı", "\(d.measurements.count) measurements saved")).font(.hfBody.weight(.semibold)).foregroundStyle(HC.text)
                    }
                }
                if let current, let target, let remaining, remaining >= 0.1, weeks != nil {
                    HfCard {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(tr("Ara hedefler", "Milestones")).font(.hfTitleL).foregroundStyle(HC.text).padding(.bottom, 6)
                            ForEach(Array([0.25, 0.5, 0.75, 1.0].enumerated()), id: \.offset) { i, share in
                                let base = start ?? current
                                let mw = base + (target - base) * share
                                let reached = losing ? current <= mw + 0.05 : current >= mw - 0.05
                                let w = reached ? 0 : Int(ceil(abs(mw - current) / (weeklyRate ?? 0.5)))
                                if i > 0 { HfDivider() }
                                HStack(spacing: 12) {
                                    Group { if reached { Image(systemName: "checkmark").font(.system(size: 14, weight: .bold)).foregroundStyle(HC.onLime) } else { Text("\(Int(share * 100))").font(.system(size: 11, weight: .bold)).foregroundStyle(HC.text) } }
                                        .frame(width: 30, height: 30).background(reached ? HC.lime : HC.surfaceHigh, in: Circle())
                                    Text(kg(mw, 1)).font(.hfBody.weight(.heavy)).foregroundStyle(HC.text); Spacer()
                                    Text(reached ? tr("Ulaşıldı", "Reached") : dateFmt(Calendar.current.date(byAdding: .weekOfYear, value: w, to: Date()) ?? Date())).font(.hfBody.weight(.bold)).foregroundStyle(reached ? HC.lime : HC.text)
                                }.padding(.vertical, 8)
                            }
                        }
                    }
                }
                HfCard {
                    VStack(alignment: .leading, spacing: 12) {
                        Text(tr("Süreç nasıl ilerleyecek", "How the process works")).font(.hfTitleL).foregroundStyle(HC.text)
                        ForEach(Array(phases(losing, gaining, weeks).enumerated()), id: \.offset) { i, p in
                            HStack(alignment: .top, spacing: 12) {
                                Text("\(i + 1)").font(.hfBody.weight(.heavy)).foregroundStyle(HC.lime).frame(width: 28, height: 28).background(HC.lime.opacity(0.16), in: Circle())
                                VStack(alignment: .leading, spacing: 2) { Text(p.0).font(.hfBody.weight(.heavy)).foregroundStyle(HC.text); Text(p.1).font(.hfBody).foregroundStyle(HC.textSecondary) }
                            }
                        }
                    }
                }
                HfCard {
                    VStack(spacing: 10) {
                        Text(tr("Bu hedef için günlük hedeflerin", "Daily targets for this goal")).font(.hfTitleL).foregroundStyle(HC.text).frame(maxWidth: .infinity, alignment: .leading)
                        row(tr("Kalori", "Calories"), "\(d.nutritionGoal.calories) kcal")
                        row("Protein", "\(d.nutritionGoal.protein) g" + (recProtein.map { tr(" • önerilen \($0) g", " • recommended \($0) g") } ?? ""))
                        row(tr("Karb / Yağ", "Carbs / Fat"), "\(d.nutritionGoal.carbs) g / \(d.nutritionGoal.fat) g")
                        row(tr("Adım", "Steps"), "\(app.prefs.stepGoal.formatted())" + tr(" • önerilen 8.000", " • recommended 8,000"))
                        row(tr("Su", "Water"), Units.formatWater(app.prefs.waterGoalMl, u) + tr(" • önerilen ", " • recommended ") + Units.formatWater(recWater, u))
                        if Double(app.prefs.waterGoalMl) > Double(recWater) * 1.5 || Double(app.prefs.waterGoalMl) < Double(recWater) * 0.6 {
                            HStack { Text(tr("Su hedefin bilimsel önerinin çok dışında.", "Your water goal is far from the evidence-based range.")).font(.hfSmall).foregroundStyle(HC.text); Spacer(); Button(tr("Önerilene ayarla", "Use recommended")) { app.prefs.waterGoalMl = recWater }.font(.hfBody.weight(.bold)).foregroundStyle(HC.lime) }
                                .padding(12).background(HC.warning.opacity(0.12), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                        }
                        row(tr("Hedef türü", "Goal type"), goalTypeLabel(d.profile.goal))
                    }
                }
                HfCard {
                    VStack(alignment: .leading, spacing: 10) {
                        Text(tr("Bilimsel kaynaklar", "Scientific sources")).font(.hfTitleL).foregroundStyle(HC.text)
                        ForEach(goalSources, id: \.url) { s in
                            Link(destination: URL(string: s.url)!) {
                                VStack(alignment: .leading, spacing: 2) { Text(LangStore.english ? s.claimEn : s.claim).font(.hfBody.weight(.bold)).foregroundStyle(HC.text).multilineTextAlignment(.leading); Text(s.citation + " ↗").font(.hfSmall).foregroundStyle(HC.lime) }
                            }
                        }
                        Text(tr("Bu hesaplar genel yetişkinler içindir; tıbbi tavsiye değildir.", "These estimates are for healthy adults and are not medical advice.")).font(.hfSmall).foregroundStyle(HC.muted)
                    }.frame(maxWidth: .infinity, alignment: .leading)
                }
            }
            .sheet(isPresented: $editCurrent) { WeightEditSheet(title: tr("Mevcut kilo", "Current weight"), value: current) { kgValue in Task { await app.saveQuickWeight(kgValue) } } }
            .sheet(isPresented: $editTarget) { WeightEditSheet(title: tr("Hedef kilo", "Target weight"), value: target) { kgValue in
                let p = d.profile
                Task { _ = await app.saveProfile(ProfileUpdate(displayName: p.displayName, age: p.age, gender: p.gender, heightCm: p.heightCm, weightKg: p.weightKg, goalType: p.goal, targetWeightKg: kgValue, targetWeeks: GoalScience.weeks(current: current, target: kgValue, pace: pace), environment: p.environment, equipment: p.equipment, historyAnswers: p.historyAnswers)) }
            } }
        } else { ProgressView().tint(HC.lime).frame(maxWidth: .infinity, maxHeight: .infinity) }
    }

    private func goalTypeLabel(_ goal: String) -> String {
        switch goal.components(separatedBy: " | ").first ?? goal {
        case "Kilo verme": return tr("Kilo verme", "Lose weight"); case "Kilo alma": return tr("Kilo alma", "Gain weight"); case "Kas kazanma", "Kas alma": return tr("Kas kazanma", "Build muscle"); case "Formu koruma": return tr("Formu koruma", "Stay in shape")
        case let g: return g.isEmpty ? "—" : g
        }
    }
    private func row(_ l: String, _ v: String) -> some View { HStack { Text(l).font(.hfBody.weight(.semibold)).foregroundStyle(HC.text); Spacer(); Text(v).font(.hfBody.weight(.heavy)).foregroundStyle(HC.text).multilineTextAlignment(.trailing) } }
    private func weightButton(_ label: String, _ value: String, end: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(alignment: end ? .trailing : .leading, spacing: 0) { Text(label.uppercased()).font(.hfLabel.weight(.bold)).foregroundStyle(HC.textSecondary); Text(value).font(.hfHeadline.weight(.heavy)).foregroundStyle(HC.text).minimumScaleFactor(0.6).lineLimit(1) }
                .frame(maxWidth: .infinity, alignment: end ? .trailing : .leading).padding(8)
        }.buttonStyle(.plain)
    }
    private func spark(_ values: [Double], _ color: Color) -> some View {
        Chart(Array(values.enumerated()), id: \.offset) { i, v in
            LineMark(x: .value("i", i), y: .value("v", v)).foregroundStyle(color).interpolationMethod(.catmullRom).lineStyle(StrokeStyle(lineWidth: 3, lineCap: .round))
        }.chartYScale(domain: .automatic(includesZero: false)).chartXAxis(.hidden).chartYAxis { AxisMarks { _ in AxisGridLine().foregroundStyle(HC.divider) } }
    }

    private func phases(_ losing: Bool, _ gaining: Bool, _ weeks: Int?) -> [(String, String)] {
        let total = weeks ?? 12, half = max(total / 2, 4)
        if LangStore.english {
            return [("Weeks 1–2 • Adaptation", losing ? "The scale can drop fast at first, mostly water. Build the routine: log meals, hit protein, walk daily." : "Your body adapts to the new intake and training. Focus on consistency over perfection."),
                    ("Weeks 3–\(half) • Momentum", gaining ? "Aim for the weekly pace with a small calorie surplus and progressive training. Protein at every meal." : "The weekly pace settles. Keep the calorie target, reach your step goal and complete planned workouts."),
                    ("Every 4 weeks • Check-in", "Weigh yourself under the same conditions and compare with the planned path. Targets are recalculated from your new weight."),
                    ("Plateaus are normal", "1–2 flat weeks happen. Keep going; if it lasts 3 weeks, adjust calories by ~100–150 kcal or add activity."),
                    ("Final stretch • Maintain", "Near the goal the pace slows. After reaching it, move to maintenance calories to keep the result.")]
        }
        return [("1–2. hafta • Uyum", losing ? "Başta tartı hızlı düşebilir, bu çoğunlukla sudur. Rutini oturt: öğünlerini kaydet, proteini tamamla, her gün yürü." : "Vücudun yeni beslenme ve antrenmana uyum sağlar. Mükemmellik değil süreklilik hedefle."),
                ("3–\(half). hafta • İvme", gaining ? "Küçük bir kalori fazlası ve artan antrenman yüküyle haftalık tempoyu yakala. Her öğünde protein olsun." : "Haftalık tempo oturur. Kalori hedefini koru, adım hedefine ulaş ve planlı antrenmanlarını tamamla."),
                ("Her 4 haftada • Kontrol", "Aynı koşullarda tartıl ve planlanan yolla karşılaştır. Hedeflerin yeni kilona göre yeniden hesaplanır."),
                ("Duraklama normaldir", "1–2 hafta sabit kalmak olağandır. Devam et; 3 haftayı geçerse kaloriyi ~100–150 kcal ayarla ya da aktiviteyi artır."),
                ("Son dönem • Koruma", "Hedefe yaklaştıkça tempo yavaşlar. Ulaştıktan sonra sonucu korumak için koruma kalorisine geçersin.")]
    }
}

struct WeightEditSheet: View {
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss
    let title: String
    let value: Double?
    var onSave: (Double) -> Void
    @State private var text = ""
    var body: some View {
        let u = app.units
        let parsed = Double(text.replacingOccurrences(of: ",", with: "."))
        NavigationStack {
            VStack(spacing: 16) {
                HfField(title: "\(title) (\(Units.weightUnit(u)))", text: Binding(get: { text }, set: { text = String($0.filter { $0.isNumber || $0 == "," || $0 == "." }.prefix(6)) }), keyboard: .decimalPad)
                HfButton(title: tr("Kaydet", "Save"), enabled: parsed.map { (30.0...300.0).contains(Units.weightToKg($0, u)) } ?? false) { onSave(Units.weightToKg(parsed!, u)); dismiss() }
                Spacer()
            }.padding(20).background(HC.bg).navigationTitle(title).navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button(tr("Vazgeç", "Cancel")) { dismiss() } } }
        }.presentationDetents([.height(280)]).onAppear { text = value.map { String(format: "%.1f", Units.weightValue($0, u)) } ?? "" }
    }
}
