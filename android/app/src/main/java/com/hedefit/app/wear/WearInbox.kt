package com.hedefit.app.wear

import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Saatten gelen su eklemeleri. Uygulama açıksa akışa verilir; kapalıysa
 * SharedPreferences'ta biriktirilir ve uygulama açılınca [drainPending] ile alınır.
 */
object WearInbox {
    private const val PREFS = "wear_inbox"
    private const val KEY_WATER = "pending_water_ml"
    private val flow = MutableSharedFlow<Int>(extraBufferCapacity = 16)
    val water: SharedFlow<Int> = flow.asSharedFlow()

    @Synchronized
    fun deliverWater(context: Context, ml: Int) {
        if (ml <= 0 || ml > 2_000) return
        if (flow.subscriptionCount.value > 0 && flow.tryEmit(ml)) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_WATER, (prefs.getInt(KEY_WATER, 0) + ml).coerceAtMost(20_000)).apply()
    }

    @Synchronized
    fun drainPending(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val pending = prefs.getInt(KEY_WATER, 0)
        if (pending != 0) prefs.edit().remove(KEY_WATER).apply()
        return pending
    }
}
