import SwiftUI

private let fullBodyKey = "full_body"
private let fullBodyRegions = ["legs", "chest", "back", "shoulders", "glutes", "abdominals"]
private let levelOrder = ["beginner", "intermediate", "advanced"]
private let foundationLift = try! NSRegularExpression(pattern: "(^|-)(squat|leg-press|deadlift|romanian|lunge|split-squat|hip-thrust|bench-press|push-up|pull-up|chin-up|lat-pulldown|row|shoulder-press|ohp|overhead-press|dips?)(-|$)")

private func regionLabel(_ key: String, en: Bool) -> String {
    switch key { case "legs": return en ? "Legs" : "Bacak"; case "chest": return en ? "Chest" : "Göğüs"; case "back": return en ? "Back" : "Sırt"
    case "shoulders": return en ? "Shoulders" : "Omuz"; case "glutes": return en ? "Glutes" : "Kalça"; default: return en ? "Core" : "Karın" }
}

/// Bir hareketin varyasyonlarını ("push-up", "knee-push-ups") aynı aileye toplar.
private func movementFamily(_ id: String) -> String {
    var key = id.lowercased(); if key.hasSuffix("s") { key.removeLast() }
    let families = ["push-up", "pull-up", "chin-up", "squat", "lunge", "row", "deadlift", "rdl", "bench-press", "floor-press", "leg-press", "shoulder-press", "overhead-press", "push-press", "press",
                    "curl", "lateral-raise", "front-raise", "calf-raise", "raise", "fly", "dip", "plank", "crunch", "bridge", "thrust", "extension", "pulldown", "kickback"]
    return families.first { key.contains($0) } ?? key
}

@MainActor func readyProgramTitle(_ key: String) -> String {
    switch key {
    case "push_a": return tr("Göğüs & Arka Kol Güç Günü", "Chest & Triceps Power Day")
    case "push_b": return tr("Omuz & Göğüs Şekillendirme", "Shoulder & Chest Sculpt")
    case "pull_a": return tr("Geniş Sırt & Ön Kol", "Wide Back & Biceps")
    case "pull_b": return tr("Kalın Sırt & Arka Omuz", "Thick Back & Rear Delts")
    case "leg_a": return tr("Ön Bacak Güç Günü", "Quad Power Day")
    case "leg_b": return tr("Kalça & Arka Bacak", "Glutes & Hamstrings")
    case "full_a": return tr("Tüm Vücut Temel Kuvvet", "Full Body Strength Basics")
    case "full_b": return tr("Tüm Vücut Deadlift Günü", "Full Body Deadlift Day")
    default: return key
    }
}

let readyProgramKeys = ["push_a", "push_b", "pull_a", "pull_b", "leg_a", "leg_b", "full_a", "full_b"]

extension AppModel {
    // MARK: Yetki kontrolü

    /// Katman kontrolü. İzin yoksa uygun teklifi açar ve false döner.
    func require(_ feature: LockedFeature, allowed: (TierLimits) -> Bool) -> Bool {
        if allowed(limits) { return true }
        if isGuest { showSaveAccount = true }
        lockedFeature = feature
        return false
    }

    func canAddMeals(_ count: Int = 1) -> Bool {
        require(.mealLogs) { (dashboard?.todayMealLogCount() ?? 0) + count <= $0.dailyMealLogs }
    }
    func canCreateCustomProgram() -> Bool {
        require(.customProgram) { (dashboard?.workoutPrograms.filter { $0.source == "custom" }.count ?? 0) < $0.customPrograms }
    }

    // MARK: Plan değişiklikleri

    private func mutatePlan(_ message: String, _ transform: ([WorkoutExercise]) -> [WorkoutExercise]) async {
        guard let d = dashboard else { return }
        let next = transform(d.workouts)
        dashboard?.workouts = next
        do {
            if let active = d.workoutPrograms.first(where: { $0.isActive }) {
                let saved = try await repo.saveProgram(name: active.name, source: active.source, focusArea: active.focusArea, workouts: next, id: active.id, showOnHome: active.showOnHome, trainingDays: active.trainingDays)
                updateDashboard { $0.workoutPrograms = self.withActive($0.workoutPrograms, saved) }
            } else { try await repo.saveWorkoutPlan(next) }
            notify(message)
            WidgetBridge.update(self)
        } catch { report(error) }
    }

    func updateWorkoutExercise(_ updated: WorkoutExercise) async { await mutatePlan(tr("Hareket güncellendi.", "Exercise updated.")) { $0.map { $0.id == updated.id ? updated : $0 } } }
    func replaceWorkoutExercise(previousId: String, with replacement: WorkoutExercise) async { await mutatePlan(tr("Hareket değiştirildi.", "Exercise replaced.")) { $0.map { $0.id == previousId ? replacement : $0 } } }
    func removeWorkoutExercise(_ id: String) async { await mutatePlan(tr("Hareket programdan çıkarıldı.", "Exercise removed from the program.")) { $0.filter { $0.id != id } } }
    func moveWorkoutExercise(_ id: String, offset: Int) async {
        await mutatePlan(tr("Program sırası güncellendi.", "Program order updated.")) { list in
            guard let from = list.firstIndex(where: { $0.id == id }) else { return list }
            let to = min(max(from + offset, 0), list.count - 1)
            if from == to { return list }
            var copy = list; let item = copy.remove(at: from); copy.insert(item, at: to); return copy
        }
    }

    func useExerciseFromLibrary(_ item: ExerciseCatalogItem) async {
        guard require(.exercise, allowed: { $0.canUseExercise(item) }), let d = dashboard else { return }
        var current = d.workouts
        let replacement = WorkoutExercise(id: item.id, name: item.name, area: item.primaryMuscles.first ?? "Tüm Vücut", sets: 3, reps: "8–12", restSeconds: 75)
        if !current.contains(where: { $0.id == replacement.id || $0.name.caseInsensitiveCompare(replacement.name) == .orderedSame }) { current.append(replacement) }
        dashboard?.workouts = current
        do {
            let program: WorkoutProgram
            if let active = d.workoutPrograms.first(where: { $0.isActive }) { program = try await repo.saveProgram(name: active.name, source: active.source, focusArea: active.focusArea, workouts: current, id: active.id, showOnHome: active.showOnHome, trainingDays: active.trainingDays) }
            else { program = try await repo.saveProgram(name: "Kendi Programım", source: "custom", focusArea: replacement.area, workouts: current) }
            dashboard?.workouts = current; updateDashboard { $0.workoutPrograms = self.withActive($0.workoutPrograms, program) }
            notify(tr("Hareket programa eklendi.", "Exercise added to the program."))
        } catch { report(error) }
    }

    func createProgram(name: String, from items: [ExerciseCatalogItem]) async {
        guard canCreateCustomProgram(), !planGenerating, !items.isEmpty else { return }
        planGenerating = true; defer { planGenerating = false }
        let plan = items.map { WorkoutExercise(id: $0.id, name: $0.name, area: $0.primaryMuscles.first ?? "Tüm Vücut", sets: 3, reps: "8–12", restSeconds: 75) }
        let counts = Dictionary(items.flatMap(\.primaryMuscles).map { ($0, 1) }, uniquingKeysWith: +)
        let focus = counts.max { $0.value < $1.value }?.key ?? ""
        do {
            let program = try await repo.saveProgram(name: name.isEmpty ? tr("Programım", "My Program") : name, source: "custom", focusArea: focus, workouts: plan)
            dashboard?.workouts = program.exercises; updateDashboard { $0.workoutPrograms = self.withActive($0.workoutPrograms, program) }
            notify(tr("\(program.name) oluşturuldu ve aktif edildi.", "\(program.name) was created and activated."))
        } catch { report(error) }
    }

    func createCustomProgram(name: String, trainingDays: [WorkoutProgramDay]) async -> Bool {
        guard canCreateCustomProgram() else { return false }
        do {
            let program = try await repo.saveProgram(name: name.isEmpty ? tr("Programım", "My Program") : name, source: "custom", focusArea: "", workouts: [], trainingDays: trainingDays)
            dashboard?.workouts = []; updateDashboard { $0.workoutPrograms = self.withActive($0.workoutPrograms, program) }
            notify(tr("Kendi programın oluşturuldu.", "Custom program created.")); return true
        } catch { report(error); return false }
    }

    func copyProgram(_ program: WorkoutProgram) async {
        guard canCreateCustomProgram() else { return }
        do {
            let copy = try await repo.saveProgram(name: "\(program.name) Kopyası", source: "custom", focusArea: program.focusArea, workouts: program.exercises, trainingDays: program.trainingDays)
            dashboard?.workouts = copy.exercises; updateDashboard { $0.workoutPrograms = self.withActive($0.workoutPrograms, copy) }
            notify(tr("Program kopyalandı ve aktif edildi.", "Program copied and activated."))
        } catch { report(error) }
    }

    func activateProgram(_ program: WorkoutProgram) async {
        do {
            let active = try await repo.activateProgram(program)
            dashboard?.workouts = active.exercises; updateDashboard { $0.workoutPrograms = self.withActive($0.workoutPrograms, active) }
            notify(tr("\(active.name) aktif program oldu.", "\(active.name) is now your active program."))
            WidgetBridge.update(self)
        } catch { report(error) }
    }

    func renameProgram(_ program: WorkoutProgram, to newName: String) async {
        let name = String(newName.trimmingCharacters(in: .whitespaces).prefix(60))
        guard !name.isEmpty, name != program.name else { return }
        do {
            let updated = try await repo.renameProgram(program, name: name)
            updateDashboard { $0.workoutPrograms = $0.workoutPrograms.map { $0.id == updated.id ? updated : $0 } }
            notify(tr("Program adı güncellendi.", "Program renamed."))
        } catch { report(error) }
    }

    func deleteProgram(_ program: WorkoutProgram) async {
        do {
            let active = try await repo.deleteProgram(program)
            guard var d = dashboard else { return }
            let remaining = d.workoutPrograms.filter { $0.id != program.id }
            if program.isActive { d.workouts = active?.exercises ?? [] }
            d.workoutPrograms = active.map { withActive(remaining, $0) } ?? remaining
            dashboard = d
            notify(tr("Program kaldırıldı.", "Program removed."))
        } catch { report(error) }
    }

    // MARK: Hazır programlar

    func readyProgram(_ key: String, names: [String: String] = [:]) -> (String, [WorkoutExercise])? {
        let en = AppLang.shared.en
        func ex(_ id: String, _ fallback: String, _ area: String, _ sets: Int, _ reps: String, _ rest: Int) -> WorkoutExercise { WorkoutExercise(id: id, name: names[id] ?? fallback, area: area, sets: sets, reps: reps, restSeconds: rest) }
        func a(_ tr: String, _ e: String) -> String { en ? e : tr }
        let templates: [String: [WorkoutExercise]] = [
            "push_a": [ex("bench-press", "Barbell Bench Press", a("Göğüs", "Chest"), 4, "6–8", 120), ex("incline-db-press", "Incline Dumbbell Press", a("Üst göğüs", "Upper chest"), 3, "8–12", 90), ex("dumbbell-shoulder-press", "Dumbbell Shoulder Press", a("Omuz", "Shoulders"), 3, "8–12", 90), ex("seated-dumbbell-lateral-raise", "Dumbbell Lateral Raise", a("Omuz", "Shoulders"), 3, "12–15", 60), ex("tricep-pushdown", "Cable Triceps Pushdown", a("Arka kol", "Triceps"), 3, "10–15", 60)],
            "push_b": [ex("seated-barbell-overhead-press", "Barbell Overhead Press", a("Omuz", "Shoulders"), 4, "6–8", 120), ex("db-bench-press", "Dumbbell Bench Press", a("Göğüs", "Chest"), 3, "8–12", 90), ex("cable-fly", "Cable Fly", a("Göğüs", "Chest"), 3, "12–15", 60), ex("seated-dumbbell-lateral-raise", "Dumbbell Lateral Raise", a("Omuz", "Shoulders"), 4, "12–20", 60), ex("overhead-tricep-extension", "Overhead Triceps Extension", a("Arka kol", "Triceps"), 3, "10–15", 60)],
            "pull_a": [ex("pull-up", "Pull-up", a("Sırt", "Back"), 4, "6–10", 120), ex("v-bar-lat-pulldown", "Lat Pulldown", a("Sırt", "Back"), 3, "8–12", 90), ex("seated-cable-row", "Seated Cable Row", a("Sırt", "Back"), 3, "8–12", 90), ex("face-pull", "Face Pull", a("Arka omuz", "Rear delts"), 3, "12–15", 60), ex("seated-dumbbell-curl", "Seated Dumbbell Curl", a("Ön kol", "Biceps"), 3, "10–15", 60)],
            "pull_b": [ex("barbell-row", "Barbell Row", a("Sırt", "Back"), 4, "6–8", 120), ex("close-grip-lat-pulldown", "Close-Grip Lat Pulldown", a("Sırt", "Back"), 3, "8–12", 90), ex("chest-supported-db-row", "Chest-Supported Dumbbell Row", a("Sırt", "Back"), 3, "8–12", 90), ex("rear-delt-fly", "Rear Delt Fly", a("Arka omuz", "Rear delts"), 3, "12–15", 60), ex("hammer-curl", "Hammer Curl", a("Ön kol", "Biceps"), 3, "10–15", 60)],
            "leg_a": [ex("squat", "Barbell Back Squat", a("Ön bacak", "Quads"), 4, "6–8", 150), ex("bulgarian-split-squat", "Bulgarian Split Squat", a("Ön bacak & kalça", "Quads & glutes"), 3, "8–12", 90), ex("close-stance-leg-press", "Leg Press", a("Ön bacak", "Quads"), 3, "10–15", 90), ex("leg-extension", "Leg Extension", a("Ön bacak", "Quads"), 3, "12–15", 60), ex("standing-calf-raise", "Standing Calf Raise", a("Baldır", "Calves"), 4, "12–15", 45)],
            "leg_b": [ex("romanian-deadlift", "Romanian Deadlift", a("Arka bacak & kalça", "Hamstrings & glutes"), 4, "8–10", 120), ex("hip-thrust", "Barbell Hip Thrust", a("Kalça", "Glutes"), 4, "8–12", 90), ex("seated-leg-curl", "Seated Leg Curl", a("Arka bacak", "Hamstrings"), 3, "10–15", 60), ex("goblet-squat", "Goblet Squat", a("Ön bacak & kalça", "Quads & glutes"), 3, "10–12", 90), ex("seated-calf-raise", "Seated Calf Raise", a("Baldır", "Calves"), 4, "12–15", 45)],
            "full_a": [ex("squat", "Barbell Back Squat", a("Bacak", "Legs"), 3, "8–10", 120), ex("bench-press", "Barbell Bench Press", a("Göğüs", "Chest"), 3, "8–10", 120), ex("barbell-row", "Barbell Row", a("Sırt", "Back"), 3, "8–10", 90), ex("dumbbell-shoulder-press", "Dumbbell Shoulder Press", a("Omuz", "Shoulders"), 3, "10–12", 90), ex("plank", "Plank", a("Karın", "Core"), 3, "30–45 sn", 45)],
            "full_b": [ex("deadlift", "Barbell Deadlift", a("Tüm vücut", "Full body"), 3, "6–8", 150), ex("db-bench-press", "Dumbbell Bench Press", a("Göğüs", "Chest"), 3, "10–12", 90), ex("pull-up", "Pull-up", a("Sırt", "Back"), 3, "6–10", 120), ex("goblet-squat", "Goblet Squat", a("Ön bacak & kalça", "Quads & glutes"), 3, "10–12", 90), ex("hanging-leg-raise", "Hanging Leg Raise", a("Karın", "Core"), 3, "10–15", 60)],
        ]
        return templates[key].map { (readyProgramTitle(key), $0) }
    }

    func addReadyProgram(_ key: String) async {
        guard !planGenerating else { return }
        planGenerating = true; defer { planGenerating = false }
        let names = Dictionary(((try? await repo.loadExerciseCatalog(locale: AppLang.shared.code)) ?? []).map { ($0.id, $0.name) }, uniquingKeysWith: { a, _ in a })
        guard let (title, exercises) = readyProgram(key, names: names) else { return }
        do {
            let program = try await repo.saveProgram(name: title, source: "custom", focusArea: title, workouts: exercises)
            dashboard?.workouts = program.exercises; updateDashboard { $0.workoutPrograms = self.withActive($0.workoutPrograms, program) }
            notify(tr("\(program.name) eklendi ve aktif edildi.", "\(program.name) was added and activated."))
        } catch { report(error) }
    }

    // MARK: Bölgesel / hızlı program

    private func buildRegional(muscle: String, label: String) async throws -> WorkoutProgram {
        let locale = AppLang.shared.code
        let items = try await repo.loadExerciseCatalog(muscle: muscle, muscleRole: "primary", category: "strength", locale: locale)
            .sorted { ($0.mechanic == "compound" ? 0 : 1, $0.name) < ($1.mechanic == "compound" ? 0 : 1, $1.name) }.prefix(5)
        let plan = items.enumerated().map { WorkoutExercise(id: $1.id, name: $1.name, area: label, sets: $0 < 2 ? 4 : 3, reps: "8–12", restSeconds: 75) }
        guard !plan.isEmpty else { throw AppError.message(tr("Bu bölge için hareket bulunamadı.", "No exercises found for this area.")) }
        return try await repo.saveProgram(name: AppLang.shared.en ? "\(label) Program" : "\(label) Programı", source: "regional", focusArea: label, workouts: plan)
    }

    func generateRegionalPlan(muscle: String, label: String, connected: (String, String)? = nil) async {
        guard require(.regionalPlan, allowed: { $0.regionalPlans }), !planGenerating else { return }
        planGenerating = true; defer { planGenerating = false }
        do {
            let primary = try await buildRegional(muscle: muscle, label: label)
            let secondary: WorkoutProgram? = try? await connected.asyncMap { try await buildRegional(muscle: $0.0, label: $0.1) }
            var list = withActive(dashboard?.workoutPrograms ?? [], primary)
            if let secondary { list = withActive(list, secondary) }
            dashboard?.workouts = (secondary ?? primary).exercises; dashboard?.workoutPrograms = list
            let en = AppLang.shared.en
            if let secondary { notify(en ? "\(primary.name) and \(secondary.name) are ready." : "\(primary.name) ve \(secondary.name) hazır.") }
            else if connected != nil { notify(en ? "\(primary.name) is ready; the connected area had no matching exercises." : "\(primary.name) hazır; bağlı bölge için uygun hareket bulunamadı.") }
            else { notify(en ? "\(label) plan is ready." : "\(label) odaklı programın hazır.") }
        } catch { report(error) }
    }

    /// Zaman, enerji, yer, ekipman ve seviyeye göre tek seferlik kaydedilebilir seans üretir.
    func generateQuickWorkout(regions: [(String, String)], durationMinutes: Int, fatigue: String, environment: String, owned: [String], level: String) async {
        guard !planGenerating, !regions.isEmpty else { return }
        planGenerating = true; defer { planGenerating = false }
        let en = AppLang.shared.en, locale = AppLang.shared.code
        do {
            let total = durationMinutes <= 20 ? 3 : (durationMinutes <= 30 ? 4 : (durationMinutes <= 45 ? 6 : 8))
            let (sets, reps, rest): (Int, String, Int) = fatigue == "yorgun" ? (2, "12–15", 45) : (fatigue == "dinc" ? (4, "6–10", 90) : (3, "8–12", 60))
            let fullBody = regions.contains { $0.0 == fullBodyKey }
            let targets = fullBody ? fullBodyRegions.map { ($0, regionLabel($0, en: en)) } : regions
            let ownedFilter = environment == "gym" ? ["gym"] : (owned.isEmpty ? ["none"] : owned)
            let maxLevel = max(levelOrder.firstIndex(of: level) ?? 0, 0)
            var perRegion: [String: [ExerciseCatalogItem]] = [:]
            for (muscle, _) in targets {
                let items = try await repo.loadExerciseCatalog(muscle: muscle, muscleRole: "primary", category: "strength", locale: locale, owned: ownedFilter)
                    .filter { let l = levelOrder.firstIndex(of: $0.levelKey) ?? -1; return l < 0 || l <= maxLevel }
                perRegion[muscle] = items.sorted { a, b in
                    func key(_ i: ExerciseCatalogItem) -> [Int] {
                        let ownedMiss = (environment != "gym" && !owned.isEmpty && !i.requiredEquipment.contains { !$0.isEmpty && $0.allSatisfy(owned.contains) }) ? 1 : 0
                        let foundation = foundationLift.firstMatch(in: i.id, range: NSRange(i.id.startIndex..., in: i.id)) != nil ? 0 : 1
                        return [ownedMiss, i.mechanic == "compound" ? 0 : 1, foundation, abs((levelOrder.firstIndex(of: i.levelKey) ?? 0) - maxLevel), abs(i.id.hashValue % 1000)]
                    }
                    return key(a).lexicographicallyPrecedes(key(b))
                }
            }
            var chosen: [WorkoutExercise] = [], families = Set<String>(), round = 0
            while chosen.count < total && round < 6 {
                for (muscle, label) in targets where chosen.count < total {
                    let pool = (perRegion[muscle] ?? []).filter { item in !chosen.contains { $0.id == item.id } }
                    if let pick = pool.first(where: { !families.contains(movementFamily($0.id)) }) ?? pool.first {
                        families.insert(movementFamily(pick.id))
                        chosen.append(WorkoutExercise(id: pick.id, name: pick.name, area: label, sets: sets, reps: reps, restSeconds: rest))
                    }
                }
                round += 1
            }
            guard !chosen.isEmpty else { throw AppError.message(tr("Seçilen bölgeler için bu ekipman ve seviyeye uygun hareket bulunamadı.", "No exercises match this equipment and level for the selected areas.")) }
            let names = fullBody ? (en ? "Full body" : "Tüm vücut") : regions.map(\.1).joined(separator: " & ")
            let program = try await repo.saveProgram(name: en ? "Quick Workout: \(names)" : "Hızlı Antrenman: \(names)", source: "custom", focusArea: names, workouts: chosen)
            dashboard?.workouts = program.exercises; updateDashboard { $0.workoutPrograms = self.withActive($0.workoutPrograms, program) }
            notify(en ? "\(program.name) is ready." : "\(program.name) hazır.")
        } catch { report(error) }
    }

    /// AI planı üretir ve "assessment" programına yazar.
    func generateAIPlan() async {
        guard !planGenerating, let profile = dashboard?.profile else { return }
        planGenerating = true; defer { planGenerating = false }
        do {
            let workouts = try await repo.generatePlan(profile: profile, rotationPeriod: prefs.planRotation)
            let existing = dashboard?.workoutPrograms.first { $0.source == "assessment" }
            let program = try await repo.saveProgram(name: "Kişisel Atlas Programım", source: "assessment", focusArea: profile.goal, workouts: workouts, id: existing?.id ?? UUID().uuidString.lowercased(), showOnHome: true)
            PlanRotationStore.markGenerated(await AuthService.shared.userId)
            dashboard?.workouts = program.exercises; updateDashboard { $0.workoutPrograms = self.withActive($0.workoutPrograms, program) }
            notify(tr("Fit Koç yeni programını hazırladı.", "Fit Coach prepared your new program."))
            WidgetBridge.update(self)
        } catch { report(error) }
    }

    func newBlockDue(_ period: PlanRotationPeriod) -> Bool {
        guard let active = activeProgram, active.source == "assessment" else { return false }
        let generatedOn = PlanRotationStore.generatedOn(session?.user.id) ?? active.updatedAt.flatMap(ISO.date)
        return PlanRotation.isDue(period, generatedOn: generatedOn, today: Date())
    }

    // MARK: Uyarlama (hazırlık / süre kısaltma)

    func requestPlanAdaptation(trigger: String, targetMinutes: Int?, exercises: [WorkoutExercise]? = nil) async -> WorkoutAdaptationResult? {
        coach.planAdaptationBusy = true; defer { coach.planAdaptationBusy = false }
        do { return try await repo.adaptWorkoutPlan(trigger: trigger, targetMinutes: targetMinutes, exercises: exercises ?? dashboard?.workouts ?? [], profile: dashboard?.profile, locale: AppLang.shared.code) } catch { report(error); return nil }
    }

    func applyPlanAdaptation(_ result: WorkoutAdaptationResult) async {
        guard !result.adaptedExercises.isEmpty else { return }
        await mutatePlanPublic(result.explanationTr.isEmpty ? tr("Antrenman başarıyla uyarlandı.", "Workout adapted.") : result.explanationTr) { _ in result.adaptedExercises }
    }

    func mutatePlanPublic(_ message: String, _ transform: @escaping ([WorkoutExercise]) -> [WorkoutExercise]) async { await mutatePlan(message, transform) }

    /// Koç yanıtındaki yapılandırılmış eylemleri uygular; ekran geçişi gerekenler `handled` metniyle döner.
    func executeCoachAction(_ action: CoachAction) async {
        let current = dashboard?.workouts ?? []
        switch action.type {
        case "replace_exercise":
            let origId = (action.exerciseId ?? "").trimmingCharacters(in: .whitespaces), repId = (action.replacementId ?? "").trimmingCharacters(in: .whitespaces)
            let repName = (action.replacementName ?? repId).trimmingCharacters(in: .whitespaces)
            guard !repId.isEmpty else { return }
            await mutatePlan(tr("Hareket '\(repName)' ile güncellendi.", "Exercise updated to '\(repName)'.")) { list in
                var replaced = false
                var updated = list.map { item -> WorkoutExercise in
                    let matches = !replaced && ((!origId.isEmpty && (item.id.caseInsensitiveCompare(origId) == .orderedSame || item.name.localizedCaseInsensitiveContains(origId))) || (origId.isEmpty && action.reason != nil && item.name.localizedCaseInsensitiveContains(action.reason!)))
                    guard matches else { return item }
                    replaced = true
                    return WorkoutExercise(id: repId, name: repName, area: item.area, sets: action.sets ?? item.sets, reps: action.reps ?? item.reps, restSeconds: action.restSeconds ?? item.restSeconds, targetWeightKg: item.targetWeightKg)
                }
                if !replaced, !updated.isEmpty { let f = updated[0]; updated[0] = WorkoutExercise(id: repId, name: repName, area: f.area, sets: action.sets ?? f.sets, reps: action.reps ?? f.reps, restSeconds: action.restSeconds ?? f.restSeconds) }
                return updated
            }
        case "reduce_intensity":
            let percent = action.percent ?? 25
            await mutatePlan(tr("Yoğunluk %\(percent) düşürüldü ve dinlenme uzatıldı.", "Intensity reduced by \(percent)% and rest extended.")) { list in
                list.map { var c = $0; c.sets = max(c.sets * (100 - percent) / 100, 1); c.restSeconds += 20; return c }
            }
        case "shorten_workout":
            let target = action.targetMinutes ?? 20
            if let result = await requestPlanAdaptation(trigger: "time_shortage", targetMinutes: target) { await applyPlanAdaptation(result) }
        case "start_recovery_check": notify(tr("Hazırlık ve toparlanma kontrolü için Bugün sekmesindeki check-in'i aç.", "Open the check-in on the Today tab for a readiness check."))
        case "modify_sets": let id = action.exerciseId ?? "", sets = action.sets ?? 3
            await mutatePlan(tr("Set sayısı \(sets) olarak güncellendi.", "Sets updated to \(sets).")) { $0.map { var c = $0; if c.id == id { c.sets = sets }; return c } }
        case "modify_reps": let id = action.exerciseId ?? "", reps = action.reps ?? "10"
            await mutatePlan(tr("Tekrar sayısı \(reps) olarak güncellendi.", "Reps updated to \(reps).")) { $0.map { var c = $0; if c.id == id { c.reps = reps }; return c } }
        case "modify_rest_time": let id = action.exerciseId ?? "", rest = action.restSeconds ?? 60
            await mutatePlan(tr("Dinlenme süresi \(rest)s olarak güncellendi.", "Rest updated to \(rest)s.")) { $0.map { var c = $0; if c.id == id { c.restSeconds = rest }; return c } }
        case "openWorkout": select(.explore)
        case "createWorkout": select(.explore); push(.programs)
        case "startOutdoor": push(.route)
        case "suggestMeal": select(.nutrition)
        case "remind": push(.notifications)
        case "changeGoal": push(.goalJourney)
        default: _ = current; notify(tr("Bu öneriyi uygulamak için ilgili sekmeyi aç.", "Open the matching tab to apply this suggestion."))
        }
    }
}

extension Optional {
    func asyncMap<T>(_ transform: (Wrapped) async throws -> T) async rethrows -> T? {
        guard let value = self else { return nil }
        return try await transform(value)
    }
}
