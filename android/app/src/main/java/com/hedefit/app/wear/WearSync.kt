package com.hedefit.app.wear

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.hedefit.app.data.model.DashboardData

/** Telefon -> saat: günlük özeti Wearable Data Layer ile gönderir. */
object WearSync {
    const val SNAPSHOT_PATH = "/hedefit/snapshot"
    const val WATER_PATH = "/hedefit/water"
    const val WORKOUT_PATH = "/hedefit/workout"

    fun push(context: Context, dashboard: DashboardData?, stepGoal: Int, waterGoalMl: Int) {
        if (dashboard == null) return
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
        }.asPutDataRequest().setUrgent()
        // Saat/eşleşme yoksa görev sessizce başarısız olur; bu beklenen bir durumdur.
        Wearable.getDataClient(context.applicationContext).putDataItem(request)
    }
}
