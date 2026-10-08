package com.hedefit.app.data

import com.hedefit.app.data.model.CycleProfileData
import com.hedefit.app.data.model.cycleOptInInOnboarding
import com.hedefit.app.data.model.cycleSettingsVisible
import com.hedefit.app.data.model.parseCycleSnapshot
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CycleDataTest {
    @Test fun onboardingAsksOnlyWomen() {
        assertTrue(cycleOptInInOnboarding("Kadın"))
        assertTrue(cycleOptInInOnboarding(" female "))
        listOf("Erkek", "Male", "Diğer", "", "Belirtmek istemiyorum").forEach { assertFalse(it, cycleOptInInOnboarding(it)) }
    }

    @Test fun settingsEntryIsHiddenOnlyForMen() {
        listOf("Erkek", "male", " MALE ").forEach { assertFalse(it, cycleSettingsVisible(it)) }
        listOf("Kadın", "Female", "Diğer", "", "Belirtmek istemiyorum").forEach { assertTrue(it, cycleSettingsVisible(it)) }
    }

    @Test fun profileSerializesWithNullsAndLocalDate() {
        val json = CycleProfileData(trackingEnabled = false).toJson(LocalDate.of(2026, 10, 5))
        assertEquals(false, json.getBoolean("trackingEnabled"))
        assertTrue(json.isNull("lastPeriodStart"))
        assertEquals("2026-10-05", json.getString("localDate"))
        val full = CycleProfileData(true, "2026-10-01", 28, 5, "regular").toJson(LocalDate.of(2026, 10, 5))
        assertEquals(28, full.getInt("cycleLengthDays"))
        assertEquals("regular", full.getString("regularity"))
    }

    @Test fun snapshotParsesStateAndTolerantOfNulls() {
        val snap = parseCycleSnapshot(JSONObject("""{"profile":{"trackingEnabled":true,"lastPeriodStart":"2026-10-01","cycleLengthDays":28,"periodLengthDays":5,"regularity":"regular"},"state":{"cycleDay":5,"phase":"menstrual","periodLikely":true,"nextPeriodStart":"2026-10-29","daysToNextPeriod":24,"stale":false}}"""))
        assertEquals("menstrual", snap.state?.phase)
        assertEquals(28, snap.profile.cycleLengthDays)
        val off = parseCycleSnapshot(JSONObject("""{"profile":{"trackingEnabled":false,"lastPeriodStart":null,"cycleLengthDays":null},"state":null}"""))
        assertNull(off.state)
        assertNull(off.profile.lastPeriodStart)
        assertNull(off.profile.cycleLengthDays)
        val irregular = parseCycleSnapshot(JSONObject("""{"profile":{"trackingEnabled":true},"state":{"cycleDay":3,"phase":null,"periodLikely":false,"nextPeriodStart":"2026-10-20","daysToNextPeriod":10,"stale":false}}"""))
        assertNull(irregular.state?.phase)
    }
}
