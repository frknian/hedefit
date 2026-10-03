package com.hedefit.app.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BillingModelsTest {
    private fun phase(micros: Long, price: String, period: String) = RawPhase(micros, price, period)
    private val base = RawOffer("monthly", null, "tok-base", listOf(phase(99_000_000, "₺99,00", "P1M")))
    private val trial = RawOffer("monthly", "trial7", "tok-trial", listOf(phase(0, "Ücretsiz", "P1W"), phase(99_000_000, "₺99,00", "P1M")))

    @Test fun accountIdMatchesServerAlgorithm() {
        // Sunucu (lib/billing/play.ts) ile aynı sabit vektör: tests/billing.test.mjs'te de var.
        assertEquals("52b17cb67e7f44d81142e5a009d0917c24c255cd2c2f014917b5ce4a47e30644", obfuscatedAccountId("abc"))
        assertTrue(obfuscatedAccountId("user-1") != obfuscatedAccountId("user-2"))
    }

    @Test fun periodParsing() {
        assertEquals(7, isoPeriodToDays("P1W"))
        assertEquals(7, isoPeriodToDays("P7D"))
        assertEquals(14, isoPeriodToDays("P2W"))
        assertNull(isoPeriodToDays("P1M"))
        assertNull(isoPeriodToDays("garbage"))
        assertNull(isoPeriodToDays("P0D"))
    }

    @Test fun trialOfferPreferredWhenPresent() {
        val offer = selectOffer(PRODUCT_PLUS, "monthly", listOf(base, trial))!!
        assertEquals("tok-trial", offer.offerToken)
        assertEquals(7, offer.freeTrialDays)
        assertEquals("₺99,00", offer.price)
    }

    @Test fun baseOfferUsedWhenNoTrial() {
        val offer = selectOffer(PRODUCT_PLUS, "monthly", listOf(base))!!
        assertEquals("tok-base", offer.offerToken)
        assertNull(offer.freeTrialDays)
    }

    @Test fun discountedIntroIsNotTreatedAsFreeTrial() {
        val intro = RawOffer("monthly", "intro", "tok-intro", listOf(phase(49_000_000, "₺49,00", "P1M"), phase(99_000_000, "₺99,00", "P1M")))
        val offer = selectOffer(PRODUCT_PLUS, "monthly", listOf(base, intro))!!
        assertEquals("tok-base", offer.offerToken)
    }

    @Test fun otherBasePlansAreIgnored() {
        val yearly = RawOffer("yearly", null, "tok-year", listOf(phase(849_000_000, "₺849,00", "P1Y")))
        assertEquals("tok-year", selectOffer(PRODUCT_PLUS, "yearly", listOf(base, yearly))!!.offerToken)
        assertNull(selectOffer(PRODUCT_PLUS, "weekly", listOf(base, yearly)))
        assertNull(selectOffer(PRODUCT_PLUS, "monthly", emptyList()))
    }

    @Test fun verifyResponses() {
        assertEquals(VerifyOutcome.Granted("plus", true), parseVerifyResponse(200, """{"plan_tier":"plus","state":"active","entitled":true}"""))
        assertEquals(VerifyOutcome.Granted("free", false), parseVerifyResponse(200, """{"plan_tier":"free","entitled":false}"""))
        assertEquals(VerifyOutcome.Pending, parseVerifyResponse(202, """{"status":"pending"}"""))
        assertEquals(VerifyOutcome.Rejected, parseVerifyResponse(409, "{}"))
        assertEquals(VerifyOutcome.Rejected, parseVerifyResponse(400, "{}"))
        assertEquals(VerifyOutcome.Rejected, parseVerifyResponse(403, "{}"))
        assertEquals(VerifyOutcome.Retry, parseVerifyResponse(502, "{}"))
        assertEquals(VerifyOutcome.Retry, parseVerifyResponse(503, "{}"))
        assertEquals(VerifyOutcome.Retry, parseVerifyResponse(429, "{}"))
        assertEquals(VerifyOutcome.Retry, parseVerifyResponse(200, "not json"))
    }
}
