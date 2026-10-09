import SwiftUI
import Observation
import AVFoundation
import UIKit

struct WorkoutSummaryData: Identifiable {
    let id = UUID()
    var title: String
    var durationSeconds: Int
    var calories: Int
    var sets: [WorkoutSetInput]
    var personalRecords: [PersonalRecordResult]
    var exerciseAreas: [String: String] = [:]

    var exerciseCount: Int { Set(sets.map(\.exerciseId)).count }
    var setCount: Int { sets.count }
    var repetitions: Int { sets.reduce(0) { $0 + ($1.reps ?? 0) } }
    var volumeKg: Double { sets.reduce(0) { $0 + setVolume(weightKg: $1.weightKg, reps: $1.reps) } }
    var strongestExercise: String? {
        Dictionary(grouping: sets, by: \.exerciseName).max { a, b in a.value.reduce(0) { $0 + setVolume(weightKg: $1.weightKg, reps: $1.reps) } < b.value.reduce(0) { $0 + setVolume(weightKg: $1.weightKg, reps: $1.reps) } }?.key
    }
}

/// Aktif antrenman motoru (Android `DetailedActiveWorkoutScreen` durumu + `ActiveWorkoutStore`).
@MainActor @Observable
final class WorkoutModel {
    var isPresented = false
    var exercises: [WorkoutExercise] = []
    var title: String?
    var prepared = false
    var paused = false
    var weight = 0.0 // kg
    var weightTouched = false
    var reps = 10
    var rpe = 7
    var setType = "normal"
    var note = ""
    var exerciseIndex = 0
    var currentSet = 1
    var completedSets: [WorkoutSetInput] = []
    var restDeadline: Date?
    var restSeconds = 0
    var restPaused = false
    var stopwatchSeconds = 0
    var stopwatchRunning = false
    var startedAt = Date()
    var elapsed = 0
    var personalRecord = false
    var previous: [String: [PreviousSet]] = [:]
    var showFeedback = false
    var showFinishConfirm = false
    var showNoSets = false
    var showReplacement = false
    var showCoach = false
    var summary: WorkoutSummaryData?
    var replacementBusy = false
    var replacementCandidate: ExerciseReplacementCandidate?
    var saving: Bool { AppModel.shared.workoutSaving }

    @ObservationIgnored private let synth = AVSpeechSynthesizer()
    @ObservationIgnored private var pushTask: Task<Void, Never>?
    private var app: AppModel { AppModel.shared }

    static let maxActiveMillis: Double = 24 * 3600

    // MARK: Hesaplananlar

    var exercise: WorkoutExercise? { exercises.indices.contains(exerciseIndex) ? exercises[exerciseIndex] : nil }
    var totalSets: Int { max(exercise?.sets ?? 1, 1) }
    var isLastSet: Bool { currentSet >= totalSets && exerciseIndex >= exercises.count - 1 }
    var hasRecoverable: Bool { readSnapshot() != nil }
    func previousSets(for id: String?) -> [PreviousSet] { id.flatMap { previous[$0] } ?? [] }

    // MARK: Başlatma

    /// Verilen hareketlerle yeni antrenman başlatır.
    func start(exercises list: [WorkoutExercise], title: String? = nil) {
        guard !list.isEmpty else { app.notify(tr("Önce bir antrenman programı oluştur.", "Create a workout program first.")); return }
        exercises = list; self.title = title
        prepared = false; paused = false; exerciseIndex = 0; currentSet = 1; completedSets = []; restDeadline = nil; restSeconds = 0; restPaused = false
        stopwatchSeconds = 0; stopwatchRunning = false; startedAt = Date(); elapsed = 0; note = ""; setType = "normal"; rpe = 7; personalRecord = false; weightTouched = false
        reps = Self.prescribedStartingReps(list.first?.reps)
        weight = 0
        isPresented = true
        UIApplication.shared.isIdleTimerDisabled = true
        Task { await loadPrevious(); persist() }
        app.markTried("workout")
    }

    /// Kaldığı yerden devam (uygulama kapanmışsa).
    func resumeRecoverable() {
        guard let snap = readSnapshot() else { return }
        apply(snap)
        isPresented = true
        UIApplication.shared.isIdleTimerDisabled = true
        Task { await loadPrevious() }
    }

    func minimize() { isPresented = false; UIApplication.shared.isIdleTimerDisabled = false }
    func reopen() { isPresented = true; UIApplication.shared.isIdleTimerDisabled = true }
    var isActive: Bool { !exercises.isEmpty && readSnapshot() != nil }

    private func loadPrevious() async {
        previous = await app.repo.loadPreviousPerformance(exercises)
        if !weightTouched, let ex = exercise { weight = previous[ex.id]?.first?.weightKg ?? weight }
    }

    static func prescribedStartingReps(_ prescription: String?) -> Int {
        guard let prescription, let match = prescription.range(of: "\\d+", options: .regularExpression), let value = Int(prescription[match]) else { return 10 }
        return min(max(value, 1), 100)
    }

    // MARK: Zamanlayıcılar

    /// Ekran saniyede iki kez çağırır.
    func tick(_ now: Date = Date()) {
        if !paused { elapsed = min(max(Int(now.timeIntervalSince(startedAt)), 0), 24 * 3600) }
        if let deadline = restDeadline, !restPaused, !paused {
            let remaining = max(Int(ceil(deadline.timeIntervalSince(now))), 0)
            if remaining != restSeconds { restSeconds = remaining }
            if remaining == 0 {
                restDeadline = nil
                restFinished()
            }
        }
    }

    func stopwatchTick() {
        guard stopwatchRunning, stopwatchSeconds > 0, !paused else { return }
        stopwatchSeconds -= 1
        if stopwatchSeconds == 0 { stopwatchRunning = false; UINotificationFeedbackGenerator().notificationOccurred(.success); speak(tr("Süre tamamlandı", "Timer complete")) }
    }

    private func restFinished() {
        UINotificationFeedbackGenerator().notificationOccurred(.success)
        NotificationService.shared.cancelRestEnd()
        speak(tr("Dinlenme tamamlandı. Sıradaki sete hazırsın.", "Rest complete. You are ready for the next set."))
        persist()
    }

    private func speak(_ text: String) {
        let utterance = AVSpeechUtterance(string: text)
        utterance.voice = AVSpeechSynthesisVoice(language: AppLang.shared.en ? "en-US" : "tr-TR")
        synth.speak(utterance)
    }

    func skipRest() { restSeconds = 0; restDeadline = nil; restPaused = false; NotificationService.shared.cancelRestEnd(); persist() }

    func addRest(_ seconds: Int = 30) {
        restSeconds += seconds
        if !restPaused { restDeadline = max(restDeadline ?? Date(), Date()).addingTimeInterval(Double(seconds)); scheduleRestNotification() }
        persist()
    }

    func toggleRest() {
        restPaused.toggle()
        if restPaused { restDeadline = nil; NotificationService.shared.cancelRestEnd() }
        else { restDeadline = Date().addingTimeInterval(Double(restSeconds)); scheduleRestNotification() }
        persist()
    }

    private func scheduleRestNotification() {
        let remaining = restDeadline.map { Int($0.timeIntervalSinceNow) } ?? 0
        let name = exercise?.name ?? ""
        Task { await NotificationService.shared.scheduleRestEnd(seconds: remaining, exercise: name) }
    }

    func setTimer(_ seconds: Int) { stopwatchSeconds = seconds; stopwatchRunning = seconds > 0 }

    // MARK: Set akışı

    func adjustWeight(_ delta: Double) { weightTouched = true; weight = max(weight + delta, 0); persist() }
    func adjustReps(_ delta: Int) { reps = max(reps + delta, 1); persist() }

    static func smartRestSeconds(_ exercise: WorkoutExercise, rpe: Int) -> Int {
        let name = exercise.name.lowercased()
        let compound = ["squat", "bench", "deadlift", "row", "press", "pull-up", "barbell"].contains { name.contains($0) }
        return min(max((compound ? 120 : 75) + (rpe >= 9 ? 30 : (rpe <= 5 ? -15 : 0)), 60), 150)
    }

    func recordSetAndContinue() {
        guard let current = exercise else { return }
        if completedSets.contains(where: { $0.exerciseId == current.id && $0.setNumber == currentSet }) { return }
        let old = previous[current.id]?.indices.contains(currentSet - 1) == true ? previous[current.id]![currentSet - 1] : nil
        personalRecord = old.map { o in (o.weightKg.map { weight > $0 } ?? false) || (weight >= (o.weightKg ?? weight) && reps > (o.reps ?? reps)) } ?? false
        completedSets.append(WorkoutSetInput(exerciseId: current.id, exerciseName: current.name, exerciseOrder: exerciseIndex + 1, setNumber: currentSet, weightKg: weight, reps: reps, durationSeconds: nil, rpe: rpe, setType: setType, note: note))
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        note = ""; setType = "normal"
        if currentSet < totalSets {
            currentSet += 1
            restSeconds = Self.smartRestSeconds(current, rpe: rpe)
            restPaused = false
            restDeadline = Date().addingTimeInterval(Double(restSeconds))
            scheduleRestNotification()
        } else if exerciseIndex < exercises.count - 1 {
            moveToExercise(exerciseIndex + 1)
        } else { showFeedback = true }
        persist()
    }

    private func moveToExercise(_ index: Int) {
        exerciseIndex = index; currentSet = 1; weightTouched = false
        let next = previous[exercises[index].id]?.first
        weight = next?.weightKg ?? 0
        reps = next?.reps ?? Self.prescribedStartingReps(exercises[index].reps)
        restSeconds = 0; restDeadline = nil; restPaused = false
        NotificationService.shared.cancelRestEnd()
    }

    func skipExercise() {
        if exerciseIndex < exercises.count - 1 { moveToExercise(exerciseIndex + 1); persist() }
        else if completedSets.isEmpty { showNoSets = true }
        else { showFeedback = true }
    }

    func requestFinish() { if completedSets.isEmpty { showNoSets = true } else { showFeedback = true } }

    // MARK: Hareket değiştirme

    func requestReplacement(reason: String, discomfortArea: String?) async {
        guard let ex = exercise else { return }
        replacementBusy = true; replacementCandidate = nil; defer { replacementBusy = false }
        do { replacementCandidate = try await app.repo.replaceWorkoutExercise(currentExerciseId: ex.id, reason: reason, exercises: exercises, profile: app.profile, discomfortArea: discomfortArea, locale: AppLang.shared.code)
            if replacementCandidate == nil { app.notify(tr("Uygun bir alternatif bulunamadı.", "No suitable alternative found.")) }
        } catch { app.fail(error) }
    }

    func applyReplacement(_ r: ExerciseReplacementCandidate) {
        guard exercises.indices.contains(exerciseIndex) else { return }
        exercises[exerciseIndex].id = r.replacementExerciseId; exercises[exerciseIndex].name = r.replacementExerciseName
        exercises[exerciseIndex].sets = r.sets; exercises[exerciseIndex].reps = r.reps; exercises[exerciseIndex].restSeconds = r.restSeconds
        weight = 0; reps = Self.prescribedStartingReps(r.reps); weightTouched = false
        replacementCandidate = nil; showReplacement = false
        Task { await loadPrevious() }
        persist()
    }

    // MARK: Bitirme

    func finish(feedback: WorkoutFeedback) async {
        let duration = max(Int(Date().timeIntervalSince(startedAt)), 1)
        let calories = max(duration / 60 * 7, 60)
        let sets = completedSets, list = exercises
        let history = app.dashboard?.exercisePerformance ?? []
        let prs = Dictionary(grouping: sets, by: \.exerciseId).compactMap { detectPersonalRecord(exerciseName: $0.value[0].exerciseName, current: $0.value, history: history) }
        let areas = Dictionary(list.map { ($0.id, $0.area) }, uniquingKeysWith: { a, _ in a })
        let programTitle = title ?? app.activeProgram.map { localizedProgramName($0.name, source: $0.source) } ?? tr("Antrenman", "Workout")
        let snap = app.gamification()
        let todayActive = snap?.habitWeek.first(where: { $0.today })?.active == true
        let streak = (snap?.streakDays ?? 0) + (todayActive ? 0 : 1)
        let xpGain = min(40, max((snap?.dailyXpCap ?? 100) - (snap?.dailyQuests.filter(\.completed).reduce(0) { $0 + $1.xp } ?? 0), 0))

        clearStore()
        let saved = await app.completeWorkout(durationSeconds: duration, calories: calories, sets: sets, feedback: feedback, exercises: list)
        _ = saved
        Task { try? await app.repo.clearActiveWorkout() }
        showFeedback = false
        isPresented = false
        UIApplication.shared.isIdleTimerDisabled = false
        exercises = []
        summary = WorkoutSummaryData(title: programTitle, durationSeconds: duration, calories: calories, sets: sets, personalRecords: prs, exerciseAreas: areas)
        app.celebrations.insert(CelebrationEvent(kind: .workout, title: prs.isEmpty ? tr("Antrenman tamam!", "Workout done!") : tr("Yeni rekor! 🏆", "New record! 🏆"),
            subtitle: prs.isEmpty ? tr("\(sets.count) set, \(duration / 60) dakika. Bugün kendine söz verdin ve tuttun.", "\(sets.count) sets, \(duration / 60) minutes. You made yourself a promise and kept it.") : prs.map(\.exerciseName).joined(separator: ", ") + tr(" için kişisel rekor kırdın.", ": new personal record."),
            xpGained: xpGain, level: snap?.level.level, streakDays: streak, emoji: prs.isEmpty ? "💪" : "🏆"), at: 0)
    }

    func skipWorkout(postpone: Bool) async {
        clearStore()
        let program = app.activeProgram
        await app.skipTodayWorkout(postpone: postpone, programId: program?.id, programName: program?.name)
        Task { try? await app.repo.clearActiveWorkout() }
        showNoSets = false; isPresented = false; exercises = []
        UIApplication.shared.isIdleTimerDisabled = false
    }

    func discard() { clearStore(); NotificationService.shared.cancelRestEnd(); isPresented = false; exercises = []; UIApplication.shared.isIdleTimerDisabled = false; Task { try? await app.repo.clearActiveWorkout() } }

    // MARK: Kalıcılık (Android ActiveWorkoutStore ile aynı biçim)

    private static let key = "active_workout_snapshot"

    var deviceId: String {
        if let id = UserDefaults.standard.string(forKey: "active_workout_device") { return id }
        let id = UUID().uuidString; UserDefaults.standard.set(id, forKey: "active_workout_device"); return id
    }

    func persist() {
        guard !exercises.isEmpty else { return }
        let nowMs = Date().timeIntervalSince1970 * 1000
        let json: JSON = [
            "version": 1, "startedAt": .number(startedAt.timeIntervalSince1970 * 1000), "savedAt": .number(nowMs), "exercises": WorkoutExercise.jsonArray(exercises),
            "exerciseIndex": JSON(exerciseIndex), "currentSet": JSON(currentSet), "weight": JSON(Int(weight.rounded())), "reps": JSON(reps), "rpe": JSON(rpe), "setType": JSON(setType), "note": JSON(note),
            "completedSets": .array(completedSets.map { JSON(Self.encodeSet($0)) }),
            "restDeadlineEpochMs": .number((restDeadline?.timeIntervalSince1970 ?? 0) * 1000), "restPausedSeconds": JSON(restPaused ? restSeconds : 0),
            "weightKg": JSON(weight), "title": .opt(title),
        ]
        UserDefaults.standard.set(json.text, forKey: Self.key)
        pushTask?.cancel()
        pushTask = Task { [snapshot = json, device = deviceId] in
            try? await Task.sleep(for: .milliseconds(1500))
            if Task.isCancelled { return }
            try? await AppModel.shared.repo.pushActiveWorkout(snapshot: snapshot, deviceId: device)
        }
    }

    private func clearStore() { UserDefaults.standard.removeObject(forKey: Self.key); pushTask?.cancel() }

    private func readSnapshot(now: Date = Date()) -> JSON? {
        guard let text = UserDefaults.standard.string(forKey: Self.key) else { return nil }
        let json = JSON.parse(text)
        guard json.int("version") == 1 else { clearStore(); return nil }
        let started = json.double("startedAt")
        let nowMs = now.timeIntervalSince1970 * 1000
        guard started >= nowMs - Self.maxActiveMillis * 1000, started <= nowMs + 60_000, !WorkoutExercise.list(json["exercises"]).isEmpty else { clearStore(); return nil }
        return json
    }

    private func apply(_ json: JSON) {
        exercises = WorkoutExercise.list(json["exercises"])
        startedAt = Date(timeIntervalSince1970: json.double("startedAt") / 1000)
        exerciseIndex = min(max(json.int("exerciseIndex"), 0), exercises.count - 1)
        currentSet = max(json.int("currentSet", 1), 1)
        weight = json.doubleOrNil("weightKg") ?? Double(json.int("weight"))
        reps = max(json.int("reps", 10), 1); rpe = min(max(json.int("rpe", 7), 1), 10)
        setType = json.string("setType", "normal"); note = json.string("note")
        completedSets = json["completedSets"].items.compactMap { $0.stringValue.flatMap(Self.decodeSet) }
        title = json.nonEmptyString("title")
        let deadline = json.double("restDeadlineEpochMs")
        let pausedSeconds = json.int("restPausedSeconds")
        if pausedSeconds > 0 { restPaused = true; restSeconds = pausedSeconds; restDeadline = nil }
        else if deadline > 0 { restDeadline = Date(timeIntervalSince1970: deadline / 1000); restSeconds = max(Int(ceil(restDeadline!.timeIntervalSinceNow)), 0); restPaused = false }
        prepared = true; paused = false; weightTouched = true
    }

    /// Başka cihazdan gelen daha yeni anlığı (aynı hesap) yerel kayıt yerine koyar.
    func importRemote(_ remote: JSON) -> Bool {
        guard remote.double("savedAt") > (readSnapshot()?.double("savedAt") ?? 0), remote.int("version") == 1 else { return false }
        UserDefaults.standard.set(remote.text, forKey: Self.key)
        return true
    }

    // Set kodlama: Android `toSavedWorkoutSet` ile birebir.
    private static func b64(_ s: String) -> String { Data(s.utf8).base64EncodedString().replacingOccurrences(of: "+", with: "-").replacingOccurrences(of: "/", with: "_").replacingOccurrences(of: "=", with: "") }
    private static func unb64(_ s: String) -> String? {
        var v = s.replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
        while v.count % 4 != 0 { v += "=" }
        return Data(base64Encoded: v).flatMap { String(data: $0, encoding: .utf8) }
    }
    static func encodeSet(_ s: WorkoutSetInput) -> String {
        [b64(s.exerciseId), b64(s.exerciseName), "\(s.exerciseOrder)", "\(s.setNumber)", s.weightKg.map { "\($0)" } ?? "", s.reps.map { "\($0)" } ?? "", s.durationSeconds.map { "\($0)" } ?? "", s.rpe.map { "\($0)" } ?? "", b64(s.setType), b64(s.note)].joined(separator: "|")
    }
    static func decodeSet(_ value: String) -> WorkoutSetInput? {
        let f = value.components(separatedBy: "|")
        guard f.count == 10, let id = unb64(f[0]), let name = unb64(f[1]), let order = Int(f[2]), let number = Int(f[3]), let type = unb64(f[8]), let note = unb64(f[9]) else { return nil }
        return WorkoutSetInput(exerciseId: id, exerciseName: name, exerciseOrder: order, setNumber: number, weightKg: Double(f[4]), reps: Int(f[5]), durationSeconds: Int(f[6]), rpe: Int(f[7]), setType: type, note: note)
    }
}
