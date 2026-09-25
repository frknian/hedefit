package com.hedefit.app.data.model

import kotlin.math.pow

/** Bir makinede canlı ayarlanabilen değer (hız, eğim, direnç…). */
data class CardioControl(
    val key: String,
    val label: String,
    val unit: String,
    val min: Double,
    val max: Double,
    val step: Double,
    val default: Double,
    /** Sayı yerine gösterilecek adlar (ör. arazi tipi); değer liste indeksidir. */
    val labels: List<String>? = null,
)

data class CardioMachine(
    val key: String,
    val emoji: String,
    val title: String,
    val controls: List<CardioControl>,
    /** Mesafe hesaplanabiliyor mu (eliptik ve merdivende gösterilmez). */
    val tracksDistance: Boolean = true,
)

private val speedControl = CardioControl("speed", "Hız", "km/s", 1.0, 20.0, 0.5, 6.0)
private val inclineControl = CardioControl("incline", "Eğim", "%", 0.0, 15.0, 1.0, 1.0)
private val levelControl = CardioControl("level", "Direnç", "seviye", 1.0, 20.0, 1.0, 6.0)

val cardioMachines = listOf(
    CardioMachine("treadmill", "🏃", "Koşu bandı", listOf(speedControl, inclineControl)),
    CardioMachine("bike", "🚴", "Bisiklet", listOf(levelControl, CardioControl("rpm", "Devir", "rpm", 30.0, 130.0, 5.0, 75.0))),
    CardioMachine("elliptical", "〰️", "Eliptik", listOf(levelControl, CardioControl("spm", "Adım hızı", "adım/dk", 60.0, 200.0, 5.0, 130.0)), tracksDistance = false),
    CardioMachine("rower", "🚣", "Kürek", listOf(CardioControl("spm", "Tempo", "kürek/dk", 14.0, 40.0, 1.0, 24.0), CardioControl("split", "500 m süresi", "sn", 90.0, 240.0, 5.0, 150.0))),
    CardioMachine("stepper", "🪜", "Merdiven", listOf(CardioControl("spm", "Basamak", "basamak/dk", 20.0, 160.0, 5.0, 70.0)), tracksDistance = false),
    // Dışarıda eğim yüzdesi bilinemez: arazi tipi seçilir, hesapta tahmini eğime çevrilir.
    CardioMachine("outdoor", "🌳", "Açık hava", listOf(speedControl.copy(default = 5.5), CardioControl("terrain", "Arazi", "", 0.0, 3.0, 1.0, 0.0, labels = listOf("Düz", "Hafif yokuş", "Dik yokuş", "İnişli çıkışlı")))),
)

/** İlerleme listesi gibi yerlerde kardiyo seanslarını göstermek için (activity:cardio_<key>). */
val cardioActivityTypes: List<ManualActivityType> get() = cardioMachines.map { ManualActivityType("cardio_${it.key}", it.emoji, it.title, cardioEn[it.title] ?: it.title, 6.0) }

fun activityTypeFor(key: String?): ManualActivityType? =
    key?.let { k -> (manualActivityTypes + cardioActivityTypes).firstOrNull { it.key == k } }

/** Bir saniyelik anlık durum için aktif (dinlenme hariç) kalori/dk ve km/s. */
data class CardioRate(val activeKcalPerMinute: Double, val speedKmh: Double)

/**
 * Tahmini yakım. Koşu bandı ve açık havada ACSM metabolik denklemleri, kürekte
 * Concept2 güç formülü, diğerlerinde direnç/tempoya göre MET kullanılır.
 * Makinelerin kendi göstergeleri genelde abartılıdır; bu değer "tahmini"dir.
 */
fun cardioRate(machineKey: String, values: Map<String, Double>, weightKg: Double?): CardioRate {
    val kg = (weightKg ?: 70.0).coerceIn(35.0, 250.0)
    fun v(key: String, fallback: Double) = values[key] ?: fallback
    fun fromMet(met: Double) = (met.coerceIn(1.5, 16.0) - 1.0) * 3.5 * kg / 200.0
    return when (machineKey) {
        "treadmill", "outdoor" -> {
            val kmh = v("speed", 6.0)
            val grade = if (machineKey == "outdoor") outdoorTerrainGrade(v("terrain", 0.0)) else v("incline", 0.0) / 100.0
            val mPerMin = kmh * 1000.0 / 60.0
            // 6.4 km/s altında yürüme, üstünde koşu denklemi.
            val vo2 = if (kmh < 6.4) 0.1 * mPerMin + 1.8 * mPerMin * grade else 0.2 * mPerMin + 0.9 * mPerMin * grade
            CardioRate(vo2 * kg / 1000.0 * 5.0, kmh)
        }
        "bike" -> {
            val level = v("level", 6.0); val rpm = v("rpm", 75.0)
            CardioRate(fromMet(3.5 + level * 0.35 + (rpm - 60.0) * 0.05), (rpm * 0.3).coerceIn(0.0, 45.0))
        }
        "elliptical" -> {
            val level = v("level", 6.0); val spm = v("spm", 130.0)
            CardioRate(fromMet(4.5 + level * 0.3 + (spm - 120.0) * 0.02), 0.0)
        }
        "rower" -> {
            val split = v("split", 150.0).coerceIn(60.0, 300.0)
            val watts = 2.8 / (split / 500.0).pow(3)
            CardioRate(4.0 * watts * 0.8604 / 60.0, 500.0 / split * 3.6)
        }
        "stepper" -> CardioRate(fromMet(4.0 + v("spm", 70.0) * 0.05), 0.0)
        else -> CardioRate(fromMet(6.0), 0.0)
    }
}

/** Arazi tipi → ortalama eğim (Düz %0, Hafif yokuş %3, Dik yokuş %7, İnişli çıkışlı ~%4). */
private fun outdoorTerrainGrade(terrain: Double): Double = when (terrain.toInt()) { 1 -> .03; 2 -> .07; 3 -> .04; else -> 0.0 }

/** Hazır programın bir bölümü: süre ve o bölümde hedeflenen ayarlar. */
data class CardioSegment(val seconds: Int, val label: String, val targets: Map<String, Double>)

data class CardioPreset(
    val key: String,
    val title: String,
    val description: String,
    val machineKey: String,
    val segments: List<CardioSegment>,
) {
    val totalSeconds: Int get() = segments.sumOf { it.seconds }
}

private fun intervals(rounds: Int, fast: CardioSegment, slow: CardioSegment) = List(rounds) { listOf(fast, slow) }.flatten()

val cardioPresets = listOf(
    CardioPreset(
        "12-3-30", "12-3-30 yürüyüşü", "%12 eğim, 3 km/s, 30 dakika. Eklemleri yormadan yüksek yakım.", "treadmill",
        listOf(CardioSegment(30 * 60, "Tempo yürüyüş", mapOf("speed" to 3.0, "incline" to 12.0))),
    ),
    CardioPreset(
        "hiit-treadmill", "Aralıklı koşu (HIIT)", "Isınma, 8 × (1 dk hızlı + 2 dk yavaş), soğuma.", "treadmill",
        listOf(CardioSegment(5 * 60, "Isınma", mapOf("speed" to 5.5, "incline" to 1.0))) +
            intervals(8, CardioSegment(60, "Hızlı", mapOf("speed" to 11.0, "incline" to 1.0)), CardioSegment(120, "Yavaş", mapOf("speed" to 5.5, "incline" to 1.0))) +
            CardioSegment(4 * 60, "Soğuma", mapOf("speed" to 4.5, "incline" to 0.0)),
    ),
    CardioPreset(
        "fat-burn", "Yağ yakım bölgesi", "Konuşabileceğin tempoda 40 dakika sabit yürüyüş.", "treadmill",
        listOf(CardioSegment(5 * 60, "Isınma", mapOf("speed" to 4.5, "incline" to 1.0)), CardioSegment(30 * 60, "Sabit tempo", mapOf("speed" to 5.8, "incline" to 4.0)), CardioSegment(5 * 60, "Soğuma", mapOf("speed" to 4.0, "incline" to 0.0))),
    ),
    CardioPreset(
        "hill-climb", "Tepe tırmanışı", "Eğim her 3 dakikada artar, sonra iner.", "treadmill",
        listOf(0.0, 3.0, 5.0, 7.0, 9.0, 7.0, 5.0, 2.0).mapIndexed { i, grade -> CardioSegment(3 * 60, if (i < 5) "Tırmanış" else "İniş", mapOf("speed" to 5.2, "incline" to grade)) },
    ),
    CardioPreset(
        "hiit-bike", "Bisiklet sprintleri", "Isınma, 10 × (30 sn sprint + 90 sn kolay), soğuma.", "bike",
        listOf(CardioSegment(5 * 60, "Isınma", mapOf("level" to 5.0, "rpm" to 75.0))) +
            intervals(10, CardioSegment(30, "Sprint", mapOf("level" to 12.0, "rpm" to 105.0)), CardioSegment(90, "Kolay", mapOf("level" to 5.0, "rpm" to 70.0))) +
            CardioSegment(5 * 60, "Soğuma", mapOf("level" to 3.0, "rpm" to 65.0)),
    ),
    CardioPreset(
        "rower-pyramid", "Kürek piramidi", "Tempo 20 → 28 → 20, her basamak 3 dakika.", "rower",
        listOf(20.0, 22.0, 24.0, 26.0, 28.0, 24.0, 20.0).map { spm -> CardioSegment(3 * 60, "$spm kürek/dk", mapOf("spm" to spm, "split" to (190.0 - spm * 2.0))) },
    ),
)

/** Oyun modunda sanal rota: Kadıköy sahilinden Bostancı'ya (km). */
val virtualRouteLandmarks = listOf(
    0.0 to "Kadıköy iskelesi", 1.2 to "Moda", 2.5 to "Kalamış", 3.4 to "Fenerbahçe feneri",
    5.5 to "Caddebostan", 7.0 to "Suadiye", 8.3 to "Bostancı", 12.0 to "Maltepe sahili", 21.1 to "Yarı maraton!",
)


/** Kardiyo metinlerinin İngilizce karşılıkları (Türkçe kaynak metin → İngilizce). */
private val cardioEn = mapOf(
    "Koşu bandı" to "Treadmill",
    "Bisiklet" to "Bike",
    "Eliptik" to "Elliptical",
    "Kürek" to "Rower",
    "Merdiven" to "Stair climber",
    "Açık hava" to "Outdoor",
    "Hız" to "Speed",
    "Eğim" to "Incline",
    "Direnç" to "Resistance",
    "Devir" to "Cadence",
    "Adım hızı" to "Stride rate",
    "Tempo" to "Stroke rate",
    "500 m süresi" to "500 m split",
    "Basamak" to "Steps",
    "Arazi" to "Terrain",
    "km/s" to "km/h",
    "seviye" to "level",
    "adım/dk" to "strides/min",
    "kürek/dk" to "strokes/min",
    "sn" to "sec",
    "basamak/dk" to "steps/min",
    "Düz" to "Flat",
    "Hafif yokuş" to "Gentle hill",
    "Dik yokuş" to "Steep hill",
    "İnişli çıkışlı" to "Rolling",
    "12-3-30 yürüyüşü" to "12-3-30 walk",
    "%12 eğim, 3 km/s, 30 dakika. Eklemleri yormadan yüksek yakım." to "12% incline, 3 km/h, 30 minutes. High burn, easy on joints.",
    "Aralıklı koşu (HIIT)" to "Interval run (HIIT)",
    "Isınma, 8 × (1 dk hızlı + 2 dk yavaş), soğuma." to "Warm-up, 8 × (1 min fast + 2 min easy), cool-down.",
    "Yağ yakım bölgesi" to "Fat-burn zone",
    "Konuşabileceğin tempoda 40 dakika sabit yürüyüş." to "40 minutes of steady walking at a conversational pace.",
    "Tepe tırmanışı" to "Hill climb",
    "Eğim her 3 dakikada artar, sonra iner." to "Incline rises every 3 minutes, then comes down.",
    "Bisiklet sprintleri" to "Bike sprints",
    "Isınma, 10 × (30 sn sprint + 90 sn kolay), soğuma." to "Warm-up, 10 × (30 s sprint + 90 s easy), cool-down.",
    "Kürek piramidi" to "Rowing pyramid",
    "Tempo 20 → 28 → 20, her basamak 3 dakika." to "Stroke rate 20 → 28 → 20, 3 minutes per step.",
    "Tempo yürüyüş" to "Brisk walk",
    "Isınma" to "Warm-up",
    "Hızlı" to "Fast",
    "Yavaş" to "Easy",
    "Soğuma" to "Cool-down",
    "Sabit tempo" to "Steady pace",
    "Tırmanış" to "Climb",
    "İniş" to "Descent",
    "Sprint" to "Sprint",
    "Kolay" to "Easy",
    "Kadıköy iskelesi" to "Kadıköy pier",
    "Moda" to "Moda",
    "Kalamış" to "Kalamış",
    "Fenerbahçe feneri" to "Fenerbahçe lighthouse",
    "Caddebostan" to "Caddebostan",
    "Suadiye" to "Suadiye",
    "Bostancı" to "Bostancı",
    "Maltepe sahili" to "Maltepe shore",
    "Yarı maraton!" to "Half marathon!",
    "Geri" to "Back",
    "Kardiyo" to "Cardio",
    "Makineni seç, ayarları canlı değiştir; yaktığın kalori günlük hesabına eklenir." to "Pick a machine and adjust settings live; the calories you burn are added to your daily budget.",
    "Oyun modu" to "Game mode",
    "Sanal rota, rekorunla yarış ve hedef görevleri" to "Virtual route, race your record and target quests",
    "Antrenman sonrası kardiyo öner" to "Suggest cardio after workouts",
    "Kuvvet antrenmanını bitirince kardiyoya geçmeyi hatırlatır" to "Reminds you to do cardio after strength training",
    "Hazır programlar" to "Ready programs",
    "Kalori tahminidir: kilo, hız, eğim ve dirence göre hesaplanır. Makinelerin gösterdiği değer genelde daha yüksektir." to "Calories are estimated from your weight, speed, incline and resistance. Machine displays usually read higher.",
    "Yarıya geldin, harika gidiyorsun." to "Halfway there, great job.",
    "Görev tamam! On puan." to "Quest complete! Ten points.",
    "Program tamamlandı. Tebrikler!" to "Program complete. Well done!",
    "Kilitle" to "Lock",
    "Duraklat" to "Pause",
    "Devam" to "Resume",
    "Bitir" to "Finish",
    "🔒 Açmak için uzun bas" to "🔒 Long-press to unlock",
    "Seans bitsin mi?" to "End session?",
    "Bitirirsen özet ekranına geçersin. Vazgeçersen kayıt yapılmaz." to "Finishing opens the summary. Leaving discards the session.",
    "Bitir ve özetle" to "Finish & summarise",
    "Kaydetmeden çık" to "Leave without saving",
    "Başlangıç" to "Start",
    "👻 Bu ilk seansın; bitirince rekorun olacak." to "👻 First session; finishing sets your record.",
    "İlk rekorun kaydedildi! 🎉" to "First record saved! 🎉",
    "Yeni rekor! 🏆" to "New record! 🏆",
    "Kardiyo tamam!" to "Cardio done!",
    "Günlük kalori hedefine eklenecek (tahmini)" to "Will be added to your daily calorie target (estimated)",
    "Yoğunluk (kcal/dk)" to "Intensity (kcal/min)",
    "1 dakikadan kısa seanslar kaydedilmez." to "Sessions shorter than 1 minute are not saved.",
    "Kaydediliyor…" to "Saving…",
    "Kaydet ve kaloriye ekle" to "Save & add calories",
    "Kaydetmeden çıkılsın mı?" to "Leave without saving?",
    "Bu seansın kalorisi günlük hesabına eklenmez." to "This session's calories won't be added to your day.",
    "Çık" to "Leave",
    "Vazgeç" to "Cancel",
    "kcal (tahmini)" to "kcal (est.)",
    "km" to "km",
    "kcal/dk" to "kcal/min",
    "süre" to "time",
    "oyun puanı" to "game points",
)

/** Kardiyo metnini uygulama diline çevirir; sözlükte yoksa olduğu gibi döner. */
fun ct(text: String): String = com.hedefit.app.ui.i18n.tr(text, cardioEn[text] ?: text)
