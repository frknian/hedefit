import SwiftUI
import Observation

struct ChallengeCelebration: Identifiable, Equatable {
    let id = UUID()
    var title: String, xp: Int, streak: Int, finished: Bool, dayText: String
}

struct PendingChallengeDay: Codable { var userChallengeId: String; var status: String; var minutes: Int? }

/// Keşfet > Challenge durumu (Android MainViewModel challenge bölümü).
@MainActor @Observable
final class ChallengeModel {
    var hub: ChallengeHub?
    var hubBusy = false
    var hubError: String?
    var hubOffline = false
    var today: ChallengeToday?
    var todayBusy = false
    var actionBusy = false
    var celebration: ChallengeCelebration?
    var coachPreview: CoachChallengePreview?
    var coachBusy = false
    var friendProfile: FriendProfile?
    var friendProfileBusy = false
    var shareProgress: Bool?

    private var app: AppModel { AppModel.shared }
    private let defaults = UserDefaults.standard
    private var reconcileAttempts = Set<String>()

    // MARK: Önbellek / bekleyen gün

    private func cachedHub() -> ChallengeHub? {
        guard let text = defaults.string(forKey: "challenge.hub") else { return nil }
        let json = JSON.parse(text)
        return json.isObject ? ChallengeHub(json: json) : nil
    }

    private func cache(_ hub: ChallengeHub) {
        defaults.set(hub.json.text, forKey: "challenge.hub")
        let today = Date()
        let primary = hub.primaryActive(today: today)
        let state = primary?.state(today: today)
        Task { await NotificationService.shared.scheduleChallengeReminder(prefs: app.prefs, title: primary?.plan.title.text, done: state?.todayDone ?? true, streak: state?.streak ?? 0) }
    }

    var pending: PendingChallengeDay? {
        get {
            guard defaults.string(forKey: "challenge.pending_date") == Dates.day(), let data = defaults.data(forKey: "challenge.pending"), let value = try? JSONDecoder().decode(PendingChallengeDay.self, from: data) else { return nil }
            return value
        }
        set {
            if let newValue, let data = try? JSONEncoder().encode(newValue) { defaults.set(data, forKey: "challenge.pending"); defaults.set(Dates.day(), forKey: "challenge.pending_date") }
            else { defaults.removeObject(forKey: "challenge.pending"); defaults.removeObject(forKey: "challenge.pending_date") }
        }
    }

    // MARK: Hub

    func loadHub(reconcile: Bool = false) async {
        guard !hubBusy else { return }
        if hub == nil, let cached = cachedHub() { hub = cached; hubOffline = true }
        hubBusy = true; hubError = nil
        defer { hubBusy = false }
        do {
            let value = try await app.repo.loadChallengeHub(profile: app.profile, wellnessProminent: wellnessProminentNow())
            cache(value); hub = value; hubOffline = false
            if reconcile { await reconcileChallenges() }
        } catch { hubError = error.localizedDescription; hubOffline = hub != nil }
    }

    private func wellnessProminentNow() -> Bool {
        guard let p = app.profile else { return false }
        return wellnessProminent(gender: p.gender, preferredStyles: p.historyAnswers.count > 8 ? p.historyAnswers[8] : "")
    }

    private func replace(_ updated: UserChallenge) {
        guard var current = hub else { return }
        current.challenges = current.challenges.map { $0.id == updated.id ? updated : $0 }
        hub = current; cache(current)
        if today?.challenge.id == updated.id { today?.challenge = updated }
    }

    func join(key: String) async -> String? {
        guard !actionBusy else { return nil }
        actionBusy = true; defer { actionBusy = false }
        do {
            let id = try await app.repo.joinChallenge(key: key)
            await app.repo.trackEvent("challenge_joined")
            app.notify(tr("Challenge başladı. Bugünkü görevin ana ekranda.", "Challenge started. Today's task is on your Today screen."))
            await loadHub()
            return id
        } catch { app.errorToast = error.localizedDescription; return nil }
    }

    func loadToday(_ id: String) async {
        todayBusy = true
        if today?.challenge.id != id { today = nil }
        defer { todayBusy = false }
        do { today = try await app.repo.challengeToday(id: id, profile: app.profile, locale: AppLang.shared.code) } catch { app.errorToast = error.localizedDescription }
    }

    /// "Bugünkü görevi yap": hareket görevleri aktif antrenman ekranında yapılır; adım/su/öğün/check-in sunucuda doğrulanır.
    func startTask(_ id: String) async {
        guard !actionBusy else { return }
        actionBusy = true
        let result: ChallengeToday
        do { result = try await app.repo.challengeToday(id: id, profile: app.profile, locale: AppLang.shared.code) }
        catch { actionBusy = false; app.errorToast = error.localizedDescription; return }
        today = result; actionBusy = false
        guard let adaptation = result.adaptation else { return }
        let task = adaptation.task
        if task.kind == "session", let session = result.session, !session.exercises.isEmpty {
            pending = PendingChallengeDay(userChallengeId: id, status: adaptation.completionStatus, minutes: task.minutes)
            await app.repo.trackEvent("challenge_task_started")
            app.path.removeAll()
            app.workout.start(exercises: session.exercises, title: session.title)
        } else if task.kind == "workout" {
            if (app.dashboard?.workouts ?? []).isEmpty { app.notify(tr("Önce Keşfet > Programlar'dan bir program seç.", "Pick a program in Explore > Programs first.")); return }
            pending = PendingChallengeDay(userChallengeId: id, status: adaptation.completionStatus, minutes: task.minutes)
            await app.repo.trackEvent("challenge_task_started")
            app.path.removeAll()
            app.workout.start(exercises: app.dashboard?.workouts ?? [], title: app.activeProgram.map { localizedProgramName($0.name, source: $0.source) })
        } else if task.kind == "checkin" && !result.checkinDone {
            app.showCheckin = true
        } else {
            await completeDay(id, action: "complete", status: adaptation.completionStatus, minutes: task.minutes, sessionId: nil)
        }
    }

    func useRecoveryDay(_ id: String) async { await completeDay(id, action: "recovery", status: "recovery", minutes: nil, sessionId: nil) }

    func completeDay(_ id: String, action: String, status: String, minutes: Int?, sessionId: String?, silent: Bool = false) async {
        if !silent { actionBusy = true }
        defer { actionBusy = false }
        do {
            let (result, challenge) = try await app.repo.completeChallengeDay(id: id, action: action, status: status, minutes: minutes, sessionId: sessionId)
            if pending?.userChallengeId == id { pending = nil }
            if let challenge { replace(challenge) }
            if !result.alreadyDone {
                await app.repo.trackEvent(action == "recovery" ? "challenge_recovery_day" : "challenge_day_completed")
                if result.finished { await app.repo.trackEvent("challenge_completed") }
                let plan = challenge?.plan ?? hub?.challenges.first(where: { $0.id == id })?.plan
                let state = challenge?.state()
                celebration = ChallengeCelebration(title: plan?.title.text ?? "", xp: result.xp, streak: result.streak, finished: result.finished,
                                                   dayText: state.map { tr("Gün \($0.doneDays) / \($0.totalDays)", "Day \($0.doneDays) / \($0.totalDays)") } ?? "")
                await refreshGamificationQuietly()
            }
        } catch { if !silent { app.errorToast = error.localizedDescription } }
    }

    private func refreshGamificationQuietly() async {
        guard let fresh = try? await app.repo.loadDashboard() else { return }
        app.dashboard?.gamificationTotalXp = fresh.gamificationTotalXp
        app.dashboard?.gamificationWeeklyXp = fresh.gamificationWeeklyXp
        app.dashboard?.unlockedAchievements = fresh.unlockedAchievements
    }

    /// Antrenman/aktivite kaydedildiğinde: bekleyen challenge günü bu oturumla tamamlanır, yoksa doğrulanabilir görevler eşitlenir.
    func onActivitySaved(sessionId: String, minutes: Int?) async {
        if let pending { await completeDay(pending.userChallengeId, action: "complete", status: pending.status, minutes: pending.minutes ?? minutes, sessionId: sessionId) }
        else { await reconcileChallenges() }
    }

    /// Bugünkü görevi verisiyle zaten karşılanmış aktif challenge'ları sessizce tamamlar (sunucu yine doğrular).
    func reconcileChallenges() async {
        guard let hub, let data = app.dashboard else { return }
        let today = Date()
        let todaySessions = data.sessions.filter { $0.date.map { Dates.isSameDay($0, today) } ?? false }
        let sessionMinutes = todaySessions.reduce(0) { $0 + $1.durationSeconds } / 60
        let meals = data.nutritionLogs.count
        for challenge in hub.active {
            let state = challenge.state(today: today)
            if state.todayDone || state.finished { continue }
            guard state.doneDays < challenge.plan.days.count else { continue }
            let task = challenge.plan.days[state.doneDays]
            let satisfied: Bool
            switch task.kind {
            case "session", "workout": satisfied = !todaySessions.isEmpty && Double(sessionMinutes) >= Double(min(task.minutes ?? 10, 30)) * 0.6
            case "steps": satisfied = data.steps >= (task.target ?? Int.max)
            case "water": satisfied = data.waterMl >= (task.target ?? Int.max)
            case "meals": satisfied = meals >= (task.target ?? Int.max)
            case "checkin": satisfied = app.adaptive.checkinToday != nil
            default: satisfied = false
            }
            let attempt = "\(challenge.id):\(Dates.day(today)):\(task.kind):\(todaySessions.count):\(data.steps / 500):\(data.waterMl / 250):\(meals):\(app.adaptive.checkinToday != nil)"
            if satisfied && reconcileAttempts.insert(attempt).inserted {
                await completeDay(challenge.id, action: "complete", status: "completed", minutes: task.minutes, sessionId: todaySessions.first?.id, silent: true)
            }
        }
    }

    func abandon(_ id: String) async -> Bool {
        guard !actionBusy else { return false }
        actionBusy = true; defer { actionBusy = false }
        do {
            try await app.repo.abandonChallenge(id: id)
            if pending?.userChallengeId == id { pending = nil }
            await app.repo.trackEvent("challenge_abandoned")
            app.notify(tr("Challenge bırakıldı. Kazandığın XP seninle kalır.", "Challenge left. The XP you earned stays with you."))
            await loadHub(); return true
        } catch { app.errorToast = error.localizedDescription; return false }
    }

    // MARK: Fit Koç challenge

    private func recentWorkouts14d() -> Int {
        let since = Dates.add(-13)
        return (app.dashboard?.sessions ?? []).filter { ($0.date.map(Dates.startOfDay) ?? .distantPast) >= Dates.startOfDay(since) }.count
    }

    func previewCoach(_ prefs: CoachChallengePreferences) async {
        coachBusy = true; defer { coachBusy = false }
        do {
            let json = try await app.repo.coachChallenge(preferences: prefs, profile: app.profile, recentWorkouts14d: recentWorkouts14d(), start: false)
            await app.repo.trackEvent("coach_challenge_previewed")
            coachPreview = CoachChallengePreview(json: json)
        } catch { app.errorToast = error.localizedDescription }
    }

    func startCoach(_ prefs: CoachChallengePreferences) async -> String? {
        guard !coachBusy else { return nil }
        coachBusy = true; defer { coachBusy = false }
        do {
            let json = try await app.repo.coachChallenge(preferences: prefs, profile: app.profile, recentWorkouts14d: recentWorkouts14d(), start: true)
            await app.repo.trackEvent("coach_challenge_started")
            coachPreview = nil
            app.notify(tr("Fit Koç challenge'ın başladı.", "Your Fit Coach challenge has started."))
            await loadHub()
            return json.string("id")
        } catch { app.errorToast = error.localizedDescription; return nil }
    }

    func createFriendChallenge(templateKey: String, mode: String, friendIds: [String]) async -> Bool {
        guard !actionBusy else { return false }
        actionBusy = true; defer { actionBusy = false }
        do {
            _ = try await app.repo.createFriendChallenge(templateKey: templateKey, mode: mode, friendIds: friendIds, locale: AppLang.shared.code)
            await app.repo.trackEvent("friend_challenge_created")
            app.notify(tr("Meydan okuma gönderildi. Arkadaşın kabul edince ilerlemeniz burada görünür.", "Challenge sent. Your progress shows here once your friend accepts."))
            await app.social.loadChallenges(); await loadHub()
            return true
        } catch { app.errorToast = error.localizedDescription; return false }
    }

    func loadFriendProfile(_ userId: String) async {
        friendProfileBusy = true; friendProfile = nil; defer { friendProfileBusy = false }
        do { friendProfile = try await app.repo.friendProfile(userId: userId) } catch { app.errorToast = error.localizedDescription }
    }

    func loadShareProgress() async { if let value = try? await app.repo.loadShareProgress() { shareProgress = value } }
    func setShareProgress(_ value: Bool) async {
        shareProgress = value
        do { shareProgress = try await app.repo.setShareProgress(value) } catch { shareProgress = !value; app.fail(error) }
    }
}
