package com.hedefit.wear.exercise

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseConfig
import androidx.health.services.client.data.ExerciseLapSummary
import androidx.health.services.client.data.ExerciseState
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.ExerciseUpdate
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.hedefit.wear.MainActivity
import com.hedefit.wear.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

enum class WorkoutKind(val id: String, val title: String, val type: ExerciseType, val outdoor: Boolean) {
    RUN("running", "Koşu", ExerciseType.RUNNING, true),
    WALK("walking", "Yürüyüş", ExerciseType.WALKING, true),
    HIKE("hiking", "Doğa yürüyüşü", ExerciseType.HIKING, true),
    BIKE("cycling", "Bisiklet", ExerciseType.BIKING, true),
    STRENGTH("strength", "Ağırlık", ExerciseType.STRENGTH_TRAINING, false),
}

data class ExerciseUi(
    val active: Boolean = false,
    val paused: Boolean = false,
    val kind: WorkoutKind = WorkoutKind.RUN,
    val heartRate: Int = 0,
    val calories: Int = 0,
    val distanceM: Double = 0.0,
    val elapsedSeconds: Long = 0,
) {
    val paceSecondsPerKm: Int get() = if (distanceM > 50) (elapsedSeconds / (distanceM / 1000)).toInt() else 0
}

/** Health Services ExerciseClient'ı önplan servisi olarak çalıştırır; ekran kapansa da ölçüm sürer. */
class ExerciseService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val client by lazy { HealthServices.getClient(this).exerciseClient }
    private var checkpointTime: Instant = Instant.now()
    private var checkpointActive: Duration = Duration.ZERO
    private var ticking = false

    private val callback = object : ExerciseUpdateCallback {
        override fun onExerciseUpdateReceived(update: ExerciseUpdate) {
            val metrics = update.latestMetrics
            val paused = update.exerciseStateInfo.state.let { it == ExerciseState.USER_PAUSED || it == ExerciseState.AUTO_PAUSED }
            update.activeDurationCheckpoint?.let { checkpointTime = it.time; checkpointActive = it.activeDuration }
            ticking = update.exerciseStateInfo.state == ExerciseState.ACTIVE
            state.value = state.value.copy(
                active = !update.exerciseStateInfo.state.isEnded,
                paused = paused,
                heartRate = metrics.getData(DataType.HEART_RATE_BPM).lastOrNull()?.value?.toInt() ?: state.value.heartRate,
                calories = metrics.getData(DataType.CALORIES_TOTAL)?.total?.toInt() ?: state.value.calories,
                distanceM = metrics.getData(DataType.DISTANCE_TOTAL)?.total ?: state.value.distanceM,
            )
        }
        override fun onLapSummaryReceived(lapSummary: ExerciseLapSummary) {}
        override fun onRegistered() {}
        override fun onRegistrationFailed(throwable: Throwable) { stopSelf() }
        override fun onAvailabilityChanged(dataType: androidx.health.services.client.data.DataType<*, *>, availability: Availability) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start(WorkoutKind.entries.firstOrNull { it.id == intent.getStringExtra(EXTRA_KIND) } ?: WorkoutKind.RUN)
            ACTION_TOGGLE_PAUSE -> scope.launch { if (state.value.paused) client.resumeExerciseAsync() else client.pauseExerciseAsync() }
            ACTION_END -> end()
        }
        return START_NOT_STICKY
    }

    private fun start(kind: WorkoutKind) {
        startForeground(NOTIFICATION_ID, notification(kind))
        state.value = ExerciseUi(active = true, kind = kind)
        scope.launch {
            val supported = client.getCapabilitiesAsync().await().getExerciseTypeCapabilities(kind.type).supportedDataTypes
            val wanted = setOf(DataType.HEART_RATE_BPM, DataType.CALORIES_TOTAL, DataType.DISTANCE_TOTAL).filter { it in supported }.toSet()
            client.setUpdateCallback(callback)
            client.startExerciseAsync(
                ExerciseConfig.builder(kind.type).setDataTypes(wanted).setIsGpsEnabled(kind.outdoor).setIsAutoPauseAndResumeEnabled(false).build()
            ).await()
            tickElapsed()
        }
    }

    private fun tickElapsed() = scope.launch {
        while (state.value.active) {
            val extra = if (ticking) Duration.between(checkpointTime, Instant.now()) else Duration.ZERO
            state.value = state.value.copy(elapsedSeconds = (checkpointActive + extra).seconds)
            kotlinx.coroutines.delay(1000)
        }
    }

    private fun end() {
        scope.launch {
            val summary = state.value
            runCatching { client.endExerciseAsync().await() }
            state.value = state.value.copy(active = false)
            // Telefona gönder; hesaba yazılması telefon tarafında yapılır.
            com.hedefit.wear.data.PhoneLink.queueWorkout(applicationContext, summary.kind.id, summary.elapsedSeconds, summary.distanceM, summary.calories)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun notification(kind: WorkoutKind): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Antrenman", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_workout).setContentTitle("Hedefit").setContentText(kind.title)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT).setOngoing(true).setContentIntent(open)
        OngoingActivity.Builder(this, NOTIFICATION_ID, builder)
            .setStaticIcon(R.drawable.ic_stat_workout).setTouchIntent(open).setStatus(Status.Builder().addTemplate(kind.title).build())
            .build().apply(this)
        return builder.build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    companion object {
        private const val CHANNEL = "workout"
        private const val NOTIFICATION_ID = 42
        private const val ACTION_START = "start"
        private const val ACTION_TOGGLE_PAUSE = "pause"
        private const val ACTION_END = "end"
        private const val EXTRA_KIND = "kind"
        val state: MutableStateFlow<ExerciseUi> = MutableStateFlow(ExerciseUi())
        val ui: StateFlow<ExerciseUi> get() = state.asStateFlow()

        fun start(context: Context, kind: WorkoutKind) =
            context.startForegroundService(Intent(context, ExerciseService::class.java).setAction(ACTION_START).putExtra(EXTRA_KIND, kind.id))
        fun togglePause(context: Context) = context.startService(Intent(context, ExerciseService::class.java).setAction(ACTION_TOGGLE_PAUSE))
        fun end(context: Context) = context.startService(Intent(context, ExerciseService::class.java).setAction(ACTION_END))
    }
}
