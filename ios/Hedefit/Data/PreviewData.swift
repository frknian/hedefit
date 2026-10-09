#if DEBUG
import Foundation

/// Yalnızca DEBUG derlemelerinde, `-HedefitPreview` başlatma argümanıyla: ağ olmadan arayüzü doğrulamak için ÖRNEK VERİ (Sample Data).
@MainActor
enum PreviewData {
    static var isEnabled: Bool { CommandLine.arguments.contains("-HedefitPreview") }

    static func install(into app: AppModel) {
        app.previewMode = true
        app.session = AuthSession(accessToken: "preview", refreshToken: "preview", expiresAt: Date().timeIntervalSince1970 + 3600, user: AuthUser(id: "preview", email: "sample@hedefit.app", emailVerified: true, isAnonymous: false))
        app.phase = .signedIn
        app.dashboard = dashboard()
        app.prefs.welcomeGuideSeen = true
        app.adaptive.checkinToday = Checkin(day: Dates.day(), energy: 8, sleepQuality: 8)
        app.challenge.hub = hub()
        app.social.summary = FriendsSummary(friends: [FriendRequest(json: ["id": "f1", "status": "accepted", "isIncoming": false, "user": ["id": "u1", "username": "mert", "displayName": "Mert"]]),
                                                       FriendRequest(json: ["id": "f2", "status": "accepted", "isIncoming": false, "user": ["id": "u2", "username": "ayse", "displayName": "Ayşe"]])], incoming: [], outgoing: [])
        app.social.leaderboard = [LeaderboardEntry(json: ["rank": 1, "weeklyXp": 420, "isCurrentUser": false, "user": ["id": "u1", "username": "mert", "displayName": "Mert"]]),
                                  LeaderboardEntry(json: ["rank": 2, "weeklyXp": 310, "isCurrentUser": true, "user": ["id": "preview", "username": "alex", "displayName": "Alex"]])]
    }

    static func dashboard() -> Dashboard {
        var p = Profile(id: "preview", displayName: "Alex", weightKg: 74, heightCm: 178, goal: "Kilo vermek", isPremium: false)
        p.age = 29; p.gender = "Erkek"; p.targetWeightKg = 68; p.targetWeeks = 12; p.environment = "Spor salonu"; p.equipment = "Tam salon"; p.username = "alex"
        var d = Dashboard(profile: p)
        d.workouts = [WorkoutExercise(id: "bench-press", name: "Barbell Bench Press", area: "Göğüs", sets: 4, reps: "8–10", restSeconds: 90),
                      WorkoutExercise(id: "v-bar-lat-pulldown", name: "Lat Pulldown", area: "Sırt", sets: 4, reps: "10–12", restSeconds: 75),
                      WorkoutExercise(id: "dumbbell-shoulder-press", name: "Dumbbell Shoulder Press", area: "Omuz", sets: 3, reps: "10–12", restSeconds: 75),
                      WorkoutExercise(id: "seated-cable-row", name: "Seated Cable Row", area: "Sırt", sets: 3, reps: "10–12", restSeconds: 75),
                      WorkoutExercise(id: "squat", name: "Barbell Back Squat", area: "Bacak", sets: 4, reps: "6–8", restSeconds: 120),
                      WorkoutExercise(id: "plank", name: "Plank", area: "Karın", sets: 3, reps: "30–45 sn", restSeconds: 45)]
        d.workoutPrograms = [WorkoutProgram(id: "p1", name: "Upper / Lower Strength", source: "custom", focusArea: "Tüm vücut", exercises: d.workouts, isActive: true, showOnHome: true),
                             WorkoutProgram(id: "p2", name: "Kişisel Atlas Programım", source: "assessment", focusArea: "Kilo vermek", exercises: Array(d.workouts.prefix(4)), isActive: false, showOnHome: true)]
        let now = Date()
        d.sessions = (0..<8).map { i in WorkoutSession(id: "s\(i)", completedAt: ISO.string(Dates.add(-i * 2 - 1, to: now)), durationSeconds: 3000 + i * 120, calories: 280 + i * 10, completedExercises: 5, totalExercises: 6, fatigue: 3, exerciseNames: ["Barbell Bench Press", "Lat Pulldown"]) }
        d.nutritionLogs = [NutritionLog(json: ["id": "n1", "logged_date": JSON(Dates.day()), "meal": "Kahvaltı", "name": "Yulaf ezmesi", "calories": 320, "protein_g": 12, "carbs_g": 54, "fat_g": 6, "grams": 80]),
                           NutritionLog(json: ["id": "n2", "logged_date": JSON(Dates.day()), "meal": "Öğle yemeği", "name": "Izgara tavuk & pilav", "calories": 640, "protein_g": 48, "carbs_g": 70, "fat_g": 14, "grams": 350])]
        d.steps = 6240; d.waterMl = 1250; d.sleepMinutes = 440; d.streakDays = 5; d.activeCalories = 240
        d.measurements = (0..<10).map { i in BodyMeasurement(date: Dates.day(Dates.add(-(9 - i) * 7, to: now)), weightKg: 80 - Double(i) * 0.6) }
        d.stepHistory = (0..<14).map { i in DailyStep(localDate: Dates.day(Dates.add(-(13 - i), to: now)), steps: 4000 + (i * 733) % 7000) }
        d.gamificationTotalXp = 1240; d.gamificationWeeklyXp = 310
        return d
    }

    static func hub() -> ChallengeHub {
        func plan(_ key: String, _ cat: String, _ tr: String, _ en: String, days: Int, task: JSON) -> JSON {
            let title: [String: JSON] = ["tr": JSON(tr), "en": JSON(en)]
            let desc: [String: JSON] = ["tr": JSON("Her gün küçük bir adım."), "en": JSON("A small step every day.")]
            let list: [JSON] = (0..<days).map { _ in task }
            let object: [String: JSON] = ["key": JSON(key), "source": JSON("catalog"), "category": JSON(cat), "difficulty": JSON("beginner"), "equipment": JSON("none"), "title": title.json, "description": desc.json, "days": .array(list)]
            return object.json
        }
        var steps = plan("steps_7", "steps", "7 Günlük Adım Challenge", "7-Day Step Challenge", days: 7, task: ["kind": "steps", "target": 8000])
        steps = steps.merging(["rewardXp": 400, "participants": 1200, "fit": "fit", "recommended": true, "minutes": .null])
        let core = plan("core_14", "pilates", "14 Gün Core", "14-Day Core", days: 14, task: ["kind": "session", "session": "core_focus", "minutes": 10]).merging(["rewardXp": 600, "participants": 340, "fit": "fit", "recommended": true])
        let active: JSON = ["id": "c1", "templateKey": "steps_7", "plan": steps, "status": "active", "startedOn": JSON(Dates.day(Dates.add(-3))), "days": [["dayIndex": 0, "localDate": JSON(Dates.day(Dates.add(-3))), "status": "completed"], ["dayIndex": 1, "localDate": JSON(Dates.day(Dates.add(-2))), "status": "completed"], ["dayIndex": 2, "localDate": JSON(Dates.day(Dates.add(-1))), "status": "completed"]]]
        return ChallengeHub(json: ["rules": [:], "templates": [steps, core], "challenges": [active]])
    }
}
#endif
