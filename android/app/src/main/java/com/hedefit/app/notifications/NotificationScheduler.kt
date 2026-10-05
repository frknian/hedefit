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
import com.hedefit.app.gym.PlanRotation
import com.hedefit.app.gym.PlanRotationPeriod
import com.hedefit.app.ui.settings.AppPreferences
import com.hedefit.app.ui.settings.AppPreferencesStore
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar

private const val CHANNEL_ID = "hedefit_routines"
private const val NEW_BLOCK_REQUEST_CODE = 790
private const val NEW_BLOCK_NOTIFICATION_ID = 1202
private const val NEW_BLOCK_HOUR = 9
private const val WEIGH_IN_REQUEST_CODE = 791
private const val WEIGH_IN_NOTIFICATION_ID = 1203
private const val WEIGH_IN_HOUR = 9

class NotificationScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun apply(preferences: AppPreferences) {
        cancelAll()
        if (!preferences.notificationsEnabled) return
        createChannel(context)
        preferences.notificationDays.forEach { day ->
            val intent = Intent(context, RoutineNotificationReceiver::class.java).putExtra("day", day)
            val pending = PendingIntent.getBroadcast(
                context, 700 + day, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val first = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_WEEK, day)
                set(Calendar.HOUR_OF_DAY, preferences.notificationHour)
                set(Calendar.MINUTE, preferences.notificationMinute)
                set(Calendar.SECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) add(Calendar.WEEK_OF_YEAR, 1)
            }
            alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, first.timeInMillis, AlarmManager.INTERVAL_DAY * 7, pending)
        }
        scheduleNewBlock(preferences)
        scheduleWeighIn(preferences)
    }

    /**
     * One-shot reminder at 09:00 on the first day of the next training block
     * (Monday for weekly, the 1st for monthly). It re-arms itself when it fires and
     * whenever the app opens; it follows the same notifications switch as routines.
     */
    fun scheduleNewBlock(preferences: AppPreferences) {
        cancelNewBlock()
        if (!preferences.notificationsEnabled) return
        createChannel(context)
        val period = PlanRotationPeriod.fromKey(preferences.planRotation)
        val start = PlanRotation.nextBlockStart(period, LocalDate.now())
        val triggerAt = start.atTime(NEW_BLOCK_HOUR, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pending = PendingIntent.getBroadcast(
            context, NEW_BLOCK_REQUEST_CODE, Intent(context, NewBlockNotificationReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pending)
    }

    /**
     * One-shot weekly weigh-in reminder at 09:00 on the chosen weekday. Like the new-block
     * reminder it re-arms itself when it fires and whenever the app opens.
     */
    fun scheduleWeighIn(preferences: AppPreferences) {
        cancelWeighIn()
        if (!preferences.notificationsEnabled || !preferences.weighInReminderEnabled) return
        createChannel(context)
        val triggerAt = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, preferences.weighInReminderDay)
            set(Calendar.HOUR_OF_DAY, WEIGH_IN_HOUR)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.WEEK_OF_YEAR, 1)
        }.timeInMillis
        val pending = PendingIntent.getBroadcast(
            context, WEIGH_IN_REQUEST_CODE, Intent(context, WeighInNotificationReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pending)
    }

    private fun cancelWeighIn() {
        val pending = PendingIntent.getBroadcast(
            context, WEIGH_IN_REQUEST_CODE, Intent(context, WeighInNotificationReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (pending != null) alarmManager.cancel(pending)
    }

    private fun cancelNewBlock() {
        val pending = PendingIntent.getBroadcast(
            context, NEW_BLOCK_REQUEST_CODE, Intent(context, NewBlockNotificationReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (pending != null) alarmManager.cancel(pending)
    }

    fun cancelAll() {
        (Calendar.SUNDAY..Calendar.SATURDAY).forEach { day ->
            val pending = PendingIntent.getBroadcast(
                context, 700 + day, Intent(context, RoutineNotificationReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
            if (pending != null) alarmManager.cancel(pending)
        }
        cancelNewBlock()
        cancelWeighIn()
    }
}

class RoutineNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        createChannel(context)
        if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val openApp = PendingIntent.getActivity(
            context, 900, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Bugünün hedefi hazır")
            .setContentText("Kısa bir antrenman bile serini korur. Planına göz at.")
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(1201, notification)
    }
}

class NewBlockNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val preferences = AppPreferencesStore(context).read()
        // One-shot alarm: arm the following block first so a missing permission never ends the chain.
        NotificationScheduler(context).scheduleNewBlock(preferences)
        if (!preferences.notificationsEnabled) return
        createChannel(context)
        if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val en = preferences.language == "en"
        val weekly = PlanRotationPeriod.fromKey(preferences.planRotation) == PlanRotationPeriod.Weekly
        val openApp = PendingIntent.getActivity(
            context, 901, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(if (en) "A new training block is ready" else "Yeni antrenman bloğun hazır")
            .setContentText(
                if (en) "A new ${if (weekly) "week" else "month"} has started. Refresh your accessory exercises."
                else "Yeni ${if (weekly) "hafta" else "ay"} başladı. Yardımcı hareketlerini yenile.",
            )
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(NEW_BLOCK_NOTIFICATION_ID, notification)
    }
}

class WeighInNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val preferences = AppPreferencesStore(context).read()
        // One-shot alarm: arm next week first so a missing permission never ends the chain.
        NotificationScheduler(context).scheduleWeighIn(preferences)
        if (!preferences.notificationsEnabled || !preferences.weighInReminderEnabled) return
        createChannel(context)
        if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val en = preferences.language == "en"
        val openApp = PendingIntent.getActivity(
            context, 902, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(if (en) "Weekly weigh-in" else "Haftalık tartı zamanı")
            .setContentText(if (en) "Log your weight to keep your trend and goal estimate up to date." else "Kilonu kaydet; trendin ve hedef tahminin güncel kalsın.")
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(WEIGH_IN_NOTIFICATION_ID, notification)
    }
}

private fun createChannel(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Antrenman hatırlatmaları", NotificationManager.IMPORTANCE_DEFAULT))
}
