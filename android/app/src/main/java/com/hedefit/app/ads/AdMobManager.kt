package com.hedefit.app.ads

import android.app.Activity
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.rewarded.ServerSideVerificationOptions
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.hedefit.app.BuildConfig

class AdMobManager(private val activity: Activity) {
    private var interstitial: InterstitialAd? = null
    private var rewarded: RewardedAd? = null

    /** Ödüllü reklam yüklendi ve gösterilmeye hazır mı (Compose durumu: arayüz buna bakar). */
    var rewardedReady by mutableStateOf(false)
        private set

    /** Release derlemesinde birim kimliği tanımlı mı; değilse "reklam izle" gösterilmez. */
    val rewardedConfigured: Boolean get() = BuildConfig.DEBUG || BuildConfig.ADMOB_REWARDED_AD_UNIT_ID.isNotBlank()
    private var lastShownAt = 0L
    private var shownToday = 0

    fun requestConsent(onReady: (Boolean) -> Unit) {
        val consent = UserMessagingPlatform.getConsentInformation(activity)
        consent.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    val allowed = consent.canRequestAds()
                    if (allowed) {
                        MobileAds.initialize(activity)
                        loadInterstitial()
                        loadRewarded()
                    }
                    onReady(allowed)
                }
            },
            { onReady(false) },
        )
    }

    fun loadInterstitial() {
        val unitId = if (BuildConfig.DEBUG) TEST_INTERSTITIAL else BuildConfig.ADMOB_INTERSTITIAL_AD_UNIT_ID
        InterstitialAd.load(activity, unitId, AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) { interstitial = ad }
            override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) { interstitial = null }
        })
    }

    fun loadRewarded() {
        if (!rewardedConfigured || rewarded != null) return
        val unitId = if (BuildConfig.DEBUG) TEST_REWARDED else BuildConfig.ADMOB_REWARDED_AD_UNIT_ID
        RewardedAd.load(activity, unitId, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) { rewarded = ad; rewardedReady = true }
            override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) { rewarded = null; rewardedReady = false }
        })
    }

    /**
     * Ödüllü reklamı gösterir. Ödül SUNUCUDA, AdMob'un imzalı SSV callback'iyle verilir: userId ve feature
     * (custom data) Google üzerinden callback'e taşınır. [onEarned] yalnız "reklam bitti" bilgisidir, hak
     * vermez; çağıran sunucudaki bonusu yoklar. Reklam hazır değilse false döner.
     */
    fun showRewarded(userId: String, feature: String, onEarned: () -> Unit, onClosedWithoutReward: () -> Unit): Boolean {
        val ad = rewarded ?: return false
        rewarded = null
        rewardedReady = false
        ad.setServerSideVerificationOptions(ServerSideVerificationOptions.Builder().setUserId(userId).setCustomData(feature).build())
        var earned = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                loadRewarded()
                if (!earned) onClosedWithoutReward()
            }
            override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                loadRewarded()
                onClosedWithoutReward()
            }
        }
        ad.show(activity) { earned = true; onEarned() }
        return true
    }

    /** Only call at a natural completed-flow transition; never during AI, meals, or active training. */
    fun showAtNaturalTransition(isFreePlan: Boolean) {
        val now = System.currentTimeMillis()
        if (!isFreePlan || shownToday >= 3 || now - lastShownAt < 10 * 60 * 1000L) return
        val ad = interstitial ?: return
        interstitial = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { loadInterstitial() }
            override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) { loadInterstitial() }
        }
        shownToday += 1
        lastShownAt = now
        ad.show(activity)
    }

    companion object {
        const val TEST_BANNER = "ca-app-pub-3940256099942544/6300978111"
        const val TEST_INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"
        const val TEST_REWARDED = "ca-app-pub-3940256099942544/5224354917"
    }
}
