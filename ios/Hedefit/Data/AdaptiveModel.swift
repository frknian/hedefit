import SwiftUI
import Observation

/// Günlük check-in, uyarlanmış plan, wellness oturumları, döngü ve kişiselleştirme.
@MainActor @Observable
final class AdaptiveModel {
    var checkinToday: Checkin?
    var checkinCycle: CycleState?
    var checkinSaved = false
    var result: AdaptiveResult?
    var nutritionWellness: NutritionWellness?
    var health = HealthPrivacyState()
    var busy = false

    private var app: AppModel { AppModel.shared }

    struct HealthPrivacyState {
        var loaded = false
        var available = true
        var busy = false
        var personalization = Personalization()
        var cycle: CycleSnapshot?
    }

    func loadToday() async { if let today = try? await app.repo.todayCheckin() { checkinToday = today } }

    /// Check-in'i kaydeder. Başarısızlık hiçbir akışı engellemez; yalnızca hafif bir mesaj gösterilir.
    @discardableResult
    func save(_ checkin: Checkin) async -> CheckinSaveResult? {
        busy = true; defer { busy = false }
        do {
            guard let saved = try await app.repo.saveCheckin(checkin) else {
                app.notify(tr("Check-in şimdi kaydedilemedi. Daha sonra tekrar dene.", "Couldn't save the check-in right now. Try again later.")); return nil
            }
            checkinToday = saved.checkin; checkinCycle = saved.cycle; checkinSaved = true
            await app.repo.trackEvent("daily_checkin_completed")
            await loadAdaptivePlan()
            await app.challenge.reconcileChallenges()
            return saved
        } catch { app.fail(error); return nil }
    }

    /// Kayıtlı check-in'e göre bugünün planını uyarlar. Hata sessizdir: plan aynen geçerli kalır.
    func loadAdaptivePlan() async {
        guard let d = app.dashboard, !d.workouts.isEmpty else { return }
        let cutoff = Dates.startOfDay(Dates.add(-3))
        let recent = d.sessions.filter { ($0.date.map(Dates.startOfDay) ?? .distantPast) > cutoff }.count
        guard let value = try? await app.repo.adaptivePlan(exercises: d.workouts, recentSessions3d: recent, locale: AppLang.shared.code) else { return }
        if value.adapted { await app.repo.trackEvent("adaptive_workout_generated") }
        result = value.adapted ? value : nil
    }

    func dismissAdaptive() { result = nil }

    /// Beslenme ekranı açılınca bir kez; hata olursa kart sessizce gizlenir.
    func loadNutritionWellness() async {
        guard let d = app.dashboard else { return }
        let worked = d.sessions.contains { $0.date.map { Dates.isSameDay($0, Date()) } ?? false }
        guard let value = try? await app.repo.nutritionWellness(diet: dietFromAnswers(d.profile.historyAnswers), workedOutToday: worked, locale: AppLang.shared.code) else { return }
        if !value.tips.isEmpty { await app.repo.trackEvent("nutrition_notes_viewed") }
        nutritionWellness = value.tips.isEmpty ? nil : value
    }

    /// Wellness oturumunu sunucudan alır; hata antrenmanı başlatmaz.
    func loadWellnessSession(_ kind: WellnessKind, minutes: Int) async -> WellnessSession? {
        do {
            let session = try await app.repo.wellnessSession(kind: kind.rawValue, minutes: minutes, locale: AppLang.shared.code)
            if session.exercises.isEmpty { app.notify(tr("Şimdilik uygun bir oturum bulunamadı.", "No suitable session found right now.")); return nil }
            let event = kind == .pilatesToday ? "pilates_workout_started" : (kind == .lowImpactRecovery ? "low_impact_workout_started" : "mobility_workout_started")
            await app.repo.trackEvent(event)
            return session
        } catch { app.fail(error); return nil }
    }

    // MARK: Gizlilik & kişiselleştirme

    func loadHealthPrivacy() async {
        health.busy = true; defer { health.busy = false }
        do {
            let (available, value) = try await app.repo.personalization()
            health.available = available; health.personalization = value
            health.cycle = available ? try? await app.repo.cycleSnapshot() : nil
            health.loaded = true
        } catch { health.loaded = true; app.fail(error) }
    }

    func updatePersonalization(adaptive: Bool? = nil, aiHealthContext: Bool? = nil, cycleOff: Bool = false) async {
        health.busy = true; defer { health.busy = false }
        do { health.personalization = try await app.repo.updatePersonalization(adaptive: adaptive, aiHealthContext: aiHealthContext, cycleOff: cycleOff) } catch { app.fail(error) }
    }

    func saveCycleProfile(_ profile: CycleProfile) async -> Bool {
        do {
            let ok = try await app.repo.saveCycleProfile(profile)
            if !ok { app.notify(tr("Döngü ayarı şimdi kaydedilemedi. Daha sonra Ayarlar'dan tekrar deneyebilirsin.", "Couldn't save the cycle setting right now. You can try again later in Settings.")) }
            else { await app.repo.trackEvent(profile.trackingEnabled ? "menstrual_tracking_enabled" : "menstrual_tracking_disabled") }
            return ok
        } catch { app.fail(error); return false }
    }

    func deleteCycleData() async {
        health.busy = true; defer { health.busy = false }
        do { try await app.repo.deleteCycleData(); health.cycle = try? await app.repo.cycleSnapshot(); await updatePersonalization(cycleOff: true); app.notify(tr("Döngü verisi silindi.", "Cycle data deleted.")) } catch { app.fail(error) }
    }

    func deleteAllHealthData() async {
        health.busy = true; defer { health.busy = false }
        do { try await app.repo.deleteAllHealthData(); checkinToday = nil; result = nil; health.cycle = nil; health.personalization = Personalization(); app.notify(tr("Sağlık verilerin silindi.", "Your health data was deleted.")) } catch { app.fail(error) }
    }
}
