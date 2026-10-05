package com.hedefit.app.data.model

import org.json.JSONObject

/** Sunucunun (lib/training/wellness-session.ts) ürettiği Pilates / toparlanma / mobilite oturumu. */
data class WellnessSessionData(
    val kind: String,
    val title: String,
    val subtitle: String,
    val estimatedMinutes: Int,
    val exercises: List<WorkoutExerciseData>,
)

enum class WellnessKind(val wire: String) {
    PilatesToday("pilates_today"), LowImpactRecovery("low_impact_recovery"), PostureMobility("posture_mobility");

    companion object { fun fromWire(value: String) = entries.firstOrNull { it.wire == value } }
}

fun parseWellnessSession(json: JSONObject): WellnessSessionData {
    val session = json.getJSONObject("session")
    val items = session.optJSONArray("exercises")
    return WellnessSessionData(
        kind = session.optString("kind"),
        title = session.optString("title"),
        subtitle = session.optString("subtitle"),
        estimatedMinutes = session.optInt("estimatedMinutes", 20),
        exercises = buildList {
            if (items != null) for (index in 0 until items.length()) items.optJSONObject(index)?.let {
                add(WorkoutExerciseData(it.optString("id"), it.optString("name"), it.optString("area", "Tüm Vücut"), it.optInt("sets", 2), it.optString("reps", "8–10"), it.optInt("restSeconds", 25)))
            }
        },
    )
}

/**
 * "Bugün için öneriler" satırı ana ekranda herkese gösterilir; Pilates/mobilite/barre/düşük etkili/toparlanma
 * tercihi olanlara ya da kadın olarak belirtenlere yalnızca daha YUKARIDA sunulur (cinsiyete kilitli bir mod yoktur).
 */
fun wellnessProminent(gender: String, preferredStyles: String): Boolean {
    val prefersWellness = listOf("pilates", "mobilite", "mobility", "barre", "düşük etkili", "low impact", "toparlanma", "recovery")
        .any { preferredStyles.lowercase(java.util.Locale.ROOT).contains(it) }
    return prefersWellness || cycleOptInInOnboarding(gender)
}
