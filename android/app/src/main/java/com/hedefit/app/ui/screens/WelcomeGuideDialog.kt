package com.hedefit.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hedefit.app.ui.components.ActivityRing
import com.hedefit.app.ui.components.staggeredEntrance
import com.hedefit.app.ui.i18n.tr
import com.hedefit.app.ui.theme.HedefitColors

enum class GuideAction { Workout, Nutrition, Cardio, Coach, Reminders, HealthConnect }

private data class GuideMission(val action: GuideAction, val icon: ImageVector, val tint: Color, val title: String, val body: String, val cta: String)

private fun missions(coachName: String) = listOf(
    GuideMission(GuideAction.Workout, Icons.Default.FitnessCenter, HedefitColors.Lime, tr("İlk antrenmanını başlat", "Start your first workout"), tr("Programın hazır. Bir seti tamamla, sayacın ve XP'nin nasıl çalıştığını gör.", "Your program is ready. Finish a set and see the timer and XP in action."), tr("Antrenmana git", "Go to workout")),
    GuideMission(GuideAction.Nutrition, Icons.Default.Restaurant, HedefitColors.Warning, tr("İlk öğününü ekle", "Log your first meal"), tr("Yaz, fotoğraf çek ya da ara. Kalori ve makrolar otomatik hesaplanır.", "Type it, snap a photo or search. Calories and macros are calculated for you."), tr("Öğün ekle", "Add a meal")),
    GuideMission(GuideAction.Cardio, Icons.AutoMirrored.Filled.DirectionsRun, HedefitColors.Coral, tr("Kardiyoyu dene", "Try cardio"), tr("Koşu bandı, bisiklet ya da açık hava. Yaktığın kalori günlük hedefine eklenir.", "Treadmill, bike or outdoors. Burned calories are added to your daily target."), tr("Kardiyoyu aç", "Open cardio")),
    GuideMission(GuideAction.Coach, Icons.Default.AutoAwesome, HedefitColors.Sleep, tr("$coachName'a bir soru sor", "Ask $coachName a question"), tr("Antrenman, beslenme ya da motivasyon: seni tanıyan koçuna sor.", "Training, food or motivation: ask the coach who knows you."), tr("Soru sor", "Ask")),
    GuideMission(GuideAction.Reminders, Icons.Default.NotificationsActive, HedefitColors.Water, tr("Hatırlatma kur", "Set a reminder"), tr("Antrenman günlerinde seni programına geri getirir.", "Brings you back to your plan on training days."), tr("Hatırlatma kur", "Set reminder")),
    GuideMission(GuideAction.HealthConnect, Icons.Default.Watch, HedefitColors.Lime, tr("Saatini ya da adımlarını bağla", "Connect your watch or steps"), tr("Adım, uyku ve nabız otomatik gelsin.", "Get steps, sleep and heart rate automatically."), tr("Bağla", "Connect")),
)

/** Başlangıç görevlerinin ilerlemesi (ana ekran kartı için). */
fun guideProgress(completed: Set<GuideAction>) = completed.count() to GuideAction.entries.size

/**
 * Uygulamalı başlangıç rehberi: her görev kullanıcıyı ilgili ekrana götürür ve kullanıcı
 * o işi gerçekten yaptığında (veriye göre) otomatik tamamlanır. Görevler bitene kadar
 * ana ekrandaki "Başlangıç görevleri" kartından yeniden açılabilir.
 */
@Composable
fun WelcomeGuideDialog(
    language: String,
    coachName: String = if (language == "en") "Fit Coach" else "FitKoç",
    completed: Set<GuideAction> = emptySet(),
    onAction: (GuideAction) -> Unit,
    onDismiss: () -> Unit,
    onSkip: (GuideAction) -> Unit = {},
    mandatory: Boolean = false,
) {
    val list = missions(coachName)
    val done = list.count { it.action in completed }
    val allDone = done == list.size
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = !mandatory || allDone, dismissOnClickOutside = false)) {
        Column(Modifier.fillMaxSize().background(HedefitColors.Background).systemBarsPadding()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Hedefit'i birlikte keşfedelim", "Let's explore Hedefit together"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = HedefitColors.TextPrimary)
                        Text(tr("Her görevi gerçekten yaparak öğren. Yaptıkça otomatik işaretlenir.", "Learn by doing. Each mission ticks itself off when you complete it."), color = HedefitColors.TextSecondary)
                    }
                    Spacer(Modifier.width(12.dp))
                    ActivityRing(done / list.size.toFloat(), HedefitColors.Lime, size = 64.dp, stroke = 7.dp) {
                        Text("$done/${list.size}", fontWeight = FontWeight.Black, color = HedefitColors.TextPrimary)
                    }
                }
                list.forEachIndexed { index, mission ->
                    val isDone = mission.action in completed
                    val border by animateColorAsState(if (isDone) HedefitColors.Lime else HedefitColors.Divider, label = "mission")
                    Row(
                        Modifier.fillMaxWidth().staggeredEntrance("guide", index).clip(RoundedCornerShape(20.dp))
                            .background(HedefitColors.Surface).border(1.dp, border, RoundedCornerShape(20.dp))
                            .clickable(enabled = !isDone) { onAction(mission.action) }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(44.dp).background(if (isDone) HedefitColors.Lime else mission.tint.copy(alpha = .16f), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(if (isDone) Icons.Default.Check else mission.icon, null, tint = if (isDone) HedefitColors.OnLime else mission.tint, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(mission.title, fontWeight = FontWeight.Bold, color = if (isDone) HedefitColors.TextMuted else HedefitColors.TextPrimary, textDecoration = if (isDone) TextDecoration.LineThrough else null)
                            if (!isDone) Text(mission.body, color = HedefitColors.TextSecondary, fontSize = 13.sp)
                        }
                        if (!isDone) Text(mission.cta, color = mission.tint, fontWeight = FontWeight.Black, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
                    }
                    // Saati olmayanlar ya da Health Connect'i desteklemeyen cihazlar rehberde takılmasın.
                    if (!isDone && mission.action == GuideAction.HealthConnect) TextButton(onClick = { onSkip(mission.action) }, modifier = Modifier.align(Alignment.End)) {
                        Text(tr("Saatim yok, atla", "No watch, skip"), color = HedefitColors.TextMuted, fontSize = 13.sp)
                    }
                }
            }
            Button(
                onClick = onDismiss, enabled = allDone || !mandatory,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp).height(54.dp), shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (done == list.size) HedefitColors.Lime else HedefitColors.SurfaceHigh, contentColor = if (done == list.size) HedefitColors.OnLime else HedefitColors.TextPrimary),
            ) { Text(if (done == list.size) tr("Hepsi tamam! 🎉", "All done! 🎉") else if (mandatory) tr("Devam etmek için görevleri tamamla (${done}/${list.size})", "Finish the missions to continue (${done}/${list.size})") else tr("Kapat", "Close"), fontWeight = FontWeight.Bold) }
        }
    }
}
