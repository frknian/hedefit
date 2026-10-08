package com.hedefit.app.data

import com.hedefit.app.data.model.CheckinChoices
import com.hedefit.app.data.model.CheckinData
import com.hedefit.app.data.model.parseCheckin
import com.hedefit.app.data.model.parseCheckinSave
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CheckinDataTest {
    @Test fun readinessMappingMatchesTheServerFormula() {
        // sunucu: fatigue = round((11-energy)*0.6 + soreness*0.4), hasSoreness = soreness >= 3, discomfort = max(1, pain)
        val bad = CheckinData("2026-10-05", energy = 2, sleepQuality = 3, sleepHours = 4.5, soreness = 6, pain = 9).toReadinessInput()
        assertEquals(8, bad.fatigue); assertTrue(bad.hasSoreness); assertEquals(9, bad.discomfortLevel)
        val good = CheckinData("2026-10-05", energy = 10, sleepQuality = 8, soreness = 0, pain = 0).toReadinessInput()
        assertEquals(1, good.fatigue); assertFalse(good.hasSoreness); assertEquals(1, good.discomfortLevel)
        assertTrue(good.sorenessAreas.isEmpty())
    }

    @Test fun jsonCarriesNullsAndLocalDate() {
        val json = CheckinData("2026-10-05", 6, 5).toJson(LocalDate.of(2026, 10, 5))
        assertTrue(json.isNull("sleepHours")); assertTrue(json.isNull("availableMinutes"))
        assertEquals("2026-10-05", json.getString("localDate"))
        assertEquals(0, json.getInt("pain"))
    }

    @Test fun parsingIsTolerant() {
        assertNull(parseCheckin(null)); assertNull(parseCheckin(JSONObject("""{"today":null}""")))
        val c = parseCheckin(JSONObject("""{"day":"2026-10-05","energy":7,"sleepQuality":6,"sleepHours":null,"soreness":2,"pain":0,"availableMinutes":30}"""))!!
        assertEquals(7, c.energy); assertNull(c.sleepHours); assertEquals(30, c.availableMinutes)
        val saved = parseCheckinSave(JSONObject("""{"checkin":{"day":"2026-10-05","energy":7,"sleepQuality":6},"cycle":{"cycleDay":5,"phase":"menstrual","periodLikely":true,"nextPeriodStart":"2026-10-29","daysToNextPeriod":24,"stale":false}}"""))
        assertEquals(5, saved.cycle?.cycleDay)
        assertNull(parseCheckinSave(JSONObject("""{"checkin":{"day":"d","energy":5,"sleepQuality":5},"cycle":null}""")).cycle)
    }

    @Test fun chipValuesStayInsideTheServerRanges() {
        assertTrue(CheckinChoices.energy.all { it in 1..10 })
        assertTrue(CheckinChoices.sleep.all { it.second in 1..10 && it.first in 0.0..24.0 })
        assertTrue((CheckinChoices.soreness + CheckinChoices.pain).all { it in 0..10 })
        assertTrue(CheckinChoices.minutes.all { it in 5..240 })
    }
}
