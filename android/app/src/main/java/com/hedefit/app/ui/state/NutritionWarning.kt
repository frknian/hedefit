package com.hedefit.app.ui.state

import com.hedefit.app.data.model.NutritionEstimateData

/**
 * Turns the server's per-item warning code into a message in the app language.
 * The server sends a stable code plus a Turkish sentence; an app that does not know a
 * newer code still shows the sentence instead of nothing.
 */
object NutritionWarning {
    /** Photo items carry no warning code; below this confidence the person should check them. */
    const val LOW_CONFIDENCE = 0.6

    fun text(item: NutritionEstimateData, en: Boolean): String? = text(item.warningCode, item.warning, item.confidence, en)

    fun text(code: String?, serverText: String?, confidence: Double, en: Boolean): String? {
        known(code, en)?.let { return it }
        serverText?.takeIf { it.isNotBlank() }?.let { return it }
        return if (confidence < LOW_CONFIDENCE) known("low_confidence", en) else null
    }

    private fun known(code: String?, en: Boolean): String? = when (code) {
        "approximate_amount" -> if (en) "Amount is approximate; check it before saving." else "Miktar yaklaşık tahmin edildi; kaydetmeden önce kontrol et."
        "cooked_assumed" -> if (en) "Cooked weight assumed. If you weighed it raw or dry, type “çiğ” before the food (e.g. çiğ pirinç); calories are about 2.5x higher."
        else "Pişmiş ağırlık varsayıldı. Çiğ/kuru tarttıysan yiyeceğin başına “çiğ” yaz (örn. çiğ pirinç); kalori yaklaşık 2,5 kat yüksek olur."
        "not_in_catalogue" -> if (en) "Not in our catalogue; a rough average is shown. Check the values before saving." else "Bu yiyecek kataloğumuzda yok; kaba bir ortalama gösteriliyor. Kaydetmeden önce değerleri kontrol et."
        "ai_estimate" -> if (en) "AI estimate; it varies with the recipe and brand." else "Yapay zekâ tahmini; tarife ve markaya göre değişebilir."
        "low_confidence" -> if (en) "Low confidence for this item; check the name and amount." else "Bu kalem için güven düşük; adı ve miktarı kontrol et."
        else -> null
    }
}
