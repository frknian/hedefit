package com.hedefit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.selectable
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.hedefit.app.ui.components.CountUpText
import com.hedefit.app.ui.components.PlanBuildingScene
import com.hedefit.app.ui.components.rememberReducedMotion
import com.hedefit.app.ui.components.selectionBounce
import com.hedefit.app.ui.components.staggeredEntrance
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.hedefit.app.data.model.ProfileData
import com.hedefit.app.data.model.ProfileUpdateData
import com.hedefit.app.ui.settings.estimatedGoalWeeks
import com.hedefit.app.ui.theme.HedefitColors

private data class ProfileQuestion(
    val title: String,
    val subtitle: String,
    val choices: List<String> = emptyList(),
    val freeText: Boolean = false,
    val multiSelect: Boolean = false,
    val exclusiveChoice: String? = null,
    val kind: QuestionKind = QuestionKind.Choices,
)

private enum class QuestionKind { Choices, FocusMap, Slider, Performance, CycleOptIn }

private val profileQuestions = listOf(
    ProfileQuestion("Ana hedefin ne?", "Programın ve tahmini süren bu hedefe göre hazırlanır.", listOf("Kilo verme", "Kilo alma", "Kas kazanma", "Formu koruma")),
    ProfileQuestion("Bunu neden istiyorsun?", "Seni harekete geçiren kişisel nedenini yaz.", freeText = true),
    ProfileQuestion("Seni en çok ne durdurdu?", "Sürdürülebilir bir plan için gerçek engeli seç.", listOf("Zaman", "Motivasyon", "Sakatlık", "Program eksikliği", "Beslenme")),
    ProfileQuestion("Daha önce düzenli spor yaptın mı?", "Geçmiş deneyimin", listOf("Hayır", "Kısa süre", "6–12 ay", "1 yıldan fazla")),
    ProfileQuestion("Kendini hangi seviyede görüyorsun?", "Yoğunluğu buna göre ayarlarız.", listOf("Başlangıç", "Orta", "İleri")),
    ProfileQuestion("Son 3 ayda haftada kaç gün spor yaptın?", "Mevcut alışkanlığın", listOf("0 gün", "1–2 gün", "3–4 gün", "5+ gün")),
    ProfileQuestion("Haftada kaç gün ayırabilirsin?", "Gerçekçi bir tempo seç.", listOf("2 gün", "3 gün", "4 gün", "5 gün", "6 gün", "7 gün")),
    ProfileQuestion("Bir antrenman için ne kadar süren var?", "Isınma ve soğuma dahil ayırabileceğin toplam süre.", listOf("15 dk", "30 dk", "45 dk", "60 dk", "75+ dk")),
    ProfileQuestion("Hangi antrenmanları seversin?", "Birden fazla seçebilirsin.", listOf("Ağırlık", "HIIT", "Koşu", "Bisiklet", "Vücut ağırlığı", "Pilates", "Mobilite", "Barre", "Düşük etkili", "Toparlanma"), multiSelect = true),
    ProfileQuestion("Nerede çalışacaksın?", "Egzersizler ortama göre seçilir.", listOf("Evde", "Spor salonunda", "Açık havada", "Karışık")),
    ProfileQuestion("Hangi ekipmanların var?", "Birden fazla seçebilirsin. “Ekipman yok” tek başına seçilir.", listOf("Ekipman yok", "Dambıl", "Kettlebell", "Direnç bandı", "Barfiks barı", "Sehpa", "Halter", "TRX / halka", "Pilates topu", "Atlama ipi", "Tam salon", "Kardiyo aleti"), multiSelect = true, exclusiveChoice = "Ekipman yok"),
    ProfileQuestion("Ağrı veya sakatlık var mı?", "Birden fazla bölge seçebilirsin. “Yok” tek başına seçilir.", listOf("Yok", "Bel", "Diz", "Omuz", "Boyun", "Diğer"), multiSelect = true, exclusiveChoice = "Yok"),
    ProfileQuestion("Gün içinde ne kadar hareketlisin?", "Günlük enerji hesabı", listOf("Çoğunlukla oturuyorum", "Ara sıra hareket", "Aktif", "Çok aktif")),
    ProfileQuestion("Uyku düzenin nasıl?", "Toparlanma kapasiten", listOf("5 saatten az", "5–6 saat", "7–8 saat", "9+ saat")),
    ProfileQuestion("Koçunun bilmesi gereken başka bir şey?", "Tercih, kısıt veya not ekleyebilirsin.", freeText = true),
    ProfileQuestion("Hangi bölgelere odaklanmak istersin?", "Birden fazla seçebilirsin. Haritadan da dokunabilirsin.", listOf("Karın", "Göğüs", "Omuz", "Sırt", "Kol", "Kalça", "Bacak", "Genel Gelişim"), multiSelect = true, kind = QuestionKind.FocusMap),
    ProfileQuestion("Sağlık durumun hakkında bilgi ver.", "Birden fazla seçebilirsin. “Yok” tek başına seçilir.", listOf("Yok", "Diyabet", "Kalp Hastalığı", "İnsülin Direnci", "Hipotiroidi", "Hipertansiyon", "Yüksek Kolesterol", "Diğer"), multiSelect = true, exclusiveChoice = "Yok"),
    ProfileQuestion("Besin alerjin var mı?", "Birden fazla seçebilirsin. “Yok” tek başına seçilir.", listOf("Yok", "Gluten", "Süt ürünleri", "Yumurta", "Kuruyemiş", "Deniz ürünleri", "Soya", "Diğer"), multiSelect = true, exclusiveChoice = "Yok"),
    ProfileQuestion("Beslenme tercihin nedir?", "Beslenme önerilerinde bunu dikkate alırız.", listOf("Standart", "Vejetaryen", "Vegan", "Pesketaryen")),
    ProfileQuestion("Bu alışkanlıklardan hangisine sahipsin?", "Birden fazla seçebilirsin. “Yok” seçeneği diğerlerini temizler.", listOf("Yok", "Sigara", "Alkol", "Gece geç yatıyorum", "Fazla şeker tüketiyorum", "Çok oturuyorum", "Egzersizi erteliyorum", "Düzenli beslenemiyorum"), multiSelect = true, exclusiveChoice = "Yok"),
    ProfileQuestion("Günlük stres seviyeni nasıl tanımlarsın?", "Toparlanma ihtiyacını planlarken bu bilgiyi de dikkate alırız.", listOf("Nadiren stres yaşıyorum", "Bazen stresli oluyorum", "Sık sık stres yaşıyorum", "Neredeyse her gün stres altındayım")),
    ProfileQuestion("Mevcut yaşam tarzından ne kadar memnunsun?", "", kind = QuestionKind.Slider),
    ProfileQuestion("Fiziksel performansın nasıl?", "Bilmiyorsan boş bırakabilirsin.", kind = QuestionKind.Performance),
)

/** İlk 15 soru eski şemadır; bunlar sonradan eklenen sağlık/yaşam tarzı soruları. */
private const val QUESTION_COUNT = 23
private val EXTRA_QUESTION_INDICES = (15 until QUESTION_COUNT).toList()

/**
 * Tam akış sırası: hedef, seviye, aktivite düzeyi, gün, süre, tercih edilen antrenman türleri, ortam, ekipman,
 * sakatlık; ardından sağlık/yaşam tarzı soruları. `CORE_QUESTION_INDICES` (tamamlanma ölçütü) bilerek DEĞİŞMEZ:
 * mevcut kullanıcılar testi yeniden doldurmaya zorlanmaz; yeni sorular yalnız akışa eklenir.
 */
private val FULL_FLOW_ORDER = listOf(0, 4, 12, 6, 7, 8, 9, 10, 11) + EXTRA_QUESTION_INDICES

/** Döngü adımı bir `history` slotu değildir; yalnızca kadın olarak belirten kullanıcıya, isteğe bağlı gösterilir. */
private const val CYCLE_STEP = 100
private val CYCLE_QUESTION = ProfileQuestion(
    "Hedefit'in antrenmanlarını adet döngüsü bilgilerine göre uyarlamasını ister misin?",
    "İsteğe bağlı. Tek başına antrenman kararı vermez; günlük check-in cevapların her zaman önceliklidir. İstediğin zaman Ayarlar'dan kapatabilir ve verini silebilirsin.",
    kind = QuestionKind.CycleOptIn,
)

private fun questionAt(index: Int): ProfileQuestion = if (index == CYCLE_STEP) CYCLE_QUESTION else profileQuestions[index]

/** Döngü adımının taslağı: yalnızca kullanıcı "Aktifleştir" derse sunucuya yazılır. */
internal class CycleDraft {
    var enabled by mutableStateOf<Boolean?>(null)
    var lastPeriod by mutableStateOf<java.time.LocalDate?>(null)
    var cycleLength by mutableIntStateOf(28)
    var periodLength by mutableIntStateOf(5)
    var regularity by mutableStateOf("unknown")
}

/** Harita bölgesi → odak alanı. Cevaplar odak alanı adıyla (Türkçe) saklanır. */
private val focusAreaByMuscle = mapOf(
    "abdominals" to "Karın", "chest" to "Göğüs", "shoulders" to "Omuz",
    "lats" to "Sırt", "traps" to "Sırt", "middle back" to "Sırt", "lower back" to "Sırt",
    "biceps" to "Kol", "triceps" to "Kol", "forearms" to "Kol", "glutes" to "Kalça",
    "quadriceps" to "Bacak", "hamstrings" to "Bacak", "calves" to "Bacak",
)


/** Sorular ve cevaplar sunucuya Türkçe kaydedilir; yalnız ekranda çevrilir. */
private val questionEn = mapOf(
    "Ana hedefin ne?" to "What's your main goal?",
    "Programın ve tahmini süren bu hedefe göre hazırlanır." to "Your program and estimated timeline are built around it.",
    "Kilo verme" to "Lose weight",
    "Kilo alma" to "Gain weight",
    "Kas kazanma" to "Build muscle",
    "Formu koruma" to "Stay in shape",
    "Bunu neden istiyorsun?" to "Why do you want this?",
    "Seni harekete geçiren kişisel nedenini yaz." to "Write the personal reason that drives you.",
    "Seni en çok ne durdurdu?" to "What stopped you most?",
    "Sürdürülebilir bir plan için gerçek engeli seç." to "Pick the real obstacle so the plan is sustainable.",
    "Zaman" to "Time",
    "Motivasyon" to "Motivation",
    "Sakatlık" to "Injury",
    "Program eksikliği" to "No program",
    "Beslenme" to "Nutrition",
    "Daha önce düzenli spor yaptın mı?" to "Have you trained regularly before?",
    "Geçmiş deneyimin" to "Your past experience",
    "Hayır" to "No",
    "Kısa süre" to "Briefly",
    "6–12 ay" to "6–12 months",
    "1 yıldan fazla" to "Over 1 year",
    "Kendini hangi seviyede görüyorsun?" to "What level are you?",
    "Yoğunluğu buna göre ayarlarız." to "We set the intensity to match.",
    "Başlangıç" to "Beginner",
    "Orta" to "Intermediate",
    "İleri" to "Advanced",
    "Son 3 ayda haftada kaç gün spor yaptın?" to "Days per week you trained in the last 3 months?",
    "Mevcut alışkanlığın" to "Your current habit",
    "0 gün" to "0 days",
    "1–2 gün" to "1–2 days",
    "3–4 gün" to "3–4 days",
    "5+ gün" to "5+ days",
    "Haftada kaç gün ayırabilirsin?" to "How many days a week can you train?",
    "Gerçekçi bir tempo seç." to "Pick a realistic pace.",
    "2 gün" to "2 days",
    "3 gün" to "3 days",
    "4 gün" to "4 days",
    "5 gün" to "5 days",
    "6 gün" to "6 days",
    "7 gün" to "7 days",
    "Bir antrenman için ne kadar süren var?" to "How long do you have per workout?",
    "Isınma ve soğuma dahil ayırabileceğin toplam süre." to "Total time including warm-up and cool-down.",
    "15 dk" to "15 min",
    "30 dk" to "30 min",
    "45 dk" to "45 min",
    "60 dk" to "60 min",
    "75+ dk" to "75+ min",
    "Hangi antrenmanları seversin?" to "Which workouts do you enjoy?",
    "Birden fazla seçebilirsin." to "You can pick more than one.",
    "Ağırlık" to "Weights",
    "Koşu" to "Running",
    "Bisiklet" to "Cycling",
    "Vücut ağırlığı" to "Bodyweight",
    "Pilates" to "Pilates", "Mobilite" to "Mobility", "Barre" to "Barre", "Düşük etkili" to "Low impact", "Toparlanma" to "Recovery",
    "Nerede çalışacaksın?" to "Where will you train?",
    "Egzersizler ortama göre seçilir." to "Exercises are chosen for your setting.",
    "Evde" to "At home",
    "Spor salonunda" to "At the gym",
    "Açık havada" to "Outdoors",
    "Karışık" to "Mixed",
    "Hangi ekipmanların var?" to "What equipment do you have?",
    "Birden fazla seçebilirsin. “Ekipman yok” tek başına seçilir." to "Pick any that apply. “No equipment” is chosen on its own.",
    "Ekipman yok" to "No equipment",
    "Dambıl" to "Dumbbells",
    "Direnç bandı" to "Resistance band",
    "Barfiks barı" to "Pull-up bar",
    "Sehpa" to "Bench",
    "Halter" to "Barbell",
    "TRX / halka" to "TRX / rings",
    "Pilates topu" to "Pilates ball",
    "Atlama ipi" to "Jump rope",
    "Tam salon" to "Full gym",
    "Kardiyo aleti" to "Cardio machine",
    "Ağrı veya sakatlık var mı?" to "Any pain or injury?",
    "Birden fazla bölge seçebilirsin. “Yok” tek başına seçilir." to "Pick any areas. “None” is chosen on its own.",
    "Yok" to "None",
    "Bel" to "Lower back",
    "Diz" to "Knee",
    "Omuz" to "Shoulder",
    "Boyun" to "Neck",
    "Diğer" to "Other",
    "Gün içinde ne kadar hareketlisin?" to "How active are you during the day?",
    "Günlük enerji hesabı" to "For your daily energy estimate",
    "Çoğunlukla oturuyorum" to "Mostly sitting",
    "Ara sıra hareket" to "Occasionally active",
    "Aktif" to "Active",
    "Çok aktif" to "Very active",
    "Uyku düzenin nasıl?" to "How do you sleep?",
    "Toparlanma kapasiten" to "Your recovery capacity",
    "5 saatten az" to "Under 5 hours",
    "5–6 saat" to "5–6 hours",
    "7–8 saat" to "7–8 hours",
    "9+ saat" to "9+ hours",
    "Koçunun bilmesi gereken başka bir şey?" to "Anything else your coach should know?",
    "Tercih, kısıt veya not ekleyebilirsin." to "Add preferences, limits or notes.",
    "Hedefit'in antrenmanlarını adet döngüsü bilgilerine göre uyarlamasını ister misin?" to "Would you like Hedefit to adapt your workouts using menstrual cycle information?",
    "İsteğe bağlı. Tek başına antrenman kararı vermez; günlük check-in cevapların her zaman önceliklidir. İstediğin zaman Ayarlar'dan kapatabilir ve verini silebilirsin." to "Optional. It never decides your workout on its own — your daily check-in always comes first. You can turn it off and delete your data in Settings at any time.",
    "Hangi bölgelere odaklanmak istersin?" to "Which areas do you want to focus on?",
    "Birden fazla seçebilirsin. Haritadan da dokunabilirsin." to "Pick any that apply. You can also tap the map.",
    "Karın" to "Abs", "Göğüs" to "Chest", "Omuz" to "Shoulders", "Sırt" to "Back", "Kol" to "Arms", "Kalça" to "Hips", "Bacak" to "Legs", "Genel Gelişim" to "Overall",
    "Sağlık durumun hakkında bilgi ver." to "Tell us about your health.",
    "Birden fazla seçebilirsin. “Yok” tek başına seçilir." to "Pick any that apply. “None” is chosen on its own.",
    "Yok" to "None", "Diyabet" to "Diabetes", "Kalp Hastalığı" to "Heart disease", "İnsülin Direnci" to "Insulin resistance", "Hipotiroidi" to "Hypothyroidism", "Hipertansiyon" to "Hypertension", "Yüksek Kolesterol" to "High cholesterol", "Diğer" to "Other",
    "Besin alerjin var mı?" to "Do you have food allergies?",
    "Gluten" to "Gluten", "Süt ürünleri" to "Dairy", "Yumurta" to "Eggs", "Kuruyemiş" to "Nuts", "Deniz ürünleri" to "Seafood", "Soya" to "Soy",
    "Beslenme tercihin nedir?" to "What is your diet preference?",
    "Beslenme önerilerinde bunu dikkate alırız." to "We take this into account in nutrition suggestions.",
    "Standart" to "Standard", "Vejetaryen" to "Vegetarian", "Vegan" to "Vegan", "Pesketaryen" to "Pescatarian",
    "Bu alışkanlıklardan hangisine sahipsin?" to "Which of these habits apply to you?",
    "Birden fazla seçebilirsin. “Yok” seçeneği diğerlerini temizler." to "Pick any that apply. “None” clears the others.",
    "Sigara" to "Smoking", "Alkol" to "Alcohol", "Gece geç yatıyorum" to "I go to bed late", "Fazla şeker tüketiyorum" to "I eat a lot of sugar", "Çok oturuyorum" to "I sit a lot", "Egzersizi erteliyorum" to "I put off exercise", "Düzenli beslenemiyorum" to "I can't eat regularly",
    "Günlük stres seviyeni nasıl tanımlarsın?" to "How would you describe your daily stress?",
    "Toparlanma ihtiyacını planlarken bu bilgiyi de dikkate alırız." to "We take this into account when planning your recovery.",
    "Nadiren stres yaşıyorum" to "I rarely feel stressed", "Bazen stresli oluyorum" to "I'm sometimes stressed", "Sık sık stres yaşıyorum" to "I'm often stressed", "Neredeyse her gün stres altındayım" to "I'm stressed almost every day",
    "Mevcut yaşam tarzından ne kadar memnunsun?" to "How satisfied are you with your current lifestyle?",
    "Fiziksel performansın nasıl?" to "How is your physical performance?",
    "Bilmiyorsan boş bırakabilirsin." to "Leave it blank if you don't know.",
)

private fun qt(text: String): String = com.hedefit.app.ui.i18n.tr(text, questionEn[text] ?: text)

private val quickStartQuestionOrder = listOf(0, 4, 6, 7, 11)

/**
 * Sorulan belirleyici sorular: hedef, seviye, gün, süre, ortam, ekipman, sakatlık.
 * Cevaplar yine 15'lik sabit sırada saklanır (bkz. lib/onboarding-questions.ts);
 * sorulmayanlar boş gider, serbest metinler "Yok" olarak kaydedilir.
 */
val CORE_QUESTION_INDICES = listOf(0, 4, 6, 7, 9, 10, 11)

@Composable
fun ProfileQuestionnaireScreen(profile: ProfileData, saving: Boolean, quickStart: Boolean, onClose: () -> Unit, onSave: (ProfileUpdateData) -> Unit, onSaveCycle: (com.hedefit.app.data.model.CycleProfileData) -> Unit = {}) {
    val answers = remember(profile) { mutableStateListOf<String>().apply { addAll((profile.historyAnswers + List(QUESTION_COUNT) { "" }).take(QUESTION_COUNT).map { if (it == "Kas alma") "Kas kazanma" else it }) } }
    val cycleStep = !quickStart && com.hedefit.app.data.model.cycleOptInInOnboarding(profile.gender)
    val questionOrder = remember(quickStart, cycleStep) { if (quickStart) quickStartQuestionOrder else FULL_FLOW_ORDER + (if (cycleStep) listOf(CYCLE_STEP) else emptyList()) }
    val cycleDraft = remember { CycleDraft() }
    var step by rememberSaveable { mutableIntStateOf(0) }
    var forward by remember { mutableStateOf(true) }
    val index = questionOrder[step]
    var targetText by remember(profile) { mutableStateOf(profile.targetWeightKg?.toString() ?: suggestedTarget(profile).toString()) }
    val question = questionAt(index)
    val target = targetText.toDoubleOrNull()
    val weeks = estimatedGoalWeeks(profile.weightKg, target)
    val haptic = LocalHapticFeedback.current
    // Belirleyici sorular hiç cevaplanmamışsa bu ilk program oluşturmadır.
    val firstRun = remember(profile.id) { CORE_QUESTION_INDICES.any { profile.historyAnswers.getOrNull(it).isNullOrBlank() } }
    val reduced = rememberReducedMotion()
    val scope = rememberCoroutineScope()
    val progress by animateFloatAsState((step + 1).toFloat() / questionOrder.size, tween(if (reduced) 0 else 450), label = "questionProgress")

    fun goBack() { if (step > 0) { forward = false; step-- } else onClose() }
    // Yalnızca açıkça "Aktifleştir" denirse; "Şimdi değil" hiçbir şey yazmaz.
    val saveCycleIfEnabled = {
        if (cycleStep && cycleDraft.enabled == true) onSaveCycle(com.hedefit.app.data.model.CycleProfileData(true, cycleDraft.lastPeriod?.toString(), cycleDraft.cycleLength, cycleDraft.periodLength, cycleDraft.regularity))
    }
    fun submit() { saveCycleIfEnabled(); onSave(ProfileUpdateData(
        profile.displayName, profile.age, profile.gender, profile.heightCm, profile.weightKg,
        answers[0].ifBlank { profile.goal }, target, weeks, answers[9].ifBlank { profile.environment }, answers[10].ifBlank { profile.equipment },
        answers.mapIndexed { answerIndex, answer -> if (answer.isBlank() && profileQuestions[answerIndex].freeText) "Yok" else answer },
    )) }
    fun next() { if (step < questionOrder.lastIndex) { forward = true; step++ } else submit() }

    BackHandler { goBack() }

    if (saving) { PlanBuildingScene(); return }

    Column(Modifier.fillMaxSize().background(HedefitColors.Background).systemBarsPadding().imePadding()) {
        // Üst çubuk: geri, ince ilerleme çizgisi ve sayaç.
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = ::goBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = com.hedefit.app.ui.i18n.tr("Geri", "Back"), tint = HedefitColors.TextPrimary) }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(50)),
                color = HedefitColors.Lime,
                trackColor = HedefitColors.Divider,
                strokeCap = StrokeCap.Round,
            )
            Text("${step + 1}/${questionOrder.size}", Modifier.padding(horizontal = 14.dp), color = HedefitColors.TextSecondary, style = MaterialTheme.typography.labelLarge)
        }

        val fraction = (step + 1).toFloat() / questionOrder.size
        Text(
            when {
                step == 0 -> com.hedefit.app.ui.i18n.tr("Isınma turu — rahat ol, birlikte başlıyoruz 👊", "Warm-up round — relax, we start together 👊")
                fraction < .5f -> com.hedefit.app.ui.i18n.tr("Seni tanıdıkça plan netleşiyor, devam 🔥", "The plan sharpens as we get to know you 🔥")
                fraction < .75f -> com.hedefit.app.ui.i18n.tr("Yarıyı geçtin, tam formdasın 💪", "Past halfway — you're in great form 💪")
                step < questionOrder.lastIndex -> com.hedefit.app.ui.i18n.tr("Son cevaplar — bırakma, neredeyse bitti 🏁", "Last answers — almost there 🏁")
                else -> com.hedefit.app.ui.i18n.tr("Son soru! Planın hazırlanıyor 🏁", "Final question! Your plan is next 🏁")
            },
            Modifier.fillMaxWidth().padding(horizontal = 22.dp), color = HedefitColors.TextSecondary,
            style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )

        AnimatedContent(
            targetState = step,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                if (reduced) fadeIn(tween(150)) togetherWith fadeOut(tween(150))
                else {
                    val dir = if (forward) 1 else -1
                    (slideInHorizontally(tween(320)) { it * dir / 3 } + fadeIn(tween(320))) togetherWith
                        (slideOutHorizontally(tween(260)) { -it * dir / 3 } + fadeOut(tween(200)))
                }
            },
            label = "questionStep",
        ) { shownStep ->
            val shownIndex = questionOrder[shownStep]
            val shown = questionAt(shownIndex)
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (quickStart && shownStep == 0) Text(com.hedefit.app.ui.i18n.tr("Bu ${questionOrder.size} yanıtla ilk programını hemen hazırlayacağız.", "With these ${questionOrder.size} answers we will build your first program right away."), color = HedefitColors.Lime, style = MaterialTheme.typography.labelLarge)
                Text(qt(shown.title), color = HedefitColors.TextPrimary, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, modifier = Modifier.staggeredEntrance(shownStep, 0))
                Text(qt(shown.subtitle), color = HedefitColors.TextSecondary, modifier = Modifier.staggeredEntrance(shownStep, 1))
                Spacer(Modifier.height(8.dp))
                if (shown.freeText) {
                    OutlinedTextField(answers[shownIndex], { answers[shownIndex] = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp).staggeredEntrance(shownStep, 2), placeholder = { Text(com.hedefit.app.ui.i18n.tr("Yanıtını yaz… (boş bırakabilirsin)", "Type your answer… (optional)")) }, colors = questionnaireFieldColors())
                } else if (shown.kind != QuestionKind.Choices) {
                    QuestionExtras(shown, answers, shownIndex, cycleDraft) { enabled ->
                        cycleDraft.enabled = enabled
                        if (!enabled && shownStep < questionOrder.lastIndex) scope.launch {
                            delay(if (reduced) 120 else 320)
                            if (step == shownStep) next()
                        }
                    }
                } else shown.choices.forEachIndexed { choiceIndex, choice ->
                    val selected = if (shown.multiSelect) choice in selectedProfileChoices(answers[shownIndex]) else answers[shownIndex] == choice
                    val bounce = selectionBounce(selected)
                    val border by animateColorAsState(if (selected) HedefitColors.Lime else HedefitColors.Divider, label = "choiceBorder")
                    val fill by animateColorAsState(if (selected) HedefitColors.Lime.copy(alpha = .16f) else HedefitColors.Surface, label = "choiceFill")
                    Row(
                        Modifier.fillMaxWidth().staggeredEntrance(shownStep, choiceIndex + 2).scale(bounce)
                            .clip(RoundedCornerShape(18.dp))
                            .background(fill)
                            .border(BorderStroke(if (selected) 2.dp else 1.dp, border), RoundedCornerShape(18.dp))
                            .selectable(selected = selected, role = if (shown.multiSelect) Role.Checkbox else Role.RadioButton) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                answers[shownIndex] = if (shown.multiSelect) {
                                    toggleProfileChoice(answers[shownIndex], choice, shown.choices, shown.exclusiveChoice)
                                } else choice
                                // Tek seçimli sorularda (hedef kilo alanı olan ilk soru hariç) kısa bir
                                // gecikmeyle otomatik ilerle: seçim animasyonu görünsün, sonra geçilsin.
                                if (!shown.multiSelect && shownIndex != 0 && shownStep < questionOrder.lastIndex) scope.launch {
                                    delay(if (reduced) 120 else 320)
                                    if (step == shownStep) next()
                                }
                            }
                            .padding(horizontal = 18.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(qt(choice), Modifier.weight(1f), color = HedefitColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                        AnimatedVisibility(selected, enter = scaleIn(spring(dampingRatio = .45f)) + fadeIn(), exit = scaleOut() + fadeOut()) {
                            Box(Modifier.size(26.dp).background(HedefitColors.Lime, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = HedefitColors.OnLime, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
                if (shownIndex == 0 && answers[0].isNotBlank() && answers[0] != "Formu koruma") Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
                    OutlinedTextField(targetText, { targetText = it }, label = { Text(com.hedefit.app.ui.i18n.tr("Hedef kilo (kg)", "Target weight (kg)")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth(), colors = questionnaireFieldColors())
                    AnimatedVisibility(target != null && weeks != null, enter = expandVertically() + fadeIn()) {
                        Surface(color = HedefitColors.SurfaceHigh, shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(com.hedefit.app.ui.i18n.tr("Tahmini ", "About "), style = MaterialTheme.typography.titleLarge, color = HedefitColors.Lime)
                                    CountUpText(weeks ?: 0, suffix = com.hedefit.app.ui.i18n.tr(" hafta", " weeks"), fontSizeSp = 22, durationMs = 600)
                                }
                                Text(com.hedefit.app.ui.i18n.tr("Sağlıklı ve sürdürülebilir haftalık değişim hızına göre hesaplandı.", "Based on a healthy, sustainable weekly rate of change."), color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }

        val canContinue = answers.getOrElse(index) { "" }.isNotBlank() || question.freeText || question.kind == QuestionKind.Performance || (question.kind == QuestionKind.CycleOptIn && cycleDraft.enabled != null)
        val isLast = step == questionOrder.lastIndex
        AnimatedVisibility(canContinue || question.multiSelect || isLast, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
            Button(
                enabled = canContinue,
                onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); next() },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp).height(58.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = HedefitColors.Lime, contentColor = HedefitColors.OnLime),
            ) { Text(if (isLast) if (quickStart || firstRun) com.hedefit.app.ui.i18n.tr("Planımı oluştur", "Build my plan") else com.hedefit.app.ui.i18n.tr("Kaydet ve planı yenile", "Save & refresh plan") else com.hedefit.app.ui.i18n.tr("Devam", "Continue"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun questionnaireFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = HedefitColors.TextPrimary,
    unfocusedTextColor = HedefitColors.TextPrimary,
    focusedBorderColor = HedefitColors.Lime,
    unfocusedBorderColor = HedefitColors.Divider,
    focusedLabelColor = HedefitColors.Lime,
    unfocusedLabelColor = HedefitColors.TextSecondary,
    focusedPlaceholderColor = HedefitColors.TextSecondary,
    unfocusedPlaceholderColor = HedefitColors.TextSecondary,
    cursorColor = HedefitColors.Lime,
)

private fun suggestedTarget(profile: ProfileData): Double {
    val weight = profile.weightKg ?: 75.0
    return when {
        profile.goal.contains("ver", true) -> weight - 6
        else -> weight + 4
    }.coerceAtLeast(40.0)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuestionExtras(question: ProfileQuestion, answers: MutableList<String>, index: Int, cycleDraft: CycleDraft, onCycleChoice: (Boolean) -> Unit) {
    when (question.kind) {
        QuestionKind.FocusMap -> {
            val chosen = selectedProfileChoices(answers[index])
            val toggle = { area: String -> answers[index] = toggleProfileChoice(answers[index], area, question.choices, null) }
            com.hedefit.app.ui.components.MuscleMap(
                selected = focusAreaByMuscle.filterValues { it in chosen }.keys,
                onSelect = { id -> focusAreaByMuscle[id]?.let(toggle) },
                modifier = Modifier.padding(vertical = 4.dp),
                description = com.hedefit.app.ui.i18n.tr("Odaklanmak istediğin bölgelere dokun", "Tap the areas you want to focus on"),
            )
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                question.choices.forEach { area ->
                    FilterChip(
                        selected = area in chosen, onClick = { toggle(area) },
                        label = { Text(qt(area), fontWeight = FontWeight.SemiBold) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = HedefitColors.Lime, selectedLabelColor = HedefitColors.OnLime),
                    )
                }
            }
        }
        QuestionKind.Slider -> {
            // Varsayılan değer cevaba yazılır; kullanıcı kaydırmadan da devam edebilir.
            LaunchedEffect(Unit) { if (answers[index].isBlank()) answers[index] = "5" }
            val value = answers[index].toIntOrNull()?.coerceIn(0, 10) ?: 5
            Column(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$value", color = HedefitColors.Lime, style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Black)
                Slider(
                    value = value.toFloat(), onValueChange = { answers[index] = it.toInt().toString() }, valueRange = 0f..10f, steps = 9,
                    colors = SliderDefaults.colors(thumbColor = HedefitColors.Lime, activeTrackColor = HedefitColors.Lime),
                )
                Row(Modifier.fillMaxWidth()) {
                    Text(com.hedefit.app.ui.i18n.tr("Hiç memnun değilim", "Not satisfied at all"), Modifier.weight(1f), color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    Text(com.hedefit.app.ui.i18n.tr("Çok memnunum", "Very satisfied"), color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        QuestionKind.Performance -> {
            val squat = Regex("Squat: (\\d+)").find(answers[index])?.groupValues?.get(1).orEmpty()
            val pullUp = Regex("Barfiks: (\\d+)").find(answers[index])?.groupValues?.get(1).orEmpty()
            fun write(newSquat: String, newPullUp: String) {
                answers[index] = listOfNotNull(
                    newSquat.takeIf(String::isNotBlank)?.let { "Squat: $it" },
                    newPullUp.takeIf(String::isNotBlank)?.let { "Barfiks: $it" },
                ).joinToString(", ")
            }
            OutlinedTextField(squat, { write(it.filter(Char::isDigit).take(3), pullUp) }, label = { Text(com.hedefit.app.ui.i18n.tr("Squat (tekrar)", "Squat (reps)")) }, placeholder = { Text(com.hedefit.app.ui.i18n.tr("örn. 15", "e.g. 15")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth(), colors = questionnaireFieldColors())
            OutlinedTextField(pullUp, { write(squat, it.filter(Char::isDigit).take(3)) }, label = { Text(com.hedefit.app.ui.i18n.tr("Barfiks (tekrar)", "Pull-ups (reps)")) }, placeholder = { Text(com.hedefit.app.ui.i18n.tr("örn. 5", "e.g. 5")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth(), colors = questionnaireFieldColors())
            Surface(color = HedefitColors.SurfaceHigh, shape = RoundedCornerShape(16.dp)) {
                Text(com.hedefit.app.ui.i18n.tr("Bu bilgi, programının ve koçunun seviyeni daha iyi anlamasına yardım eder.", "This helps your program and coach understand your level."), Modifier.padding(16.dp), color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
        }
        QuestionKind.CycleOptIn -> CycleOptInStep(cycleDraft, onCycleChoice)
        QuestionKind.Choices -> Unit
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun CycleOptInStep(draft: CycleDraft, onChoice: (Boolean) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    @Composable
    fun ChoiceCard(selected: Boolean, label: String, onClick: () -> Unit) {
        val border by animateColorAsState(if (selected) HedefitColors.Lime else HedefitColors.Divider, label = "cycleBorder")
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(if (selected) HedefitColors.Lime.copy(alpha = .16f) else HedefitColors.Surface)
                .border(BorderStroke(if (selected) 2.dp else 1.dp, border), RoundedCornerShape(18.dp))
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, Modifier.weight(1f), color = HedefitColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
            if (selected) Icon(Icons.Filled.Check, null, tint = HedefitColors.Lime)
        }
    }
    @Composable
    fun Stepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), color = HedefitColors.TextPrimary)
            OutlinedButton(onClick = { if (value > range.first) onChange(value - 1) }, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(40.dp)) { Text("−") }
            Text("$value", Modifier.width(48.dp), color = HedefitColors.Lime, fontWeight = FontWeight.Black, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            OutlinedButton(onClick = { if (value < range.last) onChange(value + 1) }, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(40.dp)) { Text("+") }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ChoiceCard(draft.enabled == true, com.hedefit.app.ui.i18n.tr("Aktifleştir", "Enable")) { onChoice(true) }
        ChoiceCard(draft.enabled == false, com.hedefit.app.ui.i18n.tr("Şimdi değil", "Not now")) { onChoice(false) }
        AnimatedVisibility(draft.enabled == true, enter = expandVertically() + fadeIn()) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 4.dp)) {
                Text(com.hedefit.app.ui.i18n.tr("Son adetin ne zaman başladı?", "When did your last period start?"), color = HedefitColors.TextPrimary, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { picking = true }, shape = RoundedCornerShape(14.dp)) {
                        Text(draft.lastPeriod?.toString() ?: com.hedefit.app.ui.i18n.tr("Tarih seç", "Pick a date"))
                    }
                    if (draft.lastPeriod != null) TextButton(onClick = { draft.lastPeriod = null }) { Text(com.hedefit.app.ui.i18n.tr("Bilmiyorum", "I don't know"), color = HedefitColors.TextSecondary) }
                }
                Stepper(com.hedefit.app.ui.i18n.tr("Döngü uzunluğu (gün)", "Cycle length (days)"), draft.cycleLength, 21..45) { draft.cycleLength = it; if (draft.periodLength >= it) draft.periodLength = it - 1 }
                Stepper(com.hedefit.app.ui.i18n.tr("Adet süresi (gün)", "Period length (days)"), draft.periodLength, 1..minOf(10, draft.cycleLength - 1)) { draft.periodLength = it }
                Text(com.hedefit.app.ui.i18n.tr("Döngün ne kadar düzenli?", "How regular is your cycle?"), color = HedefitColors.TextPrimary, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "regular" to com.hedefit.app.ui.i18n.tr("Düzenli", "Regular"),
                        "somewhat_irregular" to com.hedefit.app.ui.i18n.tr("Biraz düzensiz", "Somewhat irregular"),
                        "irregular" to com.hedefit.app.ui.i18n.tr("Düzensiz", "Irregular"),
                        "unknown" to com.hedefit.app.ui.i18n.tr("Bilmiyorum", "Not sure"),
                    ).forEach { (value, label) ->
                        FilterChip(
                            selected = draft.regularity == value, onClick = { draft.regularity = value }, label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = HedefitColors.Lime, selectedLabelColor = HedefitColors.OnLime),
                        )
                    }
                }
            }
        }
    }
    if (picking) {
        val today = java.time.LocalDate.now()
        val toMillis = { date: java.time.LocalDate -> date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli() }
        val state = rememberDatePickerState(
            initialSelectedDateMillis = toMillis(draft.lastPeriod ?: today),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= toMillis(today) && utcTimeMillis >= toMillis(today.minusYears(1))
            },
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { draft.lastPeriod = java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate() }
                    picking = false
                }) { Text(com.hedefit.app.ui.i18n.tr("Tamam", "OK")) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(com.hedefit.app.ui.i18n.tr("İptal", "Cancel")) } },
        ) { DatePicker(state = state) }
    }
}
