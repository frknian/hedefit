package com.hedefit.app.wear

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import org.json.JSONObject

/** Saatten gelen mesajları karşılar (uygulama kapalıyken de çalışır). */
class PhoneWearListener : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        val body = String(event.data, Charsets.UTF_8)
        when (event.path) {
            WearSync.WATER_PATH -> body.toIntOrNull()?.let { WearInbox.deliverWater(applicationContext, it) }
            WearSync.WORKOUT_PATH -> WearInbox.deliverWorkout(applicationContext, body)
            WearSync.ASK_PATH -> runCatching {
                val o = JSONObject(body)
                val ask = WatchAsk(event.sourceNodeId, o.getString("id"), o.getString("mode"), o.getString("text").take(300))
                if (ask.mode in setOf("chat", "food") && !WearInbox.deliverAsk(ask)) {
                    WearSync.reply(applicationContext, ask.nodeId, ask.id, false, "Telefonda Hedefit uygulamasını aç ve tekrar dene.")
                }
            }
        }
    }
}
