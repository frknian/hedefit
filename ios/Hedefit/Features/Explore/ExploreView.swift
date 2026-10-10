import SwiftUI

enum ExploreSegment: Int { case programs, challenges, community }

struct ExploreView: View {
    @Environment(AppModel.self) private var app
    @State private var segment: Int = 0

    var body: some View {
        ScreenScaffold(spacing: 14) {
            Text(tr("Keşfet", "Explore")).font(.hfTitle).foregroundStyle(HC.text)
            HfSegmented(options: [tr("Programlar", "Programs"), "Challenge", tr("Topluluk", "Community")], selection: $segment)
            switch ExploreSegment(rawValue: segment) ?? .programs {
            case .programs: ProgramsSection()
            case .challenges: ChallengeHubSection()
            case .community: CommunitySection()
            }
        }
        .refreshable { await app.refreshAll(); await app.challenge.loadHub() }
        .task(id: segment) {
            if segment == 1 { await app.challenge.loadHub() }
            if segment == 2 { await app.social.loadSummary(); await app.social.loadLeaderboard(); await app.social.loadChallenges(); await app.social.loadDiscoverable(); await app.challenge.loadShareProgress() }
        }
        .onAppear { if let s = app.exploreSegmentRequest { segment = s.rawValue; app.exploreSegmentRequest = nil } }
    }
}

// MARK: - Programlar

struct ProgramsSection: View {
    @Environment(AppModel.self) private var app
    @State private var editing: WorkoutExercise?
    @State private var previewing: WorkoutExercise?
    @State private var replacing: WorkoutExercise?
    @State private var showReadiness = false
    @State private var showTimeAdapt = false
    @State private var renaming: WorkoutProgram?
    @State private var renameText = ""
    @State private var removing: WorkoutProgram?
    @State private var showAll = false
    @State private var scanTip = false

    private var d: Dashboard? { app.dashboard }

    var body: some View {
        let workouts = d?.workouts ?? []
        let programs = d?.workoutPrograms ?? []
        let active = app.activeProgram
        VStack(alignment: .leading, spacing: 14) {
            categories
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(tr("Antrenmanım", "My Workout")).font(.hfTitleL).foregroundStyle(HC.text)
                    Text(active.map { localizedProgramName($0.name, source: $0.source) } ?? tr("Aktif program yok", "No active program")).font(.hfSmall).foregroundStyle(HC.textSecondary).lineLimit(1)
                }
                Spacer()
                HfCircleButton(system: "calendar", label: tr("Antrenman takvimi", "Workout calendar")) { app.push(.calendar) }
            }
            if app.workout.hasRecoverable && !app.workout.isPresented {
                HfNavRow(icon: "play.fill", tint: HC.lime, title: tr("Antrenman devam ediyor", "Workout in progress"), subtitle: tr("Setlerini kaybetmeden devam et", "Tap to resume without losing your sets")) { app.workout.resumeRecoverable() }
            }
            if app.newBlockDue(PlanRotationPeriod(key: app.prefs.planRotation)) { newBlockCard }
            activeCard(workouts: workouts, active: active)
            HfSectionHeader(title: tr("Araçlar", "Tools"))
            tools
            if !programs.isEmpty {
                HfSectionHeader(title: tr("Programlarım", "My programs"), trailing: nil)
                ForEach(programs) { program in programRow(program) }
            }
            HfNavRow(icon: "plus", tint: HC.textSecondary, title: tr("Yeni program oluştur", "Create a new program"), subtitle: tr("AI ile, hazır şablondan veya kendin", "With AI, from a template or your own")) { app.push(.programHub) }
        }
        .sheet(item: $editing) { ExerciseEditorSheet(exercise: $0) }
        .sheet(item: $previewing) { ExercisePreviewSheet(exercise: $0) }
        .sheet(item: $replacing) { ProgramReplacementSheet(exercise: $0) }
        .sheet(isPresented: $showReadiness) { ReadinessSheet() }
        .sheet(isPresented: $showTimeAdapt) { TimeAdaptationSheet() }
        .alert(tr("Program adını değiştir", "Rename program"), isPresented: Binding(get: { renaming != nil }, set: { if !$0 { renaming = nil } })) {
            TextField(tr("Program adı", "Program name"), text: $renameText)
            Button(tr("Vazgeç", "Cancel"), role: .cancel) { renaming = nil }
            Button(tr("Kaydet", "Save")) { if let p = renaming { Task { await app.renameProgram(p, to: renameText) } }; renaming = nil }
        }
        .alert(tr("Program kaldırılsın mı?", "Remove program?"), isPresented: Binding(get: { removing != nil }, set: { if !$0 { removing = nil } })) {
            Button(tr("Vazgeç", "Cancel"), role: .cancel) { removing = nil }
            Button(tr("Kaldır", "Remove"), role: .destructive) { if let p = removing { Task { await app.deleteProgram(p) } }; removing = nil }
        } message: { Text(tr("\(removing.map { localizedProgramName($0.name, source: $0.source) } ?? "") kaldırılacak. Tamamlanmış antrenman kayıtların korunur.", "\(removing.map { localizedProgramName($0.name, source: $0.source) } ?? "") will be removed. Completed workout records stay safe.")) }
    }

    // MARK: Kategoriler

    private var categories: some View {
        let items: [(String, String, () -> Void)] = [
            ("sparkles", tr("Sana Özel", "For You"), { app.push(.aiProgram) }),
            ("dumbbell.fill", "Gym", { app.openLibrary(environment: "gym") }),
            ("house.fill", tr("Evde", "At Home"), { app.openLibrary(environment: "home") }),
            ("minus", tr("Direnç Bandı", "Band"), { app.openLibrary(equipment: "band") }),
            ("figure.pilates", "Pilates", { app.openLibrary(modality: "pilates") }),
            ("figure.flexibility", tr("Esneklik", "Flexibility"), { app.openLibrary(modality: "mobility") }),
            ("figure.arms.open", tr("Kas Grupları", "Muscle Groups"), { app.push(.regionalPrograms) }),
            ("book.fill", tr("Hareket Kütüphanesi", "Exercise Library"), { app.openLibrary() }),
        ]
        return ScrollView(.horizontal) {
            HStack(spacing: 10) {
                ForEach(items.indices, id: \.self) { i in
                    Button(action: items[i].2) {
                        VStack(spacing: 6) {
                            HfIconBadge(system: items[i].0, tint: HC.lime, size: 34, radius: 11)
                            Text(items[i].1).font(.system(size: 11, weight: .bold)).foregroundStyle(HC.text).multilineTextAlignment(.center).lineLimit(2).frame(height: 28)
                        }.frame(width: 80).padding(.vertical, 10).background(HC.surface, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                    }.buttonStyle(PressableStyle())
                }
            }
        }.scrollIndicators(.hidden)
    }

    private var newBlockCard: some View {
        let weekly = app.prefs.planRotation == "weekly"
        return HfCard(padding: 18) {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 12) {
                    HfIconBadge(system: "arrow.triangle.2.circlepath", tint: HC.lime, size: 44, radius: 14)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(tr("Yeni blok hazır", "A new block is ready")).font(.hfTitleL.weight(.heavy)).foregroundStyle(HC.text)
                        Text(weekly ? tr("Yeni hafta başladı. Antrenmanın sıkıcı olmaması için yardımcı hareketlerini yenile.", "A new week has started. Refresh your accessories to keep training fresh.") : tr("Yeni ay başladı. Antrenmanın sıkıcı olmaması için yardımcı hareketlerini yenile.", "A new month has started. Refresh your accessories to keep training fresh.")).font(.hfBody).foregroundStyle(HC.textSecondary)
                    }
                }
                Text(tr("Ana hareketlerin ilerlemen için aynı kalır.", "Your main lifts stay the same so you keep progressing.")).font(.hfSmall).foregroundStyle(HC.textSecondary)
                HfButton(title: app.planGenerating ? tr("Hazırlanıyor…", "Preparing…") : tr("Yeni bloğu başlat", "Start new block"), icon: "arrow.triangle.2.circlepath", loading: app.planGenerating) { Task { await app.generateAIPlan() } }
            }
        }
    }

    // MARK: Aktif program

    @ViewBuilder private func activeCard(workouts: [WorkoutExercise], active: WorkoutProgram?) -> some View {
        if app.dataLoading && workouts.isEmpty { HfCard { Text(tr("Programın yükleniyor…", "Loading your program…")).foregroundStyle(HC.textSecondary) } }
        else if workouts.isEmpty {
            HfCard(padding: 18) {
                VStack(alignment: .leading, spacing: 12) {
                    HfIconBadge(system: "dumbbell.fill", tint: HC.lime, size: 44, radius: 14)
                    Text(tr("Henüz programın yok", "No program yet")).font(.hfTitleL).foregroundStyle(HC.text)
                    Text(tr("Hedefit AI ile oluştur, hazır bir şablon seç ya da kendin yap.", "Create one with Hedefit AI, pick a ready template or build your own.")).foregroundStyle(HC.textSecondary).font(.hfBody)
                    HfButton(title: tr("Program oluştur", "Create a program"), icon: "plus") { app.push(.programHub) }
                }
            }
        } else {
            HfCard(padding: 18) {
                VStack(alignment: .leading, spacing: 12) {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(tr("AKTİF PROGRAM", "ACTIVE PROGRAM")).font(.hfLabel.weight(.heavy)).foregroundStyle(HC.lime)
                            Text(active.map { localizedProgramName($0.name, source: $0.source) } ?? workouts[0].area).font(.hfHeadline.weight(.heavy)).foregroundStyle(HC.text).lineLimit(2)
                            Text(tr("\(workouts.count) hareket • ~\(workouts.count * 7) dk", "\(workouts.count) exercises • ~\(workouts.count * 7) min")).font(.hfBody).foregroundStyle(HC.textSecondary)
                        }
                        Spacer()
                        if active?.source == "regional" { Button(tr("Bölgeyi değiştir", "Change area")) { app.push(.regionalPrograms) }.font(.hfLabel.weight(.bold)).foregroundStyle(HC.lime) }
                    }
                    HfDivider()
                    ForEach(showAll ? workouts : Array(workouts.prefix(4))) { ex in programRowExercise(ex) }
                    if workouts.count > 4 {
                        Button(showAll ? tr("Daha az göster", "Show less") : tr("+\(workouts.count - 4) hareket daha göster", "Show \(workouts.count - 4) more")) { withAnimation { showAll.toggle() } }.font(.system(size: 14, weight: .bold)).foregroundStyle(HC.textSecondary).frame(maxWidth: .infinity)
                    }
                    HStack(spacing: 8) {
                        HfButton(title: tr("Antrenmanı başlat", "Start workout"), icon: "play.fill") { startWorkout(workouts) }
                        squareButton("bolt.fill", HC.warning, tr("Hazırlık kontrolü", "Readiness check")) { showReadiness = true }
                        squareButton("timer", HC.text, tr("Planı uyarla", "Adapt plan")) { showTimeAdapt = true }
                    }
                }
            }
        }
    }

    private func startWorkout(_ workouts: [WorkoutExercise]) {
        guard !workouts.isEmpty else { return }
        app.workout.start(exercises: workouts, title: app.activeProgram.map { localizedProgramName($0.name, source: $0.source) })
    }

    private func squareButton(_ icon: String, _ tint: Color, _ label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) { Image(systemName: icon).foregroundStyle(tint).frame(width: 52, height: 52).background(HC.surfaceHigh, in: RoundedRectangle(cornerRadius: 16, style: .continuous)) }.buttonStyle(.plain).accessibilityLabel(label)
    }

    private func programRowExercise(_ ex: WorkoutExercise) -> some View {
        HStack(spacing: 10) {
            Button { previewing = ex } label: { ExerciseMedia(id: ex.id).frame(width: 68, height: 68).clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous)) }.buttonStyle(.plain)
            VStack(alignment: .leading, spacing: 2) {
                Text(ex.name).font(.hfTitleM).foregroundStyle(HC.text).lineLimit(1)
                Text("\(ex.reps) \(tr("tekrar", "reps")) • \(ex.restSeconds) \(tr("sn dinlenme", "sec rest"))").font(.hfSmall).foregroundStyle(HC.textSecondary)
                Button(tr("Değiştir", "Replace")) { replacing = ex }.font(.system(size: 12, weight: .semibold)).foregroundStyle(HC.lime)
            }
            Spacer(minLength: 4)
            HStack(spacing: 0) {
                Button { Task { var u = ex; u.sets = max(ex.sets - 1, 1); await app.updateWorkoutExercise(u) } } label: { Image(systemName: "minus").frame(width: 34, height: 34) }.disabled(ex.sets <= 1)
                Text("\(ex.sets)").font(.system(size: 16, weight: .black)).foregroundStyle(HC.lime).frame(minWidth: 18)
                Button { Task { var u = ex; u.sets = min(ex.sets + 1, 10); await app.updateWorkoutExercise(u) } } label: { Image(systemName: "plus").frame(width: 34, height: 34) }.disabled(ex.sets >= 10)
            }.foregroundStyle(HC.text).background(HC.surfaceHigh, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            Button { editing = ex } label: { Image(systemName: "ellipsis").rotationEffect(.degrees(90)).foregroundStyle(HC.textSecondary).frame(width: 30, height: 40) }
        }.padding(.vertical, 6)
    }

    // MARK: Araçlar

    private var tools: some View {
        VStack(spacing: 10) {
            HStack(spacing: 10) {
                HfActionTile(icon: "figure.run", tint: HC.lime, title: tr("Kardiyo", "Cardio"), subtitle: tr("Canlı hız ve eğim", "Live speed & incline")) { app.push(.cardio) }
                HfActionTile(icon: "camera.fill", tint: HC.lime, title: tr("Ekipman tara", "Scan equipment"), subtitle: tr("Kamerayla tanı", "Recognise with camera")) { app.push(.equipmentScanner) }
            }
            HStack(spacing: 10) {
                HfActionTile(icon: "book.fill", tint: HC.lime, title: tr("Hareket Atlası", "Movement Atlas"), subtitle: tr("Teknik ve hareketler", "Technique and exercises")) { app.openLibrary() }
                HfActionTile(icon: "sparkles", tint: HC.lime, title: tr("Program oluştur", "New program"), subtitle: tr("AI, şablon veya kendin", "AI, templates or custom")) { app.push(.programHub) }
            }
            HStack(spacing: 10) {
                HfActionTile(icon: "figure.arms.open", tint: HC.lime, title: tr("Kas Atlası", "Muscle Atlas"), subtitle: tr("Çalışan kasların", "Muscles you trained")) { app.push(.workoutHistory) }
                HfActionTile(icon: "figure.stand", tint: HC.lime, title: tr("Kas Haritası", "Muscle Map"), subtitle: tr("Kasa dokun, hareketleri gör", "Tap a muscle, see exercises")) { app.push(.muscleMap) }
            }
            HStack(spacing: 10) {
                HfActionTile(icon: "location.fill", tint: HC.lime, title: tr("Hedefit Rota", "Hedefit Route"), subtitle: tr("GPS aktivitesi", "GPS activity")) { app.push(.route) }
                HfActionTile(icon: "plus", tint: HC.lime, title: tr("Antrenman ekle", "Log activity"), subtitle: tr("Spor, mesafe, tempo", "Sport, distance, pace")) { app.push(.manualActivity) }
            }
        }
    }

    private func programRow(_ program: WorkoutProgram) -> some View {
        let (icon, tint): (String, Color) = {
            switch program.source { case "regional": return ("figure.arms.open", HC.coral); case "assessment": return ("sparkles", HC.lime); case "push_pull_template": return ("square.grid.2x2.fill", HC.water); default: return ("pencil", HC.warning) }
        }()
        let source: String = {
            switch program.source { case "regional": return tr("Bölgesel", "Body part"); case "assessment": return "Hedefit AI"; case "push_pull_template": return tr("Hazır program", "Template"); default: return tr("Kendi programın", "Custom") }
        }()
        return HfNavRow(icon: icon, tint: tint, title: localizedProgramName(program.name, source: program.source), subtitle: "\(source) • " + tr("\(program.exercises.count) hareket", "\(program.exercises.count) exercises"), chevron: false, action: { Task { await app.activateProgram(program) } }) {
            if program.isActive { HfPill(text: tr("AKTİF", "ACTIVE")) }
            Menu {
                Button(tr("Yeniden adlandır", "Rename"), systemImage: "pencil") { renaming = program; renameText = localizedProgramName(program.name, source: program.source) }
                Button(tr("Kopyala", "Copy"), systemImage: "doc.on.doc") { Task { await app.copyProgram(program) } }
                Button(program.showOnHome ? tr("Ana ekrandan kaldır", "Hide from Home") : tr("Ana ekranda göster", "Show on Home"), systemImage: "house") { Task { await app.setProgramHome(program, !program.showOnHome) } }
                Button(tr("Kaldır", "Remove"), systemImage: "trash", role: .destructive) { removing = program }
            } label: { Image(systemName: "ellipsis").rotationEffect(.degrees(90)).foregroundStyle(HC.muted).frame(width: 32, height: 40) }
        }
    }
}

// MARK: - Hareket düzenleme

struct ExerciseEditorSheet: View {
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss
    let exercise: WorkoutExercise
    @State private var sets = ""
    @State private var reps = ""
    @State private var rest = ""
    @State private var weight = ""
    @State private var submitted = false

    private var index: Int { app.dashboard?.workouts.firstIndex { $0.id == exercise.id } ?? -1 }
    private var count: Int { app.dashboard?.workouts.count ?? 0 }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    HfField(title: tr("Set", "Sets"), text: $sets, keyboard: .numberPad); err(submitted && !(1...10).contains(Int(sets) ?? 0), tr("Set sayısı 1 ile 10 arasında olmalı.", "Sets must be between 1 and 10."))
                    HfField(title: tr("Tekrar", "Reps"), text: $reps); err(submitted && (reps.trimmingCharacters(in: .whitespaces).isEmpty || reps.count > 12), tr("Tekrar hedefini yaz.", "Enter a repetition target."))
                    HfField(title: tr("Dinlenme (saniye)", "Rest (seconds)"), text: $rest, keyboard: .numberPad); err(submitted && !(15...300).contains(Int(rest) ?? 0), tr("Dinlenme 15 ile 300 saniye arasında olmalı.", "Rest must be between 15 and 300 seconds."))
                    HfField(title: tr("Hedef ağırlık (\(Units.weightUnit(app.units)))", "Target weight (\(Units.weightUnit(app.units)))"), text: $weight, keyboard: .decimalPad)
                    HStack(spacing: 10) {
                        HfButton(title: "", icon: "chevron.up", secondary: true, enabled: index > 0) { Task { await app.moveWorkoutExercise(exercise.id, offset: -1); dismiss() } }
                        HfButton(title: "", icon: "chevron.down", secondary: true, enabled: index >= 0 && index < count - 1) { Task { await app.moveWorkoutExercise(exercise.id, offset: 1); dismiss() } }
                        HfButton(title: "", icon: "trash", destructive: true) { Task { await app.removeWorkoutExercise(exercise.id); dismiss() } }
                    }
                    HfButton(title: tr("Kaydet", "Save")) {
                        submitted = true
                        guard let s = Int(sets), (1...10).contains(s), !reps.trimmingCharacters(in: .whitespaces).isEmpty, reps.count <= 12, let r = Int(rest), (15...300).contains(r) else { return }
                        var u = exercise; u.sets = s; u.reps = reps.trimmingCharacters(in: .whitespaces); u.restSeconds = r
                        u.targetWeightKg = Double(weight.replacingOccurrences(of: ",", with: ".")).map { Units.weightToKg($0, app.units) }.flatMap { (0...1000).contains($0) ? $0 : nil }
                        Task { await app.updateWorkoutExercise(u); dismiss() }
                    }
                }.padding(20)
            }.background(HC.bg).navigationTitle(exercise.name).navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button(tr("Vazgeç", "Cancel")) { dismiss() } } }
        }
        .onAppear { sets = "\(exercise.sets)"; reps = exercise.reps; rest = "\(exercise.restSeconds)"; weight = exercise.targetWeightKg.map { String(format: "%.1f", Units.weightValue($0, app.units)) } ?? "" }
        .presentationDetents([.large])
    }

    @ViewBuilder private func err(_ show: Bool, _ text: String) -> some View { if show { Text(text).font(.hfSmall).foregroundStyle(HC.coral) } }
}

struct ExercisePreviewSheet: View {
    @Environment(\.dismiss) private var dismiss
    let exercise: WorkoutExercise
    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 14) {
                ExerciseMotionPlayer(id: exercise.id).frame(height: 260).clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                Text(tr("Başlamadan önce hareket yolunu izle. Hareketi kontrollü uygula; keskin ağrı hissedersen dur.", "Watch the full movement path before starting. Keep the motion controlled and stop if you feel sharp pain.")).font(.hfBody).foregroundStyle(HC.textSecondary)
                Spacer()
            }.padding(20).background(HC.bg).navigationTitle(exercise.name).navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button(tr("Kapat", "Close")) { dismiss() } } }
        }.presentationDetents([.medium, .large])
    }
}

/// Aynı bölgeden katalog alternatifleri (programdaki hareket değiştirme).
struct ProgramReplacementSheet: View {
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss
    let exercise: WorkoutExercise
    @State private var mode = 0
    @State private var items: [ExerciseCatalogItem] = []
    @State private var loading = true
    @State private var reason = "too_hard"
    @State private var busy = false
    @State private var candidate: ExerciseReplacementCandidate?

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    HfSegmented(options: [tr("Akıllı öneri", "Smart swap"), tr("Katalogdan seç", "Pick from catalog")], selection: $mode)
                    if mode == 0 { smart } else { catalog }
                }.padding(20)
            }.background(HC.bg).navigationTitle(exercise.name).navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button(tr("Kapat", "Close")) { dismiss() } } }
        }
        .task { await loadCatalog(); await smartSwap() }
        .presentationDetents([.large])
    }

    private var smart: some View {
        VStack(alignment: .leading, spacing: 12) {
            FlowLayout(spacing: 8) {
                ForEach([("too_hard", tr("Çok zor", "Too hard")), ("too_easy", tr("Çok kolay", "Too easy")), ("no_equipment", tr("Ekipmanım yok", "No equipment")), ("pain_discomfort", tr("Ağrı / rahatsızlık", "Pain")), ("disliked", tr("Sevmiyorum", "Don't like"))], id: \.0) { c, l in
                    HfChip(text: l, selected: reason == c) { reason = c; Task { await smartSwap() } }
                }
            }
            if busy { ProgressView().tint(HC.lime).frame(maxWidth: .infinity).padding(20) }
            else if let c = candidate {
                HfCard(fill: HC.surfaceHigh) {
                    VStack(alignment: .leading, spacing: 10) {
                        Text("\(c.originalExerciseName) → \(c.replacementExerciseName)").font(.hfTitleM.weight(.bold)).foregroundStyle(HC.text)
                        ExerciseMedia(id: c.replacementExerciseId, maxDimension: 512).frame(height: 150).clipShape(RoundedCornerShape(radius: 14))
                        if !c.explanationTr.isEmpty { Text(c.explanationTr).font(.hfBody).foregroundStyle(HC.textSecondary) }
                        HfButton(title: tr("Bu hareketle değiştir", "Swap to this exercise"), icon: "checkmark") {
                            Task { await app.replaceWorkoutExercise(previousId: exercise.id, with: WorkoutExercise(id: c.replacementExerciseId, name: c.replacementExerciseName, area: exercise.area, sets: c.sets, reps: c.reps, restSeconds: c.restSeconds)); dismiss() }
                        }
                    }
                }
            }
        }
    }

    private var catalog: some View {
        VStack(spacing: 8) {
            if loading { ProgressView().tint(HC.lime).padding(20) }
            ForEach(items.filter { $0.id != exercise.id }.prefix(24)) { item in
                Button {
                    Task { await app.replaceWorkoutExercise(previousId: exercise.id, with: WorkoutExercise(id: item.id, name: item.name, area: item.primaryMuscles.first ?? exercise.area, sets: exercise.sets, reps: exercise.reps, restSeconds: exercise.restSeconds)); dismiss() }
                } label: {
                    HStack(spacing: 10) {
                        ExerciseMedia(id: item.id, imageURLs: item.imageUrls).frame(width: 46, height: 46).clipShape(RoundedRectangle(cornerRadius: 12))
                        VStack(alignment: .leading) { Text(item.name).font(.hfBody).foregroundStyle(HC.text).lineLimit(1); Text(item.equipment).font(.hfSmall).foregroundStyle(HC.textSecondary) }
                        Spacer()
                    }
                }.buttonStyle(.plain)
            }
        }
    }

    private func loadCatalog() async {
        loading = true; defer { loading = false }
        items = (try? await app.repo.loadExerciseCatalog(muscle: replacementMuscle(exercise.area), muscleRole: "primary", category: "strength", locale: AppLang.shared.code)) ?? []
    }

    private func smartSwap() async {
        busy = true; defer { busy = false }
        candidate = try? await app.repo.replaceWorkoutExercise(currentExerciseId: exercise.id, reason: reason, exercises: app.dashboard?.workouts ?? [], profile: app.profile, discomfortArea: nil, locale: AppLang.shared.code)
    }
}

/// Program alanı → katalog kas anahtarı.
func replacementMuscle(_ area: String) -> String {
    let v = area.lowercased()
    func has(_ parts: String...) -> Bool { parts.contains { v.contains($0) } }
    if has("göğüs", "chest") { return "chest" }; if has("sırt", "back") { return "back" }; if has("omuz", "shoulder") { return "shoulders" }
    if has("arka kol", "triceps") { return "triceps" }; if has("ön kol", "biceps") { return "biceps" }; if has("karın", "core", "abs") { return "abdominals" }
    if has("kalça", "glute") { return "glutes" }; if has("baldır", "calf", "calves") { return "calves" }; if has("bacak", "leg", "quad", "hamstring") { return "legs" }
    return v
}

// MARK: - Hazırlık & süre uyarlama sheet'leri

struct ReadinessSheet: View {
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss
    @State private var energy = 8.0
    @State private var sleep = 8.0
    @State private var sore: Set<String> = []
    @State private var busy = false
    @State private var result: ReadinessAdaptation?
    private let muscles: [(String, String)] = [("chest", "Göğüs"), ("back", "Sırt"), ("shoulders", "Omuz"), ("legs", "Bacak"), ("glutes", "Kalça"), ("abdominals", "Karın"), ("biceps", "Ön kol"), ("triceps", "Arka kol")]

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    if let r = result {
                        HfCard(fill: HC.surfaceHigh) {
                            VStack(alignment: .leading, spacing: 10) {
                                Text(r.needsAdaptation ? tr("Plan uyarlandı", "Plan adapted") : tr("Hazırsın!", "You're ready!")).font(.hfHeadline.weight(.heavy)).foregroundStyle(HC.text)
                                Text(AppLang.shared.en ? r.explanationEn : r.explanationTr).font(.hfBody).foregroundStyle(HC.textSecondary)
                                if r.volumeReductionPercent > 0 { HfPill(text: "-\(r.volumeReductionPercent)% " + tr("hacim", "volume")) }
                            }
                        }
                        HfButton(title: r.needsAdaptation ? tr("Uyarlanmış planı uygula", "Apply adapted plan") : tr("Tamam", "Done")) {
                            Task { if r.needsAdaptation && !r.adaptedExercises.isEmpty { await app.mutatePlanPublic(tr("Antrenman günlük toparlanma durumuna göre uyarlandı.", "Workout adapted to today's readiness.")) { _ in r.adaptedExercises } }; dismiss() }
                        }
                        HfButton(title: tr("Orijinal planı koru", "Keep original plan"), secondary: true) { dismiss() }
                    } else {
                        Text(tr("Enerji ve uyku durumuna göre bugünkü antrenmanı uyarlayalım.", "Let's adapt today's workout to your energy and sleep.")).font(.hfBody).foregroundStyle(HC.textSecondary)
                        slider(tr("Enerji", "Energy"), $energy)
                        slider(tr("Uyku kalitesi", "Sleep quality"), $sleep)
                        Text(tr("Kas ağrısı olan bölgeler", "Sore areas")).font(.hfTitleM.weight(.bold)).foregroundStyle(HC.text)
                        FlowLayout(spacing: 8) { ForEach(muscles, id: \.0) { k, l in HfChip(text: replacementLabel(k, l), selected: sore.contains(k)) { if sore.contains(k) { sore.remove(k) } else { sore.insert(k) } } } }
                        HfButton(title: tr("Uyarla", "Adapt"), icon: "bolt.fill", loading: busy) { Task { await run() } }
                    }
                }.padding(20)
            }.background(HC.bg).navigationTitle(tr("Hazırlık kontrolü", "Readiness check")).navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button(tr("Kapat", "Close")) { dismiss() } } }
        }.presentationDetents([.large])
    }

    private func replacementLabel(_ key: String, _ tr: String) -> String { localizedArea(tr) }

    private func slider(_ title: String, _ value: Binding<Double>) -> some View {
        HfCard { VStack(spacing: 6) { HStack { Text(title).foregroundStyle(HC.text); Spacer(); Text("\(Int(value.wrappedValue)) / 10").foregroundStyle(HC.lime).fontWeight(.bold) }.font(.hfBody); Slider(value: value, in: 1...10, step: 1).tint(HC.lime) } }
    }

    private func run() async {
        busy = true; defer { busy = false }
        var input = DailyReadinessInput()
        input.energy = Int(energy); input.sleepQuality = Int(sleep); input.fatigue = max(1, 10 - Int(energy)); input.hasSoreness = !sore.isEmpty; input.sorenessAreas = Array(sore)
        do { result = try await app.repo.adaptWorkoutForReadiness(input, exercises: app.dashboard?.workouts ?? [], profile: app.profile, locale: AppLang.shared.code) } catch { app.fail(error) }
    }
}

struct TimeAdaptationSheet: View {
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss
    @State private var result: WorkoutAdaptationResult?
    @State private var busy = false

    private var options: [(String, String, String, Int?, String)] {
        [(tr("15 Dakikalık Hızlı Seans", "15-Minute Express"), tr("Vaktin çok azsa sadece temel bileşik hareketleri tutar", "Short on time, keep compound movements and supersets"), "time_shortage", 15, "timer"),
         (tr("20 Dakikalık Kompakt Seans", "20-Minute Compact"), tr("Kısa sürede ana kas gruplarını çalıştıracak ideal süre", "High efficiency compact volume for main muscles"), "time_shortage", 20, "timer"),
         (tr("30 Dakikalık Dengeli Seans", "30-Minute Balanced"), tr("İzolasyon hareketleri elenir, verim korunur", "Prunes isolation movements while preserving core stimulus"), "time_shortage", 30, "timer"),
         (tr("Seyahat / Sadece Vücut Ağırlığı", "Travel / Bodyweight Only"), tr("Ekipman yoksa tüm hareketleri vücut ağırlığı varyasyonlarına çevirir", "No gym or equipment, converts to bodyweight variations"), "travel", nil, "airplane"),
         (tr("Yorgunluk Deload'u", "Fatigue Deload"), tr("Düşük enerjide setleri azaltıp dinlenmeyi uzatır", "Low energy: reduces volume by 40% and extends rest"), "acute_fatigue", nil, "battery.25")]
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if busy { ProgressView(tr("Antrenman uyarlanıyor...", "Adapting your workout...")).tint(HC.lime).frame(maxWidth: .infinity).padding(40) }
                    else if let r = result {
                        HfCard(fill: HC.surfaceHigh) {
                            VStack(alignment: .leading, spacing: 10) {
                                Text(tr("Uyarlama hazır", "Adaptation ready")).font(.hfTitleL.weight(.bold)).foregroundStyle(HC.text)
                                Text("\(r.originalDurationMinutes) dk → \(r.adaptedDurationMinutes) dk").font(.hfBody.weight(.semibold)).foregroundStyle(HC.lime)
                                Text(r.explanationTr).font(.hfBody).foregroundStyle(HC.text)
                                if !r.changes.isEmpty { Text(tr("Yapılan değişiklikler:", "Changes:")).font(.hfLabel.weight(.bold)).foregroundStyle(HC.textSecondary); ForEach(r.changes, id: \.self) { Text("• \($0)").font(.hfBody).foregroundStyle(HC.textSecondary) } }
                            }
                        }
                        HfButton(title: tr("Uygula", "Apply")) { Task { await app.applyPlanAdaptation(r); dismiss() } }
                        HfButton(title: tr("Vazgeç", "Cancel"), secondary: true) { result = nil }
                    } else {
                        Text(tr("Mevcut durumuna uygun seçeneği belirle:", "Choose your current situation:")).font(.hfBody).foregroundStyle(HC.textSecondary)
                        ForEach(options.indices, id: \.self) { i in
                            let o = options[i]
                            HfNavRow(icon: o.4, tint: HC.lime, title: o.0, subtitle: o.1) { Task { busy = true; result = await app.requestPlanAdaptation(trigger: o.2, targetMinutes: o.3); busy = false } }
                        }
                    }
                }.padding(20)
            }.background(HC.bg).navigationTitle(tr("Antrenman planını uyarla", "Adapt workout plan")).navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button(tr("Kapat", "Close")) { dismiss() } } }
        }.presentationDetents([.large])
    }
}
