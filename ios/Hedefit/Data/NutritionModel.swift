import SwiftUI
import Observation

@MainActor @Observable
final class NutritionModel {
    var busy = false
    var viewingDate = Date()
    var viewingLogs: [NutritionLog] = []
    var history: [NutritionLog] = []
    var loadedMonths: Set<String> = []
    var dateLoading = false
    var foodSearchBusy = false
    var foodResults: [FoodSearchItem] = []
    var foodQuery: String?
    var photoBusy = false
    /// Fotoğraf ya da metinden gelen, kaydedilmeyi bekleyen tahminler.
    var reviewItems: [NutritionEstimate] = []
    /// photo | text
    var reviewSource = "photo"
    var reviewMeal: String?

    private var app: AppModel { AppModel.shared }
    private var monthsInFlight: Set<String> = []
    private var dateTask: Task<Void, Never>?
    static let mealTypes = ["Kahvaltı", "Öğle yemeği", "Akşam yemeği", "Atıştırmalık"]

    var isToday: Bool { Dates.isSameDay(viewingDate, Date()) }
    var logs: [NutritionLog] { isToday ? (app.dashboard?.nutritionLogs ?? []) : viewingLogs }

    func syncToday() { viewingDate = Date() }

    // MARK: Gün / ay yükleme

    private func monthKey(_ date: Date) -> String { String(Dates.day(date).prefix(7)) }

    func selectDate(_ date: Date) {
        let today = Date()
        if date > today { return }
        let limits = app.limits
        let age = Dates.epochDay(today) - Dates.epochDay(date)
        if age >= limits.historyDays { app.lock(.history); return }
        dateTask?.cancel()
        viewingDate = date
        let known: [NutritionLog]? = Dates.isSameDay(date, today) ? app.dashboard?.nutritionLogs : (loadedMonths.contains(monthKey(date)) ? history.filter { $0.date.prefix(10) == Dates.day(date) } : nil)
        viewingLogs = known ?? []
        dateLoading = known == nil
        Task { await loadMonth(date) }
        guard known == nil else { return }
        dateTask = Task {
            do {
                let result = try await app.repo.loadNutritionLogs(day: Dates.day(date))
                guard Dates.isSameDay(viewingDate, date) else { return }
                viewingLogs = result; history = merge(result, history); dateLoading = false
            } catch { dateLoading = false; app.fail(error) }
        }
    }

    func loadMonth(_ date: Date) async {
        let key = monthKey(date)
        if previewGuard() { return }
        let today = Date()
        let start = Dates.calendar.date(from: Dates.calendar.dateComponents([.year, .month], from: date)) ?? date
        guard start <= today, !loadedMonths.contains(key), monthsInFlight.insert(key).inserted else { return }
        let end = min(Dates.add(-1, to: Dates.calendar.date(byAdding: .month, value: 1, to: start) ?? date), today)
        defer { monthsInFlight.remove(key) }
        do {
            let result = try await app.repo.loadNutritionHistory(from: start, to: end)
            history = merge(result, history); loadedMonths.insert(key)
            if !isToday && monthKey(viewingDate) == key && !dateLoading { viewingLogs = result.filter { $0.date.prefix(10) == Dates.day(viewingDate) } }
        } catch { app.fail(error) }
    }

    private func previewGuard() -> Bool { app.previewMode }

    private func merge(_ a: [NutritionLog], _ b: [NutritionLog]) -> [NutritionLog] {
        var seen = Set<String>()
        return (a + b).filter { seen.insert($0.id).inserted }
    }

    func loadProgressHistory() async {
        guard !app.previewMode else { return }
        let to = Date(), from = Dates.add(-7 * 7, to: Dates.weekStart(to))
        if let logs = try? await app.repo.loadNutritionHistory(from: from, to: to) { history = merge(logs, history) }
    }

    // MARK: Ekleme

    private func prepend(_ log: NutritionLog) {
        app.updateDashboard { $0.nutritionLogs.insert(log, at: 0) }
        if !isToday { return }
        history.insert(log, at: 0)
        WidgetBridge.update(app)
    }

    func searchFoods(_ query: String) async {
        let q = query.trimmingCharacters(in: .whitespaces)
        guard q.count >= 2, !foodSearchBusy else { return }
        foodSearchBusy = true; foodQuery = nil; foodResults = []
        defer { foodSearchBusy = false }
        do { foodResults = try await app.repo.searchFoods(q, locale: AppLang.shared.code); foodQuery = q } catch { app.fail(error) }
    }

    func addCatalogFood(_ food: FoodSearchItem, grams: Double, meal: String) async {
        guard app.canAddMeals(), !busy else { return }
        busy = true; defer { busy = false }
        do {
            let log = try await app.repo.addCatalogFood(food, grams: grams, meal: meal)
            prepend(log); app.notify(tr("\(log.name) eklendi.", "\(log.name) added.")); await app.challenge.reconcileChallenges()
        } catch {
            if error.isNetworkLike {
                let payload = await app.repo.catalogFoodPayload(food, grams: grams, meal: meal)
                await OfflineQueue.shared.enqueue(type: "nutrition", payload: payload)
                app.offlinePending = await OfflineQueue.shared.count
                let r = grams / 100
                var local = NutritionLog(json: ["id": JSON("offline-\(UUID().uuidString)"), "logged_date": JSON(Dates.day()), "meal": JSON(meal), "name": JSON(food.name), "calories": JSON(Int(Double(food.calories) * r)),
                                                "protein_g": JSON(food.protein * r), "carbs_g": JSON(food.carbs * r), "fat_g": JSON(food.fat * r), "grams": JSON(grams), "fiber_g": JSON(food.fiber * r)])
                local.sugar = food.sugar * r
                prepend(local)
                app.notify(tr("Öğün çevrimdışı kaydedildi; bağlantı gelince eşitlenecek.", "Meal saved offline; it will sync when you're back online."))
            } else { app.fail(error) }
        }
    }

    /// Serbest metin: tek besinse hemen kaydeder (güven düşükse incelemeye gönderir); öğün cümlesi ise ayrıştırıp inceleme gösterir.
    func addWithAI(food: String, grams: Double, meal: String) async {
        guard app.canAddMeals(), !busy else { return }
        busy = true; defer { busy = false }
        if Self.looksLikeWholeMeal(food) {
            do { reviewItems = try await app.repo.parseMealText(food); reviewSource = "text"; reviewMeal = meal } catch { app.fail(error) }
            return
        }
        do {
            let estimate = try await app.repo.estimateNutrition(food: food, grams: grams)
            if estimate.needsConfirmation { reviewItems = [estimate]; reviewSource = "text"; reviewMeal = meal; return }
            let log = try await app.repo.addNutrition(estimate, meal: meal)
            prepend(log); app.notify(tr("\(log.name) öğün günlüğüne eklendi.", "\(log.name) added to your food log."))
            await app.challenge.reconcileChallenges()
        } catch { app.fail(error) }
    }

    static func looksLikeWholeMeal(_ text: String) -> Bool {
        text.trimmingCharacters(in: .whitespaces).range(of: "[,;+\\n]|\\s(ve|ile|and|with)\\s|\\s\\d", options: [.regularExpression, .caseInsensitive]) != nil
    }

    func analyzePhoto(_ jpeg: Data) async {
        guard app.canAddMeals(), !photoBusy else { return }
        photoBusy = true; reviewItems = []; defer { photoBusy = false }
        do { reviewItems = try await app.repo.analyzeNutritionPhoto(jpeg); reviewSource = "photo"; reviewMeal = nil }
        catch {
            let message = error.friendly
            app.errorToast = message
            if message.localizedCaseInsensitiveContains("limit") || message.localizedCaseInsensitiveContains("hak") { app.lock(.photoMeal) }
        }
    }

    func clearReview() { reviewItems = []; reviewSource = "photo"; reviewMeal = nil }

    func saveReview(_ items: [NutritionEstimate], meal: String) async {
        guard app.canAddMeals(items.count), !busy, !items.isEmpty else { return }
        busy = true; defer { busy = false }
        let source = reviewSource
        do {
            let logs = try await app.repo.savePhotoNutrition(items, meal: meal, inputMethod: source == "text" ? "natural_language" : "photo")
            clearReview()
            app.updateDashboard { $0.nutritionLogs.insert(contentsOf: logs, at: 0) }
            if isToday { history.insert(contentsOf: logs, at: 0) }
            app.notify(source == "text" ? tr("\(logs.count) besin öğüne eklendi.", "\(logs.count) foods added.") : tr("Fotoğraftaki \(logs.count) besin öğüne eklendi.", "\(logs.count) foods from the photo added."))
            WidgetBridge.update(app); await app.challenge.reconcileChallenges()
        } catch { app.fail(error) }
    }

    func remove(_ log: NutritionLog) async {
        guard !busy else { return }
        busy = true; defer { busy = false }
        do {
            if !log.id.hasPrefix("offline-") { try await app.repo.removeNutritionLog(log.id) }
            app.updateDashboard { $0.nutritionLogs.removeAll { $0.id == log.id } }
            viewingLogs.removeAll { $0.id == log.id }; history.removeAll { $0.id == log.id }
            app.notify(tr("\(log.name) kaldırıldı.", "\(log.name) removed.")); WidgetBridge.update(app)
        } catch { app.fail(error) }
    }

    func update(_ log: NutritionLog, grams: Double, meal: String) async {
        guard !busy, !log.id.hasPrefix("offline-") else { return }
        busy = true; defer { busy = false }
        do {
            let updated = try await app.repo.updateNutritionLog(log, grams: grams, meal: meal)
            app.updateDashboard { d in d.nutritionLogs = d.nutritionLogs.map { $0.id == updated.id ? updated : $0 } }
            viewingLogs = viewingLogs.map { $0.id == updated.id ? updated : $0 }; history = history.map { $0.id == updated.id ? updated : $0 }
            app.notify(updated.meal == log.meal ? tr("\(log.name) güncellendi.", "\(log.name) updated.") : tr("\(log.name), \(updated.meal) öğününe taşındı.", "\(log.name) moved to \(updated.meal)."))
        } catch { app.fail(error) }
    }

    func addFavorite(_ log: NutritionLog) async {
        do {
            let fav = try await app.repo.addFavorite(log)
            app.updateDashboard { d in d.favoriteMeals.removeAll { $0.id == fav.id }; d.favoriteMeals.insert(fav, at: 0) }
            app.notify(tr("Öğün favorilere eklendi.", "Meal added to favorites."))
        } catch { app.fail(error) }
    }

    func removeFavorite(_ id: String) async {
        do { try await app.repo.removeFavorite(id); app.updateDashboard { $0.favoriteMeals.removeAll { $0.id == id } } } catch { app.fail(error) }
    }

    func repeatFavorite(_ fav: FavoriteMeal) async {
        guard app.canAddMeals() else { return }
        busy = true; defer { busy = false }
        do { let log = try await app.repo.repeatFavorite(fav); prepend(log); app.notify(tr("Favori öğün tekrar eklendi.", "Favorite meal added again.")) } catch { app.fail(error) }
    }

    // MARK: Haftalık plan

    func addPlanItem(_ food: FoodSearchItem, grams: Double, date: Date, mealType: String) async {
        guard app.require(.mealPlanner, allowed: { $0.mealPlanner }), !busy else { return }
        busy = true; defer { busy = false }
        do {
            let item = try await app.repo.addMealPlanItem(food, grams: grams, date: date, mealType: mealType)
            app.updateDashboard { $0.mealPlanItems = ($0.mealPlanItems + [item]).sorted { $0.plannedDate < $1.plannedDate } }
            app.notify(tr("Öğün haftalık plana eklendi.", "Meal added to the weekly plan."))
        } catch { app.fail(error) }
    }

    func togglePlanItem(_ item: MealPlanItem, completed: Bool) async {
        guard !busy else { return }
        busy = true; defer { busy = false }
        app.updateDashboard { d in d.mealPlanItems = d.mealPlanItems.map { var c = $0; if c.id == item.id { c.completed = completed }; return c } }
        do {
            let saved = try await app.repo.setMealPlanCompleted(item, completed: completed)
            app.updateDashboard { d in d.mealPlanItems = d.mealPlanItems.map { $0.id == saved.id ? saved : $0 } }
        } catch { app.updateDashboard { d in d.mealPlanItems = d.mealPlanItems.map { $0.id == item.id ? item : $0 } }; app.fail(error) }
    }

    func removePlanItem(_ item: MealPlanItem) async {
        guard !busy else { return }
        busy = true; defer { busy = false }
        do { try await app.repo.removeMealPlanItem(item.id); app.updateDashboard { $0.mealPlanItems.removeAll { $0.id == item.id } } } catch { app.fail(error) }
    }
}

/// Sunucunun uyarı kodunu uygulama dilindeki metne çevirir.
enum NutritionWarning {
    static let lowConfidence = 0.6
    @MainActor static func text(for item: NutritionEstimate) -> String? { text(code: item.warningCode, server: item.warning, confidence: item.confidence) }

    @MainActor static func text(code: String?, server: String?, confidence: Double) -> String? {
        if let known = known(code) { return known }
        if let server, !server.trimmingCharacters(in: .whitespaces).isEmpty { return server }
        return confidence < lowConfidence ? known("low_confidence") : nil
    }

    @MainActor private static func known(_ code: String?) -> String? {
        switch code {
        case "approximate_amount": return tr("Miktar yaklaşık tahmin edildi; kaydetmeden önce kontrol et.", "Amount is approximate; check it before saving.")
        case "cooked_assumed": return tr("Pişmiş ağırlık varsayıldı. Çiğ/kuru tarttıysan yiyeceğin başına “çiğ” yaz (örn. çiğ pirinç); kalori yaklaşık 2,5 kat yüksek olur.", "Cooked weight assumed. If you weighed it raw or dry, type “çiğ” before the food (e.g. çiğ pirinç); calories are about 2.5x higher.")
        case "not_in_catalogue": return tr("Bu yiyecek kataloğumuzda yok; kaba bir ortalama gösteriliyor. Kaydetmeden önce değerleri kontrol et.", "Not in our catalogue; a rough average is shown. Check the values before saving.")
        case "ai_estimate": return tr("Yapay zekâ tahmini; tarife ve markaya göre değişebilir.", "AI estimate; it varies with the recipe and brand.")
        case "low_confidence": return tr("Bu kalem için güven düşük; adı ve miktarı kontrol et.", "Low confidence for this item; check the name and amount.")
        default: return nil
        }
    }
}
