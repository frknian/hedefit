package com.hedefit.app.data

import com.hedefit.app.data.model.aiMemoryTypeLabel
import com.hedefit.app.data.model.consentWithdrawalPatch
import com.hedefit.app.data.model.parseAiMemories
import com.hedefit.app.data.model.parseConsentStatus
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PrivacyModelsTest {
    @Test fun parsesMemories() {
        val items = parseAiMemories(JSONArray("""[
          {"id":"a","type":"constraint","key":"knee","value":"diz ağrısı","source":"user_explicit","updatedAt":"2026-10-02T00:00:00Z"},
          {"id":"b","type":"food_preference","key":"gluten","value":"dislike","source":"inferred"}
        ]"""))
        assertEquals(2, items.size)
        assertTrue(items[0].userExplicit)
        assertEquals("2026-10-02T00:00:00Z", items[0].updatedAt)
        assertFalse(items[1].userExplicit)
        assertNull(items[1].updatedAt)
    }

    @Test fun skipsMalformedMemories() {
        val items = parseAiMemories(JSONArray("""[{"type":"goal"}, 5, {"id":"","type":"goal"}, {"id":"ok","type":"goal","key":"k","value":"v"}]"""))
        assertEquals(listOf("ok"), items.map { it.id })
        assertTrue(parseAiMemories(null).isEmpty())
    }

    @Test fun typeLabels() {
        assertEquals("Hedef", aiMemoryTypeLabel("goal", false))
        assertEquals("Goal", aiMemoryTypeLabel("goal", true))
        assertTrue(aiMemoryTypeLabel("constraint", false).contains("sağlık"))
        assertEquals("future_type", aiMemoryTypeLabel("future_type", false))
    }

    @Test fun consentStatusTreatsBlankAndNullTextAsMissing() {
        val full = parseConsentStatus(JSONObject("""{"health_data_consent_at":"2026-10-01T10:00:00Z","cross_border_consent_at":"2026-10-01T10:00:00Z","consent_text_version":"2026-10-03"}"""))
        assertEquals("2026-10-01T10:00:00Z", full.healthDataConsentAt)
        assertEquals("2026-10-03", full.textVersion)
        assertNull(full.withdrawnAt)

        val withdrawn = parseConsentStatus(JSONObject("""{"health_data_consent_at":"","cross_border_consent_at":null,"consent_withdrawn_at":"2026-10-04T00:00:00Z"}"""))
        assertNull(withdrawn.healthDataConsentAt)
        assertNull(withdrawn.crossBorderConsentAt)
        assertEquals("2026-10-04T00:00:00Z", withdrawn.withdrawnAt)

        assertNull(parseConsentStatus(null).healthDataConsentAt)
    }

    @Test fun withdrawalPatchBlanksSelectedConsentsOnly() {
        val both = consentWithdrawalPatch(health = true, crossBorder = true, nowIso = "T")
        assertEquals("", both.getString("health_data_consent_at"))
        assertEquals("", both.getString("cross_border_consent_at"))
        assertEquals("T", both.getString("consent_withdrawn_at"))

        val onlyHealth = consentWithdrawalPatch(health = true, crossBorder = false, nowIso = "T")
        assertTrue(onlyHealth.has("health_data_consent_at"))
        assertFalse(onlyHealth.has("cross_border_consent_at"))
    }

    @Test fun withdrawalPatchRequiresAtLeastOneConsent() {
        try {
            consentWithdrawalPatch(health = false, crossBorder = false, nowIso = "T")
            fail("istisna bekleniyordu")
        } catch (_: IllegalArgumentException) {
        }
    }
}
