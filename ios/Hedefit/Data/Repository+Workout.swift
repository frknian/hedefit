import Foundation

extension HedefitRepository {
    // MARK: Plan üretimi & programlar

    func generatePlan(profile: Profile, feedback: WorkoutFeedback? = nil, rotationPeriod: String = "monthly") async throws -> [WorkoutExercise] {
        var body: [String: JSON] = [
            "age": .opt(profile.age), "gender": JSON(profile.gender), "height": .opt(profile.heightCm), "weight": .opt(profile.weightKg), "environment": JSON(profile.environment),
            "equipment": JSON(profile.equipment), "goal": JSON(profile.goal), "history": .from(profile.historyAnswers), "locale": "tr",
            "rotationPeriod": JSON(rotationPeriod == "weekly" ? "weekly" : "monthly"), "localDate": JSON(Dates.day()),
        ]
        if let feedback {
            body["adaptation"] = ["difficulty": JSON(feedback.difficulty), "fatigue": JSON(feedback.fatigue), "painAreas": .from(feedback.painAreas), "note": JSON(feedback.note)]
        }
        let response = try await api.post("/api/generate-plan", body.json).requireSuccess(trNow("Antrenman programı oluşturulamadı.", "Couldn't create the workout plan.")).json
        let raw = response["workouts"]
        let workouts = WorkoutExercise.list(raw)
        if workouts.isEmpty { throw AppError.message(trNow("AI kullanılabilir bir program üretmedi.", "The AI didn't produce a usable program.")) }
        try await rest.upsert("workout_plans", ["user_id": JSON(try await requireUserId()), "workouts": raw, "updated_at": JSON(ISO.string())].json, onConflict: "user_id")
        return workouts
    }

    func saveWorkoutPlan(_ workouts: [WorkoutExercise]) async throws {
        try await rest.upsert("workout_plans", ["user_id": JSON(try await requireUserId()), "workouts": WorkoutExercise.jsonArray(workouts), "updated_at": JSON(ISO.string())].json, onConflict: "user_id")
    }

    func saveProgram(name: String, source: String, focusArea: String, workouts: [WorkoutExercise], id: String = UUID().uuidString.lowercased(), showOnHome: Bool = false, trainingDays: [WorkoutProgramDay] = []) async throws -> WorkoutProgram {
        let userId = try await requireUserId()
        try await rest.update("workout_program_collections", "user_id=eq.\(userId)&is_active=eq.true", ["is_active": false, "updated_at": JSON(ISO.string())])
        let days: JSON = .array(trainingDays.map { ["weekday": JSON($0.weekday), "title": JSON(String($0.title.prefix(60)))] as JSON })
        let row = try await rest.upsert("workout_program_collections", ["id": JSON(id), "user_id": JSON(userId), "name": JSON(String(name.trimmingCharacters(in: .whitespaces).prefix(80))), "source": JSON(source), "focus_area": JSON(focusArea),
                                                                      "exercises": WorkoutExercise.jsonArray(workouts), "training_days": days, "is_active": true, "show_on_home": JSON(showOnHome), "updated_at": JSON(ISO.string())].json, onConflict: "id")
        try await saveWorkoutPlan(workouts)
        return WorkoutProgram(json: row)
    }

    func activateProgram(_ program: WorkoutProgram) async throws -> WorkoutProgram {
        let userId = try await requireUserId()
        try await rest.update("workout_program_collections", "user_id=eq.\(userId)&is_active=eq.true", ["is_active": false, "updated_at": JSON(ISO.string())])
        try await rest.update("workout_program_collections", "id=eq.\(HTTP.encode(program.id))&user_id=eq.\(userId)", ["is_active": true, "updated_at": JSON(ISO.string())])
        try await saveWorkoutPlan(program.exercises)
        var copy = program; copy.isActive = true; return copy
    }

    func setProgramHomeVisibility(_ program: WorkoutProgram, showOnHome: Bool) async throws -> WorkoutProgram {
        let userId = try await requireUserId()
        try await rest.update("workout_program_collections", "id=eq.\(HTTP.encode(program.id))&user_id=eq.\(userId)", ["show_on_home": JSON(showOnHome), "updated_at": JSON(ISO.string())])
        var copy = program; copy.showOnHome = showOnHome; return copy
    }

    func renameProgram(_ program: WorkoutProgram, name: String) async throws -> WorkoutProgram {
        let userId = try await requireUserId()
        try await rest.update("workout_program_collections", "id=eq.\(HTTP.encode(program.id))&user_id=eq.\(userId)", ["name": JSON(name), "updated_at": JSON(ISO.string())])
        var copy = program; copy.name = name; return copy
    }

    func deleteProgram(_ program: WorkoutProgram) async throws -> WorkoutProgram? {
        let userId = try await requireUserId()
        try await rest.delete("workout_program_collections", "id=eq.\(HTTP.encode(program.id))&user_id=eq.\(userId)")
        let remaining = try await rest.select("workout_program_collections", "select=*&user_id=eq.\(userId)&order=updated_at.desc&limit=50").map(WorkoutProgram.init(json:))
        var active: WorkoutProgram?
        if program.isActive { if let first = remaining.first { active = try await activateProgram(first) } } else { active = remaining.first { $0.isActive } }
        if program.isActive && active == nil { try await saveWorkoutPlan([]) }
        return active
    }

    func scheduleWorkout(date: Date, time: String, status: String = "planned", originalDate: String? = nil, programId: String? = nil, programName: String? = nil) async throws -> WorkoutSchedule {
        let row: JSON = ["id": JSON(UUID().uuidString.lowercased()), "user_id": JSON(try await requireUserId()), "scheduled_date": JSON(Dates.day(date)), "scheduled_time": JSON(time), "status": JSON(status),
                         "original_date": .opt(originalDate), "program_id": .opt(programId), "program_name": .opt(programName.map { String($0.trimmingCharacters(in: .whitespaces).prefix(120)) }), "updated_at": JSON(ISO.string())]
        return WorkoutSchedule(json: try await rest.upsert("workout_schedule", row, onConflict: "user_id,scheduled_date"))
    }

    func deleteSchedule(id: String) async throws {
        try await rest.delete("workout_schedule", "id=eq.\(HTTP.encode(id))&user_id=eq.\(try await requireUserId())")
    }

    // MARK: Egzersiz kataloğu

    struct CatalogQuery: Sendable {
        var search = "", muscle = "", equipment = "", level = "", environment = "", muscleRole = "", force = "", mechanic = "", category = ""
        var locale = "tr"
        var owned: [String] = []
        var modality = "", subcategory = ""
    }

    func loadExerciseCatalog(search: String = "", muscle: String = "", equipment: String = "", level: String = "", environment: String = "", muscleRole: String = "", force: String = "", mechanic: String = "", category: String = "", locale: String = "tr", owned: [String] = [], modality: String = "", subcategory: String = "") async throws -> [ExerciseCatalogItem] {
        let e = HTTP.encode
        let path = "/api/exercises?limit=1000&search=\(e(search))&muscle=\(e(muscle))&equipment=\(e(equipment))&level=\(e(level))&environment=\(e(environment))&muscleRole=\(e(muscleRole))&force=\(e(force))&mechanic=\(e(mechanic))&category=\(e(category))&owned=\(e(owned.joined(separator: ",")))&modality=\(e(modality))&subcategory=\(e(subcategory))&locale=\(locale == "en" ? "en" : "tr")"
        return try await api.get(path).requireSuccess(trNow("Egzersiz kütüphanesi yüklenemedi.", "Couldn't load the exercise library.")).json["items"].items.map(ExerciseCatalogItem.init(json:))
    }

    func loadPreviousPerformance(_ exercises: [WorkoutExercise]) async -> [String: [PreviousSet]] {
        guard let userId = try? await requireUserId() else { return [:] }
        var result: [String: [PreviousSet]] = [:]
        for exercise in exercises {
            guard let log = await optionalSelect("workout_exercise_logs", "select=id&user_id=eq.\(userId)&exercise_id=eq.\(HTTP.encode(exercise.id))&order=completed_at.desc&limit=1").first else { continue }
            let sets = await optionalSelect("workout_set_logs", "select=set_number,weight_kg,reps,rpe&user_id=eq.\(userId)&exercise_log_id=eq.\(log.string("id"))&order=set_number.asc")
            result[exercise.id] = sets.map { PreviousSet(setNumber: $0.int("set_number"), weightKg: $0.doubleOrNil("weight_kg"), reps: $0.intOrNil("reps"), rpe: $0.intOrNil("rpe")) }
        }
        return result
    }

    // MARK: Antrenman kaydı

    func recordWorkout(exercises: [WorkoutExercise], sets: [WorkoutSetInput], durationSeconds: Int, calories: Int, feedback: WorkoutFeedback = WorkoutFeedback()) async throws -> WorkoutSession {
        let userId = try await requireUserId()
        let id = UUID().uuidString.lowercased(), now = ISO.string()
        let distinct = Set(sets.map(\.exerciseId)).count
        try await rest.insert("workout_sessions", ["id": JSON(id), "user_id": JSON(userId), "completed_at": JSON(now), "duration_seconds": JSON(max(durationSeconds, 1)), "calories": JSON(max(calories, 0)),
                                                    "completed_exercises": JSON(distinct), "total_exercises": JSON(max(exercises.count, 1)), "exercise_names": .from(exercises.map(\.name)),
                                                    "difficulty": JSON(feedback.difficulty), "fatigue": JSON(feedback.fatigue), "pain_areas": .from(feedback.painAreas), "feedback_note": JSON(String(feedback.note.prefix(500)))].json)
        let grouped = Dictionary(grouping: sets, by: \.exerciseId).sorted { ($0.value.first?.exerciseOrder ?? 0) < ($1.value.first?.exerciseOrder ?? 0) }
        for (index, entry) in grouped.enumerated() {
            let exercise = exercises.first { $0.id == entry.key }
            let name = exercise?.name ?? entry.value.first?.exerciseName ?? ""
            let logId = UUID().uuidString.lowercased()
            try await rest.insert("workout_exercise_logs", ["id": JSON(logId), "session_id": JSON(id), "user_id": JSON(userId), "exercise_id": JSON(entry.key), "exercise_name": JSON(name),
                                                             "exercise_key": JSON(name.lowercased().replacingOccurrences(of: " ", with: "-")), "exercise_order": JSON(index + 1),
                                                             "is_bodyweight": JSON(entry.value.allSatisfy { ($0.weightKg ?? 0) <= 0 }), "completed_at": JSON(now)].json)
            for set in entry.value {
                try await rest.insert("workout_set_logs", ["id": JSON(UUID().uuidString.lowercased()), "session_id": JSON(id), "exercise_log_id": JSON(logId), "user_id": JSON(userId), "set_number": JSON(set.setNumber),
                                                            "weight_kg": .opt(set.weightKg), "reps": .opt(set.reps), "duration_seconds": .opt(set.durationSeconds), "rpe": .opt(set.rpe),
                                                            "set_type": JSON(set.setType), "note": set.note.isEmpty ? .null : JSON(set.note)].json)
            }
        }
        return WorkoutSession(id: id, completedAt: now, durationSeconds: durationSeconds, calories: calories, completedExercises: distinct, totalExercises: exercises.count, fatigue: feedback.fatigue)
    }

    func recordManualActivity(_ input: ManualActivityInput, calories: Int) async throws -> WorkoutSession {
        guard let activity = manualActivityTypes.first(where: { $0.key == input.activityKey }) else { throw AppError.message(trNow("Geçersiz aktivite türü.", "Invalid activity type.")) }
        let duration = min(max(input.durationMinutes, 1), 600), safeCalories = min(max(calories, 1), 10_000)
        let note = String(input.notes.trimmingCharacters(in: .whitespaces).prefix(500))
        let id = UUID().uuidString.lowercased(), now = ISO.string()
        try await rest.insert("workout_sessions", ["id": JSON(id), "user_id": JSON(try await requireUserId()), "completed_at": JSON(now), "duration_seconds": JSON(duration * 60), "calories": JSON(safeCalories),
                                                    "completed_exercises": 0, "total_exercises": 1, "exercise_names": .from(["activity:\(activity.key)"]), "difficulty": "Uygun", "fatigue": .null,
                                                    "pain_areas": [], "feedback_note": note.isEmpty ? .null : JSON(note)].json)
        var session = WorkoutSession(id: id, completedAt: now, durationSeconds: duration * 60, calories: safeCalories, completedExercises: 0, totalExercises: 1)
        session.manualActivityKey = activity.key
        return session
    }

    func recordCardioSession(machineKey: String, durationSeconds: Int, calories: Int, summary: String) async throws -> WorkoutSession {
        guard cardioMachines.contains(where: { $0.key == machineKey }) else { throw AppError.message(trNow("Geçersiz kardiyo makinesi.", "Invalid cardio machine.")) }
        let duration = min(max(durationSeconds, 60), 6 * 3600), safeCalories = min(max(calories, 1), 5_000)
        let id = UUID().uuidString.lowercased(), now = ISO.string(), key = "cardio_\(machineKey)"
        let note = String(summary.trimmingCharacters(in: .whitespaces).prefix(500))
        try await rest.insert("workout_sessions", ["id": JSON(id), "user_id": JSON(try await requireUserId()), "completed_at": JSON(now), "duration_seconds": JSON(duration), "calories": JSON(safeCalories),
                                                    "completed_exercises": 1, "total_exercises": 1, "exercise_names": .from(["activity:\(key)"]), "difficulty": "Uygun", "fatigue": .null,
                                                    "pain_areas": [], "feedback_note": note.isEmpty ? .null : JSON(note)].json)
        var session = WorkoutSession(id: id, completedAt: now, durationSeconds: duration, calories: safeCalories, completedExercises: 1, totalExercises: 1)
        session.manualActivityKey = key
        return session
    }

    // MARK: Rotalar

    func routePayload(_ route: RouteSnapshot, activityType: String, title: String) -> JSON {
        let points: JSON = .array(route.points.map { p in
            ["lat": JSON(p.latitude), "lng": JSON(p.longitude), "alt": .opt(p.altitudeMeters), "time": .number(Double(p.recordedAt)), "accuracy": JSON(p.accuracyMeters)] as JSON
        })
        let perKm: Double = ["Bisiklet": 28, "Kayak": 35, "Koşu": 62, "Trail Koşusu": 62][activityType] ?? 45
        let calories = max(Int(route.distanceMeters / 1000 * perKm), 0)
        return ["id": JSON(route.id), "activity_type": JSON(activityType), "title": JSON(String(title.trimmingCharacters(in: .whitespaces).prefix(80))), "started_at": JSON(ISO.string(route.startedAt)), "ended_at": JSON(ISO.string(route.stoppedAt)),
                "duration_seconds": JSON(route.elapsedDurationSeconds), "moving_duration_seconds": JSON(route.durationSeconds), "distance_meters": JSON(route.distanceMeters), "average_pace_seconds_per_km": .opt(route.paceSecondsPerKm),
                "average_speed_kmh": JSON(route.averageSpeedKmh), "calories": JSON(calories), "status": "completed", "route_points": points]
    }

    func saveRoute(_ route: RouteSnapshot, activityType: String, title: String) async throws {
        try await saveRoutePayload(routePayload(route, activityType: activityType, title: title))
    }

    func saveRoutePayload(_ payload: JSON) async throws {
        try await rest.insert("route_activities", payload.setting("user_id", JSON(try await requireUserId())))
    }

    func deleteRoute(id: String) async throws {
        guard id.range(of: "^[0-9a-fA-F-]{36}$", options: .regularExpression) != nil else { throw AppError.message(trNow("Geçersiz rota kaydı.", "Invalid route record.")) }
        try await rest.delete("route_activities", "id=eq.\(id)&user_id=eq.\(try await requireUserId())")
    }

    // MARK: Sağlık / ölçüm

    func syncHealth(_ snapshot: HealthSnapshot) async throws {
        let userId = try await requireUserId()
        try await optionalHealthUpsert("daily_steps", ["user_id": JSON(userId), "local_date": JSON(Dates.day(snapshot.date)), "steps": JSON(snapshot.steps), "source": "healthkit", "synced_at": JSON(ISO.string())].json, "user_id,local_date")
        if snapshot.sleepMinutes > 0 {
            try await optionalHealthUpsert("sleep_logs", ["user_id": JSON(userId), "local_date": JSON(Dates.day(snapshot.date)), "minutes": JSON(snapshot.sleepMinutes), "quality": JSON(snapshot.sleepMinutes >= 420 ? "iyi" : "orta")].json, "user_id,local_date")
        }
        if let weight = snapshot.weightKg {
            try await rest.upsert("body_measurements", ["id": JSON(UUID().uuidString.lowercased()), "user_id": JSON(userId), "measured_at": JSON(Dates.day(snapshot.date)), "weight_kg": JSON(weight)].json, onConflict: "user_id,measured_at")
        }
    }

    func syncTodayDeviceSteps(_ steps: Int) async throws {
        guard steps > 0 else { return }
        try await optionalHealthUpsert("daily_steps", ["user_id": JSON(try await requireUserId()), "local_date": JSON(Dates.day()), "steps": JSON(steps), "source": "device", "synced_at": JSON(ISO.string())].json, "user_id,local_date")
    }

    func saveSleepLog(minutes: Int, quality: String = "iyi", date: Date = Date(), bedTime: String? = nil, wakeTime: String? = nil) async throws {
        try await optionalHealthUpsert("sleep_logs", ["user_id": JSON(try await requireUserId()), "local_date": JSON(Dates.day(date)), "minutes": JSON(minutes), "quality": JSON(quality), "bed_time": .opt(bedTime), "wake_time": .opt(wakeTime)].json, "user_id,local_date")
    }

    private func optionalHealthUpsert(_ table: String, _ row: JSON, _ onConflict: String) async throws {
        do { try await rest.upsert(table, row, onConflict: onConflict) }
        catch {
            let message = error.localizedDescription
            if !message.localizedCaseInsensitiveContains("Could not find the table") && !message.localizedCaseInsensitiveContains("PGRST205") { throw error }
        }
    }

    func saveBodyMeasurement(_ m: BodyMeasurement) async throws -> BodyMeasurement {
        let row: JSON = ["id": JSON(UUID().uuidString.lowercased()), "user_id": JSON(try await requireUserId()), "measured_at": JSON(m.date), "weight_kg": .opt(m.weightKg), "waist_cm": .opt(m.waistCm), "hips_cm": .opt(m.hipsCm),
                         "chest_cm": .opt(m.chestCm), "arm_cm": .opt(m.armCm), "thigh_cm": .opt(m.thighCm), "updated_at": JSON(ISO.string())]
        return BodyMeasurement(json: try await rest.upsert("body_measurements", row, onConflict: "user_id,measured_at"))
    }

    func deleteBodyMeasurement(date: String) async throws {
        let day = String(date.prefix(10))
        guard day.range(of: "^\\d{4}-\\d{2}-\\d{2}$", options: .regularExpression) != nil else { throw AppError.message(trNow("Geçersiz ölçüm tarihi.", "Invalid measurement date.")) }
        try await rest.delete("body_measurements", "measured_at=eq.\(day)&user_id=eq.\(try await requireUserId())")
    }

    func setWater(totalMl: Int, date: Date = Date()) async throws -> Int {
        let clean = min(max(totalMl, 0), 20_000)
        try await rest.upsert("water_logs", ["user_id": JSON(try await requireUserId()), "local_date": JSON(Dates.day(date)), "milliliters": JSON(clean), "updated_at": JSON(ISO.string())].json, onConflict: "user_id,local_date")
        return clean
    }
}
