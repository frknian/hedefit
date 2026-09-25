package com.hedefit.app.gym

import android.content.Context
import com.hedefit.app.data.model.WorkoutExerciseData
import com.hedefit.app.data.model.WorkoutSetInput
import com.hedefit.app.ui.state.savedWorkoutSet
import com.hedefit.app.ui.state.toSavedWorkoutSet
import org.json.JSONArray
import org.json.JSONObject

private const val SNAPSHOT_VERSION = 1
const val MAX_ACTIVE_WORKOUT_MILLIS = 24L * 60L * 60L * 1_000L

data class ActiveWorkoutSnapshot(
    val startedAt: Long,
    val exercises: List<WorkoutExerciseData>,
    val exerciseIndex: Int,
    val currentSet: Int,
    val weight: Int,
    val reps: Int,
    val rpe: Int,
    val setType: String,
    val note: String,
    val completedSets: List<WorkoutSetInput>,
    val restDeadlineEpochMs: Long,
    val restPausedSeconds: Int,
)

/** Telefon UI'ından bağımsız, ileride Wear OS Data Layer'a taşınabilecek salt okunur sözleşme. */
data class WearWorkoutState(
    val startedAt: Long,
    val activeExerciseId: String,
    val activeExerciseName: String,
    val currentSet: Int,
    val totalSets: Int,
    val weightKg: Int,
    val reps: Int,
    val restRemainingSeconds: Int,
)

fun ActiveWorkoutSnapshot.toWearWorkoutState(now: Long = System.currentTimeMillis()): WearWorkoutState {
    val active = exercises[exerciseIndex.coerceIn(0, exercises.lastIndex)]
    return WearWorkoutState(
        startedAt, active.id, active.name, currentSet, active.sets, weight, reps,
        restPausedSeconds.takeIf { it > 0 } ?: remainingRestSeconds(restDeadlineEpochMs, now),
    )
}

fun remainingRestSeconds(deadlineEpochMs: Long, nowEpochMs: Long): Int =
    if (deadlineEpochMs <= 0L) 0 else ((deadlineEpochMs - nowEpochMs + 999L) / 1_000L).toInt().coerceAtLeast(0)

fun adjustedRestDeadline(deadlineEpochMs: Long, nowEpochMs: Long, deltaSeconds: Int): Long =
    (if (deadlineEpochMs > nowEpochMs) deadlineEpochMs else nowEpochMs) + deltaSeconds * 1_000L

class ActiveWorkoutStore(context: Context) {
    private val preferences = context.getSharedPreferences("hedefit_active_workout", Context.MODE_PRIVATE)

    fun hasRecoverable(now: Long = System.currentTimeMillis()): Boolean = read(now) != null

    @Synchronized fun write(snapshot: ActiveWorkoutSnapshot) {
        val exercises = JSONArray(snapshot.exercises.map { exercise -> JSONObject()
            .put("id", exercise.id).put("name", exercise.name).put("area", exercise.area)
            .put("sets", exercise.sets).put("reps", exercise.reps).put("restSeconds", exercise.restSeconds) })
        val json = JSONObject()
            .put("version", SNAPSHOT_VERSION).put("startedAt", snapshot.startedAt).put("exercises", exercises)
            .put("exerciseIndex", snapshot.exerciseIndex).put("currentSet", snapshot.currentSet)
            .put("weight", snapshot.weight).put("reps", snapshot.reps).put("rpe", snapshot.rpe)
            .put("setType", snapshot.setType).put("note", snapshot.note)
            .put("completedSets", JSONArray(snapshot.completedSets.map(WorkoutSetInput::toSavedWorkoutSet)))
            .put("restDeadlineEpochMs", snapshot.restDeadlineEpochMs).put("restPausedSeconds", snapshot.restPausedSeconds)
        preferences.edit().putString("snapshot", json.toString()).apply()
    }

    @Synchronized fun read(now: Long = System.currentTimeMillis()): ActiveWorkoutSnapshot? {
        val raw = preferences.getString("snapshot", null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            require(json.optInt("version") == SNAPSHOT_VERSION)
            val startedAt = json.getLong("startedAt")
            require(startedAt in (now - MAX_ACTIVE_WORKOUT_MILLIS)..(now + 60_000L))
            val exerciseArray = json.getJSONArray("exercises")
            val exercises = List(exerciseArray.length()) { index -> exerciseArray.getJSONObject(index).let { item ->
                WorkoutExerciseData(item.getString("id"), item.getString("name"), item.optString("area"), item.getInt("sets"), item.getString("reps"), item.getInt("restSeconds"))
            } }
            require(exercises.isNotEmpty())
            val savedSets = json.optJSONArray("completedSets") ?: JSONArray()
            ActiveWorkoutSnapshot(
                startedAt, exercises,
                json.optInt("exerciseIndex").coerceIn(0, exercises.lastIndex),
                json.optInt("currentSet", 1).coerceAtLeast(1), json.optInt("weight"), json.optInt("reps", 10).coerceAtLeast(1),
                json.optInt("rpe", 7).coerceIn(1, 10), json.optString("setType", "normal"), json.optString("note"),
                List(savedSets.length()) { savedSets.optString(it) }.mapNotNull(::savedWorkoutSet),
                json.optLong("restDeadlineEpochMs"), json.optInt("restPausedSeconds").coerceAtLeast(0),
            )
        }.getOrElse { clear(); null }
    }

    fun clear() { preferences.edit().remove("snapshot").apply() }
}
