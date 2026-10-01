package com.hedefit.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hedefit.app.ui.components.staggeredEntrance
import com.hedefit.app.ui.i18n.tr
import com.hedefit.app.ui.state.TIER_LIMITS
import com.hedefit.app.ui.state.Tier
import com.hedefit.app.ui.theme.HedefitColors

/**
 * Ayarlar → Paketler. Strateji: Plus varsayılan seçili ve "En çok tercih edilen" olarak
 * öne çıkar; Premium üst seçenek olarak durur. Ödeme (Google Play Billing) eklenene kadar
 * satın alma butonu "yakında" gösterir; limitler Entitlements.kt'den gelir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlansSheet(current: Tier, onDismiss: () -> Unit) {
    var selected by remember { mutableStateOf(if (current == Tier.Premium) Tier.Premium else Tier.Plus) }
    val plus = TIER_LIMITS.getValue(Tier.Plus)
    val premium = TIER_LIMITS.getValue(Tier.Premium)
    fun n(v: Int) = if (v == Int.MAX_VALUE) tr("Sınırsız", "Unlimited") else "$v"

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = HedefitColors.Background, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(tr("Paketini seç", "Choose your plan"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = HedefitColors.TextPrimary)
            Text(tr("Şu anki paketin: ", "Current plan: ") + current.label, color = HedefitColors.TextSecondary)

            PlanCard(
                tier = Tier.Plus, selected = selected == Tier.Plus, badge = tr("En çok tercih edilen", "Most popular"),
                price = tr("Aylık ₺—", "₺— / month"), index = 0,
                perks = listOf(
                    tr("Günde ${plus.dailyCoachQuestions} FitKoç sorusu", "${plus.dailyCoachQuestions} Fit Coach questions a day"),
                    tr("Sınırsız öğün kaydı", "Unlimited meal logging"),
                    tr("Tüm hareketler ve ${plus.customPrograms} özel program", "All exercises and ${plus.customPrograms} custom programs"),
                    tr("Kardiyo programları ve oyun modu", "Cardio programs and game mode"),
                    tr("Haftalık öğün planlayıcı", "Weekly meal planner"),
                    tr("Reklamsız", "Ad-free"),
                ),
            ) { selected = Tier.Plus }
            PlanCard(
                tier = Tier.Premium, selected = selected == Tier.Premium, badge = null,
                price = tr("Aylık ₺—", "₺— / month"), index = 1,
                perks = listOf(
                    tr("Plus'taki her şey", "Everything in Plus"),
                    tr("Günde ${premium.dailyCoachQuestions} FitKoç sorusu", "${premium.dailyCoachQuestions} Fit Coach questions a day"),
                    tr("Günde ${premium.dailyPhotoMeals} fotoğraftan kalori", "${premium.dailyPhotoMeals} photo calorie scans a day"),
                    tr("Sınırsız özel program ve bölgesel programlar", "Unlimited custom and body-part programs"),
                    tr("Sınırsız ilerleme geçmişi", "Unlimited progress history"),
                ),
            ) { selected = Tier.Premium }

            Button(
                onClick = {}, enabled = false,
                modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(disabledContainerColor = HedefitColors.Lime.copy(alpha = .25f), disabledContentColor = HedefitColors.Lime),
            ) { Text(tr("${selected.label} satın alma çok yakında", "${selected.label} purchase coming soon"), fontWeight = FontWeight.Bold) }
            Text(tr("Ödeme Google Play üzerinden alınır; aboneliği istediğin an iptal edebilirsin.", "Payment is handled by Google Play; cancel anytime."), color = HedefitColors.TextMuted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun PlanCard(tier: Tier, selected: Boolean, badge: String?, price: String, index: Int, perks: List<String>, onClick: () -> Unit) {
    val border by animateColorAsState(if (selected) HedefitColors.Lime else HedefitColors.Divider, label = "planBorder")
    Column(
        Modifier.fillMaxWidth().staggeredEntrance("plans", index).clip(RoundedCornerShape(22.dp))
            .background(if (selected) HedefitColors.Lime.copy(alpha = .08f) else HedefitColors.Surface)
            .border(if (selected) 2.dp else 1.dp, border, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tier.label, color = HedefitColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            badge?.let {
                Surface(color = HedefitColors.Lime, shape = RoundedCornerShape(50)) {
                    Text(it, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), color = HedefitColors.OnLime, fontSize = 11.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        Text(price, color = HedefitColors.TextSecondary, fontWeight = FontWeight.Bold)
        perks.forEach { Text("✓  $it", color = HedefitColors.TextPrimary, fontSize = 14.sp) }
    }
}
