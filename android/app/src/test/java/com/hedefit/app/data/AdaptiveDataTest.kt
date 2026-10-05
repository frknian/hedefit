package com.hedefit.app.data

import com.hedefit.app.data.model.parseAdaptiveResult
import com.hedefit.app.data.model.upgradeHint
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveDataTest {
    private val json = JSONObject("""{"result":{"adapted":true,"level":"low","score":40,"intensity":"recovery",
        "applied":[{"action":"switch_recovery","detailTr":"x","detailEn":"y"}],"lockedActions":["switch_pilates"],
        "exercises":[{"id":"wl-low-march-in-place","name":"Yerinde Yürüyüş","area":"Tüm vücut","sets":2,"reps":"40 sn","restSeconds":25}],
        "estimatedMinutes":18,"wellnessKind":"low_impact_recovery","explanationTr":"Bugün enerjin düşük.","explanationEn":"Today your energy is low.",
        "signals":{"checkinUsed":true,"reasons":["low_energy"],"cycle":"nudged","trainingLoad":false}}}""")

    @Test fun parsesTheEngineResult() {
        val r = parseAdaptiveResult(json)
        assertTrue(r.adapted); assertEquals("low", r.level); assertEquals(listOf("switch_recovery"), r.appliedActions)
        assertEquals(listOf("switch_pilates"), r.lockedActions); assertEquals("low_impact_recovery", r.wellnessKind)
        assertEquals(1, r.exercises.size); assertEquals("40 sn", r.exercises[0].reps); assertEquals(18, r.estimatedMinutes)
        assertEquals("nudged", r.cycleSignal)
    }

    @Test fun explanationFollowsTheAppLanguage() {
        val r = parseAdaptiveResult(json)
        assertEquals("Bugün enerjin düşük.", r.explanation(en = false)); assertEquals("Today your energy is low.", r.explanation(en = true))
    }

    @Test fun unadaptedResultIsTolerant() {
        val r = parseAdaptiveResult(JSONObject("""{"result":{"adapted":false,"level":"good","score":90,"intensity":"normal","applied":[],"lockedActions":[],"exercises":[],"wellnessKind":null,"signals":{"cycle":"none"}}}"""))
        assertFalse(r.adapted); assertNull(r.wellnessKind); assertTrue(r.exercises.isEmpty())
    }

    @Test fun upgradeHintPrefersPremiumOnlyActions() {
        assertEquals("switch_pilates", upgradeHint(listOf("add_mobility", "switch_pilates")))
        assertEquals("adaptive", upgradeHint(listOf("replace_exercises")))
        assertNull(upgradeHint(emptyList())); assertNull(upgradeHint(listOf("shorten")))
    }
}
