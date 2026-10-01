package com.hedefit.app.gym

import com.hedefit.app.data.model.ExerciseCatalogData
import com.hedefit.app.data.model.WorkoutExercisePerformanceData
import com.hedefit.app.data.model.WorkoutSetInput
import com.hedefit.app.data.model.WorkoutSetPerformanceData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import com.hedefit.app.data.model.WorkoutExerciseData

class TrainingAnalyticsTest {
    @Test fun volumeIgnoresIncompleteAndBodyweightSets() {
        assertEquals(600.0, setVolume(50.0, 12), 0.001)
        assertEquals(0.0, setVolume(0.0, 12), 0.001)
        assertEquals(0.0, setVolume(50.0, null), 0.001)
    }

    @Test fun estimatedOneRepMaxIsDeterministic() {
        assertEquals(96.0, estimatedOneRepMax(80.0, 6), 0.001)
    }

    @Test fun prRequiresHistoricImprovementAndAvoidsFalsePositive() {
        val history = listOf(performance("bench", "Bench Press", 80.0, 6))
        assertNull(detectPersonalRecord("Bench Press", listOf(set("bench", "Bench Press", 80.0, 6)), history))
        val result = detectPersonalRecord("Bench Press", listOf(set("bench", "Bench Press", 82.5, 6)), history)
        assertNotNull(result)
        assertTrue(requireNotNull(result).highestWeight)
    }

    @Test fun primaryAndSecondaryMusclesUseConfiguredWeights() {
        val catalog = listOf(ExerciseCatalogData("pulldown", "Lat Pulldown", "beginner", "machine", listOf("lats"), emptyList(), "strength", emptyList(), listOf("biceps")))
        val analysis = analyzeTraining(listOf(performance("pulldown", "Lat Pulldown", 50.0, 10, "2026-09-09T10:00:00Z")), catalog, ZoneId.of("UTC"), LocalDate.of(2026, 9, 9))
        val lats = analysis.muscleLoads.first { it.muscle == "lats" }
        val biceps = analysis.muscleLoads.first { it.muscle == "biceps" }
        assertEquals(lats.score * SECONDARY_MUSCLE_FACTOR, biceps.score, 0.001)
        assertEquals(500.0, analysis.totalVolumeKg, 0.001)
        assertEquals(0.5, biceps.setEquivalent, 0.001)
        assertEquals(250.0, biceps.totalVolumeKg, 0.001)
        assertEquals("Lat Pulldown", biceps.exercises.single().exerciseName)
    }

    @Test fun muscleMapKeepsNoDataRegionsAndHonorsSelectedRange() {
        val catalog = listOf(ExerciseCatalogData("bench", "Bench Press", "beginner", "barbell", listOf("chest"), emptyList(), "strength", emptyList(), listOf("triceps")))
        val history = listOf(
            performance("bench", "Bench Press", 80.0, 10, "2026-09-09T10:00:00Z"),
            performance("bench", "Bench Press", 60.0, 10, "2026-08-01T10:00:00Z"),
        )
        val week = analyzeTraining(history, catalog, ZoneId.of("UTC"), LocalDate.of(2026, 9, 10), rangeDays = 7)
        val threeMonths = analyzeTraining(history, catalog, ZoneId.of("UTC"), LocalDate.of(2026, 9, 10), rangeDays = 90)
        assertEquals(TRACKED_MUSCLES.size, week.muscleLoads.count { it.muscle in TRACKED_MUSCLES })
        assertEquals(LoadLevel.NONE, week.muscleLoads.first { it.muscle == "calves" }.level)
        assertTrue(threeMonths.muscleLoads.first { it.muscle == "chest" }.totalVolumeKg > week.muscleLoads.first { it.muscle == "chest" }.totalVolumeKg)
    }

    @Test fun absoluteWeeklySetBandsExposeOverloadWithoutRelativePercentages() {
        assertEquals(LoadLevel.NONE, loadLevelForWeeklySets(0.0))
        assertEquals(LoadLevel.LOW, loadLevelForWeeklySets(5.0))
        assertEquals(LoadLevel.BALANCED, loadLevelForWeeklySets(12.0))
        assertEquals(LoadLevel.HIGH, loadLevelForWeeklySets(20.0))
        assertEquals(LoadLevel.OVERLOAD, loadLevelForWeeklySets(24.0))
    }

    @Test fun everyCatalogMuscleUsedByHedefitHasAVisibleMapRegion() {
        val catalogMuscles = listOf("abdominals", "abductors", "adductors", "biceps", "calves", "chest", "forearms", "glutes", "hamstrings", "lats", "lower back", "middle back", "neck", "quadriceps", "shoulders", "traps", "triceps")
        assertTrue(catalogMuscles.all { normalizeMuscle(it) in TRACKED_MUSCLES })
    }

    @Test fun completedBodyweightSetsColorTheMapWithoutInventingKilograms() {
        val catalog = listOf(ExerciseCatalogData("pullup", "Pull-up", "beginner", "body only", listOf("lats"), emptyList(), "strength", emptyList(), listOf("biceps")))
        val sets = listOf(
            WorkoutSetPerformanceData(1, null, 8, null, 8),
            WorkoutSetPerformanceData(2, null, 7, null, 8),
            WorkoutSetPerformanceData(3, null, 6, null, 8),
        )
        val performance = WorkoutExercisePerformanceData("session", "pullup", "Pull-up", "2026-09-10T10:00:00Z", sets)
        val analysis = analyzeTraining(listOf(performance), catalog, ZoneId.of("UTC"), LocalDate.of(2026, 9, 10))
        val lats = analysis.muscleLoads.first { it.muscle == "lats" }
        val biceps = analysis.muscleLoads.first { it.muscle == "biceps" }
        assertEquals(3.0, lats.setEquivalent, .001)
        assertEquals(1.5, biceps.setEquivalent, .001)
        assertEquals(0.0, analysis.totalVolumeKg, .001)
        assertEquals(LoadLevel.LOW, lats.level)
    }

    @Test fun realisticBenchSessionsShowPrimaryMuscleBeforeSecondaryAndLeaveUntrainedLegsEmpty() {
        val catalog = listOf(ExerciseCatalogData("bench", "Bench Press", "beginner", "barbell", listOf("chest"), emptyList(), "strength", emptyList(), listOf("triceps")))
        val threeSets = (1..3).map { WorkoutSetPerformanceData(it, 60.0, 10, null, 8) }
        val history = listOf(
            WorkoutExercisePerformanceData("one", "bench", "Bench Press", "2026-09-08T10:00:00Z", threeSets),
            WorkoutExercisePerformanceData("two", "bench", "Bench Press", "2026-09-10T10:00:00Z", threeSets),
        )
        val analysis = analyzeTraining(history, catalog, ZoneId.of("UTC"), LocalDate.of(2026, 9, 10))
        val chest = analysis.muscleLoads.first { it.muscle == "chest" }
        val triceps = analysis.muscleLoads.first { it.muscle == "triceps" }
        val quads = analysis.muscleLoads.first { it.muscle == "quads" }
        assertEquals(6.0, chest.setEquivalent, .001)
        assertEquals(LoadLevel.BALANCED, chest.level)
        assertEquals(3.0, triceps.setEquivalent, .001)
        assertEquals(LoadLevel.LOW, triceps.level)
        assertEquals(LoadLevel.NONE, quads.level)
    }

    @Test fun timestampRestTimerSurvivesBackgroundTime() {
        assertEquals(90, remainingRestSeconds(190_000L, 100_000L))
        assertEquals(0, remainingRestSeconds(90_000L, 100_000L))
        assertEquals(220_000L, adjustedRestDeadline(190_000L, 100_000L, 30))
    }

    @Test fun activeSnapshotCanBeProjectedForFutureWearClient() {
        val snapshot = ActiveWorkoutSnapshot(1_000L, listOf(WorkoutExerciseData("bench", "Bench Press", "chest", 3, "8-12", 90)), 0, 2, 80, 6, 8, "normal", "", emptyList(), 190_000L, 0)
        val wear = snapshot.toWearWorkoutState(100_000L)
        assertEquals("Bench Press", wear.activeExerciseName)
        assertEquals(2, wear.currentSet)
        assertEquals(90, wear.restRemainingSeconds)
    }

    private fun set(id: String, name: String, weight: Double, reps: Int) = WorkoutSetInput(id, name, 1, 1, weight, reps, null, 8)
    private fun performance(id: String, name: String, weight: Double, reps: Int, at: String = "2026-09-01T10:00:00Z") = WorkoutExercisePerformanceData("session", id, name, at, listOf(WorkoutSetPerformanceData(1, weight, reps, null, 8)))
}
