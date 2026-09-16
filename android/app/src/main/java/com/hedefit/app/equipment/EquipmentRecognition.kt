package com.hedefit.app.equipment

import com.hedefit.app.data.model.ExerciseCatalogData

data class EquipmentInfo(
    val id: String,
    val name: String,
    val category: String,
    val description: String,
    val primaryMuscles: List<String>,
    val secondaryMuscles: List<String>,
    val instructions: List<String>,
    val commonMistakes: List<String>,
    val safetyNotes: List<String>,
    val aliases: List<String> = emptyList(),
    val exerciseTerms: List<String> = emptyList(),
)

data class RecognitionAlternative(val equipment: EquipmentInfo, val confidence: Float)

sealed interface RecognitionResult {
    data class Recognized(
        val equipment: EquipmentInfo,
        val confidence: Float,
        val alternatives: List<RecognitionAlternative> = emptyList(),
        val visibleFeatures: List<String> = emptyList(),
    ) : RecognitionResult
    data class Unknown(val confidence: Float = 0f, val visibleFeatures: List<String> = emptyList()) : RecognitionResult
}

fun interface EquipmentRecognitionService { suspend fun recognize(frame: ByteArray): RecognitionResult }

enum class EquipmentRecognitionError { OFFLINE, TIMEOUT, RATE_LIMIT, CONFIGURATION, SERVICE_UNAVAILABLE, INVALID_RESPONSE, INVALID_IMAGE, UNKNOWN }
class EquipmentRecognitionException(val kind: EquipmentRecognitionError, message: String) : Exception(message)

object EquipmentCatalog {
    private val setup = listOf("Oturağı, pedleri ve başlangıç konumunu vücut ölçülerine göre ayarla.", "Hafif bir deneme ağırlığıyla hareket yolunu kontrol et.", "Gövdeni sabit tutup tekrarı kontrollü tamamla.")
    private val mistakes = listOf("Ağırlığı savurmak", "Eklemleri kilitlemek veya hareket açıklığını kontrolsüz kısaltmak")
    private val safety = listOf("Emniyet pimini ve kilitleri başlamadan kontrol et.", "Keskin ağrıda hareketi hemen bırak.")

    val items = listOf(
        item("bench_press", "Bench Press Sehpası", "Serbest ağırlık", "Barbell bench press için sehpa ve bar askıları.", listOf("chest"), listOf("triceps", "front delts"), listOf("bench press", "chest press")),
        item("squat_rack", "Squat Rack", "Serbest ağırlık", "Squat ve barbell hareketleri için güvenlik kolları bulunan rack.", listOf("quadriceps", "glutes"), listOf("hamstrings", "lower back"), listOf("squat", "rack")),
        item("smith_machine", "Smith Machine", "Makine", "Ray üzerinde yönlendirilmiş bar sistemi.", listOf("full body"), emptyList(), listOf("smith machine"), listOf("smith rack")),
        item("cable_machine", "Cable Machine", "Kablo", "Ayarlanabilir makara ve kablo istasyonu.", listOf("full body"), emptyList(), listOf("cable"), listOf("functional trainer", "crossover")),
        item("lat_pulldown", "Lat Pulldown", "Makine", "Üstten çekiş için diz pedli kablo makinesi.", listOf("lats"), listOf("biceps", "rear delts"), listOf("lat pulldown", "pulldown")),
        item("seated_row", "Seated Row", "Makine", "Oturarak yatay çekiş makinesi.", listOf("middle back", "lats"), listOf("biceps", "rear delts"), listOf("seated row", "cable row")),
        item("chest_press", "Chest Press", "Makine", "Kontrollü yatay itiş makinesi.", listOf("chest"), listOf("triceps", "front delts"), listOf("chest press")),
        item("shoulder_press", "Shoulder Press", "Makine", "Baş üstü itiş makinesi.", listOf("shoulders"), listOf("triceps"), listOf("shoulder press")),
        item("leg_press", "Leg Press", "Makine", "Platforma karşı alt vücut itiş makinesi.", listOf("quadriceps", "glutes"), listOf("hamstrings"), listOf("leg press")),
        item("leg_extension", "Leg Extension", "Makine", "Diz ekstansiyonu makinesi.", listOf("quadriceps"), emptyList(), listOf("leg extension")),
        item("leg_curl", "Leg Curl", "Makine", "Diz fleksiyonu makinesi.", listOf("hamstrings"), listOf("calves"), listOf("leg curl")),
        item("calf_raise", "Calf Raise", "Makine", "Baldır yükseltme makinesi.", listOf("calves"), emptyList(), listOf("calf raise")),
        item("hip_abductor", "Hip Abductor", "Makine", "Kalçayı dışa açma makinesi.", listOf("abductors", "glutes"), emptyList(), listOf("hip abduction", "abductor")),
        item("hip_adductor", "Hip Adductor", "Makine", "Bacakları içe kapatma makinesi.", listOf("adductors"), emptyList(), listOf("hip adduction", "adductor")),
        item("pec_deck", "Pec Deck", "Makine", "Göğüs sıkıştırma ve ters fly makinesi.", listOf("chest"), listOf("front delts"), listOf("pec deck", "machine fly")),
        item("assisted_pullup_dip", "Assisted Pull-up / Dip", "Makine", "Diz platformuyla destekli çekiş ve dip istasyonu.", listOf("lats", "triceps"), listOf("biceps", "chest"), listOf("assisted pull", "assisted dip")),
        item("treadmill", "Koşu Bandı", "Kardiyo", "Motorlu yürüyüş ve koşu platformu.", listOf("quadriceps", "hamstrings", "calves"), listOf("glutes"), listOf("treadmill", "running"), listOf("koşu bandı")),
        item("elliptical", "Eliptik Bisiklet", "Kardiyo", "Düşük darbeli el ve ayak pedallı kardiyo cihazı.", listOf("quadriceps", "glutes"), listOf("hamstrings", "shoulders"), listOf("elliptical")),
        item("stationary_bike", "Sabit Bisiklet", "Kardiyo", "Salon tipi sabit bisiklet.", listOf("quadriceps"), listOf("hamstrings", "calves"), listOf("cycling", "bike"), listOf("exercise bike", "spin bike")),
        item("rowing_machine", "Kürek Ergometresi", "Kardiyo", "Raylı oturak ve zincir/kablo tutamaklı kürek cihazı.", listOf("middle back", "quadriceps"), listOf("lats", "biceps", "hamstrings"), listOf("rowing", "rower"), listOf("rowing ergometer")),
        item("stair_climber", "Merdiven Tırmanma", "Kardiyo", "Dönen basamaklı tırmanma cihazı.", listOf("quadriceps", "glutes"), listOf("hamstrings", "calves"), listOf("stair", "step"), listOf("stairmaster")),
        item("dumbbell_rack", "Dumbbell Rafı", "Serbest ağırlık", "Farklı ağırlıklarda dambılların bulunduğu raf.", listOf("full body"), emptyList(), listOf("dumbbell"), listOf("dumbbells", "dumbbell rack")),
        item("barbell", "Barbell", "Serbest ağırlık", "Plaka takılabilen düz uzun bar.", listOf("full body"), emptyList(), listOf("barbell"), listOf("olympic bar")),
        item("kettlebell", "Kettlebell", "Serbest ağırlık", "Üstten saplı döküm ağırlık.", listOf("full body"), emptyList(), listOf("kettlebell")),
        item("ez_bar", "EZ Bar", "Serbest ağırlık", "Açılı kavrama bölgeleri olan kısa bar.", listOf("biceps"), listOf("forearms", "triceps"), listOf("ez bar", "e-z"), listOf("curl bar")),
        item("resistance_band", "Direnç Bandı", "Aksesuar", "Elastik egzersiz bandı.", listOf("full body"), emptyList(), listOf("band"), listOf("resistance band")),
        item("pullup_bar", "Pull-up Barı", "Vücut ağırlığı", "Asılarak çekiş için sabit bar.", listOf("lats"), listOf("biceps", "forearms"), listOf("pull-up", "pullup"), listOf("chin-up bar")),
        item("dip_station", "Dip İstasyonu", "Vücut ağırlığı", "Paralel tutamaklı dip istasyonu.", listOf("triceps", "chest"), listOf("front delts"), listOf("dip"), listOf("parallel bars")),
    )

    fun findByLabel(label: String): EquipmentInfo? {
        val normalized = normalize(label)
        return items.firstOrNull { equipment -> (listOf(equipment.id, equipment.name) + equipment.aliases).any { normalize(it) == normalized } }
    }

    fun matchingExercises(equipment: EquipmentInfo, catalog: List<ExerciseCatalogData>): List<ExerciseCatalogData> {
        val terms = (equipment.exerciseTerms + equipment.aliases + equipment.name).map(::normalize).filter(String::isNotBlank)
        return catalog.filter { exercise ->
            val haystack = normalize("${exercise.equipment} ${exercise.name}")
            terms.any { term -> haystack.contains(term) || term.contains(haystack) }
        }.distinctBy(ExerciseCatalogData::id).sortedBy(ExerciseCatalogData::name)
    }

    private fun normalize(value: String) = value.lowercase()
        .replace('ı', 'i').replace('ş', 's').replace('ğ', 'g').replace('ü', 'u').replace('ö', 'o').replace('ç', 'c')
        .filter(Char::isLetterOrDigit)

    private fun item(
        id: String, name: String, category: String, description: String, primary: List<String>, secondary: List<String>,
        exerciseTerms: List<String>, aliases: List<String> = emptyList(),
    ) = EquipmentInfo(id, name, category, description, primary, secondary, setup, mistakes, safety, aliases, exerciseTerms)
}
