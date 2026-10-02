package com.hedefit.app.ui.state

import com.hedefit.app.data.model.NutritionEstimateData
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

    private fun item(name: String, code: String? = null, warning: String? = null, confidence: Double = 0.9) =
        NutritionEstimateData(name = name, grams = 100.0, calories = 100, protein = 0.0, carbs = 0.0, fat = 0.0, fiber = 0.0, confidence = confidence, warningCode = code, warning = warning, needsConfirmation = code != null)

    @Test
    fun `a watch reply without warnings is just the summary`() {
        val items = listOf(item("Kola"), item("Muz"))
        assertEquals("Kola, Muz · 180 kcal", NutritionWarning.watchReply("Kola, Muz · 180 kcal", items, en = false))
    }

    @Test
    fun `a watch reply names the flagged item and why, summary first`() {
        val items = listOf(item("Kola"), item("Pirinç Pilavı", code = "cooked_assumed"))
        val reply = NutritionWarning.watchReply("Kola, Pirinç Pilavı · 284 kcal", items, en = false)
        val lines = reply.lines()
        assertEquals("Kola, Pirinç Pilavı · 284 kcal", lines.first())
        assertTrue(lines[1].startsWith("⚠ Pirinç Pilavı: Pişmiş ağırlık varsayıldı"))
        assertEquals("Telefonda kontrol et.", lines.last())
        assertTrue(NutritionWarning.watchReply("x", items, en = true).lines().last() == "Check it on your phone.")
    }

    @Test
    fun `a watch reply shows at most two distinct warnings and counts the rest`() {
        val items = listOf(
            item("A", code = "cooked_assumed"), item("B", code = "cooked_assumed"),
            item("C", code = "not_in_catalogue"), item("D", code = "approximate_amount"),
        )
        val reply = NutritionWarning.watchReply("özet", items, en = false)
        assertEquals(2, reply.lines().count { it.startsWith("⚠") })
        assertTrue("same warning is not repeated for B", reply.lines().none { it.startsWith("⚠ B:") })
        assertTrue(reply.contains("(+1)"))
        assertTrue("fits the 600 characters the watch shows", reply.length <= 600)
    }
}
