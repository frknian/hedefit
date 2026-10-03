package com.hedefit.app.data.model

import org.json.JSONArray
import org.json.JSONObject

/** Sunucudaki AI koç hafızası kaydı (GET /api/ai/memory). Yalnız kullanıcıya gösterilen alanlar. */
data class AiMemoryItem(
    val id: String,
    val type: String,
    val key: String,
    val value: String,
    val userExplicit: Boolean,
    val updatedAt: String?,
)

fun parseAiMemories(array: JSONArray?): List<AiMemoryItem> {
    if (array == null) return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        val id = item.optString("id")
        if (id.isBlank()) return@mapNotNull null
        AiMemoryItem(
            id = id,
            type = item.optString("type"),
            key = item.optString("key"),
            value = item.optString("value"),
            userExplicit = item.optString("source") == "user_explicit",
            updatedAt = item.optString("updatedAt").takeIf { it.isNotBlank() },
        )
    }
}

/** Hafıza kategorisinin kullanıcıya gösterilen adı; bilinmeyen kategori ham adıyla görünür. */
fun aiMemoryTypeLabel(type: String, en: Boolean): String = when (type) {
    "exercise_preference" -> if (en) "Exercise preference" else "Egzersiz tercihi"
    "food_preference" -> if (en) "Food preference" else "Yemek tercihi"
    "coaching_preference" -> if (en) "Coaching preference" else "Koçluk tercihi"
    "schedule_preference" -> if (en) "Schedule preference" else "Program tercihi"
    "goal" -> if (en) "Goal" else "Hedef"
    "constraint" -> if (en) "Constraint (may include health info)" else "Kısıt (sağlık bilgisi içerebilir)"
    "habit" -> if (en) "Habit" else "Alışkanlık"
    "equipment" -> if (en) "Equipment" else "Ekipman"
    "motivation_pattern" -> if (en) "Motivation" else "Motivasyon"
    else -> type
}

/** Hesaptaki açık rıza kayıtları (Supabase user_metadata). Boş/yok = rıza yok. */
data class ConsentStatus(
    val healthDataConsentAt: String?,
    val crossBorderConsentAt: String?,
    val textVersion: String?,
    val withdrawnAt: String?,
)

fun parseConsentStatus(metadata: JSONObject?): ConsentStatus {
    // optString JSON null için "null" döndürür; boş ve "null" değerleri rıza yok sayılır.
    fun clean(name: String): String? = metadata?.optString(name)?.takeIf { it.isNotBlank() && it != "null" }
    return ConsentStatus(
        healthDataConsentAt = clean("health_data_consent_at"),
        crossBorderConsentAt = clean("cross_border_consent_at"),
        textVersion = clean("consent_text_version"),
        withdrawnAt = clean("consent_withdrawn_at"),
    )
}

/**
 * Rızayı geri çekme yaması: seçilen rıza alanları BOŞ dizeye çekilir (hasExplicitConsents boşu
 * "rıza yok" sayar, böylece hesap bir sonraki açılışta yeniden rıza kapısına düşer) ve geri çekme
 * zamanı kaydedilir. JSON null kullanılmaz; org.json bunu "null" metnine çevirir.
 */
fun consentWithdrawalPatch(health: Boolean, crossBorder: Boolean, nowIso: String): JSONObject {
    require(health || crossBorder) { "Geri çekilecek en az bir rıza seçilmeli." }
    val patch = JSONObject().put("consent_withdrawn_at", nowIso)
    if (health) patch.put("health_data_consent_at", "")
    if (crossBorder) patch.put("cross_border_consent_at", "")
    return patch
}
