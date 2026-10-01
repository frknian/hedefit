package com.hedefit.wear.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.hedefit.wear.R
import com.hedefit.wear.complication.StepsComplicationService
import com.hedefit.wear.complication.StreakComplicationService
import com.hedefit.wear.complication.WaterComplicationService
import com.hedefit.wear.tile.SummaryTileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.util.UUID

private const val SNAPSHOT_PATH = "/hedefit/snapshot"
private const val WATER_PATH = "/hedefit/water"
private const val WORKOUT_PATH = "/hedefit/workout"
private const val ASK_PATH = "/hedefit/ask"
private const val REPLY_PATH = "/hedefit/reply"
private const val NAV_PATH = "/hedefit/nav"

data class Reply(val id: String, val ok: Boolean, val text: String)
data class NavCue(val type: String, val distanceM: Int, val atMillis: Long = System.currentTimeMillis())

/** Telefondan gelen yanıt ve rota ipuçları; arayüz bunları dinler. */
object WatchEvents {
    private val replyFlow = MutableSharedFlow<Reply>(extraBufferCapacity = 4)
    private val navFlow = MutableSharedFlow<NavCue>(extraBufferCapacity = 4, replay = 1)
    val replies: SharedFlow<Reply> = replyFlow.asSharedFlow()
    val nav: SharedFlow<NavCue> = navFlow.asSharedFlow()
    internal fun emit(reply: Reply) = replyFlow.tryEmit(reply)
    internal fun emit(cue: NavCue) = navFlow.tryEmit(cue)
}

/** Saat -> telefon: su, antrenman ve istekler. Telefon erişilemezse su ve antrenman kuyruğa alınır. */
object PhoneLink {
    private const val PREFS = "phone_link"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Özeti iyimser günceller, mesajı gönderir. */
    suspend fun addWater(context: Context, ml: Int) {
        queueWaterLocally(context, ml)
        flush(context)
    }

    /** Tile gibi askıda çalışamayan yerlerden: yerelde hemen günceller, gönderimi arka planda yapar. */
    fun addWaterInBackground(context: Context, ml: Int) {
        queueWaterLocally(context, ml)
        val app = context.applicationContext
        scope.launch { flush(app) }
    }

    private fun queueWaterLocally(context: Context, ml: Int) {
        val current = SnapshotStore.load(context)
        publish(context, current.copy(waterMl = (current.waterMl + ml).coerceIn(0, 20_000)))
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putInt("pending", prefs.getInt("pending", 0) + ml).apply()
    }

    /** Biten antrenmanı kuyruğa alır ve gönderir; telefon erişilemezse sonraki açılışta tekrar dener. */
    suspend fun queueWorkout(context: Context, kind: String, durationSec: Long, distanceM: Double, calories: Int, startMillis: Long, sets: List<SetLog>) {
        if (durationSec < 60) return
        val json = WorkoutPayload.build(kind, durationSec, distanceM, calories, startMillis, sets)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putStringSet("workouts", prefs.getStringSet("workouts", emptySet()).orEmpty() + json).apply()
        flush(context)
    }

    suspend fun flush(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val water = prefs.getInt("pending", 0)
        val workouts = prefs.getStringSet("workouts", emptySet()).orEmpty()
        if (water <= 0 && workouts.isEmpty()) return
        runCatching {
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            if (nodes.isEmpty()) return
            val client = Wearable.getMessageClient(context)
            if (water > 0) {
                nodes.forEach { client.sendMessage(it.id, WATER_PATH, water.toString().toByteArray()).await() }
                prefs.edit().putInt("pending", 0).apply()
            }
            // Her antrenman ayrı gönderilir; başarılı olan kuyruktan çıkar. Telefon aynı id'yi iki kez kaydetmez.
            for (json in workouts) {
                nodes.forEach { client.sendMessage(it.id, WORKOUT_PATH, json.toByteArray()).await() }
                prefs.edit().putStringSet("workouts", prefs.getStringSet("workouts", emptySet()).orEmpty() - json).apply()
            }
        }
    }

    /** Sesle soru veya yemek kaydı. Telefon uygulaması açık olmalıdır; yanıt [WatchEvents.replies] ile gelir. */
    suspend fun sendAsk(context: Context, mode: String, text: String): String? = runCatching {
        val nodes = Wearable.getNodeClient(context).connectedNodes.await()
        if (nodes.isEmpty()) return null
        val id = UUID.randomUUID().toString()
        val payload = JSONObject().put("id", id).put("mode", mode).put("text", text).toString().toByteArray()
        nodes.forEach { Wearable.getMessageClient(context).sendMessage(it.id, ASK_PATH, payload).await() }
        id
    }.getOrNull()

    suspend fun isConnected(context: Context): Boolean = runCatching { Wearable.getNodeClient(context).connectedNodes.await().isNotEmpty() }.getOrDefault(false)

    fun publish(context: Context, value: Snapshot) {
        val old = SnapshotStore.snapshot.value
        SnapshotStore.save(context, value)
        notifyRankChange(context, old.social.rank, value)
        TileService.getUpdater(context).requestUpdate(SummaryTileService::class.java)
        listOf(StepsComplicationService::class.java, WaterComplicationService::class.java, StreakComplicationService::class.java).forEach {
            ComplicationDataSourceUpdateRequester.create(context, ComponentName(context, it)).requestUpdateAll()
        }
    }

    /** Haftalık sıralamada yükselince saatte kısa bildirim gösterir. */
    private fun notifyRankChange(context: Context, oldRank: Int, value: Snapshot) {
        val newRank = value.social.rank
        if (oldRank <= 0 || newRank <= 0 || newRank >= oldRank) return
        runCatching {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel("social", if (value.isEnglish) "Social" else "Sosyal", NotificationManager.IMPORTANCE_DEFAULT))
            val text = if (value.isEnglish) "You moved up to #$newRank this week" else "Bu hafta $newRank. sıraya yükseldin"
            nm.notify(7, NotificationCompat.Builder(context, "social").setSmallIcon(R.drawable.ic_stat_workout).setContentTitle("Hedefit").setContentText(text).setAutoCancel(true).build())
        }
    }
}

/** Telefonun gönderdiği özeti, yanıtları ve rota ipuçlarını karşılar (uygulama kapalıyken de). */
class WatchListener : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        events.filter { it.type == DataEvent.TYPE_CHANGED && it.dataItem.uri.path == SNAPSHOT_PATH }.forEach {
            PhoneLink.publish(applicationContext, Snapshot.from(DataMapItem.fromDataItem(it.dataItem).dataMap))
        }
    }

    override fun onMessageReceived(event: MessageEvent) {
        val body = String(event.data, Charsets.UTF_8)
        runCatching {
            when (event.path) {
                REPLY_PATH -> JSONObject(body).let { WatchEvents.emit(Reply(it.getString("id"), it.getBoolean("ok"), it.getString("text"))) }
                NAV_PATH -> {
                    val o = JSONObject(body)
                    val cue = NavCue(o.getString("type"), o.optInt("distanceM"))
                    WatchEvents.emit(cue)
                    vibrate(cue.type)
                }
            }
        }
    }

    /** Sol: iki kısa, sağ: bir uzun, varış: üç darbe. Düz gitmek için titreşim yok. */
    private fun vibrate(type: String) {
        val pattern = when {
            type.contains("LEFT") -> longArrayOf(0, 120, 90, 120)
            type.contains("RIGHT") -> longArrayOf(0, 450)
            type == "ARRIVE" -> longArrayOf(0, 100, 60, 100, 60, 300)
            else -> return
        }
        getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }
}
