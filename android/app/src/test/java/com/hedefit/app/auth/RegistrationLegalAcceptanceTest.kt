package com.hedefit.app.auth

import com.hedefit.app.data.auth.RegistrationLegalAcceptance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** KVKK: aydınlatma onayı ile açık rızalar ayrı ve hepsi zorunludur. */
class RegistrationLegalAcceptanceTest {
    private fun acceptance(kvkk: Boolean = true, privacy: Boolean = true, health: Boolean = true, crossBorder: Boolean = true) =
        RegistrationLegalAcceptance(kvkk, privacy, health, crossBorder)

    @Test fun allConsentsGivenIsComplete() {
        acceptance().requireComplete()
    }

    @Test fun missingKvkkNoticeIsRejected() {
        val e = assertThrows(IllegalArgumentException::class.java) { acceptance(kvkk = false).requireComplete() }
        assertEquals("KVKK Aydınlatma Metni'ni onaylamalısın.", e.message)
    }

    @Test fun missingPrivacyPolicyIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { acceptance(privacy = false).requireComplete() }
    }

    @Test fun healthDataConsentCannotBeImpliedByTheNotice() {
        val e = assertThrows(IllegalArgumentException::class.java) { acceptance(health = false).requireComplete() }
        assertEquals("Sağlık verilerinin işlenmesine açık rıza vermelisin.", e.message)
    }

    @Test fun crossBorderConsentCannotBeImpliedByTheNotice() {
        val e = assertThrows(IllegalArgumentException::class.java) { acceptance(crossBorder = false).requireComplete() }
        assertEquals("Verilerin yurt dışına aktarılmasına açık rıza vermelisin.", e.message)
    }
}
