package com.hedefit.app.ui.layout

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutPolicyTest {
    @Test
    fun compactPhonesUseBottomNavigation() {
        assertFalse(LayoutPolicy.usesExpandedNavigation(320))
        assertFalse(LayoutPolicy.usesExpandedNavigation(699))
    }

    @Test
    fun tabletsAndLargeFoldablesUseNavigationRail() {
        assertTrue(LayoutPolicy.usesExpandedNavigation(700))
        assertTrue(LayoutPolicy.usesExpandedNavigation(1_280))
    }

    @Test
    fun narrowPhonesKeepMoreUsableContentWidth() {
        assertEquals(12, LayoutPolicy.horizontalPadding(320))
        assertEquals(18, LayoutPolicy.horizontalPadding(360))
        assertEquals(18, LayoutPolicy.horizontalPadding(700))
    }

    @Test
    fun tabletsAreLargeScreensWithoutHingeSensor() {
        assertTrue(LayoutPolicy.isTablet(800, hasHingeSensor = false))
        assertTrue(LayoutPolicy.isTablet(600, hasHingeSensor = false))
        assertFalse(LayoutPolicy.isTablet(411, hasHingeSensor = false))
    }

    @Test
    fun unfoldedFoldablesAreNotTablets() {
        assertFalse(LayoutPolicy.isTablet(841, hasHingeSensor = true))
    }

    @Test
    fun standSplitsByOrientation() {
        assertTrue(LayoutPolicy.standSideBySide(1280, 800))
        assertFalse(LayoutPolicy.standSideBySide(800, 1280))
    }
}
