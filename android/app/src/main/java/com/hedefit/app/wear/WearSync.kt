package com.hedefit.app.wear

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.hedefit.app.data.model.ChallengeData
import com.hedefit.app.data.model.DashboardData
import com.hedefit.app.data.model.LeaderboardEntryData
import org.json.JSONArray
import org.json.JSONObject

/** Telefon -> saat: günlük özeti Wearable Data Layer ile gönderir. */
object WearSync {
    const val SNAPSHOT_PATH = "/hedefit/snapshot"
    const val WATER_PATH = "/hedefit/water"
    const val WORKOUT_PATH = "/hedefit/workout"
    const val ASK_PATH = "/hedefit/ask"
    const val REPLY_PATH = "/hedefit/reply"
    const val NAV_PATH = "/hedefit/nav"

    fun push(
        context: Context,
        dashboard: DashboardData?,
        stepGoal: Int,
        waterGoalMl: Int,
        language: String = "tr",
        leaderboard: List<LeaderboardEntryData> = emptyList(),
        challenges: List<ChallengeData> = emptyList(),
    ) {
        if (dashboard == null) return
        val exercises = JSONArray().also { array ->
            dashboard.workouts.take(12).forEach {
                array.put(JSONObject().put("id", it.id).put("name", it.name).put("sets", it.sets).put("reps", it.reps).put("rest", it.restSeconds).put("kg", it.targetWeightKg ?: JSONObject.NULL))
            }
        }
        val social = JSONObject().apply {
            val me = leaderboard.firstOrNull { it.isCurrentUser }
            put("rank", me?.rank ?: 0)
            put("total", leaderboard.size)
            put("xp", me?.weeklyXp ?: 0L)
            put("leaders", JSONArray().also { array ->
                leaderboard.take(3).forEach { array.put(JSONObject().put("n", it.user.displayName ?: it.user.username ?: "?").put("xp", it.weeklyXp).put("me", it.isCurrentUser)) }
            })
            challenges.firstOrNull { it.myStatus == "joined" }?.let { put("challenge", it.title) }
        }
        val request = PutDataMapRequest.create(SNAPSHOT_PATH).apply {
            dataMap.putInt("steps", dashboard.steps)
            dataMap.putInt("stepGoal", stepGoal)
            dataMap.putInt("waterMl", dashboard.waterMl)
            dataMap.putInt("waterGoal", waterGoalMl)
            dataMap.putInt("calories", dashboard.activeCalories)
            dataMap.putInt("streakDays", dashboard.streakDays)
            dataMap.putInt("proteinG", dashboard.nutritionLogs.sumOf { it.protein }.toInt())
            dataMap.putInt("proteinGoal", dashboard.nutritionGoal.protein)
            dataMap.putString("workoutName", dashboard.workouts.firstOrNull()?.name ?: "")
            dataMap.putString("lang", language)
            dataMap.putString("exercises", exercises.toString())
            dataMap.putString("social", social.toString())
        }.asPutDataRequest().setUrgent()
        // Saat/eşleşme yoksa görev sessizce başarısız olur; bu beklenen bir durumdur.
        Wearable.getDataClient(context.applicationContext).putDataItem(request)
    }

    /** Dönüş ipucu: saat titreşir ve ok gösterir. */
    fun sendNav(context: Context, maneuverType: String, distanceM: Int) {
        val payload = JSONObject().put("type", maneuverType).put("distanceM", distanceM).toString().toByteArray()
        val appContext = context.applicationContext
        Wearable.getNodeClient(appContext).connectedNodes.addOnSuccessListener { nodes ->
            nodes.forEach { Wearable.getMessageClient(appContext).sendMessage(it.id, NAV_PATH, payload) }
        }
    }

    fun reply(context: Context, nodeId: String, requestId: String, ok: Boolean, text: String) {
        val payload = JSONObject().put("id", requestId).put("ok", ok).put("text", text.take(600)).toString().toByteArray()
        Wearable.getMessageClient(context.applicationContext).sendMessage(nodeId, REPLY_PATH, payload)
    }
}
