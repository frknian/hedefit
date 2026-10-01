package com.hedefit.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hedefit.app.ui.components.staggeredEntrance
import com.hedefit.app.ui.i18n.tr
import com.hedefit.app.ui.theme.HedefitColors
import kotlin.math.roundToInt

private fun bmiCategory(bmi: Float): Pair<String, Color> = when {
    bmi < 18.5f -> tr("Zayıf", "Underweight") to HedefitColors.Water
    bmi < 25f -> tr("Normal", "Healthy") to HedefitColors.Lime
    bmi < 30f -> tr("Fazla kilolu", "Overweight") to HedefitColors.Warning
    else -> tr("Obez", "Obese") to HedefitColors.Coral
}

/**
 * Onboarding'in ilk adımı: yaş, cinsiyet, boy ve kiloyu tam ekranda alır.
 * Ortadaki figür verilen değerlere göre canlı olarak uzar/kısalır ve incelir/kalınlaşır.
 * Cinsiyet değerleri ("Erkek"/"Kadın"/"Diğer") sunucuya Türkçe kaydedilir.
 */
@Composable
fun BodyProfileScreen(
    initialAge: Int?,
    initialGender: String?,
    initialHeightCm: Double?,
    initialWeightKg: Double?,
    saving: Boolean,
    onSave: (age: Int, gender: String, heightCm: Double, weightKg: Double) -> Unit,
) {
    var gender by rememberSaveable { mutableStateOf(initialGender?.takeIf { it.isNotBlank() } ?: "Erkek") }
    var age by rememberSaveable { mutableFloatStateOf((initialAge ?: 28).toFloat()) }
    var height by rememberSaveable { mutableFloatStateOf((initialHeightCm ?: 172.0).toFloat()) }
    var weight by rememberSaveable { mutableFloatStateOf((initialWeightKg ?: 72.0).toFloat()) }
    val haptic = LocalHapticFeedback.current
    val bmi = weight / ((height / 100f) * (height / 100f))
    val (category, categoryColor) = bmiCategory(bmi)
    val animHeight by animateFloatAsState(height, spring(dampingRatio = .7f, stiffness = Spring.StiffnessLow), label = "h")
    val animBmi by animateFloatAsState(bmi, spring(dampingRatio = .7f, stiffness = Spring.StiffnessLow), label = "bmi")
    val chipColor by animateColorAsState(categoryColor, label = "cat")

    Column(Modifier.fillMaxSize().background(HedefitColors.Background).systemBarsPadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 12.dp)) {
            Text(tr("Seni tanıyalım", "Let's get to know you"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = HedefitColors.TextPrimary, modifier = Modifier.staggeredEntrance("body", 0))
            Text(tr("Kalori, su ve antrenman hedeflerin bu bilgilere göre hesaplanır.", "Your calorie, water and training targets are based on these."), color = HedefitColors.TextSecondary, modifier = Modifier.staggeredEntrance("body", 1))
            Spacer(Modifier.height(14.dp))

            // Cinsiyet
            Row(Modifier.fillMaxWidth().staggeredEntrance("body", 2), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Erkek" to tr("Erkek", "Male"), "Kadın" to tr("Kadın", "Female"), "Diğer" to tr("Diğer", "Other")).forEach { (value, label) ->
                    val selected = gender == value
                    Box(
                        Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(14.dp))
                            .background(if (selected) HedefitColors.Lime.copy(alpha = .16f) else HedefitColors.Surface)
                            .border(if (selected) 2.dp else 1.dp, if (selected) HedefitColors.Lime else HedefitColors.Divider, RoundedCornerShape(14.dp))
                            .clickable { gender = value; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                        contentAlignment = Alignment.Center,
                    ) { Text(label, color = HedefitColors.TextPrimary, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium) }
                }
            }

            // Simülasyon
            Box(Modifier.fillMaxWidth().height(300.dp).padding(vertical = 10.dp), contentAlignment = Alignment.BottomCenter) {
                Canvas(Modifier.fillMaxSize()) { drawBody(animHeight, animBmi, gender) }
                Column(Modifier.align(Alignment.TopEnd), horizontalAlignment = Alignment.End) {
                    Text(tr("VKİ", "BMI"), color = HedefitColors.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("%.1f".format(bmi), color = HedefitColors.TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Black)
                    Surface(color = chipColor.copy(alpha = .16f), shape = RoundedCornerShape(50)) {
                        Text(category, Modifier.padding(horizontal = 10.dp, vertical = 3.dp), color = chipColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            ValueSlider(tr("Yaş", "Age"), age, 14f..90f, 1f, tr("yaş", "yrs")) { age = it }
            ValueSlider(tr("Boy", "Height"), height, 130f..220f, 1f, "cm") { height = it }
            ValueSlider(tr("Kilo", "Weight"), weight, 35f..200f, 0.5f, "kg") { weight = it }
        }
        Button(
            onClick = { onSave(age.roundToInt(), gender, height.roundToInt().toDouble(), (weight * 2).roundToInt() / 2.0) },
            enabled = !saving,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp).height(58.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = HedefitColors.Lime, contentColor = HedefitColors.OnLime),
        ) { Text(if (saving) tr("Kaydediliyor…", "Saving…") else tr("Devam", "Continue"), fontWeight = FontWeight.Bold, fontSize = 17.sp) }
    }
}

@Composable
private fun ValueSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, step: Float, unit: String, onChange: (Float) -> Unit) {
    val haptic = LocalHapticFeedback.current
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(18.dp)).background(HedefitColors.Surface).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(label, color = HedefitColors.TextSecondary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(if (step < 1f) "%.1f".format(value) else value.roundToInt().toString(), color = HedefitColors.TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Black)
            Text(" $unit", color = HedefitColors.TextMuted, modifier = Modifier.padding(bottom = 4.dp))
        }
        Slider(
            value = value,
            onValueChange = { raw ->
                val snapped = (raw / step).roundToInt() * step
                if (snapped != value) { onChange(snapped); if (step >= 1f) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
            },
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = HedefitColors.Lime, activeTrackColor = HedefitColors.Lime, inactiveTrackColor = HedefitColors.Divider),
        )
    }
}

/**
 * Önden figür. Boy figürün yüksekliğini, VKİ gövde/uzuv kalınlığını belirler.
 * Referans: 220 cm sahneyi doldurur; ölçek çizgisi boy etiketini gösterir.
 */
private fun DrawScope.drawBody(heightCm: Float, bmi: Float, gender: String) {
    val ink = HedefitColors.TextPrimary
    val floor = size.height - 6.dp.toPx()
    val figH = (size.height - 16.dp.toPx()) * (heightCm / 220f)
    val top = floor - figH
    val u = figH / 100f
    val cx = size.width / 2f
    // VKİ 22 → 1.0; zayıfta incelir, kiloluda kalınlaşır.
    val fat = ((bmi - 22f) / 12f).coerceIn(-.45f, 1.1f)
    val w = 1f + fat * .85f
    val female = gender == "Kadın"
    fun y(p: Float) = top + p * u

    // Zemin ve boy çizgisi
    drawLine(ink.copy(alpha = .12f), Offset(cx - 60.dp.toPx(), floor), Offset(cx + 60.dp.toPx(), floor), 2.dp.toPx(), StrokeCap.Round)
    drawLine(HedefitColors.Lime.copy(alpha = .5f), Offset(cx + 58.dp.toPx(), top), Offset(cx + 58.dp.toPx(), floor), 1.5.dp.toPx())
    drawLine(HedefitColors.Lime.copy(alpha = .5f), Offset(cx + 52.dp.toPx(), top), Offset(cx + 64.dp.toPx(), top), 1.5.dp.toPx())

    // Bacaklar
    val legW = 5.5f * u * (1f + fat * .7f)
    val hipHalf = (7f + (if (female) 1.5f else 0f)) * u * w
    for (s in listOf(-1f, 1f)) {
        drawLine(ink, Offset(cx + s * hipHalf * .55f, y(54f)), Offset(cx + s * (hipHalf * .55f + .5f * u), y(97f)), legW, StrokeCap.Round)
    }
    // Gövde
    val shoulderHalf = (11f - (if (female) 1.5f else 0f)) * u * (1f + fat * .35f)
    val waistHalf = (7.5f - (if (female) 1.5f else 0f)) * u * w
    val torso = Path().apply {
        moveTo(cx - shoulderHalf, y(19f))
        lineTo(cx + shoulderHalf, y(19f))
        quadraticTo(cx + waistHalf * 1.15f, y(34f), cx + waistHalf, y(40f))
        lineTo(cx + hipHalf, y(56f))
        lineTo(cx - hipHalf, y(56f))
        lineTo(cx - waistHalf, y(40f))
        quadraticTo(cx - waistHalf * 1.15f, y(34f), cx - shoulderHalf, y(19f))
        close()
    }
    drawPath(torso, ink)
    // Göbek bölgesi: kiloluda belirginleşen yuvarlaklık
    if (fat > .15f) drawOval(ink, Offset(cx - waistHalf * 1.1f, y(34f)), Size(waistHalf * 2.2f, 16f * u))
    // Kollar
    val armW = 4.2f * u * (1f + fat * .6f)
    for (s in listOf(-1f, 1f)) {
        drawLine(ink, Offset(cx + s * shoulderHalf, y(20.5f)), Offset(cx + s * (shoulderHalf + 3f * u + fat * 2f * u), y(52f)), armW, StrokeCap.Round)
    }
    // Boyun ve baş
    drawLine(ink, Offset(cx, y(12f)), Offset(cx, y(20f)), 4f * u, StrokeCap.Round)
    drawCircle(ink, 6.2f * u * (1f + fat * .12f), Offset(cx, y(7f)))
    if (female) drawRoundRect(ink, Offset(cx - 7f * u, y(4f)), Size(14f * u, 12f * u), CornerRadius(5f * u))
    // Yüz
    drawCircle(HedefitColors.Background, .9f * u, Offset(cx - 2.2f * u, y(6.5f)))
    drawCircle(HedefitColors.Background, .9f * u, Offset(cx + 2.2f * u, y(6.5f)))
    // Vurgu: bel çizgisi rengi VKİ kategorisine göre
    val accent = bmiCategory(bmi).second
    drawLine(accent, Offset(cx - waistHalf, y(40f)), Offset(cx + waistHalf, y(40f)), 1.6f * u, StrokeCap.Round)
}
