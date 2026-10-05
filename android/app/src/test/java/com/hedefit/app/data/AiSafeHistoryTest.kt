package com.hedefit.app.data

import com.hedefit.app.data.repository.aiSafeHistory
import org.junit.Assert.assertEquals
import org.junit.Test

class AiSafeHistoryTest {
    private val answers = List(23) { "cevap-$it" }

    @Test fun healthAllergyHabitsStressAndSatisfactionAreBlanked() {
        val safe = aiSafeHistory(answers)
        listOf(16, 17, 19, 20, 21).forEach { assertEquals("slot $it", "", safe[it]) }
    }

    @Test fun otherSlotsKeepTheirIndexAndValue() {
        val safe = aiSafeHistory(answers)
        assertEquals(23, safe.size)
        listOf(0, 4, 11, 15, 18, 22).forEach { assertEquals(answers[it], safe[it]) }
    }

    @Test fun legacyShortHistoriesStillWork() {
        assertEquals(listOf("a", "b"), aiSafeHistory(listOf("a", "b")))
    }
}
