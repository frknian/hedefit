package com.hedefit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hedefit.app.ui.components.staggeredEntrance
import com.hedefit.app.ui.i18n.tr
import com.hedefit.app.ui.theme.HedefitColors
import com.hedefit.app.widgets.CustomWidgetConfig

private data class WidgetOption(val key: String, val labelTr: String, val labelEn: String, val sample: String) {
    val label get() = tr(labelTr, labelEn)
}

private val PRIMARY = listOf(
    WidgetOption("steps", "Adım", "Steps", "6.842"), WidgetOption("calories", "Aktif kalori", "Active calories", "312 kcal"),
    WidgetOption("water", "Su", "Water", "1,6 L"), WidgetOption("protein", "Protein", "Protein", "84 g"),
    WidgetOption("distance", "Mesafe", "Distance", "4,8 km"), WidgetOption("active_time", "Aktif süre", "Active time", "46 dk"),
    WidgetOption("weight", "Kilo", "Weight", "78,0 kg"), WidgetOption("workout", "Antrenman", "Workout", "Göğüs günü"),
)
private val SECONDARY = listOf(
    WidgetOption("remaining_steps", "Kalan adım", "Steps left", "1.158 kaldı"), WidgetOption("calories", "Aktif kalori", "Active calories", "312 kcal"),
    WidgetOption("distance", "Mesafe", "Distance", "4,8 km"), WidgetOption("water", "Su", "Water", "1,6 L"),
    WidgetOption("protein", "Protein", "Protein", "84 g"), WidgetOption("pace", "Tempo", "Pace", "6'12\"/km"),
)
private val ACTIONS = listOf(
    WidgetOption("open", "Hedefit'i aç", "Open Hedefit", ""), WidgetOption("workout", "Antrenman başlat", "Start workout", ""),
    WidgetOption("walk", "Yürüyüş başlat", "Start walk", ""), WidgetOption("run", "Koşu başlat", "Start run", ""),
    WidgetOption("water", "Su ekle", "Add water", ""), WidgetOption("meal", "Öğün ekle", "Add meal", ""),
)
private val THEMES = listOf(WidgetOption("minimal", "Sade", "Minimal", ""), WidgetOption("dashboard", "Pano", "Dashboard", ""), WidgetOption("action", "Eylem", "Action", ""))

/**
 * Ayarlar → Ana ekran widget'ları. Hazır widget'lar önizlemeyle listelenir; "Kendi widget'ın"
 * bölümünde ana/ikincil değer, dokunma eylemi ve tema seçilip canlı önizlemeyle ana ekrana eklenir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeWidgetsSheet(onDismiss: () -> Unit, onAddWidget: (String) -> Unit, onAddCustom: (CustomWidgetConfig) -> Unit, onAddShortcut: (String) -> Unit) {
    var primary by remember { mutableStateOf(PRIMARY.first()) }
    var secondary by remember { mutableStateOf(SECONDARY.first()) }
    var action by remember { mutableStateOf(ACTIONS[2]) }
    var theme by remember { mutableStateOf(THEMES[1]) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = HedefitColors.Background, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(tr("Ana ekran widget'ları", "Home screen widgets"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = HedefitColors.TextPrimary)
            Text(tr("Uygulamayı açmadan günün durumunu gör.", "See your day without opening the app."), color = HedefitColors.TextSecondary)

            // Kendi widget'ın
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(HedefitColors.Surface).border(1.5.dp, HedefitColors.Lime.copy(alpha = .5f), RoundedCornerShape(22.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(tr("Kendi widget'ını oluştur", "Build your own widget"), fontWeight = FontWeight.Black, color = HedefitColors.TextPrimary)
                WidgetPreview(primary, secondary, action, theme)
                OptionRow(tr("Ana değer", "Main value"), PRIMARY, primary) { primary = it }
                OptionRow(tr("İkinci değer", "Second value"), SECONDARY, secondary) { secondary = it }
                OptionRow(tr("Dokununca", "On tap"), ACTIONS, action) { action = it }
                OptionRow(tr("Tema", "Theme"), THEMES, theme) { theme = it }
                Button(
                    onClick = { onAddCustom(CustomWidgetConfig(primary.key, secondary.key, action.key, theme.key)) },
                    modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = HedefitColors.Lime, contentColor = HedefitColors.OnLime),
                ) { Text(tr("Ana ekrana ekle", "Add to home screen"), fontWeight = FontWeight.Bold) }
            }

            Text(tr("Hazır widget'lar", "Ready widgets"), fontWeight = FontWeight.Black, color = HedefitColors.TextPrimary, modifier = Modifier.padding(top = 6.dp))
            listOf(
                Triple("widget_dashboard", tr("Bugün", "Today") + " · 4×2", tr("Adım, kalori, su ve program", "Steps, calories, water and program")),
                Triple("widget_activity", tr("Aktivite", "Activity") + " · 3×2", tr("Adım halkası ve aktif kalori", "Step ring and active calories")),
                Triple("widget_workout", tr("Antrenman", "Workout") + " · 4×1", tr("Bugünkü programı tek dokunuşla başlat", "Start today's program in one tap")),
                Triple("widget_route", tr("Rota", "Route") + " · 4×2", tr("Canlı mesafe ve süre", "Live distance and time")),
                Triple("widget_calories", tr("Kalori", "Calories") + " · 2×2", tr("Günlük kalori özeti", "Daily calorie summary")),
            ).forEachIndexed { i, (key, title, desc) ->
                Row(
                    Modifier.fillMaxWidth().staggeredEntrance("widgets", i).clip(RoundedCornerShape(18.dp)).background(HedefitColors.Surface).clickable { onAddWidget(key) }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(HedefitColors.Lime.copy(alpha = .35f), HedefitColors.Water.copy(alpha = .25f)))))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title, fontWeight = FontWeight.Bold, color = HedefitColors.TextPrimary)
                        Text(desc, color = HedefitColors.TextSecondary, fontSize = 12.sp)
                    }
                    Text(tr("Ekle", "Add"), color = HedefitColors.Lime, fontWeight = FontWeight.Black)
                }
            }

            Text(tr("Uygulama kısayolları", "App shortcuts"), fontWeight = FontWeight.Black, color = HedefitColors.TextPrimary, modifier = Modifier.padding(top = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("route" to tr("Rota", "Route"), "workout" to tr("Antrenman", "Workout"), "nutrition" to tr("Öğün", "Meal"), "activity" to tr("Aktivite", "Activity")).forEach { (key, label) ->
                    Box(Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(14.dp)).background(HedefitColors.SurfaceHigh).clickable { onAddShortcut(key) }, contentAlignment = Alignment.Center) {
                        Text(label, color = HedefitColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetPreview(primary: WidgetOption, secondary: WidgetOption, action: WidgetOption, theme: WidgetOption) {
    val bg = when (theme.key) {
        "dashboard" -> Brush.linearGradient(listOf(Color(0xFF12301B), Color(0xFF0B0D0C)))
        "action" -> Brush.linearGradient(listOf(HedefitColors.Lime, HedefitColors.LimeDark))
        else -> Brush.linearGradient(listOf(Color(0xFF0B0D0C), Color(0xFF0B0D0C)))
    }
    val fg = if (theme.key == "action") HedefitColors.OnLime else Color.White
    Column(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(22.dp)).background(bg).padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Text("HEDEFIT", color = fg.copy(alpha = .7f), fontSize = 11.sp, fontWeight = FontWeight.Black)
        Column {
            Text(primary.sample, color = fg, fontSize = 30.sp, fontWeight = FontWeight.Black)
            Text("${primary.label} • ${secondary.sample}", color = fg.copy(alpha = .8f), fontSize = 12.sp)
        }
        Text("▶ ${action.label}", color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun OptionRow(title: String, options: List<WidgetOption>, selected: WidgetOption, onSelect: (WidgetOption) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = HedefitColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { option ->
                val on = option.key == selected.key
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(if (on) HedefitColors.Lime else HedefitColors.SurfaceHigh).clickable { onSelect(option) }.padding(horizontal = 12.dp, vertical = 7.dp),
                ) { Text(option.label, color = if (on) HedefitColors.OnLime else HedefitColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}
