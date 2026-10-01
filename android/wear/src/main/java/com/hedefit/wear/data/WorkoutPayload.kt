package com.hedefit.wear.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Saatte kaydedilen bir ağırlık seti. */
data class SetLog(val exerciseId: String, val exerciseName: String, val order: Int, val setNumber: Int, val weightKg: Double, val reps: Int)

/** Biten antrenmanın telefona giden JSON gövdesi. */
object WorkoutPayload {
    fun build(kind: String, durationSec: Long, distanceM: Double, calories: Int, startMillis: Long, sets: List<SetLog>, id: String = UUID.randomUUID().toString()): String =
        JSONObject().put("id", id).put("kind", kind).put("durationSec", durationSec).put("distanceM", distanceM)
            .put("calories", calories).put("start", startMillis)
            .put("sets", JSONArray().also { array ->
                sets.forEach { array.put(JSONObject().put("exId", it.exerciseId).put("exName", it.exerciseName).put("order", it.order).put("setNo", it.setNumber).put("kg", it.weightKg).put("reps", it.reps)) }
            }).toString()
}
