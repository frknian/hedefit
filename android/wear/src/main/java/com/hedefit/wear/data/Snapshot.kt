package com.hedefit.wear.data

import android.content.Context
import com.google.android.gms.wearable.DataMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Telefondaki aktif programdan bir egzersiz. */
data class PlannedExercise(val id: String, val name: String, val sets: Int, val reps: Int, val restSeconds: Int, val kg: Double?)

data class Leader(val name: String, val xp: Long, val me: Boolean)

/** Sıralama ve meydan okuma; telefon yüklediyse dolu gelir. */
data class SocialInfo(val rank: Int = 0, val total: Int = 0, val weeklyXp: Long = 0, val leaders: List<Leader> = emptyList(), val challenge: String = "")

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
    val lang: String = "",
    val exercises: List<PlannedExercise> = emptyList(),
    val social: SocialInfo = SocialInfo(),
) {
    fun stepFraction() = fraction(steps, stepGoal)
    fun waterFraction() = fraction(waterMl, waterGoal)
    fun proteinFraction() = fraction(proteinG, proteinGoal)
    private fun fraction(v: Int, goal: Int) = if (goal > 0) (v.toFloat() / goal).coerceIn(0f, 1f) else 0f

    /** Arayüz dili: telefonun ayarı, yoksa saatin dili. */
    val isEnglish: Boolean get() = (lang.ifBlank { java.util.Locale.getDefault().language }) != "tr"

    companion object {
        fun from(map: DataMap) = Snapshot(
            steps = map.getInt("steps"), stepGoal = map.getInt("stepGoal", 8_000),
            waterMl = map.getInt("waterMl"), waterGoal = map.getInt("waterGoal", 2_500),
            calories = map.getInt("calories"), streakDays = map.getInt("streakDays"),
            proteinG = map.getInt("proteinG"), proteinGoal = map.getInt("proteinGoal"),
            workoutName = map.getString("workoutName", ""), lang = map.getString("lang", ""),
            exercises = parseExercises(map.getString("exercises", "[]")), social = parseSocial(map.getString("social", "{}")),
        )

        fun parseExercises(json: String): List<PlannedExercise> = runCatching {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                PlannedExercise(
                    o.getString("id"), o.getString("name"), o.optInt("sets", 3).coerceIn(1, 20),
                    Regex("\\d+").find(o.optString("reps"))?.value?.toIntOrNull()?.coerceIn(1, 100) ?: 10,
                    o.optInt("rest", 90).coerceIn(10, 600), o.optDouble("kg", Double.NaN).takeIf { !it.isNaN() },
                )
            }
        }.getOrDefault(emptyList())

        fun parseSocial(json: String): SocialInfo = runCatching {
            val o = JSONObject(json)
            val leaders = o.optJSONArray("leaders")?.let { a -> (0 until a.length()).map { a.getJSONObject(it).let { l -> Leader(l.optString("n"), l.optLong("xp"), l.optBoolean("me")) } } }.orEmpty()
            SocialInfo(o.optInt("rank"), o.optInt("total"), o.optLong("xp"), leaders, o.optString("challenge"))
        }.getOrDefault(SocialInfo())
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
            p.getInt("calories", 0), p.getInt("streakDays", 0), p.getInt("proteinG", 0), p.getInt("proteinGoal", 0),
            p.getString("workoutName", "") ?: "", p.getString("lang", "") ?: "",
            Snapshot.parseExercises(p.getString("exercises", "[]") ?: "[]"), Snapshot.parseSocial(p.getString("social", "{}") ?: "{}"),
        )
        flow.value = value
        return value
    }

    fun save(context: Context, value: Snapshot) {
        val exercises = JSONArray().also { a -> value.exercises.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("sets", it.sets).put("reps", it.reps.toString()).put("rest", it.restSeconds).put("kg", it.kg ?: JSONObject.NULL)) } }
        val social = JSONObject().put("rank", value.social.rank).put("total", value.social.total).put("xp", value.social.weeklyXp).put("challenge", value.social.challenge)
            .put("leaders", JSONArray().also { a -> value.social.leaders.forEach { a.put(JSONObject().put("n", it.name).put("xp", it.xp).put("me", it.me)) } })
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("steps", value.steps).putInt("stepGoal", value.stepGoal)
            .putInt("waterMl", value.waterMl).putInt("waterGoal", value.waterGoal)
            .putInt("calories", value.calories).putInt("streakDays", value.streakDays)
            .putInt("proteinG", value.proteinG).putInt("proteinGoal", value.proteinGoal)
            .putString("workoutName", value.workoutName).putString("lang", value.lang)
            .putString("exercises", exercises.toString()).putString("social", social.toString()).apply()
        flow.value = value
    }
}
