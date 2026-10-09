import Foundation

// MARK: - Check-in

struct Checkin: Equatable, Sendable {
    var day: String
    var energy: Int
    var sleepQuality: Int
    var sleepHours: Double?
    var soreness: Int = 0
    var pain: Int = 0
    var availableMinutes: Int?

    func json(localDate: String = Dates.day(Date())) -> JSON {
        ["day": JSON(day), "energy": JSON(energy), "sleepQuality": JSON(sleepQuality), "sleepHours": .opt(sleepHours), "soreness": JSON(soreness), "pain": JSON(pain),
         "availableMinutes": .opt(availableMinutes), "localDate": JSON(localDate)]
    }

    /// Sunucudaki `readinessFromCheckin` ile aynı formül.
    var readinessInput: DailyReadinessInput {
        var input = DailyReadinessInput()
        input.energy = energy; input.sleepQuality = sleepQuality
        input.fatigue = min(max(Int(((Double(11 - energy)) * 0.6 + Double(soreness) * 0.4).rounded()), 1), 10)
        input.hasSoreness = soreness >= 3
        input.discomfortLevel = max(1, pain)
        return input
    }

    init(day: String, energy: Int, sleepQuality: Int, sleepHours: Double? = nil, soreness: Int = 0, pain: Int = 0, availableMinutes: Int? = nil) {
        self.day = day; self.energy = energy; self.sleepQuality = sleepQuality; self.sleepHours = sleepHours; self.soreness = soreness; self.pain = pain; self.availableMinutes = availableMinutes
    }

    init?(json: JSON) {
        guard json.isObject, !json["energy"].isNull else { return nil }
        self.init(day: json.string("day"), energy: json.int("energy"), sleepQuality: json.int("sleepQuality"), sleepHours: json.doubleOrNil("sleepHours"),
                  soreness: json.int("soreness"), pain: json.int("pain"), availableMinutes: json.intOrNil("availableMinutes"))
    }
}

struct CheckinSaveResult: Sendable { var checkin: Checkin; var cycle: CycleState? }

enum CheckinChoices {
    static let energy = [2, 4, 6, 8, 10]
    static let sleep: [(hours: Double, quality: Int)] = [(4.5, 3), (5.5, 5), (7.5, 8), (9.5, 8)]
    static let soreness = [0, 3, 6, 9]
    static let pain = [0, 3, 6, 9]
    static let minutes = [15, 20, 30, 45, 60]
}

// MARK: - Döngü

struct CycleProfile: Equatable, Sendable {
    var trackingEnabled = false
    var lastPeriodStart: String?
    var cycleLengthDays: Int?
    var periodLengthDays: Int?
    var regularity = "unknown"

    func json(localDate: String = Dates.day(Date())) -> JSON {
        ["trackingEnabled": JSON(trackingEnabled), "lastPeriodStart": .opt(lastPeriodStart), "cycleLengthDays": .opt(cycleLengthDays),
         "periodLengthDays": .opt(periodLengthDays), "regularity": JSON(regularity), "localDate": JSON(localDate)]
    }
}

struct CycleState: Equatable, Sendable {
    var cycleDay: Int
    /// menstrual | follicular | ovulatory | luteal; düzensiz ya da bayat veride nil.
    var phase: String?
    var periodLikely: Bool
    var nextPeriodStart: String
    var daysToNextPeriod: Int
    var stale: Bool

    init(json it: JSON) {
        cycleDay = it.int("cycleDay"); phase = it.nonEmptyString("phase"); periodLikely = it.bool("periodLikely")
        nextPeriodStart = it.string("nextPeriodStart"); daysToNextPeriod = it.int("daysToNextPeriod"); stale = it.bool("stale")
    }
}

struct CycleSnapshot: Equatable, Sendable {
    var profile: CycleProfile
    var state: CycleState?

    init(json: JSON) {
        let p = json["profile"]
        profile = CycleProfile(trackingEnabled: p.bool("trackingEnabled"), lastPeriodStart: p.nonEmptyString("lastPeriodStart"), cycleLengthDays: p.intOrNil("cycleLengthDays"),
                               periodLengthDays: p.intOrNil("periodLengthDays"), regularity: p.nonEmptyString("regularity") ?? "unknown")
        state = json["state"].isObject ? CycleState(json: json["state"]) : nil
    }
}

private func normalizedGender(_ gender: String) -> String { gender.trimmingCharacters(in: .whitespaces).lowercased() }
private let femaleValues: Set<String> = ["kadın", "kadin", "female", "woman"]
private let maleValues: Set<String> = ["erkek", "male", "man"]

/// Onboarding'de döngü sorusu yalnızca kadın olarak belirten kullanıcıya sorulur.
func cycleOptInInOnboarding(_ gender: String) -> Bool { femaleValues.contains(normalizedGender(gender)) }
/// Ayarlar girişi: erkek olarak belirten kullanıcıya gösterilmez.
func cycleSettingsVisible(_ gender: String) -> Bool { !maleValues.contains(normalizedGender(gender)) }

struct Personalization: Equatable, Sendable {
    var adaptiveEnabled = true
    var cycleEnabled = false
    var aiHealthContextEnabled = false
    init() {}
    init(json: JSON) {
        adaptiveEnabled = json.bool("adaptiveEnabled", true); cycleEnabled = json.bool("cycleEnabled"); aiHealthContextEnabled = json.bool("aiHealthContextEnabled")
    }
}

// MARK: - Uyarlama sonucu

struct AdaptiveResult: Equatable, Sendable {
    var adapted: Bool
    /// good | moderate | low | recovery
    var level: String
    var score: Int
    /// normal | reduced | recovery
    var intensity: String
    var appliedActions: [String]
    var lockedActions: [String]
    var exercises: [WorkoutExercise]
    var estimatedMinutes: Int
    var wellnessKind: String?
    var explanationTr: String
    var explanationEn: String
    var cycleSignal: String
    var reasons: [String]
    var aiExplanation: String?

    init(json: JSON) {
        let r = json["result"]
        adapted = r.bool("adapted"); level = r.string("level", "good"); score = r.int("score", 100); intensity = r.string("intensity", "normal")
        appliedActions = r["applied"].items.map { $0.string("action") }.filter { !$0.isEmpty }
        lockedActions = r.strings("lockedActions")
        exercises = r["exercises"].items.map { WorkoutExercise(id: $0.string("id"), name: $0.string("name"), area: $0.string("area", "Tüm Vücut"), sets: $0.int("sets", 2), reps: $0.string("reps", "8–10"), restSeconds: $0.int("restSeconds", 45)) }
        estimatedMinutes = r.int("estimatedMinutes", 20); wellnessKind = r.nonEmptyString("wellnessKind")
        explanationTr = r.string("explanationTr"); explanationEn = r.string("explanationEn")
        cycleSignal = r["signals"].string("cycle", "none"); reasons = r["signals"].strings("reasons")
        aiExplanation = json.nonEmptyString("aiExplanation")
    }

    func explanation(english: Bool) -> String {
        if let ai = aiExplanation, !ai.isEmpty { return ai }
        return english ? (explanationEn.isEmpty ? explanationTr : explanationEn) : (explanationTr.isEmpty ? explanationEn : explanationTr)
    }

    /// Koça yalnızca kaba özet gider; ham check-in değerleri ve döngü bilgisi asla eklenmez.
    var coachSummary: JSON {
        var object: [String: JSON] = ["level": JSON(level), "adapted": JSON(adapted), "intensity": JSON(intensity), "actions": .from(appliedActions), "reasons": .from(reasons), "estimatedMinutes": JSON(estimatedMinutes)]
        if let wellnessKind { object["sessionKind"] = JSON(wellnessKind) }
        return .object(object)
    }
}

func upgradeHint(for locked: [String]) -> String? {
    if locked.contains("switch_pilates") { return "switch_pilates" }
    if locked.contains("switch_recovery") || locked.contains("add_mobility") || locked.contains("replace_exercises") { return "adaptive" }
    return nil
}

struct NutritionTip: Identifiable, Equatable, Sendable { let id: String, title: String, body: String }
struct NutritionWellness: Equatable, Sendable {
    var tips: [NutritionTip]
    var proteinBonusGrams: Int
    var lockedTipCount: Int
    init(json: JSON) {
        tips = json["tips"].items.map { NutritionTip(id: $0.string("id"), title: $0.string("title"), body: $0.string("body")) }
        proteinBonusGrams = json.int("proteinBonusGrams"); lockedTipCount = json.int("lockedTipCount")
    }
}

func dietFromAnswers(_ answers: [String]) -> String {
    if answers.contains("Vegan") { return "vegan" }
    if answers.contains("Vejetaryen") { return "vegetarian" }
    if answers.contains("Pesketaryen") { return "pescatarian" }
    return "standard"
}

// MARK: - Wellness oturumu

struct WellnessSession: Equatable, Sendable {
    var kind: String, title: String, subtitle: String, estimatedMinutes: Int
    var exercises: [WorkoutExercise]
    init(json: JSON) {
        let s = json["session"]
        kind = s.string("kind"); title = s.string("title"); subtitle = s.string("subtitle"); estimatedMinutes = s.int("estimatedMinutes", 20)
        exercises = s["exercises"].items.map { WorkoutExercise(id: $0.string("id"), name: $0.string("name"), area: $0.string("area", "Tüm Vücut"), sets: $0.int("sets", 2), reps: $0.string("reps", "8–10"), restSeconds: $0.int("restSeconds", 25)) }
    }
}

enum WellnessKind: String, CaseIterable, Sendable { case pilatesToday = "pilates_today", lowImpactRecovery = "low_impact_recovery", postureMobility = "posture_mobility" }

/// Pilates/mobilite tercih edenlere ya da kadın olarak belirtenlere yalnızca daha yukarıda sunulur.
func wellnessProminent(gender: String, preferredStyles: String) -> Bool {
    let lowered = preferredStyles.lowercased()
    let prefers = ["pilates", "mobilite", "mobility", "barre", "düşük etkili", "low impact", "toparlanma", "recovery"].contains { lowered.contains($0) }
    return prefers || cycleOptInInOnboarding(gender)
}

// MARK: - Gizlilik

struct AiMemoryItem: Identifiable, Equatable, Sendable {
    var id: String, type: String, key: String, value: String, userExplicit: Bool
    var updatedAt: String?
    init?(json: JSON) {
        let id = json.string("id"); guard !id.isEmpty else { return nil }
        self.id = id; type = json.string("type"); key = json.string("key"); value = json.string("value")
        userExplicit = json.string("source") == "user_explicit"; updatedAt = json.nonEmptyString("updatedAt")
    }
}

@MainActor func aiMemoryTypeLabel(_ type: String) -> String {
    switch type {
    case "exercise_preference": return tr("Egzersiz tercihi", "Exercise preference")
    case "food_preference": return tr("Yemek tercihi", "Food preference")
    case "coaching_preference": return tr("Koçluk tercihi", "Coaching preference")
    case "schedule_preference": return tr("Program tercihi", "Schedule preference")
    case "goal": return tr("Hedef", "Goal")
    case "constraint": return tr("Kısıt (sağlık bilgisi içerebilir)", "Constraint (may include health info)")
    case "habit": return tr("Alışkanlık", "Habit")
    case "equipment": return tr("Ekipman", "Equipment")
    case "motivation_pattern": return tr("Motivasyon", "Motivation")
    default: return type
    }
}

func shouldExtractMemory(replySource: String, message: String) -> Bool { replySource == "ai" && message.trimmingCharacters(in: .whitespaces).count >= 8 }
