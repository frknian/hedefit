package com.hedefit.app.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Sunucu (lib/goal-plan.ts planGoal) ve site (site/tools.js) ile aynı sonuçlar. Aynı değerler
 * tests/goal-plan.test.mjs içinde sunucu çıktısıyla doğrulanır; biri değişirse diğeri de değişmeli.
 */
class GoalScienceParityTest {
    private val vectors = listOf(
        // mevcut kg, hedef kg, tempo, beklenen hafta
        Vector(60.0, 56.0, GoalPace.Steady, 9),
        Vector(100.0, 94.0, GoalPace.Steady, 8),
        Vector(100.0, 94.0, GoalPace.Slow, 12),
        Vector(100.0, 94.0, GoalPace.Fast, 6),
        Vector(70.0, 73.0, GoalPace.Steady, 11),
        Vector(50.0, 42.0, GoalPace.Steady, 21),
        Vector(50.0, 45.0, GoalPace.Steady, 13),
        Vector(80.0, 75.0, GoalPace.Steady, 8),
        Vector(50.0, 49.6, GoalPace.Steady, 0),
        Vector(75.0, 75.0, GoalPace.Steady, 0),
        Vector(90.0, 70.0, GoalPace.Fast, 22),
        Vector(250.0, 35.0, GoalPace.Slow, 172),
    )

    @Test fun matchesServerAndSite() {
        vectors.forEach { v ->
            assertEquals("${v.current}→${v.target} ${v.pace}", v.weeks, GoalScience.weeks(v.current, v.target, v.pace))
        }
    }

    private data class Vector(val current: Double, val target: Double, val pace: GoalPace, val weeks: Int)
}
