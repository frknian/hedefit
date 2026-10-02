package com.hedefit.app.gym

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PlanRotationTest {
    private fun d(iso: String) = LocalDate.parse(iso)
    private val weekly = PlanRotationPeriod.Weekly
    private val monthly = PlanRotationPeriod.Monthly

    @Test
    fun `block ids match the server (lib training rotation ts)`() {
        // Reference values computed by rotationBlockId() in lib/training/rotation.ts.
        assertEquals(0L, PlanRotation.blockId(weekly, d("1970-01-05")))
        assertEquals(0L, PlanRotation.blockId(weekly, d("1970-01-11")))
        assertEquals(2960L, PlanRotation.blockId(weekly, d("2026-10-04")))
        assertEquals(2961L, PlanRotation.blockId(weekly, d("2026-10-05")))
        assertEquals(2961L, PlanRotation.blockId(weekly, d("2026-10-11")))
        assertEquals(2962L, PlanRotation.blockId(weekly, d("2026-10-12")))
        assertEquals(2973L, PlanRotation.blockId(weekly, d("2027-01-01")))
        assertEquals(23640L, PlanRotation.blockId(monthly, d("1970-01-05")))
        assertEquals(24321L, PlanRotation.blockId(monthly, d("2026-10-31")))
        assertEquals(24323L, PlanRotation.blockId(monthly, d("2026-12-31")))
        assertEquals(24324L, PlanRotation.blockId(monthly, d("2027-01-01")))
    }

    @Test
    fun `weekly blocks start on Monday and monthly blocks on the 1st`() {
        assertEquals(d("2026-10-12"), PlanRotation.nextBlockStart(weekly, d("2026-10-07")))
        assertEquals(d("2026-10-12"), PlanRotation.nextBlockStart(weekly, d("2026-10-05")))
        assertEquals(d("2026-11-01"), PlanRotation.nextBlockStart(monthly, d("2026-10-07")))
        assertEquals(d("2027-01-01"), PlanRotation.nextBlockStart(monthly, d("2026-12-15")))
    }

    @Test
    fun `a plan is due only after its block has passed`() {
        assertFalse(PlanRotation.isDue(weekly, d("2026-10-05"), d("2026-10-11")))
        assertTrue(PlanRotation.isDue(weekly, d("2026-10-05"), d("2026-10-12")))
        assertFalse(PlanRotation.isDue(monthly, d("2026-10-05"), d("2026-10-31")))
        assertTrue(PlanRotation.isDue(monthly, d("2026-10-05"), d("2026-11-01")))
        // The same plan is stale under a weekly setting but not under a monthly one.
        assertTrue(PlanRotation.isDue(weekly, d("2026-10-05"), d("2026-10-20")))
        assertFalse(PlanRotation.isDue(monthly, d("2026-10-05"), d("2026-10-20")))
    }

    @Test
    fun `no generation marker never prompts`() {
        assertFalse(PlanRotation.isDue(weekly, null, d("2026-10-20")))
        assertFalse(PlanRotation.isDue(monthly, null, d("2026-10-20")))
    }

    @Test
    fun `period keys fall back to monthly`() {
        assertEquals(weekly, PlanRotationPeriod.fromKey("weekly"))
        assertEquals(monthly, PlanRotationPeriod.fromKey("monthly"))
        for (bad in listOf(null, "", "daily", "WEEKLY")) assertEquals(monthly, PlanRotationPeriod.fromKey(bad))
        assertNotEquals(weekly.key, monthly.key)
    }
}
