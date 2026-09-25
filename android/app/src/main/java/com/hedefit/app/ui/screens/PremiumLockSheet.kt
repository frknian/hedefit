package com.hedefit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hedefit.app.ui.components.staggeredEntrance
import com.hedefit.app.ui.state.LockedFeature
import com.hedefit.app.ui.state.TIER_LIMITS
import com.hedefit.app.ui.state.Tier
import com.hedefit.app.ui.theme.HedefitColors

/**
 * Ücretsiz kullanıcı bir kilide takılınca gösterilir. Ödeme akışı henüz yok;
 * satın alma eklendiğinde yalnız alttaki buton bağlanır, limitler Entitlements.kt'den gelir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumLockSheet(feature: LockedFeature, onDismiss: () -> Unit) {
    val tiers = listOf(Tier.Free, Tier.Plus, Tier.Premium)
    val limits = tiers.map { TIER_LIMITS.getValue(it) }
    fun fmt(value: Int) = if (value == Int.MAX_VALUE) "∞" else "$value"
    fun yesNo(value: Boolean) = if (value) "✓" else "—"
    fun levels(set: Set<String>) = when (set.size) { 1 -> com.hedefit.app.ui.i18n.tr("Başl.", "Beg."); 2 -> com.hedefit.app.ui.i18n.tr("Başl.+Orta", "Beg.+Int."); else -> "Tümü" }
    val rows: List<Pair<String, List<String>>> = listOf(
        com.hedefit.app.ui.i18n.tr("FitKoç soru / gün", "Fit Coach Qs / day") to limits.map { fmt(it.dailyCoachQuestions) },
        com.hedefit.app.ui.i18n.tr("Öğün kaydı / gün", "Meal logs / day") to limits.map { fmt(it.dailyMealLogs) },
        com.hedefit.app.ui.i18n.tr("Fotoğraftan kalori", "Photo calories") to limits.map { fmt(it.dailyPhotoMeals) },
        com.hedefit.app.ui.i18n.tr("Hareketler", "Exercises") to limits.map { levels(it.exerciseLevels) },
        com.hedefit.app.ui.i18n.tr("Kendi programın", "Own programs") to limits.map { fmt(it.customPrograms) },
        com.hedefit.app.ui.i18n.tr("Öğün planlayıcı", "Meal planner") to limits.map { yesNo(it.mealPlanner) },
        com.hedefit.app.ui.i18n.tr("Bölgesel program", "Regional program") to limits.map { yesNo(it.regionalPlans) },
        com.hedefit.app.ui.i18n.tr("Geçmiş (gün)", "History (days)") to limits.map { fmt(it.historyDays) },
        com.hedefit.app.ui.i18n.tr("Reklamsız", "Ad-free") to limits.map { yesNo(!it.bannerAds && !it.interstitialAds) },
    )
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = HedefitColors.Surface, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(feature.emoji, fontSize = 48.sp, modifier = Modifier.staggeredEntrance(feature, 0))
            Text(feature.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = HedefitColors.TextPrimary, textAlign = TextAlign.Center, modifier = Modifier.staggeredEntrance(feature, 1))
            Text(feature.body, color = HedefitColors.TextSecondary, textAlign = TextAlign.Center, modifier = Modifier.staggeredEntrance(feature, 2))
            Column(Modifier.fillMaxWidth().background(HedefitColors.SurfaceHigh, RoundedCornerShape(18.dp)).padding(14.dp).staggeredEntrance(feature, 3), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row {
                    Spacer(Modifier.weight(1.5f))
                    tiers.forEach { tier ->
                        Text(tier.label, Modifier.weight(1f), color = if (tier == Tier.Free) HedefitColors.TextMuted else HedefitColors.Warning, fontSize = 12.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                    }
                }
                rows.forEach { (label, values) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, Modifier.weight(1.5f), color = HedefitColors.TextPrimary, fontSize = 13.sp)
                        values.forEachIndexed { i, v ->
                            Text(v, Modifier.weight(1f), color = if (i == 0) HedefitColors.TextSecondary else HedefitColors.TextPrimary, fontSize = 13.sp, fontWeight = if (i == 0) FontWeight.Normal else FontWeight.Bold, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
            Button(
                onClick = onDismiss, enabled = false,
                modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(disabledContainerColor = HedefitColors.Warning.copy(alpha = .25f), disabledContentColor = HedefitColors.Warning),
            ) { Text(com.hedefit.app.ui.i18n.tr("Plus ve Premium çok yakında", "Plus & Premium coming soon"), fontWeight = FontWeight.Bold) }
            TextButton(onClick = onDismiss) { Text(com.hedefit.app.ui.i18n.tr("Tamam", "OK"), color = HedefitColors.TextSecondary) }
        }
    }
}
