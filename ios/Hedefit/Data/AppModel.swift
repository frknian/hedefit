import SwiftUI
import Observation

enum AppTab: String, CaseIterable, Hashable { case home, explore, coach, nutrition, progress }

/// Tam ekran / itme (push) sayfaları.
enum Route: Hashable {
    case profile, questionnaire, notifications, calendar, library, equipmentScanner, route, goalJourney, manualActivity, wearables, cardio
    case friends, challenges, consents, aiMemory, healthPrivacy, rewards, bodyProfile, weightTracking, legal(String), settings, muscleMap, workoutHistory, routeDetail(String), routePlanner, curlGame
    case challengeDetail(String), challengeTemplate(String), coachChallenge, programs
    case programHub, quickWorkout, aiProgram, readyPrograms, customProgram, regionalPrograms
}

struct LibraryPreset: Equatable { var environment = "", equipment = "", modality = "", muscle = "", mapMode = false }

enum AuthPhase: Equatable { case loading, signedOut, signedIn, configurationError(String) }

@MainActor @Observable
final class AppModel {
    static let shared = AppModel()

    // MARK: Oturum
    var phase: AuthPhase = .loading
    var session: AuthSession?
    var authBusy = false
    var authMessage: String?
    var accountFrozen = false
    /// Açık rıza alanları olmayan eski hesap.
    var consentRequired = false
    var isGuest: Bool { session?.user.isAnonymous == true }

    // MARK: Veri
    var dashboard: Dashboard?
    var dataLoading = false
    var dataError: String?
    var prefs = AppPreferences.load() { didSet { prefs.save() } }
    var exerciseNames: [String: (tr: String, en: String)] = [:]

    // MARK: Gezinme
    var tab: AppTab = .home
    var exploreSegmentRequest: ExploreSegment?
    var libraryPreset = LibraryPreset()
    var path: [Route] = []
    var toast: String?
    var errorToast: String?
    var showCheckin = false
    var showPlans = false
    var billingRestored = false
    var lockedFeature: LockedFeature?
    var showSaveAccount = false
    var showWelcomeGuide = false
    var workoutSaving = false
    var previewMode = false
    var offlinePending = 0
    var avatarUploading = false
    var profileSaving = false
    var measurementSaving = false
    var accountBusy = false

    let repo = HedefitRepository.shared
    let workout = WorkoutModel()
    let coach = CoachModel()
    let nutrition = NutritionModel()
    var openMealComposer = false
    var planGenerating = false
    var celebrations: [CelebrationEvent] = []
    let challenge = ChallengeModel()
    let adaptive = AdaptiveModel()
    let social = SocialModel()
    var dailyStreak = DailyStreak.touch()
    var guideTried: Set<String> = Set(UserDefaults.standard.stringArray(forKey: "guide.tried") ?? [])
    var celebration: ChallengeCelebration? { challenge.celebration }

    // MARK: Hesap katmanı
    var tier: Tier {
        if isGuest { return .guest }
        let p = dashboard?.profile
        if p?.planTier == "pro" || (p?.isPremium == true && p?.planTier != "plus") { return .premium }
        if p?.planTier == "plus" { return .plus }
        return .free
    }
    var limits: TierLimits { tierLimits[tier]! }
    var units: String { prefs.unitSystem }

    // MARK: İlk kurulum sırası: kişisel bilgiler → 7 soru → tanıtım
    var needsPersonalDetails: Bool {
        guard !previewMode, phase == .signedIn, !consentRequired, !accountFrozen, let p = dashboard?.profile else { return false }
        return p.heightCm == nil || p.weightKg == nil || p.age == nil
    }
    var needsQuestionnaire: Bool {
        guard !previewMode, phase == .signedIn, !consentRequired, !accountFrozen, !needsPersonalDetails, let p = dashboard?.profile else { return false }
        return !coreQuestionsAnswered(p.historyAnswers)
    }
    var needsUsername: Bool {
        guard !previewMode, phase == .signedIn, !isGuest, !consentRequired, !accountFrozen, let p = dashboard?.profile else { return false }
        return (p.username ?? "").isEmpty && p.heightCm != nil && p.weightKg != nil && p.age != nil && coreQuestionsAnswered(p.historyAnswers)
    }
    var onboardingComplete: Bool { !needsPersonalDetails && !needsQuestionnaire }

    func lock(_ feature: LockedFeature) { lockedFeature = feature }
    func notify(_ message: String) { toast = message }
    func fail(_ error: Error) { errorToast = error.friendly }
    func push(_ route: Route) { path.append(route) }
    func select(_ target: AppTab) { path.removeAll(); tab = target }

    // MARK: Başlatma & kimlik

    func bootstrap() async {
        #if DEBUG
        if PreviewData.isEnabled { PreviewData.install(into: self); return }
        #endif
        switch await AuthService.shared.bootstrap() {
        case .configurationError(let message): phase = .configurationError(message)
        case .signedOut: phase = .signedOut
        case .signedIn(let value):
            session = value; phase = .signedIn
            await afterSignIn()
        }
    }

    func afterSignIn() async {
        session = await AuthService.shared.session
        // Rıza kapısı: iki açık rıza kayıtlı değilse (yalnızca gerçek hesaplar) ana arayüz yerine rıza ekranı.
        if let s = session, !s.user.isAnonymous, let ok = try? await AuthService.shared.hasExplicitConsents() { consentRequired = !ok }
        await refreshAll()
        showWelcomeGuide = !prefs.welcomeGuideSeen
        await NotificationService.shared.reschedule(prefs)
    }

    func signIn(email: String, password: String) async {
        guard Validation.isEmail(email), !password.isEmpty else { fail(AppError.message(tr("Geçerli bir e-posta ve parola gir.", "Enter a valid email and password."))); return }
        authBusy = true; defer { authBusy = false }
        do { session = try await AuthService.shared.signIn(email: email, password: password); phase = .signedIn; await afterSignIn() } catch { fail(error) }
    }

    func signUp(email: String, password: String, username: String, legal: LegalAcceptance) async {
        authBusy = true; defer { authBusy = false }
        do {
            switch try await AuthService.shared.signUp(email: email, password: password, username: username, legal: legal) {
            case .signedIn(let s): session = s; phase = .signedIn; await afterSignIn()
            case .verificationRequired: authMessage = tr("Doğrulama bağlantısı e-posta adresine gönderildi. Doğruladıktan sonra giriş yap.", "A verification link was sent to your email. Sign in after verifying.")
            }
        } catch { fail(error) }
    }

    func continueAsGuest(legal: LegalAcceptance) async {
        authBusy = true; defer { authBusy = false }
        do { session = try await AuthService.shared.signInAsGuest(legal); phase = .signedIn; await afterSignIn() } catch { fail(error) }
    }

    func signInWithProvider(_ provider: String, idToken: String, nonce: String?, legal: LegalAcceptance?) async {
        authBusy = true; defer { authBusy = false }
        do {
            session = try await AuthService.shared.signInWithIdToken(provider: provider, idToken: idToken, nonce: nonce, legal: legal)
            phase = .signedIn; await afterSignIn()
        } catch { fail(error) }
    }

    func signOut() async {
        await AuthService.shared.signOut()
        session = nil; dashboard = nil; phase = .signedOut; accountFrozen = false; consentRequired = false; path = []; tab = .home
        Task { await NotificationService.shared.clearAll() }
        WidgetBridge.reset()
    }

    // MARK: Veri

    func refreshAll() async {
        guard session != nil, !previewMode else { return }
        dataLoading = true; dataError = nil
        do {
            var value = try await repo.loadDashboard()
            accountFrozen = value.profile.accountStatus == "frozen"
            // Misafirin "Sporcu" adı dile göre
            value.profile.displayName = localizedDisplayName(value.profile.displayName)
            dashboard = value
            exerciseNames = Dictionary(value.exerciseCatalog.map { ($0.id, (tr: $0.name, en: $0.name)) }, uniquingKeysWith: { a, _ in a })
            WidgetBridge.update(self)
            Task { await adaptive.loadToday(); await challenge.loadHub(reconcile: true); await adaptive.loadAdaptivePlan() }
            Task { await syncHealth(silent: true) }
            if !billingRestored { billingRestored = true; BillingManager.shared.start(); Task { await BillingManager.shared.restore(app: self) } }
            Task { await repo.syncGamificationPreferences(stepGoal: prefs.stepGoal, waterGoalMl: prefs.waterGoalMl, weeklyActivityGoal: prefs.weeklyWorkoutGoal, timezone: TimeZone.current.identifier) }
        } catch { dataError = error.localizedDescription }
        dataLoading = false
    }

    func syncHealth(silent: Bool = false) async {
        guard dashboard != nil else { return }
        do {
            if await HealthService.shared.needsAuthorization() { if silent { return }; try await HealthService.shared.authorize() }
            let value = await HealthService.shared.today()
            updateDashboard { d in
                d.steps = max(d.steps, value.steps); d.activeCalories = value.activeCalories
                if value.sleepMinutes > 0 { d.sleepMinutes = value.sleepMinutes }
                if let w = value.weightKg, d.profile.weightKg == nil { d.profile.weightKg = w }
            }
            try? await repo.syncHealth(value)
            WidgetBridge.update(self)
            if !silent { notify(tr("Sağlık verileri eşitlendi.", "Health data synced.")) }
        } catch { if !silent { fail(error) } }
    }

    func addWater(_ delta: Int) async {
        guard var d = dashboard else { return }
        let old = d.waterMl, next = min(max(old + delta, 0), 20_000)
        d.waterMl = next; dashboard = d
        do { _ = try await repo.setWater(totalMl: next); WidgetBridge.update(self) } catch { dashboard?.waterMl = old; fail(error) }
    }

    func setWater(_ ml: Int) async { await addWater(ml - (dashboard?.waterMl ?? 0)) }

    // MARK: Yardımcılar

    var profile: Profile? { dashboard?.profile }
    var displayName: String { localizedDisplayName(dashboard?.profile.displayName) }
    var activeProgram: WorkoutProgram? { dashboard?.workoutPrograms.first { $0.isActive } }

    func exerciseName(_ id: String, fallback: String) -> String { fallback }

    func gamification() -> GamificationSnapshot? {
        guard let d = dashboard else { return nil }
        return GamificationEngine.snapshot(GamificationInput(
            sessions: d.sessions, routes: d.routeActivities, stepHistory: d.stepHistory, todaySteps: d.steps, todayWaterMl: d.waterMl, sleepMinutes: d.sleepMinutes, nutritionLogs: d.nutritionLogs,
            nutritionGoal: d.nutritionGoal, schedule: d.schedule, dailyStepGoal: prefs.stepGoal, dailyWaterGoalMl: prefs.waterGoalMl, weeklyActivityGoal: prefs.weeklyWorkoutGoal,
            authoritativeTotalXp: d.gamificationTotalXp, authoritativeWeeklyXp: d.gamificationWeeklyXp, unlockedAchievements: d.unlockedAchievements))
    }
}

enum Validation {
    static func isEmail(_ value: String) -> Bool {
        let v = value.trimmingCharacters(in: .whitespaces)
        return v.range(of: "^[A-Z0-9a-z._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$", options: .regularExpression) != nil
    }
    static func isUsername(_ value: String) -> Bool { value.range(of: "^[a-z0-9_]{3,20}$", options: .regularExpression) != nil }
}

extension AppModel {
    func refreshOnForeground() async {
        await syncHealth(silent: true)
        if let d = dashboard, !Dates.isSameDay(d.loadedDate, Date()) { await refreshAll() }
    }

    /// Saatte biten antrenmanı elle eklenen aktivite gibi kaydeder.
    func recordWatchWorkout(kind: String, minutes: Int, distance: Double) async {
        let key = kind == "strength" ? "strength" : (kind == "hiking" ? "hiking" : (kind == "cycling" ? "cycling" : (kind == "walking" ? "walking" : "running")))
        guard let type = manualActivityTypes.first(where: { $0.key == key }) else { return }
        let input = ManualActivityInput(activityKey: key, durationMinutes: minutes, distanceKm: distance > 0 ? distance / 1000 : nil)
        let calories = estimateManualActivityEnergy(type, input: input, weightKg: dashboard?.profile.weightKg).activeCalories
        _ = try? await repo.recordManualActivity(input, calories: calories)
        await refreshAll()
    }
}

extension AppModel {
    func track(_ event: String) { Task { await repo.trackEvent(event) } }

    func markTried(_ key: String) {
        guard !guideTried.contains(key) else { return }
        guideTried.insert(key)
        UserDefaults.standard.set(Array(guideTried), forKey: "guide.tried")
    }
}

/// Günlük giriş serisi: uygulama her gün açıldığında +1, bir gün atlanırsa 1'den başlar.
enum DailyStreak {
    @discardableResult
    static func touch(today: Date = Date()) -> Int {
        let d = UserDefaults.standard
        let last = d.string(forKey: "streak.last").flatMap(Dates.parse)
        let count = d.integer(forKey: "streak.count")
        let next: Int
        if let last, Dates.isSameDay(last, today) { next = max(count, 1) }
        else if let last, Dates.isSameDay(last, Dates.add(-1, to: today)) { next = count + 1 }
        else { next = 1 }
        if last.map({ !Dates.isSameDay($0, today) }) ?? true {
            d.set(Dates.day(today), forKey: "streak.last"); d.set(next, forKey: "streak.count"); d.set(max(next, d.integer(forKey: "streak.best")), forKey: "streak.best")
        }
        return next
    }
}

extension AppModel {
    func openLibrary(environment: String = "", equipment: String = "", modality: String = "", muscle: String = "", mapMode: Bool = false) {
        libraryPreset = LibraryPreset(environment: environment, equipment: equipment, modality: modality, muscle: muscle, mapMode: mapMode)
        push(.library)
    }
}

extension AppModel {
    /// Kopyala → değiştir → ata: opsiyonel zincirleme atamalarda çakışan erişimi (exclusivity) önler.
    func updateDashboard(_ body: (inout Dashboard) -> Void) {
        guard var copy = dashboard else { return }
        body(&copy)
        dashboard = copy
    }
}
