package com.hedefit.app.data.model

import org.json.JSONObject
import java.time.LocalDate

/**
 * Günlük check-in (kısa): enerji, uyku, kas ağrısı, ağrı, müsait süre. Sunucudaki `daily_checkins` ile birebir;
 * döngü bilgisi burada tutulmaz.
 */
data class CheckinData(
    val day: String,
    val energy: Int,
    val sleepQuality: Int,
    val sleepHours: Double? = null,
    val soreness: Int = 0,
    val pain: Int = 0,
    val availableMinutes: Int? = null,
) {
    fun toJson(localDate: LocalDate = LocalDate.now()): JSONObject = JSONObject()
        .put("day", day).put("energy", energy).put("sleepQuality", sleepQuality)
        .put("sleepHours", sleepHours ?: JSONObject.NULL)
        .put("soreness", soreness).put("pain", pain)
        .put("availableMinutes", availableMinutes ?: JSONObject.NULL)
        .put("localDate", localDate.toString())

    /** Mevcut hazırlık adaptörünün girdisi: sunucudaki `readinessFromCheckin` ile aynı formül. */
    fun toReadinessInput() = DailyReadinessInput(
        energy = energy,
        sleepQuality = sleepQuality,
        fatigue = ((11 - energy) * 0.6 + soreness * 0.4).let { Math.round(it).toInt() }.coerceIn(1, 10),
        hasSoreness = soreness >= 3,
        sorenessAreas = emptyList(),
        discomfortLevel = maxOf(1, pain),
    )
}

fun parseCheckin(json: JSONObject?): CheckinData? {
    if (json == null || json.isNull("energy")) return null
    return CheckinData(
        day = json.optString("day"),
        energy = json.optInt("energy"),
        sleepQuality = json.optInt("sleepQuality"),
        sleepHours = if (json.isNull("sleepHours")) null else json.optDouble("sleepHours"),
        soreness = json.optInt("soreness"),
        pain = json.optInt("pain"),
        availableMinutes = if (json.isNull("availableMinutes")) null else json.optInt("availableMinutes"),
    )
}

/** PUT /api/checkin yanıtı: kaydedilen check-in + (yalnızca etkinleştirilmiş ve izinliyse) döngü durumu. */
data class CheckinSaveResult(val checkin: CheckinData, val cycle: CycleStateData?)

fun parseCheckinSave(json: JSONObject): CheckinSaveResult {
    val checkin = parseCheckin(json.optJSONObject("checkin")) ?: CheckinData("", 6, 6)
    val cycle = json.optJSONObject("cycle")?.let {
        CycleStateData(
            cycleDay = it.optInt("cycleDay"),
            phase = if (it.isNull("phase")) null else it.optString("phase").ifBlank { null },
            periodLikely = it.optBoolean("periodLikely"),
            nextPeriodStart = it.optString("nextPeriodStart"),
            daysToNextPeriod = it.optInt("daysToNextPeriod"),
            stale = it.optBoolean("stale"),
        )
    }
    return CheckinSaveResult(checkin, cycle)
}

/** Hızlı seçim çiplerinin değerleri (UI ile sunucu aralığı: enerji/uyku 1–10, ağrı/kas ağrısı 0–10). */
object CheckinChoices {
    val energy = listOf(2, 4, 6, 8, 10)
    /** Uyku süresi çipi → (saat, kalite). */
    val sleep = listOf(4.5 to 3, 5.5 to 5, 7.5 to 8, 9.5 to 8)
    val soreness = listOf(0, 3, 6, 9)
    val pain = listOf(0, 3, 6, 9)
    val minutes = listOf(15, 20, 30, 45, 60)
}
