package com.hedefit.app.data.model

import java.time.LocalDate

data class ProfileData(
    val id: String,
    val displayName: String,
    val weightKg: Double?,
    val heightCm: Double?,
    val goal: String,
    val isPremium: Boolean,
    val age: Int? = null,
    val gender: String = "",
    val environment: String = "Evde",
    val equipment: String = "",
    val historyAnswers: List<String> = emptyList(),
    val targetWeightKg: Double? = null,
    val targetWeeks: Int? = null,
    val accountStatus: String = "active",
    val avatarPath: String? = null,
    val avatarUrl: String? = null,
    val username: String? = null,
    /** Sunucudaki profiles.plan_tier: free | plus | pro (pro = uygulamadaki Premium). */
    val planTier: String = "free",
)

data class ProfileUpdateData(
    val displayName: String,
    val age: Int?,
    val gender: String,
    val heightCm: Double?,
    val weightKg: Double?,
    val goalType: String,
    val targetWeightKg: Double?,
    val targetWeeks: Int?,
    val environment: String,
    val equipment: String,
    val historyAnswers: List<String>,
)

data class WorkoutExerciseData(
    val id: String,
    val name: String,
    val area: String,
    val sets: Int,
    val reps: String,
    val restSeconds: Int,
    val targetWeightKg: Double? = null,
)

data class WorkoutProgramDayData(val weekday: Int, val title: String)
data class CustomProgramDraft(val name: String, val trainingDays: List<WorkoutProgramDayData>)

data class WorkoutProgramData(
    val id: String,
    val name: String,
    val source: String,
    val focusArea: String,
    val exercises: List<WorkoutExerciseData>,
    val isActive: Boolean,
    val showOnHome: Boolean = false,
    val trainingDays: List<WorkoutProgramDayData> = emptyList(),
)

data class WorkoutSessionData(
    val id: String,
    val completedAt: String,
    val durationSeconds: Int,
    val calories: Int,
    val completedExercises: Int,
    val totalExercises: Int,
    val fatigue: Int?,
    val exerciseNames: List<String> = emptyList(),
    val difficulty: String? = null,
    val painAreas: List<String> = emptyList(),
    val manualActivityKey: String? = null,
)

data class WorkoutSetPerformanceData(
    val setNumber: Int,
    val weightKg: Double?,
    val reps: Int?,
    val durationSeconds: Int?,
    val rpe: Int?,
)

data class WorkoutExercisePerformanceData(
    val sessionId: String,
    val exerciseId: String?,
    val exerciseName: String,
    val completedAt: String,
    val sets: List<WorkoutSetPerformanceData>,
)

data class RouteActivityData(
    val id: String,
    val activityType: String,
    val title: String,
    val startedAt: String,
    val endedAt: String,
    val durationSeconds: Int,
    val movingDurationSeconds: Int,
    val distanceMeters: Double,
    val averagePaceSecondsPerKm: Int? = null,
    val averageSpeedKmh: Double = 0.0,
    val calories: Int = 0,
    val status: String = "completed",
    val routePoints: List<ActivityRoutePointData> = emptyList(),
)

data class ActivityRoutePointData(
    val latitude: Double,
    val longitude: Double,
    val recordedAt: Long,
    val accuracyMeters: Double = 0.0,
    val altitudeMeters: Double? = null,
)

data class WorkoutSetInput(
    val exerciseId: String,
    val exerciseName: String,
    val exerciseOrder: Int,
    val setNumber: Int,
    val weightKg: Double?,
    val reps: Int?,
    val durationSeconds: Int?,
    val rpe: Int?,
    val setType: String = "normal",
    val note: String = "",
)

data class PreviousSetData(val setNumber: Int, val weightKg: Double?, val reps: Int?, val rpe: Int?)

data class WorkoutFeedbackData(
    val difficulty: String = "Uygun",
    val fatigue: Int = 3,
    val painAreas: List<String> = listOf("Yok"),
    val note: String = "",
)

data class WorkoutScheduleData(
    val id: String,
    val date: String,
    val time: String,
    val status: String,
    val originalDate: String?,
    val programId: String? = null,
    val programName: String? = null,
)

data class NutritionLogData(
    val id: String,
    val date: String,
    val meal: String,
    val name: String,
    val calories: Int,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val grams: Double?,
    val fiber: Double = 0.0,
    val sugar: Double = 0.0,
    val sodiumMg: Double = 0.0,
    val potassiumMg: Double = 0.0,
    val calciumMg: Double = 0.0,
    val ironMg: Double = 0.0,
    val vitaminCMg: Double = 0.0,
)

data class FoodSearchData(
    val id: String,
    val name: String,
    val brand: String?,
    val servingGrams: Double,
    val calories: Int,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val fiber: Double,
    val sugar: Double,
    val sodiumMg: Double,
    val potassiumMg: Double,
    val calciumMg: Double,
    val ironMg: Double,
    val vitaminCMg: Double,
    val verified: Boolean,
    val source: String,
)

data class FavoriteMealData(
    val id: String,
    val name: String,
    val meal: String,
    val grams: Double,
    val calories: Int,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val fiber: Double,
    val micros: Map<String, Double>,
)

data class MealPlanItemData(
    val id: String,
    val plannedDate: String,
    val mealType: String,
    val name: String,
    val grams: Double,
    val calories: Int,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val fiber: Double,
    val sugar: Double,
    val sodiumMg: Double,
    val potassiumMg: Double,
    val calciumMg: Double,
    val ironMg: Double,
    val vitaminCMg: Double,
    val completed: Boolean,
)

data class ExerciseCatalogData(
    val id: String,
    val name: String,
    val level: String,
    val equipment: String,
    val primaryMuscles: List<String>,
    val instructions: List<String>,
    val category: String,
    val imageUrls: List<String>,
    val secondaryMuscles: List<String> = emptyList(),
    val force: String = "",
    val mechanic: String = "",
    /** Raw catalog level: beginner | intermediate | advanced. */
    val levelKey: String = "",
    /** Audited equipment alternatives; an empty option means no equipment is needed. */
    val requiredEquipment: List<List<String>> = emptyList(),
)

data class NutritionGoalData(
    val calories: Int = 2250,
    val protein: Int = 110,
    val carbs: Int = 297,
    val fat: Int = 69,
)

data class BodyMeasurementData(
    val date: String,
    val weightKg: Double?,
    val waistCm: Double?,
    val hipsCm: Double?,
    val chestCm: Double?,
    val armCm: Double?,
    val thighCm: Double?,
)

data class DashboardData(
    val profile: ProfileData,
    val workouts: List<WorkoutExerciseData>,
    val sessions: List<WorkoutSessionData>,
    val nutritionLogs: List<NutritionLogData>,
    val nutritionGoal: NutritionGoalData,
    val steps: Int,
    val waterMl: Int,
    val sleepMinutes: Int,
    val streakDays: Int,
    val measurements: List<BodyMeasurementData>,
    val loadedDate: LocalDate,
    val activeCalories: Int = 0,
    val schedule: List<WorkoutScheduleData> = emptyList(),
    val favoriteMeals: List<FavoriteMealData> = emptyList(),
    val mealPlanItems: List<MealPlanItemData> = emptyList(),
    val workoutPrograms: List<WorkoutProgramData> = emptyList(),
    val routeActivities: List<RouteActivityData> = emptyList(),
    val exercisePerformance: List<WorkoutExercisePerformanceData> = emptyList(),
    /** Catalog metadata is used to weight primary and secondary muscle work accurately. */
    val exerciseCatalog: List<ExerciseCatalogData> = emptyList(),
    val stepHistory: List<DailyStepData> = emptyList(),
    val gamificationTotalXp: Int? = null,
    val gamificationWeeklyXp: Int? = null,
    val unlockedAchievements: Map<String, LocalDate> = emptyMap(),
)

data class DailyStepData(val localDate: LocalDate, val steps: Int)

data class NutritionEstimateData(
    val name: String,
    val grams: Double,
    val calories: Int,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val fiber: Double,
    val sugar: Double = 0.0,
    val sodiumMg: Double = 0.0,
    val potassiumMg: Double = 0.0,
    val calciumMg: Double = 0.0,
    val ironMg: Double = 0.0,
    val vitaminCMg: Double = 0.0,
    val confidence: Double,
    /** Household portion the parser recognised ("3" + "dilim"); grams are derived from it. */
    val portionQuantity: Double? = null,
    val portionUnit: String? = null,
)

data class CoachActionData(
    val type: String,
    val exerciseId: String? = null,
    val replacementId: String? = null,
    val replacementName: String? = null,
    val sets: Int? = null,
    val reps: String? = null,
    val restSeconds: Int? = null,
    val reason: String? = null,
    val targetMinutes: Int? = null,
    val percent: Int? = null,
    val region: String? = null,
    val targetKcal: Int? = null,
)

data class ChatReplyData(
    val text: String,
    val source: String,
    val used: Int?,
    val limit: Int?,
    val actions: List<CoachActionData> = emptyList(),
)

data class DailyReadinessInput(
    val energy: Int = 8,
    val sleepQuality: Int = 8,
    val fatigue: Int = 3,
    val hasSoreness: Boolean = false,
    val sorenessAreas: List<String> = emptyList(),
    val discomfortLevel: Int = 1,
    val notes: String? = null,
)

data class ReadinessAdaptationData(
    val needsAdaptation: Boolean,
    val recommendedIntensity: String,
    val explanationTr: String,
    val explanationEn: String,
    val volumeReductionPercent: Int,
    val delodedMuscles: List<String> = emptyList(),
    val adaptedExercises: List<WorkoutExerciseData> = emptyList(),
    val originalExercises: List<WorkoutExerciseData> = emptyList(),
)

data class ExerciseReplacementCandidate(
    val originalExerciseId: String,
    val originalExerciseName: String,
    val replacementExerciseId: String,
    val replacementExerciseName: String,
    val reason: String,
    val explanationTr: String,
    val sets: Int,
    val reps: String,
    val restSeconds: Int,
    val progressionType: String,
)

data class WorkoutAdaptationResultData(
    val trigger: String,
    val originalDurationMinutes: Int,
    val adaptedDurationMinutes: Int,
    val explanationTr: String,
    val changes: List<String> = emptyList(),
    val adaptedExercises: List<WorkoutExerciseData> = emptyList(),
)

data class WorkoutCoachContext(
    val exerciseId: String? = null,
    val exerciseName: String? = null,
    val muscleGroup: String? = null,
    val targetSets: Int? = null,
    val currentSet: Int? = null,
    val reps: String? = null,
    val workoutDurationMinutes: Int? = null,
    val elapsedSeconds: Int? = null,
    val isBeginner: Boolean = false,
)
