package com.hedefit.app.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WearInboxTest {
    @Test fun parsesCardioWithDistanceAndCalories() {
        val w = WearInbox.parse("""{"id":"1","kind":"running","durationSec":1805,"distanceM":5400,"calories":410,"start":1700000000000}""")
        assertNotNull(w)
        assertEquals(30, w!!.minutes)
        assertEquals(5.4, w.distanceKm!!, 0.0001)
        assertEquals(410, w.calories)
    }

    @Test fun rejectsUnknownKindShortOrAbsurdWorkouts() {
        assertNull(WearInbox.parse("""{"id":"1","kind":"skydiving","durationSec":1800}"""))
        assertNull(WearInbox.parse("""{"id":"1","kind":"running","durationSec":30}"""))
        assertNull(WearInbox.parse("""{"id":"1","kind":"running","durationSec":9999999}"""))
        assertNull(WearInbox.parse("garbage"))
    }

    @Test fun parsesStrengthSetsAndDropsInvalidReps() {
        val w = WearInbox.parse("""{"id":"2","kind":"strength","durationSec":2400,"sets":[{"exId":"a","exName":"Bench","order":0,"setNo":1,"kg":60.0,"reps":8},{"exId":"a","exName":"Bench","order":0,"setNo":2,"kg":60.0,"reps":0}]}""")!!
        assertEquals(2, w.sets.size)
        assertEquals(8, w.sets[0].reps)
        assertNull(w.sets[1].reps)
        assertNull(w.distanceKm)
    }
}
