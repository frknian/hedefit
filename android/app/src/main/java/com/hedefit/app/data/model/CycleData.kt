package com.hedefit.app.data.model

import org.json.JSONObject
import java.time.LocalDate

/**
 * İsteğe bağlı döngü takibi. Sunucudaki `cycle_profiles` ile birebir; faz ve tahmin saklanmaz,
 * sunucu her yanıtta tarihten hesaplar (lib/cycle.ts).
 */
data class CycleProfileData(
    val trackingEnabled: Boolean = false,
    val lastPeriodStart: String? = null,
    val cycleLengthDays: Int? = null,
    val periodLengthDays: Int? = null,
    val regularity: String = "unknown",
) {
    fun toJson(localDate: LocalDate = LocalDate.now()): JSONObject = JSONObject()
        .put("trackingEnabled", trackingEnabled)
        .put("lastPeriodStart", lastPeriodStart ?: JSONObject.NULL)
        .put("cycleLengthDays", cycleLengthDays ?: JSONObject.NULL)
        .put("periodLengthDays", periodLengthDays ?: JSONObject.NULL)
        .put("regularity", regularity)
        .put("localDate", localDate.toString())
}

data class CycleStateData(
    val cycleDay: Int,
    /** menstrual | follicular | ovulatory | luteal; düzensiz ya da bayat veride null (tahmin yok). */
    val phase: String?,
    val periodLikely: Boolean,
    val nextPeriodStart: String,
    val daysToNextPeriod: Int,
    val stale: Boolean,
)

data class CycleSnapshot(val profile: CycleProfileData, val state: CycleStateData?)

fun parseCycleSnapshot(json: JSONObject): CycleSnapshot {
    val p = json.optJSONObject("profile")
    val profile = CycleProfileData(
        trackingEnabled = p?.optBoolean("trackingEnabled", false) ?: false,
        lastPeriodStart = p?.optString("lastPeriodStart")?.takeIf { it.isNotBlank() && it != "null" },
        cycleLengthDays = p?.takeIf { it.has("cycleLengthDays") && !it.isNull("cycleLengthDays") }?.optInt("cycleLengthDays"),
        periodLengthDays = p?.takeIf { it.has("periodLengthDays") && !it.isNull("periodLengthDays") }?.optInt("periodLengthDays"),
        regularity = p?.optString("regularity", "unknown")?.ifBlank { "unknown" } ?: "unknown",
    )
    val s = json.optJSONObject("state")
    val state = s?.let {
        CycleStateData(
            cycleDay = it.optInt("cycleDay"),
            phase = if (it.isNull("phase")) null else it.optString("phase").ifBlank { null },
            periodLikely = it.optBoolean("periodLikely"),
            nextPeriodStart = it.optString("nextPeriodStart"),
            daysToNextPeriod = it.optInt("daysToNextPeriod"),
            stale = it.optBoolean("stale"),
        )
    }
    return CycleSnapshot(profile, state)
}

private fun normalizedGender(gender: String) = gender.trim().lowercase(java.util.Locale.ROOT)

private val FEMALE_VALUES = setOf("kadın", "kadin", "female", "woman")
private val MALE_VALUES = setOf("erkek", "male", "man")

/** Onboarding'de döngü sorusunu yalnızca kadın olarak belirten kullanıcıya sorarız; başkasına asla. */
fun cycleOptInInOnboarding(gender: String) = normalizedGender(gender) in FEMALE_VALUES

/**
 * Ayarlar → "Döngü takibi (isteğe bağlı)" girişi: erkek olarak belirten kullanıcıya gösterilmez; kadın,
 * "diğer" ya da belirtmeyen herkes erişebilir (ikili cinsiyet varsayımına kilitlenmemek için).
 */
fun cycleSettingsVisible(gender: String) = normalizedGender(gender) !in MALE_VALUES
