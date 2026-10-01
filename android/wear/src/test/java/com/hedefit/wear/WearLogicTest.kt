package com.hedefit.wear

import com.hedefit.wear.data.SetLog
import com.hedefit.wear.data.Snapshot
import com.hedefit.wear.data.WorkoutPayload
import com.hedefit.wear.exercise.ExerciseUi
import com.hedefit.wear.ui.clock
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WearLogicTest {
    @Test fun clockFormatsMinutesAndHours() {
        assertEquals("0:05", clock(5))
        assertEquals("12:34", clock(754))
        assertEquals("1:01:01", clock(3661))
        assertEquals("0:00", clock(-4))
    }

    @Test fun paceIsZeroUntilEnoughDistance() {
        assertEquals(0, ExerciseUi(distanceM = 20.0, elapsedSeconds = 30).paceSecondsPerKm)
        assertEquals(300, ExerciseUi(distanceM = 2000.0, elapsedSeconds = 600).paceSecondsPerKm)
    }

    @Test fun fractionsAreClampedAndSafeWithoutGoal() {
        val s = Snapshot(steps = 12_000, stepGoal = 8_000, waterMl = 1_000, waterGoal = 2_000, proteinG = 5, proteinGoal = 0)
        assertEquals(1f, s.stepFraction(), 0f)
        assertEquals(0.5f, s.waterFraction(), 0f)
        assertEquals(0f, s.proteinFraction(), 0f)
    }

    @Test fun exercisePlanParsingReadsFirstRepNumberAndClampsValues() {
        val list = Snapshot.parseExercises("""[{"id":"a","name":"Bench","sets":4,"reps":"8-12","rest":120,"kg":60.0},{"id":"b","name":"Row","sets":0,"reps":"","rest":1}]""")
        assertEquals(2, list.size)
        assertEquals(8, list[0].reps)
        assertEquals(60.0, list[0].kg!!, 0.0)
        assertEquals(1, list[1].sets)
        assertEquals(10, list[1].reps)
        assertEquals(10, list[1].restSeconds)
        assertTrue(Snapshot.parseExercises("not json").isEmpty())
    }

    @Test fun socialParsingHandlesMissingFields() {
        val social = Snapshot.parseSocial("""{"rank":2,"total":5,"xp":340,"leaders":[{"n":"Ali","xp":400,"me":false}]}""")
        assertEquals(2, social.rank)
        assertEquals("Ali", social.leaders.single().name)
        assertEquals("", social.challenge)
    }

    @Test fun workoutPayloadCarriesSets() {
        val json = WorkoutPayload.build("strength", 1800, 0.0, 210, 1_700_000_000_000, listOf(SetLog("ex1", "Bench", 0, 1, 60.0, 8)), id = "fixed")
        val o = JSONObject(json)
        assertEquals("fixed", o.getString("id"))
        assertEquals("strength", o.getString("kind"))
        val set = o.getJSONArray("sets").getJSONObject(0)
        assertEquals("ex1", set.getString("exId"))
        assertEquals(60.0, set.getDouble("kg"), 0.0)
        assertEquals(8, set.getInt("reps"))
    }
}
