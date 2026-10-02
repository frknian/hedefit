package com.hedefit.app.ui.settings

import android.content.Context
import java.time.LocalDate

/**
 * Remembers, per account, the day this device last generated an AI plan. The
 * "new block" prompt compares that day with today's block; without a marker the
 * caller falls back to the program's own timestamp.
 */
class PlanRotationStore(context: Context) {
    private val preferences = context.getSharedPreferences("hedefit_plan_rotation", Context.MODE_PRIVATE)

    fun generatedOn(userId: String?): LocalDate? =
        userId?.let { preferences.getString("generated_on_$it", null) }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    fun markGenerated(userId: String?, date: LocalDate = LocalDate.now()) {
        if (userId != null) preferences.edit().putString("generated_on_$userId", date.toString()).apply()
    }
}
