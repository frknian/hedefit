import Foundation

// MARK: - Profil

struct Profile: Equatable, Sendable {
    var id: String
    var displayName: String
    var weightKg: Double?
    var heightCm: Double?
    var goal: String
    var isPremium: Bool
    var age: Int?
    var gender: String = ""
    var environment: String = "Evde"
    var equipment: String = ""
    var historyAnswers: [String] = []
    var targetWeightKg: Double?
    var targetWeeks: Int?
    var accountStatus: String = "active"
    var avatarPath: String?
    var avatarURL: String?
    var username: String?
    /// profiles.plan_tier: free | plus | pro (pro = uygulamadaki Premium).
    var planTier: String = "free"

    init(id: String, displayName: String = "Sporcu", weightKg: Double? = nil, heightCm: Double? = nil, goal: String = "Güçlenme", isPremium: Bool = false) {
        self.id = id; self.displayName = displayName; self.weightKg = weightKg; self.heightCm = heightCm; self.goal = goal; self.isPremium = isPremium
    }

    init(json: JSON, userId: String, fallbackName: String) {
        let rawGoal = json.nonEmptyString("goal_text") ?? "Güçlenme"
        self.init(id: userId, displayName: json.nonEmptyString("display_name") ?? fallbackName, weightKg: json.doubleOrNil("weight_kg"), heightCm: json.doubleOrNil("height_cm"),
                  goal: rawGoal.components(separatedBy: " | ").first ?? rawGoal, isPremium: json.bool("is_premium"))
        let tier = json.string("plan_tier")
        planTier = ["free", "plus", "pro"].contains(tier) ? tier : (isPremium ? "pro" : "free")
        age = json.intOrNil("age")
        gender = json.nonEmptyString("gender") ?? ""
        environment = json.nonEmptyString("environment") ?? "Evde"
        equipment = json.nonEmptyString("equipment_text") ?? ""
        historyAnswers = json["history_answers"].items.map { $0.stringValue ?? "" }
        targetWeightKg = Self.firstMatch(in: rawGoal, pattern: "hedef:([0-9.]+)").flatMap(Double.init)
        targetWeeks = Self.firstMatch(in: rawGoal, pattern: "hafta:([0-9]+)").flatMap(Int.init)
        accountStatus = json.string("account_status", "active")
        avatarPath = json.nonEmptyString("avatar_path")
        username = json.nonEmptyString("username")
    }

    private static func firstMatch(in text: String, pattern: String) -> String? {
        guard let regex = try? NSRegularExpression(pattern: pattern), let match = regex.firstMatch(in: text, range: NSRange(text.startIndex..., in: text)),
              match.numberOfRanges > 1, let range = Range(match.range(at: 1), in: text) else { return nil }
        return String(text[range])
    }
}

struct ProfileUpdate: Sendable {
    var displayName: String
    var age: Int?
    var gender: String
    var heightCm: Double?
    var weightKg: Double?
    var goalType: String
    var targetWeightKg: Double?
    var targetWeeks: Int?
    var environment: String
    var equipment: String
    var historyAnswers: [String]
}

// MARK: - Antrenman

struct WorkoutExercise: Identifiable, Equatable, Sendable, Hashable {
    var id: String
    var name: String
    var area: String
    var sets: Int
    var reps: String
    var restSeconds: Int
    var targetWeightKg: Double?

    init(id: String, name: String, area: String, sets: Int, reps: String, restSeconds: Int, targetWeightKg: Double? = nil) {
        self.id = id; self.name = name; self.area = area; self.sets = sets; self.reps = reps; self.restSeconds = restSeconds; self.targetWeightKg = targetWeightKg
    }

    /// Android `parseWorkouts` ile aynı: adı olmayan satır atlanır, sayı 1…20'ye sıkıştırılır.
    init?(json item: JSON) {
        guard let name = item.nonEmptyString("name") else { return nil }
        let rawSets = item.intOrNil("sets") ?? Int(item.string("sets").filter(\.isNumber).prefix(2)) ?? 3
        self.init(id: item.nonEmptyString("id") ?? name.lowercased().replacingOccurrences(of: " ", with: "-"), name: name, area: item.nonEmptyString("area") ?? "Tüm Vücut",
                  sets: min(max(rawSets, 1), 20), reps: item.nonEmptyString("reps") ?? "8–12", restSeconds: item.intOrNil("restSeconds") ?? 60, targetWeightKg: item.doubleOrNil("targetWeightKg"))
    }

    var json: JSON {
        ["id": JSON(id), "name": JSON(name), "area": JSON(area), "sets": JSON(sets), "reps": JSON(reps), "restSeconds": JSON(restSeconds), "targetWeightKg": .opt(targetWeightKg)]
    }

    static func list(_ array: JSON) -> [WorkoutExercise] { array.items.compactMap(WorkoutExercise.init(json:)) }
    static func jsonArray(_ list: [WorkoutExercise]) -> JSON { .array(list.map(\.json)) }
}

struct WorkoutProgramDay: Equatable, Sendable, Hashable { var weekday: Int; var title: String }

struct WorkoutProgram: Identifiable, Equatable, Sendable {
    var id: String
    var name: String
    var source: String
    var focusArea: String
    var exercises: [WorkoutExercise]
    var isActive: Bool
    var showOnHome = false
    var trainingDays: [WorkoutProgramDay] = []
    var updatedAt: String?

    init(id: String, name: String, source: String, focusArea: String, exercises: [WorkoutExercise], isActive: Bool, showOnHome: Bool = false, trainingDays: [WorkoutProgramDay] = [], updatedAt: String? = nil) {
        self.id = id; self.name = name; self.source = source; self.focusArea = focusArea; self.exercises = exercises; self.isActive = isActive
        self.showOnHome = showOnHome; self.trainingDays = trainingDays; self.updatedAt = updatedAt
    }

    init(json item: JSON) {
        self.init(id: item.string("id"), name: item.string("name", "Programım"), source: item.string("source", "custom"), focusArea: item.string("focus_area"),
                  exercises: WorkoutExercise.list(item["exercises"]), isActive: item.bool("is_active"), showOnHome: item.bool("show_on_home"),
                  trainingDays: item["training_days"].items.map { WorkoutProgramDay(weekday: min(max($0.int("weekday"), 1), 7), title: String($0.string("title").prefix(60))) },
                  updatedAt: item.nonEmptyString("updated_at"))
    }
}

struct WorkoutSession: Identifiable, Equatable, Sendable {
    var id: String
    var completedAt: String
    var durationSeconds: Int
    var calories: Int
    var completedExercises: Int
    var totalExercises: Int
    var fatigue: Int?
    var exerciseNames: [String] = []
    var difficulty: String?
    var painAreas: [String] = []
    var manualActivityKey: String?

    init(id: String, completedAt: String, durationSeconds: Int, calories: Int, completedExercises: Int, totalExercises: Int, fatigue: Int? = nil, exerciseNames: [String] = [], difficulty: String? = nil, painAreas: [String] = [], manualActivityKey: String? = nil) {
        self.id = id; self.completedAt = completedAt; self.durationSeconds = durationSeconds; self.calories = calories; self.completedExercises = completedExercises
        self.totalExercises = totalExercises; self.fatigue = fatigue; self.exerciseNames = exerciseNames; self.difficulty = difficulty; self.painAreas = painAreas; self.manualActivityKey = manualActivityKey
    }

    init(json item: JSON) {
        let raw = item["exercise_names"].items.compactMap(\.stringValue).filter { !$0.isEmpty }
        self.init(id: item.string("id"), completedAt: item.string("completed_at"), durationSeconds: item.int("duration_seconds"), calories: item.int("calories"),
                  completedExercises: item.int("completed_exercises"), totalExercises: item.int("total_exercises"), fatigue: item.intOrNil("fatigue"),
                  exerciseNames: raw.filter { !$0.hasPrefix("activity:") }, difficulty: item.nonEmptyString("difficulty"),
                  painAreas: item["pain_areas"].items.compactMap(\.stringValue).filter { !$0.isEmpty },
                  manualActivityKey: raw.first(where: { $0.hasPrefix("activity:") }).map { String($0.dropFirst("activity:".count)) })
    }

    var date: Date? { ISO.date(completedAt) }
}

struct SetPerformance: Equatable, Sendable { var setNumber: Int; var weightKg: Double?; var reps: Int?; var durationSeconds: Int?; var rpe: Int? }

struct ExercisePerformance: Equatable, Sendable {
    var sessionId: String
    var exerciseId: String?
    var exerciseName: String
    var completedAt: String
    var sets: [SetPerformance]
}

struct RoutePoint: Equatable, Sendable, Codable {
    var latitude: Double
    var longitude: Double
    var recordedAt: Int64
    var accuracyMeters: Double = 0
    var altitudeMeters: Double?
    var speedMetersPerSecond: Double?
    var bearingDegrees: Double?
}

struct RouteActivity: Identifiable, Equatable, Sendable {
    var id: String
    var activityType: String
    var title: String
    var startedAt: String
    var endedAt: String
    var durationSeconds: Int
    var movingDurationSeconds: Int
    var distanceMeters: Double
    var averagePaceSecondsPerKm: Int?
    var averageSpeedKmh: Double
    var calories: Int
    var status: String
    var routePoints: [RoutePoint]

    init(json item: JSON) {
        id = item.string("id")
        activityType = item.string("activity_type", "walk")
        title = item.nonEmptyString("title") ?? item.string("activity_type", "Aktivite")
        startedAt = item.string("started_at"); endedAt = item.string("ended_at")
        durationSeconds = item.int("duration_seconds")
        movingDurationSeconds = item.intOrNil("moving_duration_seconds") ?? item.int("duration_seconds")
        distanceMeters = item.double("distance_meters")
        averagePaceSecondsPerKm = item.intOrNil("average_pace_seconds_per_km")
        averageSpeedKmh = item.double("average_speed_kmh")
        calories = item.int("calories")
        status = item.string("status", "completed")
        routePoints = item["route_points"].items.map { RoutePoint(latitude: $0.double("lat"), longitude: $0.double("lng"), recordedAt: Int64($0.double("time")), accuracyMeters: $0.double("accuracy"), altitudeMeters: $0.doubleOrNil("alt")) }
    }

    var date: Date? { ISO.date(startedAt) }
}

struct WorkoutSetInput: Sendable, Codable, Equatable {
    var exerciseId: String
    var exerciseName: String
    var exerciseOrder: Int
    var setNumber: Int
    var weightKg: Double?
    var reps: Int?
    var durationSeconds: Int?
    var rpe: Int?
    var setType: String = "normal"
    var note: String = ""
}

struct PreviousSet: Equatable, Sendable { var setNumber: Int; var weightKg: Double?; var reps: Int?; var rpe: Int? }

struct WorkoutFeedback: Equatable, Sendable {
    var difficulty: String = "Uygun"
    var fatigue: Int = 3
    var painAreas: [String] = ["Yok"]
    var note: String = ""
}

struct WorkoutSchedule: Identifiable, Equatable, Sendable {
    var id: String
    var date: String
    var time: String
    var status: String
    var originalDate: String?
    var programId: String?
    var programName: String?

    init(json item: JSON) {
        id = item.string("id"); date = item.string("scheduled_date"); time = String(item.string("scheduled_time").prefix(5)); status = item.string("status")
        originalDate = item.nonEmptyString("original_date"); programId = item.nonEmptyString("program_id"); programName = item.nonEmptyString("program_name")
    }
}

// MARK: - Beslenme

struct NutritionLog: Identifiable, Equatable, Sendable {
    var id: String
    var date: String
    var meal: String
    var name: String
    var calories: Int
    var protein: Double
    var carbs: Double
    var fat: Double
    var grams: Double?
    var fiber: Double = 0
    var sugar: Double = 0
    var sodiumMg: Double = 0
    var potassiumMg: Double = 0
    var calciumMg: Double = 0
    var ironMg: Double = 0
    var vitaminCMg: Double = 0

    init(json item: JSON) {
        let metadata = item["metadata"], micros = item["micros"]
        id = item.string("id")
        date = item.nonEmptyString("logged_date") ?? String(item.string("consumed_at").prefix(10))
        meal = item.string("meal", "Atıştırmalık"); name = item.string("name"); calories = item.int("calories")
        protein = item.double("protein_g"); carbs = item.double("carbs_g"); fat = item.double("fat_g")
        grams = item.doubleOrNil("grams") ?? metadata.doubleOrNil("portionGrams")
        fiber = item.doubleOrNil("fiber_g") ?? metadata.doubleOrNil("fiber") ?? 0
        func micro(_ key: String) -> Double { metadata.doubleOrNil(key) ?? micros.doubleOrNil(key) ?? 0 }
        sugar = micro("sugar"); sodiumMg = micro("sodiumMg"); potassiumMg = micro("potassiumMg"); calciumMg = micro("calciumMg"); ironMg = micro("ironMg"); vitaminCMg = micro("vitaminCMg")
    }
}

struct FoodSearchItem: Identifiable, Equatable, Sendable {
    var id: String
    var name: String
    var brand: String?
    var servingGrams: Double
    var calories: Int
    var protein: Double
    var carbs: Double
    var fat: Double
    var fiber: Double
    var sugar: Double
    var sodiumMg: Double
    var potassiumMg: Double
    var calciumMg: Double
    var ironMg: Double
    var vitaminCMg: Double
    var verified: Bool
    var source: String

    init(id: String, name: String, brand: String? = nil, servingGrams: Double = 100, calories: Int, protein: Double, carbs: Double, fat: Double, fiber: Double = 0, sugar: Double = 0, sodiumMg: Double = 0, potassiumMg: Double = 0, calciumMg: Double = 0, ironMg: Double = 0, vitaminCMg: Double = 0, verified: Bool, source: String) {
        self.id = id; self.name = name; self.brand = brand; self.servingGrams = servingGrams; self.calories = calories; self.protein = protein; self.carbs = carbs; self.fat = fat; self.fiber = fiber
        self.sugar = sugar; self.sodiumMg = sodiumMg; self.potassiumMg = potassiumMg; self.calciumMg = calciumMg; self.ironMg = ironMg; self.vitaminCMg = vitaminCMg; self.verified = verified; self.source = source
    }

    init(json item: JSON) {
        self.init(id: item.string("id"), name: item.string("name"), brand: item.nonEmptyString("brand"), servingGrams: item.double("servingGrams", 100), calories: item.int("calories"),
                  protein: item.double("protein"), carbs: item.double("carbohydrates"), fat: item.double("fat"), fiber: item.double("fiber"), sugar: item.double("sugar"),
                  sodiumMg: item.double("sodiumMg"), potassiumMg: item.double("potassiumMg"), calciumMg: item.double("calciumMg"), ironMg: item.double("ironMg"),
                  vitaminCMg: item.double("vitaminCMg"), verified: item.bool("verified"), source: item.string("source"))
    }
}

struct FavoriteMeal: Identifiable, Equatable, Sendable {
    var id: String
    var name: String
    var meal: String
    var grams: Double
    var calories: Int
    var protein: Double
    var carbs: Double
    var fat: Double
    var fiber: Double
    var micros: [String: Double]

    init(json item: JSON) {
        let micros = item["micros"]
        id = item.string("id"); name = item.string("name"); meal = item.string("meal"); grams = item.double("grams", 100); calories = item.int("calories")
        protein = item.double("protein_g"); carbs = item.double("carbs_g"); fat = item.double("fat_g"); fiber = item.double("fiber_g")
        self.micros = Dictionary(uniqueKeysWithValues: ["sugar", "sodiumMg", "potassiumMg", "calciumMg", "ironMg", "vitaminCMg"].map { ($0, micros.double($0)) })
    }

    /// Favoriyi tekrar eklemek için 100 g başına değerlere çevirir.
    var asRepeatFood: FoodSearchItem {
        let ratio = 100.0 / max(grams, 1)
        return FoodSearchItem(id: "", name: name, servingGrams: 100, calories: Int(Double(calories) * ratio), protein: protein * ratio, carbs: carbs * ratio, fat: fat * ratio, fiber: fiber * ratio,
                              sugar: (micros["sugar"] ?? 0) * ratio, sodiumMg: (micros["sodiumMg"] ?? 0) * ratio, potassiumMg: (micros["potassiumMg"] ?? 0) * ratio,
                              calciumMg: (micros["calciumMg"] ?? 0) * ratio, ironMg: (micros["ironMg"] ?? 0) * ratio, vitaminCMg: (micros["vitaminCMg"] ?? 0) * ratio, verified: true, source: "favorite")
    }
}

struct MealPlanItem: Identifiable, Equatable, Sendable {
    var id: String
    var plannedDate: String
    var mealType: String
    var name: String
    var grams: Double
    var calories: Int
    var protein: Double
    var carbs: Double
    var fat: Double
    var fiber: Double
    var sugar: Double
    var sodiumMg: Double
    var potassiumMg: Double
    var calciumMg: Double
    var ironMg: Double
    var vitaminCMg: Double
    var completed: Bool

    init(json item: JSON) {
        let micros = item["micros"]
        id = item.string("id"); plannedDate = item.string("planned_date"); mealType = item.string("meal_type", "snack"); name = item.string("food_name")
        grams = item.double("grams"); calories = item.int("calories"); protein = item.double("protein_g"); carbs = item.double("carbs_g"); fat = item.double("fat_g"); fiber = item.double("fiber_g")
        sugar = micros.double("sugar"); sodiumMg = micros.double("sodiumMg"); potassiumMg = micros.double("potassiumMg"); calciumMg = micros.double("calciumMg"); ironMg = micros.double("ironMg"); vitaminCMg = micros.double("vitaminCMg")
        completed = item.bool("completed")
    }
}

struct NutritionGoal: Equatable, Sendable {
    var calories = 2250, protein = 110, carbs = 297, fat = 69
}

struct NutritionEstimate: Identifiable, Equatable, Sendable {
    var id = UUID()
    var name: String
    var grams: Double
    var calories: Int
    var protein: Double
    var carbs: Double
    var fat: Double
    var fiber: Double
    var sugar = 0.0, sodiumMg = 0.0, potassiumMg = 0.0, calciumMg = 0.0, ironMg = 0.0, vitaminCMg = 0.0
    var confidence: Double
    var portionQuantity: Double?
    var portionUnit: String?
    var needsConfirmation = false
    var warning: String?
    var warningCode: String?
}

struct BodyMeasurement: Equatable, Sendable, Identifiable {
    var date: String
    var weightKg: Double?
    var waistCm: Double?
    var hipsCm: Double?
    var chestCm: Double?
    var armCm: Double?
    var thighCm: Double?
    var id: String { date }

    init(date: String, weightKg: Double? = nil, waistCm: Double? = nil, hipsCm: Double? = nil, chestCm: Double? = nil, armCm: Double? = nil, thighCm: Double? = nil) {
        self.date = date; self.weightKg = weightKg; self.waistCm = waistCm; self.hipsCm = hipsCm; self.chestCm = chestCm; self.armCm = armCm; self.thighCm = thighCm
    }

    init(json item: JSON) {
        self.init(date: item.string("measured_at"), weightKg: item.doubleOrNil("weight_kg"), waistCm: item.doubleOrNil("waist_cm"), hipsCm: item.doubleOrNil("hips_cm"),
                  chestCm: item.doubleOrNil("chest_cm"), armCm: item.doubleOrNil("arm_cm"), thighCm: item.doubleOrNil("thigh_cm"))
    }
}

struct DailyStep: Equatable, Sendable { var localDate: String; var steps: Int }

// MARK: - Egzersiz kataloğu

struct ExerciseCatalogItem: Identifiable, Equatable, Sendable, Hashable {
    var id: String
    var name: String
    var level: String
    var equipment: String
    var primaryMuscles: [String]
    var instructions: [String]
    var category: String
    var imageUrls: [String]
    var secondaryMuscles: [String]
    var force: String
    var mechanic: String
    var levelKey: String
    var requiredEquipment: [[String]]
    var modalities: [String]
    var subcategories: [String]
    var subcategoryLabels: [String]
    var impact: String
    var description: String
    var tips: [String]

    init(json item: JSON) {
        id = item.string("id"); name = item.string("name"); level = item.string("level"); equipment = item.string("equipment")
        primaryMuscles = item["primaryMuscles"].items.map { $0.stringValue ?? "" }
        instructions = item["instructions"].items.map { $0.stringValue ?? "" }
        category = item.string("category")
        imageUrls = item["images"].items.compactMap(\.stringValue).filter { !$0.isEmpty }
        secondaryMuscles = item["secondaryMuscles"].items.map { $0.stringValue ?? "" }
        force = item.string("force"); mechanic = item.string("mechanic"); levelKey = item.string("levelKey")
        requiredEquipment = item["requiredEquipment"].items.map { $0.items.map { $0.stringValue ?? "" } }
        modalities = item["modalities"].items.map { $0.stringValue ?? "" }
        subcategories = item["subcategories"].items.map { $0.stringValue ?? "" }
        subcategoryLabels = item["subcategoryLabels"].items.map { $0.stringValue ?? "" }
        impact = item.string("impact") == "null" ? "" : item.string("impact")
        description = item.string("description") == "null" ? "" : item.string("description")
        tips = item["tips"].items.map { $0.stringValue ?? "" }
    }
}

// MARK: - Koç / uyarlama

struct CoachAction: Equatable, Sendable, Identifiable {
    var id = UUID()
    var type: String
    var exerciseId: String?
    var replacementId: String?
    var replacementName: String?
    var sets: Int?
    var reps: String?
    var restSeconds: Int?
    var reason: String?
    var targetMinutes: Int?
    var percent: Int?
    var region: String?
    var targetKcal: Int?

    init(json a: JSON) {
        type = a.string("type")
        exerciseId = a.nonEmptyString("exerciseId"); replacementId = a.nonEmptyString("replacementId"); replacementName = a.nonEmptyString("replacementName")
        sets = a.intOrNil("sets").flatMap { $0 > 0 ? $0 : nil }; reps = a.nonEmptyString("reps"); restSeconds = a.intOrNil("restSeconds").flatMap { $0 > 0 ? $0 : nil }
        reason = a.nonEmptyString("reason"); targetMinutes = a.intOrNil("targetMinutes").flatMap { $0 > 0 ? $0 : nil }; percent = a.intOrNil("percent").flatMap { $0 > 0 ? $0 : nil }
        region = a.nonEmptyString("region"); targetKcal = a.intOrNil("targetKcal").flatMap { $0 > 0 ? $0 : nil }
    }
}

struct ChatReply: Sendable {
    var text: String
    var source: String
    var used: Int?
    var limit: Int?
    var actions: [CoachAction] = []
}

struct ChatMessage: Identifiable, Equatable, Sendable {
    var id = UUID()
    var text: String
    var fromUser: Bool
    var actions: [CoachAction] = []
    var source: String = ""
}

struct DailyReadinessInput: Sendable {
    var energy = 8, sleepQuality = 8, fatigue = 3
    var hasSoreness = false
    var sorenessAreas: [String] = []
    var discomfortLevel = 1
    var notes: String?
}

struct ReadinessAdaptation: Sendable {
    var needsAdaptation: Bool
    var recommendedIntensity: String
    var explanationTr: String
    var explanationEn: String
    var volumeReductionPercent: Int
    var deloadedMuscles: [String]
    var adaptedExercises: [WorkoutExercise]
    var originalExercises: [WorkoutExercise]
}

struct ExerciseReplacementCandidate: Sendable {
    var originalExerciseId: String, originalExerciseName: String
    var replacementExerciseId: String, replacementExerciseName: String
    var reason: String, explanationTr: String
    var sets: Int, reps: String, restSeconds: Int
    var progressionType: String
}

struct WorkoutAdaptationResult: Sendable {
    var trigger: String
    var originalDurationMinutes: Int
    var adaptedDurationMinutes: Int
    var explanationTr: String
    var changes: [String]
    var adaptedExercises: [WorkoutExercise]
}

struct WorkoutCoachContext: Sendable, Equatable {
    var exerciseId: String?, exerciseName: String?, muscleGroup: String?
    var targetSets: Int?, currentSet: Int?
    var reps: String?
    var workoutDurationMinutes: Int?, elapsedSeconds: Int?
    var isBeginner = false
}

// MARK: - Pano

struct Dashboard: Sendable {
    var profile: Profile
    var workouts: [WorkoutExercise] = []
    var sessions: [WorkoutSession] = []
    var nutritionLogs: [NutritionLog] = []
    var nutritionGoal = NutritionGoal()
    var steps = 0
    var waterMl = 0
    var sleepMinutes = 0
    var streakDays = 0
    var measurements: [BodyMeasurement] = []
    var loadedDate = Date()
    var activeCalories = 0
    var schedule: [WorkoutSchedule] = []
    var favoriteMeals: [FavoriteMeal] = []
    var mealPlanItems: [MealPlanItem] = []
    var workoutPrograms: [WorkoutProgram] = []
    var routeActivities: [RouteActivity] = []
    var exercisePerformance: [ExercisePerformance] = []
    var exerciseCatalog: [ExerciseCatalogItem] = []
    var stepHistory: [DailyStep] = []
    var gamificationTotalXp: Int?
    var gamificationWeeklyXp: Int?
    var unlockedAchievements: [String: String] = [:]
}
