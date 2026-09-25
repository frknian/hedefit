package com.hedefit.app.ui.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Uygulama dili (Ayarlar → Dil). MainActivity tercihten günceller; değiştiğinde
 * Compose ekranları yeniden çizilir. Yeni metinlerde tr("…", "…") kullanılır.
 */
object AppLang {
    var en by mutableStateOf(false)
}

/** Türkçe / İngilizce metin seçer. */
fun tr(tr: String, en: String): String = if (AppLang.en) en else tr

/** Varsayılan misafir adı ("Sporcu") dile göre gösterilir; kullanıcının kendi adı olduğu gibi kalır. */
fun localizedDisplayName(name: String?): String =
    if (name.isNullOrBlank() || name == "Sporcu" || name == "Athlete") tr("Sporcu", "Athlete") else name

/**
 * Uygulamanın kendi ürettiği program adları veritabanına Türkçe yazılır; ekranda dile göre
 * gösterilir. Kullanıcının verdiği adlar olduğu gibi kalır. Değerlendirme programı her yerde aynı adla görünür.
 */
fun localizedProgramName(name: String?, source: String? = null): String = when {
    source == "assessment" || name == "Kişisel Atlas Programım" || name == "Personal Atlas Program" -> tr("Kişisel Atlas Programım", "Personal Atlas Program")
    name == "Kendi Programım" || name == "My Program" -> tr("Kendi Programım", "My Program")
    name == "Fit Koç Programı" || name == "Fit Coach Program" -> tr("Fit Koç Programı", "Fit Coach Program")
    name == "Antrenman" || name == "Workout" -> tr("Antrenman", "Workout")
    name.isNullOrBlank() -> tr("Antrenman", "Workout")
    else -> name
}

private val areaPairs = listOf(
    "Göğüs" to "Chest", "Sırt" to "Back", "Kanat" to "Lats", "Bacak" to "Legs", "Kalça" to "Glutes", "Omuz" to "Shoulders",
    "Kol" to "Arms", "Ön kol" to "Biceps", "Arka kol" to "Triceps", "Karın" to "Core", "Tüm Vücut" to "Full body", "Kardiyo" to "Cardio",
    "Ön bacak" to "Quads", "Arka bacak" to "Hamstrings", "Baldır" to "Calves", "Trapez" to "Traps", "Bel" to "Lower back",
)

/** Program/hareket bölge etiketlerini dile göre gösterir (Türkçe ya da İngilizce kaynak). */
fun localizedArea(area: String): String {
    val pair = areaPairs.firstOrNull { it.first.equals(area, true) || it.second.equals(area, true) }
        ?: when (area.lowercase()) {
            "quadriceps" -> "Ön bacak" to "Quads"; "biceps" -> "Ön kol" to "Biceps"; "triceps" -> "Arka kol" to "Triceps"
            "abdominals", "abs" -> "Karın" to "Core"; "lower back" -> "Bel" to "Lower back"; "middle back" -> "Sırt" to "Back"
            else -> null
        }
    return pair?.let { tr(it.first, it.second) } ?: area.replaceFirstChar(Char::uppercase)
}
