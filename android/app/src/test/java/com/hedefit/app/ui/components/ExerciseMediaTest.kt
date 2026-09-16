package com.hedefit.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ExerciseMediaTest {
    @Test fun legacyTemplateIdResolvesToCatalogAsset() {
        assertEquals(
            "/exercise-images/Barbell_Bench_Press_-_Medium_Grip/0.jpg",
            workoutExerciseImagePaths("barbell-bench-press", "Barbell Bench Press").first(),
        )
    }

    @Test fun catalogIdIsPreserved() {
        assertEquals(
            "/exercise-images/Wide-Grip_Lat_Pulldown/1.jpg",
            workoutExerciseImagePaths("Wide-Grip_Lat_Pulldown", "Wide-Grip Lat Pulldown")[1],
        )
    }

    @Test fun unknownSlugFallsBackToExerciseName() {
        assertEquals(
            "/exercise-images/Face_Pull/0.jpg",
            workoutExerciseImagePaths("face-pull-v2", "Face Pull").first(),
        )
    }
}
