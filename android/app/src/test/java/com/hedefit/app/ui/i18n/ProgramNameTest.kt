package com.hedefit.app.ui.i18n

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramNameTest {
    @After fun reset() { AppLang.en = false }

    @Test fun quickWorkoutNameFollowsTheAppLanguage() {
        AppLang.en = true
        assertEquals("Quick Workout: Full body", localizedProgramName("Hızlı Antrenman: Tüm vücut"))
        assertEquals("Quick Workout: Chest & Back", localizedProgramName("Hızlı Antrenman: Göğüs & Sırt"))
        AppLang.en = false
        assertEquals("Hızlı Antrenman: Tüm Vücut", localizedProgramName("Quick Workout: Full body"))
        assertEquals("Hızlı Antrenman: Göğüs & Sırt", localizedProgramName("Quick Workout: Chest & Back"))
    }

    @Test fun userNamedProgramsStayAsTyped() {
        AppLang.en = true
        assertEquals("Hızlı Başlangıç", localizedProgramName("Hızlı Başlangıç"))
        assertFalse(isQuickWorkoutName("Hızlı Başlangıç"))
        assertTrue(isQuickWorkoutName("Quick Workout: Legs"))
    }
}
