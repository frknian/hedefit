package com.hedefit.wear

import android.content.pm.ServiceInfo
import com.hedefit.wear.exercise.HEART_RATE_PERMISSION_API36
import com.hedefit.wear.exercise.WorkoutGrants
import com.hedefit.wear.exercise.canStartWorkout
import com.hedefit.wear.exercise.foregroundTypes
import com.hedefit.wear.exercise.gpsAllowed
import com.hedefit.wear.exercise.heartRatePermission
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutPermissionsTest {
    private val none = WorkoutGrants(activityRecognition = false, heartRate = false, location = false)

    @Test fun workoutCannotStartWithoutHealthPermission() {
        assertFalse(canStartWorkout(none))
        assertEquals(0, foregroundTypes(none, outdoor = false))
        // Yalnız konum izni "health" türü için yetmez; servis başlatılmamalı.
        assertEquals(0, foregroundTypes(none.copy(location = true), outdoor = true))
    }

    @Test fun eitherActivityRecognitionOrHeartRateIsEnough() {
        assertTrue(canStartWorkout(none.copy(activityRecognition = true)))
        assertTrue(canStartWorkout(none.copy(heartRate = true)))
    }

    @Test fun indoorWorkoutUsesHealthTypeOnly() {
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH, foregroundTypes(none.copy(activityRecognition = true, location = true), outdoor = false))
    }

    @Test fun outdoorWorkoutAddsLocationTypeOnlyWhenGranted() {
        val withLocation = foregroundTypes(none.copy(activityRecognition = true, location = true), outdoor = true)
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION, withLocation)
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH, foregroundTypes(none.copy(activityRecognition = true), outdoor = true))
    }

    @Test fun gpsOnlyWhenOutdoorAndLocationGranted() {
        assertTrue(gpsAllowed(none.copy(location = true), outdoor = true))
        assertFalse(gpsAllowed(none.copy(location = true), outdoor = false))
        assertFalse(gpsAllowed(none, outdoor = true))
    }

    @Test fun heartRatePermissionSwitchesAtApi36() {
        assertEquals("android.permission.BODY_SENSORS", heartRatePermission(35))
        assertEquals("android.permission.BODY_SENSORS", heartRatePermission(30))
        assertEquals(HEART_RATE_PERMISSION_API36, heartRatePermission(36))
        assertEquals("android.permission.health.READ_HEART_RATE", heartRatePermission(37))
    }
}
