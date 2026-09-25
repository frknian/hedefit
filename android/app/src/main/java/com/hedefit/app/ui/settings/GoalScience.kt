package com.hedefit.app.ui.settings

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Bilimsel dayanaklı hedef hesapları. Kaynaklar [GOAL_SOURCES] içinde; değerler
 * değiştirilirken kaynakla birlikte güncellenmeli.
 */
enum class GoalPace(val lossFraction: Double, val gainFraction: Double) {
    // Kilo verme: haftada vücut ağırlığının %0,5–1'i kas kaybını en aza indirir (Helms 2014).
    // Kilo alma: haftada %0,25–0,5 kas kazanımını yağ artışına göre en iyi dengeler (Iraki 2019).
    Slow(0.005, 0.0025),
    Steady(0.0075, 0.00375),
    Fast(0.010, 0.005),
}

object GoalScience {
    const val KCAL_PER_KG = 7_700
    /** Paluch 2022: ölüm riskindeki azalma yetişkinlerde 8–10 bin adımda plato yapar. */
    const val STEP_TARGET = 8_000

    /** Haftalık değişim (kg, işaretsiz). Tavan: vücut ağırlığının %1'i; alt sınır 0,1 kg. */
    fun weeklyRateKg(currentKg: Double, targetKg: Double, pace: GoalPace): Double {
        val fraction = if (targetKg < currentKg) pace.lossFraction else pace.gainFraction
        return (currentKg * fraction).coerceAtLeast(0.1)
    }

    fun weeks(currentKg: Double?, targetKg: Double?, pace: GoalPace = GoalPace.Steady): Int? {
        if (currentKg == null || targetKg == null) return null
        val diff = abs(targetKg - currentKg)
        if (diff < 0.1) return 0
        return ceil(diff / weeklyRateKg(currentKg, targetKg, pace)).toInt().coerceIn(1, 208)
    }

    /** Hedef hıza karşılık gelen günlük enerji farkı (kcal). */
    fun dailyEnergyDelta(currentKg: Double, targetKg: Double, pace: GoalPace): Int =
        (weeklyRateKg(currentKg, targetKg, pace) * KCAL_PER_KG / 7).roundToInt()

    /**
     * İçilecek su (ml). EFSA 2010: toplam su (yiyecek dahil) kadın 2,0 L, erkek 2,5 L;
     * bunun ~%20'si yiyeceklerden gelir. Kiloya göre ~30 ml/kg ile ölçeklenir, 1,5–3,5 L'de tutulur,
     * antrenman günlerinde saatlik ~0,5 L haftaya yayılarak eklenir.
     */
    fun recommendedWaterMl(weightKg: Double?, gender: String?, weeklyTrainingMinutes: Int = 0): Int {
        val base = if (gender == "Kadın") 1_600.0 else 2_000.0
        val byWeight = weightKg?.let { it * 30 * 0.8 } ?: base
        val training = weeklyTrainingMinutes / 60.0 * 500.0 / 7.0
        val ml = ((base + byWeight) / 2 + training).coerceIn(1_500.0, 3_500.0)
        return ((ml / 250).roundToInt() * 250)
    }

    /** Protein (g/gün). Morton 2018: kazanım ~1,6 g/kg'da plato; açıkta kası korumak için ~2,0 g/kg. */
    fun recommendedProteinG(weightKg: Double?, losing: Boolean): Int? =
        weightKg?.let { (it * if (losing) 2.0 else 1.6).roundToInt() }
}

data class GoalSource(val claim: String, val claimEn: String, val citation: String, val url: String)

val GOAL_SOURCES = listOf(
    GoalSource("Kilo verme hızı: haftada vücut ağırlığının %0,5–1'i", "Weight loss: 0.5–1% of body weight per week", "Helms et al., JISSN 2014", "https://pubmed.ncbi.nlm.nih.gov/24864135/"),
    GoalSource("Kilo alma hızı: haftada %0,25–0,5", "Weight gain: 0.25–0.5% of body weight per week", "Iraki et al., Sports 2019", "https://pubmed.ncbi.nlm.nih.gov/31247944/"),
    GoalSource("Su: toplam 2,0 L (kadın) / 2,5 L (erkek), yiyecekler dahil", "Water: 2.0 L (women) / 2.5 L (men) total, incl. food", "EFSA, EFSA Journal 2010", "https://efsa.onlinelibrary.wiley.com/doi/10.2903/j.efsa.2010.1459"),
    GoalSource("Protein: kazanım ~1,6 g/kg'da plato (üst güven 2,2 g/kg)", "Protein: gains plateau ~1.6 g/kg (upper CI 2.2 g/kg)", "Morton et al., BJSM 2018", "https://pubmed.ncbi.nlm.nih.gov/28698222/"),
    GoalSource("Adım: fayda 8–10 bin adımda plato yapar", "Steps: benefit plateaus at 8–10k steps", "Paluch et al., Lancet Public Health 2022", "https://www.thelancet.com/journals/lanpub/article/PIIS2468-2667(21)00302-9/fulltext"),
)
