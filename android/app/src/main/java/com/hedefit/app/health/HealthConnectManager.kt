package com.hedefit.app.health

import android.content.Context
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class HealthSnapshot(
    val steps: Int,
    val activeCalories: Int,
    val sleepMinutes: Int,
    val weightKg: Double?,
    val date: LocalDate,
)

class HealthConnectManager(private val context: Context) {
    val stepPermission = HealthPermission.getReadPermission(StepsRecord::class)

    val permissions = setOf(
        stepPermission,
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
    )

    val heartPermissions = setOf(
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
    )

    val allPermissions = permissions + heartPermissions

    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    fun sdkStatus(): Int = HealthConnectClient.getSdkStatus(context)
    fun permissionContract(): ActivityResultContract<Set<String>, Set<String>> = PermissionController.createRequestPermissionResultContract()

    suspend fun hasPermissions(): Boolean = sdkStatus() == HealthConnectClient.SDK_AVAILABLE && client.permissionController.getGrantedPermissions().containsAll(permissions)

    suspend fun hasStepPermission(): Boolean =
        sdkStatus() == HealthConnectClient.SDK_AVAILABLE &&
            client.permissionController.getGrantedPermissions().contains(stepPermission)

    suspend fun readTodaySteps(): Int {
        check(sdkStatus() == HealthConnectClient.SDK_AVAILABLE) { "Health Connect bu cihazda kullanılamıyor." }
        check(hasStepPermission()) { "Health Connect adım izni verilmedi." }
        val zone = ZoneId.systemDefault()
        val start = LocalDate.now(zone).atStartOfDay(zone).toInstant()
        return aggregatePreferredSteps(start, Instant.now())
    }

    suspend fun readToday(): HealthSnapshot {
        check(sdkStatus() == HealthConnectClient.SDK_AVAILABLE) { "Health Connect bu cihazda kullanılamıyor." }
        check(hasPermissions()) { "Health Connect izinleri verilmedi." }
        val zone = ZoneId.systemDefault()
        val date = LocalDate.now(zone)
        val start = date.atStartOfDay(zone).toInstant()
        val end = Instant.now()
        val activeCalories = client.aggregate(
            AggregateRequest(
                metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(start, end),
            ),
        )
        val steps = aggregatePreferredSteps(start, end)
        val sleepStart = date.minusDays(1).atTime(12, 0).atZone(zone).toInstant()
        val sleeps = client.readRecords(
            ReadRecordsRequest(SleepSessionRecord::class, TimeRangeFilter.between(sleepStart, end), ascendingOrder = false, pageSize = 20),
        ).records
        val weights = client.readRecords(
            ReadRecordsRequest(WeightRecord::class, TimeRangeFilter.before(end), ascendingOrder = false, pageSize = 1),
        ).records
        val sleepMinutes = sleeps.sumOf { java.time.Duration.between(it.startTime, it.endTime).toMinutes() }.toInt().coerceIn(0, 1_440)
        return HealthSnapshot(
            steps = steps,
            activeCalories = (activeCalories[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0).toInt().coerceAtLeast(0),
            sleepMinutes = sleepMinutes,
            weightKg = weights.firstOrNull()?.weight?.inKilograms,
            date = date,
        )
    }

    /** Which apps and physical devices wrote health data in the last [days] days. */
    suspend fun readWearableSources(days: Long = 7): WearableSnapshot {
        check(sdkStatus() == HealthConnectClient.SDK_AVAILABLE) { "Health Connect bu cihazda kullanılamıyor." }
        val granted = client.permissionController.getGrantedPermissions()
        val end = Instant.now()
        val range = TimeRangeFilter.between(end.minus(java.time.Duration.ofDays(days)), end)
        val origins = mutableMapOf<String, Instant>()
        val devices = mutableMapOf<String, WearableDevice>()
        fun note(packageName: String, time: Instant, device: Device?) {
            if (origins[packageName]?.isAfter(time) != true) origins[packageName] = time
            if (device != null && device.type in WEARABLE_TYPES) {
                val label = listOfNotNull(device.manufacturer, device.model).joinToString(" ").ifBlank { null } ?: return
                val previous = devices[label]
                if (previous == null || previous.lastSeen.isBefore(time)) devices[label] = WearableDevice(label, device.type, packageName, time)
            }
        }
        if (stepPermission in granted) client.readRecords(ReadRecordsRequest(StepsRecord::class, range, ascendingOrder = false, pageSize = 200)).records
            .forEach { note(it.metadata.dataOrigin.packageName, it.endTime, it.metadata.device) }
        if (HealthPermission.getReadPermission(SleepSessionRecord::class) in granted) client.readRecords(ReadRecordsRequest(SleepSessionRecord::class, range, ascendingOrder = false, pageSize = 50)).records
            .forEach { note(it.metadata.dataOrigin.packageName, it.endTime, it.metadata.device) }
        var latestHeartRate: Long? = null
        if (HealthPermission.getReadPermission(HeartRateRecord::class) in granted) {
            val beats = client.readRecords(ReadRecordsRequest(HeartRateRecord::class, range, ascendingOrder = false, pageSize = 200)).records
            beats.forEach { note(it.metadata.dataOrigin.packageName, it.endTime, it.metadata.device) }
            latestHeartRate = beats.firstOrNull()?.samples?.maxByOrNull { it.time }?.beatsPerMinute
        }
        val resting = if (HealthPermission.getReadPermission(RestingHeartRateRecord::class) in granted)
            client.readRecords(ReadRecordsRequest(RestingHeartRateRecord::class, range, ascendingOrder = false, pageSize = 1)).records.firstOrNull()?.beatsPerMinute
        else null
        return WearableSnapshot(
            sourceLastSeen = origins,
            devices = devices.values.sortedByDescending { it.lastSeen },
            latestHeartRate = latestHeartRate,
            restingHeartRate = resting,
            heartPermissionGranted = granted.containsAll(heartPermissions),
        )
    }

    private suspend fun aggregatePreferredSteps(start: Instant, end: Instant): Int {
        val timeRange = TimeRangeFilter.between(start, end)
        val samsungSteps = client.aggregate(
            AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = timeRange,
                dataOriginFilter = setOf(DataOrigin(SAMSUNG_HEALTH_PACKAGE)),
            ),
        )[StepsRecord.COUNT_TOTAL]
        val allSteps = if (samsungSteps == null) {
            client.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = timeRange,
                ),
            )[StepsRecord.COUNT_TOTAL]
        } else null
        return preferredHealthStepCount(samsungSteps, allSteps)
    }

    private companion object {
        const val SAMSUNG_HEALTH_PACKAGE = "com.sec.android.app.shealth"
        val WEARABLE_TYPES = setOf(Device.TYPE_WATCH, Device.TYPE_RING, Device.TYPE_FITNESS_BAND)
    }
}

data class WearableDevice(val label: String, val type: Int, val sourcePackage: String, val lastSeen: Instant)

data class WearableSnapshot(
    val sourceLastSeen: Map<String, Instant>,
    val devices: List<WearableDevice>,
    val latestHeartRate: Long?,
    val restingHeartRate: Long?,
    val heartPermissionGranted: Boolean,
)

internal fun preferredHealthStepCount(samsungSteps: Long?, allSteps: Long?): Int =
    (samsungSteps ?: allSteps ?: 0L).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
