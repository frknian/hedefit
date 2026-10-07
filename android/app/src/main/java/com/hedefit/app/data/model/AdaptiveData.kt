package com.hedefit.app.data.model

import org.json.JSONObject

/** Sunucudaki Adaptive Training Engine sonucu (lib/training/adaptive-engine.ts). */
data class AdaptiveResultData(
    val adapted: Boolean,
    /** good | moderate | low | recovery */
    val level: String,
    val score: Int,
    /** normal | reduced | recovery */
    val intensity: String,
    val appliedActions: List<String>,
    /** İstenen ama katmanda kilitli eylemler (shorten, reduce_intensity, replace_exercises, add_mobility, switch_recovery, switch_pilates). */
    val lockedActions: List<String>,
    val exercises: List<WorkoutExerciseData>,
    val estimatedMinutes: Int,
    val wellnessKind: String?,
    val explanationTr: String,
    val explanationEn: String,
    val cycleSignal: String,
    /** Coarse nedenler (low_energy, poor_sleep, sore, pain, training_load) — koça yalnızca bunlar gider. */
    val reasons: List<String> = emptyList(),
    /** Premium: kişiye özel AI açıklaması; yoksa şablon açıklama gösterilir. */
    val aiExplanation: String? = null,
) {
    fun explanation(en: Boolean) = aiExplanation?.takeIf { it.isNotBlank() } ?: if (en) explanationEn.ifBlank { explanationTr } else explanationTr.ifBlank { explanationEn }
}

fun parseAdaptiveResult(json: JSONObject): AdaptiveResultData {
    val r = json.getJSONObject("result")
    fun strings(key: String) = r.optJSONArray(key)?.let { a -> List(a.length()) { a.optString(it) } }.orEmpty()
    val applied = r.optJSONArray("applied")?.let { a -> List(a.length()) { a.optJSONObject(it)?.optString("action").orEmpty() }.filter(String::isNotBlank) }.orEmpty()
    val items = r.optJSONArray("exercises")
    return AdaptiveResultData(
        adapted = r.optBoolean("adapted"),
        level = r.optString("level", "good"),
        score = r.optInt("score", 100),
        intensity = r.optString("intensity", "normal"),
        appliedActions = applied,
        lockedActions = strings("lockedActions"),
        exercises = buildList {
            if (items != null) for (i in 0 until items.length()) items.optJSONObject(i)?.let {
                add(WorkoutExerciseData(it.optString("id"), it.optString("name"), it.optString("area", "Tüm Vücut"), it.optInt("sets", 2), it.optString("reps", "8–10"), it.optInt("restSeconds", 45)))
            }
        },
        estimatedMinutes = r.optInt("estimatedMinutes", 20),
        wellnessKind = r.optString("wellnessKind").takeIf { it.isNotBlank() && it != "null" },
        explanationTr = r.optString("explanationTr"),
        explanationEn = r.optString("explanationEn"),
        cycleSignal = r.optJSONObject("signals")?.optString("cycle", "none") ?: "none",
        reasons = r.optJSONObject("signals")?.optJSONArray("reasons")?.let { a -> List(a.length()) { a.optString(it) } }.orEmpty(),
        aiExplanation = json.optString("aiExplanation").takeIf { it.isNotBlank() && it != "null" },
    )
}

/** Kilitli eylemden hangi yükseltme kapısının gösterileceği (Premium'a özgü olan öne çıkar). */
fun upgradeHint(locked: List<String>): String? = when {
    "switch_pilates" in locked -> "switch_pilates"
    "switch_recovery" in locked || "add_mobility" in locked || "replace_exercises" in locked -> "adaptive"
    else -> null
}

/** Koça giden kaba uyarlama özeti. Ham check-in değerleri, döngü günü/fazı ASLA eklenmez. */
fun AdaptiveResultData.coachSummary(): JSONObject = JSONObject()
    .put("level", level).put("adapted", adapted).put("intensity", intensity)
    .put("actions", org.json.JSONArray(appliedActions)).put("reasons", org.json.JSONArray(reasons))
    .put("estimatedMinutes", estimatedMinutes)
    .also { o -> wellnessKind?.let { o.put("sessionKind", it) } }

/** Sunucudaki beslenme kişiselleştirmesi (lib/nutrition-wellness.ts). Tamamen genel ipuçları; tıbbi değil. */
data class NutritionTipData(val id: String, val title: String, val body: String)

data class NutritionWellnessData(val tips: List<NutritionTipData>, val proteinBonusGrams: Int, val lockedTipCount: Int)

fun parseNutritionWellness(json: JSONObject): NutritionWellnessData {
    val items = json.optJSONArray("tips")
    return NutritionWellnessData(
        tips = buildList { if (items != null) for (i in 0 until items.length()) items.optJSONObject(i)?.let { add(NutritionTipData(it.optString("id"), it.optString("title"), it.optString("body"))) } },
        proteinBonusGrams = json.optInt("proteinBonusGrams", 0),
        lockedTipCount = json.optInt("lockedTipCount", 0),
    )
}

/** Beslenme tercihi: profil cevaplarından (Standart / Vejetaryen / Vegan / Pesketaryen). */
fun dietFromAnswers(answers: List<String>): String = when {
    "Vegan" in answers -> "vegan"
    "Vejetaryen" in answers -> "vegetarian"
    "Pesketaryen" in answers -> "pescatarian"
    else -> "standard"
}
