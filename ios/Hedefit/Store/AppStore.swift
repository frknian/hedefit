import SwiftUI
import Observation

enum AuthPhase { case loading, signedOut, signedIn, frozen, configurationError(String) }

@MainActor @Observable final class AppStore {
    var phase: AuthPhase = .loading; var session: UserSession?; var dashboard = Dashboard(); var selectedTab: AppTab = .home
    var loading = false; var message: String?; var error: String?; var chat: [ChatMessage] = []
    var settings: AppSettings { didSet { saveSettings(); Task { await NotificationService.configure(settings) }; WidgetShared.update(dashboard, settings: settings) } }
    var exercises: [ExerciseCatalogItem] = []; let route = RouteService()
    var friendsSummary = FriendsSummary(); var leaderboard: [LeaderboardEntry] = []; var feed: [FeedItem] = []
    var discoverable: Bool? = nil; var userSearchResults: [FriendUser] = []; var challenges: [Challenge] = []; var challengeProgress: [ChallengeProgressEntry] = []
    private let net = NetworkClient.shared, repository = HedefitRepository.shared

    init() { settings = (try? JSONDecoder().decode(AppSettings.self, from: UserDefaults.standard.data(forKey: "settings") ?? Data())) ?? AppSettings()
        WatchBridge.shared.activate()
        WatchBridge.shared.onWater = { [weak self] ml in Task { await self?.addWater(ml) } }
        WatchBridge.shared.onWorkout = { [weak self] workout in Task { await self?.receiveWatchWorkout(workout) } }
        WatchBridge.shared.onAsk = { [weak self] mode, text in await self?.answerWatch(mode: mode, text: text) ?? (false, "Telefonda Hedefit'i aç.") }
    }

    func bootstrap() async {
        guard !AppConfiguration.supabaseURL.isEmpty, !AppConfiguration.anonKey.isEmpty else { phase = .configurationError("iOS yapılandırmasında Supabase adresi veya anahtarı eksik."); return }
        guard var stored = await KeychainSessionStore.shared.read() else { phase = .signedOut; return }
        do { if stored.expiresAt.timeIntervalSinceNow < 90 { stored = try await net.refresh(stored); await KeychainSessionStore.shared.write(stored) }; session = stored; await net.setSession(stored); phase = .signedIn; await refresh() } catch { await KeychainSessionStore.shared.clear(); await net.setSession(nil); phase = .signedOut }
    }
    func authenticate(email: String, password: String, signUp: Bool, legal: Bool) async {
        guard !email.isEmpty, password.count >= 8 else { error = "Geçerli e-posta ve en az 8 karakterli parola gir."; return }
        if signUp && !legal { error = "KVKK Aydınlatma Metni ve Gizlilik Politikası'nı onaylamalısın."; return }
        loading = true; defer { loading = false }
        do {
            let result = signUp ? try await net.signUp(email: email, password: password) : try await net.signIn(email: email, password: password)
            guard let result else { message = "Doğrulama bağlantısı e-posta adresine gönderildi."; return }
            session = result; await net.setSession(result); await KeychainSessionStore.shared.write(result); phase = .signedIn; await refresh()
        } catch { self.error = error.localizedDescription }
    }
    func signInWithGoogle() async {
        loading = true; defer { loading = false }
        do {
            let idToken = try await GoogleAuthService.signIn()
            let result = try await net.signInWithGoogle(idToken: idToken)
            session = result; await net.setSession(result); await KeychainSessionStore.shared.write(result); phase = .signedIn; await refresh()
        } catch { self.error = error.localizedDescription }
    }
    func signOut() async { await net.signOut(); await KeychainSessionStore.shared.clear(); session = nil; dashboard = Dashboard(); phase = .signedOut }
    func refresh() async {
        guard let session else { return }; loading = true; error = nil
        do { dashboard = try await repository.dashboard(userID: session.userID); phase = dashboard.profile.accountStatus == "frozen" ? .frozen : .signedIn; if chat.isEmpty { chat = [.init(text: "Merhaba \(dashboard.profile.displayName)! Antrenman, beslenme veya ilerlemen hakkında bana bir şey sorabilirsin.", fromUser: false)] }; await syncHealth(silent: true); WidgetShared.update(dashboard, settings: settings) } catch { self.error = error.localizedDescription }
        loading = false
        await loadSocialForWatch(); await flushPendingWatchWorkouts()
    }
    func syncHealth(silent: Bool = false) async {
        guard let session else { return }; do { try await HealthService.shared.authorize(); let value = await HealthService.shared.today(); dashboard.steps = value.steps; dashboard.sleepMinutes = value.sleepMinutes; dashboard.activeCalories = value.activeCalories; if value.weightKg != nil { dashboard.profile.weightKg = value.weightKg }; await repository.saveHealth(value, userID: session.userID); WidgetShared.update(dashboard, settings: settings); if !silent { message = "Sağlık verileri eşitlendi." } } catch { if !silent { self.error = error.localizedDescription } }
    }
    func addWater(_ amount: Int) async { guard let session else { return }; let old = dashboard.waterMl; dashboard.waterMl = max(0, min(old + amount, 20_000)); do { try await repository.setWater(dashboard.waterMl, userID: session.userID); WidgetShared.update(dashboard, settings: settings) } catch { dashboard.waterMl = old; self.error = error.localizedDescription } }
    func saveWorkout(sets: [WorkoutSet], seconds: Int, calories: Int, feedback: WorkoutFeedback) async throws { guard let session else { return }; try await repository.saveWorkout(dashboard.workouts, sets: sets, duration: seconds, calories: calories, feedback: feedback, userID: session.userID); message = "Antrenman ve tüm setlerin ilerlemene kaydedildi."; await refresh() }
    /// Saatten gelen antrenman: oturum yoksa kalıcı kuyruğa alınır, giriş yapılınca kaydedilir.
    func receiveWatchWorkout(_ workout: WatchWorkoutPayload) async {
        guard session != nil else { enqueueWatchWorkout(workout); return }
        await saveWatchWorkout(workout)
    }

    private func enqueueWatchWorkout(_ workout: WatchWorkoutPayload) {
        var pending = UserDefaults.standard.array(forKey: "pendingWatchWorkouts") as? [Data] ?? []
        if let data = try? JSONEncoder().encode(workout) { pending.append(data) }
        UserDefaults.standard.set(pending, forKey: "pendingWatchWorkouts")
    }

    /// Giriş yapılınca (refresh sonunda) bekleyen saat antrenmanlarını kaydeder.
    func flushPendingWatchWorkouts() async {
        guard session != nil, let pending = UserDefaults.standard.array(forKey: "pendingWatchWorkouts") as? [Data], !pending.isEmpty else { return }
        UserDefaults.standard.removeObject(forKey: "pendingWatchWorkouts")
        for data in pending { if let workout = try? JSONDecoder().decode(WatchWorkoutPayload.self, from: data) { await saveWatchWorkout(workout) } }
    }

    /// Ağırlıkta planlı egzersizlerin setleri ayrıntılı antrenman olarak, diğerleri saatin ölçtüğü kaloriyle aktivite olarak kaydedilir.
    private func saveWatchWorkout(_ workout: WatchWorkoutPayload) async {
        let planned = Set(dashboard.workouts.map(\.id))
        let sets = workout.validSets().filter { planned.contains($0.exId) }
        if workout.kind == "strength", !sets.isEmpty {
            let converted = sets.map { WorkoutSet(exerciseID: $0.exId, exerciseName: $0.exName, exerciseOrder: $0.order, setNumber: $0.setNo, weightKg: $0.kg, reps: $0.reps, durationSeconds: nil, rpe: nil) }
            let calories = workout.calories > 0 ? workout.calories : Int(5.0 * (dashboard.profile.weightKg ?? 70) * Double(workout.minutes) / 60)
            try? await saveWorkout(sets: converted, seconds: workout.durationSec, calories: calories, feedback: WorkoutFeedback())
            return
        }
        let kind = workout.kind
        let type = manualActivities.first { $0.id == kind } ?? ManualActivityType(id: kind, tr: kind == "hiking" ? "Doğa Yürüyüşü" : "Kuvvet", en: kind == "hiking" ? "Hiking" : "Strength", icon: kind == "hiking" ? "figure.hiking" : "dumbbell.fill", met: kind == "hiking" ? 6 : 5)
        try? await addManual(type, minutes: workout.minutes, effort: 3, calories: workout.calories)
    }

    /// Saatten sesle gelen soru veya yemek kaydı.
    func answerWatch(mode: String, text: String) async -> (Bool, String) {
        guard session != nil else { return (false, "Önce telefonda giriş yap.") }
        do {
            if mode == "food" {
                let hour = Calendar.current.component(.hour, from: Date())
                let meal = hour < 11 ? "Kahvaltı" : hour < 16 ? "Öğle" : hour < 22 ? "Akşam" : "Ara Öğün"
                try await repository.addFood(text: text, grams: 100, meal: meal)
                await refresh()
                return (true, "Eklendi: \(text)")
            }
            return (true, try await repository.chat([ChatMessage(text: text, fromUser: true)], dashboard: dashboard))
        } catch { return (false, error.localizedDescription) }
    }

    func addManual(_ type: ManualActivityType, minutes: Int, effort: Int, calories: Int? = nil) async throws { guard let session else { return }; try await repository.addManualActivity(type, minutes: minutes, effort: effort, weight: dashboard.profile.weightKg ?? 70, userID: session.userID, calories: calories); message = "\(type.tr) aktivitesi kaydedildi."; await refresh() }
    func addFood(_ text: String, grams: Double, meal: String) async { guard !text.trimmingCharacters(in: .whitespaces).isEmpty else { return }; loading = true; do { try await repository.addFood(text: text, grams: grams, meal: meal); message = "Öğün kaydedildi."; await refresh() } catch { self.error = error.localizedDescription }; loading = false }
    func deleteFood(_ log: NutritionLog) async { do { try await repository.deleteFood(log.id); dashboard.nutritionLogs.removeAll { $0.id == log.id } } catch { self.error = error.localizedDescription } }
    func sendChat(_ text: String) async { guard !text.trimmingCharacters(in: .whitespaces).isEmpty else { return }; chat.append(.init(text: text, fromUser: true)); loading = true; do { let reply = try await repository.chat(chat, dashboard: dashboard); chat.append(.init(text: reply, fromUser: false)) } catch { self.error = error.localizedDescription }; loading = false }
    func searchExercises(_ query: String) async { loading = true; do { exercises = try await repository.searchExercises(query) } catch { self.error = error.localizedDescription }; loading = false }
    func recognizeEquipment(_ jpegData: Data) async throws -> EquipmentRecognitionResult { try await repository.recognizeEquipment(jpegData) }
    func activateProgram(_ program: WorkoutProgram) async {
        guard let session else { return }
        let previous = dashboard
        dashboard.workouts = program.exercises
        dashboard.workoutPrograms = dashboard.workoutPrograms.map { value in
            var copy = value; copy.isActive = value.id == program.id; return copy
        }
        do { try await repository.activateProgram(program, userID: session.userID) }
        catch { dashboard = previous; self.error = error.localizedDescription }
    }
    @discardableResult func saveRoute(type: String, title: String) async -> Bool {
        guard let value = route.stop(), let session else { return false }
        do {
            try await repository.saveRoute(value, type: type, title: title, userID: session.userID)
            message = "Rota kaydedildi; Yapılanlar'da görüntüleyebilirsin."
            await refresh()
            return true
        } catch {
            self.error = error.localizedDescription
            return false
        }
    }
    func handleDeepLink(_ url: URL) { switch url.host { case "workout": selectedTab = .workout; case "nutrition": selectedTab = .nutrition; default: selectedTab = .home } }

    // MARK: - Sosyal katman
    func loadFriendsSummary() async { do { friendsSummary = try await repository.friendsSummary() } catch { self.error = error.localizedDescription } }
    func loadDiscoverable() async { if let value = try? await repository.discoverable() { discoverable = value } }
    func setDiscoverable(_ value: Bool) async {
        let previous = discoverable
        discoverable = value
        do { discoverable = try await repository.setDiscoverable(value) } catch { discoverable = previous; self.error = error.localizedDescription }
    }
    func searchUsers(_ query: String) async { guard query.count >= 2 else { userSearchResults = []; return }; do { userSearchResults = try await repository.searchUsers(query) } catch { self.error = error.localizedDescription } }
    func sendFriendRequest(username: String) async { do { try await repository.sendFriendRequest(username: username); message = "İstek gönderildi."; await loadFriendsSummary() } catch { self.error = error.localizedDescription } }
    func respondToFriendRequest(id: String, accept: Bool) async { do { try await repository.respondToFriendRequest(id: id, accept: accept); await loadFriendsSummary(); await loadWeeklyLeaderboard() } catch { self.error = error.localizedDescription } }
    func removeFriend(id: String) async { do { try await repository.removeFriend(id: id); await loadFriendsSummary() } catch { self.error = error.localizedDescription } }
    func loadWeeklyLeaderboard() async { do { leaderboard = try await repository.weeklyLeaderboard(); pushWatchSocial() } catch { self.error = error.localizedDescription } }
    func loadFriendFeed() async { do { feed = try await repository.friendActivityFeed() } catch { self.error = error.localizedDescription } }
    func loadChallenges() async { do { challenges = try await repository.challenges(); pushWatchSocial() } catch { self.error = error.localizedDescription } }
    /// Hata göstermeden sosyal veriyi yükler (misafir hesaplarda sosyal özellik olmayabilir).
    private func loadSocialForWatch() async {
        if let rows = try? await repository.weeklyLeaderboard() { leaderboard = rows }
        if let rows = try? await repository.challenges() { challenges = rows }
        pushWatchSocial()
    }
    /// Saatteki sıralama sayfası için sosyal özeti hazırlar ve özetle birlikte gönderir.
    private func pushWatchSocial() {
        let me = leaderboard.first(where: \.isCurrentUser)
        WatchBridge.shared.social = WatchSocial(rank: me?.rank ?? 0, total: leaderboard.count, xp: me?.weeklyXp ?? 0, leaders: leaderboard.prefix(3).map { WatchLeader(name: $0.user.displayName ?? $0.user.username ?? "?", xp: $0.weeklyXp, me: $0.isCurrentUser) }, challenge: challenges.first(where: { $0.myStatus == "joined" })?.title ?? "")
        WatchBridge.shared.push(dashboard, settings: settings, social: WatchBridge.shared.social)
    }
    func createChallenge(title: String, metric: String, targetValue: Double, days: Int, friendIds: [String]) async { do { try await repository.createChallenge(title: title, metric: metric, targetValue: targetValue, days: days, friendIds: friendIds); await loadChallenges() } catch { self.error = error.localizedDescription } }
    func respondToChallengeInvite(id: String, accept: Bool) async { do { try await repository.respondToChallenge(id: id, accept: accept); await loadChallenges() } catch { self.error = error.localizedDescription } }
    func leaveOrCancelChallenge(id: String) async { do { try await repository.leaveOrCancelChallenge(id: id); await loadChallenges() } catch { self.error = error.localizedDescription } }
    func loadChallengeProgress(id: String) async { do { challengeProgress = try await repository.challengeProgress(id: id) } catch { self.error = error.localizedDescription } }
    private func saveSettings() { if let data = try? JSONEncoder().encode(settings) { UserDefaults.standard.set(data, forKey: "settings") } }
}
