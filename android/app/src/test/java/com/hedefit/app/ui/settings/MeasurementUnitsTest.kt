package com.hedefit.app.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MeasurementUnitsTest {
    /**
     * Varsayılan (Steady) tempo kilo vermede haftada vücut ağırlığının %0,75'i (GoalPace.Steady, Frontiers
     * Endocrinol 2025: %0,5–1 aralığının ortası). Eski test %1/hafta varsayıyordu; hesap kanıta dayalı tempo
     * seçenekleriyle (Slow/Steady/Fast) değiştirildi.
     */
    @Test
    fun `weight loss duration uses a gradual but not excessively slow range`() {
        // 60 → 56: 4 kg / (60 * 0,0075 = 0,45 kg/hafta) = 8,9 → 9 hafta (en yakın haftaya yuvarlanır).
        assertEquals(9, estimatedGoalWeeks(60.0, 56.0))
        // 100 → 94: 6 kg / 0,75 kg/hafta = 8 hafta.
        assertEquals(8, estimatedGoalWeeks(100.0, 94.0))
    }

    @Test
    fun `pace options scale the duration`() {
        assertEquals(12, GoalScience.weeks(100.0, 94.0, GoalPace.Slow))
        assertEquals(8, GoalScience.weeks(100.0, 94.0, GoalPace.Steady))
        assertEquals(6, GoalScience.weeks(100.0, 94.0, GoalPace.Fast))
    }

    @Test
    fun `weight gain uses the slower gain rate`() {
        // 70 → 73: 3 kg / (70 * 0,00375 = 0,2625 kg/hafta) = 11,4 → 11 hafta.
        assertEquals(11, estimatedGoalWeeks(70.0, 73.0))
    }

    @Test
    fun `missing weights give no estimate`() {
        assertNull(estimatedGoalWeeks(null, 70.0))
        assertNull(estimatedGoalWeeks(70.0, null))
    }

    @Test
    fun `equal weights need no additional week`() {
        assertEquals(0, estimatedGoalWeeks(75.0, 75.0))
    }
}
