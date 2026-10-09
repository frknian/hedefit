import Foundation

let achievementXp = 100

enum XpSource: String, Sendable { case workoutCompleted, stepGoalCompleted, routeDistance, nutritionTargetCompleted, sleepGoalCompleted, weeklyGoalCompleted, weeklyChallengeCompleted, achievementUnlocked }
struct XpEvent: Sendable { var source: XpSource; var sourceId: String; var amount: Int; var occurredOn: Date }
struct LevelProgress: Sendable { var level: Int; var currentXp: Int; var nextLevelXp: Int; var progress: Double }
struct DailyQuest: Identifiable, Sendable { var id: String; var title: String; var xp: Int; var completed: Bool }
enum AchievementRarity: Sendable { case common, rare, epic }

struct Achievement: Identifiable, Sendable {
    var id: String, title: String, description: String
    var progress: Double, target: Double
    var unlockedAt: Date?
    var rarity: AchievementRarity
    var rewardXp = achievementXp
}

struct HabitDay: Identifiable, Sendable { var date: Date; var active: Bool; var today: Bool; var id: Date { date } }
struct WeeklyChallenge: Sendable { var type: String; var title: String; var progress: Double; var target: Double; var rewardXp: Int; var completed: Bool }
struct AiReward: Sendable { var extraQuestions: Int; var nextThreshold: Int? }

struct GamificationSnapshot: Sendable {
    var streakDays: Int, totalXp: Int
    var level: LevelProgress
    var habitStrength: Int
    var habitWeek: [HabitDay]
    var dailyQuests: [DailyQuest]
    var challenge: WeeklyChallenge
    var achievements: [Achievement]
    var weeklyXp: Int, dailyXpCap: Int
    var aiReward: AiReward
    var motivation: String
}

struct GamificationInput: Sendable {
    var sessions: [WorkoutSession], routes: [RouteActivity], stepHistory: [DailyStep], todaySteps: Int
    var todayWaterMl = 0, sleepMinutes = 0
    var nutritionLogs: [NutritionLog] = []
    var nutritionGoal = NutritionGoal()
    var schedule: [WorkoutSchedule] = []
    var dailyStepGoal: Int
    var dailyWaterGoalMl = 2_000
    var weeklyActivityGoal: Int
    var authoritativeTotalXp: Int?
    var authoritativeWeeklyXp: Int?
    var unlockedAchievements: [String: String] = [:]
    var today = Date()
}

/// Saf alan servisi: görev dönüşümü ve ödüller verilen gün için deterministiktir.
@MainActor
enum GamificationEngine {
    static let dailyXpCap = 100
    private static let xpPerWorkout = 40, xpStepGoal = 40, xpPerRouteKm = 10, xpWeeklyGoal = 100, xpWeeklyChallenge = 250
    private static let challengeDistanceKm = 15.0

    static func snapshot(_ input: GamificationInput) -> GamificationSnapshot {
        let sessionDates = input.sessions.compactMap { instantDate($0.completedAt) }
        let routeDates = input.routes.compactMap { instantDate($0.startedAt) }
        let activeDates = Set((sessionDates + routeDates).map(Dates.epochDay))
        let today = Dates.startOfDay(input.today)
        let quests = dailyQuests(input, sessionDates: sessionDates, routeDates: routeDates)
        let achievements = achievementsList(input, activeDates: activeDates)
        let events = xpEvents(input, achievements: achievements)
        let totalXp = input.authoritativeTotalXp ?? events.reduce(0) { $0 + $1.amount }
        let weekStart = Dates.weekStart(today)
        let weekXp = input.authoritativeWeeklyXp ?? events.filter { $0.occurredOn >= weekStart && $0.occurredOn <= today }.reduce(0) { $0 + $1.amount }
        let distance = input.routes.filter { route in instantDate(route.startedAt).map { $0 >= weekStart && $0 <= today } ?? false }.reduce(0) { $0 + $1.distanceMeters } / 1000
        let streak = goalAwareStreak(today: today, activeDates: activeDates, weeklyGoal: input.weeklyActivityGoal)
        let week = (0..<7).map { offset -> HabitDay in
            let date = Dates.add(offset, to: weekStart)
            return HabitDay(date: date, active: activeDates.contains(Dates.epochDay(date)), today: Dates.isSameDay(date, today))
        }
        return GamificationSnapshot(streakDays: streak, totalXp: totalXp, level: levelFor(totalXp), habitStrength: habitStrength(input, activeDates: activeDates), habitWeek: week, dailyQuests: quests,
                                    challenge: WeeklyChallenge(type: "DISTANCE", title: tr("15 km Koş/Yürü", "Run/Walk 15 km"), progress: distance, target: challengeDistanceKm, rewardXp: xpWeeklyChallenge, completed: distance >= challengeDistanceKm),
                                    achievements: achievements, weeklyXp: weekXp, dailyXpCap: dailyXpCap, aiReward: aiReward(totalXp: totalXp),
                                    motivation: motivation(quests: quests, streak: streak, achievements: achievements, today: today))
    }

    static func xpEvents(_ input: GamificationInput, achievements: [Achievement] = []) -> [XpEvent] {
        var events: [XpEvent] = []
        for session in input.sessions { if let d = instantDate(session.completedAt) { events.append(XpEvent(source: .workoutCompleted, sourceId: session.id, amount: xpPerWorkout, occurredOn: d)) } }
        for route in input.routes {
            let km = Int(floor(max(route.distanceMeters, 0) / 1000))
            if km > 0, let d = instantDate(route.startedAt) { events.append(XpEvent(source: .routeDistance, sourceId: route.id, amount: km * xpPerRouteKm, occurredOn: d)) }
        }
        for step in input.stepHistory where step.steps >= input.dailyStepGoal { if let d = Dates.parse(step.localDate) { events.append(XpEvent(source: .stepGoalCompleted, sourceId: step.localDate, amount: xpStepGoal, occurredOn: d)) } }
        let sessionDates = input.sessions.compactMap { instantDate($0.completedAt) }
        for (week, dates) in Dictionary(grouping: sessionDates, by: { Dates.weekStart($0) }) where Set(dates.map(Dates.epochDay)).count >= input.weeklyActivityGoal {
            events.append(XpEvent(source: .weeklyGoalCompleted, sourceId: Dates.day(week), amount: xpWeeklyGoal, occurredOn: Dates.add(6, to: week)))
        }
        for (week, routes) in Dictionary(grouping: input.routes.compactMap { r in instantDate(r.startedAt).map { (Dates.weekStart($0), r) } }, by: { $0.0 }) where routes.reduce(0, { $0 + $1.1.distanceMeters }) >= challengeDistanceKm * 1000 {
            events.append(XpEvent(source: .weeklyChallengeCompleted, sourceId: "distance:\(Dates.day(week))", amount: xpWeeklyChallenge, occurredOn: Dates.add(6, to: week)))
        }
        for achievement in achievements where input.unlockedAchievements[achievement.id] != nil {
            if let at = achievement.unlockedAt { events.append(XpEvent(source: .achievementUnlocked, sourceId: achievement.id, amount: achievement.rewardXp, occurredOn: at)) }
        }
        var seen = Set<String>()
        return events.filter { seen.insert("\($0.source.rawValue)|\($0.sourceId)").inserted }
    }

    static func aiReward(totalXp: Int) -> AiReward {
        let xp = max(totalXp, 0)
        let questions = min(xp < 300 ? 0 : (xp < 500 ? 1 : 2 + (xp - 500) / 250), 5)
        let next: Int? = questions >= 5 ? nil : (xp < 300 ? 300 : (xp < 500 ? 500 : 500 + (questions - 1) * 250 + 250))
        return AiReward(extraQuestions: questions, nextThreshold: next)
    }

    static func levelFor(_ totalXp: Int) -> LevelProgress {
        var level = 1, remaining = max(totalXp, 0), required = xpForLevel(1)
        while remaining >= required { remaining -= required; level += 1; required = xpForLevel(level) }
        return LevelProgress(level: level, currentXp: remaining, nextLevelXp: required, progress: Double(remaining) / Double(required))
    }

    static func goalAwareStreak(today: Date, activeDates: Set<Int>, weeklyGoal: Int) -> Int {
        guard !activeDates.isEmpty else { return 0 }
        let goal = min(max(weeklyGoal, 1), 7)
        var week = Dates.weekStart(today), earliest = today, first = true
        let todayNumber = Dates.epochDay(today)
        while true {
            let effectiveEnd = first ? today : Dates.add(6, to: week)
            let count = activeDates.filter { $0 >= Dates.epochDay(week) && $0 <= Dates.epochDay(effectiveEnd) }.count
            let weekday = (Dates.calendar.component(.weekday, from: today) + 5) % 7 + 1
            let remainingDays = first ? 7 - weekday : 0
            if count < goal && (!first || count + remainingDays < goal) { break }
            if count == 0 && first { break }
            earliest = week; week = Dates.add(-7, to: week); first = false
        }
        if Dates.isSameDay(earliest, today) && !activeDates.contains(todayNumber) { return 0 }
        return max(todayNumber - Dates.epochDay(earliest) + 1, 1)
    }

    private static func fmt(_ value: Int) -> String {
        let f = NumberFormatter(); f.numberStyle = .decimal; f.locale = AppLang.shared.en ? Locale(identifier: "en_US") : Locale(identifier: "tr_TR"); return f.string(from: NSNumber(value: value)) ?? "\(value)"
    }

    private static func dailyQuests(_ input: GamificationInput, sessionDates: [Date], routeDates: [Date]) -> [DailyQuest] {
        let today = Dates.startOfDay(input.today)
        let workout = sessionDates.contains { Dates.isSameDay($0, today) }, route = routeDates.contains { Dates.isSameDay($0, today) }
        let sleep = input.sleepMinutes >= 420, meals = input.nutritionLogs.count >= 3
        let protein = input.nutritionLogs.reduce(0) { $0 + $1.protein } >= Double(input.nutritionGoal.protein) * 0.85
        let calories = Double(input.nutritionLogs.reduce(0) { $0 + $1.calories }) >= Double(input.nutritionGoal.calories) * 0.75
        let steps = input.todaySteps >= input.dailyStepGoal
        let goal = fmt(input.dailyStepGoal)
        let stepsTitle = tr("\(goal) adıma ulaş", "Reach \(goal) steps")
        let sleepTitle = tr("En az 7 saat uyu", "Sleep at least 7 hours"), mealsTitle = tr("3 öğününü kaydet", "Log 3 meals")
        let routeTitle = tr("15 dk yürüyüş/koşu yap", "Walk/run for 15 min"), workoutTitle = tr("Antrenmanını tamamla", "Complete your workout")
        let dayOfYear = Dates.calendar.ordinality(of: .day, in: .year, for: today) ?? 1
        switch dayOfYear % 4 {
        case 0: return [DailyQuest(id: "steps", title: stepsTitle, xp: 40, completed: steps), DailyQuest(id: "sleep", title: sleepTitle, xp: 25, completed: sleep), DailyQuest(id: "meals", title: mealsTitle, xp: 15, completed: meals),
                        DailyQuest(id: "pushups", title: tr("5 şınavlık antrenmanını tamamla", "Finish your 5-push-up workout"), xp: 20, completed: workout)]
        case 1: return [DailyQuest(id: "steps", title: stepsTitle, xp: 35, completed: steps), DailyQuest(id: "protein", title: tr("Protein hedefinin %85'ine ulaş", "Hit 85% of your protein goal"), xp: 25, completed: protein), DailyQuest(id: "workout", title: workoutTitle, xp: 40, completed: workout)]
        case 2: return [DailyQuest(id: "steps", title: stepsTitle, xp: 35, completed: steps), DailyQuest(id: "nutrition", title: tr("Günlük enerji hedefinin %75'ini kaydet", "Log 75% of your daily energy goal"), xp: 25, completed: calories),
                        DailyQuest(id: "sleep", title: sleepTitle, xp: 30, completed: sleep), DailyQuest(id: "route", title: routeTitle, xp: 10, completed: route)]
        default: return [DailyQuest(id: "sleep", title: sleepTitle, xp: 30, completed: sleep), DailyQuest(id: "meals", title: mealsTitle, xp: 20, completed: meals), DailyQuest(id: "route", title: routeTitle, xp: 20, completed: route), DailyQuest(id: "workout", title: workoutTitle, xp: 30, completed: workout)]
        }
    }

    private static func habitStrength(_ input: GamificationInput, activeDates: Set<Int>) -> Int {
        let today = Dates.epochDay(input.today), start = today - 29
        let recent = activeDates.filter { $0 >= start && $0 <= today }.count
        let expected = max(Double(min(max(input.weeklyActivityGoal, 1), 7)) * (30.0 / 7.0), 1)
        return min(max(Int((Double(recent) / expected * 100).rounded()), 0), 100)
    }

    private static func achievementsList(_ input: GamificationInput, activeDates: Set<Int>) -> [Achievement] {
        let workouts = Double(input.sessions.count), steps = Double(input.stepHistory.reduce(0) { $0 + $1.steps }), km = input.routes.reduce(0) { $0 + $1.distanceMeters } / 1000
        let times = input.sessions.map(\.completedAt) + input.routes.map(\.startedAt)
        let hours = times.compactMap { ISO.date($0) }.map { Dates.calendar.component(.hour, from: $0) }
        let early = Double(hours.filter { $0 < 8 }.count), night = Double(hours.filter { $0 >= 20 }.count)
        let streak = Double(goalAwareStreak(today: Dates.startOfDay(input.today), activeDates: activeDates, weeklyGoal: input.weeklyActivityGoal))
        let today = Dates.startOfDay(input.today)
        func serverOnly(_ id: String) -> Double { input.unlockedAchievements[id] != nil ? 1 : 0 }
        func item(_ id: String, _ title: String, _ description: String, _ progress: Double, _ target: Double, _ rarity: AchievementRarity) -> Achievement {
            let unlocked = input.unlockedAchievements[id].flatMap { Dates.parse($0) } ?? (progress >= target ? today : nil)
            return Achievement(id: id, title: title, description: description, progress: unlocked != nil ? target : min(progress, target), target: target, unlockedAt: unlocked, rarity: rarity)
        }
        let a = input.routes.count
        return [
            item("first_activity", tr("İlk Adım", "First Step"), tr("İlk aktiviteni tamamla", "Complete your first activity"), workouts + Double(a), 1, .common),
            item("workouts_3", tr("Ritmi Bul", "Find the Rhythm"), tr("3 antrenman tamamla", "Complete 3 workouts"), workouts, 3, .common),
            item("workouts_10", tr("Kararlı", "Committed"), tr("10 antrenman tamamla", "Complete 10 workouts"), workouts, 10, .common),
            item("workouts_25", tr("Güçleniyor", "Getting Stronger"), tr("25 antrenman tamamla", "Complete 25 workouts"), workouts, 25, .rare),
            item("workouts_50", tr("Demir İrade", "Iron Will"), tr("50 antrenman tamamla", "Complete 50 workouts"), workouts, 50, .epic),
            item("workouts_100", tr("Yüzlük Kulüp", "Century Club"), tr("100 antrenman tamamla", "Complete 100 workouts"), workouts, 100, .epic),
            item("steps_10k", tr("Hareket Et", "Get Moving"), tr("Toplam 10.000 adıma ulaş", "Reach 10,000 total steps"), steps, 10_000, .common),
            item("steps_50k", tr("50K Adım", "50K Steps"), tr("Toplam 50.000 adıma ulaş", "Reach 50,000 total steps"), steps, 50_000, .common),
            item("steps_100k", "100K Club", tr("Toplam 100.000 adıma ulaş", "Reach 100,000 total steps"), steps, 100_000, .rare),
            item("steps_250k", tr("Çeyrek Milyon", "Quarter Million"), tr("Toplam 250.000 adıma ulaş", "Reach 250,000 total steps"), steps, 250_000, .rare),
            item("steps_500k", tr("Yarım Milyon", "Half Million"), tr("Toplam 500.000 adıma ulaş", "Reach 500,000 total steps"), steps, 500_000, .epic),
            item("route_5", tr("Yola Çık", "Hit the Road"), tr("Toplam 5 km yürü/koş", "Walk/run 5 km in total"), km, 5, .common),
            item("route_half", "21.1", tr("Toplam 21,1 km yürü/koş", "Walk/run 21.1 km in total"), km, 21.1, .rare),
            item("marathon_distance", "42.2", tr("Toplam 42,2 km yürü/koş", "Walk/run 42.2 km in total"), km, 42.2, .epic),
            item("route_100", tr("Yolcu", "Traveler"), tr("Toplam 100 km yürü/koş", "Walk/run 100 km in total"), km, 100, .epic),
            item("route_250", tr("Ufuk Çizgisi", "Horizon"), tr("Toplam 250 km yürü/koş", "Walk/run 250 km in total"), km, 250, .epic),
            item("early_once", tr("Erken Kuş", "Early Bird"), tr("08:00 öncesi bir aktivite", "One activity before 08:00"), early, 1, .common),
            item("early_5", tr("Gün Doğumu", "Sunrise"), tr("08:00 öncesi 5 aktivite", "5 activities before 08:00"), early, 5, .rare),
            item("early_bird", tr("Sabah Disiplini", "Morning Discipline"), tr("08:00 öncesi 10 aktivite", "10 activities before 08:00"), early, 10, .epic),
            item("night_once", tr("Gece Modu", "Night Mode"), tr("20:00 sonrası bir aktivite", "One activity after 20:00"), night, 1, .common),
            item("night_5", tr("Gece Sporcusu", "Night Athlete"), tr("20:00 sonrası 5 aktivite", "5 activities after 20:00"), night, 5, .rare),
            item("streak_3", tr("3 Günlük Seri", "3-Day Streak"), tr("3 gün ritmini koru", "Keep your rhythm for 3 days"), streak, 3, .common),
            item("streak_7", tr("7 Günlük Seri", "7-Day Streak"), tr("7 gün ritmini koru", "Keep your rhythm for 7 days"), streak, 7, .rare),
            item("streak_21", tr("Alışkanlık", "Habit"), tr("21 gün ritmini koru", "Keep your rhythm for 21 days"), streak, 21, .epic),
            item("streak_30", tr("30 Günlük Seri", "30-Day Streak"), tr("30 gün ritmini koru", "Keep your rhythm for 30 days"), streak, 30, .epic),
            item("first_challenge", tr("İlk Challenge", "First Challenge"), tr("İlk challenge'ını tamamla", "Complete your first challenge"), serverOnly("first_challenge"), 1, .common),
            item("first_pilates_challenge", tr("İlk Pilates Challenge", "First Pilates Challenge"), tr("Bir Pilates challenge'ı tamamla", "Complete a Pilates challenge"), serverOnly("first_pilates_challenge"), 1, .rare),
            item("first_nutrition_challenge", tr("İlk Beslenme Challenge", "First Nutrition Challenge"), tr("Bir beslenme challenge'ı tamamla", "Complete a nutrition challenge"), serverOnly("first_nutrition_challenge"), 1, .rare),
            item("first_friend_challenge", tr("İlk Arkadaş Challenge", "First Friend Challenge"), tr("Bir arkadaşınla challenge tamamla", "Complete a challenge with a friend"), serverOnly("first_friend_challenge"), 1, .rare),
            item("challenges_10", tr("10 Challenge Tamamlandı", "10 Challenges Completed"), tr("10 challenge tamamla", "Complete 10 challenges"), serverOnly("challenges_10") * 10, 10, .epic),
        ]
    }

    private static func motivation(quests: [DailyQuest], streak: Int, achievements: [Achievement], today: Date) -> String {
        if achievements.contains(where: { $0.unlockedAt.map { Dates.isSameDay($0, today) } ?? false }) { return tr("Yeni başarımın +\(achievementXp) XP kazandırdı.", "Your new achievement earned +\(achievementXp) XP.") }
        if quests.allSatisfy(\.completed) { return tr("Bugünün \(dailyXpCap) XP'lik görevlerinin tamamını bitirdin.", "You finished all of today's \(dailyXpCap) XP quests.") }
        if streak > 0 && streak % 7 == 0 { return tr("\(streak) günlük ritmin sağlamlaşıyor.", "Your \(streak)-day rhythm is getting solid.") }
        return tr("Bugün küçük ama gerçek bir adım at.", "Take a small but real step today.")
    }

    private static func xpForLevel(_ level: Int) -> Int { 300 + (level - 1) * 50 }
    private static func instantDate(_ value: String) -> Date? { ISO.date(value).map(Dates.startOfDay) }
}
