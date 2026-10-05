package com.hedefit.app.data

import com.hedefit.app.data.model.WellnessKind
import com.hedefit.app.data.model.parseWellnessSession
import com.hedefit.app.data.model.wellnessProminent
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WellnessDataTest {
    @Test fun sessionParsesExercisesWithSafeDefaults() {
        val session = parseWellnessSession(JSONObject("""{"session":{"kind":"pilates_today","title":"Bugün için Pilates","subtitle":"x","estimatedMinutes":19,"exercises":[{"id":"wl-pilates-hundred","name":"Pilates Hundred","area":"Core","sets":2,"reps":"8–10 tekrar","restSeconds":25},{"id":"cat-cow","name":"Kedi-İnek"}]}}"""))
        assertEquals("Bugün için Pilates", session.title)
        assertEquals(19, session.estimatedMinutes)
        assertEquals(2, session.exercises.size)
        assertEquals("wl-pilates-hundred", session.exercises[0].id)
        assertEquals(25, session.exercises[0].restSeconds)
        assertEquals("Tüm Vücut", session.exercises[1].area)
        assertEquals(2, session.exercises[1].sets)
    }

    @Test fun kindsMapToTheServerWireValues() {
        assertEquals(WellnessKind.PostureMobility, WellnessKind.fromWire("posture_mobility"))
        assertNull(WellnessKind.fromWire("nope"))
    }

    @Test fun wellnessRowIsPromotedByPreferenceOrSelfReportedFemaleButNeverLockedToGender() {
        assertTrue(wellnessProminent("Kadın", ""))
        assertTrue(wellnessProminent("Erkek", "Ağırlık • Pilates"))
        assertTrue(wellnessProminent("Erkek", "Mobilite"))
        assertTrue(wellnessProminent("", "Toparlanma"))
        assertFalse(wellnessProminent("Erkek", "Ağırlık • HIIT"))
        assertFalse(wellnessProminent("Diğer", ""))
    }
}
