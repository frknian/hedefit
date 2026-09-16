package com.hedefit.app.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.hedefit.app.MainActivity
import com.hedefit.app.R

interface RestCompletionNotifier {
    fun schedule(deadlineEpochMs: Long)
    fun cancel()
}

class AndroidRestCompletionNotifier(private val context: Context) : RestCompletionNotifier {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val alarmIntent: PendingIntent get() = PendingIntent.getBroadcast(
        context, 8201, Intent(context, RestTimerReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    override fun schedule(deadlineEpochMs: Long) {
        if (deadlineEpochMs <= System.currentTimeMillis()) return
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, deadlineEpochMs, alarmIntent)
    }

    override fun cancel() { alarmManager.cancel(alarmIntent) }
}

class RestTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Antrenman dinlenmesi", NotificationManager.IMPORTANCE_HIGH))
        val openWorkout = PendingIntent.getActivity(
            context, 8202, Intent(context, MainActivity::class.java).putExtra("open_workout", true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_running)
            .setContentTitle("Dinlenme tamamlandı")
            .setContentText("Sıradaki sete hazırsın.")
            .setContentIntent(openWorkout)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private companion object {
        const val CHANNEL_ID = "workout_rest"
        const val NOTIFICATION_ID = 8203
    }
}
