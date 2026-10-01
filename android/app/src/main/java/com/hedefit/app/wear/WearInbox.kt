package com.hedefit.app.wear

import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONObject

/** Saatte biten antrenman özeti. */
data class WatchWorkout(val id: String, val activityKey: String, val minutes: Int, val distanceKm: Double?)

/**
 * Saatten gelen su eklemeleri ve antrenmanlar. Uygulama açıksa akışa verilir; kapalıysa
 * SharedPreferences'ta biriktirilir ve uygulama açılınca drain ile alınır.
 */
object WearInbox {
    private const val PREFS = "wear_inbox"
    private const val KEY_WATER = "pending_water_ml"
    private const val KEY_WORKOUTS = "pending_workouts"
    private const val KEY_SEEN = "seen_workout_ids"
    private val waterFlow = MutableSharedFlow<Int>(extraBufferCapacity = 16)
    private val workoutFlow = MutableSharedFlow<WatchWorkout>(extraBufferCapacity = 16)
    val water: SharedFlow<Int> = waterFlow.asSharedFlow()
    val workouts: SharedFlow<WatchWorkout> = workoutFlow.asSharedFlow()

    @Synchronized
    fun deliverWater(context: Context, ml: Int) {
        if (ml <= 0 || ml > 20_000) return
        if (waterFlow.subscriptionCount.value > 0 && waterFlow.tryEmit(ml)) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_WATER, (prefs.getInt(KEY_WATER, 0) + ml).coerceAtMost(20_000)).apply()
    }

    @Synchronized
    fun drainPendingWater(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val pending = prefs.getInt(KEY_WATER, 0)
        if (pending != 0) prefs.edit().remove(KEY_WATER).apply()
        return pending
    }

    /** Aynı antrenman (id) yeniden gelirse yok sayılır; saat tekrar gönderebilir. */
    @Synchronized
    fun deliverWorkout(context: Context, json: String) {
        val workout = parse(json) ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val seen = prefs.getStringSet(KEY_SEEN, emptySet()).orEmpty().toMutableSet()
        if (!seen.add(workout.id)) return
        prefs.edit().putStringSet(KEY_SEEN, seen.toList().takeLast(50).toSet()).apply()
        if (workoutFlow.subscriptionCount.value > 0 && workoutFlow.tryEmit(workout)) return
        val pending = prefs.getStringSet(KEY_WORKOUTS, emptySet()).orEmpty() + json
        prefs.edit().putStringSet(KEY_WORKOUTS, pending).apply()
    }

    @Synchronized
    fun drainPendingWorkouts(context: Context): List<WatchWorkout> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val pending = prefs.getStringSet(KEY_WORKOUTS, emptySet()).orEmpty()
        if (pending.isNotEmpty()) prefs.edit().remove(KEY_WORKOUTS).apply()
        return pending.mapNotNull(::parse)
    }

    private val allowedKeys = setOf("running", "walking", "hiking", "cycling", "strength")

    private fun parse(json: String): WatchWorkout? = runCatching {
        val o = JSONObject(json)
        val key = o.getString("kind")
        val minutes = (o.getLong("durationSec") / 60).toInt()
        if (key !in allowedKeys || minutes < 1 || minutes > 1_440) return null
        val km = o.optDouble("distanceM", 0.0) / 1000
        WatchWorkout(o.getString("id"), key, minutes, km.takeIf { it > 0.05 })
    }.getOrNull()
}
