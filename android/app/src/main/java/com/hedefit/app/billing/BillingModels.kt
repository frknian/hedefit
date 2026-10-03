package com.hedefit.app.billing

import org.json.JSONObject
import java.security.MessageDigest

/** Play Console'daki abonelik ürünleri. Sunucudaki lib/billing/play.ts ile AYNI olmalı. */
const val PRODUCT_PLUS = "hedefit_plus"
const val PRODUCT_PREMIUM = "hedefit_premium"
val BILLING_PRODUCTS = listOf(PRODUCT_PLUS, PRODUCT_PREMIUM)

const val BASE_PLAN_MONTHLY = "monthly"
const val BASE_PLAN_YEARLY = "yearly"

/** Play'den gelen ProductDetails'in test edilebilir, Play'e bağımlı olmayan özeti. */
data class RawPhase(val priceMicros: Long, val formattedPrice: String, val billingPeriod: String)
data class RawOffer(val basePlanId: String, val offerId: String?, val offerToken: String, val phases: List<RawPhase>)

/** Arayüzde gösterilen bir fiyat seçeneği (bir ürünün bir base plan'ı). */
data class PlanOffer(
    val productId: String,
    val basePlanId: String,
    val offerToken: String,
    /** Yenilenen (son) fazın fiyatı, örn. "₺99,00". */
    val price: String,
    /** Ücretsiz deneme günü; yoksa null. Play yalnız hak sahibi kullanıcıya deneme teklifini döndürür. */
    val freeTrialDays: Int?,
)

data class BillingUiState(
    val offers: Map<String, List<PlanOffer>> = emptyMap(),
    val loading: Boolean = false,
    val loadFailed: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
)

/**
 * Play'in obfuscatedExternalAccountId değeri: sunucu (lib/billing/play.ts) aynı değeri
 * sha256("hedefit:" + userId) ile üretip karşılaştırır; uyuşmayan satın alma reddedilir.
 */
fun obfuscatedAccountId(userId: String): String =
    MessageDigest.getInstance("SHA-256").digest("hedefit:$userId".toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

/** ISO-8601 süre ("P7D", "P1W", "P1M"...) için gün sayısı; yalnız gün/hafta kesin olarak çevrilir. */
fun isoPeriodToDays(period: String): Int? {
    val match = Regex("^P(?:(\\d+)W)?(?:(\\d+)D)?$").matchEntire(period) ?: return null
    val weeks = match.groupValues[1].toIntOrNull() ?: 0
    val days = match.groupValues[2].toIntOrNull() ?: 0
    val total = weeks * 7 + days
    return total.takeIf { it > 0 }
}

/**
 * Bir base plan için gösterilecek teklifi seçer: ücretsiz deneme fazı olan teklif varsa o
 * (Play, hak sahibi olmayan kullanıcıya deneme teklifini zaten döndürmez), yoksa deneme
 * içermeyen temel teklif. Ücretsiz değil ama indirimli tanıtım fazı olan teklifler seçilmez.
 */
fun selectOffer(productId: String, basePlanId: String, offers: List<RawOffer>): PlanOffer? {
    val candidates = offers.filter { it.basePlanId == basePlanId && it.phases.isNotEmpty() }
    fun trialDays(offer: RawOffer): Int? =
        offer.phases.firstOrNull()?.takeIf { it.priceMicros == 0L && offer.phases.size > 1 }?.let { isoPeriodToDays(it.billingPeriod) }
    val withTrial = candidates.firstOrNull { trialDays(it) != null }
    val base = candidates.firstOrNull { it.offerId == null }
    val chosen = withTrial ?: base ?: return null
    return PlanOffer(
        productId = productId,
        basePlanId = basePlanId,
        offerToken = chosen.offerToken,
        price = chosen.phases.last().formattedPrice,
        freeTrialDays = trialDays(chosen),
    )
}

sealed class VerifyOutcome {
    /** Plan açıldı. [planTier]: "plus" | "pro" | "free" (süresi dolmuşsa). */
    data class Granted(val planTier: String, val entitled: Boolean) : VerifyOutcome()
    /** Ödeme henüz tamamlanmadı (örn. nakit ödeme); sunucu plan açmadı. */
    object Pending : VerifyOutcome()
    /** Kalıcı ret: bu satın alma bu hesaba bağlanamaz (başka hesapta kullanılmış / uyuşmuyor). */
    object Rejected : VerifyOutcome()
    /** Geçici hata (ağ, Google, sunucu); satın alma korunur, sonra yeniden denenir. */
    object Retry : VerifyOutcome()
}

fun parseVerifyResponse(status: Int, body: String): VerifyOutcome = when {
    status == 202 -> VerifyOutcome.Pending
    status in 200..299 -> runCatching {
        val json = JSONObject(body)
        VerifyOutcome.Granted(json.optString("plan_tier", "free"), json.optBoolean("entitled", false))
    }.getOrDefault(VerifyOutcome.Retry)
    // Satın alma doğrulanamadı / başka hesaba bağlı: tekrar denemek sonucu değiştirmez.
    status == 400 || status == 409 || status == 403 -> VerifyOutcome.Rejected
    else -> VerifyOutcome.Retry
}
