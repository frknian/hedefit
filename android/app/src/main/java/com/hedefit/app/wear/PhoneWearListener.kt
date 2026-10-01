package com.hedefit.app.wear

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

/** Saatten gelen mesajları karşılar (uygulama kapalıyken de çalışır). */
class PhoneWearListener : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path == WearSync.WATER_PATH) {
            val ml = String(event.data, Charsets.UTF_8).toIntOrNull() ?: return
            WearInbox.deliverWater(applicationContext, ml)
        }
    }
}
