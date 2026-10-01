package com.hedefit.app.wear

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

/** Saatten gelen mesajları karşılar (uygulama kapalıyken de çalışır). */
class PhoneWearListener : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            WearSync.WATER_PATH -> String(event.data, Charsets.UTF_8).toIntOrNull()?.let { WearInbox.deliverWater(applicationContext, it) }
            WearSync.WORKOUT_PATH -> WearInbox.deliverWorkout(applicationContext, String(event.data, Charsets.UTF_8))
        }
    }
}
