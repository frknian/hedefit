import SwiftUI
import HealthKit

extension Error {
    /// Android `friendlyError` ile aynı eşlemeler.
    @MainActor var friendly: String {
        let message = localizedDescription
        func has(_ s: String) -> Bool { message.localizedCaseInsensitiveContains(s) }
        if has("Invalid login credentials") { return tr("E-posta veya şifre hatalı.", "Incorrect email or password.") }
        if has("Email not confirmed") { return tr("E-posta adresini doğrulaman gerekiyor.", "You need to verify your email address.") }
        if has("Email address") && has("invalid") { return tr("Bu e-posta adresi kabul edilmedi. Başka bir e-posta adresi dene.", "That email address was rejected. Try another one.") }
        if has("already registered") || has("already exists") { return tr("Bu e-posta adresiyle zaten bir hesap var. Giriş yapmayı dene.", "An account with this email already exists. Try signing in.") }
        if has("rate limit") || has("too many") { return tr("Çok fazla deneme yapıldı. Biraz bekleyip yeniden dene.", "Too many attempts. Wait a bit and try again.") }
        if has("Database error saving new user") || has("profiles_username") { return tr("Bu kullanıcı adı alınmış ya da kullanılamaz. Başka bir kullanıcı adı dene.", "That username is taken or unavailable. Try another.") }
        if (self as? URLError) != nil || has("network") || has("offline") || has("Internet") { return tr("İnternet bağlantısı kurulamadı.", "Couldn't connect to the internet.") }
        return message.isEmpty ? tr("Beklenmeyen bir hata oluştu.", "Something unexpected happened.") : String(message.prefix(220))
    }

    var isNetworkLike: Bool { (self as? URLError) != nil }
}

extension AppModel {
    /// Hatayı kullanıcı dostu metinle gösterir (eski `fail` yerine).
    func report(_ error: Error) { errorToast = error.friendly }

    // MARK: Uyku / su

    func saveSleep(minutes: Int, quality: String = "iyi", bedTime: String? = nil, wakeTime: String? = nil) async {
        let safe = min(max(minutes, 0), 1440)
        dashboard?.sleepMinutes = safe
        do { try await repo.saveSleepLog(minutes: safe, quality: quality, bedTime: bedTime, wakeTime: wakeTime); notify(tr("Uyku süresi kaydedildi.", "Sleep saved.")); WidgetBridge.update(self) } catch { report(error) }
    }

    // MARK: Antrenman kayıtları

    /// Detaylı antrenmanı kaydeder; ağ yoksa cihazda saklar.
    func completeWorkout(durationSeconds: Int, calories: Int, sets: [WorkoutSetInput], feedback: WorkoutFeedback, exercises: [WorkoutExercise]) async -> WorkoutSession? {
        guard !exercises.isEmpty, !sets.isEmpty, !workoutSaving else { return nil }
        workoutSaving = true; defer { workoutSaving = false }
        do {
            let session = try await repo.recordWorkout(exercises: exercises, sets: sets, durationSeconds: durationSeconds, calories: calories, feedback: feedback)
            let performances = Dictionary(grouping: sets, by: \.exerciseId).map { id, items in
                ExercisePerformance(sessionId: session.id, exerciseId: id, exerciseName: items[0].exerciseName, completedAt: session.completedAt,
                                    sets: items.sorted { $0.setNumber < $1.setNumber }.map { SetPerformance(setNumber: $0.setNumber, weightKg: $0.weightKg, reps: $0.reps, durationSeconds: $0.durationSeconds, rpe: $0.rpe) })
            }
            dashboard?.sessions.insert(session, at: 0)
            dashboard?.exercisePerformance.insert(contentsOf: performances, at: 0)
            notify(tr("Antrenman ve tüm setlerin ilerlemene kaydedildi.", "Workout and all your sets were saved to your progress."))
            WidgetBridge.update(self)
            await challenge.onActivitySaved(sessionId: session.id, minutes: durationSeconds / 60)
            if feedback.difficulty == "Zor" || feedback.painAreas.contains(where: { $0 != "Yok" }) || feedback.fatigue >= 4 { await adaptPlanFromFeedback(feedback) }
            await syncWorkoutToHealth(start: Date().addingTimeInterval(-Double(durationSeconds)), calories: Double(calories), type: .traditionalStrengthTraining)
            return session
        } catch {
            if error.isNetworkLike {
                await OfflineQueue.shared.enqueue(type: "workout", payload: OfflineQueue.workoutPayload(exercises: exercises, sets: sets, durationSeconds: durationSeconds, calories: calories, feedback: feedback))
                offlinePending = await OfflineQueue.shared.count
                notify(tr("Antrenman cihazda saklandı; bağlantı gelince otomatik eşitlenecek.", "Workout saved on device; it will sync when you're back online."))
            } else { report(error) }
            return nil
        }
    }

    func recordManualActivity(_ input: ManualActivityInput, caloriesOverride: Int? = nil) async -> Bool {
        guard !workoutSaving, let d = dashboard, let activity = manualActivityTypes.first(where: { $0.key == input.activityKey }) else { return false }
        let estimate = estimateManualActivityEnergy(activity, input: input, weightKg: d.profile.weightKg)
        let calories = (caloriesOverride ?? 0) > 0 ? caloriesOverride! : estimate.activeCalories
        workoutSaving = true; defer { workoutSaving = false }
        do {
            let session = try await repo.recordManualActivity(input, calories: calories)
            dashboard?.sessions.insert(session, at: 0)
            notify(tr("\(activity.titleTr) kaydedildi: \(calories) kcal yakıldı.", "\(activity.titleEn) saved: \(calories) kcal burned."))
            await challenge.onActivitySaved(sessionId: session.id, minutes: input.durationMinutes)
            await syncWorkoutToHealth(start: Date().addingTimeInterval(-Double(input.durationMinutes * 60)), calories: Double(calories), type: Self.healthType(for: activity.key))
            return true
        } catch { report(error); return false }
    }

    func recordCardioSession(machineKey: String, durationSeconds: Int, calories: Int, summary: String) async -> Bool {
        guard !workoutSaving else { return false }
        if durationSeconds < 60 { notify(tr("Kaydetmek için en az 1 dakika kardiyo yapmalısın.", "Do at least 1 minute of cardio to save.")); return false }
        workoutSaving = true; defer { workoutSaving = false }
        do {
            let session = try await repo.recordCardioSession(machineKey: machineKey, durationSeconds: durationSeconds, calories: calories, summary: summary)
            dashboard?.sessions.insert(session, at: 0)
            notify(tr("Kardiyo kaydedildi: \(session.calories) kcal günlük hesabına eklendi.", "Cardio saved: \(session.calories) kcal added to your day."))
            await challenge.onActivitySaved(sessionId: session.id, minutes: durationSeconds / 60)
            await syncWorkoutToHealth(start: Date().addingTimeInterval(-Double(durationSeconds)), calories: Double(session.calories), type: machineKey == "bike" ? .cycling : (machineKey == "rower" ? .rowing : (machineKey == "elliptical" ? .elliptical : (machineKey == "stepper" ? .stairClimbing : .running))))
            return true
        } catch { report(error); return false }
    }

    private func adaptPlanFromFeedback(_ feedback: WorkoutFeedback) async {
        guard let profile = dashboard?.profile else { return }
        if let workouts = try? await repo.generatePlan(profile: profile, feedback: feedback, rotationPeriod: prefs.planRotation) {
            dashboard?.workouts = workouts
            notify(tr("Fit Koç geri bildirimine göre sonraki planı uyarladı.", "Fit Coach adapted your next plan based on your feedback."))
        }
    }

    func syncWorkoutToHealth(start: Date, calories: Double, type: HKWorkoutActivityType) async {
        _ = await HealthService.shared.writeWorkout(type: type, start: start, end: Date(), calories: calories)
    }

    static func healthType(for key: String) -> HKWorkoutActivityType {
        switch key {
        case "walking": return .walking; case "running": return .running; case "cycling": return .cycling; case "swimming": return .swimming; case "strength": return .traditionalStrengthTraining
        case "yoga": return .yoga; case "football": return .soccer; case "basketball": return .basketball; case "tennis": return .tennis; case "boxing": return .boxing; case "volleyball": return .volleyball
        case "pilates": return .pilates; case "hiking": return .hiking; case "rowing": return .rowing; case "dancing": return .socialDance; case "hiit": return .highIntensityIntervalTraining; case "snowboard": return .snowboarding
        default: return .other
        }
    }

    // MARK: Rotalar

    func saveRoute(_ snapshot: RouteSnapshot, activityType: String, title: String) async {
        guard limits.routeSaving else { lock(.route); return }
        var route = RouteActivity(local: snapshot, activityType: activityType, title: title)
        dashboard?.routeActivities.removeAll { $0.id == route.id }
        dashboard?.routeActivities.insert(route, at: 0)
        notify(tr("Rota Yapılanlar'a ekleniyor…", "Adding the route to Done…"))
        do {
            try await repo.saveRoute(snapshot, activityType: activityType, title: title)
            notify(tr("Hedefit Rota kaydedildi; Yapılanlar'da görüntüleyebilirsin.", "Route saved; you can see it under Done."))
            _ = route; route.status = "completed"
            await syncWorkoutToHealth(start: snapshot.startedAt, calories: Double(route.calories), type: activityType == "Bisiklet" ? .cycling : (activityType.contains("Koş") || activityType == "Run" ? .running : .walking))
            await challenge.reconcileChallenges()
        } catch {
            await OfflineQueue.shared.enqueue(type: "route", payload: await repo.routePayload(snapshot, activityType: activityType, title: title))
            offlinePending = await OfflineQueue.shared.count
            notify(tr("Rota cihazda saklandı ve Yapılanlar'a eklendi; bağlantı gelince otomatik eşitlenecek.", "Route saved on device and added to Done; it will sync when you're online."))
        }
    }

    func deleteRoute(_ route: RouteActivity) async {
        do { try await repo.deleteRoute(id: route.id); dashboard?.routeActivities.removeAll { $0.id == route.id }; notify(tr("Rota kaydı silindi.", "Route deleted.")) } catch { report(error) }
    }

    // MARK: Profil

    func uploadAvatar(_ data: Data, mime: String) async {
        guard !avatarUploading else { return }
        avatarUploading = true; defer { avatarUploading = false }
        do {
            let (path, url) = try await repo.uploadAvatar(data, mime: mime)
            updateDashboard { $0.profile.avatarPath = path; $0.profile.avatarURL = url }
            notify(tr("Profil fotoğrafın güncellendi.", "Profile photo updated."))
        } catch { report(error) }
    }

    func saveProfile(_ update: ProfileUpdate, regeneratePlan: Bool = false) async -> Bool {
        guard !profileSaving, dashboard != nil else { return false }
        profileSaving = true; defer { profileSaving = false }
        let fallback = session?.user.email.split(separator: "@").first.map(String.init) ?? "Sporcu"
        let profile: Profile
        do { profile = try await repo.saveProfile(update, fallbackName: fallback) } catch { report(error); return false }
        dashboard?.profile = profile
        if !regeneratePlan { notify(tr("Profilin güncellendi.", "Profile updated.")); return true }
        let existing = dashboard?.workoutPrograms.first { $0.source == "assessment" }
        do {
            let workouts = try await repo.generatePlan(profile: profile, rotationPeriod: prefs.planRotation)
            let program = try await repo.saveProgram(name: "Kişisel Atlas Programım", source: "assessment", focusArea: profile.goal, workouts: workouts, id: existing?.id ?? UUID().uuidString.lowercased(), showOnHome: true)
            PlanRotationStore.markGenerated(await AuthService.shared.userId)
            if var d = dashboard { d.profile = profile; d.workouts = program.exercises; d.workoutPrograms = withActive(d.workoutPrograms, program); dashboard = d }
            notify(tr("Hareket Atlası programın ana ekranda hazır.", "Your Movement Atlas program is ready on the home screen."))
        } catch { notify(tr("Profilin kaydedildi. Program şu anda yenilenemedi: \(error.friendly)", "Profile saved. The program couldn't be refreshed right now: \(error.friendly)")) }
        return true
    }

    func withActive(_ list: [WorkoutProgram], _ active: WorkoutProgram) -> [WorkoutProgram] {
        var head = active; head.isActive = true
        return [head] + list.filter { $0.id != active.id }.map { var c = $0; c.isActive = false; return c }
    }

    func saveQuickWeight(_ kg: Double) async {
        let today = Dates.day()
        let existing = dashboard?.measurements.first { String($0.date.prefix(10)) == today }
        var m = existing ?? BodyMeasurement(date: today)
        m.date = today; m.weightKg = kg
        await saveMeasurement(m)
        _ = await HealthService.shared.writeBodyMass(kg: kg)
    }

    func saveMeasurement(_ m: BodyMeasurement) async {
        guard !measurementSaving else { return }
        measurementSaving = true; defer { measurementSaving = false }
        do {
            let saved = try await repo.saveBodyMeasurement(m)
            var list = (dashboard?.measurements ?? []).filter { String($0.date.prefix(10)) != String(saved.date.prefix(10)) }
            list.append(saved); list.sort { $0.date < $1.date }
            dashboard?.measurements = list
            if let w = saved.weightKg { dashboard?.profile.weightKg = w }
            notify(tr("Vücut ölçülerin kaydedildi.", "Body measurements saved."))
        } catch { report(error) }
    }

    func deleteMeasurement(_ m: BodyMeasurement) async {
        guard !measurementSaving else { return }
        measurementSaving = true; defer { measurementSaving = false }
        do { try await repo.deleteBodyMeasurement(date: m.date); dashboard?.measurements.removeAll { String($0.date.prefix(10)) == String(m.date.prefix(10)) }; notify(tr("Ölçüm kaydı silindi.", "Measurement deleted.")) } catch { report(error) }
    }

    // MARK: Hesap

    func resetProgress() async {
        guard !accountBusy else { return }
        accountBusy = true; defer { accountBusy = false }
        do { try await repo.resetProgress(); notify(tr("İlerleme verilerin sıfırlandı.", "Your progress was reset.")); await refreshAll() } catch { report(error) }
    }

    func freezeAccount() async {
        guard !accountBusy else { return }
        accountBusy = true; defer { accountBusy = false }
        do { try await repo.freezeAccount(); accountFrozen = true; dashboard = nil } catch { report(error) }
    }

    func deleteAccount(email: String) async {
        guard !accountBusy else { return }
        accountBusy = true; defer { accountBusy = false }
        do {
            try await repo.deleteAccount(email: email)
            await signOut()
            notify(tr("Hesabın kalıcı olarak silindi.", "Your account was permanently deleted."))
        } catch { report(error) }
    }

    // MARK: Takvim

    func scheduleWorkout(date: Date, time: String, originalDate: String? = nil, programId: String? = nil, programName: String? = nil) async {
        do {
            let entry = try await repo.scheduleWorkout(date: date, time: time, originalDate: originalDate, programId: programId, programName: programName)
            dashboard?.schedule.removeAll { $0.date == entry.date }; dashboard?.schedule.append(entry)
            notify(tr("Antrenman takvime kaydedildi.", "Workout saved to the calendar."))
            await syncCalendarReminders()
        } catch { report(error) }
    }

    func removeSchedule(_ entry: WorkoutSchedule) async {
        do { try await repo.deleteSchedule(id: entry.id); dashboard?.schedule.removeAll { $0.id == entry.id } } catch { report(error) }
    }

    func syncCalendarReminders() async {}

    /// Set yapılmadan bitirilen antrenman. Ertele: bugün "deferred", sonraki boş gün "planned". Pas geç: "rest".
    func skipTodayWorkout(postpone: Bool, programId: String?, programName: String?) async {
        let today = Date()
        let schedule = dashboard?.schedule ?? []
        let time = schedule.first { $0.date == Dates.day(today) }?.time.nilIfEmpty ?? "19:00"
        do {
            var entries = [try await repo.scheduleWorkout(date: today, time: time, status: postpone ? "deferred" : "rest", programId: programId, programName: programName)]
            if postpone {
                let taken = Set(schedule.map(\.date))
                var next = Dates.add(1, to: today)
                for _ in 0..<60 where taken.contains(Dates.day(next)) { next = Dates.add(1, to: next) }
                entries.append(try await repo.scheduleWorkout(date: next, time: time, originalDate: Dates.day(today), programId: programId, programName: programName))
            }
            dashboard?.schedule.removeAll { s in entries.contains { $0.date == s.date } }; dashboard?.schedule.append(contentsOf: entries)
            if postpone, let last = entries.last, let d = Dates.parse(last.date) {
                let label = d.formatted(.dateTime.day().month(.wide).weekday(.wide).locale(AppLang.shared.locale))
                notify(tr("Antrenman \(label) gününe eklendi.", "Workout moved to \(label)."))
            } else { notify(tr("Bugünkü antrenman pas geçildi.", "Skipped today's workout.")) }
        } catch { report(error) }
    }

    func autoDistribute(month: Date, dayTimes: [Int: String], programs: [(String, String)]) async {
        guard !programs.isEmpty, !dayTimes.isEmpty else { return }
        let today = Dates.startOfDay()
        let monthStart = Dates.calendar.date(from: Dates.calendar.dateComponents([.year, .month], from: month)) ?? month
        let start = monthStart < today ? today : monthStart
        var dates: [Date] = []
        var cursor = start
        while Dates.calendar.isDate(cursor, equalTo: monthStart, toGranularity: .month) {
            let weekday = Dates.calendar.component(.weekday, from: cursor)
            if dayTimes[weekday] != nil { dates.append(cursor) }
            cursor = Dates.add(1, to: cursor)
        }
        if dates.isEmpty { notify(tr("Bu ay için uygun gün kalmadı.", "No matching days left this month.")); return }
        do {
            var entries: [WorkoutSchedule] = []
            for (i, date) in dates.enumerated() {
                let (id, name) = programs[i % programs.count]
                entries.append(try await repo.scheduleWorkout(date: date, time: dayTimes[Dates.calendar.component(.weekday, from: date)] ?? "19:00", programId: id, programName: name))
            }
            dashboard?.schedule.removeAll { s in entries.contains { $0.date == s.date } }; dashboard?.schedule.append(contentsOf: entries)
            notify(tr("\(entries.count) güne dağıtıldı.", "Scheduled on \(entries.count) days."))
        } catch { report(error) }
    }
}

extension String { var nilIfEmpty: String? { isEmpty ? nil : self } }

extension RouteActivity {
    init(local s: RouteSnapshot, activityType: String, title: String) {
        let perKm: Double = ["Bisiklet": 28, "Kayak": 35, "Koşu": 62, "Trail Koşusu": 62][activityType] ?? (activityType.contains("Koş") ? 62 : 45)
        self.init(json: ["id": JSON(s.id), "activity_type": JSON(activityType), "title": JSON(title), "started_at": JSON(ISO.string(s.startedAt)), "ended_at": JSON(ISO.string(s.stoppedAt)), "duration_seconds": JSON(s.elapsedDurationSeconds),
                         "moving_duration_seconds": JSON(s.durationSeconds), "distance_meters": JSON(s.distanceMeters), "average_pace_seconds_per_km": .opt(s.paceSecondsPerKm), "average_speed_kmh": JSON(s.averageSpeedKmh),
                         "calories": JSON(Int(s.distanceMeters / 1000 * perKm)), "status": "completed",
                         "route_points": .array(s.points.map { ["lat": JSON($0.latitude), "lng": JSON($0.longitude), "time": .number(Double($0.recordedAt)), "accuracy": JSON($0.accuracyMeters), "alt": .opt($0.altitudeMeters)] as JSON })])
    }
}
