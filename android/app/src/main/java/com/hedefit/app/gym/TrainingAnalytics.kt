package com.hedefit.app.gym

import com.hedefit.app.data.model.ExerciseCatalogData
import com.hedefit.app.data.model.WorkoutExercisePerformanceData
import com.hedefit.app.data.model.WorkoutSetInput
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/** Single source of truth for primary/secondary muscle contribution. */
data class MuscleContributionConfig(val primary: Double = 1.0, val secondary: Double = 0.5)
val DEFAULT_MUSCLE_CONTRIBUTION = MuscleContributionConfig()
const val PRIMARY_MUSCLE_FACTOR = 1.0
const val SECONDARY_MUSCLE_FACTOR = 0.5

val TRACKED_MUSCLES = listOf(
    "chest", "upper_back", "lower_back", "lats", "traps", "neck", "front_delts", "side_delts", "rear_delts",
    "biceps", "triceps", "forearms", "abs", "glutes", "abductors", "adductors", "quads", "hamstrings", "calves",
)

data class PersonalRecordResult(
    val exerciseName: String,
    val highestWeight: Boolean,
    val highestVolume: Boolean,
    val repetitionRecord: Boolean,
    val estimatedOneRepMax: Double,
)

data class MuscleExerciseContribution(val exerciseName: String, val volumeKg: Double, val setEquivalent: Double)

data class MuscleLoad(
    val muscle: String,
    val score: Double,
    val setEquivalent: Double,
    val level: LoadLevel,
    val totalVolumeKg: Double = 0.0,
    val lastTrainedAt: String? = null,
    val exercises: List<MuscleExerciseContribution> = emptyList(),
    /** Four chronological weekly buckets from the latest 30 days. */
    val trendVolumes: List<Double> = emptyList(),
)

enum class LoadLevel { NONE, LOW, BALANCED, HIGH, OVERLOAD }

data class TrainingAnalysis(
    val totalVolumeKg: Double,
    val totalRepetitions: Int,
    val muscleLoads: List<MuscleLoad>,
    val dailyVolume: Map<DayOfWeek, Double>,
    val balanceInsightTr: String,
    val balanceInsightEn: String,
    val rangeDays: Int = 7,
)

fun setVolume(weightKg: Double?, reps: Int?): Double =
    if (weightKg != null && weightKg > 0.0 && reps != null && reps > 0) weightKg * reps else 0.0

/** Epley formula; it is intentionally presented as an estimate. */
fun estimatedOneRepMax(weightKg: Double?, reps: Int?): Double {
    if (weightKg == null || weightKg <= 0.0 || reps == null || reps <= 0) return 0.0
    return weightKg * (1.0 + reps.coerceAtMost(30) / 30.0)
}

fun detectPersonalRecord(
    exerciseName: String,
    current: List<WorkoutSetInput>,
    history: List<WorkoutExercisePerformanceData>,
): PersonalRecordResult? {
    val currentValid = current.filter { (it.reps ?: 0) > 0 }
    if (currentValid.isEmpty()) return null
    val historicSets = history.filter { it.exerciseName.equals(exerciseName, true) }.flatMap { it.sets }
    if (historicSets.isEmpty()) return null
    val maxWeight = currentValid.maxOf { it.weightKg ?: 0.0 }
    val maxVolume = currentValid.maxOf { setVolume(it.weightKg, it.reps) }
    val maxReps = currentValid.maxOf { it.reps ?: 0 }
    val weightPr = maxWeight > historicSets.maxOf { it.weightKg ?: 0.0 }
    val volumePr = maxVolume > historicSets.maxOf { setVolume(it.weightKg, it.reps) }
    val repPr = maxReps > historicSets.maxOf { it.reps ?: 0 }
    if (!weightPr && !volumePr && !repPr) return null
    return PersonalRecordResult(exerciseName, weightPr, volumePr, repPr, currentValid.maxOf { estimatedOneRepMax(it.weightKg, it.reps) })
}

private data class MutableMuscleSummary(
    var score: Double = 0.0,
    var sets: Double = 0.0,
    var volume: Double = 0.0,
    var lastTrainedAt: String? = null,
    val exerciseVolume: MutableMap<String, Double> = linkedMapOf(),
    val exerciseSets: MutableMap<String, Double> = linkedMapOf(),
    val trend: MutableList<Double> = MutableList(4) { 0.0 },
)

fun analyzeTraining(
    performances: List<WorkoutExercisePerformanceData>,
    catalog: List<ExerciseCatalogData> = emptyList(),
    zoneId: ZoneId = ZoneId.systemDefault(),
    today: LocalDate = LocalDate.now(zoneId),
    rangeDays: Int = 7,
    contribution: MuscleContributionConfig = DEFAULT_MUSCLE_CONTRIBUTION,
): TrainingAnalysis {
    val safeRange = rangeDays.coerceIn(1, 365)
    val cutoff = today.minusDays((safeRange - 1).toLong())
    val trendCutoff = today.minusDays(29)
    val catalogById = catalog.associateBy { it.id }
    val summaries = TRACKED_MUSCLES.associateWith { MutableMuscleSummary() }.toMutableMap()
    val daily = DayOfWeek.entries.associateWith { 0.0 }.toMutableMap()
    var volume = 0.0
    var repetitions = 0

    performances.forEach { performance ->
        val date = performanceDate(performance.completedAt, zoneId)
        if (date > today || date < minOf(cutoff, trendCutoff)) return@forEach
        val exercise = performance.exerciseId?.let(catalogById::get)
        val primary = exercise?.primaryMuscles?.ifEmpty { null } ?: listOf(inferMuscle(performance.exerciseName))
        val secondary = exercise?.secondaryMuscles.orEmpty()
        val validSets = performance.sets.filter { (it.reps ?: 0) > 0 || (it.durationSeconds ?: 0) > 0 }
        if (validSets.isEmpty()) return@forEach
        val exerciseVolume = validSets.sumOf { setVolume(it.weightKg, it.reps) }
        val inSelectedRange = date >= cutoff
        if (inSelectedRange) {
            volume += exerciseVolume
            repetitions += validSets.sumOf { it.reps ?: 0 }
            daily[date.dayOfWeek] = daily.getValue(date.dayOfWeek) + exerciseVolume
        }
        fun contribute(rawMuscle: String, factor: Double) {
            val muscle = normalizeMuscle(rawMuscle)
            val summary = summaries.getOrPut(muscle) { MutableMuscleSummary() }
            val weightedVolume = exerciseVolume * factor
            val weightedSets = validSets.size * factor
            if (inSelectedRange) {
                summary.score += weightedVolume.takeIf { it > 0.0 } ?: weightedSets
                summary.volume += weightedVolume
                summary.sets += weightedSets
                summary.exerciseVolume[performance.exerciseName] = (summary.exerciseVolume[performance.exerciseName] ?: 0.0) + weightedVolume
                summary.exerciseSets[performance.exerciseName] = (summary.exerciseSets[performance.exerciseName] ?: 0.0) + weightedSets
                if (summary.lastTrainedAt == null || performance.completedAt > requireNotNull(summary.lastTrainedAt)) summary.lastTrainedAt = performance.completedAt
            }
            if (date >= trendCutoff) {
                val daysAgo = java.time.temporal.ChronoUnit.DAYS.between(date, today).toInt().coerceIn(0, 29)
                val bucket = (3 - daysAgo / 8).coerceIn(0, 3)
                summary.trend[bucket] += weightedVolume.takeIf { it > 0.0 } ?: weightedSets
            }
        }
        primary.forEach { contribute(it, contribution.primary) }
        secondary.forEach { contribute(it, contribution.secondary) }
    }

    val loads = summaries.map { (muscle, summary) ->
        val weeklyEquivalent = summary.sets * 7.0 / safeRange
        MuscleLoad(
            muscle = muscle,
            score = summary.score,
            setEquivalent = summary.sets,
            level = loadLevelForWeeklySets(weeklyEquivalent),
            totalVolumeKg = summary.volume,
            lastTrainedAt = summary.lastTrainedAt,
            exercises = summary.exerciseSets.entries.map { (name, sets) ->
                MuscleExerciseContribution(name, summary.exerciseVolume[name] ?: 0.0, sets)
            }.sortedByDescending { it.volumeKg.takeIf { volume -> volume > 0 } ?: it.setEquivalent },
            trendVolumes = summary.trend.toList(),
        )
    }.sortedWith(compareByDescending<MuscleLoad> { it.score }.thenBy { TRACKED_MUSCLES.indexOf(it.muscle) })

    val trained = loads.filter { it.level != LoadLevel.NONE }
    val overload = trained.firstOrNull { it.level == LoadLevel.OVERLOAD }
    val low = trained.firstOrNull { it.level == LoadLevel.LOW }
    val tr = when {
        trained.isEmpty() -> "Henüz yeterli antrenman verin yok."
        overload != null -> "${muscleNameTr(overload.muscle).replaceFirstChar(Char::uppercase)} yükün bu dönem aşırı yüksek. Toparlanmanı izle ve gerekirse set azalt."
        low != null -> "${muscleNameTr(low.muscle).replaceFirstChar(Char::uppercase)} hacmin bu dönem düşük kaldı. Sonraki programında dengeleyici set ekleyebilirsin."
        else -> "Seçilen dönemde kas dağılımın dengeli görünüyor. Formu koruyarak kademeli ilerle."
    }
    val en = when {
        trained.isEmpty() -> "There is not enough workout data yet."
        overload != null -> "Your ${muscleNameEn(overload.muscle)} load is excessive for this period. Monitor recovery and reduce sets if needed."
        low != null -> "Your ${muscleNameEn(low.muscle)} volume is low for this period. Add balancing sets to your next program."
        else -> "Your muscle distribution looks balanced for the selected period. Keep progressing gradually with good form."
    }
    return TrainingAnalysis(volume, repetitions, loads, daily, tr, en, safeRange)
}

fun loadLevelForWeeklySets(weeklySets: Double): LoadLevel = when {
    weeklySets <= 0.0 -> LoadLevel.NONE
    weeklySets < 6.0 -> LoadLevel.LOW
    weeklySets <= 16.0 -> LoadLevel.BALANCED
    weeklySets <= 22.0 -> LoadLevel.HIGH
    else -> LoadLevel.OVERLOAD
}

private fun performanceDate(value: String, zoneId: ZoneId): LocalDate = runCatching {
    Instant.parse(value).atZone(zoneId).toLocalDate()
}.getOrElse { runCatching { LocalDate.parse(value.take(10)) }.getOrDefault(LocalDate.MIN) }

fun normalizeMuscle(raw: String): String {
    val value = raw.lowercase()
    return when {
        "chest" in value || "göğ" in value || "pect" in value -> "chest"
        "lat" in value || "kanat" in value -> "lats"
        "upper back" in value || "middle back" in value || "sırt" in value || "row" in value -> "upper_back"
        "lower back" in value || "bel" in value -> "lower_back"
        "trap" in value -> "traps"
        "neck" in value || "boyun" in value -> "neck"
        "rear delt" in value || "arka omuz" in value -> "rear_delts"
        "front delt" in value || "ön omuz" in value -> "front_delts"
        "side delt" in value || "yan omuz" in value -> "side_delts"
        "shoulder" in value || "omuz" in value -> "side_delts"
        "biceps" in value || "curl" in value || "pazu" in value || "ön kol" in value -> "biceps"
        "triceps" in value || "arka kol" in value -> "triceps"
        "forearm" in value || "önkol" in value || "bilek" in value -> "forearms"
        "abdominal" in value || "abs" in value || "core" in value || "karın" in value -> "abs"
        "glute" in value || "kalça" in value -> "glutes"
        "abductor" in value || "dış bacak" in value -> "abductors"
        "adductor" in value || "iç bacak" in value -> "adductors"
        "quad" in value || "ön bacak" in value || "leg press" in value || "squat" in value -> "quads"
        "hamstring" in value || "arka bacak" in value || "deadlift" in value -> "hamstrings"
        "calf" in value || "baldır" in value -> "calves"
        else -> raw.ifBlank { "other" }.lowercase().replace(' ', '_')
    }
}

private fun inferMuscle(exercise: String) = normalizeMuscle(exercise)

fun muscleNameTr(muscle: String) = mapOf(
    "chest" to "göğüs", "upper_back" to "üst sırt", "lower_back" to "bel", "lats" to "lat", "traps" to "trapez", "neck" to "boyun",
    "front_delts" to "ön omuz", "side_delts" to "yan omuz", "rear_delts" to "arka omuz",
    "biceps" to "biceps", "triceps" to "triceps", "forearms" to "ön kol", "abs" to "karın",
    "glutes" to "glute", "abductors" to "dış bacak", "adductors" to "iç bacak", "quads" to "quadriceps", "hamstrings" to "hamstring", "calves" to "calf",
)[muscle] ?: muscle.replace('_', ' ')

fun muscleNameEn(muscle: String) = muscle.replace('_', ' ').replaceFirstChar(Char::uppercase)
fun Double.compactKg(): String = if (this >= 1_000) "%.1f ton".format(this / 1_000) else "${roundToInt()} kg"
