import Foundation

extension HedefitRepository {
    // MARK: Uyarlama / check-in

    func adaptivePlan(exercises: [WorkoutExercise], recentSessions3d: Int, locale: String) async throws -> AdaptiveResult {
        let items: JSON = .array(exercises.map { ["id": JSON($0.id), "name": JSON($0.name), "area": JSON($0.area), "sets": JSON($0.sets), "reps": JSON($0.reps), "restSeconds": JSON($0.restSeconds)] as JSON })
        let body: JSON = ["action": "adaptive_plan", "exercises": items, "recentSessions3d": JSON(recentSessions3d), "locale": JSON(locale), "explain": true, "localDate": JSON(Dates.day())]
        let result = AdaptiveResult(json: try await api.post("/api/workout/adapt", body).requireSuccess(trNow("Plan uyarlanamadı.", "Couldn't adapt the plan.")).json)
        lastAdaptation = result
        return result
    }

    func saveCheckin(_ checkin: Checkin) async throws -> CheckinSaveResult? {
        let response = try await api.put("/api/checkin", checkin.json())
        if response.status == 503 { return nil }
        let json = try response.requireSuccess(trNow("Check-in kaydedilemedi.", "Couldn't save the check-in.")).json
        let saved = Checkin(json: json["checkin"]) ?? Checkin(day: "", energy: 6, sleepQuality: 6)
        return CheckinSaveResult(checkin: saved, cycle: json["cycle"].isObject ? CycleState(json: json["cycle"]) : nil)
    }

    func todayCheckin() async throws -> Checkin? {
        let response = try await api.get("/api/checkin?days=1&localDate=\(Dates.day())")
        if response.status == 503 { return nil }
        return Checkin(json: try response.requireSuccess(trNow("Check-in yüklenemedi.", "Couldn't load the check-in.")).json["today"])
    }

    func wellnessSession(kind: String, minutes: Int, locale: String) async throws -> WellnessSession {
        WellnessSession(json: try await api.post("/api/workout/adapt", ["action": "wellness_session", "kind": JSON(kind), "minutes": JSON(minutes), "locale": JSON(locale), "localDate": JSON(Dates.day())]).requireSuccess(trNow("Oturum hazırlanamadı.", "Couldn't prepare the session.")).json)
    }

    private func profileObject(_ profile: Profile?) -> JSON {
        ["goal": JSON(profile?.goal ?? "Kas geliştirmek"), "environment": JSON(profile?.environment ?? "Salon"), "equipment": JSON(profile?.equipment ?? "Tam salon"), "limitations": .from(aiSafeHistory(profile?.historyAnswers ?? []))]
    }

    private func exerciseArray(_ exercises: [WorkoutExercise]) -> JSON {
        .array(exercises.map { ["id": JSON($0.id), "name": JSON($0.name), "area": JSON($0.area), "sets": JSON($0.sets), "reps": JSON($0.reps), "restSeconds": JSON($0.restSeconds)] as JSON })
    }

    private func adaptedList(_ array: JSON) -> [WorkoutExercise] {
        array.items.map { WorkoutExercise(id: $0.string("id"), name: $0.string("name"), area: $0.string("area"), sets: $0.int("sets", 3), reps: $0.string("reps", "8-12"), restSeconds: $0.int("restSeconds", 60)) }
    }

    func adaptWorkoutForReadiness(_ input: DailyReadinessInput, exercises: [WorkoutExercise], profile: Profile?, locale: String = "tr") async throws -> ReadinessAdaptation {
        let checkin: JSON = ["energy": JSON(input.energy), "sleepQuality": JSON(input.sleepQuality), "fatigue": JSON(input.fatigue), "hasSoreness": JSON(input.hasSoreness), "sorenessAreas": .from(input.sorenessAreas),
                             "discomfortLevel": JSON(input.discomfortLevel), "notes": .opt(input.notes)]
        let payload: JSON = ["action": "readiness_checkin", "checkin": checkin, "exercises": exerciseArray(exercises), "profile": profileObject(profile), "locale": JSON(locale)]
        let res = try await api.post("/api/workout/adapt", payload).requireSuccess(trNow("Hazırlık adaptasyonu yapılamadı.", "Couldn't adapt for readiness.")).json
        return ReadinessAdaptation(needsAdaptation: res.bool("needsAdaptation"), recommendedIntensity: res.string("recommendedIntensity", "normal"), explanationTr: res.string("explanationTr"), explanationEn: res.string("explanationEn"),
                                   volumeReductionPercent: res.int("volumeReductionPercent"), deloadedMuscles: res.strings("deloadedMuscles"), adaptedExercises: adaptedList(res["adaptedExercises"]), originalExercises: exercises)
    }

    func replaceWorkoutExercise(currentExerciseId: String, reason: String, exercises: [WorkoutExercise], profile: Profile?, discomfortArea: String? = nil, locale: String = "tr") async throws -> ExerciseReplacementCandidate? {
        let payload: JSON = ["action": "replace_exercise", "exerciseId": JSON(currentExerciseId), "reason": JSON(reason), "sessionExerciseIds": .from(exercises.map(\.id)), "discomfortArea": .opt(discomfortArea), "profile": profileObject(profile), "locale": JSON(locale)]
        let res = try await api.post("/api/workout/adapt", payload).requireSuccess(trNow("Hareket değiştirilemedi.", "Couldn't replace the exercise.")).json
        guard res["originalExercise"].isObject, res["replacementExercise"].isObject else { return nil }
        return ExerciseReplacementCandidate(originalExerciseId: res["originalExercise"].string("id"), originalExerciseName: res["originalExercise"].string("name"), replacementExerciseId: res["replacementExercise"].string("id"),
                                            replacementExerciseName: res["replacementExercise"].string("name"), reason: res.string("reason"), explanationTr: res.string("explanationTr"), sets: res.int("sets", 3),
                                            reps: res.string("reps", "8-12"), restSeconds: res.int("restSeconds", 60), progressionType: res.string("progressionType", "lateral"))
    }

    func adaptWorkoutPlan(trigger: String, targetMinutes: Int?, exercises: [WorkoutExercise], profile: Profile?, locale: String = "tr") async throws -> WorkoutAdaptationResult {
        let payload: JSON = ["action": "adapt_plan", "exercises": exerciseArray(exercises), "params": ["trigger": JSON(trigger), "targetMinutes": .opt(targetMinutes)], "profile": profileObject(profile), "locale": JSON(locale)]
        let res = try await api.post("/api/workout/adapt", payload).requireSuccess(trNow("Plan uyarlanamadı.", "Couldn't adapt the plan.")).json
        return WorkoutAdaptationResult(trigger: res.string("trigger", trigger), originalDurationMinutes: res.int("originalDurationMinutes", 45), adaptedDurationMinutes: res.int("adaptedDurationMinutes", 20),
                                       explanationTr: res.string("explanationTr"), changes: res.strings("changes"), adaptedExercises: adaptedList(res["adaptedExercises"]))
    }

    // MARK: Koç sohbeti

    func sendChat(messages: [ChatMessage], dashboard: Dashboard?, locale: String = "tr", workoutContext: WorkoutCoachContext? = nil) async throws -> ChatReply {
        let lastUser = messages.last(where: { $0.fromUser })?.text ?? ""
        if let answer = Self.localNutritionEvaluation(lastUser, dashboard, locale) ?? Self.localProgramEvaluation(lastUser, dashboard, locale) {
            return ChatReply(text: answer, source: "local", used: nil, limit: nil)
        }
        let bodyMessages: JSON = .array(messages.suffix(12).map { ["role": JSON($0.fromUser ? "user" : "assistant"), "text": JSON($0.text)] as JSON })
        var signals: [String: JSON] = [:]
        if let d = dashboard {
            signals["profile"] = ["age": .opt(d.profile.age), "sex": JSON(d.profile.gender), "heightCm": .opt(d.profile.heightCm), "weightKg": .opt(d.profile.weightKg), "environment": JSON(d.profile.environment),
                                  "equipment": JSON(d.profile.equipment), "assessmentAnswers": .from(aiSafeHistory(Array(d.profile.historyAnswers.prefix(30))))]
            let goal = d.profile.goal.lowercased()
            let goalType = goal.contains("yağ") ? "fatLoss" : ((goal.contains("kilo ver") || goal.contains("zayıf")) ? "lose" : ((goal.contains("kas") || goal.contains("kilo al")) ? "gain" : "maintain"))
            signals["goal"] = ["goalType": JSON(goalType), "targetWeightKg": .opt(d.profile.targetWeightKg)]
            let totals = d.nutritionLogs.reduce((c: 0, p: 0.0, cb: 0.0, f: 0.0)) { ($0.c + $1.calories, $0.p + $1.protein, $0.cb + $1.carbs, $0.f + $1.fat) }
            let today = Dates.day()
            signals["today"] = ["totals": ["calories": JSON(totals.c), "protein": JSON(totals.p), "carbs": JSON(totals.cb), "fat": JSON(totals.f)].json, "steps": JSON(d.steps), "waterMl": JSON(d.waterMl), "sleepMinutes": JSON(d.sleepMinutes),
                                "workoutCompleted": JSON(d.sessions.contains { $0.date.map { Dates.day($0) == today } ?? false }),
                                "foods": .array(d.nutritionLogs.prefix(30).map { ["meal": JSON($0.meal), "name": JSON($0.name), "calories": JSON($0.calories), "protein": JSON($0.protein), "carbs": JSON($0.carbs), "fat": JSON($0.fat)] as JSON })]
            signals["measurements"] = .array(d.measurements.suffix(30).map { ["measuredAt": JSON(String($0.date.prefix(10))), "weightKg": .opt($0.weightKg)] as JSON })
            let weekAgo = Dates.add(-7)
            signals["activity"] = ["workoutsThisWeek": JSON(d.sessions.filter { ($0.date ?? .distantPast) >= weekAgo }.count), "streakDays": JSON(d.streakDays)]
            let analysis = analyzeTraining(d.exercisePerformance)
            let bests: [JSON] = Dictionary(grouping: d.exercisePerformance, by: { $0.exerciseId ?? $0.exerciseName }).values.compactMap { performances -> JSON? in
                guard let best = performances.flatMap(\.sets).max(by: { estimatedOneRepMax(weightKg: $0.weightKg, reps: $0.reps) < estimatedOneRepMax(weightKg: $1.weightKg, reps: $1.reps) }) else { return nil }
                let estimate = estimatedOneRepMax(weightKg: best.weightKg, reps: best.reps)
                return estimate <= 0 ? nil : ["exerciseName": JSON(performances[0].exerciseName), "weightKg": .opt(best.weightKg), "reps": .opt(best.reps), "estimatedOneRepMaxKg": JSON(estimate)]
            }.prefix(12).map { $0 }
            signals["training"] = [
                "activeExercises": .array(d.workouts.prefix(20).map { ["id": JSON($0.id), "name": JSON($0.name), "area": JSON($0.area), "sets": JSON($0.sets), "reps": JSON($0.reps)] as JSON }),
                "recentSessions": .array(d.sessions.prefix(4).map { ["completedAt": JSON($0.completedAt), "exerciseNames": .from(Array($0.exerciseNames.prefix(12))), "durationMinutes": JSON($0.durationSeconds / 60), "fatigue": .opt($0.fatigue)] as JSON }),
                "recentPerformance": .array(d.exercisePerformance.prefix(20).map { p in
                    ["exerciseId": .opt(p.exerciseId), "exerciseName": JSON(p.exerciseName), "sets": .array(p.sets.map { ["weightKg": .opt($0.weightKg), "reps": .opt($0.reps), "rpe": .opt($0.rpe)] as JSON })] as JSON }),
                "weeklyVolumeKg": JSON(analysis.totalVolumeKg),
                "muscleDistribution": .array(analysis.muscleLoads.prefix(16).map { ["muscle": JSON($0.muscle), "setEquivalent": JSON($0.setEquivalent), "status": JSON(Self.levelName($0.level))] as JSON }),
                "personalRecords": .array(bests),
            ]
        }
        if let last = lastAdaptation, last.adapted { signals["wellness"] = ["adaptation": last.coachSummary] }
        var payload: [String: JSON] = ["messages": bodyMessages, "localDate": JSON(Dates.day()), "signals": signals.json, "locale": JSON(locale == "en" ? "en" : "tr")]
        if let c = workoutContext {
            var w: [String: JSON] = ["isBeginner": JSON(c.isBeginner)]
            if let v = c.exerciseId { w["exerciseId"] = JSON(v) }; if let v = c.exerciseName { w["exerciseName"] = JSON(v) }; if let v = c.muscleGroup { w["muscleGroup"] = JSON(v) }
            if let v = c.targetSets { w["targetSets"] = JSON(v) }; if let v = c.currentSet { w["currentSet"] = JSON(v) }; if let v = c.reps { w["reps"] = JSON(v) }
            if let v = c.workoutDurationMinutes { w["workoutDurationMinutes"] = JSON(v) }; if let v = c.elapsedSeconds { w["elapsedSeconds"] = JSON(v) }
            payload["workoutContext"] = w.json
        }
        let response = try await api.post("/api/chat", payload.json).requireSuccess(trNow("Fit Koç yanıt veremedi.", "Fit Coach couldn't respond.")).json
        if response.string("source") == "unavailable" { throw AppError.message(response.string("notice", trNow("Çevrimiçi Fit Koç geçici olarak kullanılamıyor.", "Online Fit Coach is temporarily unavailable."))) }
        let usage = response["usage"]
        return ChatReply(text: response.string("text").replacingOccurrences(of: "**", with: "").replacingOccurrences(of: "__", with: ""), source: response.string("source"), used: usage.intOrNil("used"), limit: usage.intOrNil("limit"),
                         actions: response["actions"].items.map(CoachAction.init(json:)))
    }

    private static func levelName(_ l: LoadLevel) -> String { switch l { case .none: return "none"; case .low: return "low"; case .balanced: return "balanced"; case .high: return "high"; case .overload: return "overload" } }

    static func localNutritionEvaluation(_ question: String, _ dashboard: Dashboard?, _ locale: String) -> String? {
        let q = question.lowercased(with: Locale(identifier: "tr_TR"))
        guard q.contains("beslenmemi değerlendir") || q.contains("beslenmem nasıl") || q.contains("review my nutrition") || q.contains("evaluate my nutrition") else { return nil }
        let en = locale == "en"
        guard let d = dashboard else { return en ? "I can't see today's nutrition data yet. Open Nutrition, add what you ate, then ask me again." : "Bugünkü beslenme verini henüz göremiyorum. Beslenme sayfasından yediklerini ekledikten sonra tekrar sor." }
        let logs = d.nutritionLogs
        if logs.isEmpty { return en ? "You haven't logged a meal today, so I can't make a reliable assessment yet. Add your meals first; even approximate portions are enough to start." : "Bugün kayıtlı öğün görünmüyor; bu yüzden güvenilir bir değerlendirme yapamam. Önce yediklerini ekle, yaklaşık porsiyon yazman başlangıç için yeterli." }
        let calories = logs.reduce(0) { $0 + $1.calories }, protein = Int(logs.reduce(0) { $0 + $1.protein }), carbs = Int(logs.reduce(0) { $0 + $1.carbs }), fat = Int(logs.reduce(0) { $0 + $1.fat })
        let goal = d.nutritionGoal, calorieGap = goal.calories - calories, proteinGap = max(goal.protein - protein, 0)
        let strongest = logs.max(by: { $0.protein < $1.protein }).flatMap { $0.protein >= 10 ? $0.name : nil }
        if en {
            let energy = calorieGap >= 0 ? "You have about \(calorieGap) kcal remaining." : "You are about \(-calorieGap) kcal over your target."
            let proteinText = proteinGap > 0 ? "Protein is \(protein) / \(goal.protein) g, leaving a \(proteinGap) g gap." : "You reached your protein target with \(protein) g."
            let next = proteinGap >= 30 ? "For the next meal, prioritize a clear protein source such as 150–200 g chicken, fish, lean meat, or a yogurt-based option." : (calorieGap > 350 ? "Your protein is close; use the remaining energy for a balanced meal with vegetables and a measured carbohydrate portion." : "Keep the rest of the day light and avoid adding calories just to fill the target.")
            return "Today's \(calories) / \(goal.calories) kcal. \(energy) \(proteinText) Carbohydrate is \(carbs) / \(goal.carbs) g and fat is \(fat) / \(goal.fat) g. \(strongest.map { "Your strongest logged protein source is \($0). " } ?? "")\(next)"
        }
        let energy = calorieGap >= 0 ? "Yaklaşık \(calorieGap) kcal hakkın kaldı." : "Hedefini yaklaşık \(-calorieGap) kcal aşmışsın."
        let proteinText = proteinGap > 0 ? "Protein \(protein) / \(goal.protein) g; \(proteinGap) g eksiğin var." : "Protein hedefini \(protein) g ile tamamlamışsın."
        let next = proteinGap >= 30 ? "Sonraki öğünde 150–200 g tavuk, balık, yağsız et veya yoğurt temelli net bir protein kaynağına öncelik ver." : (calorieGap > 350 ? "Protein hedefin yakın; kalan enerjiyi sebze ve ölçülü bir karbonhidrat porsiyonuyla dengeli tamamla." : "Günün kalanını hafif tut; yalnız hedefi doldurmak için fazladan kalori ekleme.")
        return "Bugün \(calories) / \(goal.calories) kcal aldın. \(energy) \(proteinText) Karbonhidrat \(carbs) / \(goal.carbs) g, yağ \(fat) / \(goal.fat) g. \(strongest.map { "Kayıtlarındaki en güçlü protein kaynağı \($0). " } ?? "")\(next)"
    }

    static func localProgramEvaluation(_ question: String, _ dashboard: Dashboard?, _ locale: String) -> String? {
        let q = question.lowercased(with: Locale(identifier: "tr_TR"))
        guard q.contains("antrenman programımı değerlendir") || q.contains("programımı değerlendir") || q.contains("review my workout plan") || q.contains("evaluate my workout plan") else { return nil }
        let en = locale == "en"
        guard let d = dashboard else { return en ? "I can't see your active workout plan yet." : "Aktif antrenman programını henüz göremiyorum." }
        let workouts = d.workouts
        if workouts.isEmpty { return en ? "You don't have an active plan yet. Create one first, then I can assess its movements and volume." : "Aktif programın henüz yok. Önce program oluştur; ardından hareketlerini ve hacmini değerlendirebilirim." }
        let totalSets = workouts.reduce(0) { $0 + $1.sets }
        let grouped = Dictionary(grouping: workouts, by: { $0.area.isEmpty ? (en ? "Full body" : "Tüm vücut") : $0.area })
        let areas = grouped.map { ($0.key, $0.value.reduce(0) { $0 + $1.sets }) }.sorted { $0.1 > $1.1 }.prefix(5).map { "\($0.0): \($0.1) set" }.joined(separator: " • ")
        let weekly = d.sessions.filter { ($0.date ?? .distantPast) >= Dates.add(-6) }.count
        let fatigue = d.sessions.first?.fatigue
        if en {
            let load = totalSets < 9 ? "The session volume is light; add work only if you can recover well." : (totalSets > 24 ? "The session volume is high; prioritize form and recovery before adding more." : "The session volume is in a practical range for a focused day.")
            let recovery = fatigue.map { $0 >= 4 ? "Your latest fatigue is \($0)/5, so keep the next session moderate." : "Your latest fatigue is \($0)/5, which supports normal progression." } ?? "Rate your fatigue after the next workout to personalize progression."
            return "Your active program has \(workouts.count) movements and \(totalSets) working sets. Distribution: \(areas). You completed \(weekly) workout\(weekly == 1 ? "" : "s") in the last 7 days. \(load) \(recovery)"
        }
        let load = totalSets < 9 ? "Seans hacmi hafif; toparlanman iyiyse kademeli ekleme düşünebilirsin." : (totalSets > 24 ? "Seans hacmi yüksek; yeni set eklemeden önce form ve toparlanmayı önceliklendir." : "Seans hacmi odaklı bir antrenman günü için uygun aralıkta.")
        let recovery = fatigue.map { $0 >= 4 ? "Son yorgunluk puanın \($0)/5; sonraki seansı orta zorlukta tut." : "Son yorgunluk puanın \($0)/5; normal ilerlemeye uygunsun." } ?? "Bir sonraki antrenman sonunda yorgunluğunu puanla; yük önerisi daha kişisel hâle gelir."
        return "Aktif programında \(workouts.count) hareket ve toplam \(totalSets) çalışma seti var. Dağılım: \(areas). Son 7 günde \(weekly) antrenman tamamladın. \(load) \(recovery)"
    }

    // MARK: Ekipman tanıma

    func recognizeEquipmentRaw(_ jpeg: Data) async throws -> HTTPResult {
        try await api.post("/api/equipment/recognize", ["imageDataUrl": JSON("data:image/jpeg;base64,\(jpeg.base64EncodedString())")])
    }
}
