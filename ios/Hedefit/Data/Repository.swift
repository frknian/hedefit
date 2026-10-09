import Foundation

/// AI sağlayıcılarına giden cevap dizisinden hassas slotları boşaltır (indeksler hizalı kalır).
/// Sunucu aynı süzgeci uygular (lib/onboarding-questions.ts); burada veri hiç cihazdan çıkmasın diye ikinci kez yapılır.
let aiSensitiveHistorySlots: Set<Int> = [16, 17, 19, 20, 21]
func aiSafeHistory(_ answers: [String]) -> [String] { answers.enumerated().map { aiSensitiveHistorySlots.contains($0.offset) ? "" : $0.element } }

/// Supabase + Hedefit API üzerindeki tüm veri işlemleri (Android `HedefitRepository` karşılığı).
actor HedefitRepository {
    static let shared = HedefitRepository()
    let rest = SupabaseREST.shared
    let api = HedefitAPI.shared
    let auth = AuthService.shared
    var lastAdaptation: AdaptiveResult?

    func requireUserId() async throws -> String {
        guard let id = await auth.userId else { throw AppError.message(trNow("Oturum bulunamadı.", "No session found.")) }
        return id
    }

    func setLastAdaptation(_ value: AdaptiveResult?) { lastAdaptation = value }

    func optionalSelect(_ table: String, _ query: String) async -> [JSON] { (try? await rest.select(table, query)) ?? [] }

    // MARK: Pano

    func loadDashboard(date: Date = Date()) async throws -> Dashboard {
        let userId = try await requireUserId()
        let day = Dates.day(date)
        let from30 = Dates.day(Dates.add(-29, to: date)), from7 = Dates.day(Dates.add(-7, to: date)), to21 = Dates.day(Dates.add(21, to: date))
        let from35 = Dates.day(Dates.add(-35, to: date)), to35 = Dates.day(Dates.add(35, to: date))
        let from89 = Dates.day(Dates.add(-89, to: date))

        async let profileCall = rest.select("profiles", "select=*&id=eq.\(userId)&limit=1")
        async let planCall = optionalSelect("workout_plans", "select=workouts&user_id=eq.\(userId)&limit=1")
        async let sessionsCall = optionalSelect("workout_sessions", "select=*&user_id=eq.\(userId)&order=completed_at.desc&limit=40")
        async let nutritionCall = nutritionLogsOptional(day)
        async let goalCall = optionalSelect("nutrition_goals", "select=*&user_id=eq.\(userId)&limit=1")
        async let stepsCall = optionalSelect("daily_steps", "select=steps&user_id=eq.\(userId)&local_date=eq.\(day)&limit=1")
        async let stepHistoryCall = optionalSelect("daily_steps", "select=local_date,steps&user_id=eq.\(userId)&local_date=gte.\(from30)&local_date=lte.\(day)&order=local_date.asc")
        async let waterCall = optionalSelect("water_logs", "select=milliliters&user_id=eq.\(userId)&local_date=eq.\(day)&limit=1")
        async let sleepCall = optionalSelect("sleep_logs", "select=minutes&user_id=eq.\(userId)&local_date=eq.\(day)&limit=1")
        async let streakCall = optionalSelect("user_streaks", "select=current_streak&user_id=eq.\(userId)&limit=1")
        async let measurementsCall = optionalSelect("body_measurements", "select=*&user_id=eq.\(userId)&order=measured_at.asc&limit=90")
        async let scheduleCall = optionalSelect("workout_schedule", "select=*&user_id=eq.\(userId)&scheduled_date=gte.\(from7)&scheduled_date=lte.\(to21)&order=scheduled_date.asc")
        async let favoritesCall = optionalSelect("favorite_meals", "select=*&user_id=eq.\(userId)&order=updated_at.desc&limit=30")
        async let mealPlansCall = optionalSelect("meal_plan_items", "select=*&user_id=eq.\(userId)&planned_date=gte.\(from35)&planned_date=lte.\(to35)&order=planned_date.asc,created_at.asc&limit=500")
        async let programsCall = optionalSelect("workout_program_collections", "select=*&user_id=eq.\(userId)&order=updated_at.desc&limit=50")
        async let routesCall = optionalSelect("route_activities", "select=*&user_id=eq.\(userId)&order=started_at.desc&limit=100")
        async let exerciseLogsCall = optionalSelect("workout_exercise_logs", "select=id,session_id,exercise_id,exercise_name,completed_at&user_id=eq.\(userId)&completed_at=gte.\(from89)T00:00:00Z&order=completed_at.desc&limit=1000")
        async let setLogsCall = optionalSelect("workout_set_logs", "select=exercise_log_id,set_number,weight_kg,reps,duration_seconds,rpe,created_at&user_id=eq.\(userId)&created_at=gte.\(from89)T00:00:00Z&order=created_at.desc&limit=5000")
        async let catalogCall = Task { (try? await loadExerciseCatalog(locale: "tr")) ?? [] }.value
        async let xpCall = optionalSelect("xp_events", "select=amount,occurred_at&user_id=eq.\(userId)&order=occurred_at.desc&limit=5000")
        async let achievementsCall = optionalSelect("user_achievements", "select=achievement_id,unlocked_at&user_id=eq.\(userId)")

        let email = await auth.session?.user.email ?? ""
        let fallbackName = email.split(separator: "@").first.map(String.init).flatMap { $0.isEmpty ? nil : $0 } ?? "Sporcu"
        let profileRows = try await profileCall
        var profile: Profile
        if let row = profileRows.first { profile = Profile(json: row, userId: userId, fallbackName: fallbackName) }
        else {
            _ = try? await rest.upsert("profiles", ["id": JSON(userId), "display_name": JSON(fallbackName)].json, onConflict: "id")
            profile = Profile(json: .null, userId: userId, fallbackName: fallbackName)
        }
        // Fotoğraf bağlantısı üretilemese bile ana ekran yüklenmeli.
        if let path = profile.avatarPath { profile.avatarURL = try? await rest.signedURL(bucket: "profile-avatars", path: path) }

        let xpRows = await xpCall
        let weekStart = Dates.weekStart(date)
        let totalXp = xpRows.reduce(0) { $0 + $1.int("amount") }
        let weeklyXp = xpRows.reduce(0) { sum, row in
            guard let when = ISO.date(row.string("occurred_at")) else { return sum }
            let day = Dates.startOfDay(when)
            return day >= weekStart && day <= Dates.startOfDay(date) ? sum + row.int("amount") : sum
        }
        var unlocked: [String: String] = [:]
        for row in await achievementsCall { if let at = ISO.date(row.string("unlocked_at")) { unlocked[row.string("achievement_id")] = Dates.day(at) } }

        let steps = await stepsCall.first?.int("steps") ?? 0
        var dashboard = Dashboard(profile: profile)
        dashboard.workouts = WorkoutExercise.list(await planCall.first?["workouts"] ?? .null)
        dashboard.sessions = await sessionsCall.map(WorkoutSession.init(json:))
        dashboard.nutritionLogs = await nutritionCall
        dashboard.nutritionGoal = Self.parseNutritionGoal(await goalCall.first, profile: profile)
        dashboard.steps = steps
        dashboard.waterMl = await waterCall.first?.int("milliliters") ?? 0
        dashboard.sleepMinutes = await sleepCall.first?.int("minutes") ?? 0
        dashboard.streakDays = await streakCall.first?.int("current_streak") ?? 0
        dashboard.measurements = await measurementsCall.map(BodyMeasurement.init(json:))
        dashboard.loadedDate = date
        dashboard.activeCalories = steps / 25
        dashboard.schedule = await scheduleCall.map(WorkoutSchedule.init(json:))
        dashboard.favoriteMeals = await favoritesCall.map(FavoriteMeal.init(json:))
        dashboard.mealPlanItems = await mealPlansCall.map(MealPlanItem.init(json:))
        dashboard.workoutPrograms = await programsCall.map(WorkoutProgram.init(json:))
        dashboard.routeActivities = await routesCall.map(RouteActivity.init(json:))
        dashboard.exercisePerformance = Self.parsePerformance(await exerciseLogsCall, await setLogsCall)
        dashboard.exerciseCatalog = await catalogCall
        dashboard.stepHistory = await stepHistoryCall.map { DailyStep(localDate: $0.string("local_date"), steps: max($0.int("steps"), 0)) }
        dashboard.gamificationTotalXp = xpRows.isEmpty ? nil : totalXp
        dashboard.gamificationWeeklyXp = xpRows.isEmpty ? nil : weeklyXp
        dashboard.unlockedAchievements = unlocked
        return dashboard
    }

    private func nutritionLogsOptional(_ day: String) async -> [NutritionLog] { (try? await loadNutritionLogs(day: day)) ?? [] }

    static func parseNutritionGoal(_ item: JSON?, profile: Profile) -> NutritionGoal {
        let calories = (item?.intOrNil("calorie_target")).flatMap { $0 > 0 ? $0 : nil } ?? 2250
        if let item, item.bool("is_manual") {
            return NutritionGoal(calories: calories, protein: item.int("protein_g") > 0 ? item.int("protein_g") : 110, carbs: item.int("carbs_g") > 0 ? item.int("carbs_g") : 297, fat: item.int("fat_g") > 0 ? item.int("fat_g") : 69)
        }
        let weight = min(max(profile.weightKg ?? 75, 45), 90)
        let workoutDays = min(max(item?.intOrNil("workout_days") ?? 3, 0), 7)
        let multiplier = workoutDays == 0 ? 1.0 : (workoutDays <= 3 ? 1.2 : 1.4)
        let proteinUpper = max(min(140, Int(Double(calories) * 0.275 / 4)), 50)
        let protein = Int(min(max(weight * multiplier, 50), Double(proteinUpper)).rounded())
        let fat = Int((Double(calories) * 0.275 / 9).rounded())
        let carbs = Int((Double(max(calories - protein * 4 - fat * 9, 0)) / 4).rounded())
        return NutritionGoal(calories: calories, protein: protein, carbs: carbs, fat: fat)
    }

    static func parsePerformance(_ exerciseLogs: [JSON], _ setLogs: [JSON]) -> [ExercisePerformance] {
        var setsByLog: [String: [SetPerformance]] = [:]
        for item in setLogs {
            setsByLog[item.string("exercise_log_id"), default: []].append(SetPerformance(setNumber: item.int("set_number"), weightKg: item.doubleOrNil("weight_kg"), reps: item.intOrNil("reps"), durationSeconds: item.intOrNil("duration_seconds"), rpe: item.intOrNil("rpe")))
        }
        return exerciseLogs.map { item in
            ExercisePerformance(sessionId: item.string("session_id"), exerciseId: item.nonEmptyString("exercise_id"), exerciseName: item.string("exercise_name"), completedAt: item.string("completed_at"),
                                sets: (setsByLog[item.string("id")] ?? []).sorted { $0.setNumber < $1.setNumber })
        }
    }

    func syncGamificationPreferences(stepGoal: Int, waterGoalMl: Int, weeklyActivityGoal: Int, timezone: String) async {
        guard let userId = try? await requireUserId() else { return }
        _ = try? await rest.upsert("gamification_preferences", ["user_id": JSON(userId), "daily_step_goal": JSON(min(max(stepGoal, 1000), 100_000)), "daily_water_goal_ml": JSON(min(max(waterGoalMl, 250), 10_000)),
                                                                 "weekly_activity_goal": JSON(min(max(weeklyActivityGoal, 1), 7)), "timezone": JSON(timezone), "updated_at": JSON(ISO.string())].json, onConflict: "user_id")
    }

    // MARK: Profil & hesap

    func accountStatus() async throws -> String {
        let userId = try await requireUserId()
        return try await rest.select("profiles", "select=account_status&id=eq.\(userId)&limit=1").first?.string("account_status", "active") ?? "active"
    }

    func saveUsername(_ username: String) async throws -> String {
        let userId = try await requireUserId(), clean = username.trimmingCharacters(in: .whitespaces).lowercased()
        try await rest.upsert("profiles", ["id": JSON(userId), "username": JSON(clean), "updated_at": JSON(ISO.string())].json, onConflict: "id")
        return clean
    }

    func reactivateAccount() async throws {
        let userId = try await requireUserId()
        try await rest.update("profiles", "id=eq.\(userId)", ["account_status": "active", "frozen_at": .null])
    }

    func freezeAccount() async throws {
        let userId = try await requireUserId()
        try await rest.update("profiles", "id=eq.\(userId)", ["account_status": "frozen", "frozen_at": JSON(ISO.string())])
    }

    func resetProgress() async throws {
        try await api.post("/api/account/reset-progress", ["confirmation": "RESET_PROGRESS"]).requireSuccess(trNow("İlerleme verileri sıfırlanamadı.", "Couldn't reset progress."))
    }

    func deleteAccount(email: String) async throws {
        try await api.post("/api/account/delete", ["email": JSON(email.trimmingCharacters(in: .whitespaces)), "confirmation": "HESABIMI SİL", "confirmActiveSubscription": true]).requireSuccess(trNow("Hesap silinemedi.", "Couldn't delete the account."))
    }

    func saveProfile(_ update: ProfileUpdate, fallbackName: String) async throws -> Profile {
        let userId = try await requireUserId()
        var goal = update.goalType
        if let t = update.targetWeightKg { goal += " | hedef:\(t)" }
        if let w = update.targetWeeks { goal += " | hafta:\(w)" }
        let row: JSON = ["id": JSON(userId), "display_name": JSON(update.displayName.trimmingCharacters(in: .whitespaces)), "age": .opt(update.age), "gender": JSON(update.gender), "height_cm": .opt(update.heightCm),
                         "weight_kg": .opt(update.weightKg), "goal_text": JSON(goal), "environment": JSON(update.environment), "equipment_text": JSON(update.equipment),
                         "history_answers": .from(update.historyAnswers), "updated_at": JSON(ISO.string())]
        let saved = try await rest.upsert("profiles", row, onConflict: "id")
        return Profile(json: saved, userId: userId, fallbackName: fallbackName)
    }

    func uploadAvatar(_ bytes: Data, mime: String) async throws -> (path: String, url: String) {
        guard !bytes.isEmpty, bytes.count <= 5 * 1024 * 1024 else { throw AppError.message(trNow("Profil fotoğrafı en fazla 5 MB olabilir.", "The profile photo must be 5 MB or smaller.")) }
        guard ["image/jpeg", "image/png", "image/webp"].contains(mime) else { throw AppError.message(trNow("Profil fotoğrafı JPG, PNG veya WebP olmalı.", "The profile photo must be JPG, PNG or WebP.")) }
        let userId = try await requireUserId()
        let ext = mime == "image/png" ? "png" : (mime == "image/webp" ? "webp" : "jpg")
        let path = "\(userId)/avatar-\(UUID().uuidString.lowercased()).\(ext)"
        try await rest.upload(bucket: "profile-avatars", path: path, bytes: bytes, mime: mime)
        try await rest.update("profiles", "id=eq.\(userId)", ["avatar_path": JSON(path), "updated_at": JSON(ISO.string())])
        return (path, try await rest.signedURL(bucket: "profile-avatars", path: path))
    }

    // MARK: Aktif antrenman senkronu

    func pushActiveWorkout(snapshot: JSON, deviceId: String) async throws {
        let userId = try await requireUserId()
        try await rest.upsert("active_workout_sessions", ["user_id": JSON(userId), "snapshot": snapshot, "device_id": JSON(deviceId), "saved_at": .number(snapshot.double("savedAt")), "updated_at": JSON(ISO.string())].json, onConflict: "user_id")
    }

    func fetchActiveWorkout(ownDeviceId: String) async throws -> JSON? {
        let userId = try await requireUserId()
        guard let row = try await rest.select("active_workout_sessions", "select=snapshot,device_id&user_id=eq.\(userId)").first else { return nil }
        if row.string("device_id") == ownDeviceId { return nil }
        return row["snapshot"].isObject ? row["snapshot"] : nil
    }

    func clearActiveWorkout() async throws {
        try await rest.delete("active_workout_sessions", "user_id=eq.\(try await requireUserId())")
    }

    // MARK: Gizlilik / kişiselleştirme / analitik

    func trackEvent(_ event: String) async { _ = try? await api.post("/api/analytics", ["event": JSON(event)]) }

    func personalization() async throws -> (available: Bool, value: Personalization) {
        let json = try await api.get("/api/personalization").requireSuccess(trNow("Ayarlar yüklenemedi.", "Couldn't load settings.")).json
        return (json.bool("available", true), Personalization(json: json["personalization"]))
    }

    func updatePersonalization(adaptive: Bool? = nil, aiHealthContext: Bool? = nil, cycleOff: Bool = false) async throws -> Personalization {
        var body: [String: JSON] = [:]
        if let adaptive { body["adaptiveEnabled"] = JSON(adaptive) }
        if let aiHealthContext { body["aiHealthContextEnabled"] = JSON(aiHealthContext) }
        if cycleOff { body["cycleEnabled"] = JSON(false) }
        let json = try await api.put("/api/personalization", body.json).requireSuccess(trNow("Ayar kaydedilemedi.", "Couldn't save the setting.")).json
        return Personalization(json: json["personalization"])
    }

    func deleteAllHealthData() async throws { try await api.delete("/api/personalization").requireSuccess(trNow("Sağlık verisi silinemedi.", "Couldn't delete health data.")) }
    func deleteCycleData() async throws { try await api.delete("/api/cycle").requireSuccess(trNow("Döngü verisi silinemedi.", "Couldn't delete cycle data.")) }

    func cycleSnapshot() async throws -> CycleSnapshot? {
        let response = try await api.get("/api/cycle?localDate=\(Dates.day())")
        if response.status == 503 { return nil }
        return CycleSnapshot(json: try response.requireSuccess(trNow("Döngü bilgisi yüklenemedi.", "Couldn't load cycle info.")).json)
    }

    func saveCycleProfile(_ profile: CycleProfile) async throws -> Bool {
        let response = try await api.put("/api/cycle", profile.json())
        if response.status == 503 { return false }
        try response.requireSuccess(trNow("Döngü bilgisi kaydedilemedi.", "Couldn't save cycle info."))
        return true
    }

    func aiMemories() async throws -> [AiMemoryItem] {
        try await api.get("/api/ai/memory").requireSuccess(trNow("Koç hafızası yüklenemedi.", "Couldn't load coach memory.")).json["memories"].items.compactMap(AiMemoryItem.init(json:))
    }
    func deleteAiMemory(_ id: String) async throws { try await api.delete("/api/ai/memory?id=\(HTTP.encode(id))").requireSuccess(trNow("Not silinemedi.", "Couldn't delete the note.")) }
    func deleteAllAiMemories() async throws { try await api.delete("/api/ai/memory?all=true").requireSuccess(trNow("Hafıza silinemedi.", "Couldn't clear memory.")) }

    func extractCoachMemory(message: String, locale: String) async -> Int {
        guard let response = try? await api.post("/api/ai/memory", ["message": JSON(String(message.trimmingCharacters(in: .whitespaces).prefix(600))), "locale": JSON(locale)]), response.isSuccessful else { return 0 }
        return response.json.int("saved")
    }

    func adBonusToday(feature: String) async -> (Int, Int)? {
        guard let response = try? await api.get("/api/ads/reward?feature=\(feature)"), response.isSuccessful else { return nil }
        return (response.json.int("bonusCount"), response.json.int("maxBonus", 3))
    }
}
