package com.hedefit.wear.data

import android.content.Context
import com.google.android.gms.wearable.DataMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Telefondan gelen günlük özet. */
data class Snapshot(
    val steps: Int = 0,
    val stepGoal: Int = 8_000,
    val waterMl: Int = 0,
    val waterGoal: Int = 2_500,
    val calories: Int = 0,
    val streakDays: Int = 0,
    val proteinG: Int = 0,
    val proteinGoal: Int = 0,
    val workoutName: String = "",
) {
    fun stepFraction() = fraction(steps, stepGoal)
    fun waterFraction() = fraction(waterMl, waterGoal)
    fun proteinFraction() = fraction(proteinG, proteinGoal)
    private fun fraction(v: Int, goal: Int) = if (goal > 0) (v.toFloat() / goal).coerceIn(0f, 1f) else 0f

    companion object {
        fun from(map: DataMap) = Snapshot(
            steps = map.getInt("steps"), stepGoal = map.getInt("stepGoal", 8_000),
            waterMl = map.getInt("waterMl"), waterGoal = map.getInt("waterGoal", 2_500),
            calories = map.getInt("calories"), streakDays = map.getInt("streakDays"),
            proteinG = map.getInt("proteinG"), proteinGoal = map.getInt("proteinGoal"),
            workoutName = map.getString("workoutName", ""),
        )
    }
}

/** Son özet SharedPreferences'ta saklanır; tile, complication ve arayüz aynı kaynağı okur. */
object SnapshotStore {
    private const val PREFS = "snapshot"
    private val flow = MutableStateFlow(Snapshot())
    val snapshot: StateFlow<Snapshot> = flow.asStateFlow()

    fun load(context: Context): Snapshot {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val value = Snapshot(
            p.getInt("steps", 0), p.getInt("stepGoal", 8_000), p.getInt("waterMl", 0), p.getInt("waterGoal", 2_500),
            p.getInt("calories", 0), p.getInt("streakDays", 0), p.getInt("proteinG", 0), p.getInt("proteinGoal", 0), p.getString("workoutName", "") ?: "",
        )
        flow.value = value
        return value
    }

    fun save(context: Context, value: Snapshot) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("steps", value.steps).putInt("stepGoal", value.stepGoal)
            .putInt("waterMl", value.waterMl).putInt("waterGoal", value.waterGoal)
            .putInt("calories", value.calories).putInt("streakDays", value.streakDays)
            .putInt("proteinG", value.proteinG).putInt("proteinGoal", value.proteinGoal)
            .putString("workoutName", value.workoutName).apply()
        flow.value = value
    }
}
