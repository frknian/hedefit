package com.hedefit.app.ui.settings

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Bilimsel dayanaklı hedef hesapları. Kaynaklar [GOAL_SOURCES] içinde; değerler
 * değiştirilirken kaynakla birlikte güncellenmeli.
 */
enum class GoalPace(val lossFraction: Double, val gainFraction: Double) {
    // Kilo verme: haftada vücut ağırlığının %0,5–1'i kas kaybını en aza indirir (Frontiers Endocrinol 2025).
    // Kilo alma: haftada %0,25–0,5 kas kazanımını yağ artışına göre en iyi dengeler (Clin Nutr 2024).
    Slow(0.005, 0.0025),
    Steady(0.0075, 0.00375),
    Fast(0.010, 0.005),
}

object GoalScience {
    const val KCAL_PER_KG = 7_700
    /** Ding 2025 (Lancet Public Health): ~7.000 adım klinik olarak anlamlı fayda sağlar. */
    const val STEP_TARGET = 7_000

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
    GoalSource("Kilo verirken direnç antrenmanı yağ kaybını artırır, kası korur", "Resistance training during weight loss boosts fat loss and preserves muscle", "Frontiers in Endocrinology, 2025", "https://www.frontiersin.org/journals/endocrinology/articles/10.3389/fendo.2025.1725500/full"),
    GoalSource("Yağ kaybı: egzersiz türlerinin karşılaştırması (meta-analiz)", "Fat loss: comparison of exercise modes (meta-analysis)", "JISSN, 2025", "https://doi.org/10.1080/15502783.2025.2507949"),
    GoalSource("Kalori fazlası büyüdükçe protein birikimi artar, yağ da artar", "A larger surplus increases protein accretion, and fat too", "Clinical Nutrition, 2024 (RCT)", "https://www.sciencedirect.com/science/article/pii/S0261561424003467"),
    GoalSource("Kalori açığında protein alımı ve yağsız kütle (doz-yanıt)", "Protein intake and fat-free mass in a deficit (dose-response)", "Strength & Conditioning Journal, 2025", "https://journals.lww.com/nsca-scj/fulltext/9900/effect_of_dietary_protein_on_fat_free_mass_in.179.aspx"),
    GoalSource("Su alımı değişikliklerini test eden klinik çalışmalar (sistematik derleme)", "Clinical trials changing daily water intake (systematic review)", "JAMA Network Open, 2024", "https://pubmed.ncbi.nlm.nih.gov/39585691/"),
    GoalSource("Adım: günde ~7.000 adım anlamlı sağlık faydası sağlar", "Steps: ~7,000 a day gives meaningful health benefits", "Ding et al., Lancet Public Health 2025", "https://www.thelancet.com/journals/lanpub/article/PIIS2468-2667(25)00164-1/fulltext"),
)
