package com.hedefit.app.notifications

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.hedefit.app.steps.DeviceStepCounterDataSource

/**
 * Adım bildirimini uygulama kapalıyken de güncel tutar. Kalıcı adım bildirimini ön plan
 * bildirimi olarak kullanır ve cihaz adım sensörünü dinler; sayaç kaydı uygulamayla ortaktır
 * (hedefit-native-steps), bu yüzden iki taraf aynı sayıyı gösterir.
 */
class StepTrackingService : Service() {
    private var source: DeviceStepCounterDataSource? = null
    private var lastShown = -1
    private var lastAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!StepCounterNotification.isEnabled(this) || !canTrack(this)) { stopSelf(); return START_NOT_STICKY }
        val goal = StepCounterNotification.savedGoal(this)
        val initial = StepCounterNotification.build(this, 0, goal)
        runCatching {
            if (Build.VERSION.SDK_INT >= 34) startForeground(StepCounterNotification.FOREGROUND_ID, initial, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
            else startForeground(StepCounterNotification.FOREGROUND_ID, initial)
        }.onFailure { stopSelf(); return START_NOT_STICKY }
        if (source == null) {
            source = DeviceStepCounterDataSource(this) { reading -> publish(reading.count) }.also { it.start() }
        }
        return START_STICKY
    }

    private fun publish(steps: Int) {
        val now = android.os.SystemClock.elapsedRealtime()
        // Her 20 adımda ya da 30 sn'de bir güncelle: bildirim gürültüsü ve pil tüketimi düşük kalır.
        if (lastShown >= 0 && kotlin.math.abs(steps - lastShown) < 20 && now - lastAt < 30_000L) return
        StepCounterNotification.ensureCurrentDay(this)
        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.notify(StepCounterNotification.FOREGROUND_ID, StepCounterNotification.build(this, steps, StepCounterNotification.savedGoal(this)))
        lastShown = steps
        lastAt = now
    }

    override fun onDestroy() {
        source?.stop()
        source = null
        super.onDestroy()
    }

    companion object {
        private fun canTrack(context: Context): Boolean {
            val notificationsOk = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            val activityOk = Build.VERSION.SDK_INT < 29 || ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
            return notificationsOk && activityOk
        }

        fun start(context: Context) {
            if (!StepCounterNotification.isEnabled(context) || !canTrack(context)) return
            runCatching { ContextCompat.startForegroundService(context, Intent(context, StepTrackingService::class.java)) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, StepTrackingService::class.java))
        }
    }
}
