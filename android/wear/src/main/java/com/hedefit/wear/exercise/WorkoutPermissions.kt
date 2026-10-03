package com.hedefit.wear.exercise

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Antrenman önplan servisinin izin kuralları.
 *
 * Android 14+ ("health"/"location" türünde) önplan servisi, ilgili çalışma-zamanı izni VERİLMEDEN
 * başlatılırsa SecurityException fırlatır; startForegroundService'ten sonra startForeground çağrılamazsa
 * da uygulama çöker. Bu yüzden servis, izinler yoksa HİÇ başlatılmaz ve yalnız verilen izinlerin
 * türleriyle önplana alınır.
 *
 * Android 16 / Wear OS 6'da (API 36) nabız için BODY_SENSORS yerine
 * android.permission.health.READ_HEART_RATE kullanılır.
 */
const val HEART_RATE_PERMISSION_API36 = "android.permission.health.READ_HEART_RATE"
private const val API_36 = 36

/** Nabız izni: API 36+ sağlık izni, öncesinde BODY_SENSORS. */
fun heartRatePermission(sdkInt: Int = Build.VERSION.SDK_INT): String =
    if (sdkInt >= API_36) HEART_RATE_PERMISSION_API36 else Manifest.permission.BODY_SENSORS

data class WorkoutGrants(val activityRecognition: Boolean, val heartRate: Boolean, val location: Boolean)

/** "health" önplan türü için ACTIVITY_RECOGNITION veya nabız izninden biri yeterlidir. */
fun canStartWorkout(grants: WorkoutGrants): Boolean = grants.activityRecognition || grants.heartRate

/** Verilen izinlere uyan önplan servis türleri; hiçbiri uygun değilse 0 (servis başlatılmamalı). */
fun foregroundTypes(grants: WorkoutGrants, outdoor: Boolean): Int {
    if (!canStartWorkout(grants)) return 0
    var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
    if (outdoor && grants.location) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
    return types
}

/** GPS yalnız dış mekân antrenmanında ve konum izni varken açılır (izinsiz GPS isteği hata verir). */
fun gpsAllowed(grants: WorkoutGrants, outdoor: Boolean): Boolean = outdoor && grants.location

fun currentGrants(context: Context): WorkoutGrants {
    fun has(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    return WorkoutGrants(
        activityRecognition = has(Manifest.permission.ACTIVITY_RECOGNITION),
        heartRate = has(heartRatePermission()),
        location = has(Manifest.permission.ACCESS_FINE_LOCATION) || has(Manifest.permission.ACCESS_COARSE_LOCATION),
    )
}
