import SwiftUI

struct ActiveWorkoutHost: View {
    @Environment(AppModel.self) private var app
    @State private var showPlate = false
    private var w: WorkoutModel { app.workout }
    private let ticker = Timer.publish(every: 0.5, on: .main, in: .common).autoconnect()
    private let second = Timer.publish(every: 1, on: .main, in: .common).autoconnect()

    var body: some View {
        @Bindable var w = app.workout
        ZStack {
            HC.bg.ignoresSafeArea()
            VStack(spacing: 0) {
                topBar
                if w.paused { pausedBanner.padding(.horizontal, 18) }
                ScrollView {
                    VStack(spacing: 12) {
                        hero
                        controls
                    }.padding(.horizontal, 18).padding(.bottom, 30)
                }.scrollIndicators(.hidden)
            }
        }
        .onReceive(ticker) { _ in w.tick() }
        .onReceive(second) { _ in w.stopwatchTick() }
        .sheet(isPresented: $w.showReplacement) { ExerciseReplacementSheet() }
        .sheet(isPresented: $w.showCoach) { WorkoutCoachSheet() }
        .sheet(isPresented: $showPlate) { PlateCalculatorSheet(targetKg: w.weight) }
        .sheet(isPresented: $w.showFeedback) { WorkoutFeedbackSheet() }
        .alert(tr("Antrenmanı bitir?", "Finish workout?"), isPresented: $w.showFinishConfirm) {
            Button(tr("Devam et", "Keep training"), role: .cancel) {}
            Button(tr("Antrenmanı bitir", "Finish workout"), role: .destructive) { w.requestFinish() }
        } message: { Text(tr("\(w.completedSets.count) set tamamlandı\n\(clock(w.elapsed))", "\(w.completedSets.count) sets completed\n\(clock(w.elapsed))")) }
        .confirmationDialog(tr("Set yapılmadı", "No sets logged"), isPresented: $w.showNoSets, titleVisibility: .visible) {
            Button(tr("Yarına ertele", "Postpone to the next free day")) { Task { await w.skipWorkout(postpone: true) } }
            Button(tr("Bugünü pas geç", "Skip today"), role: .destructive) { Task { await w.skipWorkout(postpone: false) } }
            Button(tr("Antrenmana dön", "Back to workout"), role: .cancel) {}
        } message: { Text(tr("Hiç set tamamlamadan bitirmek istiyorsun. Antrenmanı ertelemek ya da pas geçmek ister misin?", "You're finishing without any sets. Postpone or skip today's workout?")) }
        .alert(tr("Isınma ve hazırlık", "Warm-up"), isPresented: Binding(get: { !w.prepared }, set: { _ in })) {
            Button(tr("Hazırım, başla", "I'm ready")) { w.prepared = true; w.persist() }
        } message: { Text(tr("• 3–5 dk hafif kardiyo\n• Eklemlere dinamik mobilite\n• İlk harekette 1–2 hafif ısınma seti\n\nSet türünden 'Isınma'yı seçerek hazırlık setlerini ayrıca kaydedebilirsin.", "• 3–5 min light cardio\n• Dynamic joint mobility\n• 1–2 light sets for the first move\n\nChoose Warm-up as the set type to log preparation sets.")) }
        .task { await pullRemote() }
    }

    private func pullRemote() async {
        if let remote = try? await app.repo.fetchActiveWorkout(ownDeviceId: w.deviceId), w.importRemote(remote), w.exercises.isEmpty { w.resumeRecoverable() }
    }

    private func clock(_ s: Int) -> String { String(format: "%02d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) }

    // MARK: Üst çubuk

    private var topBar: some View {
        HStack(spacing: 6) {
            Button { w.minimize() } label: { Image(systemName: "chevron.down").font(.system(size: 18, weight: .bold)).foregroundStyle(HC.text).frame(width: 44, height: 44) }
            VStack(alignment: .leading, spacing: 0) {
                Text(tr("Aktif antrenman", "Active workout")).font(.hfTitleL).foregroundStyle(HC.text)
                Text(clock(w.elapsed)).font(.system(size: 13, weight: .semibold).monospacedDigit()).foregroundStyle(HC.lime)
            }
            Spacer()
            Button { w.paused.toggle() } label: { Image(systemName: w.paused ? "play.fill" : "pause.fill").foregroundStyle(HC.lime).frame(width: 44, height: 44) }
            Button(tr("Bitir", "Finish")) { w.showFinishConfirm = true }.font(.system(size: 16, weight: .semibold)).foregroundStyle(HC.coral).padding(.trailing, 12)
        }.padding(.horizontal, 10).frame(height: 64)
    }

    private var pausedBanner: some View {
        HfCard {
            HStack { Image(systemName: "pause.fill").foregroundStyle(HC.warning); Text(tr("Antrenman duraklatıldı", "Workout paused")).font(.hfTitleM).foregroundStyle(HC.text); Spacer(); Button(tr("Devam et", "Resume")) { w.paused = false }.foregroundStyle(HC.lime) }
        }
    }

    // MARK: Hero

    private var hero: some View {
        ZStack(alignment: .bottomLeading) {
            if let ex = w.exercise { ExerciseMotionPlayer(id: ex.id).frame(height: 300) } else { HC.surfaceHigh.frame(height: 300) }
            LinearGradient(colors: [.clear, HC.bg.opacity(0.92)], startPoint: .center, endPoint: .bottom)
            VStack(alignment: .leading, spacing: 2) {
                Text(w.exercise?.name ?? tr("Program yüklenemedi", "Program couldn't load")).font(.system(size: 26, weight: .heavy)).foregroundStyle(HC.text).lineLimit(2)
                Text("\(w.currentSet) / \(w.exercise?.sets ?? 4) set").font(.hfHeadline).foregroundStyle(HC.lime)
            }.padding(16)
        }.frame(height: 300).clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
    }

    // MARK: Kontroller

    private var controls: some View {
        @Bindable var w = app.workout
        let prev = w.previousSets(for: w.exercise?.id)
        let imperial = Units.isImperial(app.units)
        let step = imperial ? 2.268 : 2.5
        return VStack(spacing: 12) {
            Text(tr("Hareket \(w.exerciseIndex + 1)/\(w.exercises.count) • Set \(w.currentSet)/\(w.totalSets)", "Exercise \(w.exerciseIndex + 1)/\(w.exercises.count) • Set \(w.currentSet)/\(w.totalSets)")).font(.hfTitleM).foregroundStyle(HC.lime).frame(maxWidth: .infinity, alignment: .leading)
            if !prev.isEmpty {
                let old = prev.indices.contains(w.currentSet - 1) ? prev[w.currentSet - 1] : prev.last!
                HfCard { HStack { Image(systemName: "flame.fill").foregroundStyle(HC.warning); Text(tr("Önceki: ", "Previous: ") + "\(old.weightKg.map { Units.formatWeight($0, app.units, decimals: 0) } ?? tr("vücut ağırlığı", "bodyweight")) × \(old.reps.map(String.init) ?? "—") • RPE \(old.rpe.map(String.init) ?? "—")").foregroundStyle(HC.textSecondary).font(.hfBody); Spacer() } }
                if let tip = overloadTip(old) { Text(tip).font(.hfSmall).foregroundStyle(HC.lime).frame(maxWidth: .infinity, alignment: .leading) }
            }
            if w.personalRecord { HfCard { HStack { Image(systemName: "flame.fill").foregroundStyle(HC.lime); Text(tr("Yeni kişisel rekor! Son set önceki performansını geçti.", "New personal record! Your last set beat your previous best.")).font(.hfBody.weight(.bold)).foregroundStyle(HC.lime); Spacer() } } }
            HStack(spacing: 10) {
                counter(value: Units.formatWeight(w.weight, app.units, decimals: w.weight.truncatingRemainder(dividingBy: 1) == 0 ? 0 : 1), label: tr("Ağırlık", "Weight"), minus: { w.adjustWeight(-step) }, plus: { w.adjustWeight(step) })
                counter(value: tr("\(w.reps) tekrar", "\(w.reps) reps"), label: tr("Tekrar", "Reps"), minus: { w.adjustReps(-1) }, plus: { w.adjustReps(1) })
            }
            ScrollView(.horizontal) {
                HStack(spacing: 6) {
                    ForEach([("warmup", tr("Isınma", "Warm-up")), ("normal", "Normal"), ("superset", tr("Süper", "Superset")), ("dropset", "Drop"), ("failure", tr("Tükeniş", "Failure"))], id: \.0) { value, label in
                        HfChip(text: label, selected: w.setType == value) { w.setType = value; w.persist() }
                    }
                }
            }.scrollIndicators(.hidden)
            HfCard {
                VStack(spacing: 6) {
                    HStack { Text(tr("Efor (RPE)", "Effort (RPE)")).foregroundStyle(HC.text); Spacer(); Text("\(w.rpe)/10").foregroundStyle(HC.lime).fontWeight(.bold) }.font(.hfBody)
                    Slider(value: Binding(get: { Double(w.rpe) }, set: { w.rpe = Int($0); w.persist() }), in: 1...10, step: 1).tint(HC.lime)
                }
            }
            if w.restSeconds > 0 { restCard }
            stopwatchCard
            TextField(tr("Set notu (isteğe bağlı)", "Set note (optional)"), text: $w.note).textFieldStyle(.plain).padding(14).background(HC.surfaceHigh, in: RoundedRectangle(cornerRadius: 14, style: .continuous)).foregroundStyle(HC.text)
            if w.weight >= 15 { HfButton(title: tr("Plaka hesaplayıcı", "Plate calculator"), icon: "circle.grid.cross", secondary: true) { showPlate = true } }
            HfButton(title: w.saving ? tr("Kaydediliyor…", "Saving…") : (w.isLastSet ? tr("Antrenmanı değerlendir", "Review workout") : tr("Seti tamamla", "Complete set")), icon: "checkmark", enabled: !(w.saving || w.paused || w.restSeconds > 0)) { w.recordSetAndContinue() }
            HfNavRow(icon: "forward.fill", tint: HC.lime, title: tr("Hareketi atla", "Skip exercise"), chevron: false) { w.skipExercise() }
            HStack(spacing: 10) {
                HfButton(title: tr("Hareketi değiştir", "Swap exercise"), icon: "arrow.triangle.2.circlepath", secondary: true) { w.showReplacement = true }
                HfButton(title: tr("Koça sor", "Ask coach"), icon: "sparkles", secondary: true) { w.showCoach = true }
            }
            Text(tr("Antrenmanı bitirdikten sonra 3–5 dk hafif soğuma ve esneme yap.", "After finishing, do 3–5 min of light cool-down and stretching.")).font(.hfSmall).foregroundStyle(HC.muted).multilineTextAlignment(.center)
        }
    }

    private func overloadTip(_ p: PreviousSet) -> String? {
        guard let weight = p.weightKg, let reps = p.reps else { return nil }
        if reps >= 12 && (p.rpe ?? 8) <= 8 {
            let next = weight >= 50 ? weight + 2.5 : weight + 1.25
            return tr("Yük önerisi: \(Units.formatWeight(weight, app.units, decimals: 1)) yerine \(Units.formatWeight(next, app.units, decimals: 1)) ile 8–10 dene.", "Load tip: try \(Units.formatWeight(next, app.units, decimals: 1)) instead of \(Units.formatWeight(weight, app.units, decimals: 1)) for 8–10 reps.")
        }
        if reps < 12 { return tr("Yük önerisi: \(Units.formatWeight(weight, app.units, decimals: 1)) ile \(reps + 1) tekrar hedefle.", "Load tip: aim for \(reps + 1) reps at \(Units.formatWeight(weight, app.units, decimals: 1)).") }
        return tr("Yük önerisi: \(Units.formatWeight(weight, app.units, decimals: 1)) ile formu koruyarak tekrar et.", "Load tip: repeat \(Units.formatWeight(weight, app.units, decimals: 1)) with good form.")
    }

    private func counter(value: String, label: String, minus: @escaping () -> Void, plus: @escaping () -> Void) -> some View {
        HfCard {
            VStack(spacing: 9) {
                Text(value).font(.system(size: 22, weight: .semibold)).foregroundStyle(HC.text).minimumScaleFactor(0.6).lineLimit(1)
                HStack {
                    round("minus", minus); Spacer(); Text(label).font(.hfSmall).foregroundStyle(HC.textSecondary); Spacer(); round("plus", plus)
                }
            }
        }
    }
    private func round(_ icon: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) { Image(systemName: icon).font(.system(size: 16, weight: .bold)).foregroundStyle(HC.lime).frame(width: 40, height: 40).background(HC.surfaceHigh, in: Circle()) }.buttonStyle(.plain)
    }

    private var restCard: some View {
        let w = app.workout
        return HfCard {
            HStack(spacing: 18) {
                ZStack {
                    HfRing(progress: Double(w.restSeconds) / 180, lineWidth: 8).frame(width: 108, height: 108)
                    VStack(spacing: 0) { Text(tr("Dinlenme", "Rest")).font(.hfSmall).foregroundStyle(HC.textSecondary); Text(String(format: "%02d:%02d", w.restSeconds / 60, w.restSeconds % 60)).font(.system(size: 26, weight: .semibold).monospacedDigit()).foregroundStyle(HC.text) }
                }
                VStack(alignment: .leading, spacing: 8) {
                    Text(tr("Akıllı süre: hareketin zorluğu ve RPE'ye göre ayarlandı.", "Smart timer: set from the exercise difficulty and RPE.")).font(.hfSmall).foregroundStyle(HC.textSecondary)
                    HStack { Button("+30 sn") { w.addRest() }; Button(w.restPaused ? tr("Devam", "Resume") : tr("Duraklat", "Pause")) { w.toggleRest() } }.font(.hfBody.weight(.semibold)).foregroundStyle(HC.lime)
                    Button(tr("Sonraki set / atla", "Next set / skip")) { w.skipRest() }.font(.hfBody.weight(.semibold)).foregroundStyle(HC.text).padding(.horizontal, 12).padding(.vertical, 8).overlay(Capsule().stroke(HC.divider))
                }
            }
        }
    }

    private var stopwatchCard: some View {
        let w = app.workout
        return HfCard {
            VStack(spacing: 9) {
                HStack { Text(tr("Zamanlayıcı", "Timer")).font(.hfTitleM).foregroundStyle(HC.text); Spacer(); Text(String(format: "%02d:%02d", w.stopwatchSeconds / 60, w.stopwatchSeconds % 60)).font(.system(size: 22, weight: .semibold).monospacedDigit()).foregroundStyle(HC.lime) }
                ScrollView(.horizontal) { HStack(spacing: 7) { ForEach([30, 60, 90, 120], id: \.self) { s in HfChip(text: "\(s / 60):\(String(format: "%02d", s % 60))", selected: w.stopwatchSeconds == s) { w.setTimer(s) } } } }.scrollIndicators(.hidden)
                HStack(spacing: 8) {
                    HfButton(title: w.stopwatchRunning ? tr("Duraklat", "Pause") : tr("Başlat", "Start"), secondary: true) { w.stopwatchRunning.toggle() }
                    HfButton(title: tr("Sıfırla", "Reset"), secondary: true) { w.setTimer(0) }
                }
            }
        }
    }
}

// MARK: - Plaka hesaplayıcı

struct PlateCalculatorSheet: View {
    let targetKg: Double
    @State private var bar = 20.0
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        let perSide = max(targetKg - bar, 0) / 2
        var remaining = perSide
        var plates: [Double] = []
        for plate in [25.0, 20, 15, 10, 5, 2.5, 1.25] { let c = Int(remaining / plate); plates += Array(repeating: plate, count: c); remaining -= Double(c) * plate }
        return NavigationStack {
            VStack(alignment: .leading, spacing: 16) {
                Text(tr("Hedef: \(Int(targetKg)) kg", "Target: \(Int(targetKg)) kg")).font(.hfHeadline.weight(.bold)).foregroundStyle(HC.lime)
                HStack { ForEach([15.0, 20.0], id: \.self) { v in HfChip(text: "\(Int(v)) kg bar", selected: bar == v) { bar = v } } }
                Text(plates.isEmpty ? tr("Yalnızca barı kullan.", "Use the bar only.") : tr("Her tarafa: ", "Each side: ") + plates.map { $0.truncatingRemainder(dividingBy: 1) == 0 ? "\(Int($0))" : "\($0)" }.joined(separator: " + ") + " kg").font(.hfBody).foregroundStyle(HC.textSecondary)
                Spacer()
            }.padding(20).background(HC.bg).navigationTitle(tr("Plaka hesaplayıcı", "Plate calculator")).navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button(tr("Tamam", "OK")) { dismiss() } } }
        }.presentationDetents([.height(320)])
    }
}

// MARK: - Antrenman sonu geri bildirimi

struct WorkoutFeedbackSheet: View {
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss
    @State private var difficulty = "Uygun"
    @State private var fatigue = 3.0
    @State private var pain = "Yok"
    @State private var note = ""
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Text(tr("Fit Koç sonraki planını bu geri bildirime göre düzenler. Bitirdikten sonra 3–5 dk hafif soğuma ve esneme yap.", "Fit Coach adjusts your next plan based on this feedback. Finish with 3–5 min of light cool-down and stretching.")).font(.hfBody).foregroundStyle(HC.textSecondary)
                    HfSegmented(options: [tr("Kolay", "Easy"), tr("Uygun", "Just right"), tr("Zor", "Hard")], selection: Binding(get: { ["Kolay", "Uygun", "Zor"].firstIndex(of: difficulty) ?? 1 }, set: { difficulty = ["Kolay", "Uygun", "Zor"][$0] }))
                    Text(tr("Yorgunluk: \(Int(fatigue))/5", "Fatigue: \(Int(fatigue))/5")).font(.hfTitleM).foregroundStyle(HC.text)
                    Slider(value: $fatigue, in: 1...5, step: 1).tint(HC.lime)
                    Text(tr("Ağrı / hassasiyet", "Pain / tenderness")).font(.hfTitleM).foregroundStyle(HC.text)
                    HfChipRow { ForEach([("Yok", tr("Yok", "None")), ("Bel", tr("Bel", "Lower back")), ("Diz", tr("Diz", "Knee")), ("Omuz", tr("Omuz", "Shoulder"))], id: \.0) { v, l in HfChip(text: l, selected: pain == v) { pain = v } } }
                    TextField(tr("Koça not", "Note to coach"), text: $note, axis: .vertical).padding(14).background(HC.surfaceHigh, in: RoundedRectangle(cornerRadius: 14, style: .continuous)).foregroundStyle(HC.text)
                    HfButton(title: app.workoutSaving ? tr("Kaydediliyor…", "Saving…") : tr("Bitir ve uyarla", "Finish & adapt"), loading: app.workoutSaving) {
                        let feedback = WorkoutFeedback(difficulty: difficulty, fatigue: Int(fatigue), painAreas: [pain], note: String(note.prefix(500)))
                        Task { await app.workout.finish(feedback: feedback) }
                    }
                }.padding(20)
            }.background(HC.bg).navigationTitle(tr("Antrenman nasıldı?", "How was the workout?")).navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button(tr("Geri dön", "Go back")) { dismiss() } } }
        }.presentationDetents([.large]).interactiveDismissDisabled(app.workoutSaving)
    }
}

// MARK: - Hareket değiştirme

struct ExerciseReplacementSheet: View {
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss
    @State private var reason = "too_hard"
    @State private var painArea: String?
    private var w: WorkoutModel { app.workout }

    private var reasons: [(String, String)] {
        [("too_hard", tr("Çok zor", "Too hard")), ("cant_do", tr("Yapamıyorum", "Can't do form")), ("too_easy", tr("Çok kolay", "Too easy")), ("no_equipment", tr("Ekipmanım yok", "No equipment")),
         ("pain_discomfort", tr("Ağrı / rahatsızlık", "Joint pain / discomfort")), ("dont_understand", tr("Hareketi anlamadım", "Don't understand")), ("disliked", tr("Sevmiyorum", "Don't like this"))]
    }
    private var painAreas: [(String, String)] { [("knee", tr("Diz", "Knee")), ("shoulder", tr("Omuz", "Shoulder")), ("lower_back", tr("Bel", "Lower back")), ("wrist", tr("Bilek", "Wrist")), ("hip", tr("Kalça", "Hip"))] }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Text(w.exercise?.name ?? "").font(.hfTitleM.weight(.semibold)).foregroundStyle(HC.lime)
                    Text(tr("Bu hareketi neden değiştirmek istiyorsun?", "Why do you want to change it?")).font(.hfBody.weight(.semibold)).foregroundStyle(HC.text)
                    FlowLayout(spacing: 8) {
                        ForEach(reasons, id: \.0) { code, label in
                            Button { reason = code; Task { await w.requestReplacement(reason: code, discomfortArea: painArea) } } label: {
                                Text(label).font(.hfLabel.weight(reason == code ? .bold : .regular)).foregroundStyle(reason == code ? HC.lime : HC.text).padding(.horizontal, 14).padding(.vertical, 9)
                                    .background(reason == code ? HC.lime.opacity(0.2) : HC.surfaceHigh, in: RoundedRectangle(cornerRadius: 12)).overlay(RoundedRectangle(cornerRadius: 12).stroke(reason == code ? HC.lime : .clear))
                            }.buttonStyle(.plain)
                        }
                    }
                    if reason == "pain_discomfort" {
                        Text(tr("Hangi bölgede rahatsızlık hissediyorsun?", "Where do you feel discomfort?")).font(.hfLabel).foregroundStyle(HC.textSecondary)
                        HfChipRow { ForEach(painAreas, id: \.0) { c, l in HfChip(text: l, selected: painArea == c) { painArea = c; Task { await w.requestReplacement(reason: "pain_discomfort", discomfortArea: c) } } } }
                    }
                    if w.replacementBusy { ProgressView().tint(HC.lime).frame(maxWidth: .infinity).padding(24) }
                    else if let c = w.replacementCandidate {
                        HfCard(fill: HC.surfaceHigh) {
                            VStack(alignment: .leading, spacing: 12) {
                                HStack { Image(systemName: "arrow.triangle.2.circlepath.circle.fill").foregroundStyle(HC.lime); Text(badge(c.progressionType)).font(.hfLabel.weight(.bold)).foregroundStyle(HC.lime) }
                                HStack(alignment: .top) {
                                    VStack(alignment: .leading) { Text(tr("Mevcut", "Current")).font(.hfSmall).foregroundStyle(HC.textSecondary); Text(c.originalExerciseName).font(.hfBody.weight(.semibold)).foregroundStyle(HC.text.opacity(0.6)) }
                                    Spacer(); Image(systemName: "arrow.right").foregroundStyle(HC.lime); Spacer()
                                    VStack(alignment: .trailing) { Text(tr("Önerilen", "Proposed")).font(.hfSmall).foregroundStyle(HC.lime); Text(c.replacementExerciseName).font(.hfBody.weight(.bold)).foregroundStyle(HC.text).multilineTextAlignment(.trailing) }
                                }
                                ExerciseMedia(id: c.replacementExerciseId, maxDimension: 512).frame(height: 160).clipShape(RoundedRectangle(cornerRadius: 14))
                                if !c.explanationTr.isEmpty { Text(c.explanationTr).font(.hfBody).foregroundStyle(HC.textSecondary) }
                                Text("\(c.sets) × \(c.reps) • \(c.restSeconds) \(tr("sn dinlenme", "sec rest"))").font(.hfSmall).foregroundStyle(HC.muted)
                                HfButton(title: tr("Bu hareketle değiştir", "Swap to this exercise"), icon: "checkmark") { w.applyReplacement(c) }
                            }
                        }
                    }
                }.padding(20)
            }.background(HC.bg).navigationTitle(tr("Hareketi değiştir", "Replace exercise")).navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button(tr("Kapat", "Close")) { dismiss() } } }
        }
        .task { if w.replacementCandidate == nil { await w.requestReplacement(reason: "too_hard", discomfortArea: nil) } }
        .presentationDetents([.large])
    }

    private func badge(_ type: String) -> String {
        switch type {
        case "regression": return tr("Kontrollü regresyon (daha kolay)", "Controlled regression")
        case "progression": return tr("İleri varyasyon (daha zor)", "Higher progression")
        case "equipment_swap": return tr("Ekipmanına uyumlu", "Equipment match")
        case "safety_swap": return tr("Eklem dostu güvenli alternatif", "Joint friendly")
        default: return tr("Hedef kas alternatifi", "Targeted alternative")
        }
    }
}

/// Basit satır kaydıran çip yerleşimi.
struct FlowLayout: Layout {
    var spacing: CGFloat = 8
    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? 320
        var x: CGFloat = 0, y: CGFloat = 0, rowH: CGFloat = 0
        for v in subviews { let s = v.sizeThatFits(.unspecified); if x + s.width > width { x = 0; y += rowH + spacing; rowH = 0 }; x += s.width + spacing; rowH = max(rowH, s.height) }
        return CGSize(width: width, height: y + rowH)
    }
    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX, y = bounds.minY, rowH: CGFloat = 0
        for v in subviews { let s = v.sizeThatFits(.unspecified); if x + s.width > bounds.maxX { x = bounds.minX; y += rowH + spacing; rowH = 0 }; v.place(at: CGPoint(x: x, y: y), proposal: .unspecified); x += s.width + spacing; rowH = max(rowH, s.height) }
    }
}

// MARK: - Antrenman içi koç

struct WorkoutCoachSheet: View {
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss
    @State private var input = ""
    private var context: WorkoutCoachContext {
        let w = app.workout
        return WorkoutCoachContext(exerciseId: w.exercise?.id, exerciseName: w.exercise?.name, muscleGroup: w.exercise?.area, targetSets: w.totalSets, currentSet: w.currentSet, reps: w.exercise?.reps,
                                   workoutDurationMinutes: w.elapsed / 60, elapsedSeconds: w.elapsed, isBeginner: (app.dashboard?.sessions.count ?? 0) < 5)
    }
    var body: some View {
        NavigationStack {
            CoachChatBody(input: $input, context: context, quick: [
                tr("Bu harekette zorlanıyorum, ne yapabilirim?", "I'm struggling with this exercise, what can I do?"), tr("Bu hareket nereyi çalıştırır?", "What does this exercise work?"),
                tr("Doğru form ve nefes nasıl olmalı?", "What's the right form and breathing?"), tr("Daha kolay bir alternatifi var mı?", "Is there an easier alternative?"), tr("Dinlenme süresi kaç saniye olmalı?", "How many seconds should I rest?")])
            .navigationTitle(app.workout.exercise?.name ?? tr("Fit Koç", "Fit Coach")).navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button(tr("Kapat", "Close")) { dismiss() } } }
        }.presentationDetents([.medium, .large]).onAppear { app.coach.greetIfNeeded() }
    }
}
