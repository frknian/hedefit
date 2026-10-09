import Foundation

/// Gün-bazlı tarih yardımcıları (yerel takvim günü, `yyyy-MM-dd`).
enum Dates {
    static let calendar: Calendar = { var c = Calendar(identifier: .gregorian); c.firstWeekday = 2; return c }()
    private static let dayFormatter: DateFormatter = {
        let f = DateFormatter(); f.calendar = Calendar(identifier: .gregorian); f.locale = Locale(identifier: "en_US_POSIX"); f.dateFormat = "yyyy-MM-dd"; return f
    }()

    static func day(_ date: Date = Date()) -> String { dayFormatter.string(from: date) }
    static func parse(_ day: String) -> Date? { dayFormatter.date(from: String(day.prefix(10))) }
    static func add(_ days: Int, to date: Date = Date()) -> Date { calendar.date(byAdding: .day, value: days, to: date) ?? date }
    static func startOfDay(_ date: Date = Date()) -> Date { calendar.startOfDay(for: date) }
    static func isSameDay(_ a: Date, _ b: Date) -> Bool { calendar.isDate(a, inSameDayAs: b) }
    /// Pazartesi başlangıçlı hafta başı.
    static func weekStart(_ date: Date = Date()) -> Date {
        let day = startOfDay(date)
        let weekday = calendar.component(.weekday, from: day) // 1 = Pazar
        return add(-((weekday + 5) % 7), to: day)
    }
    static func startOfMonth(_ date: Date = Date()) -> Date { calendar.date(from: calendar.dateComponents([.year, .month], from: date)) ?? startOfDay(date) }
    /// Epoch gün numarası (UTC'siz, yerel gün).
    static func epochDay(_ date: Date) -> Int { Int((startOfDay(date).timeIntervalSince1970 + Double(TimeZone.current.secondsFromGMT(for: startOfDay(date)))) / 86_400) }
    static func epochDay(_ day: String) -> Int? { parse(day).map(epochDay) }
}

// MARK: - Challenge

struct L10nText: Equatable, Sendable {
    var tr: String, en: String
    @MainActor var text: String { AppLang.shared.en ? (en.isEmpty ? tr : en) : (tr.isEmpty ? en : tr) }
    init(tr: String = "", en: String = "") { self.tr = tr; self.en = en }
    init(json: JSON) { tr = json.string("tr"); en = json.string("en") }
    var json: JSON { ["tr": JSON(tr), "en": JSON(en)] }
}

struct ChallengeTask: Equatable, Sendable {
    /// session | workout | steps | water | meals | checkin
    var kind: String
    var session: String?
    var minutes: Int?
    var target: Int?
    init(kind: String, session: String? = nil, minutes: Int? = nil, target: Int? = nil) { self.kind = kind; self.session = session; self.minutes = minutes; self.target = target }
    init(json: JSON) { kind = json.string("kind"); session = json.nonEmptyString("session"); minutes = json.intOrNil("minutes"); target = json.intOrNil("target") }
    var json: JSON {
        var object: [String: JSON] = ["kind": JSON(kind)]
        if let session { object["session"] = JSON(session) }; if let minutes { object["minutes"] = JSON(minutes) }; if let target { object["target"] = JSON(target) }
        return .object(object)
    }
}

struct ChallengeDayLog: Equatable, Sendable { var dayIndex: Int, localDate: String, status: String, minutes: Int? }

struct ChallengePlan: Equatable, Sendable {
    var key: String, source: String, category: String, difficulty: String, equipment: String
    var title: L10nText, description: L10nText
    var days: [ChallengeTask]
    init(json: JSON) {
        key = json.string("key"); source = json.string("source", "catalog"); category = json.string("category"); difficulty = json.string("difficulty", "beginner"); equipment = json.string("equipment", "none")
        title = L10nText(json: json["title"]); description = L10nText(json: json["description"]); days = json["days"].items.map(ChallengeTask.init(json:))
    }
    var json: JSON {
        ["key": JSON(key), "source": JSON(source), "category": JSON(category), "difficulty": JSON(difficulty), "equipment": JSON(equipment), "title": title.json, "description": description.json, "days": .array(days.map(\.json))]
    }
}

struct ChallengeTemplate: Equatable, Sendable, Identifiable {
    var plan: ChallengePlan
    var minutes: (Int, Int)?
    var rewardXp: Int
    var participants: Int
    var popular: Bool
    /// fit | stretch | needs_band
    var fit: String
    var recommended: Bool
    var id: String { plan.key }

    static func == (a: ChallengeTemplate, b: ChallengeTemplate) -> Bool { a.plan == b.plan && a.rewardXp == b.rewardXp && a.participants == b.participants && a.fit == b.fit && a.recommended == b.recommended }
}

struct ChallengeState: Equatable, Sendable {
    var totalDays: Int, doneDays: Int, currentDay: Int, todayDone: Bool, streak: Int, longestStreak: Int
    var recoveryUsed: Int, recoveryAllowance: Int, percent: Int, finished: Bool
}

func recoveryAllowance(totalDays: Int) -> Int { max(1, totalDays / 7) }

/// lib/challenges/engine.ts ile aynı durum hesabı.
func challengeState(totalDays: Int, logs: [ChallengeDayLog], today: Date = Date()) -> ChallengeState {
    let dates = Array(Set(logs.compactMap { Dates.epochDay($0.localDate) })).sorted()
    var longest = 0, run = 0
    var previous: Int?
    for day in dates { run = (previous != nil && day - previous! == 1) ? run + 1 : 1; longest = max(longest, run); previous = day }
    let todayNumber = Dates.epochDay(today)
    let streak = (previous != nil && todayNumber - previous! <= 1) ? run : 0
    let done = min(totalDays, logs.count), finished = done >= totalDays
    return ChallengeState(totalDays: totalDays, doneDays: done, currentDay: finished ? totalDays : done + 1, todayDone: dates.contains(todayNumber), streak: streak, longestStreak: longest,
                          recoveryUsed: logs.filter { $0.status == "recovery" }.count, recoveryAllowance: recoveryAllowance(totalDays: totalDays),
                          percent: totalDays > 0 ? done * 100 / totalDays : 0, finished: finished)
}

struct UserChallenge: Identifiable, Equatable, Sendable {
    var id: String
    var templateKey: String
    var plan: ChallengePlan
    /// active | completed | abandoned
    var status: String
    var socialChallengeId: String?
    var startedOn: String
    var completedAt: String?
    var days: [ChallengeDayLog]

    init(json: JSON) {
        id = json.string("id"); templateKey = json.string("templateKey"); plan = ChallengePlan(json: json["plan"]); status = json.string("status", "active")
        socialChallengeId = json.nonEmptyString("socialChallengeId"); startedOn = json.string("startedOn"); completedAt = json.nonEmptyString("completedAt")
        days = json["days"].items.map { ChallengeDayLog(dayIndex: $0.int("dayIndex"), localDate: $0.string("localDate"), status: $0.string("status"), minutes: $0.intOrNil("minutes")) }
    }

    var totalDays: Int { plan.days.count }
    func state(today: Date = Date()) -> ChallengeState { challengeState(totalDays: totalDays, logs: days, today: today) }
    func nextTask(today: Date = Date()) -> ChallengeTask? {
        let s = state(today: today)
        return s.finished || s.doneDays >= plan.days.count ? nil : plan.days[s.doneDays]
    }

    var json: JSON {
        ["id": JSON(id), "templateKey": JSON(templateKey), "plan": plan.json, "status": JSON(status), "socialChallengeId": .opt(socialChallengeId), "startedOn": JSON(startedOn), "completedAt": .opt(completedAt),
         "days": .array(days.map { ["dayIndex": JSON($0.dayIndex), "localDate": JSON($0.localDate), "status": JSON($0.status), "minutes": .opt($0.minutes)] as JSON })]
    }
}

struct ChallengeRules: Equatable, Sendable {
    var dayXp = 25, recoveryXp = 5, streak3Xp = 30, streak7Xp = 100, completeXp = 250, friendBonusXp = 100
}

struct ChallengeHub: Equatable, Sendable {
    var rules: ChallengeRules
    var templates: [ChallengeTemplate]
    var challenges: [UserChallenge]

    var active: [UserChallenge] { challenges.filter { $0.status == "active" } }
    var history: [UserChallenge] { challenges.filter { $0.status != "active" } }
    func primaryActive(today: Date = Date()) -> UserChallenge? {
        active.sorted {
            let a = $0.state(today: today), b = $1.state(today: today)
            if a.todayDone != b.todayDone { return !a.todayDone }
            return a.percent > b.percent
        }.first
    }

    init(rules: ChallengeRules = .init(), templates: [ChallengeTemplate] = [], challenges: [UserChallenge] = []) { self.rules = rules; self.templates = templates; self.challenges = challenges }

    init(json: JSON) {
        let r = json["rules"]
        func rule(_ name: String, _ fallback: Int) -> Int { r[name].intOrNil("amount") ?? fallback }
        rules = ChallengeRules(dayXp: rule("CHALLENGE_DAY_COMPLETED", 25), recoveryXp: rule("CHALLENGE_RECOVERY_DAY", 5), streak3Xp: rule("CHALLENGE_STREAK_3", 30),
                               streak7Xp: rule("CHALLENGE_STREAK_7", 100), completeXp: rule("CHALLENGE_COMPLETED", 250), friendBonusXp: rule("FRIEND_CHALLENGE_BONUS", 100))
        templates = json["templates"].items.map { item in
            let m = item["minutes"]
            return ChallengeTemplate(plan: ChallengePlan(json: item), minutes: m.count == 2 ? (m[0].intValue ?? 0, m[1].intValue ?? 0) : nil, rewardXp: item.int("rewardXp"),
                                     participants: item.int("participants"), popular: item.bool("popular"), fit: item.string("fit", "fit"), recommended: item.bool("recommended"))
        }
        challenges = json["challenges"].items.map(UserChallenge.init(json:))
    }

    /// Çevrimdışı önbellek için sunucu biçiminde geri yazar.
    var json: JSON {
        [
            "rules": ["CHALLENGE_DAY_COMPLETED": ["amount": JSON(rules.dayXp)].json, "CHALLENGE_RECOVERY_DAY": ["amount": JSON(rules.recoveryXp)].json, "CHALLENGE_STREAK_3": ["amount": JSON(rules.streak3Xp)].json,
                      "CHALLENGE_STREAK_7": ["amount": JSON(rules.streak7Xp)].json, "CHALLENGE_COMPLETED": ["amount": JSON(rules.completeXp)].json, "FRIEND_CHALLENGE_BONUS": ["amount": JSON(rules.friendBonusXp)].json].json,
            "templates": .array(templates.map { t in
                t.plan.json.merging(["rewardXp": JSON(t.rewardXp), "participants": JSON(t.participants), "popular": JSON(t.popular), "fit": JSON(t.fit), "recommended": JSON(t.recommended),
                                     "minutes": t.minutes.map { JSON.array([JSON($0.0), JSON($0.1)]) } ?? .null])
            }),
            "challenges": .array(challenges.map(\.json)),
        ]
    }
}

struct ChallengeAdaptation: Equatable, Sendable {
    var adapted: Bool
    var task: ChallengeTask
    var message: String?
    var suggestRecovery: Bool
    /// completed | adapted
    var completionStatus: String
}

struct ChallengeToday: Equatable, Sendable {
    var challenge: UserChallenge
    var task: ChallengeTask?
    var adaptation: ChallengeAdaptation?
    var session: WellnessSession?
    var checkinDone: Bool

    init(json: JSON) {
        challenge = UserChallenge(json: json["challenge"])
        task = json["task"].isObject ? ChallengeTask(json: json["task"]) : nil
        let a = json["adaptation"]
        adaptation = a.isObject ? ChallengeAdaptation(adapted: a.bool("adapted"), task: ChallengeTask(json: a["task"]), message: a.nonEmptyString("message"), suggestRecovery: a.bool("suggestRecovery"), completionStatus: a.string("completionStatus", "completed")) : nil
        session = json["session"].isObject ? WellnessSession(json: ["session": json["session"]]) : nil
        checkinDone = json.bool("checkinDone")
    }
}

struct ChallengeCompletion: Sendable { var alreadyDone: Bool, xp: Int, streak: Int, finished: Bool
    init(json: JSON) { alreadyDone = json.bool("alreadyDone"); xp = json.int("xp"); streak = json.int("streak"); finished = json.bool("finished") }
}

struct CoachChallengePreferences: Equatable, Sendable {
    var focus: String?, days: Int?, minutes: Int?, equipment: String?, level: String?
    var json: JSON {
        var o: [String: JSON] = [:]
        if let focus { o["focus"] = JSON(focus) }; if let days { o["days"] = JSON(days) }; if let minutes { o["minutes"] = JSON(minutes) }
        if let equipment { o["equipment"] = JSON(equipment) }; if let level { o["level"] = JSON(level) }
        return .object(o)
    }
}

struct CoachChallengePreview: Sendable {
    var plan: ChallengePlan, rewardXp: Int
    var minutes: (Int, Int)?
    var resolved: CoachChallengePreferences
    init(json: JSON) {
        plan = ChallengePlan(json: json["plan"]); rewardXp = json.int("rewardXp")
        let m = json["minutes"]; minutes = m.count == 2 ? (m[0].intValue ?? 0, m[1].intValue ?? 0) : nil
        let i = json["inputs"]
        resolved = CoachChallengePreferences(focus: i.nonEmptyString("focus"), days: i.intOrNil("days"), minutes: i.intOrNil("minutes"), equipment: i.nonEmptyString("equipment"), level: i.nonEmptyString("level"))
    }
}

struct FriendProfile: Sendable {
    var id: String, username: String?, displayName: String?, shared: Bool
    var totalXp: Int, completedChallenges: Int, bestStreak: Int
    var activeChallenges: [(title: L10nText, done: Int, total: Int)]
    var achievements: [String]
    init(json: JSON) {
        id = json.string("id"); username = json.nonEmptyString("username"); displayName = json.nonEmptyString("displayName"); shared = json.bool("shared", true)
        totalXp = json.int("totalXp"); completedChallenges = json.int("completedChallenges"); bestStreak = json.int("bestStreak")
        activeChallenges = json["activeChallenges"].items.map { (L10nText(json: $0["title"]), $0.int("doneDays"), $0.int("totalDays")) }
        achievements = json["achievements"].items.compactMap(\.stringValue).filter { !$0.isEmpty }
    }
}

// MARK: - Metinler

@MainActor func sessionLabel(_ session: String?) -> String {
    switch session {
    case "core_focus": return "Core"
    case "band_strength": return tr("Direnç bandı", "Resistance band")
    case "flexibility": return tr("Esneklik", "Flexibility")
    case "home_strength": return tr("Evde güç", "Home strength")
    case "pilates_today": return "Pilates"
    case "low_impact_recovery": return tr("Düşük etkili toparlanma", "Low impact recovery")
    case "posture_mobility": return tr("Duruş ve mobilite", "Posture & mobility")
    default: return tr("Antrenman", "Workout")
    }
}

@MainActor private func thousands(_ value: Int) -> String {
    let f = NumberFormatter(); f.numberStyle = .decimal; f.locale = AppLang.shared.en ? Locale(identifier: "en_US") : Locale(identifier: "tr_TR"); return f.string(from: NSNumber(value: value)) ?? "\(value)"
}

@MainActor func taskLabel(_ task: ChallengeTask) -> String {
    switch task.kind {
    case "session": return tr("\(task.minutes ?? 10) dk \(sessionLabel(task.session))", "\(task.minutes ?? 10) min \(sessionLabel(task.session))")
    case "workout": return tr("Programındaki antrenman", "Your program workout")
    case "steps": return tr("\(thousands(task.target ?? 0)) adım", "\(thousands(task.target ?? 0)) steps")
    case "water":
        let litres = Double(task.target ?? 0) / 1000
        let text = litres.truncatingRemainder(dividingBy: 1) == 0 ? "\(Int(litres))" : String(format: "%.1f", litres).replacingOccurrences(of: ".", with: AppLang.shared.en ? "." : ",")
        return tr("\(text) L su iç", "Drink \(text) L of water")
    case "meals": return tr("\(task.target ?? 3) öğün kaydet", "Log \(task.target ?? 3) meals")
    case "checkin": return tr("Günlük check-in", "Daily check-in")
    default: return tr("Görev", "Task")
    }
}

@MainActor func categoryLabel(_ category: String) -> String {
    switch category {
    case "workout": return tr("Antrenman", "Workout")
    case "nutrition": return tr("Beslenme", "Nutrition")
    case "steps": return tr("Adım", "Steps")
    case "pilates": return "Pilates"
    case "flexibility": return tr("Esneklik", "Flexibility")
    case "coach": return tr("Fit Koç", "Fit Coach")
    default: return category
    }
}

@MainActor func difficultyLabel(_ d: String) -> String {
    switch d { case "intermediate": return tr("Orta", "Intermediate"); case "advanced": return tr("İleri", "Advanced"); default: return tr("Başlangıç", "Beginner") }
}

@MainActor func equipmentLabel(_ e: String) -> String? {
    switch e { case "band": return tr("Direnç bandı", "Resistance band"); case "mat": return "Mat"; case "program": return tr("Kendi programın", "Your own program"); default: return nil }
}

@MainActor func fitLabel(_ fit: String) -> String {
    switch fit { case "stretch": return tr("Zorlayıcı", "Challenging"); case "needs_band": return tr("Bant gerekir", "Needs a band"); default: return tr("Sana uygun", "Good fit") }
}

@MainActor func minutesLabel(_ range: (Int, Int)?) -> String? {
    guard let (low, high) = range else { return nil }
    return low == high ? tr("\(low) dk/gün", "\(low) min/day") : tr("\(low)–\(high) dk/gün", "\(low)–\(high) min/day")
}

@MainActor func participantsLabel(_ count: Int) -> String? {
    if count <= 0 { return nil }
    if count >= 1000 {
        let k = Double(count) / 1000
        let text = AppLang.shared.en ? String(format: "%.1fK", k) : String(format: "%.1fB", k).replacingOccurrences(of: ".", with: ",")
        return tr("\(text) katılımcı", "\(text) participants")
    }
    return tr("\(count) katılımcı", count == 1 ? "1 participant" : "\(count) participants")
}

@MainActor func achievementTitle(_ id: String) -> String {
    switch id {
    case "first_challenge": return tr("İlk Challenge", "First Challenge")
    case "streak_3": return tr("3 Günlük Seri", "3-Day Streak")
    case "streak_7": return tr("7 Günlük Seri", "7-Day Streak")
    case "streak_30": return tr("30 Günlük Seri", "30-Day Streak")
    case "workouts_10": return tr("10 Antrenman", "10 Workouts")
    case "workouts_50": return tr("50 Antrenman", "50 Workouts")
    case "first_pilates_challenge": return tr("İlk Pilates Challenge", "First Pilates Challenge")
    case "first_nutrition_challenge": return tr("İlk Beslenme Challenge", "First Nutrition Challenge")
    case "first_friend_challenge": return tr("İlk Arkadaş Challenge", "First Friend Challenge")
    case "challenges_10": return tr("10 Challenge Tamamlandı", "10 Challenges Completed")
    case "first_activity": return tr("İlk Adım", "First Step")
    case "steps_100k": return "100K Club"
    case "marathon_distance": return "42.2"
    case "early_bird": return tr("Sabah Disiplini", "Morning Discipline")
    case "night_athlete": return tr("Gece Sporcusu", "Night Athlete")
    default: return id.replacingOccurrences(of: "_", with: " ").firstUppercased
    }
}

/// Sunucu hata kodlarının kullanıcı metni.
@MainActor func challengeErrorText(_ code: String?) -> String {
    switch code {
    case "already_joined": return tr("Bu challenge zaten aktif.", "This challenge is already active.")
    case "too_many_active": return tr("Aynı anda en fazla 3 challenge yürütebilirsin. Önce birini bitir ya da bırak.", "You can run up to 3 challenges at once. Finish or leave one first.")
    case "task_not_verified": return tr("Bugünkü görev henüz tamamlanmamış görünüyor. Görevi yaptıktan sonra tekrar dene.", "Today's task doesn't look complete yet. Try again after you've done it.")
    case "adaptation_not_allowed": return tr("Uyarlanmış görev için önce bugünün check-in'ini yap.", "Do today's check-in first to use the adapted task.")
    case "no_recovery_left": return tr("Bu challenge için toparlanma hakkın kalmadı.", "No recovery days left for this challenge.")
    case "invalid_day": return tr("Bugünün görevi zaten kaydedildi ya da tarih geçersiz.", "Today's task is already logged or the date is invalid.")
    case "not_active": return tr("Bu challenge artık aktif değil.", "This challenge is no longer active.")
    case "not_friends": return tr("Bu profili yalnızca arkadaşlar görebilir.", "Only friends can see this profile.")
    case "no_friends": return tr("Meydan okumak için en az bir arkadaş seç.", "Pick at least one friend to challenge.")
    case "challenges_unavailable": return tr("Challenge'lar şu an kullanılamıyor. Biraz sonra tekrar dene.", "Challenges are unavailable right now. Try again shortly.")
    default: return tr("İşlem tamamlanamadı. Bağlantını kontrol edip tekrar dene.", "Couldn't complete that. Check your connection and try again.")
    }
}
