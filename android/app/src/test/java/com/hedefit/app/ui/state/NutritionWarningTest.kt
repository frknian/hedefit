package com.hedefit.app.ui.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionWarningTest {
    private val codes = listOf("approximate_amount", "cooked_assumed", "not_in_catalogue", "ai_estimate", "low_confidence")

    @Test
    fun `every known code has a Turkish and an English message`() {
        for (code in codes) {
            val tr = NutritionWarning.text(code, null, 0.9, en = false)
            val en = NutritionWarning.text(code, null, 0.9, en = true)
            assertTrue("$code tr", !tr.isNullOrBlank())
            assertTrue("$code en", !en.isNullOrBlank())
            assertNotEquals("$code should be localised", tr, en)
        }
    }

    @Test
    fun `an unknown code falls back to the server sentence`() {
        assertEquals("Sunucu cümlesi", NutritionWarning.text("brand_new_code", "Sunucu cümlesi", 0.9, en = true))
        assertEquals("Sunucu cümlesi", NutritionWarning.text(null, "Sunucu cümlesi", 0.9, en = false))
    }

    @Test
    fun `a known code wins over the server sentence so the app language is respected`() {
        val english = NutritionWarning.text("cooked_assumed", "Pişmiş ağırlık varsayıldı.", 0.7, en = true)
        assertTrue(english!!.startsWith("Cooked weight assumed"))
    }

    @Test
    fun `an item without a warning is flagged only when confidence is low`() {
        assertNull(NutritionWarning.text(null, null, 0.85, en = false))
        assertNull(NutritionWarning.text(null, "  ", 0.6, en = false))
        assertEquals(NutritionWarning.text("low_confidence", null, 0.0, en = false), NutritionWarning.text(null, null, 0.59, en = false))
    }
}
