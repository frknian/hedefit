import Foundation

extension HedefitRepository {
    func loadNutritionLogs(day: String) async throws -> [NutritionLog] {
        try await api.get("/api/nutrition/logs?date=\(day)").requireSuccess(trNow("Beslenme günlüğü yüklenemedi.", "Couldn't load the food log.")).json["logs"].items.map(NutritionLog.init(json:))
    }

    func loadNutritionHistory(from: Date, to: Date) async throws -> [NutritionLog] {
        try await api.get("/api/nutrition/logs?from=\(Dates.day(from))&to=\(Dates.day(to))").requireSuccess(trNow("Beslenme geçmişi yüklenemedi.", "Couldn't load food history.")).json["logs"].items.map(NutritionLog.init(json:))
    }

    func searchFoods(_ query: String, locale: String = "tr") async throws -> [FoodSearchItem] {
        let json = try await api.get("/api/nutrition/foods?q=\(HTTP.encode(query.trimmingCharacters(in: .whitespaces)))&locale=\(locale == "en" ? "en" : "tr")").requireSuccess(trNow("Besin kataloğu aranamadı.", "Couldn't search the food catalogue.")).json
        // Eski bir sunucu sürümü önbellekten dönse bile Türkçe arayüzde USDA İngilizce adları görünmesin.
        return json["items"].items.map(FoodSearchItem.init(json:)).filter { !(locale != "en" && $0.source.caseInsensitiveCompare("usda") == .orderedSame) }
    }

    func analyzeNutritionPhoto(_ jpeg: Data) async throws -> [NutritionEstimate] {
        guard !jpeg.isEmpty, jpeg.count <= 5 * 1024 * 1024 else { throw AppError.message(trNow("Fotoğraf 5 MB'den küçük olmalı.", "The photo must be under 5 MB.")) }
        let json = try await api.post("/api/nutrition/analyze-photo", ["imageDataUrl": JSON("data:image/jpeg;base64,\(jpeg.base64EncodedString())")]).requireSuccess(trNow("Fotoğraftaki öğün analiz edilemedi.", "Couldn't analyse the meal photo.")).json
        let items = json["items"].items.map { item in
            NutritionEstimate(name: item.string("name"), grams: item.double("estimatedGrams"), calories: item.int("calories"), protein: item.double("protein"), carbs: item.double("carbohydrates"), fat: item.double("fat"),
                              fiber: item.double("fiber"), sugar: item.double("sugar"), sodiumMg: item.double("sodiumMg"), potassiumMg: item.double("potassiumMg"), calciumMg: item.double("calciumMg"),
                              ironMg: item.double("ironMg"), vitaminCMg: item.double("vitaminCMg"), confidence: item.double("confidence"))
        }
        if items.isEmpty { throw AppError.message(trNow("Fotoğrafta öğün bulunamadı.", "No meal found in the photo.")) }
        return items
    }

    func savePhotoNutrition(_ items: [NutritionEstimate], meal: String, inputMethod: String = "photo") async throws -> [NutritionLog] {
        var logs: [NutritionLog] = []
        for item in items {
            let grams = min(max(item.grams, 1), 5000), ratio = 100 / grams
            let food = FoodSearchItem(id: "photo", name: item.name, servingGrams: grams, calories: Int(Double(item.calories) * ratio), protein: item.protein * ratio, carbs: item.carbs * ratio, fat: item.fat * ratio, fiber: item.fiber * ratio,
                                      sugar: item.sugar * ratio, sodiumMg: item.sodiumMg * ratio, potassiumMg: item.potassiumMg * ratio, calciumMg: item.calciumMg * ratio, ironMg: item.ironMg * ratio, vitaminCMg: item.vitaminCMg * ratio,
                                      verified: false, source: inputMethod == "photo" ? "photo_ai" : "meal_text")
            logs.append(try await addCatalogFood(food, grams: grams, meal: meal, inputMethod: inputMethod))
        }
        return logs
    }

    func catalogFoodPayload(_ food: FoodSearchItem, grams: Double, meal: String, inputMethod: String = "search", date: Date = Date()) -> JSON {
        let ratio = grams / 100
        let isUUID = food.id.range(of: "^[0-9a-fA-F-]{36}$", options: .regularExpression) != nil
        return ["foodId": isUUID ? JSON(food.id) : .null, "loggedDate": JSON(Dates.day(date)), "mealType": JSON(meal), "foodName": JSON(food.name), "portionGrams": JSON(grams), "calories": JSON(Int(Double(food.calories) * ratio)),
                "protein": JSON(food.protein * ratio), "carbohydrates": JSON(food.carbs * ratio), "fat": JSON(food.fat * ratio), "fiber": JSON(food.fiber * ratio), "inputMethod": JSON(inputMethod),
                "confidence": JSON(food.verified ? 1.0 : 0.85), "isEstimated": JSON(!food.verified),
                "metadata": ["source": JSON(food.source), "sugar": JSON(food.sugar * ratio), "sodiumMg": JSON(food.sodiumMg * ratio), "potassiumMg": JSON(food.potassiumMg * ratio),
                             "calciumMg": JSON(food.calciumMg * ratio), "ironMg": JSON(food.ironMg * ratio), "vitaminCMg": JSON(food.vitaminCMg * ratio)].json]
    }

    func addCatalogFood(_ food: FoodSearchItem, grams: Double, meal: String, inputMethod: String = "search", date: Date = Date()) async throws -> NutritionLog {
        let response = try await api.post("/api/nutrition/logs", catalogFoodPayload(food, grams: grams, meal: meal, inputMethod: inputMethod, date: date)).requireSuccess(trNow("Besin kaydedilemedi.", "Couldn't save the food."))
        return NutritionLog(json: response.json["log"])
    }

    func removeNutritionLog(_ id: String) async throws { try await api.delete("/api/nutrition/logs/\(id)").requireSuccess(trNow("Besin kaldırılamadı.", "Couldn't remove the food.")) }

    /// Kayıtlı öğeyi kendi değerlerinden yeniden ölçekler ve öğünler arasında taşıyabilir.
    func updateNutritionLog(_ log: NutritionLog, grams: Double, meal: String) async throws -> NutritionLog {
        let ratio = grams / max(log.grams ?? 100, 1)
        let body: JSON = ["mealType": JSON(meal), "portionGrams": JSON(grams), "calories": JSON(Int(Double(log.calories) * ratio)), "protein": JSON(log.protein * ratio), "carbohydrates": JSON(log.carbs * ratio), "fat": JSON(log.fat * ratio), "fiber": JSON(log.fiber * ratio)]
        return NutritionLog(json: try await api.patch("/api/nutrition/logs/\(log.id)", body).requireSuccess(trNow("Besin güncellenemedi.", "Couldn't update the food.")).json["log"])
    }

    func addFavorite(_ log: NutritionLog) async throws -> FavoriteMeal {
        let row: JSON = ["user_id": JSON(try await requireUserId()), "name": JSON(log.name), "meal": JSON(log.meal), "grams": JSON(log.grams ?? 100), "calories": JSON(log.calories), "protein_g": JSON(log.protein), "carbs_g": JSON(log.carbs),
                         "fat_g": JSON(log.fat), "fiber_g": JSON(log.fiber),
                         "micros": ["sugar": JSON(log.sugar), "sodiumMg": JSON(log.sodiumMg), "potassiumMg": JSON(log.potassiumMg), "calciumMg": JSON(log.calciumMg), "ironMg": JSON(log.ironMg), "vitaminCMg": JSON(log.vitaminCMg)].json]
        return FavoriteMeal(json: try await rest.upsert("favorite_meals", row, onConflict: "user_id,name,grams"))
    }

    func removeFavorite(_ id: String) async throws { try await rest.delete("favorite_meals", "id=eq.\(id)&user_id=eq.\(try await requireUserId())") }

    func repeatFavorite(_ favorite: FavoriteMeal) async throws -> NutritionLog {
        try await addCatalogFood(favorite.asRepeatFood, grams: favorite.grams, meal: favorite.meal, inputMethod: "favorite")
    }

    func addMealPlanItem(_ food: FoodSearchItem, grams: Double, date: Date, mealType: String) async throws -> MealPlanItem {
        let clean = min(max(grams, 1), 5000), ratio = clean / 100
        let micros: JSON = ["sugar": JSON(food.sugar * ratio), "sodiumMg": JSON(food.sodiumMg * ratio), "potassiumMg": JSON(food.potassiumMg * ratio), "calciumMg": JSON(food.calciumMg * ratio), "ironMg": JSON(food.ironMg * ratio), "vitaminCMg": JSON(food.vitaminCMg * ratio)]
        let row: JSON = ["user_id": JSON(try await requireUserId()), "planned_date": JSON(Dates.day(date)), "meal_type": JSON(["breakfast", "lunch", "dinner", "snack"].contains(mealType) ? mealType : "snack"),
                         "food_name": JSON(String(food.name.prefix(160))), "grams": JSON(clean), "calories": JSON(Int(Double(food.calories) * ratio)), "protein_g": JSON(food.protein * ratio), "carbs_g": JSON(food.carbs * ratio),
                         "fat_g": JSON(food.fat * ratio), "fiber_g": JSON(food.fiber * ratio), "micros": micros]
        return MealPlanItem(json: try await rest.insert("meal_plan_items", row))
    }

    func setMealPlanCompleted(_ item: MealPlanItem, completed: Bool) async throws -> MealPlanItem {
        MealPlanItem(json: try await rest.update("meal_plan_items", "id=eq.\(item.id)&user_id=eq.\(try await requireUserId())", ["completed": JSON(completed), "completed_at": completed ? JSON(ISO.string()) : .null]))
    }

    func removeMealPlanItem(_ id: String) async throws { try await rest.delete("meal_plan_items", "id=eq.\(id)&user_id=eq.\(try await requireUserId())") }

    private func estimate(from item: JSON, defaultName: String, defaultGrams: Double, defaultConfidence: Double) -> NutritionEstimate {
        let n = item["nutrition"]
        var estimate = NutritionEstimate(name: item.nonEmptyString("name") ?? item.string("query", defaultName), grams: item.double("estimatedGrams", defaultGrams), calories: n.int("calories"), protein: n.double("protein"),
                                         carbs: n.double("carbohydrates"), fat: n.double("fat"), fiber: n.double("fiber"), sugar: n.double("sugar"), sodiumMg: n.double("sodiumMg"), potassiumMg: n.double("potassiumMg"),
                                         calciumMg: n.double("calciumMg"), ironMg: n.double("ironMg"), vitaminCMg: n.double("vitaminCMg"), confidence: item.double("confidence", defaultConfidence))
        estimate.portionQuantity = item.doubleOrNil("quantity").flatMap { $0 > 0 ? $0 : nil }
        estimate.portionUnit = item.nonEmptyString("unit")
        estimate.needsConfirmation = item.bool("needsConfirmation")
        estimate.warning = item.nonEmptyString("warning"); estimate.warningCode = item.nonEmptyString("warningCode")
        return estimate
    }

    /// Bütün bir öğün cümlesini ("omlet, 3 dilim ekmek, domates") ayrı porsiyonlu besinlere böler.
    func parseMealText(_ text: String) async throws -> [NutritionEstimate] {
        let json = try await api.post("/api/nutrition/parse-text", ["text": JSON(text)]).requireSuccess(trNow("Öğün metni çözümlenemedi.", "Couldn't parse the meal text.")).json
        return json["items"].items.map { estimate(from: $0, defaultName: "", defaultGrams: 100, defaultConfidence: 0.7) }
    }

    func estimateNutrition(food: String, grams: Double) async throws -> NutritionEstimate {
        let json = try await api.post("/api/nutrition/parse-text", ["query": JSON(food), "grams": JSON(grams)]).requireSuccess(trNow("Besin değerleri hesaplanamadı.", "Couldn't estimate nutrition.")).json
        guard json["items"].count > 0 else { throw AppError.message(trNow("Besin değerleri hesaplanamadı.", "Couldn't estimate nutrition.")) }
        return estimate(from: json["items"][0], defaultName: food, defaultGrams: grams, defaultConfidence: json.double("confidence", 0.5))
    }

    func addNutrition(_ e: NutritionEstimate, meal: String, date: Date = Date()) async throws -> NutritionLog {
        let body: JSON = ["foodId": .null, "loggedDate": JSON(Dates.day(date)), "mealType": JSON(meal), "foodName": JSON(e.name), "portionGrams": JSON(e.grams), "calories": JSON(e.calories), "protein": JSON(e.protein),
                          "carbohydrates": JSON(e.carbs), "fat": JSON(e.fat), "fiber": JSON(e.fiber), "inputMethod": "natural_language", "confidence": JSON(e.confidence), "isEstimated": true,
                          "metadata": ["client": "ios", "sugar": JSON(e.sugar), "sodiumMg": JSON(e.sodiumMg), "potassiumMg": JSON(e.potassiumMg), "calciumMg": JSON(e.calciumMg), "ironMg": JSON(e.ironMg), "vitaminCMg": JSON(e.vitaminCMg)]]
        return NutritionLog(json: try await api.post("/api/nutrition/logs", body).requireSuccess(trNow("Öğün kaydedilemedi.", "Couldn't save the meal.")).json["log"])
    }

    func nutritionWellness(diet: String, workedOutToday: Bool, locale: String) async throws -> NutritionWellness {
        var training: [String: JSON] = ["workedOutToday": JSON(workedOutToday)]
        if let last = lastAdaptation, last.adapted {
            training["level"] = JSON(last.level); training["minutes"] = JSON(last.estimatedMinutes)
            if let kind = last.wellnessKind { training["sessionKind"] = JSON(kind) }
        }
        let body: JSON = ["locale": JSON(locale), "diet": JSON(diet), "training": training.json, "localDate": JSON(Dates.day())]
        return NutritionWellness(json: try await api.post("/api/nutrition/wellness", body).requireSuccess(trNow("İpuçları alınamadı.", "Couldn't load tips.")).json)
    }
}
