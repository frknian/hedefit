package com.hedefit.app.equipment

import com.hedefit.app.ui.screens.cameraPermissionMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EquipmentRecognitionTest {
    @Test fun knownEquipmentLabelResolves() {
        assertEquals("Lat Pulldown", EquipmentCatalog.findByLabel("lat pulldown")?.name)
        assertNotNull(EquipmentCatalog.findByLabel("leg-press"))
    }

    @Test fun unknownEquipmentStaysUnknown() {
        assertNull(EquipmentCatalog.findByLabel("mystery machine"))
    }

    @Test fun canonicalIdsAndLocalizedAliasesResolveToSameEquipment() {
        assertEquals("treadmill", EquipmentCatalog.findByLabel("Koşu Bandı")?.id)
        assertEquals("smith_machine", EquipmentCatalog.findByLabel("smith-machine")?.id)
        assertTrue(EquipmentCatalog.items.size >= 28)
    }

    @Test fun suitableExercisesComeFromRealCatalog() {
        val exercise = com.hedefit.app.data.model.ExerciseCatalogData(
            "db-1", "Wide Grip Lat Pulldown", "beginner", "Cable", listOf("lats"), emptyList(), "strength", emptyList(),
        )
        val equipment = requireNotNull(EquipmentCatalog.findByLabel("lat_pulldown"))
        assertEquals(listOf("db-1"), EquipmentCatalog.matchingExercises(equipment, listOf(exercise)).map { it.id })
    }

    @Test fun permissionStatesHaveActionableMessages() {
        assertNull(cameraPermissionMessage(true, false, true))
        assertEquals("Bu cihazda kullanılabilir kamera bulunamadı.", cameraPermissionMessage(false, false, false))
        assertEquals("Kamera izni kalıcı olarak kapalı. Ayarlar'dan Hedefit için kamera iznini açabilirsin.", cameraPermissionMessage(false, true, true))
    }
}
