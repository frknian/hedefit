package com.hedefit.app.settings

import com.hedefit.app.data.model.BodyMeasurementData
import com.hedefit.app.ui.settings.WeightTrend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeightTrendTest {
    private val today = LocalDate.parse("2026-10-03")

    private fun m(date: String, kg: Double?) = BodyMeasurementData(date, kg, null, null, null, null, null)

    private fun daily(vararg kgs: Double) = kgs.mapIndexed { i, kg ->
        WeightTrend.Point(today.minusDays((kgs.size - 1 - i).toLong()), kg)
    }

    @Test
    fun `points skips entries without weight or with bad dates and sorts by date`() {
        val points = WeightTrend.points(listOf(m("2026-10-02T08:00:00Z", 80.0), m("2026-10-01", null), m("bozuk", 70.0), m("2026-09-30", 81.0)))
        assertEquals(listOf(LocalDate.parse("2026-09-30"), LocalDate.parse("2026-10-02")), points.map { it.date })
    }

    @Test
    fun `smooth averages the previous seven days`() {
        val smoothed = WeightTrend.smooth(daily(80.0, 82.0, 81.0))
        assertEquals(80.0, smoothed[0], 1e-9)
        assertEquals(81.0, smoothed[1], 1e-9)
        assertEquals(81.0, smoothed[2], 1e-9)
    }

    @Test
    fun `smooth ignores entries older than the window`() {
        val points = listOf(WeightTrend.Point(today.minusDays(20), 90.0), WeightTrend.Point(today, 80.0))
        assertEquals(80.0, WeightTrend.smooth(points).last(), 1e-9)
    }

    @Test
    fun `weekly rate is the regression slope per week`() {
        // Günde 0,1 kg düşüş = haftada 0,7 kg.
        val rate = WeightTrend.weeklyRateKg(daily(80.0, 79.9, 79.8, 79.7, 79.6, 79.5, 79.4, 79.3), today)
        assertNotNull(rate)
        assertEquals(-0.7, rate!!, 1e-6)
    }

    @Test
    fun `weekly rate needs enough span and entries`() {
        assertNull(WeightTrend.weeklyRateKg(daily(80.0), today))
        assertNull(WeightTrend.weeklyRateKg(daily(80.0, 79.5, 79.0), today))
    }

    @Test
    fun `weekly rate ignores entries outside the lookback window`() {
        val points = listOf(WeightTrend.Point(today.minusDays(90), 120.0)) + daily(80.0, 80.0, 80.0, 80.0, 80.0, 80.0, 80.0)
        assertEquals(0.0, WeightTrend.weeklyRateKg(points, today)!!, 1e-9)
    }

    @Test
    fun `bmi uses height in meters and rejects implausible input`() {
        assertEquals(24.69, WeightTrend.bmi(80.0, 180.0)!!, 0.01)
        assertNull(WeightTrend.bmi(null, 180.0))
        assertNull(WeightTrend.bmi(80.0, 50.0))
        assertNull(WeightTrend.bmi(10.0, 180.0))
    }

    @Test
    fun `bmi bands and adult check`() {
        assertEquals(WeightTrend.BmiBand.Low, WeightTrend.bmiBand(18.4))
        assertEquals(WeightTrend.BmiBand.Healthy, WeightTrend.bmiBand(18.5))
        assertEquals(WeightTrend.BmiBand.Healthy, WeightTrend.bmiBand(24.9))
        assertEquals(WeightTrend.BmiBand.High, WeightTrend.bmiBand(25.0))
        assertTrue(WeightTrend.bmiApplies(null))
        assertTrue(WeightTrend.bmiApplies(18))
        assertFalse(WeightTrend.bmiApplies(17))
    }

    @Test
    fun `goal progress works for loss and gain and clamps`() {
        assertEquals(0.5f, WeightTrend.goalProgress(90.0, 85.0, 80.0)!!, 1e-6f)
        assertEquals(0.5f, WeightTrend.goalProgress(60.0, 65.0, 70.0)!!, 1e-6f)
        assertEquals(0f, WeightTrend.goalProgress(90.0, 95.0, 80.0)!!, 1e-6f)
        assertEquals(1f, WeightTrend.goalProgress(90.0, 78.0, 80.0)!!, 1e-6f)
        assertNull(WeightTrend.goalProgress(80.0, 80.0, 80.0))
    }

    @Test
    fun `projected date only when moving toward the target`() {
        assertEquals(today.plusDays(35), WeightTrend.projectedDate(85.0, 80.0, -1.0, today))
        assertNull(WeightTrend.projectedDate(85.0, 80.0, 0.5, today))
        assertNull(WeightTrend.projectedDate(85.0, 80.0, -0.01, today))
        assertNull(WeightTrend.projectedDate(85.0, 80.0, null, today))
        assertNull(WeightTrend.projectedDate(80.0, 80.0, -1.0, today))
        assertNull(WeightTrend.projectedDate(120.0, 60.0, -0.1, today))
    }
}
