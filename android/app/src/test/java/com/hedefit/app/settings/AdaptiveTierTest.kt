package com.hedefit.app.settings

import com.hedefit.app.ui.state.TIER_LIMITS
import com.hedefit.app.ui.state.Tier
import com.hedefit.app.ui.state.canUseAdaptiveAction
import com.hedefit.app.ui.state.canUseModalityExercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveTierTest {
    private val free = TIER_LIMITS.getValue(Tier.Free)
    private val plus = TIER_LIMITS.getValue(Tier.Plus)
    private val premium = TIER_LIMITS.getValue(Tier.Premium)

    @Test fun adaptiveActionsGrowWithTier() {
        assertEquals(setOf("shorten", "reduce_intensity"), free.adaptiveActions)
        assertTrue(plus.adaptiveActions.containsAll(free.adaptiveActions) && plus.adaptiveActions.size > free.adaptiveActions.size)
        assertTrue(premium.adaptiveActions.containsAll(plus.adaptiveActions) && premium.adaptiveActions.size > plus.adaptiveActions.size)
        assertFalse(plus.canUseAdaptiveAction("switch_pilates"))
        assertTrue(plus.canUseAdaptiveAction("switch_recovery"))
        assertTrue(premium.canUseAdaptiveAction("switch_pilates"))
    }

    @Test fun cycleTrackingIsFreeButAdaptationAndCoachAreSplit() {
        assertFalse(free.cycleAdaptation); assertTrue(plus.cycleAdaptation); assertTrue(premium.cycleAdaptation)
        assertFalse(plus.coachCycleAware); assertTrue(premium.coachCycleAware)
        assertFalse(plus.aiAdaptiveCoach); assertTrue(premium.aiAdaptiveCoach)
    }

    @Test fun historyAndNutritionExpandByTier() {
        assertEquals(7, TIER_LIMITS.getValue(Tier.Guest).checkinHistoryDays)
        assertEquals(14, free.checkinHistoryDays)
        assertEquals(90, plus.checkinHistoryDays)
        assertEquals(Int.MAX_VALUE, premium.checkinHistoryDays)
        assertEquals(listOf("basic", "training_load", "full"), listOf(free, plus, premium).map { it.nutritionPersonalization })
        assertTrue(premium.checkinTrends); assertFalse(plus.checkinTrends)
    }

    @Test fun modalityContentIsGatedLikeTheServer() {
        assertTrue(free.canUseModalityExercise(emptyList(), emptyList()))
        assertTrue(free.canUseModalityExercise(listOf("pilates"), listOf("beginner", "core")))
        assertTrue(free.canUseModalityExercise(listOf("mobility"), listOf("morning")))
        assertFalse(free.canUseModalityExercise(listOf("pilates"), listOf("lower_body")))
        assertFalse(free.canUseModalityExercise(listOf("barre"), listOf("beginner")))
        assertTrue(plus.canUseModalityExercise(listOf("barre"), listOf("lower_body")))
        assertTrue(premium.canUseModalityExercise(listOf("pilates"), listOf("lower_body")))
    }
}
