package com.hedefit.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Görsel adayları: RepDB klasörleri (`start`/`main`/`peak.webp`) HER ZAMAN önce denenir; eski
 * (free-exercise-db) planlar için elle eşlenmiş `legacyWorkoutImageIds` yedeği sonda gelir
 * (bkz. ExerciseMedia.kt açıklaması). `name` parametresi artık görsel yolunu etkilemez.
 */
class ExerciseMediaTest {
    @Test fun repdbCandidatesComeFirstAndLegacyMappingIsTheFallback() {
        assertEquals(
            listOf(
                "/exercise-images/barbell-bench-press/start.webp",
                "/exercise-images/barbell-bench-press/main.webp",
                "/exercise-images/barbell-bench-press/peak.webp",
                "/exercise-images/Barbell_Bench_Press_-_Medium_Grip/0.jpg",
                "/exercise-images/Barbell_Bench_Press_-_Medium_Grip/1.jpg",
            ),
            workoutExerciseImagePaths("barbell-bench-press", "Barbell Bench Press"),
        )
    }

    @Test fun catalogIdIsPreservedAsTheFirstFolder() {
        // Eşlemede olmayan (zaten katalog) kimlik: yalnız RepDB adayları, eski yedek eklenmez.
        assertEquals(
            listOf(
                "/exercise-images/Wide-Grip_Lat_Pulldown/start.webp",
                "/exercise-images/Wide-Grip_Lat_Pulldown/main.webp",
                "/exercise-images/Wide-Grip_Lat_Pulldown/peak.webp",
            ),
            workoutExerciseImagePaths("Wide-Grip_Lat_Pulldown", "Wide-Grip Lat Pulldown"),
        )
    }

    @Test fun unknownSlugUsesItsOwnFolderAndDoesNotGuessFromTheName() {
        val paths = workoutExerciseImagePaths("face-pull-v2", "Face Pull")
        assertEquals("/exercise-images/face-pull-v2/start.webp", paths.first())
        assertEquals(3, paths.size)
        assertEquals(false, paths.any { it.contains("Face_Pull") })
    }

    @Test fun legacyLookupIsCaseInsensitive() {
        assertEquals(
            workoutExerciseImagePaths("barbell-bench-press", "x").drop(3),
            workoutExerciseImagePaths("Barbell-Bench-Press", "x").drop(3),
        )
    }
}
