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
import com.hedefit.app.billing.BASE_PLAN_MONTHLY
import com.hedefit.app.billing.BASE_PLAN_YEARLY
import com.hedefit.app.billing.BillingUiState
import com.hedefit.app.billing.PRODUCT_PREMIUM
import com.hedefit.app.billing.PRODUCT_PLUS
import com.hedefit.app.billing.PlanOffer
import com.hedefit.app.ui.components.staggeredEntrance
import com.hedefit.app.ui.i18n.tr
import com.hedefit.app.ui.state.TIER_LIMITS
import com.hedefit.app.ui.state.Tier
import com.hedefit.app.ui.theme.HedefitColors

/**
 * Ayarlar → Paketler. Strateji: Plus varsayılan seçili ve "En çok tercih edilen" olarak
 * öne çıkar; Premium üst seçenek olarak durur. Fiyat ve ücretsiz deneme teklifi Google Play'den
 * (BillingManager) gelir; Play'e ulaşılamazsa referans fiyat gösterilir ama satın alma kapalı kalır.
 * Plan yalnızca sunucu doğrulamasından sonra açılır (MainViewModel.purchasePlan).
 * Limitler Entitlements.kt'den gelir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlansSheet(
    current: Tier,
    billing: BillingUiState = BillingUiState(),
    isGuest: Boolean = false,
    onLoadOffers: () -> Unit = {},
    onPurchase: (productId: String, basePlanId: String) -> Unit = { _, _ -> },
    onManage: () -> Unit = {},
    onSaveAccount: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    LaunchedEffect(Unit) { onLoadOffers() }
    var selected by remember { mutableStateOf(if (current == Tier.Premium) Tier.Premium else Tier.Plus) }
    var yearly by remember { mutableStateOf(false) }
    val basePlan = if (yearly) BASE_PLAN_YEARLY else BASE_PLAN_MONTHLY
    val plus = TIER_LIMITS.getValue(Tier.Plus)
    val premium = TIER_LIMITS.getValue(Tier.Premium)

    fun productFor(tier: Tier) = if (tier == Tier.Premium) PRODUCT_PREMIUM else PRODUCT_PLUS
    fun offerFor(tier: Tier): PlanOffer? = billing.offers[productFor(tier)]?.firstOrNull { it.basePlanId == basePlan }
    // Referans fiyatlar: Play Console'daki fiyatlarla aynı (lib/billing/play.ts).
    fun priceText(tier: Tier): String {
        val offer = offerFor(tier)
        val amount = offer?.price ?: when {
            tier == Tier.Premium && yearly -> "₺1.449"
            tier == Tier.Premium -> "₺169"
            yearly -> "₺849"
            else -> "₺99"
        }
        val period = if (yearly) tr("yıl", "year") else tr("ay", "month")
        val trial = offer?.freeTrialDays?.let { tr(" · ilk $it gün ücretsiz", " · first $it days free") }.orEmpty()
        return "$amount / $period$trial"
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = HedefitColors.Background, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(tr("Paketini seç", "Choose your plan"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = HedefitColors.TextPrimary)
            Text(tr("Şu anki paketin: ", "Current plan: ") + current.label, color = HedefitColors.TextSecondary)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !yearly, onClick = { yearly = false }, label = { Text(tr("Aylık", "Monthly")) })
                FilterChip(selected = yearly, onClick = { yearly = true }, label = { Text(tr("Yıllık · %28 indirim", "Yearly · 28% off")) })
            }

            PlanCard(
                tier = Tier.Plus, selected = selected == Tier.Plus, badge = tr("En çok tercih edilen", "Most popular"),
                price = priceText(Tier.Plus), index = 0,
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
                price = priceText(Tier.Premium), index = 1,
                perks = listOf(
                    tr("Plus'taki her şey", "Everything in Plus"),
                    tr("Günde ${premium.dailyCoachQuestions} FitKoç sorusu", "${premium.dailyCoachQuestions} Fit Coach questions a day"),
                    tr("Günde ${premium.dailyPhotoMeals} fotoğraftan kalori", "${premium.dailyPhotoMeals} photo calorie scans a day"),
                    tr("Sınırsız özel program ve bölgesel programlar", "Unlimited custom and body-part programs"),
                    tr("Sınırsız ilerleme geçmişi", "Unlimited progress history"),
                ),
            ) { selected = Tier.Premium }

            val offer = offerFor(selected)
            val owned = current == selected
            val buttonLabel = when {
                isGuest -> tr("Satın almak için hesabını kaydet", "Save your account to subscribe")
                owned -> tr("Şu anki paketin", "Your current plan")
                billing.busy -> tr("İşleniyor…", "Processing…")
                billing.loading -> tr("Fiyatlar yükleniyor…", "Loading prices…")
                offer == null -> tr("Fiyatlar yüklenemedi, yeniden dene", "Couldn't load prices, tap to retry")
                offer.freeTrialDays != null -> tr("${offer.freeTrialDays} gün ücretsiz dene", "Try free for ${offer.freeTrialDays} days")
                current == Tier.Premium -> tr("${selected.label} paketine geç", "Switch to ${selected.label}")
                else -> tr("${selected.label} paketine abone ol", "Subscribe to ${selected.label}")
            }
            Button(
                onClick = {
                    when {
                        isGuest -> { onDismiss(); onSaveAccount() }
                        offer == null -> onLoadOffers()
                        else -> onPurchase(productFor(selected), basePlan)
                    }
                },
                enabled = !owned && !billing.busy && !billing.loading,
                modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HedefitColors.Lime, contentColor = HedefitColors.OnLime,
                    disabledContainerColor = HedefitColors.Lime.copy(alpha = .25f), disabledContentColor = HedefitColors.Lime,
                ),
            ) { Text(buttonLabel, fontWeight = FontWeight.Bold) }

            if (current == Tier.Plus || current == Tier.Premium) {
                TextButton(onClick = onManage, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("Aboneliği yönet veya iptal et", "Manage or cancel subscription"))
                }
            }
            billing.message?.let { Text(it, color = HedefitColors.TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
            Text(
                tr("Ödeme Google Play üzerinden alınır; aboneliği istediğin an Play'den iptal edebilirsin.", "Payment is handled by Google Play; cancel anytime in Play."),
                color = HedefitColors.TextMuted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
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
