package com.hedefit.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.WavingHand
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hedefit.app.ui.components.HfIconBadge
import com.hedefit.app.ui.components.HfPrimaryButton
import com.hedefit.app.ui.components.HfProgressBar
import com.hedefit.app.ui.theme.HedefitColors

/** Where a walkthrough step's "try it now" button takes the user. */
enum class GuideAction { Workout, Nutrition, Reminders, HealthConnect, Coach }

private data class GuideStep(
    val icon: ImageVector,
    val tint: () -> Color,
    val title: String,
    val body: String,
    val points: List<String>,
    val action: GuideAction? = null,
    val actionLabel: String? = null,
)

private fun guideSteps(en: Boolean, coachName: String) = listOf(
    GuideStep(
        Icons.Default.WavingHand, { HedefitColors.Lime },
        if (en) "Welcome to Hedefit" else "Hedefit'e hoş geldin",
        if (en) "Let's take a one-minute tour of what you can do. You can skip any time and replay it from Settings." else "Neler yapabileceğini bir dakikada gezelim. İstediğin an geçebilir, Ayarlar'dan tekrar izleyebilirsin.",
        if (en) listOf("Workouts, nutrition and progress in one place", "A plan built from your profile", "An AI coach whenever you need it") else listOf("Antrenman, beslenme ve ilerleme tek yerde", "Profiline göre hazırlanan program", "İhtiyaç duyduğunda AI koç"),
    ),
    GuideStep(
        Icons.Default.TrackChanges, { HedefitColors.Water },
        if (en) "Your day at a glance" else "Günün tek bakışta",
        if (en) "The Today tab shows calories, steps, water and sleep as rings. Tap any ring to see details or log quickly." else "Bugün sekmesi kalori, adım, su ve uykuyu halkalarla gösterir. Ayrıntı veya hızlı kayıt için bir halkaya dokun.",
        if (en) listOf("Start today's workout from the top card", "Quick actions for meals, routes and sleep") else listOf("Bugünün antrenmanını en üstteki karttan başlat", "Öğün, rota ve uyku için hızlı işlemler"),
    ),
    GuideStep(
        Icons.Default.FitnessCenter, { HedefitColors.Lime },
        if (en) "Train with a plan" else "Programla çalış",
        if (en) "Create a program with Hedefit AI, a ready template or your own, then record every set while you train." else "Hedefit AI, hazır şablon veya kendi programınla başla; antrenman sırasında her seti kaydet.",
        if (en) listOf("Readiness check adapts the day's load", "Movement Atlas explains every exercise") else listOf("Hazırlık kontrolü günün yükünü uyarlar", "Hareket Atlası her hareketi anlatır"),
        GuideAction.Workout, if (en) "Open my workout" else "Antrenmanıma git",
    ),
    GuideStep(
        Icons.Default.Restaurant, { HedefitColors.Warning },
        if (en) "Log meals in seconds" else "Öğünleri saniyede kaydet",
        if (en) "Tap + to search the catalogue, snap a photo or just describe what you ate. Past days stay browsable." else "+ ile katalogda ara, fotoğraf çek ya da ne yediğini yaz. Geçmiş günlere de dönüp bakabilirsin.",
        if (en) listOf("Calories, macros and water in one card", "Favourites add a repeat meal in one tap") else listOf("Kalori, makro ve su tek kartta", "Favoriler tekrar eden öğünü tek dokunuşla ekler"),
        GuideAction.Nutrition, if (en) "Add my first meal" else "İlk öğünümü ekle",
    ),
    GuideStep(
        Icons.Default.NotificationsActive, { HedefitColors.Coral },
        if (en) "Set gentle reminders" else "Hatırlatıcı kur",
        if (en) "Pick the days and time you want a nudge so workouts and meals don't slip." else "Antrenman ve öğünleri kaçırmamak için hatırlatma istediğin gün ve saati seç.",
        if (en) listOf("Change or turn them off any time") else listOf("İstediğin zaman değiştir ya da kapat"),
        GuideAction.Reminders, if (en) "Set up reminders" else "Hatırlatıcı kur",
    ),
    GuideStep(
        Icons.Default.Favorite, { HedefitColors.Sleep },
        if (en) "Bring in your health data" else "Sağlık verilerini bağla",
        if (en) "Connect Health Connect to sync steps, sleep, weight and calories from Samsung Health, Fitbit and more." else "Samsung Health, Fitbit ve diğerlerinden adım, uyku, kilo ve kaloriyi almak için Health Connect'i bağla.",
        if (en) listOf("Optional — you can connect later in Settings") else listOf("İsteğe bağlı — Ayarlar'dan sonra da bağlayabilirsin"),
        GuideAction.HealthConnect, if (en) "Connect Health Connect" else "Health Connect'i bağla",
    ),
    GuideStep(
        Icons.Default.AutoAwesome, { HedefitColors.Lime },
        if (en) "Meet $coachName" else "$coachName ile tanış",
        if (en) "Ask about training, food or recovery. Suggested actions can adjust your plan with one tap." else "Antrenman, beslenme veya toparlanma hakkında sor. Önerilen eylemler planını tek dokunuşla uyarlar.",
        if (en) listOf("Daily questions grow as you earn XP") else listOf("XP kazandıkça günlük soru hakkın artar"),
        GuideAction.Coach, if (en) "Ask a question" else "Bir soru sor",
    ),
)

@Composable
fun WelcomeGuideDialog(
    language: String,
    coachName: String = if (language == "en") "Fit Coach" else "FitKoç",
    onAction: (GuideAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val en = language == "en"
    val steps = guideSteps(en, coachName)
    var index by rememberSaveable { mutableIntStateOf(0) }
    val step = steps[index]
    val last = index == steps.lastIndex
    val progress by animateFloatAsState((index + 1) / steps.size.toFloat(), label = "guide-progress")
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BackHandler { if (index > 0) index-- else onDismiss() }
        Box(Modifier.fillMaxSize().background(HedefitColors.Background).safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.fillMaxSize().widthIn(max = 560.dp).padding(horizontal = 20.dp, vertical = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (en) "Step ${index + 1} of ${steps.size}" else "Adım ${index + 1} / ${steps.size}",
                        color = HedefitColors.TextSecondary,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    if (!last) TextButton(onClick = onDismiss) { Text(if (en) "Skip" else "Geç", color = HedefitColors.TextSecondary, fontWeight = FontWeight.Bold) }
                }
                HfProgressBar(progress, Modifier.padding(top = 4.dp).semantics { contentDescription = if (en) "Tour progress ${index + 1} of ${steps.size}" else "Rehber ilerlemesi ${index + 1} / ${steps.size}" })
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                    steps.indices.forEach { i ->
                        Box(Modifier.size(if (i == index) 8.dp else 6.dp).background(if (i <= index) HedefitColors.Lime else HedefitColors.SurfaceSoft, CircleShape))
                    }
                }
                AnimatedContent(step, modifier = Modifier.weight(1f), transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "guide-step") { current ->
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    ) {
                        HfIconBadge(current.icon, current.tint(), 96.dp, 46.dp, 30.dp)
                        Text(current.title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                        Text(current.body, color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                        Column(
                            Modifier.fillMaxWidth().background(HedefitColors.Surface, androidx.compose.foundation.shape.RoundedCornerShape(20.dp)).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            current.points.forEach { point ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Box(Modifier.size(22.dp).background(current.tint().copy(alpha = .15f), CircleShape), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Check, null, tint = current.tint(), modifier = Modifier.size(14.dp))
                                    }
                                    Text(point, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                        if (current.action != null && current.actionLabel != null) {
                            HfPrimaryButton(current.actionLabel, { onAction(current.action) }, Modifier.fillMaxWidth(), secondary = true)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (index > 0) HfPrimaryButton(if (en) "Back" else "Geri", { index-- }, icon = Icons.AutoMirrored.Filled.ArrowBack, secondary = true)
                    HfPrimaryButton(
                        if (last) (if (en) "Let's start" else "Başlayalım") else if (en) "Next" else "İleri",
                        { if (last) onDismiss() else index++ },
                        Modifier.weight(1f),
                        icon = if (last) Icons.Default.Check else Icons.AutoMirrored.Filled.ArrowForward,
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}
