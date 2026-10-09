import Foundation

let trackedMuscles = ["chest", "upper_back", "lower_back", "lats", "traps", "neck", "front_delts", "side_delts", "rear_delts",
                      "biceps", "triceps", "forearms", "abs", "glutes", "abductors", "adductors", "quads", "hamstrings", "calves"]

struct PersonalRecordResult: Sendable {
    var exerciseName: String
    var highestWeight: Bool, highestVolume: Bool, repetitionRecord: Bool
    var estimatedOneRepMax: Double
}

enum LoadLevel: Int, Sendable { case none, low, balanced, high, overload }

struct MuscleExerciseContribution: Sendable { var exerciseName: String; var volumeKg: Double; var setEquivalent: Double }

struct MuscleLoad: Identifiable, Sendable {
    var muscle: String
    var score: Double
    var setEquivalent: Double
    var level: LoadLevel
    var totalVolumeKg = 0.0
    var lastTrainedAt: String?
    var exercises: [MuscleExerciseContribution] = []
    var trendVolumes: [Double] = []
    var id: String { muscle }
}

struct TrainingAnalysis: Sendable {
    var totalVolumeKg: Double
    var totalRepetitions: Int
    var muscleLoads: [MuscleLoad]
    /// 1 = Pazartesi … 7 = Pazar
    var dailyVolume: [Int: Double]
    var balanceInsightTr: String
    var balanceInsightEn: String
    var rangeDays = 7
}

func setVolume(weightKg: Double?, reps: Int?) -> Double {
    if let w = weightKg, w > 0, let r = reps, r > 0 { return w * Double(r) }
    return 0
}

/// Epley formülü; tahmin olarak sunulur.
func estimatedOneRepMax(weightKg: Double?, reps: Int?) -> Double {
    guard let w = weightKg, w > 0, let r = reps, r > 0 else { return 0 }
    return w * (1 + Double(min(r, 30)) / 30)
}

func detectPersonalRecord(exerciseName: String, current: [WorkoutSetInput], history: [ExercisePerformance]) -> PersonalRecordResult? {
    let valid = current.filter { ($0.reps ?? 0) > 0 }
    guard !valid.isEmpty else { return nil }
    let past = history.filter { $0.exerciseName.caseInsensitiveCompare(exerciseName) == .orderedSame }.flatMap(\.sets)
    guard !past.isEmpty else { return nil }
    let maxWeight = valid.map { $0.weightKg ?? 0 }.max() ?? 0
    let maxVolume = valid.map { setVolume(weightKg: $0.weightKg, reps: $0.reps) }.max() ?? 0
    let maxReps = valid.map { $0.reps ?? 0 }.max() ?? 0
    let weightPR = maxWeight > (past.map { $0.weightKg ?? 0 }.max() ?? 0)
    let volumePR = maxVolume > (past.map { setVolume(weightKg: $0.weightKg, reps: $0.reps) }.max() ?? 0)
    let repPR = maxReps > (past.map { $0.reps ?? 0 }.max() ?? 0)
    guard weightPR || volumePR || repPR else { return nil }
    return PersonalRecordResult(exerciseName: exerciseName, highestWeight: weightPR, highestVolume: volumePR, repetitionRecord: repPR,
                                estimatedOneRepMax: valid.map { estimatedOneRepMax(weightKg: $0.weightKg, reps: $0.reps) }.max() ?? 0)
}

func loadLevel(forWeeklySets sets: Double) -> LoadLevel {
    switch sets { case ...0: return .none; case ..<6: return .low; case ...16: return .balanced; case ...22: return .high; default: return .overload }
}

func normalizeMuscle(_ raw: String) -> String {
    let v = raw.lowercased()
    func has(_ parts: String...) -> Bool { parts.contains { v.contains($0) } }
    if has("chest", "göğ", "pect") { return "chest" }
    if has("lat", "kanat") { return "lats" }
    if has("upper back", "middle back", "sırt", "row") { return "upper_back" }
    if has("lower back", "bel") { return "lower_back" }
    if has("trap") { return "traps" }
    if has("neck", "boyun") { return "neck" }
    if has("rear delt", "arka omuz") { return "rear_delts" }
    if has("front delt", "ön omuz") { return "front_delts" }
    if has("side delt", "yan omuz") { return "side_delts" }
    if has("shoulder", "omuz") { return "side_delts" }
    if has("biceps", "curl", "pazu", "ön kol") { return "biceps" }
    if has("triceps", "arka kol") { return "triceps" }
    if has("forearm", "önkol", "bilek") { return "forearms" }
    if has("abdominal", "abs", "core", "karın") { return "abs" }
    if has("glute", "kalça") { return "glutes" }
    if has("abductor", "dış bacak") { return "abductors" }
    if has("adductor", "iç bacak") { return "adductors" }
    if has("quad", "ön bacak", "leg press", "squat") { return "quads" }
    if has("hamstring", "arka bacak", "deadlift") { return "hamstrings" }
    if has("calf", "baldır") { return "calves" }
    return (raw.isEmpty ? "other" : raw).lowercased().replacingOccurrences(of: " ", with: "_")
}

func muscleNameTr(_ muscle: String) -> String {
    let map = ["chest": "göğüs", "upper_back": "üst sırt", "lower_back": "bel", "lats": "lat", "traps": "trapez", "neck": "boyun", "front_delts": "ön omuz", "side_delts": "yan omuz", "rear_delts": "arka omuz",
               "biceps": "biceps", "triceps": "triceps", "forearms": "ön kol", "abs": "karın", "glutes": "glute", "abductors": "dış bacak", "adductors": "iç bacak", "quads": "quadriceps", "hamstrings": "hamstring", "calves": "calf"]
    return map[muscle] ?? muscle.replacingOccurrences(of: "_", with: " ")
}
func muscleNameEn(_ muscle: String) -> String { muscle.replacingOccurrences(of: "_", with: " ").firstUppercased }
@MainActor func muscleName(_ muscle: String) -> String { tr(muscleNameTr(muscle).firstUppercased, muscleNameEn(muscle)) }

private func performanceDate(_ value: String) -> Date {
    if let date = ISO.date(value) { return Dates.startOfDay(date) }
    return Dates.parse(String(value.prefix(10))) ?? .distantPast
}

private struct MutableSummary {
    var score = 0.0, sets = 0.0, volume = 0.0
    var lastTrainedAt: String?
    var exerciseVolume: [String: Double] = [:]
    var exerciseSets: [String: Double] = [:]
    var exerciseOrder: [String] = []
    var trend = [0.0, 0.0, 0.0, 0.0]
}

func analyzeTraining(_ performances: [ExercisePerformance], catalog: [ExerciseCatalogItem] = [], today: Date = Date(), rangeDays: Int = 7, primaryFactor: Double = 1, secondaryFactor: Double = 0.5) -> TrainingAnalysis {
    let safeRange = min(max(rangeDays, 1), 365)
    let todayDay = Dates.startOfDay(today)
    let cutoff = Dates.add(-(safeRange - 1), to: todayDay), trendCutoff = Dates.add(-29, to: todayDay)
    let byId = Dictionary(catalog.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
    var summaries = Dictionary(uniqueKeysWithValues: trackedMuscles.map { ($0, MutableSummary()) })
    var daily: [Int: Double] = Dictionary(uniqueKeysWithValues: (1...7).map { ($0, 0.0) })
    var volume = 0.0, repetitions = 0

    for performance in performances {
        let date = performanceDate(performance.completedAt)
        if date > todayDay || date < min(cutoff, trendCutoff) { continue }
        let exercise = performance.exerciseId.flatMap { byId[$0] }
        let primary = (exercise?.primaryMuscles.isEmpty == false ? exercise!.primaryMuscles : [normalizeMuscle(performance.exerciseName)])
        let secondary = exercise?.secondaryMuscles ?? []
        let validSets = performance.sets.filter { ($0.reps ?? 0) > 0 || ($0.durationSeconds ?? 0) > 0 }
        if validSets.isEmpty { continue }
        let exerciseVolume = validSets.reduce(0) { $0 + setVolume(weightKg: $1.weightKg, reps: $1.reps) }
        let inRange = date >= cutoff
        if inRange {
            volume += exerciseVolume
            repetitions += validSets.reduce(0) { $0 + ($1.reps ?? 0) }
            let weekday = (Dates.calendar.component(.weekday, from: date) + 5) % 7 + 1
            daily[weekday, default: 0] += exerciseVolume
        }
        func contribute(_ rawMuscle: String, _ factor: Double) {
            let muscle = normalizeMuscle(rawMuscle)
            var summary = summaries[muscle] ?? MutableSummary()
            let weightedVolume = exerciseVolume * factor
            let weightedSets = Double(validSets.count) * factor
            if inRange {
                summary.score += weightedVolume > 0 ? weightedVolume : weightedSets
                summary.volume += weightedVolume
                summary.sets += weightedSets
                if summary.exerciseSets[performance.exerciseName] == nil { summary.exerciseOrder.append(performance.exerciseName) }
                summary.exerciseVolume[performance.exerciseName, default: 0] += weightedVolume
                summary.exerciseSets[performance.exerciseName, default: 0] += weightedSets
                if summary.lastTrainedAt == nil || performance.completedAt > summary.lastTrainedAt! { summary.lastTrainedAt = performance.completedAt }
            }
            if date >= trendCutoff {
                let daysAgo = min(max(Dates.epochDay(todayDay) - Dates.epochDay(date), 0), 29)
                let bucket = min(max(3 - daysAgo / 8, 0), 3)
                summary.trend[bucket] += weightedVolume > 0 ? weightedVolume : weightedSets
            }
            summaries[muscle] = summary
        }
        primary.forEach { contribute($0, primaryFactor) }
        secondary.forEach { contribute($0, secondaryFactor) }
    }

    var loads = summaries.map { muscle, summary -> MuscleLoad in
        let weekly = summary.sets * 7 / Double(safeRange)
        let contributions = summary.exerciseOrder.map { MuscleExerciseContribution(exerciseName: $0, volumeKg: summary.exerciseVolume[$0] ?? 0, setEquivalent: summary.exerciseSets[$0] ?? 0) }
            .sorted { ($0.volumeKg > 0 ? $0.volumeKg : $0.setEquivalent) > ($1.volumeKg > 0 ? $1.volumeKg : $1.setEquivalent) }
        return MuscleLoad(muscle: muscle, score: summary.score, setEquivalent: summary.sets, level: loadLevel(forWeeklySets: weekly), totalVolumeKg: summary.volume,
                          lastTrainedAt: summary.lastTrainedAt, exercises: contributions, trendVolumes: summary.trend)
    }
    loads.sort { a, b in
        if a.score != b.score { return a.score > b.score }
        return (trackedMuscles.firstIndex(of: a.muscle) ?? 99) < (trackedMuscles.firstIndex(of: b.muscle) ?? 99)
    }

    let trained = loads.filter { $0.level != .none }
    let overload = trained.first { $0.level == .overload }, low = trained.first { $0.level == .low }
    let insightTr: String, insightEn: String
    if trained.isEmpty { insightTr = "Henüz yeterli antrenman verin yok."; insightEn = "There is not enough workout data yet." }
    else if let overload {
        insightTr = "\(muscleNameTr(overload.muscle).firstUppercased) yükün bu dönem aşırı yüksek. Toparlanmanı izle ve gerekirse set azalt."
        insightEn = "Your \(muscleNameEn(overload.muscle).lowercased()) load is excessive for this period. Monitor recovery and reduce sets if needed."
    } else if let low {
        insightTr = "\(muscleNameTr(low.muscle).firstUppercased) hacmin bu dönem düşük kaldı. Sonraki programında dengeleyici set ekleyebilirsin."
        insightEn = "Your \(muscleNameEn(low.muscle).lowercased()) volume is low for this period. Add balancing sets to your next program."
    } else {
        insightTr = "Seçilen dönemde kas dağılımın dengeli görünüyor. Formu koruyarak kademeli ilerle."
        insightEn = "Your muscle distribution looks balanced for the selected period. Keep progressing gradually with good form."
    }
    return TrainingAnalysis(totalVolumeKg: volume, totalRepetitions: repetitions, muscleLoads: loads, dailyVolume: daily, balanceInsightTr: insightTr, balanceInsightEn: insightEn, rangeDays: safeRange)
}

func compactKg(_ value: Double) -> String { value >= 1000 ? String(format: "%.1f ton", value / 1000) : "\(Int(value.rounded())) kg" }

// MARK: - Plan rotasyonu

enum PlanRotationPeriod: String, Sendable {
    case weekly, monthly
    init(key: String?) { self = key == "weekly" ? .weekly : .monthly }
}

enum PlanRotation {
    static func blockId(_ period: PlanRotationPeriod, _ date: Date) -> Int {
        switch period {
        case .weekly: return Int(floor(Double(Dates.epochDay(date) - 4) / 7))
        case .monthly:
            let c = Dates.calendar.dateComponents([.year, .month], from: date)
            return (c.year ?? 0) * 12 + ((c.month ?? 1) - 1)
        }
    }

    static func nextBlockStart(_ period: PlanRotationPeriod, _ date: Date) -> Date {
        switch period {
        case .weekly: return Dates.add(7, to: Dates.weekStart(date))
        case .monthly:
            let start = Dates.calendar.date(from: Dates.calendar.dateComponents([.year, .month], from: date)) ?? date
            return Dates.calendar.date(byAdding: .month, value: 1, to: start) ?? date
        }
    }

    static func isDue(_ period: PlanRotationPeriod, generatedOn: Date?, today: Date) -> Bool {
        guard let generatedOn else { return false }
        return blockId(period, today) > blockId(period, generatedOn)
    }
}

/// Cihazın hesap başına son AI planı ürettiği günü hatırlar.
enum PlanRotationStore {
    static func generatedOn(_ userId: String?) -> Date? {
        guard let userId, let value = UserDefaults.standard.string(forKey: "generated_on_\(userId)") else { return nil }
        return Dates.parse(value)
    }
    static func markGenerated(_ userId: String?, date: Date = Date()) {
        if let userId { UserDefaults.standard.set(Dates.day(date), forKey: "generated_on_\(userId)") }
    }
}
