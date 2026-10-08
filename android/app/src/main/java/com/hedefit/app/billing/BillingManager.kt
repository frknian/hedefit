package com.hedefit.app.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Kullanıcı akışından (satın alma penceresi) gelen sonuçlar. */
sealed class BillingEvent {
    data class Purchased(val purchases: List<Purchase>) : BillingEvent()
    data class PendingPayment(val purchases: List<Purchase>) : BillingEvent()
    object Canceled : BillingEvent()
    object AlreadyOwned : BillingEvent()
    data class Failed(val code: Int) : BillingEvent()
}

/**
 * Google Play Billing sarmalayıcısı. YALNIZCA Play ile konuşur; plan vermek sunucunun işidir:
 * satın alma sonucu `purchaseToken` olarak /api/billing/verify'a gönderilir, sunucu Google'a
 * sorup planı yazar ve satın almayı onaylar (acknowledge). İstemci hiçbir zaman acknowledge
 * etmez ve plan yazmaz.
 */
class BillingManager(
    context: Context,
    private val onEvent: (BillingEvent) -> Unit,
) {
    private val listener = PurchasesUpdatedListener { result, purchases ->
        val list = purchases.orEmpty().filter { purchase -> purchase.products.any { it in BILLING_PRODUCTS } }
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val bought = list.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                val pending = list.filter { it.purchaseState == Purchase.PurchaseState.PENDING }
                if (bought.isNotEmpty()) onEvent(BillingEvent.Purchased(bought))
                if (pending.isNotEmpty()) onEvent(BillingEvent.PendingPayment(pending))
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> onEvent(BillingEvent.Canceled)
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> onEvent(BillingEvent.AlreadyOwned)
            else -> onEvent(BillingEvent.Failed(result.responseCode))
        }
    }

    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(listener)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    private var detailsByProduct: Map<String, ProductDetails> = emptyMap()

    private suspend fun connect(): Boolean {
        if (client.isReady) return true
        val ready = CompletableDeferred<Boolean>()
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                ready.complete(result.responseCode == BillingClient.BillingResponseCode.OK)
            }

            override fun onBillingServiceDisconnected() {
                // enableAutoServiceReconnection yeniden bağlanır; bekleyen ilk çağrıyı bloklama.
                ready.complete(false)
            }
        })
        return ready.await()
    }

    /** Ürün fiyatlarını ve tekliflerini (deneme dahil) Play'den okur. Başarısızlıkta null. */
    suspend fun loadOffers(): Map<String, List<PlanOffer>>? {
        if (!connect()) return null
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(BILLING_PRODUCTS.map { id ->
                QueryProductDetailsParams.Product.newBuilder().setProductId(id).setProductType(BillingClient.ProductType.SUBS).build()
            })
            .build()
        val details = suspendCancellableCoroutine<List<ProductDetails>?> { continuation ->
            client.queryProductDetailsAsync(params) { result, queryResult ->
                continuation.resume(
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) queryResult.productDetailsList else null,
                )
            }
        } ?: return null
        detailsByProduct = details.associateBy { it.productId }
        return details.associate { product ->
            val raw = product.subscriptionOfferDetails.orEmpty().map { offer ->
                RawOffer(
                    basePlanId = offer.basePlanId,
                    offerId = offer.offerId,
                    offerToken = offer.offerToken,
                    phases = offer.pricingPhases.pricingPhaseList.map { RawPhase(it.priceAmountMicros, it.formattedPrice, it.billingPeriod) },
                )
            }
            product.productId to listOf(BASE_PLAN_MONTHLY, BASE_PLAN_YEARLY).mapNotNull { selectOffer(product.productId, it, raw) }
        }
    }

    /** Hesapta Play'e kayıtlı, Hedefit'e ait satın almalar (yeniden yükleme/restore ve yükseltme için). */
    suspend fun ownedPurchases(): List<Purchase>? {
        if (!connect()) return null
        return suspendCancellableCoroutine { continuation ->
            client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()) { result, purchases ->
                continuation.resume(
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        purchases.filter { purchase -> purchase.products.any { it in BILLING_PRODUCTS } }
                    } else null,
                )
            }
        }
    }

    /**
     * Satın alma penceresini açar. Başka bir Hedefit aboneliği aktifse (Plus ↔ Premium) ikinci bir
     * abonelik açmak yerine Play'in plan değiştirme akışı kullanılır: yükseltme hemen ve orantılı,
     * düşürme dönem sonunda.
     * @return pencere açıldıysa true; ürün/teklif yoksa ya da Play hazır değilse false.
     */
    suspend fun launch(activity: Activity, offer: PlanOffer, userId: String): Boolean {
        if (!connect()) return false
        val details = detailsByProduct[offer.productId] ?: return false
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .setOfferToken(offer.offerToken)
            .build()
        val builder = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            // Sunucu bu değeri sha256("hedefit:"+userId) ile yeniden üretip eşleştirir.
            .setObfuscatedAccountId(obfuscatedAccountId(userId))

        val current = ownedPurchases()?.firstOrNull {
            it.purchaseState == Purchase.PurchaseState.PURCHASED && it.products.none { product -> product == offer.productId }
        }
        if (current != null) {
            val upgrading = offer.productId == PRODUCT_PREMIUM
            builder.setSubscriptionUpdateParams(
                BillingFlowParams.SubscriptionUpdateParams.newBuilder()
                    .setOldPurchaseToken(current.purchaseToken)
                    .setSubscriptionReplacementMode(
                        if (upgrading) BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.CHARGE_PRORATED_PRICE
                        else BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.DEFERRED,
                    )
                    .build(),
            )
        }
        return client.launchBillingFlow(activity, builder.build()).responseCode == BillingClient.BillingResponseCode.OK
    }

    fun release() {
        if (client.isReady) client.endConnection()
    }
}
