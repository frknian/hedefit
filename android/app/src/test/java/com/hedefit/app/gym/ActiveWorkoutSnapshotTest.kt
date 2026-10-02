package com.hedefit.app.gym

import com.hedefit.app.data.model.WorkoutExerciseData
import com.hedefit.app.data.model.WorkoutSetInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ActiveWorkoutSnapshotTest {
    private val now = 1_800_000_000_000L
    private val snapshot = ActiveWorkoutSnapshot(
        startedAt = now - 600_000L,
        exercises = listOf(WorkoutExerciseData("bench", "Bench Press", "Göğüs", 4, "8–12", 90)),
        exerciseIndex = 0, currentSet = 3, weight = 60, reps = 10, rpe = 8, setType = "normal", note = "ağır geldi",
        completedSets = listOf(WorkoutSetInput("bench", "Bench Press", 1, 1, 60.0, 10, null, 7, "normal", "")),
        restDeadlineEpochMs = now + 30_000L, restPausedSeconds = 0, savedAt = now,
    )

    @Test
    fun snapshotSurvivesJsonRoundTripBetweenDevices() {
        val restored = snapshotFromJson(snapshotToJson(snapshot).toString(), now)
        assertNotNull(restored)
        assertEquals(snapshot, restored)
    }

    @Test
    fun staleOrCorruptRemoteSnapshotsAreRejected() {
        assertNull(snapshotFromJson("{", now))
        assertNull(snapshotFromJson(snapshotToJson(snapshot).toString(), now + MAX_ACTIVE_WORKOUT_MILLIS + 1_000L))
    }
}
