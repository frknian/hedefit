package com.hedefit.app.challenges

import com.hedefit.app.data.model.ChallengeDayLog
import com.hedefit.app.data.model.ChallengeHubData
import com.hedefit.app.data.model.ChallengePlanData
import com.hedefit.app.data.model.ChallengeRulesData
import com.hedefit.app.data.model.ChallengeTaskData
import com.hedefit.app.data.model.L10nText
import com.hedefit.app.data.model.UserChallengeData
import com.hedefit.app.data.model.challengeState
import com.hedefit.app.data.model.parseChallengeHub
import com.hedefit.app.data.model.recoveryAllowance
import com.hedefit.app.data.model.toJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** lib/challenges/engine.ts ile aynı kurallar (tests/challenges.test.mjs ile aynı senaryolar). */
class ChallengeStateTest {
    private val logs = listOf(
        ChallengeDayLog(0, "2026-10-01", "completed", 10),
        ChallengeDayLog(1, "2026-10-02", "adapted", 8),
        ChallengeDayLog(2, "2026-10-03", "recovery", null),
    )

    @Test fun `progress follows completed days and recovery keeps the streak`() {
        val state = challengeState(14, logs, LocalDate.parse("2026-10-04"))
        assertEquals(4, state.currentDay)
        assertEquals(3, state.streak)
        assertFalse(state.todayDone)
        assertEquals(1, state.recoveryUsed)
        assertEquals(2, state.recoveryAllowance)
    }

    @Test fun `missing a day resets only the streak, never progress`() {
        val state = challengeState(14, logs, LocalDate.parse("2026-10-06"))
        assertEquals(0, state.streak)
        assertEquals(3, state.longestStreak)
        assertEquals(4, state.currentDay)
    }

    @Test fun `finished challenge has no next task`() {
        val challenge = userChallenge(days = 3, logs = logs)
        val today = LocalDate.parse("2026-10-03")
        assertTrue(challenge.state(today).finished)
        assertTrue(challenge.state(today).todayDone)
        assertNull(challenge.nextTask(today))
        assertEquals(100, challenge.state(today).percent)
        assertEquals(1, recoveryAllowance(7))
        assertEquals(4, recoveryAllowance(30))
    }

    @Test fun `home shows the active challenge whose task is still open`() {
        val today = LocalDate.parse("2026-10-04")
        val doneToday = userChallenge("a", 14, listOf(ChallengeDayLog(0, "2026-10-04", "completed", 10)))
        val open = userChallenge("b", 14, emptyList())
        val hub = ChallengeHubData(ChallengeRulesData(), emptyList(), listOf(doneToday, open))
        assertEquals("b", hub.primaryActive(today)?.id)
    }

    @Test fun `offline cache round-trips the hub`() {
        val hub = ChallengeHubData(ChallengeRulesData(dayXp = 30), emptyList(), listOf(userChallenge("x", 7, logs)))
        val restored = parseChallengeHub(hub.toJson())
        assertEquals(30, restored.rules.dayXp)
        assertEquals(hub.challenges.single().days, restored.challenges.single().days)
        assertEquals(hub.challenges.single().plan.title, restored.challenges.single().plan.title)
    }

    private fun userChallenge(id: String = "c", days: Int, logs: List<ChallengeDayLog>) = UserChallengeData(
        id = id, templateKey = "core_14",
        plan = ChallengePlanData("core_14", "catalog", "workout", "beginner", "none", L10nText("14 Gün Core", "14-Day Core"), L10nText("a", "b"), List(days) { ChallengeTaskData("session", "core_focus", 10) }),
        status = "active", socialChallengeId = null, startedOn = "2026-10-01", completedAt = null, days = logs,
    )
}
